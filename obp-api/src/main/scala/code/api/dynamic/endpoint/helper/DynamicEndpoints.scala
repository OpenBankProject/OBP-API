package code.api.dynamic.endpoint.helper

import org.json4s._
import cats.effect.IO
import code.api.dynamic.endpoint.helper.practise.{DynamicEndpointCodeGenerator, PractiseEndpointGroup}
import code.api.dynamic.endpoint.helper.practise.PractiseEndpointGroup
import code.api.util.DynamicUtil.{Sandbox, Validation}
import code.api.util.APIUtil.{BooleanBody, DoubleBody, EmptyBody, LongBody, Http4sEndpointIO, PrimaryDataBody, ResourceDoc, StringBody, getDisabledEndpointOperationIds}
import code.api.util.{CallContext, DynamicUtil}
import net.liftweb.common.{Box, Failure, Full}
import org.json4s.{JNothing, JValue}
import org.json4s.JsonAST.{JBool, JDouble, JInt, JString}
import org.apache.commons.lang3.StringUtils
import org.http4s.{Request, Response}

import java.net.URLDecoder
import scala.collection.immutable.List

object DynamicEndpoints {
  //TODO, better put all other dynamic endpoints into this list. eg: dynamicEntityEndpoints, dynamicSwaggerDocsEndpoints ....
  val disabledEndpointOperationIds = getDisabledEndpointOperationIds

  private val endpointGroups: List[EndpointGroup] =
    if(disabledEndpointOperationIds.contains("OBPv4.0.0-test-dynamic-resource-doc")) {
      DynamicResourceDocsEndpointGroup :: Nil
    }else{
      PractiseEndpointGroup :: DynamicResourceDocsEndpointGroup :: Nil
    }

  /**
   * Native http4s router for all runtime-compiled dynamic endpoints (Piece C).
   * Finds the matching dynamic ResourceDoc by HTTP verb + URL template; the doc carries the
   * compiled native handler in `dynamicHttp4sFunction`. The dynamic endpoints can be in the OBP
   * database (DynamicResourceDocsEndpointGroup) or compiled in code (PractiseEndpointGroup).
   *
   * This is the OBP Router for all the dynamic endpoints. It is iterated by
   * code.api.dynamic.endpoint.Http4sDynamicEndpoint, which then runs the doc's auth chain
   * (ResourceDoc.authCheckIO) and the handler. Replaces the former Lift `dynamicEndpoint`
   * (PartialFunction[Req, CallContext => Box[JsonResponse]]) that ran through the Lift dispatch.
   */
  def findEndpoint(req: Request[IO]): Option[ResourceDoc] = {
    val partPath = req.uri.path.segments.drop(2).map(_.encoded).toList // segments after /obp/dynamic-endpoint
    val verb = req.method.name
    endpointGroups.iterator
      .flatMap(_.docs.iterator)
      .find(doc => doc.requestVerb == verb && doc.dynamicHttp4sFunction.isDefined && doc.matchesPartPath(partPath))
  }

  def dynamicResourceDocs: List[ResourceDoc] = endpointGroups.flatMap(_.docs)
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
      val newDoc = doc.copy(requestUrl = newUrl) // copy preserves dynamicHttp4sFunction
      newDoc.connectorMethods = doc.connectorMethods // copy method will not keep var value, So here reset it manually
      newDoc
    }
  }
}

/**
 * This class will generate the ResourceDoc class fields(requestBody: Product, successResponse: Product and the native
 * http4s handler) by parameters: JValues and Strings.
 * successResponseBody: Option[JValue] --> toCaseObject(from JValue --> Scala code --> DynamicUtil.compileScalaCode --> generate the object.
 * methodBody: String --> prepare the template api level scala code --> DynamicUtil.compileScalaCode --> generate the api level code.
 *
 * @param exampleRequestBody exampleRequestBody from the post json body, it is JValue here.
 * @param successResponseBody successResponseBody from the post json body,it is JValue here.
 * @param methodBody it is url-encoded string for the api level code.
 */
object CompiledObjects {
  /**
   * The native http4s template a method body is inlined into. Returns the full source and the
   * 1-based line on which the method body starts, so compiler positions can be mapped back to it.
   * The compiled artifact is an `Http4sEndpointIO` (PartialFunction[Request[IO], CallContext => IO[Response[IO]]]).
   * `DynamicCompileEndpoint._` injects the `OBPReturnType[T] => IO[Response[IO]]` implicit (so the
   * familiar `Future.successful((json, HttpCode.`200`(cc)))` body style works) and the
   * `errorResponse(msg, code)` helper (replacing `Full(errorJsonResponse(...))`).
   */
  def wrapMethodBody(requestBodyCaseClasses: String, responseBodyCaseClasses: String, decodedMethodBody: String): (String, Int) = {
    val prefix =
      s"""
         |import cats.effect.IO
         |import org.http4s.{Request, Response}
         |import code.api.util.CallContext
         |import code.api.util.ErrorMessages.{InvalidJsonFormat, InvalidRequestPayload}
         |import code.api.util.NewStyle.HttpCode
         |import code.api.util.APIUtil.OBPReturnType
         |import org.json4s.MappingException
         |import code.api.dynamic.endpoint.helper.DynamicCompileEndpoint._
         |
         |import scala.concurrent.Future
         |import com.openbankproject.commons.ExecutionContext.Implicits.global
         |import net.liftweb.common.{Box, Empty, Failure, Full}
         |
         |implicit val formats = code.api.util.CustomJsonFormats.formats
         |
         |$requestBodyCaseClasses
         |
         |$responseBodyCaseClasses
         |
         |val endpoint: code.api.util.APIUtil.Http4sEndpointIO = {
         |  case request => { callContext =>
         |    val Some(pathParams) = callContext.resourceDocument.map(_.getPathParams(request.uri.path.segments.toList.map(_.encoded)))
         |    """.stripMargin
    val suffix =
      s"""
         |  }
         |}
         |
         |endpoint
         |
         |""".stripMargin
    val bodyStartLine = prefix.count(_ == '\n') + 1
    (prefix + decodedMethodBody + suffix, bodyStartLine)
  }

  /**
   * Dry run without constructing a CompiledObjects (whose constructor compiles for real): compiler
   * diagnostics with line numbers relative to the method body the author wrote. Empty = compiles.
   */
  def compileProblems(exampleRequestBody: Option[JValue], successResponseBody: Option[JValue], methodBody: String): List[DynamicUtil.CompileProblem] = {
    val decodedMethodBody = URLDecoder.decode(methodBody, "UTF-8")
    val requestBody: Product = exampleRequestBody match {
      case Some(JString(s)) if StringUtils.isBlank(s) => toCaseObject(None)
      case _ => toCaseObject(exampleRequestBody)
    }
    val successResponse: Product = toCaseObject(successResponseBody)
    val requestExample: Option[JValue] = if (requestBody.isInstanceOf[PrimaryDataBody[_]]) None else exampleRequestBody
    val responseExample: Option[JValue] = if (successResponse.isInstanceOf[PrimaryDataBody[_]]) None else successResponseBody
    val (requestBodyCaseClasses, responseBodyCaseClasses) = DynamicEndpointCodeGenerator.buildCaseClasses(requestExample, responseExample)
    val (code, bodyStartLine) = wrapMethodBody(requestBodyCaseClasses, responseBodyCaseClasses, decodedMethodBody)
    DynamicUtil.checkScalaCode(code).map { p =>
      if (p.line > 0) p.copy(line = p.line - bodyStartLine + 1) else p
    }
  }

  def toCaseObject(jValue: Option[JValue]): Product = {
     if (jValue.isEmpty || jValue.exists(JNothing == _)) {
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

case class CompiledObjects(exampleRequestBody: Option[JValue], successResponseBody: Option[JValue], methodBody: String) {
  val decodedMethodBody = URLDecoder.decode(methodBody, "UTF-8")
  val requestBody: Product = exampleRequestBody match {
      //this case means, we accept the empty string "" from json post body, we need to map it to None.
    case Some(JString(s)) if StringUtils.isBlank(s) => toCaseObject(None)
     // Here we will generate the object by the JValue (exampleRequestBody)
    case _ => toCaseObject(exampleRequestBody)
  }
  val successResponse: Product = toCaseObject(successResponseBody)

  private val partialFunction: Http4sEndpointIO = {

    //If the requestBody is PrimaryDataBody, return None. otherwise, return the exampleRequestBody:Option[JValue]
    // In side OBP resourceDoc, requestBody and successResponse must be Product type，
    // both can not be the primitive type: `boolean， string， kong， int， long， double` and List.
    // PrimaryDataBody is used for OBP mapping these types.
    // Note: List and object will generate the `Case class`, `case class` must not be PrimaryDataBody. only these two
    // possibilities: case class or PrimaryDataBody
    val requestExample: Option[JValue] = if (requestBody.isInstanceOf[PrimaryDataBody[_]]) {
      None
    } else exampleRequestBody

    val responseExample: Option[JValue] = if (successResponse.isInstanceOf[PrimaryDataBody[_]]) {
      None
    } else successResponseBody

    //  buildCaseClasses --> will generate the following case classes string, which are used for the scala template code.
    // case class RequestRootJsonClass(name: String, age: Long)
    // case class ResponseRootJsonClass(person_id: String, name: String, age: Long)
    val (requestBodyCaseClasses, responseBodyCaseClasses) = DynamicEndpointCodeGenerator.buildCaseClasses(requestExample, responseExample)

    // Native http4s template (replaces the former Lift `OBPEndpoint` template). The compiled
    // artifact is an `OBPEndpointIO` (PartialFunction[Request[IO], CallContext => IO[Response[IO]]]).
    // `DynamicCompileEndpoint._` injects the `OBPReturnType[T] => IO[Response[IO]]` implicit (so the
    // familiar `Future.successful((json, HttpCode.`200`(cc)))` body style still works) and the
    // `errorResponse(msg, code)` helper (replacing `Full(errorJsonResponse(...))`).
    val (code, _) = CompiledObjects.wrapMethodBody(requestBodyCaseClasses, responseBodyCaseClasses, decodedMethodBody)
    val endpointMethod = DynamicUtil.compileScalaCode[Http4sEndpointIO](code)

    endpointMethod match {
      case Full(func) => func
      case Failure(msg: String, exception: Box[Throwable], _) =>
        throw exception.getOrElse(new RuntimeException(msg))
      case _ => throw new RuntimeException("compiled code return nothing")
    }
  }

  /**
   * this will check all the dynamic scala code dependencies at compile time.
   *
   *Search for the usage, you can see how to use it in OBP code.
   */
  def validateDependency() = Validation.validateDependency(this.partialFunction)

  /**
   * Dry run: compiler diagnostics for this body, with line numbers relative to the method body the
   * author wrote (the wrapper's own lines are subtracted). Empty = compiles. Nothing is evaluated or cached.
   */
  def compileProblems(): List[DynamicUtil.CompileProblem] =
    CompiledObjects.compileProblems(exampleRequestBody, successResponseBody, methodBody)

  /**
   * This is used to check the security permission at the run time.
   * all the obp partialFunctions will be wrapped into the sandbox which under the permission control.
   *
   */
  def sandboxEndpoint(bankId: Option[String]) : Http4sEndpointIO = {
    val sandbox = bankId match {
      case Some(v) if StringUtils.isNotBlank(v) =>
         Sandbox.sandbox(v)
      case _ => Sandbox.sandbox("*")
    }

    new Http4sEndpointIO {
      override def isDefinedAt(req: Request[IO]): Boolean = partialFunction.isDefinedAt(req)

      // run dynamic code in sandbox
      override def apply(req: Request[IO]): CallContext => IO[Response[IO]] = { cc =>
        val fn = partialFunction.apply(req)

        sandbox.runInSandboxIO(fn(cc))
      }
    }
  }

  private def toCaseObject(jValue: Option[JValue]): Product = CompiledObjects.toCaseObject(jValue)
}
