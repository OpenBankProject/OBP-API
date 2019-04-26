package code.api.util

import code.api.util.ApiRole.rolesMappedToClasses
import net.liftweb.json.{DefaultFormats, Formats, ShortTypeHints, TypeInfo, JString, JValue, MappingException, Serializer}

trait CustomJsonFormats {
  implicit val formats: Formats = CustomJsonFormats.formats
}

object CustomJsonFormats {
  val formats: Formats = net.liftweb.json.DefaultFormats + BigDecimalSerializer

  val losslessFormats: Formats =  net.liftweb.json.DefaultFormats.lossless + BigDecimalSerializer

  val emptyHintFormats = DefaultFormats.withHints(ShortTypeHints(List())) + BigDecimalSerializer

  lazy val rolesMappedToClassesFormats: Formats = new Formats {
    val dateFormat = net.liftweb.json.DefaultFormats.dateFormat

    override val typeHints = ShortTypeHints(rolesMappedToClasses)
  } + BigDecimalSerializer
}

object BigDecimalSerializer extends Serializer[BigDecimal] {
  private val IntervalClass = classOf[BigDecimal]

  override def deserialize(implicit format: Formats): PartialFunction[(TypeInfo, JValue), BigDecimal] = {
    case (TypeInfo(IntervalClass, _), json) => json match {
      case JString(s) => BigDecimal(s)
      case x => throw new MappingException("Can't convert " + x + " to BigDecimal")
    }
  }

  override def serialize(implicit format: Formats): PartialFunction[Any, JValue] = {
    case x: BigDecimal => JString(x.toString())
  }
}
