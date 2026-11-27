package coursier.cache

import dataclass.data

import java.util.Locale

/** Checksum a file is expected to have
  *
  * See `fileWithChecks` in [[Cache]].
  *
  * @param algorithm
  *   `MessageDigest` algorithm name, like `SHA-256`
  * @param value
  *   hex-encoded digest, whatever its case
  */
@data(setters = false) case class ExpectedChecksum(
  algorithm: String,
  value: String
) {

  /** `value`, in lower case */
  def normalizedValue: String =
    value.trim.toLowerCase(Locale.ROOT)
}

object ExpectedChecksum {
  def md5(value: String): ExpectedChecksum    = ExpectedChecksum("MD5", value)
  def sha1(value: String): ExpectedChecksum   = ExpectedChecksum("SHA-1", value)
  def sha256(value: String): ExpectedChecksum = ExpectedChecksum("SHA-256", value)
  def sha512(value: String): ExpectedChecksum = ExpectedChecksum("SHA-512", value)
}
