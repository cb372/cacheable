package scalacache.redis

import redis.clients.jedis._
import scalacache._

import scala.language.{higherKinds, postfixOps}

class RedisCacheSpec extends RedisCacheSpecBase with RedisTestUtil {

  override type JClient = Jedis
  override type JPool = JedisPool

  override def withJedis = assumingRedisIsRunning

  override def constructCache[F[_]: Async](pool: JPool): CacheAlg[F] =
    RedisCache[F](pool)

  override def flushRedis(client: JClient): Unit = client.flushDB()

  runTestsIfPossible()

}