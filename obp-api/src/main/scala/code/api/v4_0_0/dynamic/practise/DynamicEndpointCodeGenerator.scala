package code.api.v4_0_0.dynamic.practise

import code.api.util.APIUtil.ResourceDoc
import code.api.v4_0_0.ResourceDocFragment
import com.google.common.base.CaseFormat
import com.openbankproject.commons.util.JsonUtils
import net.liftweb.json.JsonAST.{JBool, JDouble, JInt, JString}
import net.liftweb.json.{JArray, JObject}
import org.apache.commons.lang3.ArrayUtils

object DynamicEndpointCodeGenerator {

  def buildTemplate(fragment: ResourceDocFragment) = {
    val pathParamNames = ResourceDoc.findPathVariableNames(fragment.requestUrl)

    val pathVariables = if(ArrayUtils.isNotEmpty(pathParamNames)) {
      val variables = pathParamNames.map(it => s"""val ${CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.LOWER_CAMEL, it)} = pathParams("$it")""")
        .mkString("\n    ")
      s"""    // get Path Parameters, example:
        |    // if the requestUrl of resourceDoc is /hello/banks/BANK_ID/world
        |    // the request path is /hello/banks/bank_x/world
        |    //pathParams.get("BANK_ID") will get Option("bank_x") value
        |    val pathParams = getPathParams(callContext, request)
        |    $variables
        |""".stripMargin
    } else ""

    val requestBodyCaseClasses = if(fragment.exampleRequestBody.exists(it => it.isInstanceOf[JObject] || it.isInstanceOf[JArray]) &&
      (fragment.requestVerb == "POST" || fragment.requestVerb == "PUT")) {
      val Some(requestBody) = fragment.exampleRequestBody
      s"""  // all request case classes
         |  ${JsonUtils.toCaseClasses(requestBody, "Request")}
         |""".stripMargin
    } else ""

    val responseBodyCaseClasses = if(fragment.successResponseBody.exists(it => it.isInstanceOf[JObject] || it.isInstanceOf[JArray]) &&
      (fragment.requestVerb == "POST" || fragment.requestVerb == "PUT")) {
      val Some(responseBody) = fragment.successResponseBody
      s"""  // all response case classes
         |  ${JsonUtils.toCaseClasses(responseBody, "Response")}
         |""".stripMargin
    } else ""

    def requestEnityExp(str:String) =
      s"""    val requestEntity = request.json match {
      |        case Full(zson) =>
      |          try {
      |            zson.extract[$str]
      |          } catch {
      |            case e: MappingException =>
      |             return Full(errorJsonResponse(s"$$InvalidJsonFormat $${e.msg}"))
      |          }
      |        case _: EmptyBox =>
      |          return Full(errorJsonResponse(s"$$InvalidRequestPayload Current request has no payload"))
      |      }
      |""".stripMargin

    val requestEntity = fragment.exampleRequestBody match {
      case Some(JBool(_)) => requestEnityExp("Boolean")
      case Some(JInt(_)) => requestEnityExp("Long")
      case Some(JDouble(_)) => requestEnityExp("Double")
      case Some(JString(_)) => requestEnityExp("String")
      case Some(JObject(_)) | Some(JArray(_)) => requestEnityExp("RequestRootJsonClass")
      case _ => ""
    }

    val responseEntity = fragment.successResponseBody match {
      case Some(JBool(_)) => "val responseBody: Boolean = null"
      case Some(JInt(_)) => "val responseBody: Long = null"
      case Some(JDouble(_)) => "val responseBody: Double = null"
      case Some(JString(_)) => "val responseBody: String = null"
      case Some(JObject(_)) | Some(JArray(_)) => "val responseBody:ResponseRootJsonClass = null"
      case _ => "val responseBody = null"
    }

    s"""
      |$requestBodyCaseClasses
      |
      |$responseBodyCaseClasses
      |
      |  // request method
      |  val requestMethod = "${fragment.requestVerb}"
      |  val requestUrl = "${fragment.requestUrl}"
      |
      |  // copy the whole method body as "dynamicResourceDoc" method body
      |  override protected def process(callContext: CallContext, request: Req): Box[JsonResponse] = {
      |    // please add import sentences here, those used by this method
      |
      |    val Some(resourceDoc) = callContext.resourceDocument
      |    val hasRequestBody = request.body.isDefined
      |
      |$pathVariables
      |
      |$requestEntity
      |
      |    // please add business logic here
      |    $responseEntity
      |    Future.successful {
      |      (responseBody, HttpCode.`200`(callContext.callContext))
      |    }
      |  }
      |""".stripMargin
  }
}
