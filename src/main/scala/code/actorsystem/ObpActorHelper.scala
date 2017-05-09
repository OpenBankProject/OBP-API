package code.actorsystem

import code.api.APIFailure
import net.liftweb.common._

trait ObpActorHelper {

  def extractResult[T](in: T) = {
    in match {
        case pf: ParamFailure[_] =>
          pf.param match {
            case af: APIFailure => af
            case f: Failure => f
            case _ => pf
          }
        case af: APIFailure => af
        case f: Failure => f
        case l: List[T] => l
        case s: Set[T] => s
        case Full(r) => r
        case t: T => t
        case _ => APIFailure(s"result extraction failed", 501)
      }
  }
}