package com.hellosoda.rmq

case class RMQRoutingKey (override val toString : String) extends AnyVal

object RMQRoutingKey {
  val empty = RMQRoutingKey("")
  val none = empty
}
