package com.hellosoda.rmq.impl
import com.hellosoda.rmq._
import com.rabbitmq.client._
import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.{
  ExecutionContext,
  Future,
  Promise }
import scala.util.Try

class RMQConnectionImpl (
  private val underlying : Try[Connection])(implicit
  private val ec         : ExecutionContext)
    extends RMQConnection {

  private val connectionBlocked =
    new AtomicReference[Promise[Unit]](null)

  private val preparedConnection = for {
    conn <- underlying
    _    =  conn.addBlockedListener(new BlockedListenerImpl(connectionBlocked))
  } yield conn

  def connection : Connection =
    preparedConnection.get

  def close () : Unit =
    preparedConnection.foreach { _.close() }

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
