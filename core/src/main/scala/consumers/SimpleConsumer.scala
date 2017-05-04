package com.hellosoda.rmq.consumers
import com.hellosoda.rmq._
import scala.concurrent.{
  ExecutionContext,
  Future }

class SimpleConsumer[T] (
  val channel     : RMQChannel,
  val consumerTag : RMQConsumerTag)(implicit
  val ec    : ExecutionContext,
  val codec : RMQCodec[T])
    extends RMQConsumer[T] {

  def onDecodeFailure () : Future[RMQReply] =
    ???

  def onDeliveryFailure () : Future[RMQReply] =
    ???

  def onShutdown () : Future[RMQReply] =
    ???

  def onDelivery () : Future[RMQReply] =
    ???

}
