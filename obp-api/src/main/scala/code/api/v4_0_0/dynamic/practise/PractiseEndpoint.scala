package code.api.v4_0_0.dynamic.practise

import code.api.util.APIUtil.errorJsonResponse
import code.api.util.CallContext
import code.api.util.ErrorMessages.{InvalidJsonFormat, InvalidRequestPayload}
import code.api.util.NewStyle.HttpCode
import code.api.v4_0_0.dynamic.DynamicCompileEndpoint
import net.liftweb.common.{Box, EmptyBox, Full}
import net.liftweb.http.{JsonResponse, Req}
import net.liftweb.json.MappingException

import scala.concurrent.Future

/**
 * practise new endpoint at this object, don't commit you practise code to git
 */
object PractiseEndpoint extends DynamicCompileEndpoint {
  // all request case classes
  case class RequestRootJsonClass(name: String, age: Long, hobby: Option[List[String]])


  // all response case classes
  case class ResponseRootJsonClass(my_user_id: String, name: String, age: Long, hobby: Option[List[String]])


  // request method
  val requestMethod = "POST"
  val requestUrl = "/my_user/MY_USER_ID"

  // copy the whole method body as "dynamicResourceDoc" method body
  override protected def process(callContext: CallContext, request: Req): Box[JsonResponse] = {
    // please add import sentences here, those used by this method

    val Some(resourceDoc) = callContext.resourceDocument
    val hasRequestBody = request.body.isDefined

    // get Path Parameters, example:
    // if the requestUrl of resourceDoc is /hello/banks/BANK_ID/world
    // the request path is /hello/banks/bank_x/world
    //pathParams.get("BANK_ID") will get Option("bank_x") value
    val pathParams = getPathParams(callContext, request)
    val myUserId = pathParams("MY_USER_ID")


    val requestEntity = request.json match {
      case Full(zson) =>
        try {
          zson.extract[RequestRootJsonClass]
        } catch {
          case e: MappingException =>
            return Full(errorJsonResponse(s"$InvalidJsonFormat ${e.msg}"))
        }
      case _: EmptyBox =>
        return Full(errorJsonResponse(s"$InvalidRequestPayload Current request has no payload"))
    }


    // please add business logic here
    val responseBody:ResponseRootJsonClass = ResponseRootJsonClass(s"${myUserId}_from_path", requestEntity.name, requestEntity.age, requestEntity.hobby)
    Future.successful {
      (responseBody, HttpCode.`200`(callContext.callContext))
    }
  }

}
