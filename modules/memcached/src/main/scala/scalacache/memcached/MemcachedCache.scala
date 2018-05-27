package scalacache.memcached

import org.slf4j.LoggerFactory
import net.spy.memcached.internal.{GetCompletionListener, GetFuture, OperationCompletionListener, OperationFuture}
import net.spy.memcached.ops.StatusCode
import net.spy.memcached.{AddrUtil, BinaryConnectionFactory, MemcachedClient}
import scalacache.serialization.Codec
import scalacache.{AbstractCache, CacheConfig, Mode}
import scodec.bits.ByteVector

import scala.concurrent.duration.Duration
import scala.util.Success
import scala.language.higherKinds
import scala.util.control.NonFatal

class MemcachedException(message: String) extends Exception(message)

/**
  * Wrapper around spymemcached
  */
class MemcachedCache[F[_]](client: MemcachedClient, keySanitizer: MemcachedKeySanitizer = ReplaceAndTruncateSanitizer())(
    implicit val config: CacheConfig,
    mode: Mode[F])
    extends AbstractCache[F]
    with MemcachedTTLConverter {

  override protected final val logger =
    LoggerFactory.getLogger(getClass.getName)

  override protected def doGet[V](key: String)(implicit codec: Codec[V]): F[Option[V]] = {
    mode.M.async { cb =>
      val f = client.asyncGet(keySanitizer.toValidMemcachedKey(key))
      f.addListener(new GetCompletionListener {
        def onComplete(g: GetFuture[_]): Unit = {
          if (g.getStatus.isSuccess) {
            try {
              val bytes = g.get()
              val value = codec.decode(bytes.asInstanceOf[ByteVector]).right.map(Some(_))
              cb(value)
            } catch {
              case NonFatal(e) => cb(Left(e))
            }
          } else {
            g.getStatus.getStatusCode match {
              case StatusCode.ERR_NOT_FOUND => cb(Right(None))
              case _                        => cb(Left(new MemcachedException(g.getStatus.getMessage)))
            }

          }
        }
      })
    }
  }

  override protected def doPut[V](key: String, value: V, ttl: Option[Duration])(implicit codec: Codec[V]): F[Unit] = {
    mode.M.async { cb =>
      val valueToSend = codec.encode(value)
      val f = client.set(keySanitizer.toValidMemcachedKey(key), toMemcachedExpiry(ttl), valueToSend)
      f.addListener(new OperationCompletionListener {
        def onComplete(g: OperationFuture[_]): Unit = {
          if (g.getStatus.isSuccess) {
            logCachePut(key, ttl)
            cb(Right(()))
          } else {
            cb(Left(new MemcachedException(g.getStatus.getMessage)))
          }
          Success(())
        }
      })
    }
  }

  override protected def doRemove(key: String): F[Any] = {
    mode.M.async { cb =>
      val f = client.delete(key)
      f.addListener(new OperationCompletionListener {
        def onComplete(g: OperationFuture[_]): Unit = {
          if (g.getStatus.isSuccess)
            cb(Right(()))
          else
            cb(Left(new MemcachedException(g.getStatus.getMessage)))
        }
      })
    }
  }

  override protected def doRemoveAll(): F[Any] = {
    mode.M.async { cb =>
      val f = client.flush()
      f.addListener(new OperationCompletionListener {
        def onComplete(g: OperationFuture[_]): Unit = {
          if (g.getStatus.isSuccess)
            cb(Right(()))
          else
            cb(Left(new MemcachedException(g.getStatus.getMessage)))
        }
      })
    }
  }

  override def close(): F[Any] = mode.M.delay(client.shutdown())

}

object MemcachedCache {

  /**
    * Create a Memcached client connecting to localhost:11211 and use it for caching
    */
  def apply[F[_]: Mode](implicit config: CacheConfig): MemcachedCache[F] =
    apply("localhost:11211")

  /**
    * Create a Memcached client connecting to the given host(s) and use it for caching
    *
    * @param addressString Address string, with addresses separated by spaces, e.g. "host1:11211 host2:22322"
    */
  def apply[F[_]: Mode](addressString: String)(implicit config: CacheConfig): MemcachedCache[F] =
    apply(new MemcachedClient(new BinaryConnectionFactory(), AddrUtil.getAddresses(addressString)))

  /**
    * Create a cache that uses the given Memcached client
    *
    * @param client Memcached client
    */
  def apply[F[_]: Mode](client: MemcachedClient)(implicit config: CacheConfig): MemcachedCache[F] =
    new MemcachedCache[F](client)

}
