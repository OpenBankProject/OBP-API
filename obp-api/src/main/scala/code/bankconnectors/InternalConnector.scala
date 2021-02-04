package code.bankconnectors

import code.connectormethod.{ConnectorMethodProvider, JsonConnectorMethod}
import com.github.dwickern.macros.NameOf.nameOf
import net.liftweb.common.{Box, Failure}
import net.sf.cglib.proxy.{Enhancer, MethodInterceptor, MethodProxy}
import org.apache.commons.lang3.StringUtils

import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap
import scala.reflect.runtime.universe.{MethodSymbol, TermSymbol, typeOf}
import scala.tools.reflect.{ToolBox, ToolBoxError}

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

  // (methodName,methodBody) -> dynamic method function
  // connector methods count is 230, make the initialCapacity a little bigger
  private val dynamicMethods = new ConcurrentHashMap[(String, String), Any](300)

  private val intercept:MethodInterceptor = (_: Any, method: Method, args: Array[AnyRef], _: MethodProxy) => {
    val methodName = method.getName
    if(methodName == nameOf(connector.callableMethods)) {
      this.callableMethods
    } else if (methodName.contains("$default$")) {
      method.invoke(connector, args:_*)
    } else {
       val result = getFunction(methodName).orNull match {
        case func: Function0[AnyRef] => func()
        case func: Function1[AnyRef,AnyRef]  => func(args.head)
        case func: Function2[AnyRef,AnyRef,AnyRef]  => func(args.head, args.apply(1))
        case func: Function3[AnyRef,AnyRef,AnyRef,AnyRef]  => func(args.head, args.apply(1), args.apply(2))
        case func: Function4[AnyRef,AnyRef,AnyRef,AnyRef,AnyRef]  => func(args.head, args.apply(1), args.apply(2), args.apply(3))
        case func: Function5[AnyRef,AnyRef,AnyRef,AnyRef,AnyRef,AnyRef]  => func(args.head, args.apply(1), args.apply(2), args.apply(3), args.apply(4))
        case func: Function6[AnyRef,AnyRef,AnyRef,AnyRef,AnyRef,AnyRef,AnyRef]  => func(args.head, args.apply(1), args.apply(2), args.apply(3), args.apply(4), args.apply(5))
        case func: Function7[AnyRef,AnyRef,AnyRef,AnyRef,AnyRef,AnyRef,AnyRef,AnyRef]  => func(args.head, args.apply(1), args.apply(2), args.apply(3), args.apply(4), args.apply(5), args.apply(6))
        case func: Function8[AnyRef,AnyRef,AnyRef,AnyRef,AnyRef,AnyRef,AnyRef,AnyRef,AnyRef]  => func(args.head, args.apply(1), args.apply(2), args.apply(3), args.apply(4), args.apply(5), args.apply(6), args.apply(7))
        case func: Function9[AnyRef,AnyRef,AnyRef,AnyRef,AnyRef,AnyRef,AnyRef,AnyRef,AnyRef,AnyRef]  => func(args.head, args.apply(1), args.apply(2), args.apply(3), args.apply(4), args.apply(5), args.apply(6), args.apply(7), args.apply(8))
        case func: Function10[AnyRef,AnyRef,AnyRef,AnyRef,AnyRef,AnyRef,AnyRef,AnyRef,AnyRef,AnyRef,AnyRef]  => func(args.head, args.apply(1), args.apply(2), args.apply(3), args.apply(4), args.apply(5), args.apply(6), args.apply(7), args.apply(8), args.apply(9))
        case func: Function11[AnyRef,AnyRef,AnyRef,AnyRef,AnyRef,AnyRef,AnyRef,AnyRef,AnyRef,AnyRef,AnyRef,AnyRef]  => func(args.head, args.apply(1), args.apply(2), args.apply(3), args.apply(4), args.apply(5), args.apply(6), args.apply(7), args.apply(8), args.apply(9), args.apply(10))
        case func: Function12[AnyRef,AnyRef,AnyRef,AnyRef,AnyRef,AnyRef,AnyRef,AnyRef,AnyRef,AnyRef,AnyRef,AnyRef,AnyRef]  => func(args.head, args.apply(1), args.apply(2), args.apply(3), args.apply(4), args.apply(5), args.apply(6), args.apply(7), args.apply(8), args.apply(9), args.apply(10), args.apply(11))
        case func: Function13[AnyRef,AnyRef,AnyRef,AnyRef,AnyRef,AnyRef,AnyRef,AnyRef,AnyRef,AnyRef,AnyRef,AnyRef,AnyRef,AnyRef]  => func(args.head, args.apply(1), args.apply(2), args.apply(3), args.apply(4), args.apply(5), args.apply(6), args.apply(7), args.apply(8), args.apply(9), args.apply(10), args.apply(11), args.apply(12))
        case func: Function14[AnyRef,AnyRef,AnyRef,AnyRef,AnyRef,AnyRef,AnyRef,AnyRef,AnyRef,AnyRef,AnyRef,AnyRef,AnyRef,AnyRef,AnyRef]  => func(args.head, args.apply(1), args.apply(2), args.apply(3), args.apply(4), args.apply(5), args.apply(6), args.apply(7), args.apply(8), args.apply(9), args.apply(10), args.apply(11), args.apply(12), args.apply(13))
        case func: Function15[AnyRef,AnyRef,AnyRef,AnyRef,AnyRef,AnyRef,AnyRef,AnyRef,AnyRef,AnyRef,AnyRef,AnyRef,AnyRef,AnyRef,AnyRef,AnyRef]  => func(args.head, args.apply(1), args.apply(2), args.apply(3), args.apply(4), args.apply(5), args.apply(6), args.apply(7), args.apply(8), args.apply(9), args.apply(10), args.apply(11), args.apply(12), args.apply(13), args.apply(14))
        case func: Function16[AnyRef,AnyRef,AnyRef,AnyRef,AnyRef,AnyRef,AnyRef,AnyRef,AnyRef,AnyRef,AnyRef,AnyRef,AnyRef,AnyRef,AnyRef,AnyRef,AnyRef]  => func(args.head, args.apply(1), args.apply(2), args.apply(3), args.apply(4), args.apply(5), args.apply(6), args.apply(7), args.apply(8), args.apply(9), args.apply(10), args.apply(11), args.apply(12), args.apply(13), args.apply(14), args.apply(15))
        case func: Function17[AnyRef,AnyRef,AnyRef,AnyRef,AnyRef,AnyRef,AnyRef,AnyRef,AnyRef,AnyRef,AnyRef,AnyRef,AnyRef,AnyRef,AnyRef,AnyRef,AnyRef,AnyRef]  => func(args.head, args.apply(1), args.apply(2), args.apply(3), args.apply(4), args.apply(5), args.apply(6), args.apply(7), args.apply(8), args.apply(9), args.apply(10), args.apply(11), args.apply(12), args.apply(13), args.apply(14), args.apply(15), args.apply(16))
        case func: Function18[AnyRef,AnyRef,AnyRef,AnyRef,AnyRef,AnyRef,AnyRef,AnyRef,AnyRef,AnyRef,AnyRef,AnyRef,AnyRef,AnyRef,AnyRef,AnyRef,AnyRef,AnyRef,AnyRef]  => func(args.head, args.apply(1), args.apply(2), args.apply(3), args.apply(4), args.apply(5), args.apply(6), args.apply(7), args.apply(8), args.apply(9), args.apply(10), args.apply(11), args.apply(12), args.apply(13), args.apply(14), args.apply(15), args.apply(16), args.apply(17))
        case func: Function19[AnyRef,AnyRef,AnyRef,AnyRef,AnyRef,AnyRef,AnyRef,AnyRef,AnyRef,AnyRef,AnyRef,AnyRef,AnyRef,AnyRef,AnyRef,AnyRef,AnyRef,AnyRef,AnyRef,AnyRef]  => func(args.head, args.apply(1), args.apply(2), args.apply(3), args.apply(4), args.apply(5), args.apply(6), args.apply(7), args.apply(8), args.apply(9), args.apply(10), args.apply(11), args.apply(12), args.apply(13), args.apply(14), args.apply(15), args.apply(16), args.apply(17), args.apply(18))
        case func: Function20[AnyRef,AnyRef,AnyRef,AnyRef,AnyRef,AnyRef,AnyRef,AnyRef,AnyRef,AnyRef,AnyRef,AnyRef,AnyRef,AnyRef,AnyRef,AnyRef,AnyRef,AnyRef,AnyRef,AnyRef,AnyRef]  => func(args.head, args.apply(1), args.apply(2), args.apply(3), args.apply(4), args.apply(5), args.apply(6), args.apply(7), args.apply(8), args.apply(9), args.apply(10), args.apply(11), args.apply(12), args.apply(13), args.apply(14), args.apply(15), args.apply(16), args.apply(17), args.apply(18), args.apply(19))
        case null => throw new IllegalStateException(s"InternalConnector have no method $methodName, it should not be called on InternalConnector")
        case _ => throw new IllegalStateException(s"InternalConnector have not correct method: $methodName")
      }
      result.asInstanceOf[AnyRef]
    }
  }

  private def getFunction(methodName: String) = {
    ConnectorMethodProvider.provider.vend.getByMethodNameWithCache(methodName) map {
      case v @ JsonConnectorMethod(_, _, methodBody) =>
         dynamicMethods.computeIfAbsent(
           methodName -> methodBody,
           _ => createFunction(methodName, v.decodedMethodBody).openOrThrowException(s"InternalConnector method compile fail, method name $methodName")
         )
    }
  }

  private val toolBox = scala.reflect.runtime.currentMirror.mkToolBox()
//  private val toolBox = runtimeMirror(getClass.getClassLoader).mkToolBox()

  /**
   * dynamic create function
   * @param methodName method name of connector
   * @param methodBody method body of connector method
   * @return function of connector method that is dynamic created, can be Function0, Function1, Function2...
   */
  def createFunction(methodName: String, methodBody:String): Box[Any] =
    methodNameToSignature.get(methodName)  match {
      case Some(signature) =>
        val method = s"""
                        |def $methodName $signature = {
                        |  $importStatements
                        |
                        |  $methodBody
                        |}
                        |
                        |$methodName _
                        |""".stripMargin

        compile(method)
      case None => Failure(s"method name $methodName not exists in Connector")
    }

  /**
   * toolBox have bug that first compile fail, second or later compile success.
   * @param code
   * @return compiled function or Failure
   */
  private def compile(code: String): Box[Any] = {

    val tree = try {
      toolBox.parse(code)
    } catch {
      case e: ToolBoxError =>
        return Failure(e.message)
    }

    try {
      val func: () => Any = toolBox.compile(tree)
      Box.tryo(func())
    } catch {
      case _: ToolBoxError =>
        // try compile again
        try {
          val func: () => Any = toolBox.compile(tree)
          Box.tryo(func())
        } catch {
          case e: ToolBoxError =>
            Failure(e.message)
        }
    }

  }

  private def callableMethods: Map[String, MethodSymbol] = {
    val dynamicMethods: Map[String, MethodSymbol] = ConnectorMethodProvider.provider.vend.getAll().map {
      case JsonConnectorMethod(_, methodName, _) =>
        methodName -> Box(methodNameToSymbols.get(methodName)).openOrThrowException(s"method name $methodName not exists in Connector")
    } toMap

    dynamicMethods
  }

  private lazy val methodNameToSymbols: Map[String, MethodSymbol] = typeOf[Connector].decls collect {
    case t: TermSymbol if t.isMethod && t.isPublic && !t.isConstructor && !t.isVal && !t.isVar =>
      val methodName = t.name.decodedName.toString.trim
      val method = t.asMethod
      methodName -> method
  } toMap

  private lazy val methodNameToSignature: Map[String, String] = methodNameToSymbols map {
    case (methodName, methodSymbol) =>
      val signature = methodSymbol.typeSignature.toString
      val returnType = methodSymbol.returnType.toString
      val methodSignature = StringUtils.substringBeforeLast(signature, returnType) + ":" + returnType
      methodName -> methodSignature
  }

  /**
   * common import statements those are used by connector method body
   */
  private val importStatements =
    """
      |import java.net.{ConnectException, URLEncoder, UnknownHostException}
      |import java.util.Date
      |import java.util.UUID.randomUUID
      |
      |import _root_.akka.stream.StreamTcpException
      |import akka.http.scaladsl.model.headers.RawHeader
      |import akka.http.scaladsl.model.{HttpProtocol, _}
      |import akka.util.ByteString
      |import code.api.APIFailureNewStyle
      |import code.api.ResourceDocs1_4_0.MessageDocsSwaggerDefinitions
      |import code.api.cache.Caching
      |import code.api.util.APIUtil.{AdapterImplementation, MessageDoc, OBPReturnType, saveConnectorMetric, _}
      |import code.api.util.ErrorMessages._
      |import code.api.util.ExampleValue._
      |import code.api.util.{APIUtil, CallContext, OBPQueryParam}
      |import code.api.v4_0_0.MockResponseHolder
      |import code.bankconnectors._
      |import code.bankconnectors.vJune2017.AuthInfo
      |import code.customer.internalMapping.MappedCustomerIdMappingProvider
      |import code.kafka.KafkaHelper
      |import code.model.dataAccess.internalMapping.MappedAccountIdMappingProvider
      |import code.util.AkkaHttpClient._
      |import code.util.Helper.MdcLoggable
      |import com.openbankproject.commons.dto.{InBoundTrait, _}
      |import com.openbankproject.commons.model.enums.StrongCustomerAuthentication.SCA
      |import com.openbankproject.commons.model.enums.{AccountAttributeType, CardAttributeType, DynamicEntityOperation, ProductAttributeType}
      |import com.openbankproject.commons.model.{ErrorMessage, TopicTrait, _}
      |import com.openbankproject.commons.util.{JsonUtils, ReflectUtils}
      |// import com.tesobe.{CacheKeyFromArguments, CacheKeyOmit}
      |import net.liftweb.common.{Box, Empty, _}
      |import net.liftweb.json
      |import net.liftweb.json.Extraction.decompose
      |import net.liftweb.json.JsonDSL._
      |import net.liftweb.json.JsonParser.ParseException
      |import net.liftweb.json.{JValue, _}
      |import net.liftweb.util.Helpers.tryo
      |import org.apache.commons.lang3.StringUtils
      |
      |import scala.collection.immutable.List
      |import scala.collection.mutable.ArrayBuffer
      |import scala.concurrent.duration._
      |import scala.concurrent.{Await, Future}
      |import com.openbankproject.commons.dto._
      |""".stripMargin

}

