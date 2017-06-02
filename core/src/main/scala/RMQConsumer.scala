package com.hellosoda.rmq
import com.hellosoda.rmq.consumers._
import com.rabbitmq.client._
import scala.concurrent.Future

trait RMQConsumer[T] {

  def fallback (
    event : RMQEvent[T])(implicit
    ctx   : RMQConsumerContext
  ) : Future[RMQReply]

  def receive (implicit
    ctx : RMQConsumerContext
  ) : RMQConsumer.EventReceiver[T]

}

object RMQConsumer {

  type DeliveryReceiver[T] = PartialFunction[RMQDelivery[T], Future[RMQReply]]
  type EventReceiver[T]    = PartialFunction[RMQEvent[T], Future[RMQReply]]

  type OnFailureNack[T] = OnFailureNackConsumer[T]
  val  OnFailureNack    = OnFailureNackConsumer

  type OnFailureRedeliver[T] = OnFailureNackRedeliver[T]
  val  OnFailureRedeliver    = OnFailureNackRedeliver

}
