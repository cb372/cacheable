package scalacache

import java.time.{Clock, Instant}

/**
  * A cache entry with an optional expiry time
  */
case class Entry(value: Array[Byte], expiresAt: Option[Instant]) {

  /**
    * Has the entry expired yet?
    */
  def isExpired(implicit clock: Clock): Boolean = expiresAt.exists(_.isBefore(Instant.now(clock)))

}
