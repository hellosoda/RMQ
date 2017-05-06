package com.hellosoda.rmq
import com.rabbitmq.client._
import scala.concurrent.Future

trait RMQConsumer[T] {

  def onCancel (
    reason : Option[Throwable]
  ) : Future[Unit]

  def onDecodeFailure (
    message : RMQMessage,
    reason  : Throwable
  ) : Future[RMQReply]

  def onDelivery (delivery : RMQDelivery[T]) : Future[RMQReply]

  def onDeliveryFailure (
    delivery : RMQDelivery[T],
    reason   : Throwable
  ) : Future[RMQReply]

  def onRecover () : Future[Unit]

  def onShutdown (signal : ShutdownSignalException) : Future[Unit]

}
