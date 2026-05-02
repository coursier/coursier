package coursierbuild.modules

import coursierbuild.Deps.Deps
import mill._, mill.scalalib._

trait CsTests extends TestModule with JavaModule {
  def mvnDeps = super.mvnDeps() ++ Seq(
    Deps.pprint,
    Deps.utest
  )
  def testFramework = "utest.runner.Framework"

  def defaultTask() = super[TestModule].defaultTask()

  def forkArgs = Task {
    val workspaceRoot = Iterator.iterate(Task.dest)(_ / os.up)
      .find(p => os.exists(p / "build.mill"))
      .getOrElse(sys.error("could not find workspace root (no build.mill found)"))
    val jfc = workspaceRoot / "mill-build" / "jfr-diag.jfc"
    super.forkArgs() ++ Seq(
      "-Xmx512M",
      "-Xlog:gc*:stdout:time,uptime,level,tags",
      "-Xlog:gc*:file=gc_pid%p.log:time,uptime,level,tags:filecount=1,filesize=50m",
      "-XX:+PrintCommandLineFlags",
      "-XX:+HeapDumpOnOutOfMemoryError",
      "-XX:ErrorFile=hs_err_pid%p.log",
      s"-XX:StartFlightRecording=name=continuous,disk=true,dumponexit=true,maxage=1h,maxsize=256m,settings=profile,$jfc"
    )
  }
}
