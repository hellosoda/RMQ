package com.hellosoda.rmq.codecs
import com.hellosoda.rmq.RMQCodec

object RMQDefaultCodecs extends RMQDefaultCodecs
trait RMQDefaultCodecs {

  implicit val byteArrayRMQCodec : RMQCodec[Array[Byte]] =
    new RMQCodec[Array[Byte]] {
      val contentType = Some("application/octet-stream")
      def encode (value : Array[Byte]) = value
      def decode (array : Array[Byte]) = array
    }

  implicit val stringRMQCodec : RMQCodec[String] =
    new RMQCodec[String] {
      val contentType = Some("text/plain")
      def encode (value : String) = value.getBytes("utf-8")
      def decode (array : Array[Byte]) = new String(array, "utf-8")
    }

}
