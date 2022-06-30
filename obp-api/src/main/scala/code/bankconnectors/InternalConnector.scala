package code.bankconnectors

import code.api.util.DynamicUtil.compileScalaCode
import code.api.util.ErrorMessages.{DynamicCodeLangNotSupport, InvalidConnectorMethodName}
import net.liftweb.common.Full

import scala.concurrent.Future
import code.connectormethod.{ConnectorMethodProvider, JsonConnectorMethod}
import com.github.dwickern.macros.NameOf.nameOf
import net.liftweb.common.{Box, Failure}
import net.sf.cglib.proxy.{Enhancer, MethodInterceptor, MethodProxy}

import java.lang.reflect.Method
import code.api.util.{CallContext, DynamicUtil}
import com.auth0.jwt.internal.org.apache.commons.lang3.StringEscapeUtils
import org.apache.commons.lang3.StringUtils
import com.github.dwickern.macros.NameOf.{nameOf, qualifiedNameOfType}
import com.openbankproject.commons.util.ReflectUtils

import scala.reflect.runtime.universe.{MethodSymbol, TermSymbol, typeOf}


object InternalConnector {

  lazy val instance: Connector = {
    val enhancer: Enhancer = new Enhancer()
    enhancer.setSuperclass(classOf[Connector])
    enhancer.setCallback(intercept)
    enhancer.create().asInstanceOf[Connector]
  }

  //this object is a empty Connector implementation, just for supply default args
  private object connector extends Connector {
    // you can create method at here and copy the method body to create `ConnectorMethod`, but never keep the code
    // in this object, you must make sure this object is empty.
  }

  private val intercept:MethodInterceptor = (_: Any, method: Method, args: Array[AnyRef], _: MethodProxy) => {
    val methodName = method.getName
    if(methodName == nameOf(connector.callableMethods)) {
      this.callableMethods
    } else if (methodName.contains("$default$")) {
      method.invoke(connector, args:_*)
    } else {
      val function = getFunction(methodName)
      DynamicUtil.executeFunction(methodName, function, args)
    }
  }

  private def getFunction(methodName: String) = {
    ConnectorMethodProvider.provider.vend.getByMethodNameWithCache(methodName) map {
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
                v.map(_.map(it =>(net.liftweb.json.parse(it._1).extract[$t], it._2)))(com.openbankproject.commons.ExecutionContext.Implicits.global)
              val result: Box[($t, Option[CallContext])] = scala.concurrent.Await.result(f, 5 minutes)
              result
            }"""

          case boxRegx2(t)   =>
            s"""(v: scala.concurrent.Future[net.liftweb.common.Box[(String, scala.Option[code.api.util.CallContext])]]) =>{
              implicit val formats = code.api.util.CustomJsonFormats.formats
              import scala.concurrent.duration._
              val f: Future[Box[$t]] =
                v.map(_.map(it =>net.liftweb.json.parse(it._1).extract[$t]))(com.openbankproject.commons.ExecutionContext.Implicits.global)
              val result: Box[$t] = scala.concurrent.Await.result(f, 5 minutes)
              result
            }"""

          case futureRegx1(t) =>
            s"""(v: scala.concurrent.Future[net.liftweb.common.Box[(String, scala.Option[code.api.util.CallContext])]]) =>{
              implicit val formats = code.api.util.CustomJsonFormats.formats
              val result : Future[Box[($t, Option[CallContext])]] =
                v.map(_.map(it =>(net.liftweb.json.parse(it._1).extract[$t], it._2)))(com.openbankproject.commons.ExecutionContext.Implicits.global)
              result
            }"""

          case futureRegx2(t) =>
            s"""(v: scala.concurrent.Future[net.liftweb.common.Box[(String, scala.Option[code.api.util.CallContext])]]) =>{
              implicit val formats = code.api.util.CustomJsonFormats.formats
              val result : Future[Box[$t]] =
                v.map(_.map(it => net.liftweb.json.parse(it._1).extract[$t]))(com.openbankproject.commons.ExecutionContext.Implicits.global)
              result
            }"""

          case futureRegx3(t) =>
            s"""(v: scala.concurrent.Future[net.liftweb.common.Box[(String, scala.Option[code.api.util.CallContext])]]) =>{
              implicit val formats = code.api.util.CustomJsonFormats.formats
              val result : Future[$t] =
                v.map(_.map(it => net.liftweb.json.parse(it._1).extract[$t]).orNull)(com.openbankproject.commons.ExecutionContext.Implicits.global)
              result
            }"""

          case obpReturnTypeRegx1(t) =>
            s"""(v: scala.concurrent.Future[net.liftweb.common.Box[(String, scala.Option[code.api.util.CallContext])]]) =>{
              implicit val formats = code.api.util.CustomJsonFormats.formats
              val result : Future[(Box[$t], Option[CallContext])] = v.map { box =>
                  val net.liftweb.common.Full((zson , cc)) = box
                  (Box !! net.liftweb.json.parse(zson).extract[$t]) -> cc
                }(com.openbankproject.commons.ExecutionContext.Implicits.global)
              result
            }"""

          case obpReturnTypeRegx2(t) =>
            s"""(v: scala.concurrent.Future[net.liftweb.common.Box[(String, scala.Option[code.api.util.CallContext])]]) =>{
              implicit val formats = code.api.util.CustomJsonFormats.formats
              val result : Future[($t, Option[CallContext])] = v.map { box =>
                  val net.liftweb.common.Full((zson , cc )) = box
                  net.liftweb.json.parse(zson).extract[$t] -> cc
                }(com.openbankproject.commons.ExecutionContext.Implicits.global)
              result
            }"""

          case otherTypeRegx(t) =>
            s"""(v: scala.concurrent.Future[net.liftweb.common.Box[(String, scala.Option[code.api.util.CallContext])]]) =>{
              implicit val formats = code.api.util.CustomJsonFormats.formats
              import scala.concurrent.duration._
              val f: Future[$t] = v.map { box =>
                  val net.liftweb.common.Full((zson , _ )) = box
                  net.liftweb.json.parse(zson).extract[$t]
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
      case JsonConnectorMethod(_, methodName, _, _) =>
        methodName -> Box(methodNameToSymbols.get(methodName)).openOrThrowException(s"method name $methodName does not exist in the Connector")
    } toMap

    dynamicMethods
  }

  private lazy val methodNameToSymbols: Map[String, MethodSymbol] = typeOf[Connector].decls collect {
    case t: TermSymbol if t.isMethod && t.isPublic && !t.isConstructor && !t.isVal && !t.isVar =>
      val methodName = t.name.decodedName.toString.trim
      val method = t.asMethod
      methodName -> method
  } toMap

  lazy val methodNameToSignature: Map[String, String] = methodNameToSymbols map {
    case (methodName, methodSymbol) =>
      val signature = methodSymbol.typeSignature.toString
      val returnType = methodSymbol.returnType.toString
      val methodSignature = StringUtils.substringBeforeLast(signature, returnType) + ":" + returnType
      methodName -> methodSignature
  }
}