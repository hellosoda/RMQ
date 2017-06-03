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

  def id : String

  def id_= (value : String) : Unit

  def isBlocked : Boolean

  def waitUnblocked () : Future[Unit]

}

object RMQConnection {

  case class ConnectOptions (
    val hosts : Seq[Address],
    val virtualHost : String,
    val username : String,
    val password : String,
    val options : Options)

  case class Options (
    val autoRecovery : Boolean,
    val connectionId : Option[String],
    val nio : Boolean,
    val ssl : Boolean)

  object Options {
    val default = Options(
      autoRecovery = true,
      connectionId = None,
      nio = true,
      ssl = false)
  }

  // TODO: Need more flexibility of passing options.

  private def connect (
    options : ConnectOptions
  ) : Connection = {
    val factory = new ConnectionFactory();

    factory.setUsername(options.username)
    factory.setPassword(options.password)
    factory.setVirtualHost(options.virtualHost)
    factory.setAutomaticRecoveryEnabled(options.options.autoRecovery)
    if (options.options.nio) factory.useNio()

    factory.newConnection(options.hosts.toArray)
  }

  def parseURI (uri : URI) : Try[ConnectOptions] =
    Try { AMQPAddressParser.parseURI(uri) }

  def wrap (
    conn : Connection)(implicit
    ec   : ExecutionContext
  ) : RMQConnection =
    new RMQConnectionImpl(Try(conn))

  def open (
    uri     : URI,
    options : Options = Options.default)(implicit
    ec      : ExecutionContext
  ) : RMQConnection = {
    val connection = for {
      options <- parseURI(uri)
      conn    <- Try { connect(options) }
    } yield conn

    new RMQConnectionImpl(connection)
  }

  def open (
    options : ConnectOptions)(implicit
    ec      : ExecutionContext
  ) : RMQConnection = {
    val connection = Try { connect(options) }
    new RMQConnectionImpl(connection)
  }

}
