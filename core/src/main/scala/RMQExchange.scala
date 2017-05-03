package com.hellosoda.rmq
import com.rabbitmq.client.BuiltinExchangeType

sealed trait RMQExchange {
  def name : String
  def passive : RMQExchange.Passive
}

object RMQExchange {

  case class Passive (
    val name : String)
      extends RMQExchange {
    def passive = this
  }

  case class Declare (
    val name       : String,
    val kind       : Kind,
    val durable    : Boolean = true,
    val autoDelete : Boolean = false,
    val arguments  : Map[String, Object] = Map.empty)
      extends RMQExchange {
    def passive = Passive(name = name)
  }

  sealed abstract class Kind private[RMQExchange] (
    val underlying : BuiltinExchangeType)

  case object Direct extends Kind(BuiltinExchangeType.DIRECT)
  case object Fanout extends Kind(BuiltinExchangeType.FANOUT)
  case object Headers extends Kind(BuiltinExchangeType.HEADERS)
  case object Topic extends Kind(BuiltinExchangeType.TOPIC)

}
