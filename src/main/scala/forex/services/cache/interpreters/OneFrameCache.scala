package forex.services.cache.interpreters

import java.time.LocalDateTime

import cats.Applicative
import cats.effect.{Async, Concurrent}
import cats.effect.concurrent.MVar
import forex.common.parser.CommonJsonParser
import forex.domain.{Currency, Rate}
import forex.programs.rates.CacheState
import forex.services.cache.Algebra
import forex.services.cache.frame.Protocol.FrameRate
import forex.services.rates.errors
import forex.services.rates.errors._
import cats.implicits._
import scalaj.http.Http

import scala.annotation.tailrec

class OneFrameCache[F[_]: Applicative: Concurrent] extends Algebra[F] with CommonJsonParser {

  val url = "http://localhost:8088"

  override def refresh(state: MVar[F, Option[CacheState]]): F[Either[errors.Error, Option[CacheState]]] = {
    Async[F].async { cb =>

      val timestamp = LocalDateTime.now()
      val allRates: List[FrameRate] = allRatesResponse()
      val mapByFrom = getMapByFrom(allRates, Map[Rate.Pair, Rate]())

      val newCache: F[Option[CacheState]] = for {
        _ <- state.take
        _ <- state.put(Some(CacheState(timestamp, mapByFrom)))
        v <- state.read
      } yield v

      newCache.map(_.asRight[Error]).map(cacheE => {
        cb(cacheE.asRight[Throwable])
      })
    }
  }

  @tailrec
  private def getMapByFrom(allRates: List[FrameRate], map: Map[Rate.Pair, Rate]): Map[Rate.Pair, Rate] = {
    import forex.services.cache.frame.Converters._
    allRates match {
      case frameRate :: otherFrameRates =>
        val pair = Rate.Pair(frameRate.from, frameRate.to)
        val rate = frameRate.asRate
        getMapByFrom(otherFrameRates, map + (pair -> rate))
      case Nil => map
    }
  }

  // todo - add try
  private def allRatesResponse(): List[FrameRate] = {
    println("call external service")
    val allPairs = Currency.allPairs.map(p => s"pair=${p.from}${p.to}").reduce(_ + "&" + _)
    val response = Http(s"$url/rates?$allPairs")
      .header("token", "10dc303535874aeccc86a8251e6992f5").asString
    parseTo[List[FrameRate]](response.body) match {
      case Right(list) => list
      case Left(m) =>
        println(" m -- " + m)
        Nil
    }
  }
}
