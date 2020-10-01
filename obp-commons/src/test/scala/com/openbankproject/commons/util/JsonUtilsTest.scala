package com.openbankproject.commons.util

import java.util.Date

import net.liftweb.json.Extraction.decompose
import net.liftweb.json.Formats
import org.scalatest.{FlatSpec, Matchers, Tag}

class JsonUtilsTest extends FlatSpec with Matchers {
  object FunctionsTag extends Tag("JsonUtils")

  "collectFieldNames" should "return all the field names and path" taggedAs FunctionsTag in {

    case class NestNestClass(nestNestField: String)
    case class NestClass(nestField: String, nestNestClass: NestNestClass)
    case class TestObject(
      stringField: String,
      nestClass: NestClass,
      date: Date,
      boolean: Boolean
    )

    val testObject = TestObject(
      "1",
      NestClass("1", NestNestClass("2")),
      new Date(),
      true
    )

    implicit def formats: Formats = net.liftweb.json.DefaultFormats
    val fields = JsonUtils.collectFieldNames(decompose(testObject))

    val names: List[String] = fields.map(_._1).toList
    names.length should be (7)
    names should contain ("stringField")
    names should contain ("nestClass")
    names should contain ("date")
    names should contain ("boolean")
    names should contain ("nestField")
    names should not contain ("nestField1")
  }
 
}
