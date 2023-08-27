package forex.services.rates.interpreters

import cats.effect.Sync
import cats.implicits.catsSyntaxEitherId
import cats.syntax.functor._
import forex.domain.Rate
import forex.services.rates.Algebra
import forex.services.rates.cache.CacheService
import forex.services.rates.errors.Error.RateNotFound
import forex.services.rates.errors._

class OneFrameLive[F[_]: Sync](ratesCache: CacheService[F, Rate.Pair, Rate])
    extends Algebra[F]{

  override def get(pair: Rate.Pair): F[Error Either Rate] = ratesCache.get(pair).map {
    case Some(rate) => rate.asRight[Error]
    case None => RateNotFound(s"Rate not found for pair: $pair").asLeft[Rate]
  }
}
