package com.hellosoda.rmq
import java.io.IOException
import org.scalatest._
import org.scalatest.concurrent._
import scala.concurrent.ExecutionContext.Implicits.global

class SmokeSuite
    extends FlatSpec
    with    ConnectionFixture
    with    GivenWhenThen
    with    Matchers
    with    ScalaFutures {

  behavior of "fundamental RabbitMQ operations"

  val prefix   = s"${suiteName}.${randomString()}"
  val conn     = openConnection()
  val sendChan = conn.createChannel()
  val recvChan = conn.createChannel()

  val exchange = RMQExchange.Declare(
    name       = s"${prefix}.exchange",
    kind       = RMQExchange.Topic,
    durable    = false,
    autoDelete = true)

  val queue = RMQQueue.Declare(
    name       = s"${prefix}.queue",
    durable    = false,
    exclusive  = true,
    autoDelete = true)

  it should "permit exchange declarations" in {
    // XXX: This crashes the channel.
    /*
    Given("a passive, undeclared exchange, an error should be thrown")
    val error = sendChan.declareExchange(exchange.toPassive).failed.futureValue
    error shouldBe a[IOException]
     */

    Given("an exchange declaration, an exchange should be declared")
    sendChan.declareExchange(exchange).futureValue

    And("the passive declaration now succeeds")
    sendChan.declareExchange(exchange.toPassive).futureValue
  }

  it should "permit queue declarations" in {
    Given("a queue declaration, a queue should be declared")
    sendChan.declareQueue(queue).futureValue

    And("the passive declaration now succeeds")
    sendChan.declareQueue(queue.toPassive).futureValue
  }

  it should "enable the binding of queues to exchanges" in {
    sendChan.bindQueue(queue, exchange, RMQRoutingKey("test")).futureValue
    sendChan.consumerCount(queue).futureValue shouldBe 0
    sendChan.messageCount(queue).futureValue shouldBe 0
  }

}
