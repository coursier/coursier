package coursier.core

// scala.collection.immutable.AbstractSeq does not exist in 2.12
private[coursier] abstract class AbstractSeq[+A] extends scala.collection.immutable.Seq[A]
