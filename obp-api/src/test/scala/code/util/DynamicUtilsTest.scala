package code.util

import code.api.util.DynamicUtil
import com.openbankproject.commons.util.{JsonUtils, ReflectUtils}
import net.liftweb.common.Box
import net.liftweb.json
import org.scalatest.{FlatSpec, Matchers, Tag}

class DynamicUtilsTest extends FlatSpec with Matchers {
  object DynamicUtilsTag extends Tag("DynamicUtils")
  implicit val formats = code.api.util.CustomJsonFormats.formats
  val zson = {
    """
      |{
      |   "name": "Sam",
      |   "age": [12],
      |   "isMarried": true,
      |   "weight": 12.11,
      |   "class": "2",
      |   "def": 12,
      |   "email": ["abc@def.com", "hijk@abc.com"],
      |   "address": [{
      |     "name": "jieji",
      |     "code": 123123,
      |     "street":{"road": "gongbin", "number": 123},
      |     "_optional_fields_": ["code"]
      |   }],
      |   "street": {"name": "hongqi", "width": 12.11},
      |   "_optional_fields_": ["age", "weight", "address"]
      |}
      |""".stripMargin
  }
  val zson2 = """{"road": "gongbin", "number": 123}"""

  def buildFunction(jsonStr: String): String => Any = {
    val caseClasses = JsonUtils.toCaseClasses(json.parse(jsonStr))

    val code =
      s"""
         | $caseClasses
         |
         | // throws exception: net.liftweb.json.MappingException:
         | //No usable value for name
         | //Did not find value which can be converted into java.lang.String
         |
         |implicit val formats = code.api.util.CustomJsonFormats.formats
         |(str: String) => {
         |  net.liftweb.json.parse(str).extract[RootJsonClass]
         |}
         |""".stripMargin

    val fun: Box[String => Any] = DynamicUtil.compileScalaCode(code)
    fun.orNull
  }

  "Parse json to dynamic case object" should "success" taggedAs DynamicUtilsTag in {

    val func = buildFunction(zson)
    val func2 = buildFunction(zson2)
    val value1 = func.apply(zson)
    val value2 = func2.apply(zson2)

    ReflectUtils.getNestedField(value1.asInstanceOf[AnyRef], "street", "name") should be ("hongqi")
    ReflectUtils.getField(value1.asInstanceOf[AnyRef], "weight") shouldEqual Some(12.11)
    ReflectUtils.getField(value2.asInstanceOf[AnyRef], "number") shouldEqual (123)

  }
}