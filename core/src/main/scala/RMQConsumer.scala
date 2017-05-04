package com.hellosoda.rmq
import scala.concurrent.Future

trait RMQConsumer[T] {

  def onDecodeFailure () : Future[RMQReply]

  def onDeliveryFailure () : Future[RMQReply]

  def onShutdown () : Future[RMQReply]

  def onDelivery () : Future[RMQReply]

}
