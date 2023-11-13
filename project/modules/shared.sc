import $ivy.`com.github.lolgab::mill-mima::0.0.23`
import com.github.lolgab.mill.mima.Mima
import $file.^.deps, deps.{Deps, ScalaVersions}

import mill._, mill.scalalib._, mill.scalajslib._

trait CsMima extends Mima {
  def mimaPreviousVersions = T {
    Seq.empty[String]
  }
}

def commitHash = T {
  os.proc("git", "rev-parse", "HEAD").call().out.text().trim()
}

lazy val latestTaggedVersion = os.proc("git", "describe", "--abbrev=0", "--tags", "--match", "v*")
  .call().out
  .trim()
lazy val buildVersion = {
  val gitHead = os.proc("git", "rev-parse", "HEAD").call().out.trim()
  val maybeExactTag = scala.util.Try {
    os.proc("git", "describe", "--exact-match", "--tags", "--always", gitHead)
      .call().out
      .trim()
      .stripPrefix("v")
  }
  maybeExactTag.toOption.getOrElse {
    val commitsSinceTaggedVersion =
      os.proc("git", "rev-list", gitHead, "--not", latestTaggedVersion, "--count")
        .call().out.trim()
        .toInt
    val gitHash = os.proc("git", "rev-parse", "--short", "HEAD").call().out.trim()
    s"${latestTaggedVersion.stripPrefix("v")}-$commitsSinceTaggedVersion-$gitHash-SNAPSHOT"
  }
}

trait PublishLocalNoFluff extends PublishModule {
  def emptyZip = T {
    import java.io._
    import java.util.zip._
    val dest = T.dest / "empty.zip"
    val baos = new ByteArrayOutputStream
    val zos  = new ZipOutputStream(baos)
    zos.finish()
    zos.close()
    os.write(dest, baos.toByteArray)
    PathRef(dest)
  }
  // adapted from https://github.com/com-lihaoyi/mill/blob/fea79f0515dda1def83500f0f49993e93338c3de/scalalib/src/PublishModule.scala#L70-L85
  // writes empty zips as source and doc JARs
  def publishLocalNoFluff(localIvyRepo: String = null): define.Command[PathRef] = T.command {

    import mill.scalalib.publish.LocalIvyPublisher
    val publisher = localIvyRepo match {
      case null => LocalIvyPublisher
      case repo =>
        new LocalIvyPublisher(os.Path(repo.replace("{VERSION}", publishVersion()), os.pwd))
    }

    publisher.publish(
      jar = jar().path,
      sourcesJar = emptyZip().path,
      docJar = emptyZip().path,
      pom = pom().path,
      ivy = ivy().path,
      artifact = artifactMetadata(),
      extras = extraPublish()
    )

    jar()
  }
}

trait CoursierPublishModule extends PublishModule with PublishLocalNoFluff with JavaModule {
  import mill.scalalib.publish._
  def pomSettings = PomSettings(
    description = artifactName(),
    organization = "io.get-coursier",
    url = "https://github.com/coursier/coursier",
    licenses = Seq(License.`Apache-2.0`),
    versionControl = VersionControl.github("coursier", "coursier"),
    developers = Seq(
      Developer("alexarchambault", "Alex Archambault", "https://github.com/alexarchambault")
    )
  )
  def publishVersion = T(buildVersion)
  def javacOptions = T {
    super.javacOptions() ++ Seq("-source", "8", "-target", "8")
  }
}

trait CsTests extends TestModule {
  def ivyDeps = super.ivyDeps() ++ Seq(
    Deps.utest
  )
  def testFramework = "utest.runner.Framework"
}

trait CsScalaJsModule extends ScalaJSModule {
  def scalaJSVersion = ScalaVersions.scalaJs
}

trait JvmTests extends TestModule {
  def sources = T.sources {
    val shared = Seq(
      millSourcePath / os.up / "shared" / "src" / "test",
      millSourcePath / os.up / "jvm" / "src" / "test"
    )
    super.sources() ++ shared.map(PathRef(_))
  }
}

trait JsTests extends TestModule {
  def sources = T.sources {
    val shared = Seq(
      millSourcePath / os.up / os.up / "shared" / "src" / "test",
      millSourcePath / os.up / os.up / "js" / "src" / "test"
    )
    super.sources() ++ shared.map(PathRef(_))
  }
}

trait CsModule extends SbtModule {
  def scalacOptions = T {
    val sv = scalaVersion()
    val scala212Opts =
      if (sv.startsWith("2.12.")) Seq("-Ypartial-unification")
      else Nil
    val scala213Opts =
      if (sv.startsWith("2.13.")) Seq("-Ymacro-annotations")
      else Nil
    super.scalacOptions() ++ scala212Opts ++ scala213Opts ++ Seq("-deprecation")
  }
  def scalacPluginIvyDeps = T {
    val sv = scalaVersion()
    val scala212Plugins =
      if (sv.startsWith("2.12.")) Agg(Deps.macroParadise)
      else Nil
    super.scalacPluginIvyDeps() ++ scala212Plugins
  }
  def sources = T.sources {
    val sbv    = mill.scalalib.api.ZincWorkerUtil.scalaBinaryVersion(scalaVersion())
    val parent = super.sources()
    val extra = parent.map(_.path).filter(_.last == "scala").flatMap { p =>
      val dirNames = Seq(s"scala-$sbv")
      dirNames.map(n => PathRef(p / os.up / n))
    }
    parent ++ extra
  }
}

trait CsCrossJvmJsModule extends CrossSbtModule with CsModule {
  def sources = T.sources {
    val shared = PathRef(millSourcePath / os.up / "shared" / "src" / "main")
    super.sources() ++ Seq(shared)
  }
}
