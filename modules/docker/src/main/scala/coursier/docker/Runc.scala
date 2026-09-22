package coursier.docker

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker
import coursier.cache.{Cache, CacheLogger}
import coursier.cache.util.{Cpu, Os}
import coursier.util.{Artifact, Task}

import java.io.File

import scala.collection.immutable.ListMap
import scala.concurrent.Await
import scala.concurrent.duration.Duration

object Runc {

  def defaultRuncVersion = "1.2.4"
  def defaultCrunVersion = "1.20"

  def runcArtifact(
    version: String = defaultRuncVersion,
    cpu: Cpu = Cpu.get()
  ): Artifact = {
    val cpuPart = cpu match {
      case Cpu.X86_64 => "amd64"
      case Cpu.Arm64  => "arm64"
    }
    Artifact(s"https://github.com/opencontainers/runc/releases/download/v$version/runc.$cpuPart")
  }

  def crunArtifact(
    version: String = defaultCrunVersion,
    cpu: Cpu = Cpu.get()
  ): Artifact = {
    val cpuPart = cpu match {
      case Cpu.X86_64 => "amd64"
      case Cpu.Arm64  => "arm64"
    }
    Artifact(
      s"https://github.com/containers/crun/releases/download/$version/crun-$version-linux-$cpuPart"
    )
  }

  def runc(cache: Cache[Task]): File = {

    val logger = cache.loggerOpt.getOrElse(CacheLogger.nop)

    val runcArtifact = crunArtifact()
    val runcFile     = Await.result(
      logger.using(cache.file(runcArtifact).run).future()(cache.ec),
      Duration.Inf
    ) match {
      case Left(err) => throw new Exception(err)
      case Right(f)  => f
    }

    val runcMadeExecutable = runcFile.setExecutable(true, false)
    if (!runcMadeExecutable)
      System.err.println(s"Warning: could not make $runcFile executable, proceeding anyway")

    runcFile
  }

  final case class Config(
    ociVersion: String,
    process: Runc.Config.Process,
    root: Runc.Config.Root,
    hostname: String,
    mounts: Seq[Runc.Config.Mount],
    linux: Runc.Config.Linux
  )

  object Config {
    final case class Process(
      terminal: Boolean,
      user: User,
      args: Seq[String],
      env: Seq[String],
      cwd: String,
      capabilities: ListMap[String, Seq[String]],
      rlimits: Seq[Rlimit],
      noNewPrivileges: Boolean
    )

    final case class User(uid: Int, gid: Int)

    final case class Rlimit(`type`: String, hard: Int, soft: Int)

    final case class Root(
      path: String,
      readonly: Boolean
    )

    final case class Mount(
      destination: String,
      `type`: String,
      source: String,
      options: Seq[String] = Nil
    )

    final case class Linux(
      resources: LinuxResources,
      namespaces: Seq[LinuxNamespace],
      maskedPaths: Seq[String],
      readonlyPaths: Seq[String]
    )

    final case class LinuxResources(
      devices: Seq[LinuxDevice]
    )

    final case class LinuxDevice(
      allow: Boolean,
      access: String
    )

    final case class LinuxNamespace(
      `type`: String
    )

    implicit lazy val codec: JsonValueCodec[Config] =
      JsonCodecMaker.make
  }

  def config(
    terminal: Boolean,
    env: Seq[String],
    cwd: String,
    cmd: Seq[String],
    rootFsDirName: String
  ): Config =
    Config(
      ociVersion = "1.2.0",
      process = Config.Process(
        terminal = terminal,
        user = Config.User(0, 0),
        args = cmd,
        env = env,
        cwd = if (cwd.isEmpty) "/" else cwd,
        capabilities = ListMap(
          "bounding" -> Seq(
            "CAP_AUDIT_WRITE",
            "CAP_KILL",
            "CAP_NET_BIND_SERVICE"
          ),
          "effective" -> Seq(
            "CAP_AUDIT_WRITE",
            "CAP_KILL",
            "CAP_NET_BIND_SERVICE"
          ),
          "permitted" -> Seq(
            "CAP_AUDIT_WRITE",
            "CAP_KILL",
            "CAP_NET_BIND_SERVICE"
          )
        ),
        rlimits = Seq(
          Config.Rlimit(
            `type` = "RLIMIT_NOFILE",
            hard = 1024,
            soft = 1024
          )
        ),
        noNewPrivileges = true
      ),
      root = Config.Root(
        path = rootFsDirName,
        readonly = false
      ),
      hostname = "runc",
      mounts = Seq(
        Config.Mount("/proc", "proc", "proc"),
        Config.Mount(
          "/dev",
          "tmpfs",
          "tmpfs",
          Seq(
            "nosuid",
            "strictatime",
            "mode=755",
            "size=65536k"
          )
        ),
        Config.Mount(
          "/dev/pts",
          "devpts",
          "devpts",
          Seq(
            "nosuid",
            "noexec",
            "newinstance",
            "ptmxmode=0666",
            "mode=0620",
            "gid=5"
          )
        ),
        Config.Mount(
          "/dev/shm",
          "tmpfs",
          "shm",
          Seq(
            "nosuid",
            "noexec",
            "nodev",
            "mode=1777",
            "size=65536k"
          )
        ),
        Config.Mount(
          "/dev/mqueue",
          "mqueue",
          "mqueue",
          Seq(
            "nosuid",
            "noexec",
            "nodev"
          )
        ),
        Config.Mount(
          "/sys",
          "sysfs",
          "sysfs",
          Seq(
            "nosuid",
            "noexec",
            "nodev",
            "ro"
          )
        ),
        Config.Mount(
          "/sys/fs/cgroup",
          "cgroup",
          "cgroup",
          Seq(
            "nosuid",
            "noexec",
            "nodev",
            "relatime",
            "ro"
          )
        )
      ),
      linux = Config.Linux(
        resources = Config.LinuxResources(
          devices = Seq(
            Config.LinuxDevice(
              allow = false,
              access = "rwm"
            )
          )
        ),
        namespaces = Seq(
          Config.LinuxNamespace("pid"),
          Config.LinuxNamespace("network"),
          Config.LinuxNamespace("ipc"),
          Config.LinuxNamespace("uts"),
          Config.LinuxNamespace("mount"),
          Config.LinuxNamespace("cgroup")
        ),
        maskedPaths = Seq(
          "/proc/acpi",
          "/proc/asound",
          "/proc/kcore",
          "/proc/keys",
          "/proc/latency_stats",
          "/proc/timer_list",
          "/proc/timer_stats",
          "/proc/sched_debug",
          "/sys/firmware",
          "/proc/scsi"
        ),
        readonlyPaths = Seq(
          "/proc/bus",
          "/proc/fs",
          "/proc/irq",
          "/proc/sys",
          "/proc/sysrq-trigger"
        )
      )
    )
}
