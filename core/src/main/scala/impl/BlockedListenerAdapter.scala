package com.hellosoda.rmq.impl
import com.rabbitmq.client._
import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.Promise

private[impl] class BlockedListenerAdapter (
  private val connectionId : String,
  private val reference : AtomicReference[Promise[Unit]])
    extends BlockedListener
    with    com.typesafe.scalalogging.LazyLogging {

  def handleBlocked(reason : String) : Unit = {
    logger.debug(s"handleBlocked: reason='$reason' connection=$connectionId")
    reference.compareAndSet(null, Promise[Unit]())
  }

  def handleUnblocked() : Unit = {
    logger.debug(s"handleUnblocked: connection=$connectionId")
    reference.getAndSet(null).success(Unit)
  }

}
