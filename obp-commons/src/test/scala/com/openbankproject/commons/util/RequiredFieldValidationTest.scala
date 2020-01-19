package com.openbankproject.commons.util

import com.openbankproject.commons.util.ApiVersion
import com.openbankproject.commons.util.ApiVersion._
import org.scalatest.{FlatSpec, Matchers, Tag}
import org.scalatest.PartialFunctionValues._

import scala.reflect.runtime.universe._

class RequiredFieldValidationTest extends FlatSpec with Matchers {
  object tag extends Tag("RequiredFieldValidation")

  "when annotated at constructor param and overriding val" should "all the annotations be extract by call RequiredFieldValidation.getAnnotations" taggedAs tag in {
    val symbols = RequiredFieldValidation.getAnnotations(typeOf[Bar])

    symbols should have size 3

    symbols("name") should equal(RequiredArgs(Array(ApiVersion.allVersion), Array(v3_0_0)))

    symbols("age") should equal (
      RequiredArgs(Array(v2_0_0, v1_2_1, v1_4_0), Array.empty)
    )

    symbols("email") should equal(RequiredArgs(Array(ApiVersion.allVersion), Array()))
  }

  "method RequiredFieldValidation.getAllNestedRequiredInfo" should "extract all nested required info" taggedAs tag in {
    val stringToArgs: Map[String, RequiredArgs] = RequiredFieldValidation.getAllNestedRequiredInfo(typeOf[Outer])

    val expectedRequireFooAnnoInfo = RequiredArgs(Array( v1_4_0))
    stringToArgs.valueAt("requireFoo") should equal (expectedRequireFooAnnoInfo)

    val expectedNameAnnoInfo = RequiredArgs(Array(ApiVersion.allVersion))
    val expectedAgeAnnoInfo = RequiredArgs(Array(v2_0_0,  v1_2_1, v1_4_0))
    val expectedEmailAnnoInfo = RequiredArgs(Array(ApiVersion.allVersion), Array(v2_0_0))

    stringToArgs.valueAt("foo.name") should equal (expectedNameAnnoInfo)
    stringToArgs.valueAt("foo.age") should equal (expectedAgeAnnoInfo)
    stringToArgs.valueAt("foo.email") should equal (expectedEmailAnnoInfo)

    stringToArgs.valueAt("requireFoo.name") should equal (expectedNameAnnoInfo)
    stringToArgs.valueAt("requireFoo.age") should equal (expectedAgeAnnoInfo)
    stringToArgs.valueAt("requireFoo.email") should equal (expectedEmailAnnoInfo)

    stringToArgs.valueAt("list.name") should equal (expectedNameAnnoInfo)
    stringToArgs.valueAt("list.age") should equal (expectedAgeAnnoInfo)
    stringToArgs.valueAt("list.email") should equal (expectedEmailAnnoInfo)

    stringToArgs.valueAt("array.name") should equal (expectedNameAnnoInfo)
    stringToArgs.valueAt("array.age") should equal (expectedAgeAnnoInfo)
    stringToArgs.valueAt("array.email") should equal (expectedEmailAnnoInfo)

    val expectedMiddleRequiredAnnoInfo = RequiredArgs(Array(ApiVersion.allVersion))
    stringToArgs.valueAt("middle.middleRequired") should equal (expectedMiddleRequiredAnnoInfo)

    stringToArgs.valueAt("middle.middleRequired.name") should equal (expectedNameAnnoInfo)
    stringToArgs.valueAt("middle.middleRequired.age") should equal (expectedAgeAnnoInfo)
    stringToArgs.valueAt("middle.middleRequired.email") should equal (expectedEmailAnnoInfo)

    stringToArgs.valueAt("middle.middleNoRequire.name") should equal (expectedNameAnnoInfo)
    stringToArgs.valueAt("middle.middleNoRequire.age") should equal (expectedAgeAnnoInfo)
    stringToArgs.valueAt("middle.middleNoRequire.email") should equal (expectedEmailAnnoInfo)

    stringToArgs should have size (20)
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


