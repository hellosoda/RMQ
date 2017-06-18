package com.hellosoda.rmq.impl
import java.net.URI
import org.scalatest._

class AMQPAddressParserSpec
    extends FlatSpec
    with    Matchers {

  it should "parse a composite URI" in {

    val options = AMQPAddressParser.parseURI(new URI(
      "amqp://username:password@10.30.120.36,10.30.120.68/"))

    options.hosts.map(_.getHost) should contain theSameElementsAs Seq(
      "10.30.120.36", "10.30.120.68")
    options.hosts.map(_.getPort).toSet shouldBe Set(5672)
    options.username shouldBe "username"
    options.password shouldBe "password"
  }

}
