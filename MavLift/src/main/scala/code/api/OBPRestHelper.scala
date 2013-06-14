package code.api

import net.liftweb.http.rest.RestHelper
import net.liftweb.http.Req
import net.liftweb.common._
import net.liftweb.http.LiftResponse
import net.liftweb.http.JsonResponse
import code.util.APIUtil._
import code.model.User
import code.api.OAuthHandshake._

class OBPRestHelper extends RestHelper with Loggable {

  implicit def jsonResponseBoxToJsonReponse(box: Box[JsonResponse]): JsonResponse = {
    box match {
      case Full(r) => r
      case Failure(msg, _, _) => {
        logger.info("API Failure: " + msg)
        errorJsonResponse(msg)
      }
      case _ => errorJsonResponse()
    }
  }

  def failIfBadOauth(fn: (Box[User]) => Box[JsonResponse]) : JsonResponse = {
    if (isThereAnOAuthHeader) {
      getUser match {
        case Full(u) => fn(Full(u))
        case Failure(msg, _, _) => errorJsonResponse(msg)
        case _ => errorJsonResponse("oauth error")
      }
    } else fn(Empty)
  }

  class RichStringList(list: List[String]) {
    val listLen = list.length

    /**
     * Normally we would use ListServeMagic's prefix function, but it works with PartialFunction[Req, () => Box[LiftResponse]]
     * instead of the PartialFunction[Req, Box[User] => Box[JsonResponse]] that we need. This function does the same thing, really.
     */
    def oPrefix(pf: PartialFunction[Req, Box[User] => Box[JsonResponse]]): PartialFunction[Req, Box[User] => Box[JsonResponse]] =
      new PartialFunction[Req, Box[User] => Box[JsonResponse]] {
        def isDefinedAt(req: Req): Boolean =
          req.path.partPath.startsWith(list) && {
            pf.isDefinedAt(req.withNewPath(req.path.drop(listLen)))
          }

        def apply(req: Req): Box[User] => Box[JsonResponse] =
          pf.apply(req.withNewPath(req.path.drop(listLen)))
      }
  }

  //Give all lists of strings in OBPRestHelpers the oPrefix method
  implicit def stringListToRichStringList(list : List[String]) : RichStringList = new RichStringList(list)

  def oauthServe(handler : PartialFunction[Req, Box[User] => Box[JsonResponse]]) : Unit = {
    val obpHandler : PartialFunction[Req, () => Box[LiftResponse]] = {
      new PartialFunction[Req, () => Box[LiftResponse]] {
        def apply(r : Req) = {
          failIfBadOauth {
            handler(r)
          }
        }
        def isDefinedAt(r : Req) = handler.isDefinedAt(r)
      }
    }
    serve(obpHandler)
  }

  override protected def serve(handler: PartialFunction[Req, () => Box[LiftResponse]]) : Unit= {

    val obpHandler : PartialFunction[Req, () => Box[LiftResponse]] = {
      new PartialFunction[Req, () => Box[LiftResponse]] {
        def apply(r : Req) = {
          //Wraps the partial function with some logging
          logAPICall
          handler(r)
        }
        def isDefinedAt(r : Req) = handler.isDefinedAt(r)
      }
    }
    super.serve(obpHandler)
  }


}