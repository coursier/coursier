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
      testDataDir().path.toString,
    "COURSIER_TESTS_METADATA_DIR" ->
      testMetadataDir().path.toString,
    "COURSIER_TESTS_HANDMADE_METADATA_DIR" ->
      testHandmadeMetadataDir().path.toString,
    "COURSIER_TESTS_METADATA_DIR_URI" ->
      testMetadataDir().path.toNIO.toUri.toASCIIString,
    "COURSIER_TESTS_HANDMADE_METADATA_DIR_URI" ->
      testHandmadeMetadataDir().path.toNIO.toUri.toASCIIString
  )
}
