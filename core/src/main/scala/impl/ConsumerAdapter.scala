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
    extends Consumer
    with    com.typesafe.scalalogging.LazyLogging {

  private val ctx : RMQConsumerContext =
    new RMQConsumerContext(channel)(ec)

  private val liftedReceive = consumer.receive(ctx).lift

  private def call (
    message : RMQMessage,
    event   : RMQEvent[T]
  ) : Future[Unit] = {
    val reply : Future[RMQReply] = liftedReceive(event) match {
      case None => consumer.fallback(event)(ctx)
      case Some(reply) => reply.flatMap {
        case RMQReply.Ignore => consumer.fallback(event)(ctx)
        case _ => reply
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

  def handleCancel (consumerTag : String) : Unit = {
    logger.trace(s"handleCancel($consumerTag)")
  }

  def handleCancelOk (consumerTag : String) : Unit = {
    logger.trace(s"handleCancelOk($consumerTag)")
  }

  // TODO: Would this be called if, for example, the consumer
  //       is registered to many basicConsume? (i.e. may receive
  //       messages across several consumerTags).
  def handleConsumeOk (consumerTag : String) : Unit = {
    logger.trace(s"handleConsumeOk($consumerTag)")
  }

  def handleDelivery (
    consumerTag : String,
    envelope    : Envelope,
    properties  : AMQP.BasicProperties,
    body        : Array[Byte]
  ) : Unit = try {
    logger.trace(s"handleDelivery($consumerTag, ...)")

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
          try {
            call(message, RMQEvent.OnDecodeFailure(message, error))
          } catch {
            case NonFatal(error) =>
              logger.error(s"Consumer error (OnDecodeFailure)", error)
              channel.close(-1, s"The channel has encountered an unrecoverable $error: ${error.getMessage}")
          }

          return
      }

    call(message, RMQDelivery[T](message, decoded)).recoverWith {
      case NonFatal(error) =>
        try {
          call(message, RMQEvent.OnDeliveryFailure(message, error))
        } catch {
          case NonFatal(error) =>
            logger.error(s"Consumer error (OnDeliveryFailure)", error)
            channel.close(
              -1, s"The channel has encountered an unrecoverable $error: ${error.getMessage}")
            Future.successful(Unit)
        }
    }
  } catch {
    case NonFatal(error) =>
      logger.error(s"Consumer error (OnDelivery)", error)
      channel.close(
        -1, s"The channel has encountered an unrecoverable $error: ${error.getMessage}")
  }

  def handleRecoverOk (consumerTag : String) : Unit = {
    logger.trace(s"handleRecoverOk($consumerTag)")
    Await.result(call(null, RMQEvent.OnRecover()), Duration.Inf)
  }

  def handleShutdownSignal (
    consumerTag : String,
    signal      : ShutdownSignalException
  ) : Unit = {
    logger.trace(
      s"handleShutdownSignal($consumerTag, $signal '${signal.getMessage}')")
    Await.result(call(null, RMQEvent.OnShutdown(signal)), Duration.Inf)
  }

}
