package code

import java.lang.reflect.Method
import java.util.regex.Pattern

import akka.http.scaladsl.model.HttpMethod
import code.api.{APIFailureNewStyle, ApiVersionHolder}
import code.api.util.{CallContext, NewStyle}
import code.methodrouting.{MethodRouting, MethodRoutingT}
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
import com.openbankproject.commons.util.{ApiVersion, ReflectUtils}
import com.openbankproject.commons.util.ReflectUtils._
import com.openbankproject.commons.util.Functions.Implicits._
import net.liftweb.util.ThreadGlobal

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
      if (method.getName.contains("$default$")) {
          method.invoke(StubConnector, args:_*)
      } else {
        val (connectorMethodResult, methodSymbol) = invokeMethod(method, args)
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
   * if the connector method invoked according MethodRouting, then pass it to connector
   */
  object MethodRoutingHolder {
    private val _routing = new ThreadGlobal[MethodRoutingT]

    def init[B](boxRouting: Box[MethodRoutingT])(f: => B): B = {
      _routing.doWith(boxRouting.orNull) {
        f
      }
    }

    def methodRouting: Box[MethodRoutingT] = _routing.box
  }
  /**
    * according invoked method and arguments value to invoke connector method
    * @param method invoked method
    * @param args passed arguments
    * @return connector method return value to method info
    */
  private[this]def invokeMethod(method: Method, args: Array[AnyRef]): (AnyRef, MethodSymbol) = {
    val methodName = method.getName
    val argNameToValue: Array[(String, AnyRef)] = method.getParameters.map(_.getName).zip(args)

    val (methodRouting: Box[MethodRoutingT], connectorName: String) = getConnectorNameAndMethodRouting(methodName, argNameToValue)

    val connector = connectorName match {
      case "star" => throw new IllegalStateException(s"Props of connector.start.methodName.$methodName, value should not be 'star'")
      case name => Connector.getConnectorInstance(name)
    }
    val methodSymbol = connector.implementedMethods.get(methodName).map(_.alternatives) match {
      case Some(m::Nil) if m.isMethod => m.asMethod
      case _ =>
        findMethodByArgs(connector, methodName, args:_*)
        .getOrElse(sys.error(s"not found matched method, method name: ${methodName}, params: ${args.mkString(",")}"))
    }

    MethodRoutingHolder.init(methodRouting){
      (method.invoke(connector, args: _*), methodSymbol)
    }
  }

  /**
   * according connector method name, bankId and call parameters to find connector name and MethodRouting
   * @param methodName connector method name
   * @param argNameToValue connector method parameterName -> parameterValue
   * @return connector name and methodRouting instance
   */
  def getConnectorNameAndMethodRouting(methodName: String, argNameToValue: Array[(String, AnyRef)]): (Box[MethodRoutingT], String) = {
    val args = argNameToValue.map(_._2)

    var bankId: Option[String] = argNameToValue collectFirst {
      case BankIdExtractor(v) => v
    }
    if(bankId.isEmpty) {
      bankId = args.toStream.map(findBankIdIn(_)).find(_.isDefined).flatten
    }

    val methodRouting: Box[MethodRoutingT] = bankId match {
      case None if methodName == "dynamicEntityProcess" => {
        val entityName = args.tail.head
        NewStyle.function.getMethodRoutings(Some(methodName))
          .find(_.parameters.exists(it => it.key == "entityName" && it.value == entityName))
      }
      case _ if methodName == "dynamicEndpointProcess" => {
        val Array(url: String, _, method: HttpMethod, _*) = args
        NewStyle.function.getMethodRoutings(Some(methodName))
          .find(routing => {
            routing.parameters.exists(it => it.key == "http_method" && it.value.equalsIgnoreCase(method.value)) &&
              routing.parameters.exists(it => it.key == "url") &&
              routing.parameters.exists(
                it => {
                  val value = it.value
                  it.key == "url_pattern" && // url_pattern is equals with current target url to remote server or as regex match
                    (value == url || {
                      val regexStr = value.replaceAll("""\{[^/]+?\}""", "[^/]+?")
                      Pattern.compile(regexStr).matcher(url).matches()
                    })
                }
              )
          })
      }
      case None => NewStyle.function.getMethodRoutings(Some(methodName), Some(false))
        .find { routing =>
          val bankIdPattern = routing.bankIdPattern
          bankIdPattern.isEmpty || bankIdPattern.get == MethodRouting.bankIdPatternMatchAny
        }
      // found bankId in method args, so query connectorName with bankId
      case Some(bankId) => {
        //if methodName and bankId do exact match query no result, do query with methodName, and use bankId do match with bankIdPattern
        NewStyle.function.getMethodRoutings(Some(methodName), Some(true), Some(bankId)).headOption
          .orElse {
            NewStyle.function.getMethodRoutings(Some(methodName), Some(false))
              .filter { methodRouting =>
                methodRouting.bankIdPattern.isEmpty || bankId.matches(methodRouting.bankIdPattern.get)
              }
              .sortBy(_.bankIdPattern) // if there are both matched bankIdPattern and null bankIdPattern, the have value bankIdPattern success
              .lastOption
          }
      }
    }
    val connectorName: String = methodRouting.map(_.connectorName).getOrElse("mapped")
    (methodRouting, connectorName)
  }

  private[this] object BankIdExtractor {
    /**
     * according valueName and value to find bankId, it can be String type or BankId type
     * @return String type bankId
     */
    def unapply(nameAndValue: (String, Any)): Option[String] = nameAndValue match {
      case ("bankId", null | None) => None
      case ("bankId", _: EmptyBox) => None
      case ("bankId", v: String) => Some(v)
      case ("bankId", Some(v: String))  => Some(v)
      case ("bankId", Full(v: String))  => Some(v)
      case(name, v) if name.endsWith("BankId") => unapply("bankId" -> v)

      case (_, v: BankId)  => Some(v.toString)
      case (_, Some(v: BankId))  => Some(v.toString)
      case (_, Full(v: BankId))  => Some(v.toString)
      case _ => None
    }
  }
  /**
    * find bankId value in the object, nested bankId value will be searched, For example:
    * BankAccount(BankId("bkId"), AccountId("aId")) ---> Some(BankId("bkId"))
    * List(BankId("bkId"), BankId("bkId2")) ---> Some(BankId("bkId"))
    * Array(BankId("bkId"), BankId("bkId2")) ---> Some(BankId("bkId"))
    *
    * @param obj to extract bankId object
    * @return Some(bankId) or None, type maybe Option[String] or Option[BankId]
    */
  private[this] def findBankIdIn(obj: Any): Option[String] = {
    val processObj: Option[Any] = obj match {
      case null | None => None
      case _: EmptyBox => None
      case Seq() | Array() => None
      case map: Map[_, _] if map.isEmpty => None
      case Seq(head, _*) => Some(head)
      case Array(head, _*) => Some(head)
      case map: Map[_, _] => map.headOption.map(_._2)
      case other => {
        // only obp project defined type will do nested search
        if (ReflectUtils.isObpObject(other)) {
          Some(other)
        } else {
          None
        }
      }
    }

    processObj match {
      case Some(bankId: BankId) => Some(bankId.value)
      case Some(value) if ReflectUtils.isObpObject(value) => {
        val argNameToValues: Map[String, Any] = getConstructorArgs(value)
        //find from current object constructor args
        // orElse: if current object constructor args not found value, recursive search args
        var bankIdOption: Option[String] = argNameToValues collectFirst {
          case BankIdExtractor(v) => v
        }
        if(bankIdOption.isEmpty) {
          val argValues = argNameToValues.values
          bankIdOption = argValues.toStream.map(findBankIdIn(_)).find(_.isDefined).map(_.get)
        }
        bankIdOption
      }

      case _ => None
    }
  }

  private def validateRequiredFields(value: AnyRef, returnType: Type, apiVersion: ApiVersion): AnyRef = {
    value match {
      // when method return one of Unit, null, EmptyBox, None, empty Array, empty collection,
      // don't validate fields.
      case Unit | null => value
      case v @(_: EmptyBox, Some(_:CallContext) | None) => v
      case n @(_:EmptyBox | None |  Array()) => n
      case n : GenTraversableOnce[_] if n.isEmpty => n

      // all the follow return value need do validation of requied fields.
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

      // if returnType is OBPReturnType, returnType is f's type, So need check returnType <:< typeOf[Box[_]]
      case (f @Full(v), cc: Option[_])
        if returnType <:< typeOf[(Box[_], Option[CallContext])] || returnType <:< typeOf[Box[_]] =>
        val elementTpe = if(returnType <:< typeOf[(Box[_], Option[CallContext])] ) {
          getNestTypeArg(returnType, 0, 0)
        } else {
          returnType.typeArgs.head
        }
        val callContext = cc.asInstanceOf[Option[CallContext]]
        val result = validate(f, elementTpe, v, apiVersion, callContext)
        (result, cc)

      // if returnType is OBPReturnType, returnType is v's type, So need check !(returnType <:< typeOf[(_, _)])
      case (v, cc: Option[_])
        if returnType <:< typeOf[(_, Some[CallContext])] || !(returnType <:< typeOf[(_, _)]) =>
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
