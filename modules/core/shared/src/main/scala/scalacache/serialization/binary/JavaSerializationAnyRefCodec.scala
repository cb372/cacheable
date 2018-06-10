package scalacache.serialization.binary

import java.io._

import scalacache.serialization.Codec.DecodingResult
import scalacache.serialization.{Codec, GenericCodecObjectInputStream}

import scala.reflect.ClassTag
import scala.util.control.NonFatal

/**
  * Codec that uses Java serialization to serialize objects
  *
  * Credit: Shade @ https://github.com/alexandru/shade/blob/master/src/main/scala/shade/memcached/Codec.scala
  */
class JavaSerializationAnyRefCodec[S <: Serializable](classTag: ClassTag[S]) extends Codec[S] {

  private final def using[T <: Closeable, R](obj: T)(f: T => R): R =
    try f(obj)
    finally try obj.close()
    catch {
      case NonFatal(_) => // does nothing
    }

  override def encode(value: S): Array[Byte] =
    using(new ByteArrayOutputStream()) { buf =>
      using(new ObjectOutputStream(buf)) { out =>
        out.writeObject(value)
        out.close()
        buf.toByteArray
      }
    }

  override def decode(data: Array[Byte]): DecodingResult[S] =
    Codec.tryDecode {
      using(new ByteArrayInputStream(data.toArray)) { buf =>
        val in = new GenericCodecObjectInputStream(classTag, buf)
        using(in) { inp =>
          inp.readObject().asInstanceOf[S]
        }
      }
    }
}