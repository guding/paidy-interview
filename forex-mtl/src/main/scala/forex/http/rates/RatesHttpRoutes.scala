package forex.http
package rates

import cats.effect.Sync
import cats.syntax.flatMap._
import forex.programs.RatesProgram
import forex.programs.rates.errors.Error.RateLookupFailed
import forex.programs.rates.{ Protocol => RatesProgramProtocol }
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl
import org.http4s.server.Router

class RatesHttpRoutes[F[_]: Sync](rates: RatesProgram[F]) extends Http4sDsl[F] {

  import Converters._, QueryParams._, Protocol._

  private[http] val prefixPath = "/rates"

  private val httpRoutes: HttpRoutes[F] = HttpRoutes.of[F] {
    case GET -> Root :? FromQueryParam(Left(fromError)) +& _ =>
      BadRequest(s"Invalid 'from' currency: $fromError")

    case GET -> Root :? _ +& ToQueryParam(Left(toError)) =>
      BadRequest(s"Invalid 'to' currency: $toError")

    case GET -> Root :? FromQueryParam(Right(from)) +& ToQueryParam(Right(to)) if from == to =>
      BadRequest("The 'from' and 'to' currencies cannot be the same.")

    case GET -> Root :? FromQueryParam(Right(from)) +& ToQueryParam(Right(to)) =>
      rates.get(RatesProgramProtocol.GetRatesRequest(from, to)).flatMap {
        case Right(rate) => Ok(rate.asGetApiResponse)
        case Left(programError) =>
          programError match {
            case RateLookupFailed(message) =>
              NotFound(message)
            case _ => InternalServerError("An unexpected error occurred.")
          }
      }
  }

  val routes: HttpRoutes[F] = Router(
    prefixPath -> httpRoutes
  )

}
