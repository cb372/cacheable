---
layout: docs
title: Cache implementations
---

## Cache implementations

### Google Guava

SBT:

```
libraryDependencies += "com.github.cb372" %% "scalacache-guava" % "0.28.0"
```

Usage:

```tut:silent
import scalacache._
import scalacache.guava._

implicit val guavaCache: Cache[String] = GuavaCache[String]
```

This will build a Guava cache with all the default settings. If you want to customize your Guava cache, then build it yourself and pass it to `GuavaCache` like this:

```tut:silent
import scalacache._
import scalacache.guava._
import com.google.common.cache.CacheBuilder

val underlyingGuavaCache = CacheBuilder.newBuilder().maximumSize(10000L).build[String, Entry[String]]
implicit val guavaCache: Cache[String] = GuavaCache(underlyingGuavaCache)
```

Cache eviction is handled by the underlying guava cache. The ScalaCache API's will never return an expired item, but they will stay in Guava untill either the max elements or time based expiration occurs. To keep memory down consider using `expireAfterWrite` in the CacheBuilder.

### Memcached

SBT:

```
libraryDependencies += "com.github.cb372" %% "scalacache-memcached" % "0.28.0"
```

Usage:

```tut:silent
import scalacache._
import scalacache.memcached._
import scalacache.serialization.binary._

implicit val memcachedCache: Cache[String] = MemcachedCache("localhost:11211")
```

or provide your own Memcached client, like this:

```tut:silent
import scalacache._
import scalacache.memcached._
import scalacache.serialization.binary._
import net.spy.memcached._

val memcachedClient = new MemcachedClient(
  new BinaryConnectionFactory(), 
  AddrUtil.getAddresses("localhost:11211")
)
implicit val customisedMemcachedCache: Cache[String] = MemcachedCache(memcachedClient)
```

#### Keys

Memcached only accepts ASCII keys with length <= 250 characters (see the [spec](https://github.com/memcached/memcached/blob/1.4.20/doc/protocol.txt#L41) for more details).

ScalaCache provides two `KeySanitizer` implementations that convert your cache keys into valid Memcached keys.

* `ReplaceAndTruncateSanitizer` simply replaces non-ASCII characters with underscores and truncates long keys to 250 chars. This sanitizer is convenient because it keeps your keys human-readable. Use it if you only expect ASCII characters to appear in cache keys and you don't use any massively long keys.

* `HashingMemcachedKeySanitizer` uses a hash of your cache key, so it can turn any string into a valid Memcached key. The only downside is that it turns your keys into gobbledigook, which can make debugging a pain. 

### Ehcache

SBT:

```
libraryDependencies += "com.github.cb372" %% "scalacache-ehcache" % "0.28.0"
```

Usage:

```tut:silent
import scalacache._
import scalacache.ehcache._
import net.sf.ehcache.{Cache => UnderlyingCache, _}

// We assume you've already taken care of Ehcache config, 
// and you have an initialized Ehcache cache.
val cacheManager = new CacheManager
val underlying: UnderlyingCache = cacheManager.getCache("myCache")

implicit val ehcacheCache: Cache[String] = EhcacheCache(underlying)
```

### Redis

SBT:

```
libraryDependencies += "com.github.cb372" %% "scalacache-redis" % "0.28.0"
```

Usage:

```tut:silent
import scalacache._
import scalacache.redis._
import scalacache.serialization.binary._

implicit val redisCache: Cache[String] = RedisCache("host1", 6379)
```

or provide your own [Jedis](https://github.com/xetorthio/jedis) client, like this:

```tut:silent
import scalacache._
import scalacache.redis._
import scalacache.serialization.binary._
import _root_.redis.clients.jedis._

val jedisPool = new JedisPool("localhost", 6379)
implicit val customisedRedisCache: Cache[String] = RedisCache(jedisPool)
```

ScalaCache also supports [sharded Redis](https://github.com/xetorthio/jedis/wiki/AdvancedUsage#shardedjedis) and [Redis Sentinel](http://redis.io/topics/sentinel). Just create a `ShardedRedisCache` or `SentinelRedisCache` respectively.

### Caffeine

SBT:

```
libraryDependencies += "com.github.cb372" %% "scalacache-caffeine" % "0.28.0"
```

Usage:

```tut:silent
import scalacache._
import scalacache.caffeine._

implicit val caffeineCache: Cache[String] = CaffeineCache[String]
```

This will build a Caffeine cache with all the default settings. If you want to customize your Caffeine cache, then build it yourself and pass it to `CaffeineCache` like this:

```tut:silent
import scalacache._
import scalacache.caffeine._
import com.github.benmanes.caffeine.cache.Caffeine

val underlyingCaffeineCache = Caffeine.newBuilder().maximumSize(10000L).build[String, Entry[String]]
implicit val customisedCaffeineCache: Cache[String] = CaffeineCache(underlyingCaffeineCache)
```

### cache2k

SBT:

```
libraryDependencies += "com.github.cb372" %% "scalacache-cache2k" % "0.28.0"
```

Usage:

```tut:silent
import java.util.concurrent.TimeUnit
import scalacache._
import scalacache.cache2k._
import org.cache2k.Cache2kBuilder

// You have to configure the cache with a ExpiryPolicy or Cache2kBuilder.expireAfterWrite
// if you want to set expiry on individual values.
val underlyingCache2kCache = new Cache2kBuilder[String, String]() {}.expireAfterWrite(1L, TimeUnit.MINUTES).build
implicit val customisedCache2kCache: Cache[String] = Cache2kCache(underlyingCache2kCache)
```

### OHC

SBT:

```
libraryDependencies += "com.github.cb372" %% "scalacache-ohc" % "0.28.0"
```

Usage:

```tut:silent
import scalacache._
import scalacache.ohc._
import org.caffinitas.ohc.CacheSerializer

implicit val valueSerializer: CacheSerializer[String] = OhcCache.stringSerializer
implicit val ohcCache: Cache[String] = OhcCache[String]
```

This will build a OHC cache with almost default settings. If you want to customize your OHC cache, then build it yourself and pass it to `OhcCache` like this:

```tut:silent
import scalacache._
import scalacache.ohc._
import org.caffinitas.ohc.OHCacheBuilder

// You have to configure the cache with OHCacheBuilder.timeouts(true)
// if you want to set expiry on individual values.
val underlyingOhcCache =
  OHCacheBuilder.
    newBuilder().
    keySerializer(OhcCache.stringSerializer).
    valueSerializer(OhcCache.stringSerializer).
    timeouts(true).
    build()
implicit val customisedOhcCache: Cache[String] = OhcCache(underlyingOhcCache)
```

```tut:invisible
for (cache <- List(ehcacheCache, redisCache, customisedRedisCache, memcachedCache, customisedMemcachedCache, customisedCache2kCache, ohcCache, customisedOhcCache)) {
  cache.close()(scalacache.modes.sync.mode)
} 
```
