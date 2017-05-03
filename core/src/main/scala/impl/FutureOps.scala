package com.hellosoda.rmq.impl
import scala.concurrent.{
  Await,
  ExecutionContext,
  Future,
  Promise }
import scala.concurrent.duration.Duration
import scala.util.Try

private[rmq] object FutureOps {

  val unit = Future.successful(())

  def wrap[T] (f : => T) : Future[T] =
    Future.fromTry { Try { f }}

  private lazy val scheduledPool =
    new java.util.concurrent.ScheduledThreadPoolExecutor(1)

  private class PromiseCompletingRunnable (
    val promise : Promise[Unit])
      extends Runnable {
    def run : Unit =
      promise.success(())
  }

  def delay [T] (delay : Duration) : Future[Unit] = {
    if (delay.length == 0)
      return FutureOps.unit

    require(delay.isFinite,   "Cannot schedule an indefinite Future!")
    require(delay.length > 0, "Cannot schedule with negative delay!")

    val promise = Promise[Unit]
    val runnable = new PromiseCompletingRunnable(promise)
    scheduledPool.schedule(runnable, delay.length, delay.unit)
    promise.future
  }

}

private[rmq] trait ToFutureOps {
  import scala.language.implicitConversions
  implicit def futureToFutureOpsCompanion (it : Future.type) : FutureOps.type =
    FutureOps
}
