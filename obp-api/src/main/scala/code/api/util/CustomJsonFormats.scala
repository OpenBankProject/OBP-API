package code.api.util

import code.api.util.ApiRole.rolesMappedToClasses
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

  val formats: Formats = JsonSerializers.commonFormats

  val losslessFormats: Formats =  net.liftweb.json.DefaultFormats.lossless ++ JsonSerializers.serializers

  val emptyHintFormats = DefaultFormats.withHints(ShortTypeHints(List())) ++ JsonSerializers.serializers

  implicit val nullTolerateFormats = JsonSerializers.nullTolerateFormats

  lazy val rolesMappedToClassesFormats: Formats = new Formats {
    val dateFormat = net.liftweb.json.DefaultFormats.dateFormat

    override val typeHints = ShortTypeHints(rolesMappedToClasses)
  } ++ JsonSerializers.serializers
}


object FieldIgnoreSerializer extends Serializer[AnyRef] {
  private val typedIgnoreRegx = "(.+?):(.+)".r
  private val memo = new Memo[universe.Type, List[String]]()
  override def deserialize(implicit format: Formats): PartialFunction[(TypeInfo, json.JValue), AnyRef] = Functions.doNothing
  private lazy val propsConfigIgnoreFields = Array(
      APIUtil.getPropsValue("outbound.ignore.fields", "").split("""\s*,\s*"""),
      APIUtil.getPropsValue("inbound.ignore.fields", "").split("""\s*,\s*""")
    ).flatten.filterNot(StringUtils.isBlank).toList

  // keep current process InBound or OutBound instance, avoid dead loop.
  private val threadLocal = new java.lang.ThreadLocal[Any]

  override def serialize(implicit format: Formats): PartialFunction[Any, json.JValue] = {
    case x if isInOutBoundType(x) && threadLocal.get() == null =>
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
    val filterPropsIgnoreFields = propsConfigIgnoreFields collect {
      case typedIgnoreRegx(TYPE_NAME, ignorePath) => ignorePath
      case x => x
    }
    val ignoreFieldNames: List[String] = getIgnores(tp) ::: filterPropsIgnoreFields
    val zson = json.Extraction.decompose(any)
    ignoreFieldNames match {
      case Nil => zson
      case ignores => JsonUtils.deleteFields(zson, ignoreFunc(ignores))
    }
  }

  private def isInOutBoundType(any: Any) = {
    if(ReflectUtils.isObpObject(any)) {
      val className = any.getClass.getSimpleName
      className.startsWith("OutBound") || className.startsWith("InBound")
    } else {
      false
    }
  }

  def getIgnores(tp: universe.Type): List[String] = {
    if(!ReflectUtils.isObpType(tp)) {
      return Nil
    }
    memo.memoize(tp){
      val fields: List[universe.Symbol] = tp.decls.filter(decl => decl.isTerm && (decl.asTerm.isVal || decl.asTerm.isVar)).toList
      val (ignoreFields, notIgnoreFields) = fields.partition(_.annotations.exists(_.tree.tpe <:< typeOf[ignore]))
      val annotedFieldNames = ignoreFields.map(_.name.decodedName.toString.trim)
      val subAnnotedFieldNames = notIgnoreFields.flatMap(it => {
        val fieldName = it.name.decodedName.toString.trim
        val fieldType: universe.Type = it.info match {
          case x if x <:< typeOf[Iterable[_]] &&  !(x <:< typeOf[Map[_,_]]) =>
            x.typeArgs.head
          case x if x <:< typeOf[Array[_]] =>
            x.typeArgs.head
          case x => x
        }

        getIgnores(fieldType)
          .map(it =>  s"$fieldName.$it")
      })
      annotedFieldNames ++ subAnnotedFieldNames
    }
  }
}

