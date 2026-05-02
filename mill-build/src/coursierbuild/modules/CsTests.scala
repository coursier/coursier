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
    // Write a JFC overlay into this task's own dest so the file is guaranteed to exist
    // whenever this task's cached result is used. A separate task's dest can be cleaned
    // independently, causing the path in a cached forkArgs result to dangle.
    val jfc = Task.dest / "allocation.jfc"
    os.write(jfc,
      """<?xml version="1.0" encoding="UTF-8"?>
        |<configuration version="2.0" label="allocation" description="profile + TLAB allocation events" provider="coursier-build">
        |  <event name="jdk.ObjectAllocationInNewTLAB">
        |    <setting name="enabled">true</setting>
        |    <setting name="stackTrace">true</setting>
        |  </event>
        |  <event name="jdk.ObjectAllocationOutsideTLAB">
        |    <setting name="enabled">true</setting>
        |    <setting name="stackTrace">true</setting>
        |  </event>
        |</configuration>
        |""".stripMargin
    )
    super.forkArgs() ++ Seq(
      "-Xmx2G",
      "-Xlog:gc*:stdout:time,uptime,level,tags",
      "-XX:+PrintCommandLineFlags",
      "-XX:+HeapDumpOnOutOfMemoryError",
      // settings= accepts a comma-separated list of JFC files layered left-to-right.
      s"-XX:StartFlightRecording=name=continuous,disk=true,dumponexit=true,maxage=1h,maxsize=256m,settings=profile,$jfc"
    )
  }
}
