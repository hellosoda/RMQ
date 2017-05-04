package com.hellosoda.rmq.impl
import com.hellosoda.rmq._
import com.rabbitmq.client._

class ConsumerAdapter (
  private val channel  : RMQChannel,
  private val consumer : RMQConsumer[_])
    extends com.rabbitmq.client.DefaultConsumer(channel.channel) {

}
