package com.hellosoda.rmq

case class RMQMessage (
  val consumerTag : RMQConsumerTag,
  val envelope    : RMQEnvelope,
  val properties  : RMQBasicProperties,
  val bytes       : Array[Byte])
