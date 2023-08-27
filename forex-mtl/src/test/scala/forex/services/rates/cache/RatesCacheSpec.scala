import cats.effect._
import forex.config.OneFrameApiConfig
import forex.domain._
import forex.services.rates.cache.{ OneFrameResponse, RatesCache }
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.http4s.client.Client
import org.http4s._
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import org.scalatestplus.mockito.MockitoSugar

class RatesCacheSpec extends AnyFlatSpec with Matchers with MockitoSugar {

  implicit val timer: Timer[IO]  = IO.timer(scala.concurrent.ExecutionContext.global)
  val mockHttpClient: Client[IO] = mock[Client[IO]]
  val config                     = OneFrameApiConfig("mockEndpoint", "mockToken")
  val ratesCache                 = new RatesCache[IO](mockHttpClient, config)

  "RatesCache" should "refresh all rates" in {
    val pair = Rate.Pair(Currency.USD, Currency.EUR)
    val rateResponse = List(
      OneFrameResponse("USD", "EUR", BigDecimal(1), BigDecimal(1), BigDecimal(1), java.time.OffsetDateTime.now)
    )
    when(
      mockHttpClient.expect[List[OneFrameResponse]](any[Request[IO]])(any[EntityDecoder[IO, List[OneFrameResponse]]])
    ).thenReturn(IO(rateResponse))

    ratesCache.refreshAll.unsafeRunSync()
    val fetchedRate = ratesCache.get(pair).unsafeRunSync()

    fetchedRate.get.pair shouldEqual pair
  }

  it should "get a rate from the cache" in {
    val pair = Rate.Pair(Currency.USD, Currency.JPY)
    val now = java.time.OffsetDateTime.now
    val rateResponse = List(
      OneFrameResponse("USD", "JPY", BigDecimal(1), BigDecimal(1), BigDecimal(100), now)
    )
    when(
      mockHttpClient.expect[List[OneFrameResponse]](any[Request[IO]])(any[EntityDecoder[IO, List[OneFrameResponse]]])
    ).thenReturn(IO(rateResponse))
    val rate = Rate(pair, Price(BigDecimal(100)), Timestamp(now))

    ratesCache.refreshAll.unsafeRunSync()
    val fetchedRate = ratesCache.get(pair).unsafeRunSync()

    fetchedRate shouldEqual Some(rate)
  }

}
