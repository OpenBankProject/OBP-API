package code.bankconnectors

import code.api.util.DynamicUtil.compileScalaCode
import code.connectormethod.{ConnectorMethodProvider, JsonConnectorMethod}
import com.github.dwickern.macros.NameOf.nameOf
import net.liftweb.common.{Box, Failure}
import net.sf.cglib.proxy.{Enhancer, MethodInterceptor, MethodProxy}
import org.apache.commons.lang3.StringUtils
import java.lang.reflect.Method
import code.api.util.DynamicUtil
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

