scalaVersion := "2.11.8"

val checkEmpty = TaskKey[Unit]("checkEmpty")

checkEmpty := {
  assert(coursier.Helper.checkEmpty)
}

val checkNotEmpty = TaskKey[Unit]("checkNotEmpty")

checkNotEmpty := {
  assert(!coursier.Helper.checkEmpty)
}