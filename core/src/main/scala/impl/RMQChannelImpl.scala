package com.hellosoda.rmq.impl
import com.hellosoda.rmq._
import com.rabbitmq.client._
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import scala.collection.JavaConverters._
import scala.concurrent.{
  Await,
  ExecutionContext,
  Future,
  Promise }
import scala.concurrent.duration._
import scala.util.{
  Failure,
  Success,
  Try }
import scala.util.control.NonFatal

class RMQChannelImpl (
  private val underlying : Try[Channel],
  private val connection : RMQConnection)(implicit
  private val ec         : ExecutionContext)
    extends RMQChannel
    with    com.typesafe.scalalogging.LazyLogging {

  private val channelInfo = underlying.map { channel =>
    List(
      s"connection=${channel.getConnection.getId}",
      s"channel=${channel.getChannelNumber}").
      mkString(" ")
  }.getOrElse("<DEAD CHANNEL>")

  underlying match {
    case Failure(error) =>
      logger.error("Channel open failure", error)

    case Success(channel) =>
      logger.info(s"createChannel: ${channelInfo}")
  }

  private val publisherConfirmsEnabled = new AtomicBoolean(false)
  private val publisherConfirms = new ConcurrentLinkedQueue[PublisherConfirm]()

  // https://www.rabbitmq.com/api-guide.html#channel-threads
  // "While some operations on channels are safe to invoke concurrently,
  //  some are not and will result in incorrect frame
  //  interleaving on the wire"
  //
  // As an example of thread-unsafety, check out the addReturnListener etc.
  // methods of ChannelN of the Java RabbitMQ client. Thread-safe methods of
  // the Channel are generally implemented using `synchronized`.
  //
  // RMQChannel plays it safe by wrapping all channel calls with the
  // asynchronous mutex, thus removing the thread-safety consideration and
  // further ensuring that a calling thread pool isn't blocked by a
  // hidden critical section. This will come at the cost of all calls passing
  // through the `ec`.
  //
  private val lock = new AsyncMutex()

  def ack (
    deliveryTag : RMQDeliveryTag,
    multiple    : Boolean
  ) : Future[Unit] =
    mutex {
      lazy val msg = s"deliveryTag=$deliveryTag multiple=${if (multiple) 1 else 0} $channelInfo"
      logger.trace(s"basicAck: $msg")
      try {
        channel.basicAck(deliveryTag.value, multiple)
      } catch {
        case NonFatal(error) =>
          logger.error(s"ack failed: $msg", error)
          throw error
      }
    }

  def bindQueue (
    queue      : RMQQueue,
    exchange   : RMQExchange,
    routingKey : RMQRoutingKey
  ) : Future[Unit] =
    lock.acquire {
      lazy val msg = s"queue=$queue exchange=$exchange routingKey=$routingKey $channelInfo"
      logger.trace(s"queueBind: $msg")
      try {
        channel.queueBind(
          queue.name,
          exchange.name,
          routingKey.toString,
          Map.empty.asJava)
      } catch {
        case NonFatal(error) =>
          logger.error(s"bindQueue failed: $msg", error)
          throw error
      }
    }

  def cancelConsumer (consumerTag : RMQConsumerTag) : Future[Unit] =
    mutex {
      lazy val msg = s"consumerTag=$consumerTag $channelInfo"
      logger.trace(s"basicCancel: $msg")
      try {
        channel.basicCancel(consumerTag.toString)
      } catch {
        case NonFatal(error) =>
          logger.error(s"cancelConsumer failed: $msg", error)
          throw error
      }
    }

  def channel : Channel =
    underlying.get

  def close () : Unit =
    underlying.foreach { chan =>
      logger.info(s"close: $channelInfo")
      try {
        chan.close()
      } catch {
        case NonFatal(error) =>
          logger.error(s"close failed: $channelInfo", error)
          throw error
      }
    }

  def close (
    code    : Int,
    message : String
  ) : Unit =
    underlying.foreach { chan =>
      lazy val msg = s"code=$code message=$message $channelInfo"
      logger.debug(s"close: $msg")
      try {
        chan.close(code, message)
      } catch {
        case NonFatal(error) =>
          logger.error(s"close failed: $msg", error)
          throw error
      }
    }

  def consumeNative (
    queue  : RMQQueue,
    native : Consumer
  ) : Future[RMQConsumerTag] =
    mutex {
      lazy val msg = s"queue=$queue $channelInfo"

      val consumerTag = try {
        RMQConsumerTag(
          channel.basicConsume(
            queue.name,
            false, // autoAck
            "", // consumerTag
            false, // noLocal
            false, // exclusive
            Map.empty.asJava, // arguments
            native))
      } catch {
        case NonFatal(error) =>
          logger.error(s"consumeNative failed: $msg", error)
          throw error
      }

      logger.trace(s"basicConsume: $msg")
      consumerTag
    }

  def consume[T] (
    queue    : RMQQueue,
    consumer : RMQConsumer[T])(implicit
    codec    : RMQCodec.Decoder[T]
  ) : Future[RMQConsumerHandle[T]] =
    mutex {
      lazy val msg = s"queue=$queue $channelInfo"

      val consumerTag = try {
        RMQConsumerTag(
          channel.basicConsume(
            queue.name,
            false, // autoAck
            "", // consumerTag
            false, // noLocal
            false, // exclusive
            Map.empty.asJava, // arguments
            new ConsumerAdapter[T](this, consumer)))
      } catch {
        case NonFatal(error) =>
          logger.error(s"consume failed: $msg", error)
          throw error
      }

      logger.trace(s"basicConsume: $msg")

      new RMQConsumerHandle[T](
        consumerTag = consumerTag,
        channel = this,
        consumer = consumer)
    }

  def consumeDelivery[T] (
    queue    : RMQQueue)(
    delivery : RMQConsumer.DeliveryReceiver[T])(implicit
    codec    : RMQCodec.Decoder[T],
    strategy : RMQConsumerStrategy
  ) : Future[RMQConsumerHandle[T]] =
    consume[T](queue, strategy.createConsumer[T](delivery))

  def consumeDeliveryAck[T] (
    queue    : RMQQueue)(
    delivery : PartialFunction[RMQDelivery[T], Future[_]])(implicit
    codec    : RMQCodec.Decoder[T],
    strategy : RMQConsumerStrategy
  ) : Future[RMQConsumerHandle[T]] =
    consumeDelivery(queue)(delivery.andThen { _.map { _ => RMQReply.Ack }})

  def consumerCount (queue : RMQQueue) : Future[Long] =
    mutex {
      try {
        channel.consumerCount(queue.name)
      } catch {
        case NonFatal(error) =>
          logger.error(s"consumerCount failed: queue=$queue", error)
          throw error
      }
    }

  def declareExchange (exchange : RMQExchange) : Future[Unit] =
    mutex {
      try {
        exchange match {
          case pasv: RMQExchange.Passive =>
            logger.trace(
              s"exchangeDeclare: passive=1 exchange=$pasv $channelInfo")
            channel.exchangeDeclarePassive(pasv.name)

          case decl: RMQExchange.Declare =>
            logger.trace(
              s"exchangeDeclare: passive=0 exchange=$decl durable=${decl.durable} autoDelete=${decl.autoDelete} $channelInfo")
            channel.exchangeDeclare(
              decl.name,
              decl.kind.native,
              decl.durable,
              decl.autoDelete,
              decl.arguments.mapValues(_.asInstanceOf[AnyRef]).asJava)
        }
      } catch {
        case NonFatal(error) =>
          logger.error(s"declareExchange failed: exchange=$exchange $channelInfo", error)
          throw error
      }
    }

  def declareQueue (queue : RMQQueue) : Future[Unit] =
    mutex {
      try {
        queue match {
          case pasv: RMQQueue.Passive =>
            logger.trace(s"queueDeclare: passive=1 queue=$pasv $channelInfo")
            channel.queueDeclarePassive(pasv.name)

          case decl: RMQQueue.Declare =>
            logger.trace(s"queueDeclare: passive=0 queue=$decl durable=${decl.durable} exclusive=${decl.exclusive} autoDelete=${decl.autoDelete} $channelInfo")
            channel.queueDeclare(
              decl.name,
              decl.durable,
              decl.exclusive,
              decl.autoDelete,
              decl.arguments.mapValues(_.asInstanceOf[AnyRef]).asJava)
        }
      } catch {
        case NonFatal(error) =>
          logger.error(s"declareQueue failed: queue=$queue $channelInfo", error)
          throw error
      }
    }

  def enablePublisherConfirms () : Future[Unit] =
    mutex {
      logger.trace(s"enablePublisherConfirms: $channelInfo")
      try {
        channel.addConfirmListener(
          new ConfirmListenerAdapter(publisherConfirms))
        publisherConfirmsEnabled.set(true)
        channel.confirmSelect()
      } catch {
        case NonFatal(error) =>
          logger.error(s"enablePublisherConfirms failed: $channelInfo", error)
          throw error
      }
    }

  def enablePublisherConfirmsSync () : Unit =
    Await.result(enablePublisherConfirms(), Duration.Inf)

  def messageCount (queue : RMQQueue) : Future[Long] =
    mutex {
      try {
        channel.messageCount(queue.name)
      } catch {
        case NonFatal(error) =>
          logger.error(s"messageCount failed: queue=$queue $channelInfo", error)
          throw error
      }
    }

  def mutex[T] (f : => T) : Future[T] =
    lock.acquire(f)

  def nack (
    deliveryTag : RMQDeliveryTag,
    multiple    : Boolean = false,
    requeue     : Boolean = false
  ) : Future[Unit] =
    mutex {
      lazy val msg = s"deliveryTag=$deliveryTag multiple=${if (multiple) 1 else 0} requeue=${if (requeue) 1 else 0} $channelInfo"

      logger.trace(s"basicNack: $msg")
      try {
        channel.basicNack(deliveryTag.value, multiple, requeue)
      } catch {
        case NonFatal(error) =>
          logger.error(s"nack failed: $msg", error)
          throw error
      }
    }

  def setQos (qos : Int) : Future[Unit] =
    mutex {
      lazy val msg = s"qos=$qos $channelInfo"
      logger.trace(s"basicQos: $msg")
      try {
        channel.basicQos(qos)
      } catch {
        case NonFatal(error) =>
          logger.error(s"setQos failed: $msg", error)
          throw error
      }
    }

  def publish [T] (
    exchange   : RMQExchange,
    routingKey : RMQRoutingKey,
    properties : RMQBasicProperties,
    body       : T)(implicit
    encoder    : RMQCodec.Encoder[T]
  ) : Future[Unit] =
    mutex {
      val pc : Option[PublisherConfirm] =
        if (!publisherConfirmsEnabled.get()) None
        else Some(PublisherConfirm(
          seqNo   = channel.getNextPublishSeqNo(),
          promise = Promise[Unit]()))

      pc.foreach { pc =>
        publisherConfirms.add(pc)
      }

      val props = properties.copy(
        contentType = properties.contentType orElse encoder.contentType)

      connection.
        waitUnblocked(). // Preempt blocking in basicPublish.
        flatMap { _ =>
          channel.basicPublish(
            exchange.name,
            routingKey.toString,
            props.asBasicProperties,
            encoder.encode(body))
          pc.map(_.promise.future).getOrElse(Future.unit)
        }.recoverWith {
          case NonFatal(error) =>
            pc.foreach { pc =>
              publisherConfirms.remove(pc)
              pc.promise.tryFailure(error)
            }

            Future.failed(error)
        }
    }.flatMap {
      x => x
    }

  def recover (requeue : Boolean) : Future[Unit] =
    mutex {
      logger.trace(s"recover: requeue=${if (requeue) 1 else 0} $channelInfo")
      channel.basicRecover(requeue)
    }

  def txCommit () : Future[Unit] =
    mutex { channel.txCommit() }

  def txRollback () : Future[Unit] =
    mutex { channel.txRollback() }

  def txSelect () : Future[Unit] =
    mutex { channel.txSelect() }

}
