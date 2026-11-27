package manifesttest

/** Run by [[coursier.launcher.BootstrapManifestTests]]: whether `java.lang` is open to it
  *
  * Through reflection, as this module targets Java 8, which has no modules.
  */
object OpensCheck {
  def main(args: Array[String]): Unit = {
    def module(cls: Class[_]): AnyRef =
      classOf[Class[_]].getMethod("getModule").invoke(cls)
    val javaBase    = module(classOf[Object])
    val self        = module(getClass)
    val moduleClass = Class.forName("java.lang.Module")
    val opened = moduleClass
      .getMethod("isOpen", classOf[String], moduleClass)
      .invoke(javaBase, "java.lang", self)
    println(s"java.lang opened: $opened")
  }
}
