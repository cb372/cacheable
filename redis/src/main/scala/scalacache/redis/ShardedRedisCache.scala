package scalacache.redis

import redis.clients.jedis._
import scala.concurrent.{ Future, ExecutionContext, blocking }
import scala.collection.JavaConverters._

class ShardedRedisCache(val jedisPool: ShardedJedisPool,
                        override val customClassloader: Option[ClassLoader] = None,
                        override val useLegacySerialization: Boolean = false)(implicit val execContext: ExecutionContext = ExecutionContext.global)
    extends RedisCacheBase {

  type JClient = ShardedJedis

  override def removeAll() = Future {
    blocking {
      val jedis = jedisPool.getResource()
      try {
        jedis.getAllShards.asScala.foreach(_.flushDB())
      } finally {
        jedis.close()
      }
    }
  }

}

object ShardedRedisCache {

  /**
   * Create a sharded Redis client connecting to the given hosts and use it for caching
   */
  def apply(hosts: (String, Int)*): ShardedRedisCache = {
    val shards = hosts.map { case (host, port) => new JedisShardInfo(host, port) }
    val pool = new ShardedJedisPool(new JedisPoolConfig(), shards.asJava)
    apply(pool)
  }

  /**
   * Create a cache that uses the given ShardedJedis client pool
   * @param jedisPool a ShardedJedis pool
   * @param customClassloader a classloader to use when deserializing objects from the cache.
   *                          If you are using Play, you should pass in `app.classloader`.
   */
  def apply(jedisPool: ShardedJedisPool, customClassloader: Option[ClassLoader] = None): ShardedRedisCache =
    new ShardedRedisCache(jedisPool, customClassloader)

}
