package com.hellosoda.rmq

case class RMQDelivery[T] (
  val message : RMQMessage,
  val body    : T)
