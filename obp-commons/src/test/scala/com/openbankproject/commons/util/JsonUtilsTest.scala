package com.openbankproject.commons.util

import net.liftweb.common.Full
import net.liftweb.json

import java.util.Date
import net.liftweb.json.Extraction.decompose
import net.liftweb.json._
import net.liftweb.json.JsonAST.JValue
import org.scalatest.{FlatSpec, Matchers, Tag}

import java.text.SimpleDateFormat
import scala.collection.immutable.{List, Nil}

class JsonUtilsTest extends FlatSpec with Matchers {
  object FunctionsTag extends Tag("JsonUtils")
  implicit def formats: Formats = net.liftweb.json.DefaultFormats

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
  
  "deleteFieldRec" should "work " taggedAs FunctionsTag in {

    case class NestNestClass(nestNestField: String)
    case class NestClass(nestField: String, nestNestClass: NestNestClass)
    case class TestObject(
      stringField: String,
      nestClass: NestClass,
      date: Date,
      boolean: Boolean
    )

    val DateWithDay = "yyyy-MM-dd"
    val DateWithDay2 = "yyyyMMdd"
    val DateWithDay3 = "dd/MM/yyyy"
    val DateWithMinutes = "yyyy-MM-dd'T'HH:mm'Z'"
    val DateWithSeconds = "yyyy-MM-dd'T'HH:mm:ss'Z'"
    val DateWithMs = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"
    val DateWithMsRollback = "yyyy-MM-dd'T'HH:mm:ss.SSSZ" //?? what does this `Rollback` mean ??

    val DateWithDayFormat = new SimpleDateFormat(DateWithDay)
    val DateWithSecondsFormat = new SimpleDateFormat(DateWithSeconds)
    val DateWithMsFormat = new SimpleDateFormat(DateWithMs)
    val DateWithMsRollbackFormat = new SimpleDateFormat(DateWithMsRollback)

    val DateWithDayExampleString: String = "1100-01-01"
    val DateWithSecondsExampleString: String = "1100-01-01T01:01:01Z"
    val DateWithMsExampleString: String = "1100-01-01T01:01:01.000Z"
    val DateWithMsRollbackExampleString: String = "1100-01-01T01:01:01.000+0000"
    
    
    val DateWithDayExampleObject = DateWithDayFormat.parse(DateWithDayExampleString)
    val DateWithSecondsExampleObject = DateWithSecondsFormat.parse(DateWithSecondsExampleString)
    val DateWithMsExampleObject = DateWithMsFormat.parse(DateWithMsExampleString)
    val DateWithMsRollbackExampleObject = DateWithMsRollbackFormat.parse(DateWithMsRollbackExampleString)
    
    
    val testObject = TestObject(
      "1",
      NestClass(
        "1", 
        NestNestClass("2")
      ),
      DateWithSecondsExampleObject,
      true
    )

    implicit def formats: Formats = net.liftweb.json.DefaultFormats

    val excludedFieldValues = Full(s"""["$DateWithSecondsExampleString", "", null, [], {}]""").map[JArray](it => json.parse(it).asInstanceOf[JArray])

    val jsonValue = excludedFieldValues match {
      case Full(JArray(arr:List[JValue])) =>
        JsonUtils.deleteFieldRec(Extraction.decompose(testObject))(v => arr.contains(v.value))
      case _ => testObject
    }
    jsonValue.toString.contains(s"$DateWithSecondsExampleString") should be (false)
    
    
    val jsonString = """{"metadata": {}}"""
    
    
    val jsonValue2 = parse(jsonString)


    val jsonValue2Removed = excludedFieldValues match {
      case Full(JArray(arr:List[JValue])) =>
        JsonUtils.deleteFieldRec(Extraction.decompose(jsonValue2))(v => arr.contains(v.value))
      case _ => testObject
    }

    jsonValue2Removed.toString contains "{}" should be (false)


  
  }

  def toCaseClass(str: String, typeNamePrefix: String = ""): String = JsonUtils.toCaseClasses(json.parse(str), typeNamePrefix)

  "object json String" should "generate correct case class" taggedAs FunctionsTag in {

    val zson = {
      """
        |{
        |   "name": "Sam",
        |   "age": 12,
        |   "isMarried": true,
        |   "weight": 12.11,
        |   "class": "2",
        |   "def": 12,
        |   "email": ["abc@def.com", "hijk@abc.com"],
        |   "address": [{
        |     "name": "jieji",
        |     "code": 123123,
        |     "street":{"road": "gongbin", "number": 123}
        |   }],
        |   "street": {"name": "hongqi", "width": 12.11},
        |   "_optional_fields_": ["age", "weight", "address"]
        |}
        |""".stripMargin
    }
    {
      val expectedCaseClass =
        """case class AddressStreetJsonClass(road: String, number: Long)
          |case class AddressJsonClass(name: String, code: Long, street: AddressStreetJsonClass)
          |case class StreetJsonClass(name: String, width: Double)
          |case class RootJsonClass(name: String, age: Option[java.lang.Long], isMarried: Boolean, weight: Option[java.lang.Double], `class`: String, `def`: Long, email: List[String], address: Option[List[AddressJsonClass]], street: StreetJsonClass)""".stripMargin

      val generatedCaseClass = toCaseClass(zson)

      generatedCaseClass should be(expectedCaseClass)
    }
    {// test type name prefix
      val expectedCaseClass =
        """case class RequestAddressStreetJsonClass(road: String, number: Long)
          |case class RequestAddressJsonClass(name: String, code: Long, street: RequestAddressStreetJsonClass)
          |case class RequestStreetJsonClass(name: String, width: Double)
          |case class RequestRootJsonClass(name: String, age: Option[java.lang.Long], isMarried: Boolean, weight: Option[java.lang.Double], `class`: String, `def`: Long, email: List[String], address: Option[List[RequestAddressJsonClass]], street: RequestStreetJsonClass)""".stripMargin

      val generatedCaseClass = toCaseClass(zson, "Request")
      generatedCaseClass should be(expectedCaseClass)
    }
  }

  "List json" should "generate correct case class" taggedAs FunctionsTag in {
    {
      val listIntJson = """[1,2,3]"""

      toCaseClass(listIntJson) should be(""" type RootJsonClass = List[Long]""")
      toCaseClass(listIntJson, "Response") should be(""" type ResponseRootJsonClass = List[Long]""")
    }
    {
      val listObjectJson =
        """[
          | {
          |   "name": "zs"
          |   "weight": 12.34
          | },
          | {
          |   "name": "ls"
          |   "weight": 21.43
          | }
          |]""".stripMargin
      val expectedCaseClass = """case class RootItemJsonClass(name: String, weight: Double)
                                | type RootJsonClass = List[RootItemJsonClass]""".stripMargin

      val expectedRequestCaseClass = """case class RequestRootItemJsonClass(name: String, weight: Double)
                                | type RequestRootJsonClass = List[RequestRootItemJsonClass]""".stripMargin


      toCaseClass(listObjectJson) should be(expectedCaseClass)
      toCaseClass(listObjectJson, "Request") should be(expectedRequestCaseClass)
    }
  }

  "List json have different type items" should "throw exception" taggedAs FunctionsTag in {

    val listJson = """["abc",2,3]"""
    val listJson2 =
      """[
        | {
        |   "name": "zs"
        |   "weight": 12.34
        | },
        | {
        |   "name": "ls"
        |   "weight": 21
        | }
        |]""".stripMargin
    val objectJson =
      """{
        | "emails": [true, "abc@def.com"]
        |}""".stripMargin

    val objectNestedListJson =
      """{
        | "emails": {
        |   "list": [12.34, "abc@def.com"]
        |   }
        |}""".stripMargin

    the [IllegalArgumentException] thrownBy toCaseClass(listJson) should have message "All the items of Json  should be String type."
    the [IllegalArgumentException] thrownBy toCaseClass(listJson2) should have message "All the items of Json  should the same structure."
    the [IllegalArgumentException] thrownBy toCaseClass(objectJson) should have message "All the items of Json emails should be Boolean type."
    the [IllegalArgumentException] thrownBy toCaseClass(objectNestedListJson) should have message "All the items of Json emails.list should be number type."
  }

}
