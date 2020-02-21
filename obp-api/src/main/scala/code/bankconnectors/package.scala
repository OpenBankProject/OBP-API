package code

import java.lang.reflect.Method

import code.api.{APIFailureNewStyle, ApiVersionHolder}
import code.api.util.{CallContext, NewStyle}
import code.bankconnectors.akka.AkkaConnector_vDec2018
import code.bankconnectors.rest.RestConnector_vMar2019
import code.bankconnectors.storedprocedure.StoredProcedureConnector_vDec2019
import code.bankconnectors.vJune2017.KafkaMappedConnector_vJune2017
import code.bankconnectors.vMar2017.KafkaMappedConnector_vMar2017
import code.bankconnectors.vMay2019.KafkaMappedConnector_vMay2019
import code.bankconnectors.vSept2018.KafkaMappedConnector_vSept2018
import code.methodrouting.MethodRouting
import code.util.Helper
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.model.BankId
import com.openbankproject.commons.util.ReflectUtils.{findMethodByArgs, getConstructorArgs}
import com.openbankproject.commons.ExecutionContext.Implicits.global
import net.liftweb.common.{Box, Empty, EmptyBox, Full, ParamFailure}
import net.sf.cglib.proxy.{Enhancer, MethodInterceptor, MethodProxy}

import scala.collection.mutable.ArrayBuffer
import scala.reflect.runtime.universe.{MethodSymbol, Type, typeOf}
import code.api.util.ErrorMessages.InvalidConnectorResponseForMissingRequiredValues
import code.api.util.APIUtil.fullBoxOrException
import com.openbankproject.commons.util.ApiVersion
import com.openbankproject.commons.util.ReflectUtils._
import com.openbankproject.commons.util.Functions.Implicits._

import scala.collection.GenTraversableOnce
import scala.concurrent.Future

package object bankconnectors extends MdcLoggable {

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
        val (objToCall, methodSymbol) =  getConnectorObject(method, args)
        val connectorMethodResult = method.invoke(objToCall, args: _*)
        logger.debug(s"do required field validation for ${methodSymbol.typeSignature}")
        val apiVersion = ApiVersionHolder.getApiVersion
        validateRequiredFields(connectorMethodResult, methodSymbol.returnType, apiVersion)
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
  private[this]def getConnectorObject(method: Method, args: Seq[Any]): (Connector, MethodSymbol) = {
    val methodName = method.getName
    val tpe = typeOf[Connector]
    val methodSymbol: MethodSymbol = findMethodByArgs(tpe, methodName, args:_*).getOrElse(sys.error(s"not found matched method, method name: ${methodName}, params: ${args.mkString(",")}"))
    val paramList = methodSymbol.paramLists.headOption.getOrElse(Nil)
    val paramNameToType: Map[String, Type] = paramList.map(param => (param.name.toString, param.info)).toMap
    val paramNameToValue: Map[String, Any] = paramList.zip(args).map(pair =>(pair._1.name.toString, pair._2)).toMap

    val bankIdInArgs = paramNameToValue.find(isBankId).map(_._2)

    val bankId: Option[String] = bankIdInArgs match {
      case Some(v) => Some(v.toString())
      case None => args.toStream.map(getNestedBankId(_)).find(_.isDefined).flatten.map(_.toString)
    }

    val connectorName: Box[String] = bankId match {
      case None if methodName == "dynamicEntityProcess" => {
        val entityName = args.tail.head
        NewStyle.function.getMethodRoutings(Some(methodName))
          .find(_.parameters.exists(it => it.key == "entityName" && it.value == entityName))
          .map(_.connectorName)
      }
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

    val connector = connectorName.getOrElse("mapped") match {
      case "mapped" => LocalMappedConnector
      case "akka_vDec2018" => AkkaConnector_vDec2018
      case "kafka" => KafkaMappedConnector
      case "kafka_JVMcompatible" => KafkaMappedConnector_JVMcompatible
      case "kafka_vMar2017" => KafkaMappedConnector_vMar2017
      case "kafka_vJune2017" => KafkaMappedConnector_vJune2017
      case "kafka_vSept2018" => KafkaMappedConnector_vSept2018
      case "kafka_vMay2019" => KafkaMappedConnector_vMay2019
      case "rest_vMar2019" => RestConnector_vMar2019
      case "stored_procedure_vDec2019" => StoredProcedureConnector_vDec2019
      case _ => throw new IllegalStateException(s"config of connector.start.methodName.${methodName} have wrong value, not exists connector of name ${connectorName.get}")
    }
    (connector, methodSymbol)
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

  private def validateRequiredFields(value: AnyRef, returnType: Type, apiVersion: ApiVersion): AnyRef = {
    value match {
      case Unit | null => value
      case v @(_: EmptyBox, _: Option[CallContext]) => v
      case n @(_:EmptyBox | None |  Array()) => n
      case n : GenTraversableOnce[_] if n.isEmpty => n

      case coll @(_:Array[_] | _: ArrayBuffer[_] | _: GenTraversableOnce[_]) =>
        val elementTpe = returnType.typeArgs.head
        validate(value, elementTpe, coll, apiVersion, None, false)

      case Full((coll: GenTraversableOnce[_], cc: Option[_]))
        if coll.nonEmpty && returnType <:< typeOf[Box[(_, Option[CallContext])]] =>
        val elementTpe = getNestTypeArg(returnType, 0, 0, 0)
        val callContext = cc.asInstanceOf[Option[CallContext]]
        validate(value, elementTpe, coll, apiVersion, callContext)

      case Full((v, cc: Option[_]))
        if returnType <:< typeOf[Box[(_, Option[CallContext])]] =>
        val elementTpe = getNestTypeArg(returnType, 0, 0)
        val callContext = cc.asInstanceOf[Option[CallContext]]
        validate(value, elementTpe, v, apiVersion, callContext)

      case Full((v1, v2)) =>
        val tpe1 = getNestTypeArg(returnType, 0, 0)
        val tpe2 = getNestTypeArg(returnType, 0, 1)
        validateMultiple(value, apiVersion)(v1 -> tpe1, v2 -> tpe2)

      // return type is: Box[List[(ProductCollectionItem, Product, List[ProductAttribute])]]
      case Full(coll: Traversable[_])
        if coll.nonEmpty &&
          getNestTypeArg(returnType, 0, 0) <:< typeOf[(_, _, GenTraversableOnce[_])] =>
        val tpe1 = getNestTypeArg(returnType, 0, 0, 0)
        val tpe2 = getNestTypeArg(returnType, 0, 0, 1)
        val tpe3 = getNestTypeArg(returnType, 0, 0, 2, 0)
        val collTuple = coll.asInstanceOf[Traversable[(_, _, _)]]
        val v1 = collTuple.map(_._1)
        val v2 = collTuple.map(_._2)
        val v3 = collTuple.map(_._3)
        validateMultiple(value, apiVersion)(v1 -> tpe1, v2 -> tpe2, v3 -> tpe3)

      case Full(coll: GenTraversableOnce[_]) if coll.nonEmpty =>
        val elementTpe = getNestTypeArg(returnType, 0, 0)
        validate(value, elementTpe, coll, apiVersion)

      case Full(v) =>
        val elementTpe = returnType.typeArgs.head
        validate(value, elementTpe, v, apiVersion)

      case (f @Full(v), cc: Option[_])
        if returnType <:< typeOf[(Box[_], Option[CallContext])] || returnType <:< typeOf[Box[_]] => // if returnType is OBPReturnType, returnType is f's type
        val elementTpe = if(returnType <:< typeOf[(Box[_], Option[CallContext])] ) {
          getNestTypeArg(returnType, 0, 0)
        } else {
          returnType.typeArgs.head
        }
        val callContext = cc.asInstanceOf[Option[CallContext]]
        val result = validate(f, elementTpe, v, apiVersion, callContext)
        (result, cc)

      case (v, cc: Option[_])
        if returnType <:< typeOf[(_, Some[CallContext])] || !(returnType <:< typeOf[(_, _)]) => // if returnType is OBPReturnType, returnType is v's type
        val elementTpe = if(returnType <:< typeOf[(_, Some[CallContext])]) {
          returnType.typeArgs.head
        } else {
          returnType
        }
        val callContext = cc.asInstanceOf[Option[CallContext]]
        validate(value, elementTpe, v, apiVersion, callContext, false)

      case future: Future[_]  =>
        val futureType = returnType.typeArgs.head
        future.map(v => validateRequiredFields(v.asInstanceOf[AnyRef], futureType, apiVersion))

      case _ => validate(value, returnType, value, apiVersion, None, false)
    }

  }

  private def validate[T: Manifest](originValue: AnyRef,
                                         validateType: Type,
                                         any: Any,
                                         apiVersion: ApiVersion,
                                         cc: Option[CallContext] = None,
                                         resultIsBox: Boolean = true): AnyRef =
    validateMultiple[T](originValue, apiVersion, cc, resultIsBox)(any -> validateType)


  private def validateMultiple[T: Manifest](originValue: AnyRef,
                                         apiVersion: ApiVersion,
                                         cc: Option[CallContext] = None,
                                         resultIsBox: Boolean = true)(valueAndType: (Any, Type)*): AnyRef = {
    val (lefts, _) = valueAndType
      .map(it => Helper.getRequiredFieldInfo(it._2).validate(it._1, apiVersion))
      .classify(_.isLeft)

    if(lefts.isEmpty) { // all validation passed
      originValue
    } else {
      val missingFields = lefts.flatMap(_.left.get)
      val value = missingFieldsToFailure(missingFields, cc)
      if(resultIsBox) value else fullBoxOrException(value)
    }
  }


  private def missingFieldsToFailure(missingFields: Seq[String], cc: Option[CallContext] = None): ParamFailure[APIFailureNewStyle] = {
    val message = missingFields.map(it => s"data.$it")
                .mkString(s"INTERNAL-$InvalidConnectorResponseForMissingRequiredValues The missing fields: [", ", ", "]")
    logger.error(message)
    ParamFailure(message, Empty, Empty, APIFailureNewStyle(message, 400, cc.map(_.toLight)))
  }
}
