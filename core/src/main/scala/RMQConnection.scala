package com.hellosoda.rmq
import com.hellosoda.rmq.impl._
import com.rabbitmq.client._
import java.net.URI
import scala.concurrent.{
  ExecutionContext,
  Future }
import scala.util.Try

// TODO: Wrap as Try[Connection]?

class RMQConnection private (
  private val underlying : Try[Connection])(implicit
  private val ec         : ExecutionContext)
    extends java.io.Closeable {

  def connection : Connection =
    underlying.get

  def close () : Unit =
    underlying.foreach { _.close() }

  def createChannel () : RMQChannel =
    new RMQChannelImpl(Try(connection.createChannel()))

}

object RMQConnection {

  def from (
    conn : Connection)(implicit
    ec   : ExecutionContext
  ) : RMQConnection =
    new RMQConnection(Try(conn))

  def open (
    conn : URI)(implicit
    ec   : ExecutionContext
  ) : RMQConnection =
    ???

}
