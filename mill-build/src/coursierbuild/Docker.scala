package coursierbuild

object Docker {
  def customMuslBuilderImageName = "scala-cli-base-musl"
  def muslBuilder                = s"$customMuslBuilderImageName:latest"
  def alpineImage                = "alpine:3.21.2"
  def alpineJavaImage            = "eclipse-temurin:21-jre-alpine"
  def linuxBinaryBaseImage       = "ubuntu:22.04"
}
