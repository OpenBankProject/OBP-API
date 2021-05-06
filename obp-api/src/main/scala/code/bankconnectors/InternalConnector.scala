package code.bankconnectors

import code.api.util.DynamicUtil.compileScalaCode
import code.connectormethod.{ConnectorMethodProvider, JsonConnectorMethod}
import com.github.dwickern.macros.NameOf.nameOf
import net.liftweb.common.{Box, Failure, Full}
import net.sf.cglib.proxy.{Enhancer, MethodInterceptor, MethodProxy}
import org.apache.commons.lang3.StringUtils
import java.lang.reflect.Method
import java.util.Date

import code.adapter.soap.customers.{ListCustomers, ListCustomersQuery}
import code.adapter.soap.orders.{InternalAccountIdentification, ListGLTransactions, ListGLTransactionsQuery, RequestHeaders}
import code.api.BerlinGroup.{AuthenticationType, ScaStatus}
import code.api.util.APIUtil.{OBPReturnType, connectorEmptyResponse}
import code.api.util.ErrorMessages.{InvalidAuthContextUpdateRequestKey, InvalidConnectorResponse, InvalidJsonFormat, MissingPropsValueAtThisInstance, ScaMethodNotDefined, SmsServerNotResponding}
import code.api.util._
import code.api.v4_0_0.CallLimitPostJsonV400
import code.bankconnectors.LocalMappedConnector.{createChallengeInternal, getBankAccountsHeldLegacy, logger}
import code.model.dataAccess.MappedBankAccount
import code.model.dataAccess.internalMapping.MappedAccountIdMappingProvider
import code.util.Helper
import com.openbankproject.commons.model._
import com.openbankproject.commons.util.optional
import net.liftweb.mapper.By

import scala.collection.immutable.List
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Await, Future}
import scala.reflect.runtime.universe.{MethodSymbol, TermSymbol, typeOf}
import code.api.util.DynamicUtil
import code.util.AkkaHttpClient.{makeHttpRequest, prepareHttpRequest}
import code.util.Helper.MdcLoggable
import code.bankconnectors.{akka => obpakka}
import code.database.authorisation.Authorisations
import code.transactionChallenge.Challenges
import com.nexmo.client.NexmoClient
import com.nexmo.client.sms.messages.TextMessage
import com.openbankproject.commons.model.enums.{ChallengeType, StrongCustomerAuthentication}
import com.openbankproject.commons.model.enums.StrongCustomerAuthentication.SCA
import com.openbankproject.commons.model.enums.StrongCustomerAuthenticationStatus.SCAStatus
import net.liftweb.json.parse
import net.liftweb.util.Helpers.tryo
import net.liftweb.util.Mailer
import net.liftweb.util.Mailer.{From, PlainMailBodyType, Subject, To}
import org.mindrot.jbcrypt.BCrypt

import scala.util.Random

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
        createFunction(methodName, v.decodedMethodBody).openOrThrowException(s"InternalConnector method compile fail, method name $methodName")
    }
  }

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
                        |  ${DynamicUtil.importStatements}
                        |
                        |  $methodBody
                        |}
                        |
                        |$methodName _
                        |""".stripMargin

        compileScalaCode(method)
      case None => Failure(s"method name $methodName does not exist in the Connector")
    }



  private def callableMethods: Map[String, MethodSymbol] = {
    val dynamicMethods: Map[String, MethodSymbol] = ConnectorMethodProvider.provider.vend.getAll().map {
      case JsonConnectorMethod(_, methodName, _) =>
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

  private lazy val methodNameToSignature: Map[String, String] = methodNameToSymbols map {
    case (methodName, methodSymbol) =>
      val signature = methodSymbol.typeSignature.toString
      val returnType = methodSymbol.returnType.toString
      val methodSignature = StringUtils.substringBeforeLast(signature, returnType) + ":" + returnType
      methodName -> methodSignature
  }
}

object abc extends App{
  import scala.concurrent.ExecutionContext.Implicits.global
  import scala.concurrent._
  import scala.concurrent.duration._
  import _root_.akka.http.javadsl.model.HttpEntity
  import net.liftweb.json.Formats
  implicit val formats = CustomJsonFormats.formats
  import net.liftweb.util.Helpers.tryo

  
  case class CbsExternalUserResponse(
    userId: Int,
    customerId: Int,
    customerNameKa: String,
    isCorporateCustomer: Boolean
  )
  
  val request = prepareHttpRequest(
    "http://localhost:8083/api/v1/user/authorize",
    _root_.akka.http.scaladsl.model.HttpMethods.POST,
    _root_.akka.http.scaladsl.model.HttpProtocol("HTTP/1.1"),
    s"""{"userName": "NATROSHVILI","password": "Aa123456"}"""
  )
  val responseFuture = makeHttpRequest(request)

  val listAccountsServiceResponseResult = Await.result(responseFuture, 30 seconds).entity.asInstanceOf[HttpEntity.Strict].getData().utf8String
  val object1= parse(listAccountsServiceResponseResult).extract[CbsExternalUserResponse]

  println(123123)
  println(listAccountsServiceResponseResult)
  println(object1)
}