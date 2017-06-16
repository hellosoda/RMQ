package com.hellosoda.rmq.benchmark
import java.net.URI

case class Options (
  val connection : URI = new URI("https://localhost/"),
  val consumerCount : Int = 1,
  val producerCount : Int = 1)

object Options {
  val parser = new scopt.OptionParser[Options]("rmq-benchmark") {
    head("rmq-benchmark")
    opt[URI]("connection").action { (x, o) => o.copy(connection = x) }
    opt[Int]("consumers").action { (x, o) => o.copy(consumerCount = x) }
    opt[Int]("producers").action { (x, o) => o.copy(producerCount = x) }
  }

  def parse (args: Array[String]) : Options =
    parser.parse(args, Options()) match {
      case Some(options) => options
      case None =>
        System.exit(1)
        ???
    }

}
