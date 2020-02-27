package code.util

import org.scalatest.{FlatSpec, Matchers, Tag}
import JsonUtils.buildJson
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
}
