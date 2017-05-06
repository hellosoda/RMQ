package com.hellosoda.rmq
import scala.concurrent.Future

trait RMQConsumerStrategy {
  def newConsumer[T] (
    delivery : PartialFunction[T, Future[RMQReply]]
  ) : RMQConsumer[T]
}

object RMQConsumerStrategy {

  case class OnFailureNack (
    val requeue : Boolean = false)
      extends RMQConsumerStrategy {
    def newConsumer[T] (delivery : PartialFunction[T, Future[RMQReply]]) =
      new OnFailureNackConsumer[T](
        requeue  = requeue,
        delivery = delivery)
  }

}
