/**
Open Bank Project - API
Copyright (C) 2011-2015, TESOBE / Music Pictures Ltd

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.

Email: contact@tesobe.com
TESOBE / Music Pictures Ltd
Osloerstrasse 16/17
Berlin 13359, Germany

  This product includes software developed at
  TESOBE (http://www.tesobe.com/)
  by
  Simon Redfern : simon AT tesobe DOT com
  Stefan Bethge : stefan AT tesobe DOT com
  Everett Sochowski : everett AT tesobe DOT com
  Ayoub Benali: ayoub AT tesobe DOT com

 */

package code.api

import code.api.util.APIUtil
import net.liftweb.http.rest.RestHelper
import net.liftweb.http.Req
import net.liftweb.common._
import net.liftweb.http.LiftResponse
import net.liftweb.http.JsonResponse
import APIUtil._
import code.model.User
import code.api.OAuthHandshake._
import net.liftweb.json.JsonAST.JValue
import net.liftweb.json.Extraction

trait APIFailure{
  val msg : String
  val responseCode : Int
}

object APIFailure {
  def apply(message : String, httpResponseCode : Int) : APIFailure = new APIFailure{
    val msg = message
    val responseCode = httpResponseCode
  }
}

//if you change this, think about backwards compatibility! All existing
//versions of the API return this failure message, so if you change it, make sure
//that all stable versions retain the same behavior
case class UserNotFound(providerId : String, userId: String) extends APIFailure {
  val responseCode = 400 //TODO: better as 404? -> would break some backwards compatibility (or at least the tests!)
  
  //to reiterate the comment about preserving backwards compatibility:
  //consider the case that an app may be parsing this string to decide what message to show their users
  //e.g. when granting view permissions, an app may not give their users a choice of provider and only
  //allow them to grant permissions to users from a certain hardcoded provider. In this case, showing this error
  //message is undesired and confusing. So in fact that app may be doing some regex stuff to try to match the string below
  //so that they can provide a useful message to their users. Obviously in the future this should be redesigned in a better
  //way, perhaps by using error codes.
  val msg = s"user $userId not found at provider $providerId"
}

trait OBPRestHelper extends RestHelper with Loggable {

  implicit def errorToJson(error: ErrorMessage): JValue = Extraction.decompose(error)
  implicit def successToJson(success: SuccessMessage): JValue = Extraction.decompose(success)

  val VERSION : String
  def vPlusVersion = "v" + VERSION
  def apiPrefix = ("obp" / vPlusVersion).oPrefix(_)

  /*
  An implicit function to convert magically between a Boxed JsonResponse and a JsonResponse
  If we have something good, return it. Else log and return an error.
  */
  implicit def jsonResponseBoxToJsonResponse(box: Box[JsonResponse]): JsonResponse = {
    box match {
      case Full(r) => r
      case ParamFailure(_, _, _, apiFailure : APIFailure) => {
        logger.info("jsonResponseBoxToJsonResponse case ParamFailure says: API Failure: " + apiFailure.msg + " ($apiFailure.responseCode)")
        errorJsonResponse(apiFailure.msg, apiFailure.responseCode)
      }
      case Failure(msg, _, _) => {
        logger.info("jsonResponseBoxToJsonResponse case Failure API Failure: " + msg)
        errorJsonResponse(msg)
      }
      case _ => errorJsonResponse()
    }
  }

  /*
  A method which takes
    a Request r
    and
    a partial function h
      which takes
      a Request
      and
      a User
      and returns a JsonResponse
    and returns a JsonResponse (but what about the User?)


   */
  def failIfBadJSON(r: Req, h: (PartialFunction[Req, Box[User] => Box[JsonResponse]])): Box[User] => Box[JsonResponse] = {
    // Check if the content-type is text/json or application/json
    r.json_? match {
      case true =>
        //logger.info("failIfBadJSON says: Cool, content-type is json")
        r.json match {
          case Failure(msg, _, _) => (x: Box[User]) => Full(errorJsonResponse(s"Error: Invalid JSON: $msg"))
          case _ => h(r)
        }
      case false => h(r)
    }
  }

  def failIfBadOauth(fn: (Box[User]) => Box[JsonResponse]) : JsonResponse = {
    if (isThereAnOAuthHeader) {
      getUser match {
        case Full(u) => fn(Full(u))
        case ParamFailure(_, _, _, apiFailure : APIFailure) => errorJsonResponse(apiFailure.msg, apiFailure.responseCode)
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

  /*
  oauthServe wraps many get calls and probably all calls that post (and put and delete) json data.
  Since the URL path matching will fail if there is invalid JsonPost, and this leads to a generic 404 response which is confusing to the developer,
  we want to detect invalid json *before* matching on the url so we can fail with a more specific message.
  See SandboxApiCalls for an example of JsonPost being used.
  The down side is that we might be validating json more than once per request and we're doing work before authentication is completed
  (possible DOS vector?)

  TODO: should this be moved to def serve() further down?
   */

  def oauthServe(handler : PartialFunction[Req, Box[User] => Box[JsonResponse]]) : Unit = {
    val obpHandler : PartialFunction[Req, () => Box[LiftResponse]] = {
      new PartialFunction[Req, () => Box[LiftResponse]] {
        def apply(r : Req) = {
          //check (in that order):
          //if request is correct json
          //if request matches PartialFunction cases for each defined url
          //if request has correct oauth headers
          failIfBadOauth {
            failIfBadJSON(r, handler)
          }
        }
        def isDefinedAt(r : Req) = {
          //if the content-type is json and json parsing failed, simply accept call but then fail in apply() before
          //the url cases don't match because json failed
          r.json_? match {
            case true =>
              //Try to evaluate the json
              r.json match {
                case Failure(msg, _, _) => true
                case _ => handler.isDefinedAt(r)
              }
            case false => handler.isDefinedAt(r)
          }
        }
      }
    }
    serve(obpHandler)
  }

  override protected def serve(handler: PartialFunction[Req, () => Box[LiftResponse]]) : Unit = {
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