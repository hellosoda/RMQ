package com.hellosoda.rmq.impl
import com.hellosoda.rmq._
import com.rabbitmq.client._
import java.net.URI
import scala.concurrent.{
  ExecutionContext,
  Future }
import scala.util.Try

class RMQConnectionImpl (
  private val underlying : Try[Connection])(implicit
  private val ec         : ExecutionContext)
    extends RMQConnection {

  def connection : Connection =
    underlying.get

  def close () : Unit =
    underlying.foreach { _.close() }

  def createChannel () : RMQChannel =
    new RMQChannelImpl(Try(connection.createChannel()))

}
