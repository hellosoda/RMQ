package com.hellosoda.rmq.impl
import scala.concurrent.Promise

private[rmq] case class PublisherConfirm (
  val seqNo   : Long,
  val promise : Promise[Unit])
