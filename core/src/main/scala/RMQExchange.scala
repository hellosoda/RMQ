package com.hellosoda.rmq
import com.rabbitmq.client.BuiltinExchangeType

sealed trait RMQExchange {
  def name : String
  def toPassive : RMQExchange.Passive
}

object RMQExchange {

  val none = Passive("")

  case class Passive (
    val name : String)
      extends RMQExchange {

    def toPassive =
      this
  }

  case class Declare (
    val name       : String,
    val kind       : Kind,
    val durable    : Boolean = true,
    val autoDelete : Boolean = false,
    val arguments  : Map[String, Any] = Map.empty)
      extends RMQExchange {

    def toPassive =
      Passive(name = name)
  }

  sealed abstract class Kind private[RMQExchange] (val native: String) {
    override val toString = native
  }

  case object Direct extends Kind("direct")
  case object Fanout extends Kind("fanout")
  case object Headers extends Kind("headers")
  case object Topic extends Kind("topic")
  case class Custom(override val native : String) extends Kind(native)

}
