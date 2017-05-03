package com.hellosoda.rmq.codecs
import com.hellosoda.rmq.RMQCodec
import play.api.libs.json._

object RMQPlayJsonCodecs extends RMQPlayJsonCodecs
trait RMQPlayJsonCodecs {

  implicit def playJsonFormatRMQCodec[T] (implicit
    fmt : Format[T]
  ) : RMQCodec[T] = new RMQCodec[T] {
    val contentType = Some("application/json")

    def encode (value : T) =
      Json.stringify(fmt.writes(value)).getBytes("utf-8")

    def decode (array : Array[Byte]) =
      Json.parse(array).as(fmt)
  }

}
