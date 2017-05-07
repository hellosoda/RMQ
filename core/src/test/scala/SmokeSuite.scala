package com.hellosoda.rmq
import org.scalatest._
import org.scalatest.concurrent._
import scala.concurrent.{
  Future,
  Promise }

class SmokeSuite
    extends FlatSpec
    with    ConnectionFixture
    with    GivenWhenThen
    with    Matchers
    with    ScalaFutures {

  implicit val patience = {
    import org.scalatest.time.Span._
    import scala.concurrent.duration._
    PatienceConfig(
      timeout  = 5.seconds,
      interval = 50.millis)
  }

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
    recvChan.declareExchange(exchange).futureValue

    And("the passive declaration now succeeds")
    recvChan.declareExchange(exchange.toPassive).futureValue
  }

  it should "permit queue declarations" in {
    Given("a queue declaration, a queue should be declared")
    recvChan.declareQueue(queue).futureValue

    And("the passive declaration now succeeds")
    recvChan.declareQueue(queue.toPassive).futureValue
  }

  it should "enable the binding of queues to exchanges" in {
    recvChan.bindQueue(queue, exchange, RMQRoutingKey("test")).futureValue
    recvChan.consumerCount(queue).futureValue shouldBe 0
    recvChan.messageCount(queue).futureValue shouldBe 0
  }

  it should "enable the publication of messages" in {
    sendChan.
      publish(queue, RMQBasicProperties.default, "Greetings, programs!").
      futureValue
    recvChan.messageCount(queue).futureValue shouldBe 1
  }

  it should "enable the consumption of messages" in {
    implicit val strategy = RMQConsumerStrategy.OnFailureNack(false)
    val promise = Promise[String]()
    val handle = recvChan.consumeAck[String](queue) {
      case RMQDelivery(_, string) =>
        Future.successful(promise.success(string))
    }.futureValue

    recvChan.consumerCount(queue).futureValue shouldBe 1
    promise.future.futureValue shouldBe "Greetings, programs!"
    recvChan.messageCount(queue).futureValue shouldBe 0
  }

}
