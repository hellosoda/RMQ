package com.hellosoda.rmq

case class RMQDelivery[T] (
  val message : RMQMessage[T],
  val body    : T)
