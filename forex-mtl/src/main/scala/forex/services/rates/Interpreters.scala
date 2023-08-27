package forex.services.rates

import cats.Applicative
import cats.effect.Sync
import forex.config.OneFrameApiConfig
import interpreters._
import org.http4s.client.Client

object Interpreters {
  def dummy[F[_]: Applicative]: Algebra[F] = new OneFrameDummy[F]()
  def live[F[_] : Sync](client: Client[F], config: OneFrameApiConfig): Algebra[F] =
    new OneFrameLive[F](client, config)
}
