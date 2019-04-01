package coursier.parse

import coursier.cache.CachePolicy
import coursier.util.ValidationNel
import coursier.util.Traverse.TraverseOps

object CachePolicyParser {

  def cachePolicies(s: String): ValidationNel[String, Seq[CachePolicy]] =
    s
      .split(',')
      .toVector
      .validationNelTraverse[String, Seq[CachePolicy]] {
        case "offline" =>
          ValidationNel.success(Seq(CachePolicy.LocalOnly))
        case "local" =>
          ValidationNel.success(Seq(CachePolicy.LocalOnlyIfValid))
        case "update-local-changing" =>
          ValidationNel.success(Seq(CachePolicy.LocalUpdateChanging))
        case "update-local" =>
          ValidationNel.success(Seq(CachePolicy.LocalUpdate))
        case "update-changing" =>
          ValidationNel.success(Seq(CachePolicy.UpdateChanging))
        case "update" =>
          ValidationNel.success(Seq(CachePolicy.Update))
        case "missing" =>
          ValidationNel.success(Seq(CachePolicy.FetchMissing))
        case "force" =>
          ValidationNel.success(Seq(CachePolicy.ForceDownload))
        case "default" =>
          ValidationNel.success(Seq(CachePolicy.LocalOnly, CachePolicy.FetchMissing))
        case other =>
          ValidationNel.failure(s"Unrecognized mode: $other")
      }
      .map(_.flatten)

}
