package code

import java.lang.reflect.Method

import code.api.util.NewStyle
import code.bankconnectors.akka.AkkaConnector_vDec2018
import code.bankconnectors.rest.RestConnector_vMar2019
import code.bankconnectors.vSept2018.KafkaMappedConnector_vSept2018
import code.methodrouting.{MethodRouting}
import com.openbankproject.commons.model.BankId
import com.openbankproject.commons.util.ReflectUtils.{findMethodByArgs, getConstructorArgs}
import net.liftweb.common.{Box, EmptyBox}
import net.sf.cglib.proxy.{Enhancer, MethodInterceptor, MethodProxy}

import scala.reflect.runtime.universe.{Type, typeOf}

package object bankconnectors {

  /**
    * a star connector object, usage:
    *
    * first modify default.props, default connector is mapped:
    *   connector=star
    *   connector.start.methodName.getBanks=mapped
    *   connector.start.methodName.getCustomersByUserIdFuture=rest_vMar2019
    *
    * run the follow demo code anywhere
    *   import code.bankconnectors.StarConnector
    *   val b = new Boot() // initiate connectors state
    *   StarConnector.getBanks(None) //call LocalMappedConnector
    *   StarConnector.getCustomersByUserIdFuture("hello", None) // call RestConnector_vMar2019
    */
  val StarConnector: Connector = {
    //this object is a empty Connector implementation, just for supply default args
    object StubConnector extends Connector

    val intercept:MethodInterceptor = (_: Any, method: Method, args: Array[AnyRef], _: MethodProxy) => {
      // when method name contains $default$, that means method have default parameter, this is not the correct call,
      // so just get the default parameter values from StubConnector
      if(method.getName.contains("$default$")) {
          method.invoke(StubConnector, args:_*)
      } else {
        val objToCall =  getConnectorObject(method, args)
        method.invoke(objToCall, args:_*)
      }
    }
    val enhancer: Enhancer = new Enhancer()
    enhancer.setSuperclass(classOf[Connector])
    enhancer.setCallback(intercept)
    enhancer.create().asInstanceOf[Connector]
  }

  /**
    * according invoked method and arguments value to find connector object
    * @param method invoked method
    * @param args passed arguments
    * @return connector Object
    */
  private[this]def getConnectorObject(method: Method, args: Seq[Any]): Connector = {
    val methodName = method.getName
    val methodSymbol = findMethodByArgs(typeOf[Connector], methodName, args:_*).getOrElse(sys.error(s"not found matched method, method name: ${methodName}, params: ${args.mkString(",")}"))
    val paramList = methodSymbol.paramLists.headOption.getOrElse(Nil)
    val paramNameToType: Map[String, Type] = paramList.map(param => (param.name.toString, param.info)).toMap
    val paramNameToValue: Map[String, Any] = paramList.zip(args).map(pair =>(pair._1.name.toString, pair._2)).toMap

    val bankIdInArgs = paramNameToValue.find(isBankId).map(_._2)

    val bankId: Option[String] = bankIdInArgs match {
      case Some(v) => Some(v.toString())
      case None => args.toStream.map(getNestedBankId(_)).find(_.isDefined).flatten.map(_.toString)
    }

    val connectorName: Box[String] = bankId match {
      case None => NewStyle.function.getMethodRoutings(Some(methodName), Some(false))
        .find {routing =>
          val bankIdPattern = routing.bankIdPattern
          bankIdPattern.isEmpty || bankIdPattern.get == MethodRouting.bankIdPatternMatchAny
        }.map(_.connectorName)
      // found bankId in method args, so query connectorName with bankId
      case Some(bankId) => {
        //if methodName and bankId do exact match query no result, do query with methodName, and use bankId do match with bankIdPattern
        NewStyle.function.getMethodRoutings(Some(methodName), Some(true), Some(bankId)).headOption
          .orElse {
            NewStyle.function.getMethodRoutings(Some(methodName), Some(false))
              .filter {methodRouting=>
                methodRouting.bankIdPattern.isEmpty || bankId.matches(methodRouting.bankIdPattern.get)
              }
              .sortBy(_.bankIdPattern) // if there are both matched bankIdPattern and null bankIdPattern, the have value bankIdPattern success
              .lastOption
          }.map(_.connectorName)
      }
    }

    connectorName.getOrElse("mapped") match {
      case "mapped" => LocalMappedConnector
      case "rest_vMar2019" => RestConnector_vMar2019
      case "kafka_vSept2018" => KafkaMappedConnector_vSept2018
      case "akka_vDec2018" => AkkaConnector_vDec2018
      case _ => throw new IllegalStateException(s"config of connector.start.methodName.${methodName} have wrong value, not exists connector of name ${connectorName.get}")
    }
  }


  /**
    * according valueName and value to check if the value is bankId
    * @param valueNameAndValue tuple of valueName and value
    * @return true if this value is bankId
    */
  private[this] def isBankId(valueNameAndValue: (String, Any)) = {
    valueNameAndValue match {
      case ("bankId", _) => true
      case (_, _:BankId) => true
      case(valueName, _:String) if(valueName.endsWith("BankId")) => true
      case _ => false
    }
  }
  /**
    * find nested bankId value in the object, For example:
    * BankAccount(BankId("bkId"), AccountId("aId")) ---> Some(BankId("bkId"))
    * List(BankId("bkId"), BankId("bkId2")) ---> Some(BankId("bkId"))
    * Array(BankId("bkId"), BankId("bkId2")) ---> Some(BankId("bkId"))
    *
    * @param obj to extract bankId object
    * @return Some(bankId) or None, type maybe Option[String] or Option[BankId]
    */
  private[this] def getNestedBankId(obj: Any): Option[Any] = {
    val processObj: Option[Any] = obj match {
      case null | None => None
      case _: EmptyBox => None
      case Seq() | Array() => None
      case map: Map[_, _] if(map.isEmpty) => None
      case Seq(head, _*) => Some(head)
      case Array(head, _*) => Some(head)
      case map: Map[_, _] => map.headOption.map(_._2)
      case other => {
        val typeName = other.getClass.getName
        // only obp project defined type will do nested search
        if(typeName.startsWith("code.") || typeName.startsWith("com.openbankproject.commons.")) {
          Some(other)
        } else {
          None
        }
      }
    }

    processObj match {
      case None => None
      case Some(value) => {
        val argNameToValues: Map[String, Any] = getConstructorArgs(value)
        //find from current object constructor args
        // orElse: if current object constructor args not found value, recursive search args
        argNameToValues
          .find(isBankId)
          .map(_._2)
          .orElse {
            argNameToValues.values
              .map(getNestedBankId(_))
              .find(it => it.isDefined)
          }
      }
    }
  }

}
