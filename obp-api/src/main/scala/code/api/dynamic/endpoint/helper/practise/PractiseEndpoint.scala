package code.api.dynamic.endpoint.helper.practise

import org.json4s._
import code.api.dynamic.endpoint.helper.DynamicCompileEndpoint

// any import statement here need be moved into the process method body

/**
 * practise new endpoint at this object, don't commit you practise code to git
 * 
 * This endpoint is only for testing new dynamic resource/messages method body.
 * eg: when you try the create dynamic resource doc endpoint, you need to prepare the method body properly.
 *   you can prepare the obp scala code just under the method:
 *    `process(callContext: CallContext, request: Req, pathParams: Map[String, String])`,
 *    
 *    
 * 
 */
object PractiseEndpoint extends DynamicCompileEndpoint {
  // don't modify these import statement
  import code.api.util.CallContext
  import code.api.util.ErrorMessages.{InvalidJsonFormat, InvalidRequestPayload}
  import code.api.util.NewStyle.HttpCode
  import code.api.util.APIUtil.OBPReturnType

  import cats.effect.IO
  import org.http4s.{Request, Response}
  import org.json4s.MappingException

  import scala.concurrent.Future
  import com.openbankproject.commons.ExecutionContext.Implicits.global
  import code.api.dynamic.endpoint.helper.DynamicCompileEndpoint._


  // request method
  val requestMethod = "POST"
  val requestUrl = "/my_user/MY_USER_ID"


  // all request case classes
  case class RequestRootJsonClass(name: String, age: Long, hobby: List[String])


  // all response case classes
  case class ResponseRootJsonClass(my_user_id: String, name: String, age: Long, hobby: List[String])

  // * is any bankId, if bound to other bankId, just modify this value to correct one
  override val boundBankId = "*"

  // copy the whole method body as "dynamicResourceDoc" method body
  override protected def
    process(callContext: CallContext, request: Request[IO], pathParams: Map[String, String]) : IO[Response[IO]] = {
    // please add import sentences here, those used by this method
    import code.api.util.NewStyle
    import code.api.v4_0_0.JSONFactory400

    val Some(resourceDoc) = callContext.resourceDocument
    // the request body is available as a String on the CallContext (read by Http4sCallContextBuilder)
    val hasRequestBody = callContext.httpBody.exists(_.nonEmpty)

    // get Path Parameters, example:
    // if the requestUrl of resourceDoc is /hello/banks/BANK_ID/world
    // the request path is /hello/banks/bank_x/world
    //pathParams.get("BANK_ID") will get Option("bank_x") value
    val myUserId = pathParams("MY_USER_ID")

    val requestEntity = callContext.httpBody.filter(_.nonEmpty) match {
      case Some(rawBody) =>
        try {
            com.openbankproject.commons.util.JsonAliases.parse(rawBody).extract[RequestRootJsonClass]
        } catch {
          case e: MappingException =>
            return errorResponse(s"$InvalidJsonFormat ${e.msg}")
        }
      case None =>
        return errorResponse(s"$InvalidRequestPayload Current request has no payload")
    }
    // please add business logic here
    val responseBody:ResponseRootJsonClass = ResponseRootJsonClass(s"${myUserId}_from_path", requestEntity.name, requestEntity.age,  requestEntity.hobby)

    for {
      (banks, callContext) <- NewStyle.function.getBanks(Some(callContext))
    } yield {
      (JSONFactory400.createBanksJson(banks), HttpCode.`200`(callContext))
    }
  }

}
