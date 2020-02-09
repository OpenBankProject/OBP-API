package com.openbankproject.commons.util

import com.openbankproject.commons.util.ApiVersion._
import org.scalatest.{FlatSpec, Matchers, Tag}
import org.scalatest.PartialFunctionValues._

import scala.reflect.runtime.universe._
import Functions.Implicits.RichCollection
import net.liftweb.json._
import RequiredFieldValidation.getRequiredInfo

class RequiredFieldValidationTest extends FlatSpec with Matchers {
  object tag extends Tag("RequiredFieldValidation")

  "when annotated at constructor param and overriding val" should "all the annotations be extract by call RequiredFieldValidation.getAnnotations" taggedAs tag in {
    val symbols = RequiredFieldValidation.getAnnotations(typeOf[Bar]).toMapByKey(_.fieldPath)

    symbols should have size 3

    symbols("name") should equal(RequiredArgs("name", Array(ApiVersion.allVersion), Array(v3_0_0)))

    symbols("age") should equal (
      RequiredArgs("age", Array(v2_0_0, v1_2_1, v1_4_0), Array.empty)
    )

    symbols("email") should equal(RequiredArgs("email", Array(ApiVersion.allVersion), Array()))
  }

  "method RequiredFieldValidation.getAllNestedRequiredInfo" should "extract all nested required info" taggedAs tag in {
    val requiredArgses = RequiredFieldValidation.getAllNestedRequiredInfo(typeOf[Outer])
    val stringToArgs: Map[String, RequiredArgs] = requiredArgses.toMapByKey(_.fieldPath)

    val expectedRequireFooAnnoInfo = RequiredArgs("", Array( v1_4_0))
    stringToArgs.valueAt("requireFoo") should equal (expectedRequireFooAnnoInfo.copy(fieldPath = "requireFoo"))

    val expectedNameAnnoInfo = RequiredArgs("", Array(ApiVersion.allVersion))
    val expectedAgeAnnoInfo = RequiredArgs("", Array(v2_0_0,  v1_2_1, v1_4_0))
    val expectedEmailAnnoInfo = RequiredArgs("", Array(ApiVersion.allVersion), Array(v2_0_0))

    stringToArgs.valueAt("foo.name") should equal (expectedNameAnnoInfo.copy(fieldPath = "foo.name"))
    stringToArgs.valueAt("foo.age") should equal (expectedAgeAnnoInfo.copy(fieldPath = "foo.age"))
    stringToArgs.valueAt("foo.email") should equal (expectedEmailAnnoInfo.copy(fieldPath = "foo.email"))

    stringToArgs.valueAt("requireFoo.name") should equal (expectedNameAnnoInfo.copy(fieldPath = "requireFoo.name"))
    stringToArgs.valueAt("requireFoo.age") should equal (expectedAgeAnnoInfo.copy(fieldPath = "requireFoo.age"))
    stringToArgs.valueAt("requireFoo.email") should equal (expectedEmailAnnoInfo.copy(fieldPath = "requireFoo.email"))

    stringToArgs.valueAt("list.name") should equal (expectedNameAnnoInfo.copy(fieldPath = "list.name"))
    stringToArgs.valueAt("list.age") should equal (expectedAgeAnnoInfo.copy(fieldPath = "list.age"))
    stringToArgs.valueAt("list.email") should equal (expectedEmailAnnoInfo.copy(fieldPath = "list.email"))

    stringToArgs.valueAt("array.name") should equal (expectedNameAnnoInfo.copy(fieldPath = "array.name"))
    stringToArgs.valueAt("array.age") should equal (expectedAgeAnnoInfo.copy(fieldPath = "array.age"))
    stringToArgs.valueAt("array.email") should equal (expectedEmailAnnoInfo.copy(fieldPath = "array.email"))

    val expectedMiddleRequiredAnnoInfo = RequiredArgs("", Array(ApiVersion.allVersion))
    stringToArgs.valueAt("middle.middleRequired") should equal (expectedMiddleRequiredAnnoInfo.copy(fieldPath = "middle.middleRequired"))

    stringToArgs.valueAt("middle.middleRequired.name") should equal (expectedNameAnnoInfo.copy(fieldPath = "middle.middleRequired.name"))
    stringToArgs.valueAt("middle.middleRequired.age") should equal (expectedAgeAnnoInfo.copy(fieldPath = "middle.middleRequired.age"))
    stringToArgs.valueAt("middle.middleRequired.email") should equal (expectedEmailAnnoInfo.copy(fieldPath = "middle.middleRequired.email"))

    stringToArgs.valueAt("middle.middleNoRequire.name") should equal (expectedNameAnnoInfo.copy(fieldPath = "middle.middleNoRequire.name"))
    stringToArgs.valueAt("middle.middleNoRequire.age") should equal (expectedAgeAnnoInfo.copy(fieldPath = "middle.middleNoRequire.age"))
    stringToArgs.valueAt("middle.middleNoRequire.email") should equal (expectedEmailAnnoInfo.copy(fieldPath = "middle.middleNoRequire.email"))

    stringToArgs should have size (20)
  }

  // this test be grouped, every group is more complex than former.
  "method RequiredFieldValidation.getRequiredInfo" should "extract instance of T or invalid path names" taggedAs tag in {
    implicit val formats = net.liftweb.json.DefaultFormats
    val tp = typeOf[LevelFirst]
    val requiredInfo = getRequiredInfo(tp)

    {
      val json = Extraction.decompose(
        LevelFirst(null, null, null, null, null, null)
      )
      val value = requiredInfo.validateAndExtract[LevelFirst](json, v3_1_0)
      value shouldBe a [Left[_, _]]
      val Left(left) = value
      left should contain theSameElementsAs List("name", "age", "email")
    }

    {
      val json = Extraction.decompose(
        LevelFirst(null, null, null, null, null, null)
      )
      val value = requiredInfo.validateAndExtract[LevelFirst](json, v2_0_0)
      value shouldBe a [Left[_, _]]
      val Left(left) = value
      left should contain theSameElementsAs List("name")
    }


    {
      val json = Extraction.decompose(
        LevelFirst("first name", 12, "first email", LevelSecond(null, null, null, null, null, null), null, null)
      )
      val value = requiredInfo.validateAndExtract[LevelFirst](json, v3_1_0)
      value shouldBe a [Left[_, _]]
      val Left(left) = value
      left should contain theSameElementsAs  List("second.name", "second.age", "second.email")
    }
    {
      val json = Extraction.decompose(
        LevelFirst("first name", 12, null, LevelSecond(null, null, null, null, null, null), null, null)
      )
      val value = requiredInfo.validateAndExtract[LevelFirst](json, v2_0_0)
      value shouldBe a [Left[_, _]]
      val Left(left) = value
      left should contain theSameElementsAs  List("second.name")
    }

    {
      val json = Extraction.decompose(
        LevelFirst(null, 12, "first email", LevelSecond(null, null, null, null, null, null), null, null)
      )
      val value = requiredInfo.validateAndExtract[LevelFirst](json, v1_2_1)
      value shouldBe a [Left[_, _]]
      val Left(left) = value
      left should contain theSameElementsAs  List("name", "second.name", "second.email")
    }

    {
      val json = Extraction.decompose(
        LevelFirst(null, 12, "first email",
          LevelSecond("secondName", 11, null, LevelThird(null, null, null, null, null, null), null, null),
          null, null)
      )
      val value = requiredInfo.validateAndExtract[LevelFirst](json, v1_2_1)
      value shouldBe a [Left[_, _]]
      val Left(left) = value
      left should contain theSameElementsAs  List("name", "second.email", "second.third.name", "second.third.email")
    }

    {
      val json = Extraction.decompose(
        LevelFirst(null, 12, "first email",
          LevelSecond("secondName", 11, null,
            LevelThird(null, null, "third email",
                LevelForth(null, null, null)
              , null, null)
            , null, null),
          null, null)
      )
      val value = requiredInfo.validateAndExtract[LevelFirst](json, v1_2_1)
      value shouldBe a [Left[_, _]]
      val Left(left) = value
      left should contain theSameElementsAs  List("name", "second.email", "second.third.name", "second.third.forth.email", "second.third.forth.name")
    }

    {
      val json = Extraction.decompose(
        LevelFirst(null, 12, "first email",
          LevelSecond("secondName", 11, null,
            LevelThird(null, null, "third email",
                LevelForth(null, null, null)
              , null, null)
            , null, null),
          null, null)
      )
      val value = requiredInfo.validateAndExtract[LevelFirst](json, v1_2_1)
      value shouldBe a [Left[_, _]]
      val Left(left) = value
      left should contain theSameElementsAs  List("name", "second.email", "second.third.name", "second.third.forth.email", "second.third.forth.name")
    }

    {
      val forthList = List(LevelForth("name value", 12, null), LevelForth(null, null, "email value"))

      val threeList = List(LevelThird("name value", 12, null, LevelForth("name value", 12, null), forthList, forthList.toArray),
        LevelThird("name value", 12, "email value", null, forthList, forthList.toArray)
      )
      val twoList = List(LevelSecond("name value", 12, "email value", null, threeList, threeList.toArray),
        LevelSecond("name value", 12, "email value", null, threeList, threeList.toArray)
      )

      val first = LevelFirst("name value", 12, null, LevelSecond(null, 12, "email value", null, null, null), twoList, twoList.toArray)

      val json = Extraction.decompose(
        first
      )

      val expected = List(
        "email",
        "second.name",

        "listSecond.listThird.email",
        "listSecond.listThird.listForth.email",
        "listSecond.listThird.listForth.name",
        "listSecond.listThird.arrayForth.email",
        "listSecond.listThird.arrayForth.name",
        "listSecond.listThird.forth.email",

        "listSecond.arrayThird.email",
        "listSecond.arrayThird.forth.email",
        "listSecond.arrayThird.listForth.email",
        "listSecond.arrayThird.listForth.name",
        "listSecond.arrayThird.arrayForth.email",
        "listSecond.arrayThird.arrayForth.name",

        "arraySecond.listThird.email",
        "arraySecond.listThird.forth.email",
        "arraySecond.listThird.listForth.email",
        "arraySecond.listThird.listForth.name",
        "arraySecond.listThird.arrayForth.email",
        "arraySecond.listThird.arrayForth.name",

        "arraySecond.arrayThird.email",
        "arraySecond.arrayThird.forth.email",
        "arraySecond.arrayThird.listForth.email",
        "arraySecond.arrayThird.listForth.name",
        "arraySecond.arrayThird.arrayForth.email",
        "arraySecond.arrayThird.arrayForth.name",
        )

      val value  = requiredInfo.validateAndExtract[LevelFirst](json, v1_2_1)
      val value2 = requiredInfo.validate(first, v1_2_1)

      value shouldBe a [Left[_, _]]
      value2 shouldBe a [Left[_, _]]

      val Left(left) = value
      val Left(left2) = value2

      left should contain theSameElementsAs expected
      left2 should contain theSameElementsAs expected
    }

    {
      val forthList = List(LevelForth("name value", 12, "email value"))

      val threeList = List(LevelThird("name value", 12, "email value", LevelForth("name value", 12, "email value"), forthList, forthList.toArray),
        LevelThird("name value", 12, "email value", null, forthList, forthList.toArray)
      )
      val twoList = List(LevelSecond("name value", 12, "email value", null, threeList, threeList.toArray),
        LevelSecond("name value", 12, "email value", null, threeList, threeList.toArray)
      )

      val first = LevelFirst("name value", 12, "email value", LevelSecond("name value", 12, "email value", null, null, null), twoList, null)

      val json = Extraction.decompose(
        first
      )

      requiredInfo.validateAndExtract[LevelFirst](json, v1_2_1) should be equals Right(first)

      requiredInfo.validate[LevelFirst](first, v1_2_1) should be equals Right(first)
    }

  }
}


trait Foo {
  @OBPRequired(value = Array(ApiVersion.allVersion))
  def name:String
  @OBPRequired(value = Array(v2_0_0, v1_2_1, v1_4_0))
  val age: Int
  @OBPRequired(value = Array(ApiVersion.allVersion), exclude = Array(v2_0_0))
  val email: String
}

case class Bar(@OBPRequired(Array(ApiVersion.allVersion), Array( v3_0_0)) name: String, age: Int, email: String) extends Foo {
  def this(@OBPRequired name: String, @OBPRequired email: String) = this(name, 0, email)
}

class Outer (foo: Foo) {
  @OBPRequired(Array( v1_4_0))
  val requireFoo: Foo = Bar("bar name", 12, "bar@tesobe.com")
  val list: List[Foo] = List(requireFoo)
  val array: Array[Foo] = Array(requireFoo)

  val middle: Middle = new Middle("middle name", requireFoo, requireFoo)
}

class Middle(middleName: String, @OBPRequired middleRequired: Foo, middleNoRequire: Foo)

//******* the follow types for do validation

case class LevelFirst(@OBPRequired
                      name: String,
                      @OBPRequired(Array(v3_1_0, v4_0_0))
                      age: Integer,
                      @OBPRequired(Array(ApiVersion.allVersion), Array(v2_0_0))
                      email: String,
                      second: LevelSecond,
                      listSecond: List[LevelSecond],
                      arraySecond: Array[LevelSecond],
                     )

case class LevelSecond(@OBPRequired
                       name: String,
                       @OBPRequired(Array(v3_1_0, v4_0_0))
                       age: Integer,
                       @OBPRequired(Array(ApiVersion.allVersion), Array(v2_0_0))
                       email: String,
                       third: LevelThird,
                       listThird: List[LevelThird],
                       arrayThird: Array[LevelThird]
                      )
case class LevelThird(@OBPRequired
                      name: String,
                      @OBPRequired(Array(v3_1_0, v4_0_0))
                      age: Integer,
                      @OBPRequired(Array(ApiVersion.allVersion), Array(v2_0_0))
                      email: String,
                      forth: LevelForth,
                      listForth: List[LevelForth],
                      arrayForth: Array[LevelForth]
                     )
case class LevelForth(@OBPRequired
                      name: String,
                      @OBPRequired(Array(v3_1_0, v4_0_0))
                      age: Integer,
                      @OBPRequired(Array(ApiVersion.allVersion), Array(v2_0_0))
                      email: String)