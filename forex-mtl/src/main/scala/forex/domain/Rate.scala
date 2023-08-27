package forex.domain

import cats.Show

case class Rate(
    pair: Rate.Pair,
    price: Price,
    timestamp: Timestamp
)

object Rate {
  final case class Pair(
      from: Currency,
      to: Currency
  )
  implicit val showPair: Show[Pair] = Show.show { pair =>
    s"${Currency.show.show(pair.from)}${Currency.show.show(pair.to)}"
  }
}
