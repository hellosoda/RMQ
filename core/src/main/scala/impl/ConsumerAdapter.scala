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

  private val ctx : RMQConsumerContext =
    new RMQConsumerContext(channel)(ec)

  private val liftedReceive = consumer.receive(ctx).lift

  private def call (
    message : RMQMessage,
    event   : RMQEvent[T]
  ) : Future[Unit] = {
    val reply = liftedReceive(event) match {
      case None => consumer.fallback(event)(ctx)
      case Some(reply) => reply.flatMap {
        case RMQReply.Ignore => consumer.fallback(event)(ctx)
        case _ => Future.successful(reply)
      }
    }

    reply.flatMap { _ match {
      case RMQReply.Ack if message != null =>
        channel.ack(
          deliveryTag = message.envelope.deliveryTag,
          multiple    = false)

      case nack: RMQReply.Nack if message != null =>
        channel.nack(
          deliveryTag = message.envelope.deliveryTag,
          multiple    = false)

      case RMQReply.Ack if message == null =>
        Future.successful {
          channel.close(-1, s"Channel closed: invalid ack")
        }

      case _: RMQReply.Nack if message == null =>
        Future.successful {
          channel.close(-1, s"Channel closed: invalid nack")
        }

      case RMQReply.Cancel =>
        // TODO: Which cancellation path to use?
        channel.cancelConsumer(message.consumerTag)

      case RMQReply.Shutdown(Some(reason)) =>
        Future.successful {
          channel.close(
            -1, s"Channel closed with $reason: ${reason.getMessage}")
        }

      case RMQReply.Shutdown(None) =>
        Future.successful {
          channel.close(-1, "Channel closed on consumer request")
        }

      case RMQReply.Ignore if message != null =>
        Future.successful {
          channel.close(-1, "Channel closed: invalid ignore")
        }

        // TODO: Re-check the cases here.
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
          call(message, RMQEvent.OnDecodeFailure(message, error))
          return
      }

    call(message, RMQDelivery[T](message, decoded)).recoverWith {
      case NonFatal(error) =>
        try {
          call(message, RMQEvent.OnDeliveryFailure(message, error))
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
    Await.result(call(null, RMQEvent.OnRecover()), Duration.Inf)

  def handleShutdownSignal (
    consumerTag : String,
    signal      : ShutdownSignalException
  ) : Unit =
    Await.result(call(null, RMQEvent.OnShutdown(signal)), Duration.Inf)

}
