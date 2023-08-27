package forex.services.rates

import cats.effect.Sync
import forex.domain.Rate
import forex.services.RatesService
import forex.services.rates.cache.CacheService
import forex.services.rates.interpreters._

object Interpreters {
  def live[F[_] : Sync](ratesCache: CacheService[F, Rate.Pair, Rate]): RatesService[F] =
    new OneFrameLive[F](ratesCache)
}
