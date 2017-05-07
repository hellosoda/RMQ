package com.hellosoda.rmq

sealed trait RMQReply
object RMQReply {
  /** basic.ack **/
  case object Ack extends RMQReply

  /** Cancel consumer **/
  case object Cancel extends RMQReply

  /** basic.nack **/
  case class Nack (val requeue : Boolean) extends RMQReply

  /** Shut down channel **/
  case class Shutdown (val reason : Option[Throwable]) extends RMQReply
}
