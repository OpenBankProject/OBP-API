package code.bankconnectors

import org.json4s._
import code.api.util.DynamicUtil.compileScalaCode
import code.api.util.ErrorMessages.{DynamicCodeLangNotSupport, InvalidConnectorMethodName}
import net.liftweb.common.Full

import scala.concurrent.Future
import code.connectormethod.{ConnectorMethodProvider, JsonConnectorMethod}
import com.github.dwickern.macros.NameOf.nameOf
import net.liftweb.common.{Box, Failure}
import java.lang.reflect.{InvocationHandler, Method}
import code.api.util.{CallContext, DynamicUtil}
import org.apache.commons.lang3.StringUtils
import org.apache.commons.text.StringEscapeUtils
import com.github.dwickern.macros.NameOf.{nameOf, qualifiedNameOfType}
import com.openbankproject.commons.util.ReflectUtils

import scala.reflect.runtime.universe.{MethodSymbol, TermSymbol, typeOf}

/**
 * InternalConnector is actually the dynamic connector, if set method to `internal`. this allows the developer to use `Create Connector Method`
 * endpoint to upload the scala/Js/Java source code to redesign the logic for the connector method
 */
object InternalConnector {

  lazy val instance: Connector = {
    ConnectorProxy.create(intercept)
  }

  //this object is a empty Connector implementation, just for supply default args
  private object connector extends Connector {
    // you can create method at here and copy the method body to create `ConnectorMethod`, but never keep the code
    // in this object, you must make sure this object is empty.
  }

  private val intercept: InvocationHandler = new InvocationHandler {
    override def invoke(proxy: AnyRef, method: Method, args: Array[AnyRef]): AnyRef = {
      val methodName = method.getName
      if(methodName == nameOf(connector.callableMethods)) {
        InternalConnector.this.callableMethods
      // isInheritedMember is subsumed by isDynamicallyImplementable today, and asked anyway: the
      // second is derived from methodNameToSignature, a map built for rendering signatures whose
      // exclusion of inherited members is a side effect of how it is filtered rather than something
      // it promises. Naming the rule directly is what keeps this from depending on that accident.
      } else if (methodName.contains("$default$") || ConnectorProxy.isInheritedMember(method)
                 || !isDynamicallyImplementable(methodName)) {
        // Anything outside the Connector API goes to the empty Connector object, which implements
        // it for real. Connector extends Helper.MdcLoggable, so the interface also carries logger,
        // clazzName and initiate; dynamic code never defines those, and sending them down the
        // lookup-and-compile path threw IllegalStateException - which is what happened to anything
        // that logged or interpolated this proxy. The $default$ accessors take the same route.
        method.invoke(connector, args:_*)
      } else {
        val function = getFunction(methodName)
        DynamicUtil.executeFunction(methodName, function, args)
      }
    }
  }

  /**
   * Whether dynamic code can supply this method at all. It is the key set of methodNameToSignature,
   * named separately because the handler asks a different question of it than its builder answers:
   * that map is decls filtered by `!t.isVal && !t.isVar`, so on top of ConnectorProxy's inherited
   * members it also excludes Connector's own vals - messageDocs among them. Everything it excludes
   * is answered by the empty stub, which for a val means the stub's own instance.
   */
  private def isDynamicallyImplementable(methodName: String): Boolean =
    methodNameToSignature.contains(methodName)

  private def getFunction(methodName: String) = {
    // Maker/checker execution guard (active + approved hash); see code.dynamicchangerequest.MakerChecker
    ConnectorMethodProvider.provider.vend.getByMethodNameWithCache(methodName)
      .filter(_.connectorMethodId.exists(code.dynamicchangerequest.MakerChecker.isExecutableConnectorMethod)) map {
      case v :JsonConnectorMethod =>
        createFunction(methodName, v.decodedMethodBody, v.programmingLang).openOrThrowException(s"InternalConnector method compile fail, method name $methodName")
    }
  }

  private val boxRegx1 = """^.+\)\s*:net.liftweb.common.Box\[\((.+),\s*Option\[code.api.util.CallContext\]\)\]$""".r
  private val boxRegx2 = """^.+\)\s*:net.liftweb.common.Box\[(.+)\]$""".r

  private val futureRegx1 = """^.+\)\s*:scala.concurrent.Future\[net.liftweb.common.Box\[\((.+),\s*Option\[code.api.util.CallContext\]\)\]\]$""".r
  private val futureRegx2 = """^.+\)\s*:scala.concurrent.Future\[net.liftweb.common.Box\[(.+)\]\]$""".r
  private val futureRegx3 = """^.+\)\s*:scala.concurrent.Future\[(.+)\]$""".r

  private val obpReturnTypeRegx1 = """^.+\)\s*:code.api.util.APIUtil.OBPReturnType\[net.liftweb.common.Box\[(.+)\]\]$""".r
  private val obpReturnTypeRegx2 = """^.+\)\s*:code.api.util.APIUtil.OBPReturnType\[(.+)\]$""".r

  private val otherTypeRegx = """^.+\)\s*:(.+)$""".r

  private val callContextRegex = """^.+?(\w+)\s*:\s*Option\[code.api.util.CallContext\].+$""".r

  private def getCallContextParamName(signature: String) =  signature match {
      case callContextRegex(callContext) => callContext
      case _ => "scala.None"
    }

  private def buildDynamicMethodBody(methodName: String, methodBody: String, dynamicFunctionCreator: String): String = methodNameToSignature.get(methodName)  match {
    case Some(signature) =>
      val convertor = signature match {
          case boxRegx1(t) =>
            s"""(v: scala.concurrent.Future[net.liftweb.common.Box[(String, scala.Option[code.api.util.CallContext])]]) => {
              implicit val formats = code.api.util.CustomJsonFormats.formats
              import scala.concurrent.duration._
              val f: Future[Box[($t, Option[CallContext])]] =
                v.map(_.map(it =>(com.openbankproject.commons.util.JsonAliases.parse(it._1).extract[$t], it._2)))(com.openbankproject.commons.ExecutionContext.Implicits.global)
              val result: Box[($t, Option[CallContext])] = scala.concurrent.Await.result(f, 5 minutes)
              result
            }"""

          case boxRegx2(t)   =>
            s"""(v: scala.concurrent.Future[net.liftweb.common.Box[(String, scala.Option[code.api.util.CallContext])]]) =>{
              implicit val formats = code.api.util.CustomJsonFormats.formats
              import scala.concurrent.duration._
              val f: Future[Box[$t]] =
                v.map(_.map(it =>com.openbankproject.commons.util.JsonAliases.parse(it._1).extract[$t]))(com.openbankproject.commons.ExecutionContext.Implicits.global)
              val result: Box[$t] = scala.concurrent.Await.result(f, 5 minutes)
              result
            }"""

          case futureRegx1(t) =>
            s"""(v: scala.concurrent.Future[net.liftweb.common.Box[(String, scala.Option[code.api.util.CallContext])]]) =>{
              implicit val formats = code.api.util.CustomJsonFormats.formats
              val result : Future[Box[($t, Option[CallContext])]] =
                v.map(_.map(it =>(com.openbankproject.commons.util.JsonAliases.parse(it._1).extract[$t], it._2)))(com.openbankproject.commons.ExecutionContext.Implicits.global)
              result
            }"""

          case futureRegx2(t) =>
            s"""(v: scala.concurrent.Future[net.liftweb.common.Box[(String, scala.Option[code.api.util.CallContext])]]) =>{
              implicit val formats = code.api.util.CustomJsonFormats.formats
              val result : Future[Box[$t]] =
                v.map(_.map(it => com.openbankproject.commons.util.JsonAliases.parse(it._1).extract[$t]))(com.openbankproject.commons.ExecutionContext.Implicits.global)
              result
            }"""

          case futureRegx3(t) =>
            s"""(v: scala.concurrent.Future[net.liftweb.common.Box[(String, scala.Option[code.api.util.CallContext])]]) =>{
              implicit val formats = code.api.util.CustomJsonFormats.formats
              val result : Future[$t] =
                v.map(_.map(it => com.openbankproject.commons.util.JsonAliases.parse(it._1).extract[$t]).orNull)(com.openbankproject.commons.ExecutionContext.Implicits.global)
              result
            }"""

          case obpReturnTypeRegx1(t) =>
            s"""(v: scala.concurrent.Future[net.liftweb.common.Box[(String, scala.Option[code.api.util.CallContext])]]) =>{
              implicit val formats = code.api.util.CustomJsonFormats.formats
              val result : Future[(Box[$t], Option[CallContext])] = v.map { box =>
                  val net.liftweb.common.Full((zson , cc)) = box
                  (Box !! com.openbankproject.commons.util.JsonAliases.parse(zson).extract[$t]) -> cc
                }(com.openbankproject.commons.ExecutionContext.Implicits.global)
              result
            }"""

          case obpReturnTypeRegx2(t) =>
            s"""(v: scala.concurrent.Future[net.liftweb.common.Box[(String, scala.Option[code.api.util.CallContext])]]) =>{
              implicit val formats = code.api.util.CustomJsonFormats.formats
              val result : Future[($t, Option[CallContext])] = v.map { box =>
                  val net.liftweb.common.Full((zson , cc )) = box
                  com.openbankproject.commons.util.JsonAliases.parse(zson).extract[$t] -> cc
                }(com.openbankproject.commons.ExecutionContext.Implicits.global)
              result
            }"""

          case otherTypeRegx(t) =>
            s"""(v: scala.concurrent.Future[net.liftweb.common.Box[(String, scala.Option[code.api.util.CallContext])]]) =>{
              implicit val formats = code.api.util.CustomJsonFormats.formats
              import scala.concurrent.duration._
              val f: Future[$t] = v.map { box =>
                  val net.liftweb.common.Full((zson , _ )) = box
                  com.openbankproject.commons.util.JsonAliases.parse(zson).extract[$t]
              }(com.openbankproject.commons.ExecutionContext.Implicits.global)

              val result: $t = scala.concurrent.Await.result(f, 5 minutes)
              result
            }"""
        }

      val argList = signature
        .replaceFirst("""(,\s*)?(\w+)\s*:\s*Option\[code.api.util.CallContext\]""", "")
        .replaceAll("""\((.*)\)\s*:.+$""", "$1")
        .replaceAll(""":.+?($|,)""", "$1")


      val args = s"Array($argList)"
      val body = StringEscapeUtils.escapeJava(methodBody)
      val cc = getCallContextParamName(signature)
      s"""val convertor = $convertor
      val net.liftweb.common.Full(dynamicFunc) = $dynamicFunctionCreator("$body")
      val result = dynamicFunc($args, $cc)
      convertor(result)"""


    case _ => ""
  }

  /**
   * dynamic create function
   *
   * @param methodName method name of connector
   * @param methodBody method body of connector method
   * @param lang methodBody programming language
   * @return function of connector method that is dynamic created, can be Function0, Function1, Function2...
   */
  def createFunction(methodName: String, methodBody:String, programmingLang: String): Box[AnyRef] = programmingLang match {
    case "js" | "Js" | "javascript" | "JavaScript" =>
      // just the value: "code.api.util.DynamicUtil.createJsFunction"
      val jsFunctionCreator = s"${ReflectUtils.getType(DynamicUtil).typeSymbol.fullName}.${nameOf(DynamicUtil.createJsFunction _)}"
      val jsMethodBody = buildDynamicMethodBody(methodName, methodBody, jsFunctionCreator)
      createScalaFunction(methodName, jsMethodBody)

    case "Java" | "java" =>
      // just the value: "code.api.util.DynamicUtil.createJavaFunction"
      val javaFunctionCreator = s"${ReflectUtils.getType(DynamicUtil).typeSymbol.fullName}.${nameOf(DynamicUtil.createJavaFunction _)}"
      val javaMethodBody = buildDynamicMethodBody(methodName, methodBody, javaFunctionCreator)
      createScalaFunction(methodName, javaMethodBody)

    case "Scala" | "scala" | "" | null => createScalaFunction(methodName, methodBody)
    case _ => Failure(s"$DynamicCodeLangNotSupport programmingLang $programmingLang, currently supported languages: Java, Javascript and Scala")
  }

  /**
   * dynamic create scala function
   * @param methodName method name of connector
   * @param methodBody method body of connector method
   * @return function of connector method that is dynamic created, can be Function0, Function1, Function2...
   */
  private def createScalaFunction(methodName: String, methodBody:String): Box[AnyRef]=
    methodNameToSignature.get(methodName)  match {
      case Some(signature) =>
        val cc = getCallContextParamName(signature)
        val postProcessorName = s"${ReflectUtils.getType(InternalConnector).typeSymbol.fullName}.${nameOf(InternalConnector.postProcessConnectorMethodResult _)}"
        val method = s"""
                        |def $methodName $signature = {
                        |  ${DynamicUtil.importStatements}
                        |
                        |  val _$$result$$_ = {$methodBody}
                        |   $postProcessorName(_$$result$$_ , $cc)
                        |}
                        |
                        |$methodName _
                        |""".stripMargin

        compileScalaCode(method)
      case None => Failure(s"$InvalidConnectorMethodName method name $methodName does not exist in the Connector")
    }

   def postProcessConnectorMethodResult[T](value: T, callContext:Option[CallContext]):T = value match {
     case Full((v, null|None)) =>
       Full(v -> callContext).asInstanceOf[T]
     case (v, null|None)  =>
       (v, callContext).asInstanceOf[T]
     case f: Future[_] =>
       import com.openbankproject.commons.ExecutionContext.Implicits.global
       f.map(it => postProcessConnectorMethodResult(it, callContext)).asInstanceOf[T]
     case _ => value
  }

  private def callableMethods: Map[String, MethodSymbol] = {
    val dynamicMethods: Map[String, MethodSymbol] = ConnectorMethodProvider.provider.vend.getAll().map {
      case v: JsonConnectorMethod =>
        val methodName = v.methodName
        methodName -> Box(methodNameToSymbols.get(methodName)).openOrThrowException(s"method name $methodName does not exist in the Connector")
    }.toMap

    dynamicMethods
  }

  private lazy val methodNameToSymbols: Map[String, MethodSymbol] = typeOf[Connector].decls.collect {
    case t: TermSymbol if t.isMethod && t.isPublic && !t.isConstructor && !t.isVal && !t.isVar =>
      val methodName = t.name.decodedName.toString.trim
      val method = t.asMethod
      methodName -> method
  }.toMap

  lazy val methodNameToSignature: Map[String, String] = methodNameToSymbols.map {
    case (methodName, methodSymbol) =>
      val signature = methodSymbol.typeSignature.toString
      val returnType = methodSymbol.returnType.toString
      // Strip any colon the parameter part already ends with before adding one back. 2.12 rendered
      // a method type as "(params)ReturnType" and 2.13 renders it as "(params): ReturnType", so
      // appending unconditionally produced "(params): : ReturnType" - which the runtime compiler
      // rejected with "identifier expected but ':' found", failing every dynamic connector method.
      val paramsPart = StringUtils.substringBeforeLast(signature, returnType).trim.stripSuffix(":")
      // No space after the colon: the boxRegx/futureRegx/obpReturnTypeRegx patterns above match
      // ")\\s*:" immediately followed by the type name.
      val methodSignature = s"$paramsPart:$returnType"
      methodName -> methodSignature
  }
}