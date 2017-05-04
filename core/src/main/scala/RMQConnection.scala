package com.hellosoda.rmq
import com.hellosoda.rmq.impl._
import com.rabbitmq.client._
import java.net.URI
import scala.concurrent.{
  ExecutionContext,
  Future }
import scala.util.Try

trait RMQConnection extends java.io.Closeable {

  /** Return the underlying [[com.rabbitmq.client.Connection]]
    *
    * **Public API for enrichment purposes.** Raise an exception if
    * an error occured when constructing the Connection.
    */
  def connection : Connection

  /** Close underlying resources. **/
  def close () : Unit

  def createChannel () : RMQChannel

  def isBlocked : Boolean

  def waitUnblocked () : Future[Unit]

}

object RMQConnection {

  def fromConnection (
    conn : Connection)(implicit
    ec   : ExecutionContext
  ) : RMQConnection =
    new RMQConnectionImpl(Try(conn))

  def open (
    conn : URI)(implicit
    ec   : ExecutionContext
  ) : RMQConnection = {
    val connection = Try {
      val (factory, addrs) = AMQPAddressParser.parseURI(conn)
      factory.newConnection(addrs)
    }

    new RMQConnectionImpl(connection)
  }

}
