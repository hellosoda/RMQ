package com.hellosoda.rmq

class RMQConsumerHandle[T] (
  val consumerTag : RMQConsumerTag,
  val channel     : RMQChannel,
  val consumer    : RMQConsumer[T])
    extends java.io.Closeable {

  final def close () : Unit =
    cancel()

  def cancel () : Unit =
    channel.cancelConsumer(consumerTag)
}
