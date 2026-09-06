package coursierbuild.modules

import mill.*
import mill.api.*
import mill.scalalib.*

trait CsResourcesTests extends TestModule {
  def testDataDir: T[PathRef]
  def testHandmadeMetadataDir: T[PathRef]
  def testMetadataDir: T[PathRef]
  private def dirUri(dir: PathRef): String =
    PathRef.toAbsNioPath(PathRef.toResolvedOsPath(dir.path)).toUri.toASCIIString
  def forkEnv = super.forkEnv() ++ Seq(
    "COURSIER_TEST_DATA_DIR" ->
      PathRef.toResolvedPathString(testDataDir().path),
    "COURSIER_TESTS_METADATA_DIR" ->
      PathRef.toResolvedPathString(testMetadataDir().path),
    "COURSIER_TESTS_HANDMADE_METADATA_DIR" ->
      PathRef.toResolvedPathString(testHandmadeMetadataDir().path),
    "COURSIER_TESTS_METADATA_DIR_URI" ->
      dirUri(testMetadataDir()),
    "COURSIER_TESTS_HANDMADE_METADATA_DIR_URI" ->
      dirUri(testHandmadeMetadataDir())
  )
}
