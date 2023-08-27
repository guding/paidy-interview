package forex.programs.rates

import forex.services.rates.errors.Error.RateNotFound
import forex.services.rates.errors.{ Error => RatesServiceError }

object errors {

  sealed trait Error extends Exception
  object Error {
    final case class RateLookupFailed(msg: String) extends Error
    final case class UnexpectedError(msg: String) extends Error
  }

  def toProgramError(error: RatesServiceError): Error = error match {
    case RatesServiceError.OneFrameLookupFailed(msg) => Error.RateLookupFailed(msg)
    case RateNotFound(msg)                           => Error.RateLookupFailed(msg)
    case _                                           => Error.UnexpectedError("Unexpected error")
  }
}
