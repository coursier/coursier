package coursier.credentials

abstract class Credentials extends Serializable {

  def get(): Seq[DirectCredentials]

}
