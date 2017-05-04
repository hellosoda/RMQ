package com.hellosoda.rmq
import com.rabbitmq.client._
import scala.concurrent.Future
import scala.concurrent.duration._

trait RMQChannel extends java.io.Closeable {

  def bindQueue (
    queue      : RMQQueue,
    exchange   : RMQExchange,
    routingKey : RMQRoutingKey
  ) : Future[Unit]

  /** Return the underlying [[com.rabbitmq.client.Channel]]
    *
    * **Public API for enrichment purposes.** Raise an exception if
    * an error occured when constructing the Channel.
    */
  def channel : Channel

  /** Acquire the internal mutex guarding calls to the underlying
    * [[com.rabbitmq.client.Channel]].
    *
    * **Public API for enrichment purposes.** The thunk `f` will be executed
    * on the internal execution context of the [[RMQChannel]]. There is only
    * one mutex per [[RMQChannel]] instance, which guarantees that only one
    * thunk is executing at any given time.
    *
    * Behaviour of the RMQChannel is undefined if the thunk `f` blocks or
    * otherwise never completes. A likely consequence is that all subsequent
    * RMQChannel calls will wait indefinitely for the lock to release.
    */
  def mutex[T] (f : => T) : Future[T]

  /** Close the underlying resource. */
  def close () : Unit

  def cancelConsumer (consumerTag : RMQConsumerTag) : Future[Unit]

  def consume[T] (
    queue    : RMQQueue,
    consumer : RMQConsumer[T]
  ) : Future[RMQConsumerHandle[T]]

  def consumerCount (queue : RMQQueue) : Future[Long]

  def declareExchange (exchange : RMQExchange) : Future[Unit]

  def declareQueue (queue : RMQQueue) : Future[Unit]

  def enablePublisherConfirms () : Future[Unit]

  def messageCount (queue : RMQQueue) : Future[Long]

  def publish [T] (
    exchange   : RMQExchange,
    routingKey : RMQRoutingKey,
    properties : RMQBasicProperties,
    body       : T)(implicit
    codec      : RMQCodec[T]
  ) : Future[Unit]

  def setQos (qos : Int) : Future[Unit]

  def txCommit () : Future[Unit]

  def txRollback () : Future[Unit]

  def txSelect () : Future[Unit]

  /** NO-OP: `publish` always awaits confirmation. **/
  def waitConfirms () : Future[Boolean]

  /** NO-OP: `publish` always awaits confirmation. **/
  def waitConfirms (timeout : Duration) : Future[Boolean]

}
