package com.hellosoda.rmq
import scala.concurrent.ExecutionContext

class RMQConsumerContext (
  val channel    : RMQChannel)(implicit
  val dispatcher : ExecutionContext)

object RMQConsumerContext {
  import scala.language.implicitConversions
  implicit def consumerContextToExecutionContext (
    ch : RMQConsumerContext
  ) : ExecutionContext =
    ch.dispatcher
}
