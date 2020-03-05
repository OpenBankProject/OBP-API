package code.util

import code.api.UKOpenBanking.v2_0_0.OBP_UKOpenBanking_200
import com.openbankproject.commons.util.ApiVersion
import code.api.util.{ CustomJsonFormats}
import com.openbankproject.commons.util.OBPRequired
import net.liftweb.json
import net.liftweb.json.JsonDSL._
import net.liftweb.json.{Formats, JObject}
import org.scalatest.{FeatureSpec, GivenWhenThen, Matchers}

case class FirstTypeForTest(val name: String, age: Option[Int])

case class WrappedFirstType(val oneField: String, firstType: FirstTypeForTest)

case class SecondTypeForTest(val name: String, age: Int) {
  def this(name: String) = this(name, 0)
}

class CustomJsonFormatsTest extends FeatureSpec with Matchers with GivenWhenThen {
  implicit val formats: Formats = CustomJsonFormats.nullTolerateFormats

  feature("test null value in JValue") {
    scenario("json have all constructor param values") {
      val jsonStr =
        """
          |{
          |  "name": "zhangsan",
          |  "age": 12,
          |}
          |""".stripMargin
      val jValue = json.parse(jsonStr)
      val obj1 = jValue.extract[FirstTypeForTest]
      val obj2 = jValue.extract[SecondTypeForTest]

      val expectedFirst = FirstTypeForTest("zhangsan", Some(12))
      obj1 should  equal (expectedFirst)
      obj2 should  equal (SecondTypeForTest("zhangsan",12))

      val jObj: JObject = "firstType" -> jValue
      val wrappedFirstType = jObj.extract[WrappedFirstType]
      wrappedFirstType should  equal (WrappedFirstType(null, expectedFirst))
    }

    scenario("json have missing required constructor param value") {
      val jsonStr =
        """
          |{
          |  "name": "zhangsan",
          |}
          |""".stripMargin
      val obj1 = json.parse(jsonStr).extract[FirstTypeForTest]
      val obj2 = json.parse(jsonStr).extract[SecondTypeForTest]

      obj1 should  equal (FirstTypeForTest("zhangsan", None))
      obj2 should  equal (SecondTypeForTest("zhangsan", 0))
    }


    scenario("json have required constructor param value") {
      val jsonStr =
        """
          |{
          |  "age": 12,
          |}
          |""".stripMargin

      val j = json.parse(jsonStr)

      val obj1 = j.extract[FirstTypeForTest]
      j.extract[FirstTypeForTest]
      val obj2 = j.extract[SecondTypeForTest]

      obj1 should  equal (FirstTypeForTest(null, Some(12)))
      obj2 should  equal (SecondTypeForTest(null, 12))
    }
  }
}


trait Foo {
  @OBPRequired(value = Array(ApiVersion.allVersion))
  def name:String
  @OBPRequired(value = Array(OBP_UKOpenBanking_200.apiVersion, ApiVersion.v1_2_1, ApiVersion.v1_4_0))
  val age: Int
  @OBPRequired(value = Array.empty)
  val email: String
}

case class Bar(@OBPRequired(Array(ApiVersion.allVersion), Array(ApiVersion.v3_0_0)) name: String, age: Int, email: String) extends Foo {
  def this(@OBPRequired name: String, @OBPRequired email: String) = this(name, 0, email)
}
