package com.hellosoda.rmq.consumers
import com.hellosoda.rmq._
import com.hellosoda.rmq.impl._
import com.rabbitmq.client._
import scala.concurrent.Future
import scala.util.control.NonFatal

class OnFailureShutdownConsumer[T] (
  val channel  : RMQChannel,
  val delivery : PartialFunction[RMQDelivery[T], Future[RMQReply]],
  val requeue  : Boolean)
    extends RMQConsumer[T] {

  def onCancel (reason : Option[Throwable]) = Future.unit

  def onDecodeFailure (
    message : RMQMessage,
    reason  : Throwable
  ) : Future[RMQReply] =
    Future.successful(RMQReply.Shutdown(Some(reason)))

  def onDelivery (delivery : RMQDelivery[T]) : Future[RMQReply] =
    try {
      this.delivery.applyOrElse(
        delivery,
        { _: RMQDelivery[T] =>
          Future.successful(RMQReply.Shutdown(Some(
            new RuntimeException("The incoming message was not handled"))))})
    } catch {
      case NonFatal(error) =>
        Future.successful(RMQReply.Shutdown(Some(error)))
    }

  def onDeliveryFailure (
    delivery : RMQDelivery[T],
    reason   : Throwable
  ) : Future[RMQReply] =
    Future.successful(RMQReply.Shutdown(Some(reason)))

  def onRecover () = Future.unit
  def onShutdown (signal : ShutdownSignalException) = Future.unit

}
