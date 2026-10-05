/**
Open Bank Project - API
Copyright (C) 2011-2026, TESOBE GmbH.

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
TESOBE GmbH.
Osloer Strasse 16/17
Berlin 13359, Germany

This product includes software developed at
TESOBE (http://www.tesobe.com/)

  */

package code.api.dynamic.endpoint.helper

import org.json4s._
import cats.effect.IO
import code.api.dynamic.endpoint.helper.practise.{DynamicEndpointCodeGenerator, PractiseEndpointGroup}
import code.api.dynamic.endpoint.helper.practise.PractiseEndpointGroup
import code.api.util.DynamicUtil.{DynamicCodeBody, Validation}
import code.api.util.APIUtil.{BooleanBody, DoubleBody, EmptyBody, LongBody, Http4sEndpointIO, PrimaryDataBody, ResourceDoc, StringBody, getDisabledEndpointOperationIds}
import code.api.util.{APIUtil, CallContext, DynamicUtil, ErrorMessages}
import code.api.JsonResponseException
import net.liftweb.common.{Box, Failure, Full}
import org.json4s.{JNothing, JValue}
import org.json4s.JsonAST.{JBool, JDouble, JInt, JString}
import org.apache.commons.lang3.StringUtils
import org.http4s.{Request, Response}

import java.net.URLDecoder
import scala.collection.immutable.List

/**
 * What a request under /obp/dynamic-endpoint/ names: one runtime-compiled endpoint (with the request it
 * should be run as, which may be the canonical form of the URL the caller used), several that the URL
 * cannot tell apart, or none.
 */
sealed trait DynamicEndpointMatch
object DynamicEndpointMatch {
  case class Found(doc: ResourceDoc, request: Request[IO]) extends DynamicEndpointMatch
  case class Ambiguous(spaces: List[String]) extends DynamicEndpointMatch
  case object NotFound extends DynamicEndpointMatch
}

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
  def findEndpoint(req: Request[IO]): DynamicEndpointMatch = {
    val partPath = req.uri.path.segments.drop(2).map(_.encoded).toList // segments after /obp/dynamic-endpoint
    val verb = req.method.name
    def servable(doc: ResourceDoc): Boolean = doc.requestVerb == verb && doc.dynamicHttp4sFunction.isDefined
    val otherGroups = endpointGroups.filterNot(_ == DynamicResourceDocsEndpointGroup)
    val resourceDocs = if (endpointGroups.contains(DynamicResourceDocsEndpointGroup)) DynamicResourceDocsEndpointGroup.docs.filter(servable) else Nil

    otherGroups.iterator.flatMap(_.docs.iterator).find(doc => servable(doc) && doc.matchesPartPath(partPath)) match {
      case Some(doc) => DynamicEndpointMatch.Found(doc, req)
      case None => partPath match {
        // The URL names its space: /banks/BANK_ID/dynamic-resource-doc/..., BANK_ID being a bank's id or SYS.
        // The space segment is compared exactly first: matchesPartPath alone would read an upper-case
        // space such as SYS as a path variable, and so match it against any bank id.
        case "banks" :: space :: _ =>
          resourceDocs.find(doc => DynamicResourceDocsEndpointGroup.spaceOf(doc) == space && doc.matchesPartPath(partPath))
            .map(doc => DynamicEndpointMatch.Found(doc, req)).getOrElse(DynamicEndpointMatch.NotFound)
        // The URL that names no space, kept so existing callers keep working: it is served only when exactly
        // one doc, in any space, answers it, and then as if the caller had used that doc's own URL.
        case first :: _ if first == DynamicResourceDocsEndpointGroup.urlPrefix =>
          resourceDocs.filter(doc => doc.matchesPartPath("banks" :: DynamicResourceDocsEndpointGroup.spaceOf(doc) :: partPath)) match {
            case doc :: Nil =>
              val canonical = req.uri.path.segments.take(2).map(_.encoded).toList ++ ("banks" :: DynamicResourceDocsEndpointGroup.spaceOf(doc) :: partPath)
              DynamicEndpointMatch.Found(doc, req.withUri(req.uri.withPath(org.http4s.Uri.Path.unsafeFromString(canonical.mkString("/", "/", "")))))
            case Nil => DynamicEndpointMatch.NotFound
            case several => DynamicEndpointMatch.Ambiguous(several.map(DynamicResourceDocsEndpointGroup.spaceOf).distinct.sorted)
          }
        case _ => DynamicEndpointMatch.NotFound
      }
    }
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
   * This is the set of `programming_lang` values a Dynamic Resource Doc may have, compared after
   * APIUtil.normaliseDynamicCodeLanguage (trimmed, lower case, blank meaning Scala). Create, update,
   * validate and the dry-run compile all check against it, and CompiledObjects chooses its compiler
   * from the same normalised value, so nothing that passes the check can reach the wrong compiler.
   */
  val supportedLanguages: List[String] = List("scala", "java", "query")
  def isSupportedLanguage(programmingLang: String): Boolean =
    supportedLanguages.contains(APIUtil.normaliseDynamicCodeLanguage(programmingLang))
  val supportedLanguagesText: String = "Scala, Java, Query"

  /**
   * True when the body is a Dynamic Query (`programming_lang` `Query`): a declaration, not code. It
   * runs no user code, so the switch for user-supplied code (`allow_user_generated_scala_code`) does
   * not apply to it, and it is read-only, so its doc's request_verb must be GET.
   */
  def isQuery(programmingLang: String): Boolean = APIUtil.normaliseDynamicCodeLanguage(programmingLang) == "query"

  /** A Dynamic Query only reads; every other language may use any verb. */
  def verbAllowed(programmingLang: String, requestVerb: String): Boolean =
    !isQuery(programmingLang) || requestVerb == "GET"

  val queryVerbMessage: String = "A Dynamic Query only reads, so its request_verb must be GET."

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
   * diagnostics with line numbers relative to the method body the author wrote, in the body's
   * programming language (Scala or Java). Empty = compiles.
   */
  def compileProblems(exampleRequestBody: Option[JValue], successResponseBody: Option[JValue], methodBody: String,
                      programmingLang: String = "Scala", bankId: Option[String] = None): List[DynamicUtil.CompileProblem] = {
    val decodedMethodBody = URLDecoder.decode(methodBody, "UTF-8")
    // Java bodies are compiled as written (no template, no generated case classes), so the example
    // bodies play no part, as they play none in CompiledObjects' own Java branch. A Dynamic Query is
    // not compiled at all: its problems are the declaration's, which have no line numbers.
    APIUtil.normaliseDynamicCodeLanguage(programmingLang) match {
      case "java" => DynamicUtil.checkJavaCode(decodedMethodBody)
      case "query" => queryProblem(decodedMethodBody, bankId).map(message => DynamicUtil.CompileProblem(0, 0, "ERROR", message)).toList
      case _ => scalaCompileProblems(exampleRequestBody, successResponseBody, decodedMethodBody)
    }
  }

  /** What is wrong with a Dynamic Query body, in full (error code included), or None when it is valid in `bankId`'s space. */
  def queryProblem(decodedMethodBody: String, bankId: Option[String]): Option[String] =
    code.api.dynamic.entity.query.DynamicQueryDeclaration.parse(decodedMethodBody) match {
      case Left(error) => Some(s"${ErrorMessages.DynamicQueryInvalid}${error.message}")
      case Right(declaration) => code.api.dynamic.entity.query.DynamicQuery.validate(bankId, declaration).left.toOption.map(_.message)
    }

  private def scalaCompileProblems(exampleRequestBody: Option[JValue], successResponseBody: Option[JValue], decodedMethodBody: String): List[DynamicUtil.CompileProblem] = {
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

/**
 * `bankId` is the Dynamic Entity space of the doc (None for the system space). Only a Dynamic Query
 * uses it: its declaration names entities, which are looked up in that space.
 */
case class CompiledObjects(exampleRequestBody: Option[JValue], successResponseBody: Option[JValue], methodBody: String,
                           programmingLang: String = "Scala", bankId: Option[String] = None) {
  val decodedMethodBody = URLDecoder.decode(methodBody, "UTF-8")
  private val isQuery = CompiledObjects.isQuery(programmingLang)
  // The stored doc of a system-level Dynamic Resource Doc carries Some(null) here, not None.
  private val space: Option[String] = bankId.flatMap(Option(_)).map(_.trim).filter(_.nonEmpty)

  // A Dynamic Query compiles nothing, not even case classes for its examples: toCaseObject generates
  // and compiles Scala, which is refused where user-supplied code is switched off. Its examples are
  // carried as JSON, which the resource-docs serialisation renders as they are.
  private def exampleOf(json: Option[JValue]): Product =
    if (!isQuery) toCaseObject(json)
    else json.filter(j => j != JNothing && j != JNull).map(code.api.berlin.group.v1_3.JvalueCaseClass(_)).getOrElse(EmptyBody)

  val requestBody: Product = exampleRequestBody match {
      //this case means, we accept the empty string "" from json post body, we need to map it to None.
    case Some(JString(s)) if StringUtils.isBlank(s) => exampleOf(None)
     // Here we will generate the object by the JValue (exampleRequestBody)
    case _ => exampleOf(exampleRequestBody)
  }
  val successResponse: Product = exampleOf(successResponseBody)

  private val partialFunction: Http4sEndpointIO = APIUtil.normaliseDynamicCodeLanguage(programmingLang) match {
    case "query" =>
      // A declaration, not code: parsed here (a malformed body cannot be served), checked against the
      // entity definitions by validateDependency, and run per request by DynamicQueryEndpoint.
      code.api.dynamic.entity.query.DynamicQueryDeclaration.parse(decodedMethodBody) match {
        case Right(declaration) => DynamicQueryEndpoint(declaration, space)
        case Left(error) => throw JsonResponseException(s"${ErrorMessages.DynamicQueryInvalid}${error.message}", 400, "none")
      }
    case "java" =>
      DynamicUtil.createJavaHttp4sEndpoint(decodedMethodBody) match {
        case Full(func) => func
        case Failure(msg: String, exception: Box[Throwable], _) =>
          throw exception.getOrElse(new RuntimeException(msg))
        case _ => throw new RuntimeException("compiled code return nothing")
      }
    case _ /* "scala", the default; create and update reject anything else (isSupportedLanguage) */ =>
      scalaPartialFunction
  }

  // Unchanged Scala-template compile path, factored out so the `partialFunction` match above stays
  // readable. Only evaluated for Scala-language docs (the default) — Java-language docs never
  // touch this, so example/response-body JValues that don't fit the Scala case-class generator
  // (irrelevant for Java, since it doesn't use RequestRootJsonClass/ResponseRootJsonClass) are a
  // non-issue there.
  private def scalaPartialFunction: Http4sEndpointIO = {

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
   *
   * Scala-only: for the Scala language, `this.partialFunction` IS the compiled user code, so
   * validating its bytecode directly is correct. For Java, `this.partialFunction` is instead
   * OBP's own Http4sEndpointIO wrapper (built by DynamicUtil.createJavaHttp4sEndpoint) around the
   * real compiled Java class -- its bytecode legitimately calls internal OBP helpers
   * (DynamicUtil.javaValueToJValue/logger, CustomJsonFormats.formats, JsonAliases.compactRender)
   * that were never meant to be dependency-whitelisted, since they are framework glue, not
   * user-supplied code. createJavaHttp4sEndpoint already validates the real compiled Java class
   * internally (see its own doc comment) before ever returning that wrapper, so re-validating the
   * wrapper here is both redundant and wrong -- it would reject every Java doc unconditionally.
   */
  def validateDependency() = APIUtil.normaliseDynamicCodeLanguage(programmingLang) match {
    case "java" => ()
    // A Dynamic Query calls no methods; what it is checked against is the entity definitions of its space.
    case "query" => CompiledObjects.queryProblem(decodedMethodBody, space).foreach(message => throw JsonResponseException(message, 400, "none"))
    case _ => Validation.validateDependency(this.partialFunction)
  }

  /**
   * Dry run: compiler diagnostics for this body, with line numbers relative to the method body the
   * author wrote (the wrapper's own lines are subtracted). Empty = compiles. Nothing is evaluated or cached.
   */
  def compileProblems(): List[DynamicUtil.CompileProblem] =
    CompiledObjects.compileProblems(exampleRequestBody, successResponseBody, methodBody, programmingLang, space)

  /**
   * Wraps the compiled partial function as an endpoint. This used to bind a per-bank
   * security sandbox; that sandbox could not be enforced on JDK 24+ and has been
   * removed (see DynamicUtil.DynamicCodeBody), so what remains is forcing the body and
   * recovering an early `return` from user code.
   */
  def compiledEndpoint() : Http4sEndpointIO =
    new Http4sEndpointIO {
      override def isDefinedAt(req: Request[IO]): Boolean = partialFunction.isDefinedAt(req)

      override def apply(req: Request[IO]): CallContext => IO[Response[IO]] = { cc =>
        val fn = partialFunction.apply(req)

        DynamicCodeBody.force(fn(cc))
      }
    }

  private def toCaseObject(jValue: Option[JValue]): Product = CompiledObjects.toCaseObject(jValue)
}
