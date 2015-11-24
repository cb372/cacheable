package scalacache.redis

import scalacache.{ LoggingSupport, Cache }
import scala.concurrent.duration._
import com.typesafe.scalalogging.StrictLogging
import redis.clients.jedis.{ JedisPool, Jedis }
import scala.concurrent.{ Future, ExecutionContext, blocking }

/**
 * Thin wrapper around Jedis
 * @param customClassloader a classloader to use when deserializing objects from the cache.
 *                          If you are using Play, you should pass in `app.classloader`.
 */
class RedisCache(jedisPool: JedisPool, override val customClassloader: Option[ClassLoader] = None)(implicit execContext: ExecutionContext = ExecutionContext.global)
    extends Cache
    with RedisSerialization
    with LoggingSupport
    with StrictLogging {

  import scalacache.redis.Implicits.StringWithUtf8Bytes

  /**
   * Get the value corresponding to the given key from the cache
   * @param key cache key
   * @tparam V the type of the corresponding value
   * @return the value, if there is one
   */
  override def get[V](key: String) = Future {
    blocking {
      withJedisClient { client =>
        val resultBytes = Option(client.get(key.utf8bytes))
        val result = resultBytes.map(deserialize[V])
        logCacheHitOrMiss(key, result)
        result
      }
    }
  }

  /**
   * Insert the given key-value pair into the cache, with an optional Time To Live.
   * @param key cache key
   * @param value corresponding value
   * @param ttl Time To Live
   * @tparam V the type of the corresponding value
   */
  override def put[V](key: String, value: V, ttl: Option[Duration]) = Future {
    blocking {
      withJedisClient { client =>
        val keyBytes = key.utf8bytes
        val valueBytes = serialize(value)
        ttl match {
          case None => client.set(keyBytes, valueBytes)
          case Some(Duration.Zero) => client.set(keyBytes, valueBytes)
          case Some(d) if d < 1.second => {
            logger.warn("Because Redis (pre 2.6.12) does not support sub-second expiry, TTL of $d will be rounded up to 1 second")
            client.setex(keyBytes, 1, valueBytes)
          }
          case Some(d) => client.setex(keyBytes, d.toSeconds.toInt, valueBytes)
        }
      }
    }
  }

  /**
   * Remove the given key and its associated value from the cache, if it exists.
   * If the key is not in the cache, do nothing.
   * @param key cache key
   */
  override def remove(key: String) = Future {
    blocking {
      withJedisClient { client =>
        client.del(key.utf8bytes)
      }
    }
  }

  override def removeAll() = Future {
    blocking {
      withJedisClient { client =>
        client.flushDB()
      }
    }
  }

  override def close(): Unit = {
    jedisPool.close()
  }

  private def withJedisClient[T](f: Jedis => T): T = {
    val jedis = jedisPool.getResource()
    try {
      f(jedis)
    } finally {
      jedis.close()
    }
  }

}

object RedisCache {

  /**
   * Create a Redis client connecting to the given host and use it for caching
   */
  def apply(host: String, port: Int): RedisCache = apply(new JedisPool(host, port))

  /**
   * Create a cache that uses the given Jedis client pool
   * @param jedisPool a Jedis pool
   * @param customClassloader a classloader to use when deserializing objects from the cache.
   *                          If you are using Play, you should pass in `app.classloader`.
   */
  def apply(jedisPool: JedisPool, customClassloader: Option[ClassLoader] = None): RedisCache =
    new RedisCache(jedisPool, customClassloader)
}

