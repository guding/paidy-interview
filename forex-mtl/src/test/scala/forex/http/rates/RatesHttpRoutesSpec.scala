package forex.http.rates

import cats.effect._
import forex.domain.{Currency, Price, Rate, Timestamp}
import forex.programs.RatesProgram
import forex.programs.rates.Protocol.GetRatesRequest
import forex.programs.rates.errors.Error.{RateLookupFailed, UnexpectedError}
import org.http4s._
import org.http4s.implicits._
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.mockito.MockitoSugar.mock

class RatesHttpRoutesTest extends AnyFlatSpec with Matchers {

  implicit val cs: ContextShift[IO] = IO.contextShift(scala.concurrent.ExecutionContext.Implicits.global)

  // Assume you have a mock `rates` to pass to the `RatesHttpRoutes`
  val mockRates: RatesProgram[IO] = mock[RatesProgram[IO]]
  val routes                      = new RatesHttpRoutes(mockRates).routes

  "GET /rates?from=EUR&to=USD" should "return a successful response for valid currencies" in {
    when(mockRates.get(any[GetRatesRequest])).thenReturn(
      IO(Right(Rate(Rate.Pair(Currency.EUR, Currency.USD), new Price(100), Timestamp(java.time.OffsetDateTime.now))))
    )
    val request  = Request[IO](Method.GET, uri"/rates?from=EUR&to=USD")
    val response = routes.orNotFound.run(request).unsafeRunSync()

    response.status shouldBe Status.Ok
  }

  "GET /rates?from=EUR&to=EUR" should "return an error when 'from' and 'to' are the same" in {
    val request  = Request[IO](Method.GET, uri"/rates?from=EUR&to=EUR")
    val response = routes.orNotFound.run(request).unsafeRunSync()

    response.status shouldBe Status.BadRequest
  }

  "GET /rates?from=INVALID&to=USD" should "return an error for invalid 'from' currency" in {
    val request  = Request[IO](Method.GET, uri"/rates?from=INVALID&to=USD")
    val response = routes.orNotFound.run(request).unsafeRunSync()

    response.status shouldBe Status.BadRequest
  }

  "GET /rates?from=EUR&to=USD" should "return an error if rate lookup fails" in {
    val errorMessage = "EURUSD"
    when(mockRates.get(any[GetRatesRequest])).thenReturn(
      IO(Left(RateLookupFailed(errorMessage)))
    )
    val request = Request[IO](Method.GET, uri"/rates?from=EUR&to=USD")
    val response = routes.orNotFound.run(request).unsafeRunSync()

    response.status shouldBe Status.NotFound
    val responseBody = new String(response.body.compile.toList.unsafeRunSync().toArray)
    responseBody should be(errorMessage)
  }

  "GET /rates?from=EUR&to=USD" should "return an internal error for unexpected program errors" in {
    when(mockRates.get(any[GetRatesRequest])).thenReturn(
      IO(Left(UnexpectedError("")))
    )
    val request = Request[IO](Method.GET, uri"/rates?from=EUR&to=USD")
    val response = routes.orNotFound.run(request).unsafeRunSync()

    response.status shouldBe Status.InternalServerError
    val responseBody = new String(response.body.compile.toList.unsafeRunSync().toArray)
    responseBody should include("An unexpected error occurred.")
  }
}
