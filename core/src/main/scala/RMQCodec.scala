package com.hellosoda.rmq
import com.hellosoda.rmq.codecs.RMQDefaultCodecs

trait RMQCodec [T] {
  def contentType : Option[String]
  def encode (value : T) : Array[Byte]
  def decode (array : Array[Byte]) : T
}

object RMQCodec extends RMQDefaultCodecs
