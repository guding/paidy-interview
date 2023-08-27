package forex.services.rates.cache

import cats.effect.{ Sync, Timer }
import cats.implicits.{ toFlatMapOps, toFoldableOps, toFunctorOps }
import com.github.blemale.scaffeine.{ Cache, Scaffeine }
import forex.config.OneFrameApiConfig
import forex.domain.Currency.{ AUD, CAD, CHF, EUR, GBP, JPY, NZD, SGD, USD }
import forex.domain.{ Currency, Price, Rate, Timestamp }
import io.circe.Decoder
import org.http4s.circe.jsonOf
import org.http4s.client.Client
import org.http4s._

import java.time.OffsetDateTime
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.{ DurationInt, FiniteDuration }

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

class RatesCache[F[_]: Sync: Timer](httpClient: Client[F], config: OneFrameApiConfig)
    extends CacheService[F, Rate.Pair, Rate] {

  private val underlyingCache: Cache[Rate.Pair, Rate] = Scaffeine()
    .recordStats()
    .expireAfterWrite(new FiniteDuration(10, TimeUnit.MINUTES))
    .maximumSize(1000)
    .build[Rate.Pair, Rate]()

  private def put(pair: Rate.Pair, rate: Rate): F[Unit] = Sync[F].delay {
    underlyingCache.put(pair, rate)
  }

  override def get(pair: Rate.Pair): F[Option[Rate]] = Sync[F].delay {
    underlyingCache.getIfPresent(pair)
  }

  override def refreshAll: F[Unit] =
    fetchAllRates.flatMap { rates =>
      rates.toList.traverse_ {
        case (pair, rate) => put(pair, rate)
      }
    }

  def cacheRefreshStream(): fs2.Stream[F, Unit] =
    fs2.Stream.eval(this.refreshAll) ++ fs2.Stream.awakeEvery[F](5.minutes) >> fs2.Stream.eval(this.refreshAll)

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

    httpClient.expect[List[OneFrameResponse]](request)(rateEntityDecoder).map { responses =>
      responses.map { response =>
        Rate.Pair(Currency.fromString(response.from), Currency.fromString(response.to)) ->
          Rate(
            Rate.Pair(Currency.fromString(response.from), Currency.fromString(response.to)),
            Price(response.price),
            Timestamp(response.time_stamp)
          )
      }.toMap
    }
  }
}
