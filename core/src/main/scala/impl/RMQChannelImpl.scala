package com.hellosoda.rmq.impl
import com.hellosoda.rmq._
import com.rabbitmq.client._
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import scala.collection.JavaConverters._
import scala.concurrent.{
  ExecutionContext,
  Future,
  Promise }
import scala.util.Try
import scala.util.control.NonFatal

class RMQChannelImpl (
  private val underlying : Try[Channel])(implicit
  private val ec         : ExecutionContext)
    extends RMQChannel {

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

  def bindQueue (
    queue      : RMQQueue,
    exchange   : RMQExchange,
    routingKey : RMQRoutingKey
  ) : Future[Unit] =
    lock.acquire {
      channel.queueBind(
        queue.name,
        exchange.name,
        routingKey.toString,
        Map.empty.asJava)
    }

  def channel : Channel =
    underlying.get

  def mutex[T] (f : => T) : Future[T] =
    lock.acquire(f)

  def close () : Unit =
    underlying.foreach { _.close() }

  def declareExchange (exchange : RMQExchange) : Future[Unit] =
    mutex {
      exchange match {
        case RMQExchange.Passive(name) =>
          channel.exchangeDeclarePassive(name)

        case decl: RMQExchange.Declare =>
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
        case RMQQueue.Passive(name) =>
          channel.queueDeclarePassive(name)

        case decl: RMQQueue.Declare =>
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
      channel.addConfirmListener(new ConfirmListenerImpl(publisherConfirms))
      channel.confirmSelect()
      publisherConfirmsEnabled.set(true)
    }

  def setQos (qos : Int) : Future[Unit] =
    mutex {
      channel.basicQos(qos)
    }

  def publish [T] (
    exchange   : RMQExchange,
    routingKey : RMQRoutingKey,
    properties : RMQBasicProperties,
    body       : T)(implicit
    codec      : RMQCodec[T]
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

      try {
        val contentType = properties.contentType orElse codec.contentType

        channel.basicPublish(
          exchange.name,
          routingKey.toString,
          properties.
            copy(contentType = contentType).
            asBasicProperties,
          codec.encode(body))

        pc.map(_.promise.future).getOrElse(Future.unit)
      } catch {
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

}
