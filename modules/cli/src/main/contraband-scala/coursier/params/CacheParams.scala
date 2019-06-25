/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package coursier.params
final class CacheParams private (
  val cacheLocation: java.io.File,
  val cachePolicies: Seq[coursier.cache.CachePolicy],
  val ttl: Option[scala.concurrent.duration.Duration],
  val parallel: Int,
  val checksum: Seq[Option[String]],
  val retryCount: Int,
  val cacheLocalArtifacts: Boolean,
  val followHttpToHttpsRedirections: Boolean,
  val credentials: Seq[coursier.credentials.Credentials],
  val useEnvCredentials: Boolean) extends coursier.params.CacheParamsHelpers with Serializable {
  
  private def this(cacheLocation: java.io.File, cachePolicies: Seq[coursier.cache.CachePolicy], ttl: Option[scala.concurrent.duration.Duration], parallel: Int, checksum: Seq[Option[String]], retryCount: Int, cacheLocalArtifacts: Boolean, followHttpToHttpsRedirections: Boolean) = this(cacheLocation, cachePolicies, ttl, parallel, checksum, retryCount, cacheLocalArtifacts, followHttpToHttpsRedirections, Nil, true)
  
  override def equals(o: Any): Boolean = o match {
    case x: CacheParams => (this.cacheLocation == x.cacheLocation) && (this.cachePolicies == x.cachePolicies) && (this.ttl == x.ttl) && (this.parallel == x.parallel) && (this.checksum == x.checksum) && (this.retryCount == x.retryCount) && (this.cacheLocalArtifacts == x.cacheLocalArtifacts) && (this.followHttpToHttpsRedirections == x.followHttpToHttpsRedirections) && (this.credentials == x.credentials) && (this.useEnvCredentials == x.useEnvCredentials)
    case _ => false
  }
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (17 + "coursier.params.CacheParams".##) + cacheLocation.##) + cachePolicies.##) + ttl.##) + parallel.##) + checksum.##) + retryCount.##) + cacheLocalArtifacts.##) + followHttpToHttpsRedirections.##) + credentials.##) + useEnvCredentials.##)
  }
  override def toString: String = {
    "CacheParams(" + cacheLocation + ", " + cachePolicies + ", " + ttl + ", " + parallel + ", " + checksum + ", " + retryCount + ", " + cacheLocalArtifacts + ", " + followHttpToHttpsRedirections + ", " + credentials + ", " + useEnvCredentials + ")"
  }
  private[this] def copy(cacheLocation: java.io.File = cacheLocation, cachePolicies: Seq[coursier.cache.CachePolicy] = cachePolicies, ttl: Option[scala.concurrent.duration.Duration] = ttl, parallel: Int = parallel, checksum: Seq[Option[String]] = checksum, retryCount: Int = retryCount, cacheLocalArtifacts: Boolean = cacheLocalArtifacts, followHttpToHttpsRedirections: Boolean = followHttpToHttpsRedirections, credentials: Seq[coursier.credentials.Credentials] = credentials, useEnvCredentials: Boolean = useEnvCredentials): CacheParams = {
    new CacheParams(cacheLocation, cachePolicies, ttl, parallel, checksum, retryCount, cacheLocalArtifacts, followHttpToHttpsRedirections, credentials, useEnvCredentials)
  }
  def withCacheLocation(cacheLocation: java.io.File): CacheParams = {
    copy(cacheLocation = cacheLocation)
  }
  def withCachePolicies(cachePolicies: Seq[coursier.cache.CachePolicy]): CacheParams = {
    copy(cachePolicies = cachePolicies)
  }
  def withTtl(ttl: Option[scala.concurrent.duration.Duration]): CacheParams = {
    copy(ttl = ttl)
  }
  def withTtl(ttl: scala.concurrent.duration.Duration): CacheParams = {
    copy(ttl = Option(ttl))
  }
  def withParallel(parallel: Int): CacheParams = {
    copy(parallel = parallel)
  }
  def withChecksum(checksum: Seq[Option[String]]): CacheParams = {
    copy(checksum = checksum)
  }
  def withRetryCount(retryCount: Int): CacheParams = {
    copy(retryCount = retryCount)
  }
  def withCacheLocalArtifacts(cacheLocalArtifacts: Boolean): CacheParams = {
    copy(cacheLocalArtifacts = cacheLocalArtifacts)
  }
  def withFollowHttpToHttpsRedirections(followHttpToHttpsRedirections: Boolean): CacheParams = {
    copy(followHttpToHttpsRedirections = followHttpToHttpsRedirections)
  }
  def withCredentials(credentials: Seq[coursier.credentials.Credentials]): CacheParams = {
    copy(credentials = credentials)
  }
  def withUseEnvCredentials(useEnvCredentials: Boolean): CacheParams = {
    copy(useEnvCredentials = useEnvCredentials)
  }
}
object CacheParams {
  
  def apply(cacheLocation: java.io.File, cachePolicies: Seq[coursier.cache.CachePolicy], ttl: Option[scala.concurrent.duration.Duration], parallel: Int, checksum: Seq[Option[String]], retryCount: Int, cacheLocalArtifacts: Boolean, followHttpToHttpsRedirections: Boolean): CacheParams = new CacheParams(cacheLocation, cachePolicies, ttl, parallel, checksum, retryCount, cacheLocalArtifacts, followHttpToHttpsRedirections)
  def apply(cacheLocation: java.io.File, cachePolicies: Seq[coursier.cache.CachePolicy], ttl: scala.concurrent.duration.Duration, parallel: Int, checksum: Seq[Option[String]], retryCount: Int, cacheLocalArtifacts: Boolean, followHttpToHttpsRedirections: Boolean): CacheParams = new CacheParams(cacheLocation, cachePolicies, Option(ttl), parallel, checksum, retryCount, cacheLocalArtifacts, followHttpToHttpsRedirections)
  def apply(cacheLocation: java.io.File, cachePolicies: Seq[coursier.cache.CachePolicy], ttl: Option[scala.concurrent.duration.Duration], parallel: Int, checksum: Seq[Option[String]], retryCount: Int, cacheLocalArtifacts: Boolean, followHttpToHttpsRedirections: Boolean, credentials: Seq[coursier.credentials.Credentials], useEnvCredentials: Boolean): CacheParams = new CacheParams(cacheLocation, cachePolicies, ttl, parallel, checksum, retryCount, cacheLocalArtifacts, followHttpToHttpsRedirections, credentials, useEnvCredentials)
  def apply(cacheLocation: java.io.File, cachePolicies: Seq[coursier.cache.CachePolicy], ttl: scala.concurrent.duration.Duration, parallel: Int, checksum: Seq[Option[String]], retryCount: Int, cacheLocalArtifacts: Boolean, followHttpToHttpsRedirections: Boolean, credentials: Seq[coursier.credentials.Credentials], useEnvCredentials: Boolean): CacheParams = new CacheParams(cacheLocation, cachePolicies, Option(ttl), parallel, checksum, retryCount, cacheLocalArtifacts, followHttpToHttpsRedirections, credentials, useEnvCredentials)
}
