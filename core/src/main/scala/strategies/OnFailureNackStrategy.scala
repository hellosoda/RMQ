package com.hellosoda.rmq.strategies
import com.hellosoda.rmq._

class OnFailureNackStrategy (
  val requeue : Boolean)
    extends RMQConsumerStrategy {

  def createConsumer[T] (
    receiver : RMQConsumer.DeliveryReceiver[T]
  ) : RMQConsumer[T] =
    new OnFailureNackConsumer[T](
      requeue  = requeue)(
      receiver = receiver)
}

object OnFailureNackStrategy {
  def apply (requeue : Boolean) =
    new OnFailureNackStrategy(
      requeue = requeue)
}
