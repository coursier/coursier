package mill

import mill.*
import mill.api.*

object MillCsHelper {
  def moduleCtxWithDiscover(ctx: ModuleCtx, discover: Discover): ModuleCtx =
    ctx.withDiscover(discover)
}
