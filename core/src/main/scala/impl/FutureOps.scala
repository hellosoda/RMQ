package com.hellosoda.rmq.impl
import scala.util.Try
import scala.concurrent.Future

object FutureOps {

  def wrap[T] (f : => T) : Future[T] =
    Future.fromTry { Try { f }}

}

trait ToFutureOps {
  import scala.language.implicitConversions
  implicit def futureToFutureOpsCompanion (it : Future.type) : FutureOps.type =
    FutureOps
}
