package scalacache.ehcache

import org.scalatest.{ BeforeAndAfter, FlatSpec, Matchers }
import net.sf.ehcache.{ Cache => Ehcache, CacheManager, Element }
import scala.concurrent.duration._
import language.postfixOps
import org.scalatest.time.{ Seconds, Span }
import org.scalatest.concurrent.{ ScalaFutures, Eventually }

class EhcacheCacheSpec extends FlatSpec with Matchers with Eventually with BeforeAndAfter with ScalaFutures {

  val underlying = {
    val cacheManager = new CacheManager
    val cache = new Ehcache("test", 1000, false, false, 0, 0)
    cacheManager.addCache(cache)
    cache
  }

  before {
    underlying.removeAll()
  }

  behavior of "get"

  it should "return the value stored in Ehcache" in {
    underlying.put(new Element("key1", 123))
    whenReady(EhcacheCache(underlying).get[String]("key1")) { result =>
      result should be(Some(123))
    }
  }

  it should "return None if the given key does not exist in the underlying cache" in {
    whenReady(EhcacheCache(underlying).get[String]("non-existent-key")) { result =>
      result should be(None)
    }
  }

  behavior of "put"

  it should "store the given key-value pair in the underlying cache" in {
    EhcacheCache(underlying).put("key1", 123, None)
    underlying.get("key1").getObjectValue should be(123)
  }

  behavior of "put with TTL"

  it should "store the given key-value pair in the underlying cache" in {
    EhcacheCache(underlying).put("key1", 123, Some(1 second))
    underlying.get("key1").getObjectValue should be(123)

    // Should expire after 1 second
    eventually(timeout(Span(2, Seconds))) {
      underlying.get("key1") should be(null)
    }
  }

  behavior of "remove"

  it should "delete the given key and its value from the underlying cache" in {
    underlying.put(new Element("key1", 123))
    underlying.get("key1").getObjectValue should be(123)

    EhcacheCache(underlying).remove("key1")
    underlying.get("key1") should be(null)
  }

}
