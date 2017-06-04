package com.hellosoda.rmq.codecs
import com.hellosoda.rmq.RMQCodec
import play.api.libs.json._

object RMQPlayJsonCodecs extends RMQPlayJsonCodecs
trait RMQPlayJsonCodecs {

  import RMQDefaultCodecs.stringRMQCodec

  implicit def playJsonFormatRMQEncoder[T] (implicit
    writes : Writes[T]
  ) : RMQCodec.Encoder[T] = new RMQCodec.Encoder[T] {
    val contentType = Some("application/json")
    def encode (value : T) =
      stringRMQCodec.encode(Json.stringify(writes.writes(value)))
  }

  implicit def playJsonFormatRMQDecoder[T] (implicit
    reads : Reads[T]
  ) : RMQCodec.Decoder[T] = new RMQCodec.Decoder[T] {
    def decode (array : Array[Byte]) =
      Json.parse(stringRMQCodec.decode(array)).as(reads)
  }

}
