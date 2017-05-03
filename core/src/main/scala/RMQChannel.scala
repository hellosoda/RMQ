package com.hellosoda.rmq
import com.hellosoda.rmq.impl._
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

class RMQChannel private[rmq] (
  private val underlying : Try[Channel])(implicit
  private val ec         : ExecutionContext)
    extends java.io.Closeable {

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

  def close () : Unit =
    underlying.foreach { _.close() }

  def declareExchange (exchange : RMQExchange) : Future[Unit] =
    lock.acquire {
      exchange match {
        case RMQExchange.Passive(name) =>
          channel.exchangeDeclarePassive(name)

        case decl: RMQExchange.Declare =>
          ???
      }
    }

  def declareQueue (queue : RMQQueue) : Future[Unit] =
    lock.acquire {
      queue match {
        case RMQQueue.Passive(name) =>
          channel.queueDeclarePassive(name)

        case decl: RMQQueue.Declare =>
          ???
      }
    }

  def enablePublisherConfirms () : Future[Unit] =
    lock.acquire {
      channel.addConfirmListener(new ConfirmListenerImpl(publisherConfirms))
      channel.confirmSelect()
      publisherConfirmsEnabled.set(true)
    }

  def setQos (qos : Int) : Future[Unit] =
    lock.acquire {
      channel.basicQos(qos)
    }

  def publish [T] (
    exchange   : RMQExchange,
    routingKey : RMQRoutingKey,
    properties : AMQP.BasicProperties,
    body       : T)(implicit
    codec      : RMQCodec[T]
  ) : Future[Unit] =
    lock.acquire {
      val pc : PublisherConfirm =
        if (!publisherConfirmsEnabled.get()) null
        else PublisherConfirm(
          seqNo   = channel.getNextPublishSeqNo(),
          promise = Promise[Unit]())

      if (pc != null)
        publisherConfirms.add(pc)

      try {
        //val contentType = properties.contentType orElse codec.contentType

        channel.basicPublish(
          exchange.name,
          routingKey.toString,
          properties,
          codec.encode(body))
        if (pc != null) pc.promise.future
        else Future.unit
      } catch {
        case NonFatal(error) =>
          if (pc != null) {
            publisherConfirms.remove(pc)
            pc.promise.tryFailure(error)
          }

          Future.failed(error)
      }
    }.flatMap {
      x => x
    }

}
