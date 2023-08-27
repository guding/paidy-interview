package forex.services.rates.interpreters

import cats.effect.{ IO }
import forex.config.OneFrameApiConfig
import forex.domain.{ Currency, Price, Rate }
import forex.services.rates.errors.Error.OneFrameLookupFailed
import io.circe.literal.JsonStringContext
import org.http4s._
import org.http4s.circe._
import org.http4s.client.Client
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class OneFrameLiveSpec extends AnyFlatSpec with Matchers {

  val mockResponseBody =
    json"""
      [
        {
          "from": "USD",
          "to": "JPY",
          "bid": 0.8025923738995486,
          "ask": 0.44608644764413763,
          "price": 0.6243394107718431,
          "time_stamp": "2023-08-27T13:15:33.482Z"
        }
      ]
    """
  val unkownCurrencyResponseBody =
    json"""
      [
        {
            "from": "XYZ",
            "to": "JPY",
            "bid": 0.8025923738995486,
            "ask": 0.44608644764413763,
            "price": 0.6243394107718431,
            "time_stamp": "2023-08-27T13:15:33.482Z"
          }
      ]
    """

  val bodyStream: fs2.Stream[IO, Byte] = EntityEncoder[IO, io.circe.Json].toEntity(mockResponseBody).body
  val unkownCurrencyBodyStream: fs2.Stream[IO, Byte] =
    EntityEncoder[IO, io.circe.Json].toEntity(unkownCurrencyResponseBody).body

  val mockResponse: Response[IO] = Response[IO](
    status = Status.Ok,
    body = bodyStream
  )

  val unknownCurrencyResponse: Response[IO] = Response[IO](
    status = Status.Ok,
    body = unkownCurrencyBodyStream
  )

  val testConfig = OneFrameApiConfig(endpoint = "http://mock.endpoint", token = "mock-token")

  "OneFrameLive#get" should "fetch a rate successfully" in {
    val mockHttpClient: Client[IO] = Client.fromHttpApp(HttpApp.pure(mockResponse))
    val oneFrameService            = new OneFrameLive[IO](mockHttpClient, testConfig)

    val result = oneFrameService.get(Rate.Pair(Currency.USD, Currency.JPY)).unsafeRunSync()

    result should be(Symbol("right")) // It should be a Right indicating success
    result.toOption.get.pair shouldEqual Rate.Pair(Currency.USD, Currency.JPY)
    result.toOption.get.price shouldEqual Price(BigDecimal("0.6243394107718431"))
  }

  it should "handle unknown currencies in the response" in {

    val clientWithUnknownCurrency = Client.fromHttpApp[IO](HttpApp.pure(unknownCurrencyResponse))
    val service                   = new OneFrameLive[IO](clientWithUnknownCurrency, testConfig)

    service.get(Rate.Pair(Currency.USD, Currency.JPY)).unsafeRunSync() match {
      case Left(OneFrameLookupFailed(_)) => succeed
      case _                             => fail("Expected an OneFrameLookupFailed error due to unknown currency")
    }
  }
}
