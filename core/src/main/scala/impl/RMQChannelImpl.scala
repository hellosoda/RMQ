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
      logger.debug(s"createChannel: ${channelInfo}")
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
      logger.trace(s"basicAck: deliveryTag=$deliveryTag multiple=${if (multiple) 1 else 0} $channelInfo")
      channel.basicAck(deliveryTag.value, multiple)
    }

  def bindQueue (
    queue      : RMQQueue,
    exchange   : RMQExchange,
    routingKey : RMQRoutingKey
  ) : Future[Unit] =
    lock.acquire {
      logger.trace(s"queueBind: queue=$queue exchange=$exchange routingKey=$routingKey $channelInfo")
      channel.queueBind(
        queue.name,
        exchange.name,
        routingKey.toString,
        Map.empty.asJava)
    }

  def cancelConsumer (consumerTag : RMQConsumerTag) : Future[Unit] =
    mutex {
      logger.trace(s"basicCancel: consumerTag=$consumerTag $channelInfo")
      channel.basicCancel(consumerTag.toString)
    }

  def channel : Channel =
    underlying.get

  def close () : Unit =
    underlying.foreach { chan =>
      logger.debug(s"close: $channelInfo")
      chan.close()
    }

  def close (
    code    : Int,
    message : String
  ) : Unit =
    underlying.foreach { chan =>
      logger.debug(s"close: code=$code message=$message $channelInfo")
      chan.close(code, message)
    }

  def consumeNative (
    queue  : RMQQueue,
    native : Consumer
  ) : Future[RMQConsumerTag] =
    mutex {
      val consumerTag = RMQConsumerTag(
        channel.basicConsume(
          queue.name,
          false, // autoAck
          "", // consumerTag
          false, // noLocal
          false, // exclusive
          Map.empty.asJava, // arguments
          native))
      logger.trace(s"basicConsume: queue=$queue tag=$consumerTag $channelInfo")

      consumerTag
    }

  def consume[T] (
    queue    : RMQQueue,
    consumer : RMQConsumer[T])(implicit
    codec    : RMQCodec.Decoder[T]
  ) : Future[RMQConsumerHandle[T]] =
    mutex {
      val consumerTag = RMQConsumerTag(
        channel.basicConsume(
          queue.name,
          false, // autoAck
          "", // consumerTag
          false, // noLocal
          false, // exclusive
          Map.empty.asJava, // arguments
          new ConsumerAdapter[T](this, consumer)))
      logger.trace(s"basicConsume: queue=$queue tag=$consumerTag $channelInfo")

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
      channel.consumerCount(queue.name)
    }

  def declareExchange (exchange : RMQExchange) : Future[Unit] =
    mutex {
      exchange match {
        case pasv: RMQExchange.Passive =>
          logger.trace(
            s"exchangeDeclare: passive=1 exchange=$pasv $channelInfo")
          channel.exchangeDeclarePassive(pasv.name)

        case decl: RMQExchange.Declare =>
          logger.trace(
            s"exchangeDeclare: passive=0 exchange=$decl $channelInfo")
          channel.exchangeDeclare(
            decl.name,
            decl.kind.native,
            decl.durable,
            decl.autoDelete,
            decl.arguments.mapValues(_.asInstanceOf[AnyRef]).asJava)
      }
    }

  def declareQueue (queue : RMQQueue) : Future[Unit] =
    mutex {
      queue match {
        case pasv: RMQQueue.Passive =>
          logger.trace(s"queueDeclare: passive=1 queue=$pasv $channelInfo")
          channel.queueDeclarePassive(pasv.name)

        case decl: RMQQueue.Declare =>
          logger.trace(s"queueDeclare: passive=0 queue=$decl $channelInfo")
          channel.queueDeclare(
            decl.name,
            decl.durable,
            decl.exclusive,
            decl.autoDelete,
            decl.arguments.mapValues(_.asInstanceOf[AnyRef]).asJava)
      }
    }

  def enablePublisherConfirms () : Future[Unit] =
    mutex {
      logger.trace(s"enablePublisherConfirms: $channelInfo")
      channel.addConfirmListener(new ConfirmListenerAdapter(publisherConfirms))
      publisherConfirmsEnabled.set(true)
      channel.confirmSelect()
    }

  def enablePublisherConfirmsSync () : Unit =
    Await.result(enablePublisherConfirms(), Duration.Inf)

  def messageCount (queue : RMQQueue) : Future[Long] =
    mutex {
      channel.messageCount(queue.name)
    }

  def mutex[T] (f : => T) : Future[T] =
    lock.acquire(f)

  def nack (
    deliveryTag : RMQDeliveryTag,
    multiple    : Boolean = false,
    requeue     : Boolean = false
  ) : Future[Unit] =
    mutex {
      logger.trace(s"basicNack: deliveryTag=$deliveryTag multiple=${if (multiple) 1 else 0} requeue=${if (requeue) 1 else 0} $channelInfo")
      channel.basicNack(deliveryTag.value, multiple, requeue)
    }

  def setQos (qos : Int) : Future[Unit] =
    mutex {
      logger.trace(s"basicQos: qos=$qos $channelInfo")
      channel.basicQos(qos)
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
