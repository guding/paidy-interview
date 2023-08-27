import cats.effect._
import forex.config.OneFrameApiConfig
import forex.domain._
import forex.services.rates.cache.{OneFrameErrorResponse, OneFrameResponse, RatesCache}
import io.circe.generic.auto._
import org.http4s._
import org.http4s.circe.CirceEntityCodec.circeEntityEncoder
import org.http4s.client.Client
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.mockito.MockitoSugar

class RatesCacheSpec extends AnyFlatSpec with Matchers with MockitoSugar with BeforeAndAfterEach{

  implicit val timer: Timer[IO]  = IO.timer(scala.concurrent.ExecutionContext.global)
  val mockHttpClient: Client[IO] = mock[Client[IO]]
  val config                     = OneFrameApiConfig("mockEndpoint", "mockToken")
  var ratesCache: RatesCache[IO] = _

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockHttpClient)
    ratesCache = new RatesCache[IO](mockHttpClient, config)
  }

  "RatesCache" should "refresh all rates" in {
    val pair = Rate.Pair(Currency.USD, Currency.EUR)

    val rateResponse = List(
      OneFrameResponse("USD", "EUR", BigDecimal(1), BigDecimal(1), BigDecimal(1), java.time.OffsetDateTime.now)
    )
    when(mockHttpClient.run(any[Request[IO]])).thenReturn(
      Resource.pure[IO, Response[IO]](Response[IO](status = Status.Ok).withEntity(rateResponse))
    )

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
    when(mockHttpClient.run(any[Request[IO]])).thenReturn(
        Resource.pure[IO, Response[IO]](Response[IO](status = Status.Ok).withEntity(rateResponse))
    )


    val rate = Rate(pair, Price(BigDecimal(100)), Timestamp(now))
    ratesCache.refreshAll.unsafeRunSync()

    val fetchedRate = ratesCache.get(pair).unsafeRunSync()
    fetchedRate shouldEqual Some(rate)
  }

  it should "handle decoding errors gracefully" in {
    when(mockHttpClient.run(any[Request[IO]])).thenReturn(
      Resource.pure[IO, Response[IO]](Response[IO](status = Status.Ok).withEntity("Invalid json"))
    )

    ratesCache.refreshAll.unsafeRunSync()

    val fetchedRate = ratesCache.get(Rate.Pair(Currency.USD, Currency.EUR)).unsafeRunSync()
    fetchedRate shouldBe None
  }

  it should "handle OneFrameErrorResponse" in {
    val errorResponse = OneFrameErrorResponse("error message")
    when(mockHttpClient.run(any[Request[IO]])).thenReturn(
      Resource.pure[IO, Response[IO]](Response[IO](status = Status.Ok).withEntity(errorResponse))
    )

    ratesCache.refreshAll.unsafeRunSync()

    val fetchedRate = ratesCache.get(Rate.Pair(Currency.USD, Currency.JPY)).unsafeRunSync()
    fetchedRate shouldBe None
  }

  it should "handle non-successful status codes" in {
    when(mockHttpClient.run(any[Request[IO]])).thenReturn(
      Resource.pure[IO, Response[IO]](Response[IO](status = Status.InternalServerError))
    )

    ratesCache.refreshAll.unsafeRunSync()

    val fetchedRate = ratesCache.get(Rate.Pair(Currency.USD, Currency.JPY)).unsafeRunSync()
    fetchedRate shouldBe None
  }
}
