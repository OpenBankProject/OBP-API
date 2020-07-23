package code.util

import org.scalatest.{FlatSpec, Matchers, Tag}
import com.openbankproject.commons.util.JsonUtils.buildJson
import net.liftweb.json

class JsonUtilsTest extends FlatSpec with Matchers {
  object JsonUtilsTag extends Tag("JsonUtils")

  val zson = json.parse(
    """
      |{
      |  "level": 3
      |  "banks":[{
      |    "id":"dmo.01.uk.uk",
      |    "short_name":"uk",
      |    "full_name":"uk",
      |    "is_deleted": true,
      |    "is_new": true,
      |    "score": 10,
      |    "logo":"http://www.example.com",
      |    "website":"http://www.example.com",
      |    "bank_routings":[{
      |      "scheme":"OBP",
      |      "address":"dmo.01.uk.uk"
      |    }]
      |  },{
      |    "id":"dmo.02.uk.uk",
      |    "short_name":"uk",
      |    "full_name":"uk",
      |    "is_deleted": false,
      |    "is_new": true,
      |    "score": 5.3,
      |    "logo":"https://static.openbankproject.com/images/sandbox/bank_y.png",
      |    "website":"http://www.example.com"
      |  },{
      |    "id":"dmo.02.de.de",
      |    "short_name":"de",
      |    "full_name":"de",
      |    "is_deleted": false,
      |    "is_new": false,
      |    "score": 2,
      |    "logo":"https://static.openbankproject.com/images/sandbox/bank_z.png",
      |    "website":"http://www.example.com",
      |    "bank_routings":[{
      |      "scheme":"OBP",
      |      "address":"dmo.02.de.de"
      |    }]
      |  }]
      |}
      |""".stripMargin)

  val schema = json.parse(
    """
      |{
      | "value": " 'number: 1.0' + 'int:1' * level",
      | "code": 200,
      | "meta$default": {
      |   "count": 10,
      |   "classInfo": {
      |       "someInfo[]": "hello"
      |   }
      | }
      | "result[]": {
      |   "bkId": "banks.id",
      |   "bkName": "'hello:' + banks.short_name+ ' +  ' + banks.full_name",
      |   "is_exists": "!banks.is_deleted",
      |   "newBank": "!banks.is_deleted & banks.is_new",
      |   "routing": "banks.bank_routings[0]"
      | },
      | "secondBank[1]": {
      |   "id": "banks.id",
      |   "name": "banks.short_name",
      |   "count": "banks.score * 'int: 2'"
      | }
      |}
      |""".stripMargin)

  val expectedJson = json.parse(
    """
      |{
      | "value":6.0,
      |  "code":200,
      |  "meta":{
      |    "count":10,
      |    "classInfo":{
      |      "someInfo[]":"hello"
      |    }
      |  },
      |  "result":[
      |    {
      |      "bkId":"dmo.01.uk.uk",
      |      "bkName":"hello:uk +  uk",
      |      "is_exists":false,
      |      "newBank":false,
      |      "routing":{
      |        "scheme":"OBP",
      |        "address":"dmo.01.uk.uk"
      |      }
      |    },
      |    {
      |      "bkId":"dmo.02.uk.uk",
      |      "bkName":"hello:uk +  uk",
      |      "is_exists":true,
      |      "newBank":true
      |    },
      |    {
      |      "bkId":"dmo.02.de.de",
      |      "bkName":"hello:de +  de",
      |      "is_exists":true,
      |      "newBank":false,
      |      "routing":{
      |        "scheme":"OBP",
      |        "address":"dmo.02.de.de"
      |      }
      |    }
      |  ],
      |  "secondBank": {
      |      "id":"dmo.02.uk.uk",
      |      "name":"uk",
      |      "count":10.6
      |    }
      |}
      |""".stripMargin)
  "transformField" should "generate JValue according schema" taggedAs JsonUtilsTag in {
    val resultJson = buildJson(zson, schema)

    val str1 = json.prettyRender(resultJson)
    println(str1)
    val str2 = json.prettyRender(expectedJson)
    str1 shouldEqual str2
  }

  val arrayRoot = json.parse(
    """
      |[
      | {"name": "Shuang", "age": 10},
      | {"name": "Sean", "age": 11},
      | ["running", "coding"]
      |]
      |""".stripMargin)

  val arrayRootSchema = json.parse(
    """
      |{
      | "firstPerson": "[0]"
      | "secondName": "[1].name",
      | "secondHobby": "[2][1]",
      | "noPerson": "[3]",
      |}
      |""".stripMargin)
  "get Array type json given index, when exists" should "get given item" taggedAs JsonUtilsTag in {
    val resultJson = buildJson(arrayRoot, arrayRootSchema)

    val str1 = json.prettyRender(resultJson)
    println(str1)

    val expectedJson = json.parse(
      """
        |{
        |  "firstPerson":{
        |    "name":"Shuang",
        |    "age":10
        |  },
        |  "secondName":"Sean",
        |  "secondHobby":"coding"
        |}
        |""".stripMargin)

    val str2 = json.prettyRender(expectedJson)
    str1 shouldEqual str2
  }

  val jsonList = json.parse(
    """
      |[
      |  {
      |    "id": "xrest-bank-x--uk",
      |    "shortName": "bank shortName string",
      |    "fullName": "bank fullName x from rest connector",
      |    "logo": "bank logoUrl string",
      |    "websiteUrl": "bank websiteUrl string",
      |    "bankRouting": [{
      |      "Scheme": "BIC",
      |      "Address": "GENODEM1GLS"
      |    }],
      |    "swiftBic": "bank swiftBic string",
      |    "nationalIdentifier": "bank nationalIdentifier string"
      |  },
      |  {
      |    "id": "xrest-bank-y--uk",
      |    "shortName": "bank shortName y",
      |    "fullName": "bank fullName y  from rest connector",
      |    "logo": "bank logoUrl y",
      |    "websiteUrl": "bank websiteUrl y",
      |    "bankRouting": [{
      |      "Scheme": "BIC2",
      |      "Address": "GENODEM1GLS2"
      |    }],
      |    "swiftBic": "bank swiftBic string",
      |    "nationalIdentifier": "bank nationalIdentifier string"
      |  }
      |]
      |""".stripMargin)
  val jsonListSchema = json.parse(
    """
      |{
      |  "inboundAdapterCallContext$default":{
      |    "correlationId":"1flssoftxq0cr1nssr68u0mioj",
      |    "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
      |    "generalContext":[{
      |      "key":"5987953",
      |      "value":"FYIUYF6SUYFSD"
      |    }]
      |  },
      |  "status$default":{
      |    "errorCode":"status error code string",
      |    "backendMessages":[{
      |      "source":"",
      |      "status":"Status string",
      |      "errorCode":"",
      |      "text":"text string"
      |    }]
      |  },
      |  "data[]":{
      |    "bankId[]":{
      |      "value":"id"
      |    },
      |    "shortName[]":"shortName",
      |    "fullName[]":"fullName",
      |    "logoUrl[]":"logo",
      |    "websiteUrl[]":"websiteUrl",
      |    "bankRoutingScheme[]":"bankRouting.Scheme",
      |    "bankRoutingAddress[]":"bankRouting.Address",
      |    "swiftBic[]":"swiftBic",
      |    "nationalIdentifier[]":"swiftBic"
      |  }
      |}
      |""".stripMargin)
  val expectListResult = json.parse(
    """
      |{
      |  "inboundAdapterCallContext":{
      |    "correlationId":"1flssoftxq0cr1nssr68u0mioj",
      |    "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
      |    "generalContext":[
      |      {
      |        "key":"5987953",
      |        "value":"FYIUYF6SUYFSD"
      |      }
      |    ]
      |  },
      |  "status":{
      |    "errorCode":"status error code string",
      |    "backendMessages":[
      |      {
      |        "source":"",
      |        "status":"Status string",
      |        "errorCode":"",
      |        "text":"text string"
      |      }
      |    ]
      |  },
      |  "data":[
      |    {
      |      "bankId":{
      |        "value":"xrest-bank-x--uk"
      |      },
      |      "shortName":"bank shortName string",
      |      "fullName":"bank fullName x from rest connector",
      |      "logoUrl":"bank logoUrl string",
      |      "websiteUrl":"bank websiteUrl string",
      |      "bankRoutingScheme":"BIC",
      |      "bankRoutingAddress":"GENODEM1GLS",
      |      "swiftBic":"bank swiftBic string",
      |      "nationalIdentifier":"bank swiftBic string"
      |    },
      |    {
      |      "bankId":{
      |        "value":"xrest-bank-y--uk"
      |      },
      |      "shortName":"bank shortName y",
      |      "fullName":"bank fullName y  from rest connector",
      |      "logoUrl":"bank logoUrl y",
      |      "websiteUrl":"bank websiteUrl y",
      |      "bankRoutingScheme":"BIC2",
      |      "bankRoutingAddress":"GENODEM1GLS2",
      |      "swiftBic":"bank swiftBic string",
      |      "nationalIdentifier":"bank swiftBic string"
      |    }
      |  ]
      |}
      |""".stripMargin)
  "list type fields" should "properly be convert" taggedAs JsonUtilsTag in {
    val resultJson = buildJson(jsonList, jsonListSchema)

    val str1 = json.prettyRender(resultJson)
    val str2 = json.prettyRender(expectListResult)
    str1 shouldEqual str2
  }
}
