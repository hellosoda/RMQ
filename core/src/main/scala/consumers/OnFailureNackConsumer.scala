package com.hellosoda.rmq.consumers
import com.hellosoda.rmq._
import com.hellosoda.rmq.impl._
import com.rabbitmq.client._
import scala.concurrent.Future
import scala.util.control.NonFatal

class OnFailureNackConsumer[T] (
  val channel  : RMQChannel,
  val delivery : PartialFunction[RMQDelivery[T], Future[RMQReply]],
  val requeue  : Boolean)
    extends RMQConsumer[T] {

  private val nack =
    Future.successful(RMQReply.Nack(requeue = requeue))

  def onCancel (reason : Option[Throwable]) = Future.unit

  def onDecodeFailure (
    message : RMQMessage,
    reason  : Throwable
  ) : Future[RMQReply] =
    nack

  def onDelivery (delivery : RMQDelivery[T]) : Future[RMQReply] =
    try {
      this.delivery.applyOrElse(delivery, { _: RMQDelivery[T] => nack })
    } catch {
      case NonFatal(error) =>
        // XXX: Produce a RMQReply.Close or RMQReply.Shutdown
        channel.close(-1, s"Unexpected error on delivery: $error")
        nack
    }

  def onDeliveryFailure (
    delivery : RMQDelivery[T],
    reason   : Throwable
  ) : Future[RMQReply] =
    nack

  def onRecover () = Future.unit
  def onShutdown (signal : ShutdownSignalException) = Future.unit

}
