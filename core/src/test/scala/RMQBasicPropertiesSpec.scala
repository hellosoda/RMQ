package com.hellosoda.rmq
import com.rabbitmq.client.AMQP
import org.scalatest._

class RMQBasicPropertiesSpec
    extends FlatSpec
    with    Matchers {

  behavior of "RMQBasicProperties"

  it should "successfully wrap an empty AMQP.BasicProperties" in {
    // It can return some boxed nulls that will subsequently end up
    // in Scala implicit conversions.
    val empty = new AMQP.BasicProperties.Builder().build()
    val props = RMQBasicProperties.fromBasicProperties(empty)
    props.contentType shouldBe None
  }

}
