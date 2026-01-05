package mill

import mill.*
import mill.api.*

object MillCsHelper {
  def moduleCtxWithDiscoverAndEnclosingModule(
    ctx: ModuleCtx,
    discover: Discover,
    enclosingModule: ModuleCtx.Wrapper
  ): ModuleCtx =
    ctx.withDiscover(discover).withEnclosingModule(enclosingModule)
}
