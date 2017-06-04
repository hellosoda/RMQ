package com.hellosoda.rmq

trait RMQCodec[T]
    extends RMQCodec.Encoder[T]
    with    RMQCodec.Decoder[T]

object RMQCodec {

  trait Encoder[T] {
    def contentType : Option[String]
    def encode (value : T) : Array[Byte]
  }

  trait Decoder[T] {
    def decode (array : Array[Byte]) : T
  }

}
