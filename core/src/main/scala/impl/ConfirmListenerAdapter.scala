package com.hellosoda.rmq.impl
import com.rabbitmq.client._
import java.util.concurrent.ConcurrentLinkedQueue
import scala.concurrent.Promise

private[rmq] class ConfirmListenerAdapter (
  val confirms : ConcurrentLinkedQueue[PublisherConfirm])
    extends ConfirmListener {

  private def completeWith (
    deliveryTag : Long,
    multiple    : Boolean)(
    action      : (Promise[Unit]) => Unit
  ) : Unit =
    if (!multiple) {
      val iter = confirms.iterator()
      while (iter.hasNext) {
        val pc = iter.next()
        if (pc.seqNo == deliveryTag) {
          confirms.remove(pc)
          action(pc.promise)
          return
        }
      }
    } else {
      val iter = confirms.iterator()
      while (iter.hasNext) {
        val pc = iter.next()

        if (pc.seqNo > deliveryTag)
          return

        confirms.remove(pc)
        action(pc.promise)
      }
    }

  override def handleAck (
    deliveryTag : Long,
    multiple    : Boolean
  ) : Unit =
    completeWith(deliveryTag, multiple)(_.success(()))

  override def handleNack (
    deliveryTag : Long,
    multiple    : Boolean
  ) : Unit =
    completeWith(deliveryTag, multiple)(_.failure(
      new java.io.IOException("NACK")))

}
