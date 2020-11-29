package code.api.util

import code.api.util.APIUtil.{BooleanBody, DoubleBody, EmptyBody, JArrayBody, LongBody, PrimaryDataBody, StringBody}
import code.api.util.ApiRole.rolesMappedToClasses
import com.openbankproject.commons.dto.InBoundTrait
import com.openbankproject.commons.model.TopicTrait
import com.openbankproject.commons.util.Functions.Memo
import com.openbankproject.commons.util.{JsonUtils, _}
import net.liftweb.json
import net.liftweb.json.JsonAST.JValue
import net.liftweb.json.{TypeInfo, _}
import org.apache.commons.lang3.StringUtils

import scala.collection.immutable.List
import scala.reflect.runtime.universe
import scala.reflect.runtime.universe._

trait CustomJsonFormats {
  implicit val formats: Formats = CustomJsonFormats.formats
}

object CustomJsonFormats {

  val formats: Formats = JsonSerializers.commonFormats + JsonAbleSerializer

  val losslessFormats: Formats =  net.liftweb.json.DefaultFormats.lossless ++ JsonSerializers.serializers

  val emptyHintFormats = DefaultFormats.withHints(ShortTypeHints(List())) ++ JsonSerializers.serializers

  implicit val nullTolerateFormats = JsonSerializers.nullTolerateFormats

  lazy val rolesMappedToClassesFormats: Formats = new Formats {
    val dateFormat = net.liftweb.json.DefaultFormats.dateFormat

    override val typeHints = ShortTypeHints(rolesMappedToClasses)
  } ++ JsonSerializers.serializers
}


object OptionalFieldSerializer extends Serializer[AnyRef] {
  private val typedOptionalPathRegx = "(.+?):(.+)".r
  private val memo = new Memo[universe.Type, List[String]]()
  override def deserialize(implicit format: Formats): PartialFunction[(TypeInfo, json.JValue), AnyRef] = Functions.doNothing
  private lazy val propsOutboundOptionalFields =
      APIUtil.getPropsValue("outbound.optional.fields", "")
        .split("""\s*,\s*""").filterNot(StringUtils.isBlank).toList

  private lazy val propsInboundOptionalFields =
    APIUtil.getPropsValue("inbound.optional.fields", "")
      .split("""\s*,\s*""").filterNot(StringUtils.isBlank).toList

  private val outboundType = typeOf[TopicTrait]
  private val inboundType = typeOf[InBoundTrait[_]]

  // keep current process InBound or OutBound instance, avoid dead loop.
  private val threadLocal = new java.lang.ThreadLocal[Any]

  override def serialize(implicit format: Formats): PartialFunction[Any, json.JValue] = {
    case x if isOutInboundType(x) && threadLocal.get() == null =>
      threadLocal.set(x)
      try{
        toIgnoreFieldJson(x)
      } finally {
        threadLocal.remove()
      }
  }

  def toIgnoreFieldJson(any: Any)(implicit formats: Formats): JValue = toIgnoreFieldJson(any, ReflectUtils.getType(any))

  def toIgnoreFieldJson(any: Any, tp: universe.Type, ignoreFunc: List[String] => List[String] = Functions.unary)(implicit formats: Formats): JValue = {
    val TYPE_NAME = tp.typeSymbol.name.decodedName.toString
    // if props value like this InBoundGetBanks:data.logoUrl, the type name must match current process object type.
    val filterPropsIgnoreFields: List[String] = {
      val optionalProps = if (tp <:< outboundType) {
        this.propsOutboundOptionalFields
      } else if (tp <:< inboundType) {
        this.propsInboundOptionalFields
      } else {
        Nil
      }

      optionalProps collect {
        case typedOptionalPathRegx(TYPE_NAME, ignorePath) => ignorePath
        case x if !x.contains(':') => x
      }
    }

    val ignoreFieldNames: List[String] = getOptionals(tp) ::: filterPropsIgnoreFields
    val zson = json.Extraction.decompose(any)
    ignoreFieldNames match {
      case Nil => zson
      case ignores => JsonUtils.deleteFields(zson, ignoreFunc(ignores))
    }
  }

  private def isOutInboundType(any: Any) = {
    val typeName = any.getClass.getSimpleName
    ReflectUtils.isObpObject(any) && (typeName.startsWith("OutBound") || typeName.startsWith("InBound"))
  }

  def getOptionals(tp: universe.Type): List[String] = {
    if(!ReflectUtils.isObpType(tp)) {
      return Nil
    }
    memo.memoize(tp){
      val fields: List[universe.Symbol] = tp.decls.filter(decl => decl.isTerm && (decl.asTerm.isVal || decl.asTerm.isVar)).toList
      val (optionalFields, notIgnoreFields) = fields.partition(_.annotations.exists(_.tree.tpe <:< typeOf[optional]))
      val annotedFieldNames = optionalFields.map(_.name.decodedName.toString.trim)
      val subAnnotedFieldNames = notIgnoreFields.flatMap(it => {
        val fieldName = it.name.decodedName.toString.trim
        val fieldType: universe.Type = it.info match {
          case x if x <:< typeOf[Iterable[_]] &&  !(x <:< typeOf[Map[_,_]]) =>
            x.typeArgs.head
          case x if x <:< typeOf[Array[_]] =>
            x.typeArgs.head
          case x => x
        }

        getOptionals(fieldType)
          .map(it =>  s"$fieldName.$it")
      })
      annotedFieldNames ++ subAnnotedFieldNames
    }
  }
}


object JsonAbleSerializer extends Serializer[PrimaryDataBody[_]] {
  private val IntervalClass = classOf[Product]

  override def serialize(implicit format: Formats): PartialFunction[Any, JValue] = Functions.doNothing

  override def deserialize(implicit format: Formats): PartialFunction[(TypeInfo, json.JValue), PrimaryDataBody[_]] = {
    case (TypeInfo(IntervalClass, _), json) if !json.isInstanceOf[JObject] => json match {
      case JNothing => EmptyBody
      case JString(v) => StringBody(v)
      case JBool(v) => BooleanBody(v)
      case JInt(v) => LongBody(v.toLong)
      case JDouble(v) => DoubleBody(v)
      case v: JArray => JArrayBody(v)
      case x => throw new MappingException("Can't convert " + x + " to PrimaryDataBody")
    }
  }
}