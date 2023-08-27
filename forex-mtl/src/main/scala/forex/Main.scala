package forex

import scala.concurrent.ExecutionContext
import cats.effect._
import forex.config._
import forex.services.rates.cache.RatesCache
import fs2.Stream
import org.http4s.client.blaze.BlazeClientBuilder
import org.http4s.server.blaze.BlazeServerBuilder

object Main extends IOApp {

  override def run(args: List[String]): IO[ExitCode] =
    new Application[IO].stream(executionContext).compile.drain.as(ExitCode.Success)
}

class Application[F[_]: ConcurrentEffect: Timer] {

  def stream(ec: ExecutionContext): Stream[F, Unit] = {
    val clientStream = Stream.resource(BlazeClientBuilder[F](ec).resource)
    clientStream.flatMap { client =>
      for {
        config <- Config.stream("app")
        cache  = new RatesCache[F](client, config.oneFrameApi)
        module = new Module[F](cache, config)
        mainStream = BlazeServerBuilder[F](ec)
          .bindHttp(config.http.port, config.http.host)
          .withHttpApp(module.httpApp)
          .serve

        // Merging the server stream with the cache refresh stream
        _ <- mainStream merge cache.cacheRefreshStream()
      } yield ()
    }
  }
}
