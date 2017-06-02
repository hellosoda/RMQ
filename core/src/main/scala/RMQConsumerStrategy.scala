package com.hellosoda.rmq
import com.hellosoda.rmq.strategies._
import scala.concurrent.Future

trait RMQConsumerStrategy {
  def createConsumer[T] (
    receive : RMQConsumer.Receive[T]
  ) : RMQConsumer[T]
}

object RMQConsumerStrategy {

  type OnFailureNack = OnFailureNackStrategy
  val  OnFailureNack = OnFailureNackStrategy

  type OnFailureRedeliver = OnFailureRedeliver
  val  OnFailureRedeliver = OnFailureRedeliver

}
