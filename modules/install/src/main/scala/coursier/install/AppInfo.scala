package coursier.install

import dataclass.data



@data case class AppInfo(
  appDescriptor: AppDescriptor,
  appDescriptorBytes: Array[Byte],
  source: Source,
  sourceBytes: Array[Byte],
  overrideVersionOpt: Option[String] = None
) {
  def overrideVersion(version: String): AppInfo =
    copy(appDescriptor = appDescriptor.overrideVersion(version))
      .copy(overrideVersionOpt = Some(version))
  def overrideVersion(versionOpt: Option[String]): AppInfo =
    versionOpt.fold(this)(overrideVersion(_))
}
