package com.hellosoda.rmq

sealed trait RMQReply
object RMQReply {
  case object Ack extends RMQReply
  case object Cancel extends RMQReply
  case class Nack (val requeue : Boolean) extends RMQReply
  case object Shutdown extends RMQReply
}
