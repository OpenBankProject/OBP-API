package code.api.v4_0_0.dynamic

import code.api.util.APIUtil.{BooleanBody, DoubleBody, EmptyBody, LongBody, OBPEndpoint, ResourceDoc, StringBody}
import code.api.util.{CallContext, DynamicUtil}
import code.api.v4_0_0.dynamic.practise.PractiseEndpointGroup
import net.liftweb.common.Box
import net.liftweb.http.{JsonResponse, Req}
import net.liftweb.json
import net.liftweb.json.JsonAST.{JBool, JDouble, JInt, JString}
import net.liftweb.util.Props
import org.apache.commons.lang3.StringUtils

import scala.collection.immutable.List
import scala.util.control.Breaks.{break, breakable}

object DynamicEndpoints {
  private var endpointGroup: List[EndpointGroup] = DynamicResourceDocs :: Nil

  if (Props.devMode) {
    endpointGroup = PractiseEndpointGroup :: endpointGroup
  }

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

case class CompiledObjects(exampleRequestBody:String, successResponseBody: String, methodBody: String) {
  val requestBody = toCaseObject(exampleRequestBody)
  val successResponse = toCaseObject(successResponseBody)

  val partialFunction: OBPEndpoint = ???

  private def toCaseObject(str: String): Product = {
     if (StringUtils.isBlank(str)) {
      EmptyBody
     } else {
       json.parse(str) match {
         case JBool(b) => BooleanBody(b)
         case JInt(l) => LongBody(l.toLong)
         case JDouble(d) => DoubleBody(d)
         case JString(s) => StringBody(s)
         case v => DynamicUtil.toCaseObject(v)
       }
     }

  }
}


