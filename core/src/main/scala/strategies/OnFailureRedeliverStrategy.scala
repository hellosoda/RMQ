package com.hellosoda.rmq.strategies
import com.hellosoda.rmq._
import com.hellosoda.rmq.consumers._

class OnFailureRedeliverStrategy (
  val maxAttempts : Int)
    extends RMQConsumerStrategy {

  def createConsumer[T] (
    receiver : RMQConsumer.DeliveryReceiver[T]
  ) : RMQConsumer[T] =
    new OnFailureRedeliverConsumer[T](
      maxAttempts = maxAttempts)(
      receiver    = receiver)
}

object OnFailureRedeliverStrategy {
  def apply (
    maxAttempts : Int
  ) : RMQConsumerStrategy =
    new OnFailureRedeliverStrategy(
      maxAttempts = maxAttempts)
}
