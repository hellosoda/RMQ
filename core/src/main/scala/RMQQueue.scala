package com.hellosoda.rmq

sealed trait RMQQueue {
  def name : String
  def passive : RMQQueue.Passive
}

object RMQQueue {

  case class Passive (
    val name : String)
      extends RMQQueue {
    def passive = this
  }

  case class Declare (
    val name       : String  = "",
    val durable    : Boolean = false,
    val exclusive  : Boolean = true,
    val autoDelete : Boolean = true,
    val arguments  : Map[String, Any] = Map.empty)
      extends RMQQueue {
    def passive = Passive(name = name)
    def maxPriority (priority : Int) =
      copy(arguments = arguments + ("x-max-priority" -> priority))
  }

}
