package com.hellosoda.rmq.consumers
import com.hellosoda.rmq._
import com.hellosoda.rmq.impl._
import com.rabbitmq.client._
import scala.concurrent.{
  ExecutionContext,
  Future }
import scala.util.control.NonFatal

/** An elaborate consumer that will make use of a separate queue for the
  * purpose of redelivering a message upon failure.
  *
  * Based on RecoveryStrategy.limitedRedeliver in:
  * https://github.com/SpinGo/op-rabbit/
  *
  * But initially omitting the intermediate queue.
  */
class OnFailureRedeliverConsumer[T] (
  val maxAttempts : Int)(
  val channel     : RMQChannel,
  val delivery    : PartialFunction[RMQDelivery[T], Future[RMQReply]])(implicit
  val ec          : ExecutionContext)
    extends RMQConsumer[T] {

  val `X-Retry-Attempts-Remaining` = "X-Retry-Attempts-Remaining"

  private def redeliver (
    message : RMQMessage,
    reason  : Throwable
  ) : Future[RMQReply] = {
    val retryAttemptsRemaining =
      message.properties.headers.get(`X-Retry-Attempts-Remaining`).
      map(_.asInstanceOf[Int]).
      getOrElse(maxAttempts)

    if (retryAttemptsRemaining < 1)
      Future.successful(RMQReply.Nack(requeue = false))
    else for {
      _ <- channel.publish(
        exchange   = RMQExchange.Passive(message.envelope.exchange),
        routingKey = message.envelope.routingKey,
        body       = message.bytes,
        properties = message.properties.mapHeaders { _.
          updated(`X-Retry-Attempts-Remaining`, retryAttemptsRemaining - 1) })
    } yield RMQReply.Nack(requeue = false)
  }

  def onCancel (reason : Option[Throwable]) =
    Future.unit

  def onDecodeFailure (
    message : RMQMessage,
    reason  : Throwable
  ) : Future[RMQReply] =
    redeliver(message, reason)

  def onDelivery (delivery : RMQDelivery[T]) : Future[RMQReply] = {
    def fallback () : Future[RMQReply] =
      redeliver(
        delivery.message,
        new RuntimeException("Delivered message not handled"))

    try {
      this.delivery.
      applyOrElse(delivery, { _: RMQDelivery[T] => fallback() }).
      recoverWith {
        case NonFatal(error) =>
          redeliver(delivery.message, error)
      }
    } catch {
      case NonFatal(error) =>
        Future.successful(RMQReply.Shutdown(Some(error)))
    }
  }

  def onDeliveryFailure (
    delivery : RMQDelivery[T],
    reason   : Throwable
  ) : Future[RMQReply] =
    redeliver(delivery.message, reason)

  def onRecover () =
    Future.unit

  def onShutdown (signal : ShutdownSignalException) =
    Future.unit

}
