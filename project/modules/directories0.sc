
import mill._, mill.scalalib._

trait Directories extends JavaModule {
  def sources = T.sources {
    super.sources().map { p =>
      if (p.path.last == "src")
        PathRef(p.path / "main" / "java")
      else
        PathRef(p.path)
    }
  }
  def resources = T.sources {
    super.resources().map { p =>
      if (p.path.last == "resources")
        PathRef(p.path / os.up / "src" / "main" / "resources")
      else
        PathRef(p.path)
    }
  }
}
