package com.hellosoda.rmq.impl
import com.rabbitmq.client._
import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.Promise

private[impl] class BlockedListenerAdapter (
  private val reference : AtomicReference[Promise[Unit]])
    extends BlockedListener {

  def handleBlocked(reason : String) : Unit =
    reference.compareAndSet(null, Promise[Unit]())

  def handleUnblocked() : Unit =
    reference.getAndSet(null).success(Unit)

}
