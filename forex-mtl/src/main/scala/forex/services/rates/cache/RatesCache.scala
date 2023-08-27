package forex.services.rates.cache

import cats.effect.{Sync, Timer}
import cats.implicits._
import com.github.blemale.scaffeine.{Cache, Scaffeine}
import forex.config.OneFrameApiConfig
import forex.domain.Currency.{AUD, CAD, CHF, EUR, GBP, JPY, NZD, SGD, USD}
import forex.domain.{Currency, Price, Rate, Timestamp}
import io.circe.Decoder
import org.http4s.circe.jsonOf
import org.http4s.client.Client
import org.http4s._

import java.time.OffsetDateTime
import scala.concurrent.duration._

trait CacheService[F[_], K, V] {
  def get(key: K): F[Option[V]]
  def refreshAll: F[Unit]
}

case class OneFrameResponse(
    from: String,
    to: String,
    bid: BigDecimal,
    ask: BigDecimal,
    price: BigDecimal,
    time_stamp: OffsetDateTime
)
case class OneFrameErrorResponse(error: String)

object OneFrameResponse {
  implicit val rateDecoder: Decoder[OneFrameResponse] = Decoder.forProduct6(
    "from",
    "to",
    "bid",
    "ask",
    "price",
    "time_stamp"
  )(OneFrameResponse.apply)
}

object OneFrameErrorResponse {
  implicit val errorDecoder: Decoder[OneFrameErrorResponse] = Decoder.forProduct1("error")(OneFrameErrorResponse.apply)
}

class RatesCache[F[_]: Sync: Timer](httpClient: Client[F], config: OneFrameApiConfig)
    extends CacheService[F, Rate.Pair, Rate] {

  private val underlyingCache: Cache[Rate.Pair, Rate] = Scaffeine()
    .recordStats()
    .expireAfterWrite(10.minutes)
    .maximumSize(1000)
    .build[Rate.Pair, Rate]()

  private def put(pair: Rate.Pair, rate: Rate): F[Unit] = Sync[F].delay {
    underlyingCache.put(pair, rate)
  }

  override def get(pair: Rate.Pair): F[Option[Rate]] = Sync[F].delay {
    underlyingCache.getIfPresent(pair)
  }

  override def refreshAll: F[Unit] =
    fetchAllRates.flatMap(_.toList.traverse_((put _).tupled))

  def cacheRefreshStream(): fs2.Stream[F, Unit] =
    fs2.Stream.eval(this.refreshAll) ++ fs2.Stream.awakeEvery[F](4.5.minutes) >> fs2.Stream.eval(this.refreshAll)

  private def toRatePair(from: String, to: String): Option[Rate.Pair] =
    for {
      fromCurrency <- Currency.fromString(from).toOption
      toCurrency <- Currency.fromString(to).toOption
    } yield Rate.Pair(fromCurrency, toCurrency)

  private def fetchAllRates: F[Map[Rate.Pair, Rate]] = {
    val allCurrencies = List(AUD, CAD, CHF, EUR, GBP, NZD, JPY, SGD, USD)

    val allCurrencyPairs: List[Rate.Pair] = for {
      base <- allCurrencies
      counter <- allCurrencies if base != counter
    } yield Rate.Pair(base, counter)

    val queryString: String = allCurrencyPairs.map(pair => s"pair=${Rate.showPair.show(pair)}").mkString("&")

    val uriStr = s"${config.endpoint}/rates?$queryString"

    val request = Request[F](
      method = Method.GET,
      headers = Headers.of(Header("token", config.token)),
      uri = Uri
        .unsafeFromString(uriStr)
    )

    implicit val rateEntityDecoder: EntityDecoder[F, List[OneFrameResponse]] = jsonOf[F, List[OneFrameResponse]]
    implicit val errorEntityDecoder: EntityDecoder[F, OneFrameErrorResponse] = jsonOf[F, OneFrameErrorResponse]

    httpClient.run(request).use {
      case Status.Successful(r) =>
        r.attemptAs[List[OneFrameResponse]].leftMap(_.message).value.flatMap {
          case Right(responses) =>
            Sync[F].pure(responses.flatMap { response =>
              toRatePair(response.from, response.to).map { pair =>
                pair -> Rate(pair, Price(response.price), Timestamp(response.time_stamp))
              }
            }.toMap)
          case Left(_) =>
            r.attemptAs[OneFrameErrorResponse].value.flatMap {
              case Right(_) =>
                // Log error in the future
                Sync[F].pure(Map.empty[Rate.Pair, Rate])
              case Left(_) =>
                // Log error in the future
                Sync[F].pure(Map.empty[Rate.Pair, Rate])
            }
        }
      case r =>
        r.as[String].flatMap { _ =>
          // Log error in the future
          Sync[F].pure(Map.empty[Rate.Pair, Rate])
        }
    }
  }
}
