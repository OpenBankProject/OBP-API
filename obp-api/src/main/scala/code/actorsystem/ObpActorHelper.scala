package code.actorsystem

import code.api.APIFailure
import net.liftweb.common._
import net.liftweb.json.JsonAST.JValue

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
        case l: List[_] => l.asInstanceOf[List[T]]
        case s: Set[_] => s.asInstanceOf[Set[T]]
        case Full(r) => r
        case j: JValue => j
        case t: T => t
        case _ => APIFailure(s"result extraction failed", 501)
      }
  }
}