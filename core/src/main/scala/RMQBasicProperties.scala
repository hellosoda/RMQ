package com.hellosoda.rmq
import com.rabbitmq.client.AMQP
import java.time.{
  Instant,
  LocalDateTime,
  ZoneId,
  ZoneOffset }
import java.util.Date
import scala.collection.JavaConverters._
import scala.concurrent.duration._

/** Scala-native access to the RMQ properties.
  *
  * The underlying ''com.rabbitmq.client.AMQP.BasicProperties'' is
  * generated automatically in "codegen.py" of the Java RabbitMQ client.
  */
case class RMQBasicProperties (
  val appId : Option[String] = None,
  val clusterId : Option[String] = None,
  val contentEncoding : Option[String] = None,
  val contentType : Option[String] = None,
  val correlationId : Option[String] = None,
  val deliveryMode : Option[RMQDeliveryMode] = None,
  val expiration : Option[Duration] = None,
  val headers : Map[String, Any] = Map.empty,
  val messageId : Option[String] = None,
  val priority : Option[Int] = None,
  val replyTo : Option[String] = None,
  val timestamp : Option[LocalDateTime] = None,
  val `type` : Option[String] = None,
  val userId : Option[String] = None) {

  def mapHeaders (f : (Map[String, Any]) => Map[String, Any]) =
    copy(headers = f(headers))

  def asBuilder : AMQP.BasicProperties.Builder =
    new AMQP.BasicProperties.Builder().
      appId(appId getOrElse null).
      clusterId(clusterId getOrElse null).
      contentEncoding(contentEncoding getOrElse null).
      contentType(contentType getOrElse null).
      correlationId(correlationId getOrElse null).
      deliveryMode(
        deliveryMode.map(_.intValue.asInstanceOf[Integer]) getOrElse null).
      expiration(expiration.map(_.toMillis.toString) getOrElse null).
      headers(
        headers.mapValues { value =>
          value.asInstanceOf[AnyRef].asInstanceOf[Object] }.
        asJava).
      messageId(messageId getOrElse null).
      priority(priority.map(_.asInstanceOf[Integer]) getOrElse null).
      replyTo(replyTo getOrElse null).
      timestamp(
        timestamp.map { localDateTime =>
          new Date(localDateTime.toInstant(ZoneOffset.UTC).toEpochMilli)
        }.getOrElse(null)).
      `type`(`type` getOrElse null).
      userId(userId getOrElse null)

  def asBasicProperties : AMQP.BasicProperties =
    asBuilder.build()

}

object RMQBasicProperties {

  val default = RMQBasicProperties()

  def fromBasicProperties (props : AMQP.BasicProperties) : RMQBasicProperties =
    RMQBasicProperties(
      appId = Option(props.getAppId),
      clusterId = Option(props.getClusterId),
      contentEncoding = Option(props.getContentEncoding),
      contentType = Option(props.getContentType),
      correlationId = Option(props.getCorrelationId),
      deliveryMode = Option(
        props.getDeliveryMode).
        map { integer => RMQDeliveryMode.fromInt(integer) },
      expiration = Option(props.getExpiration).map(_.toLong.millis),
      headers = Option(
        props.getHeaders).
        // TODO: The unboxing isn't ideal.
        map { _.asScala.mapValues { _.asInstanceOf[Any] }.toMap }.
        getOrElse(Map.empty),
      messageId = Option(props.getMessageId),
      // At Option construction time, Scala would trip up here by attempting
      // to implicitly unbox the nullable Integer.
      priority =
        if (props.getPriority == null) None else Option(props.getPriority),
      replyTo = Option(props.getReplyTo),
      timestamp = Option(props.getTimestamp).map { date =>
        LocalDateTime.ofInstant(
          Instant.ofEpochMilli(date.getTime),
          ZoneId.of("UTC")) },
      `type` = Option(props.getType),
      userId = Option(props.getUserId))

}
