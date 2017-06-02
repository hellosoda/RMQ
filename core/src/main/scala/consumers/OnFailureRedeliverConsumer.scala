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
  val receiver    : RMQConsumer.DeliveryReceiver[T])
    extends RMQConsumer[T] {

  val `X-Retry-Attempts-Remaining` = "X-Retry-Attempts-Remaining"

  private val nack : Future[RMQReply] =
    Future.successful(RMQReply.Nack(requeue = false))

  private val ignore : Future[RMQReply] =
    Future.successful(RMQReply.Ignore)

  private def redeliver (
    message : RMQMessage,
    reason  : Throwable)(implicit
    ctx     : RMQConsumerContext
  ) : Future[RMQReply] = {
    import ctx.dispatcher

    val retryAttemptsRemaining =
      message.properties.headers.get(`X-Retry-Attempts-Remaining`).
      map(_.asInstanceOf[Int]).
      getOrElse(maxAttempts)

    if (retryAttemptsRemaining < 1) {
      nack
    } else for {
      _ <- ctx.channel.publish(
        exchange   = RMQExchange.Passive(message.envelope.exchange),
        routingKey = message.envelope.routingKey,
        body       = message.bytes,
        properties = message.properties.mapHeaders { _.
          updated(`X-Retry-Attempts-Remaining`, retryAttemptsRemaining - 1) })
    } yield RMQReply.Nack(requeue = false)
  }

  def fallback (
    event : RMQEvent[T])(implicit
    ctx   : RMQConsumerContext
  ) : Future[RMQReply] = event match {
    case delivery: RMQDelivery[_] =>
      fallback(RMQEvent.OnDeliveryFailure(
        delivery, new IllegalStateException("Delivery not handled")))

    case _: RMQEvent.OnCancel => ignore
    case RMQEvent.OnDecodeFailure(m, r) => redeliver(m, r)
    case RMQEvent.OnDeliveryFailure(m, r) => redeliver(m, r)
    case _: RMQEvent.OnRecover => ignore
    case _: RMQEvent.OnShutdown => ignore
  }

  def receive (implicit ctx : RMQConsumerContext) =
    this.receiver

}
