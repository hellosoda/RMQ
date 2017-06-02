package com.hellosoda.rmq.impl
import com.hellosoda.rmq._
import com.rabbitmq.client._
import scala.concurrent.{
  Await,
  ExecutionContext,
  Future }
import scala.concurrent.duration._
import scala.util.control.NonFatal

// TODO: Ensure that errors outside of the async boundary will always result
//       in consumer shutdown.

class ConsumerAdapter[T] (
  private val channel  : RMQChannel,
  private val consumer : RMQConsumer[T])(implicit
  private val codec    : RMQCodec[T],
  private val ec       : ExecutionContext)
    extends Consumer {

  private val ctx : RMQConsumerContext =
    new RMQConsumerContext(channel)(ec)

  private def call (
    event : RMQEvent[T]
  ) : Future[Unit] = {
    val applied =
      consumer.receive(ctx).applyOrElse(event, { _ : RMQEvent[T] =>
        consumer.fallback(event)(ctx)
      })

    applied.flatMap { _ match {
      case RMQReply.Ack =>
        channel.ack(
          deliveryTag = message.envelope.deliveryTag,
          multiple    = false)

      case RMQReply.Cancel =>
        // TODO: Which cancellation path to use?
        channel.cancelConsumer(message.consumerTag)

      case nack: RMQReply.Nack =>
        channel.nack(
          deliveryTag = message.envelope.deliveryTag,
          multiple    = false)

      case RMQReply.Shutdown(Some(reason)) =>
        Future.successful {
          channel.close(
            -1, s"Channel closed with $reason: ${reason.getMessage}")
        }

      case RMQReply.Shutdown(None) =>
        Future.successful {
          channel.close(-1, "Channel closed on consumer request")
        }
    }}
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
      try {
        codec.decode(body)
      } catch {
        case NonFatal(error) =>
          call(RMQEvent.OnDecodeFailure(message, error))
          return
      }

    call(RMQDelivery[T](message, decoded)).recoverWith {
      case NonFatal(error) =>
        try {
          call(RMQEvent.OnDeliveryFailure(message, error))
        } catch {
          case NonFatal(error) =>
            error.printStackTrace
            channel.close(
              -1, s"The channel has encountered an unrecoverable $error: ${error.getMessage}")
            Future.successful(Unit)
        }
    }
  } catch {
    case NonFatal(error) =>
      error.printStackTrace
      channel.close(
        -1, s"The channel has encountered an unrecoverable $error: ${error.getMessage}")
  }

  def handleRecoverOk (consumerTag : String) : Unit =
    Await.result(call(RMQEvent.OnRecover()), Duration.Inf)

  def handleShutdownSignal (
    consumerTag : String,
    signal      : ShutdownSignalException
  ) : Unit =
    Await.result(call(RMQEvent.OnShutdown(signal)), Duration.Inf)

}
