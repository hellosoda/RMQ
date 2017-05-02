package com.hellosoda.rmq
import com.rabbitmq.client._
import scala.concurrent.ExecutionContext

class RMQChannel (
  val channel : Channel)(implicit
  val ec      : ExecutionContext)
    extends java.io.Closeable {

  def close () : Unit =
    channel.close()

}
