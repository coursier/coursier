package coursier.docker.vm

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import com.jcraft.jsch.{JSch, KeyPair => JSchKeyPair}
import scala.util.Using
import java.net.ServerSocket
import scala.util.Properties
import com.jcraft.jsch.Session
import java.util.{Properties => JProperties}
import scala.concurrent.duration.DurationInt
import com.jcraft.jsch.JSchException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.SocketException
import com.jcraft.jsch.Logger
import com.jcraft.jsch.Channel
import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.ChannelShell
import com.jcraft.jsch.Session
import coursier.cache.DigestBasedCache
import coursier.cache.FileCache
import coursier.paths.CoursierPaths
import com.github.plokhotnyuk.jsoniter_scala.core.{JsonValueCodec, readFromArray, writeToArray}
import com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker
import java.io.InputStream
import coursier.cache.util.Cpu

final class Vm(
  val id: String,
  val params: Vm.Params,
  val proc: Either[Long, os.SubProcess],
  var debug: Boolean = false
) extends AutoCloseable {

  def checkExists(): Either[String, Unit] =
    ???

  def pid(): Long =
    proc.map(_.wrapped.pid()).merge

  def isRunning(): Boolean =
    proc match {
      case Left(pid) =>
        if (Properties.isWin) {
          val exitCode = os.proc("taskkill", "/FI", s"PID eq $pid")
            .call(stderr = os.Pipe, check = false)
            .exitCode
          exitCode == 0
        }
        else {
          val exitCode = os.proc("kill", "-0", pid)
            .call(stderr = os.Pipe, check = false)
            .exitCode
          exitCode == 0
        }
      case Right(proc) => proc.isAlive()
    }

  def persist(vmsDir: os.Path): Unit = {
    val dirPerms = if (Properties.isWin) null else "rwx------"
    val perms    = if (Properties.isWin) null else "rw-------"
    val b        = writeToArray(Vm.AsJson(this))(Vm.asJsonCodec)
    val dir      = vmsDir / id
    if (!os.exists(dir)) {
      os.makeDir.all(vmsDir)
      os.makeDir(dir, perms = dirPerms)
    }
    os.write.over(dir / "vm.json", b, perms = perms)

    if (!os.exists(dir / ".ssh"))
      os.makeDir(dir / ".ssh", perms = dirPerms)
    os.write(dir / ".ssh/id_rsa.pub", params.keyPair.publicKey, createFolders = true, perms = perms)
    os.write(dir / ".ssh/id_rsa", params.keyPair.privateKey, perms = perms)
  }

  lazy val jsch: JSch = {
    val inst = new JSch
    inst.addIdentity(
      "cs-keys",
      params.keyPair.privateKey.getBytes(StandardCharsets.UTF_8),
      params.keyPair.publicKey.getBytes(StandardCharsets.UTF_8),
      null
    )
    inst
  }

  def withSession[T](f: Session => T): T = {
    val session = jsch.getSession(params.user, "127.0.0.1", params.sshLocalPort)
    val props   = new JProperties
    props.setProperty("StrictHostKeyChecking", "no")
    session.setConfig(props)
    session.setDaemonThread(true)
    session.setLogger(
      new Logger {
        def isEnabled(level: Int): Boolean =
          debug
        def levelStr(level: Int): String =
          level match {
            case 0 => "debug"
            case 1 => "info"
            case 2 => "warn"
            case 3 => "error"
            case 4 => "fatal"
            case _ => "???"
          }
        def log(level: Int, message: String): Unit =
          if (debug)
            System.err.println(s"[${levelStr(level)}] $message")
      }
    )

    val delay =
      if (System.getenv("CI") == null) 2.seconds
      else 10.seconds
    def connect(): Unit = {
      val success =
        try {
          session.connect(delay.toMillis.toInt)
          true
        }
        catch {
          case e: JSchException
              if e.getCause.isInstanceOf[ConnectException] ||
              e.getCause.isInstanceOf[SocketTimeoutException] ||
              e.getCause.isInstanceOf[SocketException] ||
              e.getMessage.contains("channel is not opened") =>
            System.err.println(s"Caught $e, waiting $delay")
            if (debug)
              e.printStackTrace(System.err)
            Thread.sleep(delay.toMillis)
            false
        }
      if (!success)
        connect()
    }

    connect()
    if (debug)
      System.err.println("Connected")

    try f(session)
    finally session.disconnect()
  }

  def setupMounts(): Unit =
    if (params.mounts.nonEmpty)
      withSession { session =>
        for (mount <- params.mounts) {
          Vm.runCommand(session)("sudo", "mkdir", "-p", s"/${mount.guestPath}")
          Vm.runCommand(session)(
            "sudo",
            "mount",
            "-t",
            "9p",
            "-o",
            s"trans=virtio,uid=${params.mainUid},gid=${params.mainGid}",
            mount.tag,
            s"/${mount.guestPath}",
            "-oversion=9p2000.L"
          )
        }
      }

  def close(): Unit =
    proc.foreach(_.close())
}

object Vm {

  final case class Params(
    sshLocalPort: Int,
    user: String,
    keyPair: KeyPair,
    mounts: Seq[Mount],
    memory: String,
    cpu: String,
    name: String,
    hostWorkDir: os.Path,
    guestWorkDir: os.SubPath,
    mainUid: Int,
    mainGid: Int,
    useVirtualization: Boolean,
    machine: String
  )

  object Params {
    def defaultCpu: String    = cpuCount.toString
    def cpuCount: Int         = Runtime.getRuntime.availableProcessors()
    def defaultMemory: String = "8g"
    def defaultUser: String   = "cs"
    def default(
      workDir: os.Path = defaultWorkDir(),
      guestWorkDir: os.SubPath = os.sub / "workdir",
      cacheLocation: Option[os.Path] = Some(os.Path(FileCache().location, os.pwd)),
      digestCacheLocation: Option[os.Path] = Some(os.Path(DigestBasedCache().location, os.pwd)),
      guestCpu: Cpu = Cpu.get()
    ): Params = Params(
      sshLocalPort = -1,
      user = defaultUser,
      keyPair = Vm.KeyPair.generate(),
      mounts = defaultMounts(workDir, guestWorkDir, cacheLocation, digestCacheLocation),
      memory = defaultMemory,
      cpu = defaultCpu,
      name = "cs docker VM",
      hostWorkDir = workDir,
      guestWorkDir = guestWorkDir,
      mainUid = 1000,
      mainGid = 1000,
      useVirtualization = true,
      machine = defaultMachine(guestCpu)
    )

    def defaultMounts(
      workDir: os.Path = defaultWorkDir(),
      guestWorkDir: os.SubPath = os.sub / "workdir",
      cacheLocation: Option[os.Path] = Some(os.Path(FileCache().location, os.pwd)),
      digestCacheLocation: Option[os.Path] = Some(os.Path(DigestBasedCache().location, os.pwd))
    ): Seq[Mount] = {

      val cacheMountOpt = cacheLocation.map { path =>
        Vm.Mount("cscache", path, os.sub / "cs/v1")
      }
      val digestCacheMountOpt = digestCacheLocation.map { path =>
        Vm.Mount("csdigestcache", path, os.sub / "cs/digest")
      }

      Seq(Vm.Mount("csworkdir", workDir, guestWorkDir)) ++
        cacheMountOpt ++
        digestCacheMountOpt
    }

    def defaultWorkDir(): os.Path = {
      val workDirBase = defaultBaseVmDir() / "workdir"
      os.makeDir.all(workDirBase)
      workDirBase
    }
  }

  def defaultBaseVmDir(): os.Path = {
    val dataLocalDir = os.Path(CoursierPaths.dataLocalDirectory(), os.pwd)
    dataLocalDir / "vm"
  }
  def defaultVmDir(): os.Path =
    defaultBaseVmDir() / "vms"

  def readFrom(vmsDir: os.Path, id: String): Vm = {
    val content = os.read.bytes(vmsDir / id / "vm.json")
    val asJson  = readFromArray(content)(asJsonCodec)
    asJson.toVm(id)
  }

  def defaultMachine(guestCpu: Cpu = Cpu.get()): String =
    guestCpu match {
      case Cpu.X86_64 => "pc"
      case Cpu.Arm64  => "virt"
    }

  def spawn(
    id: String,
    vmFiles: VmFiles,
    params: Params,
    extraArgs: Seq[String]
  ): Vm = {
    val seedIso0 = seedIso(
      params.user,
      params.keyPair.publicKey
    )
    val port =
      if (params.sshLocalPort >= 0) params.sshLocalPort
      else Using.resource(new ServerSocket(0))(_.getLocalPort)
    val mountOptions = params.mounts.flatMap { mount =>
      Seq[os.Shellable](
        "-virtfs",
        s"local,path=${mount.hostPath},mount_tag=${mount.tag},security_model=mapped-xattr,id=${mount.tag}"
      )
    }
    val accelOpts =
      if (params.useVirtualization)
        if (Properties.isMac) Seq("--accel", "hvf")
        else if (Properties.isLinux) Seq("--accel", "kvm")
        else if (Properties.isWin) Seq("--accel", "whpx")
        else Nil // ???
      else
        Seq("--accel", "tcg")
    val qemuOptions = Seq[os.Shellable](
      "-M",
      params.machine,
      accelOpts,
      "-m",
      params.memory,
      "-cpu",
      "max",
      "-smp",
      params.cpu,
      vmFiles.qemu.bios.toSeq.flatMap(p => Seq[os.Shellable]("-bios", p)),
      "-display",
      "none",
      "-netdev",
      s"id=net00,type=user,hostfwd=tcp::$port-:22",
      "-device",
      s"virtio-net-pci,netdev=net00,romfile=${vmFiles.qemu.virtioRom}",
      "-drive",
      s"file=${vmFiles.image},if=virtio",
      "-snapshot",
      "-cdrom",
      seedIso0,
      mountOptions,
      "-name",
      params.name
    )

    val qemuProc = os.proc(vmFiles.qemu.qemu, qemuOptions, extraArgs)
      .spawn(stdin = os.Inherit, stdout = os.Inherit, destroyOnExit = false)

    val params0 =
      if (params.sshLocalPort == port) params
      else params.copy(sshLocalPort = port)

    val vm = new Vm(id, params0, Right(qemuProc))
    vm.setupMounts()
    vm
  }

  def seedIso(
    user: String,
    publicKey: String
  ) = {
    val workDir =
      os.temp.dir(prefix = "coursier-vm-seed", perms = if (Properties.isWin) null else "rwx------")
    val isoFile = workDir / "seed.iso"

    val userDataContent =
      s"""#cloud-config
         |users:
         |  - name: $user
         |    lock_passwd: false
         |    sudo: ["ALL=(ALL) NOPASSWD:ALL"]
         |    shell: /bin/bash
         |    ssh-authorized-keys:
         |      - $publicKey
         |""".stripMargin
    val metaDataContent =
      """instance-id: someid/somehostname
        |""".stripMargin

    val userData = workDir / "user-data"
    val metaData = workDir / "meta-data"

    os.write(userData, userDataContent)
    os.write(metaData, metaDataContent)

    os.proc(
      "mkisofs",
      "-output",
      isoFile,
      "-volid",
      "cidata",
      "-joliet",
      "-rock",
      userData,
      metaData
    )
      .call(cwd = workDir, stdin = os.Inherit, stdout = os.Inherit)

    os.remove(userData)
    os.remove(metaData)

    isoFile
  }

  final case class KeyPair(
    publicKey: String,
    privateKey: String
  )

  object KeyPair {
    def generate(
      jsch: JSch = new JSch,
      keySize: Int = 4096
    ): KeyPair = {

      val keyPair = JSchKeyPair.genKeyPair(jsch, JSchKeyPair.RSA, keySize)

      val pubBaos  = new ByteArrayOutputStream
      val privBaos = new ByteArrayOutputStream

      keyPair.writePublicKey(pubBaos, "Generated by cs")
      keyPair.writePrivateKey(privBaos)

      keyPair.dispose()

      KeyPair(
        new String(pubBaos.toByteArray, StandardCharsets.UTF_8),
        new String(privBaos.toByteArray, StandardCharsets.UTF_8)
      )
    }
  }

  final case class Mount(
    tag: String,
    hostPath: os.Path,
    guestPath: os.SubPath
  )

  def runCommand(
    session: Session,
    check: Boolean = true,
    pty: Boolean = false,
    stdin: os.ProcessOutput = null,
    stdout: os.ProcessOutput = null
  )(command: os.Shellable*): os.CommandResult = {
    val execChannel = session.openChannel("exec") match {
      case c: ChannelExec => c
      case other          => sys.error(s"Unexpected exec channel: $other")
    }

    val stdin0  = Option(stdin).getOrElse(if (pty) os.Inherit else os.Pipe)
    val stdout0 = Option(stdout).getOrElse(if (pty) os.Inherit else os.Pipe)

    val in =
      if (stdin0 == os.Inherit || stdin0 == os.InheritRaw) System.in
      else InputStream.nullInputStream()
    val out =
      if (stdout0 == os.Inherit || stdout0 == os.InheritRaw) Right(System.out)
      else Left(new ByteArrayOutputStream)

    val command0 = command.flatMap(_.value)

    execChannel.setCommand(toCommand(command0))
    execChannel.setInputStream(in, true)
    execChannel.setOutputStream(out.merge, true)
    execChannel.setErrStream(System.err, true)
    // TODO Transmit size changes too
    // TODO Pass correct terminal type
    execChannel.setPty(pty) // FIXME Plasmon: no completion (method from package private parent)
    execChannel.connect()

    while (execChannel.isConnected)
      Thread.sleep(100L)

    val exitCode = execChannel.getExitStatus
    execChannel.disconnect()
    if (check && exitCode != 0)
      sys.error(s"Running command '$command' failed with exit code $exitCode")

    os.CommandResult(
      command0,
      exitCode,
      out
        .left
        .toSeq
        .map(_.toByteArray)
        .map(new geny.Bytes(_))
        .map(Left(_))
    )
  }

  private val sq = "'"
  private val dq = "\""
  private def toCommand(args: Seq[String]): String =
    args
      .map { arg =>
        if (arg.isEmpty) sq + sq
        else sq + arg.replace(sq, sq + dq + sq + dq + sq) + sq
      }
      .mkString(" ")

  private final case class KeyPairAsJson(
    publicKey: String,
    privateKey: String
  ) {
    def toKeyPair: KeyPair =
      KeyPair(
        publicKey = publicKey,
        privateKey = privateKey
      )
  }
  private object KeyPairAsJson {
    def apply(keyPair: KeyPair): KeyPairAsJson =
      KeyPairAsJson(
        publicKey = keyPair.publicKey,
        privateKey = keyPair.privateKey
      )
  }
  private final case class MountAsJson(
    tag: String,
    hostPath: String,
    guestPath: String
  ) {
    def toMount: Mount =
      Mount(
        tag = tag,
        hostPath = os.Path(hostPath),
        guestPath = os.SubPath(guestPath.stripPrefix("/"))
      )
  }
  private object MountAsJson {
    def apply(mount: Mount): MountAsJson =
      MountAsJson(
        tag = mount.tag,
        hostPath = mount.hostPath.toString,
        guestPath = "/" + mount.guestPath.toString
      )
  }
  private final case class AsJson(
    pid: Long,
    sshLocalPort: Int,
    user: String,
    keyPair: KeyPairAsJson,
    mounts: Seq[MountAsJson],
    memory: String,
    cpu: String,
    name: String,
    hostWorkDir: String,
    guestWorkDir: String,
    mainUid: Int,
    mainGid: Int,
    useVirtualization: Boolean,
    machine: String
  ) {
    def toVm(id: String): Vm =
      new Vm(
        id = id,
        params = Params(
          sshLocalPort = sshLocalPort,
          user = user,
          keyPair = keyPair.toKeyPair,
          mounts = mounts.map(_.toMount),
          memory = memory,
          cpu = cpu,
          name = name,
          hostWorkDir = os.Path(hostWorkDir),
          guestWorkDir = os.SubPath(guestWorkDir.stripPrefix("/")),
          mainUid = mainUid,
          mainGid = mainGid,
          useVirtualization = useVirtualization,
          machine = machine
        ),
        proc = Left(pid)
      )
  }

  private object AsJson {
    def apply(vm: Vm): AsJson =
      AsJson(
        pid = vm.pid(),
        sshLocalPort = vm.params.sshLocalPort,
        user = vm.params.user,
        keyPair = KeyPairAsJson(vm.params.keyPair),
        mounts = vm.params.mounts.map(MountAsJson(_)),
        memory = vm.params.memory,
        cpu = vm.params.cpu,
        name = vm.params.name,
        hostWorkDir = vm.params.hostWorkDir.toString,
        guestWorkDir = "/" + vm.params.guestWorkDir.toString,
        mainUid = vm.params.mainUid,
        mainGid = vm.params.mainGid,
        useVirtualization = vm.params.useVirtualization,
        machine = vm.params.machine
      )
  }

  private implicit lazy val keyPairAsJsonCodec: JsonValueCodec[KeyPairAsJson] =
    JsonCodecMaker.make
  private implicit lazy val mountAsJsonCodec: JsonValueCodec[MountAsJson] =
    JsonCodecMaker.make
  private lazy val asJsonCodec: JsonValueCodec[AsJson] =
    JsonCodecMaker.make
}
