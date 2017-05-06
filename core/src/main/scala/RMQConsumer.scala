package com.hellosoda.rmq
import com.rabbitmq.client._
import scala.concurrent.Future

trait RMQConsumer[T] extends java.io.Closeable {

  final def close () : Unit =
    cancel()

  def cancel () : Unit =
    channel.cancelConsumer(consumerTag)

  def channel : RMQChannel

  def consumerTag : RMQConsumerTag

  def onCancel () : Future[Unit]

  def onDecodeFailure (message : RMQMessage) : Future[RMQReply]

  def onDelivery (delivery : RMQDelivery[T]) : Future[RMQReply]

  def onDeliveryFailure (delivery : RMQDelivery[T]) : Future[RMQReply]

  def onRecover () : Future[Unit]

  def onShutdown (signal : ShutdownSignalException) : Future[Unit]

}
