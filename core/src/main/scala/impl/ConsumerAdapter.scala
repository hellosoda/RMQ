package com.hellosoda.rmq.impl
import com.hellosoda.rmq._
import com.rabbitmq.client._
import scala.concurrent.{
  Await,
  ExecutionContext,
  Future }
import scala.concurrent.duration._
import scala.util.control.NonFatal

class ConsumerAdapter[T] (
  private val channel  : RMQChannel,
  private val consumer : RMQConsumer[T])(implicit
  private val codec    : RMQCodec[T],
  private val ec       : ExecutionContext)
    extends Consumer {

  private def handleReply (
    message : RMQMessage,
    f       : => Future[RMQReply]
  ) : Unit =
    f.foreach {
      case RMQReply.Ack =>
        channel.ack(
          deliveryTag = message.envelope.deliveryTag,
          multiple    = false)

      case RMQReply.Cancel =>
        // TODO: Which cancellation path to use?
        Await.result(channel.cancelConsumer(message.consumerTag), Duration.Inf)

      case nack: RMQReply.Nack =>
        channel.nack(
          deliveryTag = message.envelope.deliveryTag,
          multiple    = false)

      case RMQReply.Shutdown(Some(reason)) =>
        channel.close(-1, s"Channel closed with $reason: ${reason.getMessage}")

      case RMQReply.Shutdown(None) =>
        channel.close(-1, "Channel closed on consumer request")
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
      bytes       = body)

    val decoded =
      try codec.decode(body)
      catch { case NonFatal(error) =>
        return handleReply(
          message,
          consumer.onDecodeFailure(message, error))
      }

    val delivery = RMQDelivery[T](message, decoded)

    val consumed = consumer.
      onDelivery(delivery).
      recoverWith { case NonFatal(error) =>
        consumer.onDeliveryFailure(
          delivery = delivery,
          reason   = error)
      }

    handleReply(message, consumed)
  } catch {
    case NonFatal(error) =>
      error.printStackTrace
      channel.close(
        -1, s"The channel has encountered an unrecoverable $error: ${error.getMessage}")
  }

  def handleRecoverOk (consumerTag : String) : Unit =
    Await.result(consumer.onRecover(), Duration.Inf)

  def handleShutdownSignal (
    consumerTag : String,
    signal      : ShutdownSignalException
  ) : Unit =
    Await.result(consumer.onShutdown(signal), Duration.Inf)

}
