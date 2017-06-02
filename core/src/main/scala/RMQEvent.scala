package com.hellosoda.rmq

sealed trait RMQEvent[T]

object RMQEvent {

  case class OnCancel (
    val reason : Option[Throwable])
      extends RMQEvent[Nothing]

  case class OnDecodeFailure (
    val message : RMQMessage,
    val reason  : Throwable)
      extends RMQEvent[Nothing]

  type OnDelivery[T] = RMQDelivery[T]
  val  OnDelivery    = RMQDelivery

  case class OnDeliveryFailure (
    val message : RMQMessage,
    val reason  : Throwable)
      extends RMQEvent[Nothing]

  case class OnRecover ()
      extends RMQEvent[Nothing]

  case class OnShutdown (
    val signal : ShutdownSignalException)
      extends RMQEvent[Nothing]

}

case class RMQDelivery[T] (
  val message : RMQMessage,
  val body    : T)
    extends RMQEvent[T]
