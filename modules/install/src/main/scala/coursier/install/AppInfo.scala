package coursier.install

import dataclass.data

@data class AppInfo(
  appDescriptor: AppDescriptor,
  appDescriptorBytes: Array[Byte],
  source: Source,
  sourceBytes: Array[Byte],
  overrideVersionOpt: Option[String] = None
) {
  def overrideVersion(version: String): AppInfo =
    withAppDescriptor(appDescriptor.overrideVersion(version))
      .withOverrideVersionOpt(Some(version))
  def overrideVersion(versionOpt: Option[String]): AppInfo =
    versionOpt.fold(this)(overrideVersion(_))
}
