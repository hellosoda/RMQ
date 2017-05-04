package com.hellosoda.rmq
import java.net.URI
import org.scalatest._
import scala.concurrent.ExecutionContext.Implicits.global

trait ConnectionFixture {

  def randomString() =
    scala.util.Random.alphanumeric.take(4).mkString

  private val uri = new URI("amqp://localhost:5672")

  def openConnection() : RMQConnection =
    RMQConnection.open(uri)

}
