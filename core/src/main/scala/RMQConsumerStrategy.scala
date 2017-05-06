package com.hellosoda.rmq
import com.hellosoda.rmq.consumers._
import scala.concurrent.Future

trait RMQConsumerStrategy {
  def createConsumer[T] (
    channel  : RMQChannel,
    delivery : PartialFunction[RMQDelivery[T], Future[RMQReply]]
  ) : RMQConsumer[T]
}

object RMQConsumerStrategy {

  object OnFailureNack {
    def apply (requeue : Boolean = false) : RMQConsumerStrategy =
      new RMQConsumerStrategy {
        def createConsumer[T] (
          channel  : RMQChannel,
          delivery : PartialFunction[RMQDelivery[T], Future[RMQReply]]
        ) : RMQConsumer[T] =
          new OnFailureNackConsumer(
            channel  = channel,
            delivery = delivery,
            requeue  = requeue)
      }
  }

}
