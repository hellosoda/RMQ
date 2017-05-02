package com.hellosoda.rmq
import com.hellosoda.rmq.impl._
import com.rabbitmq.client._
import java.net.URI
import scala.concurrent.{
  ExecutionContext,
  Future }

class RMQConnection (
  val connection : Connection)(implicit
  val ec         : ExecutionContext)
    extends java.io.Closeable {

  def close () : Unit =
    connection.close()

  def createChannel () : Future[RMQChannel] =
    Future.wrap { new RMQChannel(connection.createChannel()) }

}
