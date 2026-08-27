package coursierbuild.modules

import mill.*
import mill.api.*
import mill.scalalib.*

trait CsResourcesTests extends TestModule {
  def testDataDir: T[PathRef]
  def testHandmadeMetadataDir: T[PathRef]
  def testMetadataDir: T[PathRef]
  def forkEnv = super.forkEnv() ++ Seq(
    "COURSIER_TEST_DATA_DIR" ->
      PathRef.toAbsString(testDataDir().path),
    "COURSIER_TESTS_METADATA_DIR" ->
      PathRef.toAbsString(testMetadataDir().path),
    "COURSIER_TESTS_HANDMADE_METADATA_DIR" ->
      PathRef.toAbsString(testHandmadeMetadataDir().path),
    "COURSIER_TESTS_METADATA_DIR_URI" ->
      PathRef.toAbsNioPath(testMetadataDir().path).toUri.toASCIIString,
    "COURSIER_TESTS_HANDMADE_METADATA_DIR_URI" ->
      PathRef.toAbsNioPath(testHandmadeMetadataDir().path).toUri.toASCIIString
  )
}
