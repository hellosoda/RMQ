package com.hellosoda.rmq.impl
import com.hellosoda.rmq._
import com.rabbitmq.client._
import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.{
  ExecutionContext,
  Future,
  Promise }
import scala.util.{
  Failure,
  Success,
  Try }

class RMQConnectionImpl (
  private val underlying : Try[Connection])(implicit
  private val ec         : ExecutionContext)
    extends RMQConnection
    with    com.typesafe.scalalogging.LazyLogging {

  private val connectionBlocked =
    new AtomicReference[Promise[Unit]](null)

  private val preparedConnection = for {
    conn <- underlying
    _ = if (conn.getId == null) conn.setId(java.util.UUID.randomUUID.toString)
    _ = conn.addBlockedListener(
      new BlockedListenerAdapter(
        connectionId = conn.getId,
        reference    = connectionBlocked))
  } yield conn

  preparedConnection match {
    case Failure(error) =>
      logger.error("Connection open failure", error)

    case Success(connection) =>
      logger.debug(s"connectionOpen: address=${connection.getAddress} id=${connection.getId}")
  }

  def connection : Connection =
    preparedConnection.get

  def close () : Unit =
    preparedConnection.foreach { conn =>
      logger.debug(s"Connection close: id=${conn.getId}")
      conn.close()
    }

  def createChannel () : RMQChannel =
    new RMQChannelImpl(Try(connection.createChannel()), this)

  def createChannelAsync () : Future[RMQChannel] =
    Future.wrap { createChannel() }

  def id : String =
    preparedConnection.get.getId()

  def id_= (value : String) : Unit =
    preparedConnection.get.setId(value)

  def isBlocked : Boolean =
    connectionBlocked.get() != null

  def waitUnblocked () : Future[Unit] = {
    val promise = connectionBlocked.get()

    if (promise == null)
      Future.unit
    else
      promise.future
  }

}
