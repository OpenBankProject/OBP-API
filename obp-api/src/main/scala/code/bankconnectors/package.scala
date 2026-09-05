package code

import java.lang.reflect.{InvocationHandler, Method}
import java.util.regex.Pattern

import org.apache.pekko.http.scaladsl.model.HttpMethod
import code.api.{APIFailureNewStyle, ApiVersionHolder}
import code.api.util.{CallContext, FutureUtil, NewStyle}
import code.api.util.APIUtil.{canOpenFuture, fullBoxOrException, getCorrelationId, getPropsAsBoolValue}
import code.api.util.ErrorMessages.{InvalidConnectorResponseForMissingRequiredValues, ServiceIsTooBusy}
import code.methodrouting.{MethodRouting, MethodRoutingT}
import code.metrics.{ConnectorTraceProvider, ConnectorMetricsProvider, ConnectorCountsRedis}
import code.util.Helper
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.model.{AccountId, BankId}
import com.openbankproject.commons.util.ReflectUtils.{findMethodByArgs, getConstructorArgs}
import com.openbankproject.commons.ExecutionContext.Implicits.global
import net.liftweb.common.{Box, Empty, EmptyBox, Failure, Full, ParamFailure}
import net.liftweb.util.Helpers.now
import net.liftweb.util.ThreadGlobal

import scala.concurrent.Future
import scala.reflect.runtime.universe.{MethodSymbol, Type, WildcardType, appliedType, typeOf}
import scala.util.{Try, Success => TrySuccess, Failure => TryFailure}
import com.openbankproject.commons.util.{ApiVersion, ReflectUtils}
import com.openbankproject.commons.util.ReflectUtils._
import com.openbankproject.commons.util.Functions.Implicits._

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

    // Record the outcome of a connector call: counters, plus optional detailed metric/trace persistence.
    def recordConnectorInboundMetrics(connectorName: String, methodName: String, correlationId: String,
                                       duration: Long, isSuccess: Boolean, args: Array[AnyRef]): Unit = {
      ConnectorCountsRedis.incrementInbound(connectorName, methodName, isSuccess)
      if (getPropsAsBoolValue("write_connector_metrics", false)) {
        val params = extractKeyParams(args)
        Future {
          ConnectorMetricsProvider.metrics.vend.saveConnectorMetric(
            connectorName, methodName, correlationId, now, duration, params, isSuccess)
        }
      }
    }

    // correlationId is passed in, not re-derived: APIUtil.getCorrelationId() reads Lift's
    // container session and, since the Lift teardown, is a stub returning "". Calling it here
    // wrote every connectortrace row with an empty correlation id while the matching
    // connectormetric row (which is handed the id the caller extracted) carried the real one --
    // so traces could neither be looked up by OBPCorrelationId nor joined to their metric.
    def recordConnectorTrace(connectorName: String, methodName: String, correlationId: String,
                              method: Method, args: Array[AnyRef],
                              duration: Long, isSuccess: Boolean, result: Try[Any]): Unit = {
      if (getPropsAsBoolValue("write_connector_trace", false)) {
        val outbound = serializeOutboundArgs(method, args)
        val inbound = serializeInboundResult(result)
        val (detailUserId, detailHttpVerb, detailApiUrl) = extractCallContextInfo(args)
        val bankIdValue = extractBankIdFromArgs(args)
        Future {
          ConnectorTraceProvider.saveConnectorTrace(
            correlationId, connectorName, methodName, bankIdValue,
            outbound, inbound, now, duration, isSuccess,
            detailUserId, detailHttpVerb, detailApiUrl)
        }
      }
    }

    // The empty Connector implements both: the $default$ accessors it inherits, and the
    // members Connector itself does not declare. Routing the latter would look them up as
    // connector calls - and NPE on the way, since args is null for a no-arg method.
    def delegateToStub(method: Method, args: Array[AnyRef]): AnyRef = {
      val connectorMethodResult = method.invoke(StubConnector, args:_*)
      if (connectorMethodResult.isInstanceOf[Future[_]] && canOpenFuture(method.getName)) {
        FutureUtil.futureWithLimits(connectorMethodResult.asInstanceOf[Future[_]], method.getName)
      }
      connectorMethodResult
    }

    def routeToConnector(method: Method, args: Array[AnyRef]): AnyRef = {
      val methodName = method.getName
      val argNameToValue: Array[(String, AnyRef)] = method.getParameters.map(_.getName).zip(args)
      // TODO: getConnectorNameAndMethodRouting is also called inside invokeMethod.
      // Consider refactoring invokeMethod to accept a pre-resolved connectorName to avoid the duplicate lookup.
      val (_, connectorName) = getConnectorNameAndMethodRouting(methodName, argNameToValue)

      // Extract correlationId from CallContext before entering any Future callback,
      // because Lift's S.containerSession is unavailable in async contexts.
      val correlationId: String = args.collectFirst {
        case Some(cc: CallContext) => cc.correlationId
        case Full(cc: CallContext) => cc.correlationId
      }.getOrElse(getCorrelationId()) // fallback to Lift session if no CallContext in args

      // Record outbound (before call)
      ConnectorCountsRedis.incrementOutbound(connectorName, methodName)
      val t0 = System.currentTimeMillis()

      val (connectorMethodResult, methodSymbol) = invokeMethod(method, args)

      // Track metrics for Future results
      if (connectorMethodResult.isInstanceOf[Future[_]]) {
        val future = connectorMethodResult.asInstanceOf[Future[Any]]
        future.onComplete { result =>
          val duration = System.currentTimeMillis() - t0
          val isSuccess = result match {
            case TrySuccess(value) => !isFailureBox(value)
            case TryFailure(_) => false
          }
          recordConnectorInboundMetrics(connectorName, methodName, correlationId, duration, isSuccess, args)
          recordConnectorTrace(connectorName, methodName, correlationId, method, args, duration, isSuccess, result)
        }
      } else {
        // Non-future (legacy Box) result - track synchronously
        val duration = System.currentTimeMillis() - t0
        val isSuccess = !isFailureBox(connectorMethodResult)
        recordConnectorInboundMetrics(connectorName, methodName, correlationId, duration, isSuccess, args)
        recordConnectorTrace(connectorName, methodName, correlationId, method, args, duration, isSuccess, TrySuccess(connectorMethodResult))
      }

      if (connectorMethodResult.isInstanceOf[Future[_]] && canOpenFuture(method.getName)) {
        FutureUtil.futureWithLimits(connectorMethodResult.asInstanceOf[Future[_]], method.getName)
      }
      logger.debug(s"do required field validation for ${methodSymbol.typeSignature}")
      val apiVersion = ApiVersionHolder.getApiVersion
      validateRequiredFields(connectorMethodResult, methodSymbol.returnType, apiVersion)
    }

    val intercept: InvocationHandler = new InvocationHandler {
      override def invoke(proxy: AnyRef, method: Method, rawArgs: Array[AnyRef]): AnyRef = {
        // `java.lang.reflect.Proxy` passes null for a method that declares no parameters; cglib,
        // which this replaced, passed a zero-length array. Everything downstream treats args as a
        // collection -- `.zip(args)`, `args.collectFirst`, `extractKeyParams(args)` -- and every
        // one of those throws on null.
        //
        // isInheritedMember covers the members Connector does not declare, but a NO-ARGUMENT
        // method that Connector DOES declare slips past it and lands in routeToConnector.
        // Measured on GET /obp/v6.0.0/system/connector-method-names, which reads
        // `connector.callableMethods`: 200 on the 2.12/cglib build, 500 on this one, with
        // `Cannot invoke "scala.collection.IterableOnce.knownSize()" because "that" is null` --
        // which is `zip` being handed the null.
        //
        // Normalising to an empty array restores exactly what cglib did, which is what a
        // toolchain migration owes its callers. `method.invoke(target, args: _*)` is unaffected:
        // it compiles to Java varargs and an empty array means the same as null there.
        val args: Array[AnyRef] = if (rawArgs == null) Array.empty[AnyRef] else rawArgs
        if (method.getReturnType.getName == "scala.concurrent.Future" && !canOpenFuture(method.getName)) {
          throw new RuntimeException(ServiceIsTooBusy + s"Current Service(${method.getName})")
        } else if (method.getName.contains("$default$") || ConnectorProxy.isInheritedMember(method)) {
          delegateToStub(method, args)
        } else {
          routeToConnector(method, args)
        }
      }
    }
    ConnectorProxy.create(intercept)
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
    val methodSymbol = connector.callableMethods.get(methodName).map(_.alternatives) match {
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

  // These mix net.liftweb.common.Box / stdlib tuples with code.api.util.CallContext, an obp-api-only
  // type - so, unlike SwaggerTypes, they can't be precomputed in obp-commons (wrong dependency
  // direction). typeOf[T] for a parameterized type needs the Scala 2 compiler's TypeTag synthesis,
  // which Scala 3 does not implement, so these are built at runtime instead via
  // ReflectUtils.forType + appliedType (WildcardType stands in for `_`), same technique as
  // ConnectorUtils.scala/ConnectorEndpoints.scala.
  private val boxTycon = ReflectUtils.forType("net.liftweb.common.Box").typeConstructor
  private val tuple2Tycon = ReflectUtils.forType("scala.Tuple2").typeConstructor
  private val tuple3Tycon = ReflectUtils.forType("scala.Tuple3").typeConstructor
  private val optionTycon = ReflectUtils.forType("scala.Option").typeConstructor
  private val someTycon = ReflectUtils.forType("scala.Some").typeConstructor
  private val iterableTycon = ReflectUtils.forType("scala.collection.Iterable").typeConstructor
  private val callContextType = ReflectUtils.forType("code.api.util.CallContext")
  private val optionCallContextType = appliedType(optionTycon, callContextType)
  private val someCallContextType = appliedType(someTycon, callContextType)

  // Box[(_, Option[CallContext])]
  private val boxTupleWildcardOptionCallContextType =
    appliedType(boxTycon, appliedType(tuple2Tycon, WildcardType, optionCallContextType))
  // (_, _, Iterable[_])
  private val tuple3WildcardWildcardIterableWildcardType =
    appliedType(tuple3Tycon, WildcardType, WildcardType, appliedType(iterableTycon, WildcardType))
  // (Box[_], Option[CallContext])
  private val tupleBoxWildcardOptionCallContextType =
    appliedType(tuple2Tycon, appliedType(boxTycon, WildcardType), optionCallContextType)
  // Box[_]
  private val boxWildcardType = appliedType(boxTycon, WildcardType)
  // (_, Some[CallContext])
  private val tupleWildcardSomeCallContextType = appliedType(tuple2Tycon, WildcardType, someCallContextType)
  // (_, _)
  private val tupleWildcardWildcardType = appliedType(tuple2Tycon, WildcardType, WildcardType)

  private def validateRequiredFields(value: AnyRef, returnType: Type, apiVersion: ApiVersion): AnyRef = {
    value match {
      // when method return one of Unit, null, EmptyBox, None, empty Array, empty collection,
      // don't validate fields.
      // BoxedUnit, not `Unit`: the old spelling matched the Unit companion object, which
      // reflectively invoking a Unit-returning connector method never produces, so this arm
      // never fired. () cannot be matched against an AnyRef scrutinee, and the boxed value is
      // what actually arrives here.
      case _: scala.runtime.BoxedUnit | null => value
      case v @(_: EmptyBox, Some(_:CallContext) | None) => v
      case n @(_:EmptyBox | None |  Array()) => n
      case n : Iterable[_] if n.isEmpty => n

      // all the follow return value need do validation of requied fields.
      // ArrayBuffer used to be listed here beside GenTraversableOnce; it is an Iterable, so it is
      // covered by the arm below and naming it separately only read as a deliberate special case.
      case coll @(_:Array[_] | _: Iterable[_]) =>
        val elementTpe = returnType.typeArgs.head
        validate(value, elementTpe, coll, apiVersion, None, false)

      case Full((coll: Iterable[_], cc: Option[_]))
        if coll.nonEmpty && returnType <:< boxTupleWildcardOptionCallContextType =>
        val elementTpe = getNestTypeArg(returnType, 0, 0, 0)
        val callContext = cc.asInstanceOf[Option[CallContext]]
        validate(value, elementTpe, coll, apiVersion, callContext)

      case Full((v, cc: Option[_]))
        if returnType <:< boxTupleWildcardOptionCallContextType =>
        val elementTpe = getNestTypeArg(returnType, 0, 0)
        val callContext = cc.asInstanceOf[Option[CallContext]]
        validate(value, elementTpe, v, apiVersion, callContext)

      case Full((v1, v2)) =>
        val tpe1 = getNestTypeArg(returnType, 0, 0)
        val tpe2 = getNestTypeArg(returnType, 0, 1)
        validateMultiple(value, apiVersion)(v1 -> tpe1, v2 -> tpe2)

      // return type is: Box[List[(ProductCollectionItem, Product, List[ProductAttribute])]]
      case Full(coll: Iterable[_])
        if coll.nonEmpty &&
          getNestTypeArg(returnType, 0, 0) <:< tuple3WildcardWildcardIterableWildcardType =>
        val tpe1 = getNestTypeArg(returnType, 0, 0, 0)
        val tpe2 = getNestTypeArg(returnType, 0, 0, 1)
        val tpe3 = getNestTypeArg(returnType, 0, 0, 2, 0)
        val collTuple = coll.asInstanceOf[Iterable[(_, _, _)]]
        val v1 = collTuple.map(_._1)
        val v2 = collTuple.map(_._2)
        val v3 = collTuple.map(_._3)
        validateMultiple(value, apiVersion)(v1 -> tpe1, v2 -> tpe2, v3 -> tpe3)

      case Full(coll: Iterable[_]) if coll.nonEmpty =>
        val elementTpe = getNestTypeArg(returnType, 0, 0)
        validate(value, elementTpe, coll, apiVersion)

      case Full(v) =>
        val elementTpe = returnType.typeArgs.head
        validate(value, elementTpe, v, apiVersion)

      // if returnType is OBPReturnType, returnType is f's type, So need check returnType <:< typeOf[Box[_]]
      case (f @Full(v), cc: Option[_])
        if returnType <:< tupleBoxWildcardOptionCallContextType || returnType <:< boxWildcardType =>
        val elementTpe = if(returnType <:< tupleBoxWildcardOptionCallContextType) {
          getNestTypeArg(returnType, 0, 0)
        } else {
          returnType.typeArgs.head
        }
        val callContext = cc.asInstanceOf[Option[CallContext]]
        val result = validate(f, elementTpe, v, apiVersion, callContext)
        (result, cc)

      // if returnType is OBPReturnType, returnType is v's type, So need check !(returnType <:< typeOf[(_, _)])
      case (v, cc: Option[_])
        if returnType <:< tupleWildcardSomeCallContextType || !(returnType <:< tupleWildcardWildcardType) =>
        val elementTpe = if(returnType <:< tupleWildcardSomeCallContextType) {
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

  // Neither method ever used its T - a call-site type argument was never supplied anywhere in the
  // codebase, so it was always inferred, and with nothing in either signature constraining it,
  // inference had nothing to pin it to. Scala 2 quietly resolved that to Nothing and moved on;
  // Scala 3 refuses to synthesise a Manifest[Nothing] for an unconstrained inference and hard
  // errors instead. Dropping the dead parameter removes the inference rather than fixing what it
  // resolved to.
  private def validate(originValue: AnyRef,
                                         validateType: Type,
                                         any: Any,
                                         apiVersion: ApiVersion,
                                         cc: Option[CallContext] = None,
                                         resultIsBox: Boolean = true): AnyRef =
    validateMultiple(originValue, apiVersion, cc, resultIsBox)(any -> validateType)


  private def validateMultiple(originValue: AnyRef,
                                         apiVersion: ApiVersion,
                                         cc: Option[CallContext] = None,
                                         resultIsBox: Boolean = true)(valueAndType: (Any, Type)*): AnyRef = {
    val (lefts, _) = valueAndType
      .map(it => Helper.getRequiredFieldInfo(it._2).validate(it._1, apiVersion))
      .classify(_.isLeft)

    if(lefts.isEmpty) { // all validation passed
      originValue
    } else {
      val missingFields = lefts.collect { case Left(fields) => fields }.flatten
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

  /**
   * Extract key parameters (bankId, accountId) from connector method args as a compact JSON string.
   * Max 1024 characters to fit in the DB field.
   */
  private def extractKeyParams(args: Array[AnyRef]): String = {
    try {
      val params = scala.collection.mutable.Map[String, String]()
      args.foreach {
        case bankId: BankId => params("bankId") = bankId.value
        case accountId: AccountId => params("accountId") = accountId.value
        case _ => // skip other types
      }
      if (params.isEmpty) ""
      else {
        val json = params.map { case (k, v) => s""""$k":"$v"""" }.mkString("{", ",", "}")
        if (json.length > 1024) json.substring(0, 1024) else json
      }
    } catch {
      case _: Throwable => ""
    }
  }

  /**
   * Serialize method arguments to a JSON string for connector trace.
   * Filters out CallContext to avoid capturing session data.
   */
  private def serializeOutboundArgs(method: Method, args: Array[AnyRef]): String = {
    try {
      val paramNames = method.getParameters.map(_.getName)
      val filtered = paramNames.zip(args).filterNot {
        case (_, v) => v.isInstanceOf[Option[_]] && v.asInstanceOf[Option[_]].exists(_.isInstanceOf[CallContext])
        case _ => false
      }.filterNot {
        case (_, v) => v.isInstanceOf[CallContext]
        case _ => false
      }
      filtered.map { case (name, value) =>
        s"$name=${Option(value).map(_.toString).getOrElse("null")}"
      }.mkString(", ")
    } catch {
      case e: Throwable => s"Failed to serialize args: ${e.getMessage}"
    }
  }

  /**
   * Serialize the result of a connector call for connector trace.
   * Extracts data from Box[(T, Option[CallContext])] tuples, filtering out CallContext.
   */
  private def serializeInboundResult(result: scala.util.Try[Any]): String = {
    try {
      val value = result match {
        case TrySuccess(Full((data, Some(_: CallContext)))) => data
        case TrySuccess(Full((data, None))) => data
        case TrySuccess(Full(data)) => data
        case TrySuccess((data, Some(_: CallContext))) => data
        case TrySuccess((data, None)) => data
        case TrySuccess(data) => data
        case TryFailure(e) => s"Exception: ${e.getMessage}"
      }
      Option(value).map(_.toString).getOrElse("null")
    } catch {
      case e: Throwable => s"Failed to serialize result: ${e.getMessage}"
    }
  }

  /**
   * Extract userId, httpVerb, and url from the CallContext found in method args.
   */
  private def extractCallContextInfo(args: Array[AnyRef]): (String, String, String) = {
    try {
      val cc: Option[CallContext] = args.collectFirst {
        case Some(cc: CallContext) => cc
        case Full(cc: CallContext) => cc
      }
      cc match {
        case Some(callContext) =>
          val userId = callContext.user.map(_.userId).getOrElse("")
          val httpVerb = callContext.verb
          val apiUrl = callContext.url
          (userId, httpVerb, apiUrl)
        case None => ("", "", "")
      }
    } catch {
      case _: Throwable => ("", "", "")
    }
  }

  /**
   * Extract bankId from method args for connector trace.
   */
  private def extractBankIdFromArgs(args: Array[AnyRef]): String = {
    try {
      args.collectFirst {
        case bankId: BankId => bankId.value
      }.getOrElse {
        // Try nested search
        args.toStream.map(findBankIdIn(_)).find(_.isDefined).flatten.getOrElse("")
      }
    } catch {
      case _: Throwable => ""
    }
  }

  /**
   * Check if a connector result value represents a failure (Failure or Empty Box).
   */
  private def isFailureBox(value: Any): Boolean = value match {
    case _: EmptyBox => true
    case (_: EmptyBox, _) => true
    case _ => false
  }
}
