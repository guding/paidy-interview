package forex.services.rates.interpreters

import cats.effect.Sync
import cats.implicits.{catsSyntaxApplicativeError}
import cats.syntax.functor._
import forex.config.OneFrameApiConfig
import forex.domain.{Currency, Price, Rate, Timestamp}
import forex.services.rates.Algebra
import forex.services.rates.errors._
import io.circe.Decoder
import org.http4s._
import org.http4s.circe.jsonOf
import org.http4s.client.Client
import org.http4s.client.dsl.Http4sClientDsl

import java.time.OffsetDateTime

case class OneFrameResponse(
    from: String,
    to: String,
    bid: BigDecimal,
    ask: BigDecimal,
    price: BigDecimal,
    time_stamp: OffsetDateTime
)

object OneFrameResponse {
  implicit val rateDecoder: Decoder[OneFrameResponse] = Decoder.forProduct6(
    "from",
    "to",
    "bid",
    "ask",
    "price",
    "time_stamp"
  )(
    (from: String, to: String, bid: BigDecimal, ask: BigDecimal, price: BigDecimal, time_stamp: String) =>
      OneFrameResponse(from, to, bid, ask, price, OffsetDateTime.parse(time_stamp))
  )
}

class OneFrameLive[F[_]: Sync](httpClient: Client[F], config: OneFrameApiConfig)
    extends Algebra[F]
    with Http4sClientDsl[F] {

  implicit val rateEntityDecoder: EntityDecoder[F, List[OneFrameResponse]] = jsonOf[F, List[OneFrameResponse]]

  override def get(pair: Rate.Pair): F[Error Either Rate] =
    fetchRate(pair).attempt.map {
      case Right(rate) => Right(rate)
      case Left(err)   => Left(Error.OneFrameLookupFailed(err.getMessage))
    }

  private def fetchRate(pair: Rate.Pair): F[Rate] = {
    val request = Request[F](
      method = Method.GET,
      headers = Headers.of(Header("token", config.token)),
      uri = Uri
        .unsafeFromString(s"${config.endpoint}")
        .withPath("/rates")
        .withQueryParam("pair", Rate.showPair.show(pair))
    )

    httpClient.expect[List[OneFrameResponse]](request)(rateEntityDecoder).map { responses =>
      responses.headOption match {
        case Some(response) =>
          Rate(
            Rate.Pair(Currency.fromString(response.from), Currency.fromString(response.to)),
            Price(response.price),
            Timestamp(response.time_stamp)
          )
        case None => throw new Exception("Expected a rate in the response but got none")
      }
    }
  }

}
