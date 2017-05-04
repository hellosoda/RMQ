package com.hellosoda.rmq.consumers
import com.hellosoda.rmq._
import scala.concurrent.ExecutionContext

class DefaultConsumer[T] (
  val channel     : RMQChannel,
  val consumerTag : RMQConsumerTag)(implicit
  val ec    : ExecutionContext,
  val codec : RMQCodec[T])
    extends RMQConsumer[T] {
}
