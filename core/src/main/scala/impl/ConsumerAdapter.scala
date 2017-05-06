package com.hellosoda.rmq.impl
import com.hellosoda.rmq._
import com.rabbitmq.client._
import scala.concurrent.{
  Await,
  ExecutionContext }
import scala.concurrent.duration._
import scala.util.control.NonFatal

class ConsumerAdapter[T] (
  private val channel  : RMQChannel,
  private val consumer : RMQConsumer[T])(implicit
  private val codec    : RMQCodec[T],
  private val ec       : ExecutionContext)
    extends Consumer {

  private def handleReply (
    deliveryTag : RMQDeliveryTag,
    f           : => Future[RMQReply]
  ) : Unit =
    f.foreach {
      case RMQReply.Ack =>
        channel.ack(
          deliveryTag = deliveryTag,
          multiple = false)

      case RMQReply.Cancel =>
        consumer.cancel()

      case nack: RMQReply.Nack =>
        channel.nack(
          deliveryTag = deliveryTag,
          multiple = nack.multiple)

      case RMQReply.Shutdown =>
        ???
    }

  def handleCancel (consumerTag : String) : Unit =
    ()

  def handleCancelOk (consumerTag : String) : Unit =
    ()

  // TODO: Would this be called if, for example, the consumer
  //       is registered to many basicConsume? (i.e. may receive
  //       messages across several consumerTags).
  def handleConsumeOk (consumerTag : String) : Unit =
    ()

  def handleDelivery (
    consumerTag : String,
    envelope    : Envelope,
    properties  : AMQP.BasicProperties,
    body        : Array[Byte]
  ) : Unit = try {

    val message = RMQMessage(
      consumerTag = RMQConsumerTag(consumerTag),
      envelope    = new RMQEnvelope(envelope),
      properties  = RMQBasicProperties.fromBasicProperties(properties),
      body        = body)

    val decoded =
      try codec.decode(body)
      catch { case NonFatal(error) =>
        handleReply(
          message,
          consumer.onDecodeFailure(
            channel, delivery, error))
      }

    val delivery = RMQDelivery[T](message, decoded)

    val consumed = consumer.
      onDelivery(delivery)
      recoverWith { case NonFatal(error) =>
        consumer.onDeliveryFailure(channel, delivery, error)
      }

    handleReply(message, consumed)
  } catch {
    case NonFatal(error) =>
      channel.close(
        -1, s"The channel has encountered an unrecoverable error: $error")
  }

  def handleRecoverOk (consumerTag : String) : Unit =
    // Called on the Channel's dispatch thread.
    Await.result(consumer.onRecover(), Duration.Inf)

  def handleShutdownSignal (
    consumerTag : String,
    signal      : ShutdownSignalException
  ) : Unit =
    // Called on the Channel's dispatch thread.
    Await.result(consumer.onShutdown(signal), Duration.Inf)

}
