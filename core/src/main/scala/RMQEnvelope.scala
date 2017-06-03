package com.hellosoda.rmq
import com.rabbitmq.client._

class RMQEnvelope (
  val underlying : Envelope) {

  def deliveryTag : RMQDeliveryTag =
    RMQDeliveryTag(underlying.getDeliveryTag)

  def exchange : String =
    underlying.getExchange

  def routingKey : RMQRoutingKey =
    RMQRoutingKey(underlying.getRoutingKey)

  def isRedeliver : Boolean =
    underlying.isRedeliver

}
