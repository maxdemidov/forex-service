package forex.programs.rates

import errors._
import forex.programs.rates.Protocol.GetRatesResponse

trait Algebra[F[_]] {
  // todo - make one for list
  def get(request: Protocol.GetRatesRequest): F[Error Either GetRatesResponse]
  def get(requests: List[Protocol.GetRatesRequest]): F[List[Error] Either List[GetRatesResponse]]
}
