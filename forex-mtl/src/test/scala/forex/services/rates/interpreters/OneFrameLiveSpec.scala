import cats.effect.IO
import forex.domain._
import forex.services.rates.cache.CacheService
import forex.services.rates.errors.Error.RateNotFound
import forex.services.rates.interpreters.OneFrameLive
import org.mockito.Mockito._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.mockito.MockitoSugar

class OneFrameLiveSpec extends AnyFlatSpec with Matchers with MockitoSugar {

  val mockRatesCache: CacheService[IO, Rate.Pair, Rate] = mock[CacheService[IO, Rate.Pair, Rate]]
  val service                                           = new OneFrameLive[IO](mockRatesCache)

  "OneFrameLive" should "successfully fetch a rate from the cache" in {
    val pair = Rate.Pair(Currency.USD, Currency.JPY)
    val rate = Rate(pair, Price(BigDecimal(100)), Timestamp(java.time.OffsetDateTime.now))

    when(mockRatesCache.get(pair)).thenReturn(IO(Some(rate)))

    val result = service.get(pair).unsafeRunSync()

    result shouldEqual Right(rate)
  }

  it should "return an error if the rate is not found in the cache" in {
    val pair = Rate.Pair(Currency.USD, Currency.EUR)

    when(mockRatesCache.get(pair)).thenReturn(IO(None))

    val result = service.get(pair).unsafeRunSync()

    result shouldBe a[Left[_, _]]
    result.left.toOption.get shouldEqual RateNotFound(s"Rate not found for pair: $pair")
  }
}
