package code.bankconnectors

import code.api.APIFailureNewStyle
import code.api.util.APIUtil.{OBPEndpoint, _}
import code.api.util.NewStyle.HttpCode
import code.api.util.{APIUtil, CallContext, CustomJsonFormats, OBPQueryParam}
import code.api.v3_1_0.OBPAPI3_1_0.oauthServe
import com.openbankproject.commons.model._
import com.openbankproject.commons.util.ReflectUtils
import com.openbankproject.commons.util.ReflectUtils.{getType, toValueObject}
import net.liftweb.common.{Box, Empty, Failure, Full}
import com.github.dwickern.macros.NameOf.nameOf
import net.liftweb.http.rest.RestHelper
import net.liftweb.json.JValue
import net.liftweb.json.JsonAST.JNothing
import org.apache.commons.lang3.StringUtils

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.language.postfixOps
import scala.reflect.ManifestFactory
import scala.reflect.runtime.{universe => ru}

object ConnectorEndpoints extends RestHelper{

  def registerConnectorEndpoints = {
    oauthServe(connectorGetMethod)
  }

  lazy val connectorGetMethod: OBPEndpoint = {
    case "restConnector" :: methodName :: Nil JsonPost json -> _  if(hashMethod(methodName, json)) => {
      cc => {
        val methodSymbol = getMethod(methodName, json).get
        val outBoundType = Class.forName(s"com.openbankproject.commons.dto.OutBound${methodName.capitalize}")
        val mf = ManifestFactory.classType[TopicTrait](outBoundType)
        val formats = CustomJsonFormats.formats
        val outBound = json.extract(formats, mf)
        val optionCC = Option(cc)
        val queryParams: Seq[OBPQueryParam] = extractOBPQueryParams(outBound)

        // TODO need wait for confirm the rule, after that do refactor
        val paramValues: Seq[Any] = getParamValues(outBound, methodSymbol.paramLists.headOption.getOrElse(Nil), optionCC, queryParams)

        val  value = invokeMethod(methodSymbol, paramValues :_*)

        // convert any to Future[(Box[_], Option[CallContext])]  type
        val futureValue: Future[(Box[_], Option[CallContext])] = toStandaredFuture(value)

        for {
          (Full(data), callContext) <- futureValue.map {it =>
            APIUtil.fullBoxOrException(it._1 ~> APIFailureNewStyle("", 400, optionCC.map(_.toLight)))
            it
          }
          inboundAdapterCallContext = nameOf(InboundAdapterCallContext)
          //convert first letter to small case
          inboundAdapterCallContextKey = Character.toLowerCase(inboundAdapterCallContext.charAt(0)) + inboundAdapterCallContext.substring(1)
          inboundAdapterCallContextValue = InboundAdapterCallContext(cc.correlationId)
        } yield {
          // NOTE: if any filed type is BigDecimal, it is can't be serialized by lift json
          val json = Map((inboundAdapterCallContextKey, inboundAdapterCallContextValue),("status", Status("",List(InboundStatusMessage("","","","")))),("data", toValueObject(data)))
          (json, HttpCode.`200`(cc))
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

  def getParamValues(outBound: AnyRef, symbols: List[ru.Symbol], optionCC: Option[CallContext], queryParams: Seq[OBPQueryParam]): Seq[Any] = {
    val paramNameToValue: Map[String, Any] = ReflectUtils.getConstructorArgs(outBound)

    val queryParamValues: Seq[OBPQueryParam] = symbols.lastOption.find(_.info <:< paramsType).map(_ => queryParams).getOrElse(Nil)

    val otherValues: List[Any] = symbols
      .map {symbol =>
        symbol.name.toString match {
          case "callContext" => optionCC
          case name => paramNameToValue(name)
        }
      }.filterNot(_.isInstanceOf[Seq[OBPQueryParam]])
    otherValues :+ queryParamValues
  }

  private val mirror: ru.Mirror = ru.runtimeMirror(getClass().getClassLoader)
  private val mirrorObj: ru.InstanceMirror = mirror.reflect(LocalMappedConnector)

  // it is impossible to get the type of OBPQueryParam*, ru.typeOf[OBPQueryParam*] not work, it is Seq type indeed
  private val paramsType = ru.typeOf[Seq[OBPQueryParam]]

  // (methodName, paramNames, method)
  lazy val allMethods: List[(String, List[String], ru.MethodSymbol)] = {
     val mirror: ru.Mirror = ru.runtimeMirror(this.getClass.getClassLoader)
     val objMirror = mirror.reflect(LocalMappedConnector)

     val isCallContextOrQueryParams = (tp: ru.Type) => {
       tp <:< ru.typeOf[Option[CallContext]] || tp <:< paramsType
     }
     objMirror.symbol.toType.members
       .filter(_.isMethod)
       .map(it => {
         val names = it.asMethod.paramLists.headOption.getOrElse(Nil)
           .filterNot(symbol => isCallContextOrQueryParams(symbol.info))
           .map(_.name.toString)
         (it.name.toString, names, it.asMethod)
       })
      .toList
  }

  def getMethod(methodName: String, json: JValue): Option[ru.MethodSymbol] = {
    this.allMethods.filter { triple =>
      triple._1 == methodName && triple._2.forall(paramName => (json \ paramName) != JNothing)
    }
    .sortBy(_._2.size)
    .lastOption
    .map(_._3)
  }

  def hashMethod(methodName: String, json: JValue): Boolean = getMethod(methodName, json).isDefined

  def invokeMethod(method: ru.MethodSymbol, args: Any*) = {
    mirrorObj.reflectMethod(method).apply(args :_*)
  }

  def toStandaredFuture(obj: Any): Future[(Box[_], Option[CallContext])] = {
    obj match {
      case null => Future((Empty, None))
      case _: Future[_] => {
        obj.asInstanceOf[Future[_]].map {value =>
          value match {
            case (_, _) => value.asInstanceOf[(Box[_], Option[CallContext])]
            case _ : Box[_] => {
              val boxedValue = value.asInstanceOf[Box[(_, Option[CallContext])]]
              (boxedValue.map(_._1), boxedValue.map(_._2).orElse(None).flatten)
            }
            case _ => throw new IllegalArgumentException(s"not supported type ${getType(value)}")
          }
        }
      }
      case Full(data) => {
        data match {
          case _: (_, _) => toStandaredFuture(Future(obj))
          case _ => {
            val fillCallContext = obj.asInstanceOf[Box[_]].map((_, None))
            toStandaredFuture(Future(fillCallContext))
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
