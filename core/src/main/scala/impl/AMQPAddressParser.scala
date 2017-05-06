package com.hellosoda.rmq.impl
import com.rabbitmq.client._
import java.net.URI

object AMQPAddressParser {

  import ConnectionFactory._

  private val authorityRe = raw"^(?:([^:]+):([^@]+)@)?([^,]+)(?:,([^,]+))*$$".r

  def parseURI (uri : URI) : (ConnectionFactory, Array[Address]) = {

    if (uri.getScheme != "amqp")
      throw new IllegalArgumentException(
        "Invalid AMQP URI: protocol is not \"amqp\"")

    def parse(hosts: Seq[String]) : Array[Address] = for {
      host <- hosts.toArray
      if host != null
      out  =  host.split("[:]") match {
        case Array(h)    => new Address(h, DEFAULT_AMQP_PORT)
        case Array(h, p) => new Address(h, p.toInt)
        case _           => throw new IllegalArgumentException(
          "Invalid AMQP URI")
      }
    } yield out

    val (user, pass, hosts) = uri.getAuthority match {
      case authorityRe(null, null, hs @ _*) =>
        (DEFAULT_USER, DEFAULT_PASS, parse(hs))

      case authorityRe(u, p, hs @ _*) =>
        (u, p, parse(hs))

      case _ =>
        throw new IllegalArgumentException(
          "Invalid AMQP URI: " ++
          "authority not of the form [user:pass@]host[,host...]")
    }

    val vhost = Option(uri.getPath).filter(_.nonEmpty)

    if (vhost.count(_ == '/') > 1)
      throw new IllegalArgumentException(
        "Invalid AMQP URI: invalid vhost")

    val qry = {
      for {
        string <- Option(uri.getQuery).getOrElse("").split("[&]")
        if string.nonEmpty
        Array(a, b) = string.split("[=]", 2)
        key   = java.net.URLDecoder.decode(a, "utf-8")
        value = java.net.URLDecoder.decode(b, "utf-8")
      } yield key -> value
    }.toMap

    def asBoolean (str : String) =
      str.toLowerCase match {
        case "true" | "1" | "y" | "yes" => true
        case "false" | "0" | "n" | "no" => false
      }

    val withSsl = qry.get("ssl").map(asBoolean)
    val autoRecovery = qry.get("recovery").map(asBoolean)
    val withNio = qry.get("nio").map(asBoolean).orElse(Some(true))

    val factory = new ConnectionFactory()

    factory.setUsername(user)
    factory.setPassword(pass)

    vhost.foreach { vhost =>
      factory.setVirtualHost(vhost)
    }

    autoRecovery.foreach {
      factory.setAutomaticRecoveryEnabled(_)
    }

    withNio.foreach { bool =>
      if (bool)
        factory.useNio()
    }

    (factory, hosts)
  }

}
