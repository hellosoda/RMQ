package com.hellosoda.rmq.impl
import com.hellosoda.rmq.RMQConnection
import com.rabbitmq.client._
import java.net.URI

object AMQPAddressParser {

  import ConnectionFactory._

  private val authorityRe = raw"^(?:([^:]+):([^@]+)@)?([^,]+)(?:,([^,]+))*$$".r

  def parseURI (uri : URI) : RMQConnection.ConnectOptions = {

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

    val options = RMQConnection.Options(
      autoRecovery = qry.get("recovery").map(asBoolean).getOrElse(
        RMQConnection.Options.default.autoRecovery),
      connectionId = qry.get("id"),
      nio = qry.get("nio").map(asBoolean).getOrElse(
        RMQConnection.Options.default.nio),
      ssl = qry.get("ssl").map(asBoolean).getOrElse(
        RMQConnection.Options.default.ssl))

    val connectOptions = RMQConnection.ConnectOptions(
      hosts       = hosts,
      virtualHost = vhost.getOrElse(DEFAULT_VHOST),
      username    = user,
      password    = pass,
      options     = options)

    connectOptions
  }

}
