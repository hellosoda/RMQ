package com.hellosoda.rmq

sealed abstract class RMQDeliveryMode private (val intValue : Int)
object RMQDeliveryMode {
  case class Custom (override val intValue : Int)
      extends RMQDeliveryMode(intValue)
  case object NonPersistent extends RMQDeliveryMode(1)
  case object Persistent extends RMQDeliveryMode(2)

  def fromInt (int : Int) : RMQDeliveryMode =
    int match {
      case 1 => NonPersistent
      case 2 => Persistent
      case _ => Custom(int)
    }

}
