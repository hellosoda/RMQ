package com.hellosoda.rmq.impl
import scala.util.Try
import scala.concurrent.Future

private[rmq] object FutureOps {

  val unit = Future.successful(())

  def wrap[T] (f : => T) : Future[T] =
    Future.fromTry { Try { f }}

}

private[rmq] trait ToFutureOps {
  import scala.language.implicitConversions
  implicit def futureToFutureOpsCompanion (it : Future.type) : FutureOps.type =
    FutureOps
}
