package com.hellosoda.rmq.benchmark
import com.hellosoda.rmq._
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicLong
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{
  Await,
  Future }
import scala.concurrent.duration._

object BenchmarkMain {
  def main (args: Array[String]) : Unit =
    new BenchmarkMain(Options.parse(args)).run()
}

class BenchmarkMain (
  val options : Options) {

  val connection = RMQConnection.open(options.connection)
  val queue = RMQQueue.Declare(
    name = scala.util.Random.alphanumeric.take(8).mkString(""),
    exclusive = false)
  val exchange = RMQExchange.Declare(
    name = scala.util.Random.alphanumeric.take(8).mkString(""),
    kind = RMQExchange.Direct,
    durable = false,
    autoDelete = true)
  val totalMessages = new AtomicLong(options.messageCount.toLong)

  val started = new CountDownLatch(1)
  val stopped = new CountDownLatch(1)

  implicit val strategy = RMQConsumerStrategy.OnFailureNack(false)
  import com.hellosoda.rmq.codecs.RMQDefaultCodecs._

  def run () : Unit = {
    Await.result(
      for {
        c <- connection.createChannelAsync()
        _ <- c.declareExchange(exchange)
        _ <- c.declareQueue(queue)
        _ <- c.bindQueue(queue, exchange)
      } yield (),
      Duration.Inf)

    val producers = for (_ <- Range(0, options.producerCount)) {
      new Thread {
        setDaemon(true)
        start()

        override def run () : Unit = {
          val c = connection.createChannel()
          if (options.publisherConfirms)
            c.enablePublisherConfirmsSync()
          started.await()
          while (stopped.getCount > 0 && totalMessages.get() > 0) {
            c.publish(exchange, RMQBasicProperties.default, "foo")
            totalMessages.decrementAndGet()
          }
        }
      }
    }

    val consumed = new AtomicLong(0L)

    val consumer = new Thread {
      setDaemon(true)
      start()

      override def run () : Unit = {
        val c = connection.createChannel()
        Await.result(c.setQos(1), Duration.Inf)
        val h = Await.result(c.consumeDeliveryAck[String](queue) {
          case RMQDelivery(_, _) =>
            Future.successful(consumed.incrementAndGet())
        }, Duration.Inf)
        stopped.await()
        h.close()
      }
    }

    val monitor = new Thread {
      start()
      override def run () : Unit = {
        var i = 1

        started.await()
        while (stopped.getCount > 0) {
          Thread.sleep((options.interval * 1000).toInt)

          val elapsed = options.interval * i
          val count = consumed.get()
          val rate = count.toDouble / elapsed

          println(f"${elapsed.toLong}% 5d ${count}% 5d $rate%s/s")
          i += 1
        }
      }
    }

    started.countDown()
  }
}
