package com.hellosoda.rmq.consumers
import com.hellosoda.rmq._
import com.hellosoda.rmq.impl._
import com.rabbitmq.client._
import scala.concurrent.Future

class OnFailureNackConsumer[T] (
  val channel  : RMQChannel,
  val requeue  : Boolean,
  val delivery : PartialFunction[T, Future[RMQReply]])
    extends RMQConsumer[T] {

  private val nack = Future.successful(RMQReply.Nack(requeue = requeue))

  def onCancel () = Future.unit
  def onDecodeFailure () = nack
  def onDeliveryFailure () = nack
  def onRecover () = Future.unit
  def onShutdown (signal : ShutdownSignalException) = Future.unit

  def onDelivery (delivery : RMQDelivery[T]) : Future[RMQReply] =
    ???

}
