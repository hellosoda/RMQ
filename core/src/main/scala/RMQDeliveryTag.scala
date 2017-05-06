package com.hellosoda.rmq

case class RMQDeliveryTag (val value : Long) extends AnyVal {
  override def toString : String =
    value.toString
}
