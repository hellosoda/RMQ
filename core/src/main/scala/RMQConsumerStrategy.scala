package com.hellosoda.rmq
import com.hellosoda.rmq.strategies._

trait RMQConsumerStrategy {
  def createConsumer[T] (
    receive : RMQConsumer.DeliveryReceiver[T]
  ) : RMQConsumer[T]
}

object RMQConsumerStrategy {

  type OnFailureNack = OnFailureNackStrategy
  val  OnFailureNack = OnFailureNackStrategy

  type OnFailureRedeliver = OnFailureRedeliverStrategy
  val  OnFailureRedeliver = OnFailureRedeliverStrategy

}
