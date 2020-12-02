package code.bankconnectors

import code.api.APIFailureNewStyle
import code.api.util.APIUtil.{OBPEndpoint, _}
import code.api.util.NewStyle.HttpCode
import code.api.util.{APIUtil, ApiRole, CallContext, CustomJsonFormats, NewStyle, OBPQueryParam}
import code.api.v3_1_0.OBPAPI3_1_0.oauthServe
import code.bankconnectors.ConnectorEndpoints.getMethod
import code.bankconnectors.rest.RestConnector_vMar2019
import com.openbankproject.commons.model._
import com.openbankproject.commons.util.ReflectUtils
import com.openbankproject.commons.util.ReflectUtils.{getType, toValueObject}
import net.liftweb.common.{Box, Empty, Failure, Full}
import com.github.dwickern.macros.NameOf.nameOf
import net.liftweb.http.Req
import net.liftweb.http.rest.RestHelper
import net.liftweb.json.JValue
import net.liftweb.json.JsonAST.JNothing
import org.apache.commons.lang3.StringUtils

import scala.annotation.tailrec
import com.openbankproject.commons.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.language.postfixOps
import scala.reflect.ManifestFactory
import scala.reflect.runtime.{universe => ru}

object ConnectorEndpoints extends RestHelper{

  def registerConnectorEndpoints = {
    oauthServe(connectorEndpoints)
  }

  /**
   * extract request body, no matter GET, POST, PUT or DELETE method
   */
  object JsonAny extends JsonTest with JsonBody{
    def unapply(r: Req): Option[(List[String], (JValue, Req))] =
      if (testResponse_?(r))
        body(r).toOption.map(t => (r.path.partPath -> (t -> r)))
      else None
  }

  lazy val connectorEndpoints: OBPEndpoint = {
    case "connector" :: methodName :: Nil JsonAny json -> req if(hashMethod(methodName, json))  => {
      cc => {
        for {
          (Full(user), callContext) <- authenticatedAccess(cc)
          _ <- NewStyle.function.hasEntitlement("", user.userId, ApiRole.canGetConnectorEndpoint, callContext)
          methodSymbol: ru.MethodSymbol = getMethod(methodName, json).get
          outBoundType = Class.forName(s"com.openbankproject.commons.dto.OutBound${methodName.capitalize}")
          mf = ManifestFactory.classType[TopicTrait](outBoundType)
          formats = CustomJsonFormats.nullTolerateFormats
          outBound = json.extract[TopicTrait](formats, mf)
          // TODO need wait for confirm the rule, after that do refactor
          paramValues: Seq[Any] = getParamValues(outBound, methodSymbol, callContext)          
          value = invokeMethod(methodSymbol, paramValues :_*)         
          // convert any to Future[(Box[_], Option[CallContext])]  type
          (boxedData, _) <- toStandardFuture(value)              
          data = APIUtil.fullBoxOrException(boxedData ~> APIFailureNewStyle("", 400, callContext.map(_.toLight)))
          inboundAdapterCallContext = nameOf(InboundAdapterCallContext)
          //convert first letter to small case
          inboundAdapterCallContextKey = StringUtils.uncapitalize(inboundAdapterCallContext)
          inboundAdapterCallContextValue = InboundAdapterCallContext(callContext.map(_.correlationId).getOrElse(""))
        } yield {
          // NOTE: if any field type is BigDecimal, it is can't be serialized by lift json
          val json = Map((inboundAdapterCallContextKey, inboundAdapterCallContextValue),("status", Status("",List(InboundStatusMessage("","","","")))),("data", toValueObject(data)))
          (json, HttpCode.`200`(callContext))
        }
      }
    }
  }

  def extractOBPQueryParams(outBound: AnyRef): Seq[OBPQueryParam] = {
    val tp = ReflectUtils.getType(outBound)
    val decls = tp.decls.toList
    val limit = decls.find(it => it.name.toString == OBPQueryParam.LIMIT).map(_.asMethod).map(ReflectUtils.invokeMethod(outBound,_)).map(_.toString)
    val offset = decls.find(it => it.name.toString == OBPQueryParam.OFFSET).map(_.asMethod).map(ReflectUtils.invokeMethod(outBound,_)).map(_.toString)
    val fromDate = decls.find(it => it.name.toString == OBPQueryParam.FROM_DATE).map(_.asMethod).map(ReflectUtils.invokeMethod(outBound,_)).map(_.asInstanceOf[String])
    val toDate = decls.find(it => it.name.toString == OBPQueryParam.TO_DATE).map(_.asMethod).map(ReflectUtils.invokeMethod(outBound,_)).map(_.asInstanceOf[String])
    List(
      OBPQueryParam.toLimit(limit),
      OBPQueryParam.toOffset(offset),
      OBPQueryParam.toFromDate(fromDate),
      OBPQueryParam.toToDate(toDate)
    ).filter(_.isDefined).map(_.openOrThrowException("Impossible exception!"))
  }

//  def buildInboundObject(adapterCallContext: OutboundAdapterCallContext, data: Any, methodName: String): Any = {
//    val inboundType = getTypeByName(s"com.openbankproject.commons.dto.InBound${methodName.capitalize}")
//    val dataType = inboundType.decl(ru.termNames.CONSTRUCTOR).asMethod.paramLists(0)(1).info
//    val convertedData = toOther[Any](data, dataType)
//    invokeConstructor(inboundType, adapterCallContext, convertedData)
//  }

  def convertValue(str: String, tp: ru.Type): Any = {
    val typeArg = tp.typeArgs.headOption

    tp match {
      case _ if(tp =:= ru.typeOf[String]) => str
      case _ if(StringUtils.isBlank(str)) => null
      case _ if(tp =:= ru.typeOf[Int]) => str.toInt
      case _ if(tp =:= ru.typeOf[BigDecimal]) => BigDecimal(str)
      case _ if(tp =:= ru.typeOf[Boolean]) => "true" equalsIgnoreCase str
      case _ if(tp <:< ru.typeOf[List[_]]) => str.split(";").map(convertValue(_, typeArg.get)).toList
      case _ if(tp <:< ru.typeOf[Set[_]]) => str.split(";").map(convertValue(_, typeArg.get)).toSet
      case _ if(tp <:< ru.typeOf[Array[_]]) => str.split(";").map(convertValue(_, typeArg.get))
        // have single param constructor case class
      case _ if(tp.typeSymbol.asClass.isCaseClass) => {
        val paramList: Seq[ru.Symbol] = tp.decl(ru.termNames.CONSTRUCTOR).asMethod.paramLists.headOption.getOrElse(Nil)
        val params: Seq[Any] = paramList.size match {
          case 1 => Seq(convertValue(str, paramList.head.info))
          case size if(size > 1) => str.split(",", size).zipAll(paramList, null, null).map(it => convertValue(it._1, it._2.info))
          case _ => throw new IllegalStateException(s"constructor must have at lest one parameter, but $tp constructor have no parameter.")
        }
        ReflectUtils.invokeConstructor(tp, params:_*)
      }
      case _ => throw new IllegalAccessException(s"$tp type  is not support in the url, it means Shuang have not supply this type, please contact with Shuang to ask support it")
    }
  }

  def getParamValues(outBound: AnyRef, methodSymbol: ru.MethodSymbol, optionCC: Option[CallContext]): Seq[Any] = {
    RestConnector_vMar2019.convertToId(outBound) // convert reference to customerId and accountId
    val paramNameToValue: Map[String, Any] = ReflectUtils.getConstructorArgs(outBound)

    val paramValues: Seq[Any] = allMethods.find(_._3 == methodSymbol)
      .map(_._4).get
      .map(it => it match {
        case "queryParams" => extractOBPQueryParams(outBound)
        case "callContext" => optionCC
        case name => paramNameToValue(name)
      })

      paramValues
  }
  private lazy val connector: Connector = {
    val connectorName = APIUtil.getPropsValue("connector.name.export.as.endpoints", "mapped")
    Connector.getConnectorInstance(connectorName)
  }
  private val mirror: ru.Mirror = ru.runtimeMirror(getClass().getClassLoader)
  private val mirrorObj: ru.InstanceMirror = mirror.reflect(connector)

  // it is impossible to get the type of OBPQueryParam*, ru.typeOf[OBPQueryParam*] not work, it is Seq type indeed
  private val paramsType = ru.typeOf[Seq[OBPQueryParam]]

  // (methodName, paramNames, method, allParamNames, fn: paramName => isOption)
  lazy val allMethods: List[(String, List[String], ru.MethodSymbol, List[String], String => Boolean)] = {
     val mirror: ru.Mirror = ru.runtimeMirror(this.getClass.getClassLoader)

     val isCallContextOrQueryParams = (tp: ru.Type) => {
       tp <:< ru.typeOf[Option[CallContext]] || tp <:< paramsType
     }
    mirrorObj.symbol.toType.members
       .filter(_.isMethod)
       .map(it => {
         val allParams = it.asMethod.paramLists.headOption.getOrElse(Nil)
         val allNames = allParams.map(_.name.toString.trim)
         // names exclude callContext and queryParams
         val names = allParams
           .filterNot(symbol => isCallContextOrQueryParams(symbol.info))
           .map(_.name.toString.trim)
         val paramNameToIsOption: Map[String, Boolean] = allParams.map(it => (it.name.toString.trim, it.info <:< ru.typeOf[Option[_]])).toMap
         val isParamOption: String => Boolean = name => paramNameToIsOption.get(name).filter(true ==).isDefined
         (it.name.toString, names, it.asMethod, allNames, isParamOption)
       })
      .toList
  }

  def getMethod(methodName: String, json: JValue): Option[ru.MethodSymbol] = {
    this.allMethods.filter { quadruple =>
      val (mName, paramNames, _, _, isParamOption) = quadruple
      mName == methodName && paramNames.forall(paramName => isParamOption(paramName) || (json \ paramName) != JNothing)
    }
      .sortBy(_._2.size)
      .lastOption
      .map(_._3)
  }

  def hashMethod(methodName: String, json: JValue): Boolean = getMethod(methodName, json).isDefined

  def invokeMethod(method: ru.MethodSymbol, args: Any*) = {
    mirrorObj.reflectMethod(method).apply(args :_*)
  }

  @tailrec
  def toStandardFuture(obj: Any): Future[(Box[_], Option[CallContext])] = {
    obj match {
      case null => Future((Empty, None))
      case future: Future[_] => {
        future.map {value =>
          value match {
            case (_, _) => value.asInstanceOf[(Box[_], Option[CallContext])]
            case _ : Box[_] => {
              val boxedValue = value.asInstanceOf[Box[(_, Option[CallContext])]]
              (boxedValue.map(_._1), boxedValue.map(_._2).orElse(None).flatten)
            }
            case _ => throw new IllegalArgumentException(s"not supported type ${getType(value)}")
          }
        }
        .map(RestConnector_vMar2019.convertToReference(_)) //convert customerId and AccountId to reference
      }
      case Full(data) => {
        data match {
          case _: (_, _) => toStandardFuture(Future(obj))
          case _ => {
            val fillCallContext = obj.asInstanceOf[Box[_]].map((_, None))
            toStandardFuture(Future(fillCallContext))
          }
        }
      }
      case failure: Failure => {
        Future((failure.asInstanceOf[Failure], None))
      }
      case Empty => {
        Future((Full(null), None))
      }
      case _ => Future((Full(obj), None))
    }
  }
}
