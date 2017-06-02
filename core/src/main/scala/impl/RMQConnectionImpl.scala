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

  underlying match {
    case Failure(error) =>
      logger.error("Connection open failure", error)

    case Success(connection) =>
      logger.debug(s"Connection open: address=${connection.getAddress} id=${connection.getId}")
  }

  private val connectionBlocked =
    new AtomicReference[Promise[Unit]](null)

  private val preparedConnection = for {
    conn <- underlying
    _    =  conn.addBlockedListener(
      new BlockedListenerAdapter(connectionBlocked))
  } yield conn

  def connection : Connection =
    preparedConnection.get

  def close () : Unit =
    preparedConnection.foreach { conn =>
      logger.debug(s"Connection close: id=${conn.getId}")
      conn.close()
    }

  def createChannel () : RMQChannel =
    new RMQChannelImpl(Try(connection.createChannel()), this)

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
