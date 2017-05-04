package com.hellosoda.rmq.impl
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.{
  ExecutionContext,
  Future,
  Promise }

private[rmq] class AsyncMutex {
  private val queue = new ConcurrentLinkedQueue[Runnable]()
  private val queueSize = new AtomicInteger(0)

  def acquire [T] (f : => T)(implicit ec : ExecutionContext) : Future[T] = {
    val promise = Promise[T]()

    queue.add(new Runnable {
      def run () : Unit =
        Future(f).onComplete { any =>
          promise.complete(any)
          queue.poll()
          if (queueSize.decrementAndGet() > 0)
            queue.peek().run()
        }
    })

    if (queueSize.getAndIncrement() == 0) {
      queue.peek().run()
    }

    promise.future
  }

}
