package code.api.v4_0_0.dynamic

import code.api.util.APIUtil.{BooleanBody, DoubleBody, EmptyBody, LongBody, OBPEndpoint, PrimaryDataBody, ResourceDoc, StringBody}
import code.api.util.{CallContext, DynamicUtil}
import code.api.v4_0_0.dynamic.practise.{DynamicEndpointCodeGenerator, PractiseEndpointGroup}
import net.liftweb.common.{Box, Failure, Full}
import net.liftweb.http.{JsonResponse, Req}
import net.liftweb.json.{JNothing, JValue}
import net.liftweb.json.JsonAST.{JBool, JDouble, JInt, JString}
import org.apache.commons.lang3.StringUtils

import java.net.URLDecoder
import scala.collection.immutable.List
import scala.util.control.Breaks.{break, breakable}

object DynamicEndpoints {
  private val endpointGroup: List[EndpointGroup] = PractiseEndpointGroup :: DynamicResourceDocsGroup :: Nil

  private def findEndpoint(req: Req): Option[OBPEndpoint] = {
    var foundEndpoint: Option[OBPEndpoint] = None
    breakable{
      endpointGroup.foreach { group => {
        val maybeEndpoint: Option[OBPEndpoint] = group.endpoints.find(_.isDefinedAt(req))
        if(maybeEndpoint.isDefined) {
          foundEndpoint = maybeEndpoint
          break
        }
      }}
    }
    foundEndpoint
  }

  val dynamicEndpoint: OBPEndpoint = new OBPEndpoint {
    override def isDefinedAt(req: Req): Boolean = findEndpoint(req).isDefined

    override def apply(req: Req): CallContext => Box[JsonResponse] = {
      val Some(endpoint) = findEndpoint(req)
      endpoint(req)
    }
  }

  def dynamicResourceDocs: List[ResourceDoc] = endpointGroup.flatMap(_.docs)
}

trait EndpointGroup {
  protected def resourceDocs: List[ResourceDoc]

  protected lazy val urlPrefix: String = ""

  // reset urlPrefix resourceDocs
  def docs: List[ResourceDoc] = if(StringUtils.isBlank(urlPrefix)) {
    resourceDocs
  } else {
    resourceDocs map { doc =>
      val newUrl = s"/$urlPrefix/${doc.requestUrl}".replace("//", "/")
      val newDoc = doc.copy(requestUrl = newUrl)
      newDoc.connectorMethods = doc.connectorMethods // copy method will not keep var value, So here reset it manually
      newDoc
    }
  }
  def endpoints: List[OBPEndpoint] = docs.map(wrapEndpoint)

  //fill callContext with resourceDoc and operationId
  private def wrapEndpoint(resourceDoc: ResourceDoc): OBPEndpoint = {

    val endpointFunction = resourceDoc.wrappedWithAuthCheck(resourceDoc.partialFunction)

    new OBPEndpoint {
      override def isDefinedAt(req: Req): Boolean = req.requestType.method == resourceDoc.requestVerb && endpointFunction.isDefinedAt(req)

      override def apply(req: Req): CallContext => Box[JsonResponse] = {
        (callContext: CallContext) => {
          // fill callContext with resourceDoc and operationId
          val newCallContext = callContext.copy(resourceDocument = Some(resourceDoc), operationId = Some(resourceDoc.operationId))
          endpointFunction(req)(newCallContext)
        }
      }
    }
  }
}

case class CompiledObjects(exampleRequestBody: Option[JValue], successResponseBody: Option[JValue], methodBody: String) {
  val decodedMethodBody = URLDecoder.decode(methodBody, "UTF-8")
  val requestBody = exampleRequestBody match {
    case Some(JString(s)) if StringUtils.isBlank(s) => toCaseObject(None)
    case _ => toCaseObject(exampleRequestBody)
  }
  val successResponse = toCaseObject(successResponseBody)

  val partialFunction: OBPEndpoint = {

    val requestExample = if (!requestBody.isInstanceOf[PrimaryDataBody[_]]) {
      exampleRequestBody
    } else None

    val responseExample = if (!successResponse.isInstanceOf[PrimaryDataBody[_]]) {
      successResponseBody
    } else None

    val (requestBodyCaseClasses, responseBodyCaseClasses) = DynamicEndpointCodeGenerator.buildCaseClasses(requestExample, responseExample)

    val code =
      s"""
         |import code.api.util.APIUtil.errorJsonResponse
         |import code.api.util.CallContext
         |import code.api.util.ErrorMessages.{InvalidJsonFormat, InvalidRequestPayload}
         |import code.api.util.NewStyle.HttpCode
         |import code.api.v4_0_0.dynamic.DynamicCompileEndpoint
         |import net.liftweb.common.{Box, EmptyBox, Full}
         |import net.liftweb.http.{JsonResponse, Req}
         |import net.liftweb.json.MappingException
         |
         |import scala.concurrent.Future
         |
         |$requestBodyCaseClasses
         |
         |$responseBodyCaseClasses
         |
         |(new DynamicCompileEndpoint {
         |    override protected def process(callContext: CallContext, request: Req): Box[JsonResponse] = {
         |       $decodedMethodBody
         |    }
         |}).endpoint
         |
         |""".stripMargin
    val endpointMethod = DynamicUtil.compileScalaCode[OBPEndpoint](code)

    endpointMethod match {
      case Full(func) => func
      case Failure(msg: String, exception: Box[Throwable], _) =>
        throw exception.getOrElse(new RuntimeException(msg))
      case _ => throw new RuntimeException("compiled code return nothing")
    }
  }

  private def toCaseObject(jValue: Option[JValue]): Product = {
     if (jValue.isEmpty || jValue.exists(JNothing ==)) {
      EmptyBody
     } else {
       jValue.orNull match {
         case JBool(b) => BooleanBody(b)
         case JInt(l) => LongBody(l.toLong)
         case JDouble(d) => DoubleBody(d)
         case JString(s) => StringBody(s)
         case v => DynamicUtil.toCaseObject(v)
       }
     }
  }
}


