/**
Open Bank Project - API
Copyright (C) 2011-2026, TESOBE GmbH.

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.

Email: contact@tesobe.com
TESOBE GmbH.
Osloer Strasse 16/17
Berlin 13359, Germany

This product includes software developed at
TESOBE (http://www.tesobe.com/)

  */

package code.signal

import code.api.cache.Redis
import code.util.Helper.MdcLoggable
import io.grpc.stub.StreamObserver
import redis.clients.jedis.{Jedis, JedisPubSub}

import java.util.concurrent.{ConcurrentHashMap, CopyOnWriteArrayList}
import scala.jdk.CollectionConverters._

/**
 * Redis pub/sub fan-out for signal channels: the live half of the design in
 * the Signal Channels glossary entry. Every publish (REST or gRPC) goes
 * through RedisMessaging.publishMessage, which stores the envelope in the
 * channel list and then PUBLISHes it on `obp_signal:<channel_name>`. This bus
 * holds one pattern subscription on `obp_signal:*` for the process and hands
 * each envelope to the gRPC Subscribe streams registered for that channel.
 *
 * Nothing is buffered: a subscriber that connects after a message was
 * published never sees it. Late joiners ask the other agents, or Fetch.
 *
 * Same shape and lifecycle contract as ChatEventBus: start() is a no-op once
 * running, and only the ObpGrpcServer instance that started it stops it.
 */
object SignalEventBus extends MdcLoggable {

  private val CHANNEL_PREFIX = "obp_signal:"

  /** The Redis pub/sub channel that carries live envelopes for one signal channel. */
  def redisChannel(channelName: String): String = CHANNEL_PREFIX + channelName

  // channel name -> gRPC bridges waiting on it
  private val observers = new ConcurrentHashMap[String, CopyOnWriteArrayList[StreamObserver[String]]]()

  @volatile private var subscriberThread: Thread = _
  @volatile private var subscriberJedis: Jedis = _
  @volatile private var pubSub: JedisPubSub = _
  @volatile private var running = false

  def subscribe(channelName: String, observer: StreamObserver[String]): Unit = {
    observers.computeIfAbsent(channelName, _ => new CopyOnWriteArrayList[StreamObserver[String]]())
    observers.get(channelName).add(observer)
    logger.info(s"SignalEventBus says: Observer subscribed to $channelName (total: ${observers.get(channelName).size})")
  }

  def unsubscribe(channelName: String, observer: StreamObserver[String]): Unit = {
    val list = observers.get(channelName)
    if (list != null) {
      list.remove(observer)
      logger.info(s"SignalEventBus says: Observer unsubscribed from $channelName (remaining: ${list.size})")
      if (list.isEmpty) observers.remove(channelName)
    }
  }

  /** How many gRPC streams are currently attached to a channel on this instance. */
  def subscriberCount(channelName: String): Int =
    Option(observers.get(channelName)).map(_.size).getOrElse(0)

  def start(): Unit = {
    if (running) return
    running = true

    pubSub = new JedisPubSub {
      override def onPMessage(pattern: String, channel: String, message: String): Unit = {
        val channelName = channel.stripPrefix(CHANNEL_PREFIX)
        val list = observers.get(channelName)
        if (list != null) {
          list.asScala.foreach { observer =>
            try {
              observer.synchronized {
                observer.onNext(message)
              }
            } catch {
              case e: Throwable =>
                logger.warn(s"SignalEventBus says: Failed to deliver on $channelName, removing observer: ${e.getMessage}")
                list.remove(observer)
            }
          }
        }
      }
    }

    subscriberThread = new Thread(() => {
      try {
        // Dedicated connection: a subscribing Jedis cannot be returned to the pool.
        subscriberJedis = Redis.newSubscriberConnection()
        logger.info(s"SignalEventBus says: Redis subscriber started, pattern-subscribing to ${CHANNEL_PREFIX}*")
        subscriberJedis.psubscribe(pubSub, s"${CHANNEL_PREFIX}*")
      } catch {
        case e: Throwable if running =>
          logger.error(s"SignalEventBus says: Redis subscriber thread died: ${e.getMessage}")
        case _: Throwable => // shutting down, ignore
      }
    }, "signal-event-bus-subscriber")
    subscriberThread.setDaemon(true)
    subscriberThread.start()

    logger.info("SignalEventBus says: Started")
  }

  /** Whether this bus is already subscribed, so a caller can tell whether it started it. */
  def isRunning: Boolean = running

  def stop(): Unit = {
    running = false
    try { if (pubSub != null) pubSub.punsubscribe() } catch { case _: Throwable => }
    try { if (subscriberJedis != null) subscriberJedis.close() } catch { case _: Throwable => }
    observers.clear()
    logger.info("SignalEventBus says: Stopped")
  }
}
