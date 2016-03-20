package scalacache.memcached

import net.spy.memcached.internal.{ OperationFuture, OperationCompletionListener, GetFuture, GetCompletionListener }
import net.spy.memcached.transcoders.Transcoder
import net.spy.memcached.{ CachedData, AddrUtil, BinaryConnectionFactory, MemcachedClient }
import scala.concurrent.duration.Duration
import scala.util.Success
import scalacache.serdes.Codec
import scalacache.{ LoggingSupport, Cache }
import com.typesafe.scalalogging.StrictLogging
import scala.concurrent.{ Promise, Future, ExecutionContext }

/**
 * Wrapper around spymemcached
 */
class MemcachedCache(client: MemcachedClient, keySanitizer: MemcachedKeySanitizer = ReplaceAndTruncateSanitizer())(implicit execContext: ExecutionContext = ExecutionContext.global)
    extends Cache
    with MemcachedTTLConverter
    with StrictLogging
    with LoggingSupport {

  /**
   * Get the value corresponding to the given key from the cache
   *
   * @param key cache key
   * @tparam V the type of the corresponding value
   * @return the value, if there is one
   */
  override def get[V: Codec](key: String) = {
    val p = Promise[Option[V]]()
    val f = client.asyncGet(keySanitizer.toValidMemcachedKey(key))
    f.addListener(new GetCompletionListener {
      def onComplete(g: GetFuture[_]): Unit = p.complete {
        val byteArray = Option(f.get.asInstanceOf[Array[Byte]])
        val result = byteArray.map(implicitly[Codec[V]].deserialize)
        logCacheHitOrMiss(key, result)
        Success(result)
      }
    })
    p.future
  }

  /**
   * Insert the given key-value pair into the cache, with an optional Time To Live.
   *
   * @param key cache key
   * @param value corresponding value
   * @param ttl Time To Live
   * @tparam V the type of the corresponding value
   */
  override def put[V: Codec](key: String, value: V, ttl: Option[Duration]) = {
    val p = Promise[Unit]()
    val serialized = implicitly[Codec[V]].serialize(value)
    val f = client.set(keySanitizer.toValidMemcachedKey(key), toMemcachedExpiry(ttl), serialized)
    f.addListener(new OperationCompletionListener {
      def onComplete(g: OperationFuture[_]): Unit = p.complete {
        logCachePut(key, ttl)
        Success(())
      }
    })
    p.future
  }

  /**
   * Remove the given key and its associated value from the cache, if it exists.
   * If the key is not in the cache, do nothing.
   *
   * @param key cache key
   */
  override def remove(key: String) = {
    val p = Promise[Unit]()
    val f = client.delete(key)
    f.addListener(new OperationCompletionListener {
      def onComplete(g: OperationFuture[_]): Unit = p.complete {
        Success(())
      }
    })
    p.future
  }

  override def removeAll() = {
    val p = Promise[Unit]()
    val f = client.flush()
    f.addListener(new OperationCompletionListener {
      def onComplete(g: OperationFuture[_]): Unit = p.complete {
        Success(())
      }
    })
    p.future
  }

  override def close(): Unit = {
    client.shutdown()
  }

}

object MemcachedCache {

  /**
   * Create a Memcached client connecting to localhost:11211 and use it for caching
   */
  def apply(): MemcachedCache = apply("localhost:11211")

  /**
   * Create a Memcached client connecting to the given host(s) and use it for caching
   *
   * @param addressString Address string, with addresses separated by spaces, e.g. "host1:11211 host2:22322"
   */
  def apply(addressString: String): MemcachedCache =
    apply(new MemcachedClient(new BinaryConnectionFactory(), AddrUtil.getAddresses(addressString)))

  /**
   * Create a cache that uses the given Memcached client
   *
   * @param client Memcached client
   */
  def apply(client: MemcachedClient): MemcachedCache = new MemcachedCache(client)

}
