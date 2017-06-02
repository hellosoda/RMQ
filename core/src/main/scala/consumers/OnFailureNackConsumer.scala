package com.hellosoda.rmq.consumers
import com.hellosoda.rmq._
import scala.concurrent.Future

abstract class OnFailureNackConsumer[T] (
  val requeue : Boolean)
    extends RMQConsumer[T] {

  private val nack : Future[RMQReply] =
    Future.successful(RMQReply.Nack(requeue = requeue))

  private val ignore : Future[RMQReply] =
    Future.successful(RMQReply.Ignore)

  def fallback (
    event : RMQEvent[T])(implicit
    ctx   : RMQConsumerContext
  ) : Future[RMQReply] = event match {
    case _: RMQDelivery[_] => nack
    case _: RMQEvent.OnCancel => ignore
    case _: RMQEvent.OnDecodeFailure => nack
    case _: RMQEvent.OnDeliveryFailure => nack
    case _: RMQEvent.OnRecover => ignore
    case _: RMQEvent.OnShutdown => ignore
  }

}

object OnFailureNackConsumer
