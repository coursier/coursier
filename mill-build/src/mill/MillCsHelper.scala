package mill

import mill.*
import mill.api.*

object MillCsHelper {
  def moduleCtxWithDiscover(ctx: ModuleCtx, discover: Discover, enclosingModule: ModuleCtx.Wrapper): ModuleCtx =
    ctx.withDiscover(discover).withEnclosingModule(enclosingModule)
}
