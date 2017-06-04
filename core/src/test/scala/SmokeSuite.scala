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

  import com.hellosoda.rmq.codecs.RMQDefaultCodecs._

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
    autoDelete = false)

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

  it should "enable the consumption of messages (ConsumerStrategy)" in {
    implicit val strategy = RMQConsumerStrategy.OnFailureNack(false)

    val promise = Promise[String]()
    val handle = recvChan.consumeDeliveryAck[String](queue) {
      case RMQDelivery(_, string) =>
        Future.successful(promise.success(string))
    }.futureValue

    val content = "Greetings, programs!"
    recvChan.consumerCount(queue).futureValue shouldBe 1
    promise.future.futureValue shouldBe content
    recvChan.messageCount(queue).futureValue shouldBe 0

    And("when the handle is closed, the consumer exits")
    handle.close()
    recvChan.consumerCount(queue).futureValue shouldBe 0
  }

  it should "enable the consumption of messages (custom Consumer)" in {
    val promise = Promise[String]()

    object consumer extends RMQConsumer.OnFailureNack[String](false) {
      override def receive (implicit ctx : RMQConsumerContext) = {
        case RMQDelivery(_, string) =>
          promise.success(string)
          Future.successful(RMQReply.Ack)
      }
    }

    val content = "A vast, complex system!"
    recvChan.consumerCount(queue).futureValue shouldBe 0
    val handle = recvChan.consume(queue, consumer).futureValue
    recvChan.consumerCount(queue).futureValue shouldBe 1
    sendChan.publish(queue, RMQBasicProperties.default, content).futureValue
    promise.future.futureValue shouldBe content

    And("when the handle is closed, the consumer exits")
    handle.close()
    recvChan.consumerCount(queue).futureValue shouldBe 0
  }

}
