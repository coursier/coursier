package coursier.cli.search

import caseapp.core.RemainingArgs
import coursier.cli.CoursierCommand
import coursier.cli.Util.ValidatedExitOnError
import coursier.install.Channels
import coursier.util.Sync

object Search extends CoursierCommand[SearchOptions] {

  override def run(options: SearchOptions, args: RemainingArgs): Unit = {
    val params = SearchParams(options, args.all.nonEmpty).exitOnError()
    val pool   = Sync.fixedThreadPool(params.cache.parallel)
    val cache  = params.cache.cache(pool, params.output.logger())
    val channels = Channels(params.channels.channels, params.repositories.repositories, cache)
      .withVerbosity(params.output.verbosity)
    channels.searchAppName(args.all).attempt.unsafeRun()(cache.ec) match {
      case Left(err: Channels.ChannelsException) =>
        System.err.println(err.getMessage)
        sys.exit(1)
      case Left(err) =>
        System.err.println(err.getMessage)
        if (params.output.verbosity >= 2)
          throw err
        else
          sys.exit(1)
      case Right(names) =>
        print(names.map(_ + System.lineSeparator).mkString)
    }
  }

}
