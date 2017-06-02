package com.hellosoda.rmq.strategies
import com.hellosoda.rmq._
import com.hellosoda.rmq.consumers._

class OnFailureNackStrategy (
  val requeue : Boolean)
    extends RMQConsumerStrategy {

  def createConsumer[T] (
    receiver : RMQConsumer.DeliveryReceiver[T]
  ) : RMQConsumer[T] =
    new OnFailureNackConsumer[T](requeue = requeue) {
      override def receive (implicit ctx : RMQConsumerContext) = {
        case delivery: RMQDelivery[T] if receiver.isDefinedAt(delivery) =>
          receiver(delivery)
      }
    }
}

object OnFailureNackStrategy {
  def apply (requeue : Boolean) =
    new OnFailureNackStrategy(
      requeue = requeue)
}
