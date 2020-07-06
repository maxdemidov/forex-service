package forex.programs.rates

import errors._
import forex.programs.rates.Protocol.GetRatesResponse

trait Algebra[F[_]] {
  def get(requests: List[Protocol.GetRatesRequest]): F[Error Either List[GetRatesResponse]]
}
