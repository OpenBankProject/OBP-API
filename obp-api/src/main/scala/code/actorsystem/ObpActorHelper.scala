package code.actorsystem

import code.api.APIFailure
import net.liftweb.common._

trait ObpActorHelper {

  def extractResult(in: Any) = {
    in match {
      case ParamFailure(_, _, _, param@ (_:APIFailure | _: Failure)) => param
      case pf: ParamFailure[_] => pf
      case Full(r) => r
      case _ => in
    }
  }
}