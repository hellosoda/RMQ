package com.hellosoda.rmq

sealed trait RMQQueue {
  def name : String
  def toPassive : RMQQueue.Passive

  override def toString =
    name
}

object RMQQueue {

  case class Passive (
    val name : String)
      extends RMQQueue {

    def toPassive =
      this
  }

  case class Declare (
    val name       : String  = "",
    val durable    : Boolean = false,
    val exclusive  : Boolean = true,
    val autoDelete : Boolean = true,
    val arguments  : Map[String, Any] = Map.empty)
      extends RMQQueue {

    def toPassive =
      Passive(name = name)

    def maxPriority (priority : Int) =
      copy(arguments = arguments + ("x-max-priority" -> priority))
  }

}
