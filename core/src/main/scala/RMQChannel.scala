package com.hellosoda.rmq
import com.rabbitmq.client._
import scala.concurrent.Future

trait RMQChannel extends java.io.Closeable {

  def ack (
    deliveryTag : RMQDeliveryTag,
    multiple    : Boolean = false
  ) : Future[Unit]

  def bindQueue (
    queue      : RMQQueue,
    exchange   : RMQExchange,
    routingKey : RMQRoutingKey
  ) : Future[Unit]

  def bindQueue (
    queue      : RMQQueue,
    exchange   : RMQExchange
  ) : Future[Unit] =
    bindQueue(queue, exchange, RMQRoutingKey.none)

  /** Return the underlying ''com.rabbitmq.client.Channel''
    *
    * **Public API for enrichment purposes.** Raise an exception if
    * an error occured when constructing the Channel.
    */
  def channel : Channel

  /** Close the underlying resource. */
  def close () : Unit

  def close (
    code    : Int,
    message : String
  ) : Unit

  def cancelConsumer (consumerTag : RMQConsumerTag) : Future[Unit]

  /** Consume by attaching a ''com.rabbitmq.client.Consumer''. **/
  def consumeNative (
    queue  : RMQQueue,
    native : Consumer
  ) : Future[RMQConsumerTag]

  /** Consume by attaching the [[com.hellosoda.rmq.RMQConsumer]] wrapper. **/
  def consume[T] (
    queue    : RMQQueue,
    consumer : RMQConsumer[T])(implicit
    codec    : RMQCodec[T]
  ) : Future[RMQConsumerHandle[T]]

  /** Consume deliveries, with replies set by the user. **/
  def consumeDelivery[T] (
    queue    : RMQQueue)(
    delivery : RMQConsumer.DeliveryReceiver[T])(implicit
    codec    : RMQCodec[T],
    strategy : RMQConsumerStrategy
  ) : Future[RMQConsumerHandle[T]]

  /** Consume deliveries, discarding results and acking automatically. **/
  def consumeDeliveryAck[T] (
    queue    : RMQQueue)(
    delivery : PartialFunction[RMQDelivery[T], Future[_]])(implicit
    codec    : RMQCodec[T],
    strategy : RMQConsumerStrategy
  ) : Future[RMQConsumerHandle[T]]

  def consumerCount (queue : RMQQueue) : Future[Long]

  def declareExchange (exchange : RMQExchange) : Future[Unit]

  def declareQueue (queue : RMQQueue) : Future[Unit]

  def enablePublisherConfirms () : Future[Unit]

  def messageCount (queue : RMQQueue) : Future[Long]

  /** Acquire the internal mutex guarding calls to the underlying
    * ''com.rabbitmq.client.Channel''.
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

  def nack (
    deliveryTag : RMQDeliveryTag,
    multiple    : Boolean = false,
    requeue     : Boolean = false
  ) : Future[Unit]

  def publish [T] (
    queue      : RMQQueue,
    properties : RMQBasicProperties,
    body       : T)(implicit
    codec      : RMQCodec[T]
  ) : Future[Unit] =
    publish(
      exchange   = RMQExchange.none,
      routingKey = RMQRoutingKey(queue.name),
      properties = properties,
      body       = body)

  def publish [T] (
    exchange   : RMQExchange,
    routingKey : RMQRoutingKey,
    properties : RMQBasicProperties,
    body       : T)(implicit
    codec      : RMQCodec[T]
  ) : Future[Unit]

  /** Re-send all unacknowledged messages. **/
  def recover (requeue : Boolean = true) : Future[Unit]

  def setQos (qos : Int) : Future[Unit]

  def txCommit () : Future[Unit]

  def txRollback () : Future[Unit]

  def txSelect () : Future[Unit]

}
