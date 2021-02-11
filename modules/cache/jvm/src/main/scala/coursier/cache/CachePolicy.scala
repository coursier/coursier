package coursier.cache

sealed abstract class CachePolicy extends Product with Serializable {
  def acceptChanging: CachePolicy.Mixed
  def rejectChanging: CachePolicy.NoChanging
  def acceptsChangingArtifacts: Boolean
}

object CachePolicy {

  sealed abstract class Mixed extends CachePolicy {
    def acceptChanging: Mixed = this
    def acceptsChangingArtifacts: Boolean = true
  }

  /** Only pick local files, possibly from the cache. Don't try to download anything. */
  case object LocalOnly extends Mixed {
    def rejectChanging = NoChanging.LocalOnly
  }

  /** Only pick local files, possibly from the cache. Don't return changing artifacts (whose last check is) older than TTL */
  case object LocalOnlyIfValid extends Mixed {
    def rejectChanging = NoChanging.LocalOnly
  }

  /**
    * Only pick local files. If one of these local files corresponds to a changing artifact, check
    * for updates, and download these if needed.
    *
    * If no local file is found, *don't* try download it. Updates are only checked for files already
    * in cache.
    *
    * Follows the TTL parameter (assumes no update is needed if the last one is recent enough).
    */
  case object LocalUpdateChanging extends Mixed {
    def rejectChanging = NoChanging.LocalOnly
  }

  /**
    * Only pick local files, check if any update is available for them, and download these if needed.
    *
    * If no local file is found, *don't* try download it. Updates are only checked for files already
    * in cache.
    *
    * Follows the TTL parameter (assumes no update is needed if the last one is recent enough).
    *
    * Unlike `LocalUpdateChanging`, all found local files are checked for updates, not just the
    * changing ones.
    */
  case object LocalUpdate extends Mixed {
    def rejectChanging = NoChanging.LocalUpdate
  }

  /**
    * Pick local files, and download the missing ones.
    *
    * For changing ones, check for updates, and download those if any.
    *
    * Follows the TTL parameter (assumes no update is needed if the last one is recent enough).
    */
  case object UpdateChanging extends Mixed {
    def rejectChanging = NoChanging.FetchMissing
  }

  /**
    * Pick local files, download the missing ones, check for updates and download those if any.
    *
    * Follows the TTL parameter (assumes no update is needed if the last one is recent enough).
    *
    * Unlike `UpdateChanging`, all found local files are checked for updates, not just the changing
    * ones.
    */
  case object Update extends Mixed {
    def rejectChanging = NoChanging.FetchMissing
  }

  /**
    * Pick local files, download the missing ones.
    *
    * No updates are checked for files already downloaded.
    */
  case object FetchMissing extends Mixed {
    def rejectChanging = NoChanging.FetchMissing
  }

  /**
    * (Re-)download all files.
    *
    * Erases files already in cache.
    */
  case object ForceDownload extends Mixed {
    def rejectChanging = NoChanging.ForceDownload
  }

  sealed abstract class NoChanging extends CachePolicy {
    def rejectChanging: CachePolicy.NoChanging = this
    def acceptsChangingArtifacts: Boolean = false
  }

  object NoChanging {
    case object LocalOnly extends NoChanging {
      def acceptChanging = CachePolicy.LocalOnly
    }
    case object LocalUpdate extends NoChanging {
      def acceptChanging = CachePolicy.LocalUpdate
    }
    case object FetchMissing extends NoChanging {
      def acceptChanging = CachePolicy.FetchMissing
    }
    case object ForceDownload extends NoChanging {
      def acceptChanging = CachePolicy.ForceDownload
    }
  }


}
