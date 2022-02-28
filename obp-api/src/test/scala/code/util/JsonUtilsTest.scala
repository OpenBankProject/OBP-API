package code.util

import org.scalatest.{FlatSpec, Matchers, Tag}
import com.openbankproject.commons.util.JsonUtils.buildJson
import net.liftweb.json
import net.liftweb.json.JBool
import net.liftweb.json.JsonAST.{JNothing, JValue}

class JsonUtilsTest extends FlatSpec with Matchers {
  object JsonUtilsTag extends Tag("JsonUtils")

  "buildJson" should "generate JValue according schema" taggedAs JsonUtilsTag in {

    val zson = json.parse(
      """
        |{
        |  "level": 3,
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
        |       "someInfo": "hello"
        |   }
        | },
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
        |      "someInfo":"hello"
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
    
    val resultJson = buildJson(zson, schema)

    val str1 = json.prettyRender(resultJson)

    val str2 = json.prettyRender(expectedJson)

    str1 shouldEqual str2
  }

  "buildJson" should "generate JValue according schema2" taggedAs JsonUtilsTag in {
    val zson = (
      """
        |{
        |  "id1": 698761728,
        |  "name1": "James Brown",
        |  "status1": "Done"
        |}
        |""".stripMargin)

    val schema = (
      """{
        |  "id": "id1",
        |  "name": "name1",
        |  "status": "status1"
        |}""".stripMargin)

    val expectedJson = json.parse(
      """{"id": 698761728,
        |  "name": "James Brown",
        |  "status": "Done"
        |}""".stripMargin)

    val resultJson = buildJson(zson, schema)

    val str1 = json.prettyRender(resultJson)
    
    val str2 = json.prettyRender(expectedJson)
    str1 shouldEqual str2
  }
  
  """buildJson-request single{}, mapping is {"photoUrls[]":"field5"}""" should "generate JValue according schema3" taggedAs JsonUtilsTag in {
    val zson = (
      """{
        |    "field1": "field1-1",
        |    "field2": "field2-1",
        |    "field3": "field3-1",
        |    "field4": "field4-1",
        |    "field5": "field5-1",
        |    "field6": "field6-1",
        |    "field7": "field7-1",
        |    "field8": "field8-1"
        |}""".stripMargin)

    val mapping = (
      """{
        |  "id":"field1",
        |  "category":{
        |    "id":"field2",
        |    "name":"field3"
        |  },
        |  "name":"field4",
        |  "photoUrls[]":"field5",
        |  "tags[]":{
        |    "id":"field6",
        |    "name":"field7"
        |  },
        |  "status":"field8"
        |}
        |""".stripMargin)

    val expectedJson = json.parse(
      """{
        |  "id":"field1-1",
        |  "category":{
        |    "id":"field2-1",
        |    "name":"field3-1"
        |  },
        |  "name":"field4-1",
        |  "photoUrls":[
        |    "field5-1"
        |  ],
        |  "tags":[
        |    {
        |      "id":"field6-1",
        |      "name":"field7-1"
        |    }
        |  ],
        |  "status":"field8-1"
        |}
        |""".stripMargin)

    val resultJson = buildJson(zson, mapping)

    val str1 = json.prettyRender(resultJson)
    
    val str2 = json.prettyRender(expectedJson)
    str1 shouldEqual str2
  }
  
  """buildJson-request Array[{}], mapping is {"photoUrls[]":"field5"}""" should "generate JValue according schema3" taggedAs JsonUtilsTag in {
    val requestJson = (
      """[
        |  {
        |    "field5": "field5-1",
        |  },
        |    {
        |    "field5": "field5-2",
        |  }
        |]""".stripMargin)
    val mapping = (
      """{
        |  "$root[]": {
        |    "photoUrls[][]": "field5",
        |  }
        |}""".stripMargin)
    
    val expectedJson = json.parse(
      """[
        |  {
        |    "photoUrls":["field5-1"]
        |  },
        |  {
        |    "photoUrls":["field5-2"]
        |  }
        |]""".stripMargin)
    val resultJson = buildJson(requestJson, mapping)

    val str1 = json.prettyRender(resultJson)
    
    val str2 = json.prettyRender(expectedJson)
    str1 shouldEqual str2
  }

  """buildJson - request is Array.tags[], mapping is {"field5[0]": "photoUrls"}""" should "generate JValue according schema4" taggedAs JsonUtilsTag in{
    val zson = (
      """{
        |    "id": 1,
        |    "category": {
        |        "id": 1,
        |        "name": "string"
        |    },
        |    "name": "doggie",
        |    "photoUrls": [
        |        "string"
        |    ],
        |    "tags": [
        |        {
        |            "id": 1,
        |            "name": "string"
        |        }
        |    ],
        |    "status": "available"
        |}""".stripMargin)

    val schema = (
      """{
        |  "field1": "id",
        |  "field2": "category.id",
        |  "field3": "category.name",
        |  "field4": "name",
        |  "field5[0]": "photoUrls",
        |  "field6[0]": "tags.id",
        |  "field7[0]": "tags.name",
        |  "field8": "status",
        |}""".stripMargin)

    val expectedJson = json.parse(
      """{
        |  "field1":1,
        |  "field2":1,
        |  "field3":"string",
        |  "field4":"doggie",
        |  "field5":"string",
        |  "field6":1,
        |  "field7":"string",
        |  "field8":"available"
        |}
        |""".stripMargin)

    val resultJson = buildJson(zson, schema)

    val str1 = json.prettyRender(resultJson)
    
    val str2 = json.prettyRender(expectedJson)
    str1 shouldEqual str2
  }

  "buildJson request is Array, mapping is data[]:{}" should "generate JValue according schema5" taggedAs JsonUtilsTag in {
    
    val requestJson = (
      """[
        |  {
        |    "field1": 11,
        |    "field2": 12,
        |    "field3": "field3-value-1",
        |    "field4": "field4-value-2",
        |    "field5": "field5-value-3",
        |    "field6": 16,
        |    "field7": "field7-value-4",
        |    "field8": "field8-value-5",
        |    "pet_entity_id": "b57c3eed-9726-4aa0-aba2-d0dcc4f45a9e"
        |  },
        |  {
        |    "field1": 21,
        |    "field2": 22,
        |    "field3": "field3-value-2",
        |    "field4": "field4-value-2",
        |    "field5": "field5-value-2",
        |    "field6": 26,
        |    "field7": "field7-value-2",
        |    "field8": "field8-value-2",
        |    "pet_entity_id": "babb259e-859b-47d8-b40c-e77cd6cb7b12"
        |  }
        |]
        |""".stripMargin)
    
    //TODO. can we remove this data[]? this `data` is not in response.
    val mapping = ("""{
                     |  "data[]": {
                     |    "id": "field1",
                     |    "name": "field4",
                     |    "status": "field8"
                     |  }
                     |}""".stripMargin)
    val expectedJson = json.parse(
      """{
        |  "data":[
        |    {
        |      "id":11,
        |      "name":"field4-value-2",
        |      "status":"field8-value-5"
        |    },
        |    {
        |      "id":21,
        |      "name":"field4-value-2",
        |      "status":"field8-value-2"
        |    }
        |  ]
        |}""".stripMargin)
    val resultJson = buildJson(requestJson, mapping)
    
    val str1 = json.prettyRender(resultJson)
    
    val str2 = json.prettyRender(expectedJson)
    
    str1 shouldEqual str2
  }

  "buildJson - schema empty" should "generate JValue according schema6" taggedAs JsonUtilsTag in {
    val zson ="""{
        |  "field1": 2,
        |  "field2": 1,
        |  "field3": "string",
        |  "field4": "doggie",
        |  "field5": "string",
        |  "field6": 1,
        |  "field7": "string",
        |  "field8": "available",
        |  "pet_entity_id": "b57c3eed-9726-4aa0-aba2-d0dcc4f45a9e"
        |}""".stripMargin

    val schema = json.parse("{}")

    val expectedJson = json.parse("""{}""")

    val resultJson = buildJson(zson, schema)

    val str1 = json.prettyRender(resultJson)
    val str2 = json.prettyRender(expectedJson)
    str1 shouldEqual str2
    
  }

  "buildJson - JNothing" should "generate JValue according schema7" taggedAs JsonUtilsTag in {
    val zson = JNothing
    val schema = json.parse("""{
                               |  "id":"pet_entity_id",
                               |  "name":"field4",
                               |  "status":"field8"
                               |}""".stripMargin)
    
    val expectedJson = JNothing

    val resultJson = buildJson(zson, schema)

    expectedJson shouldEqual resultJson
    
  }

  "buildJson - Array" should "generate JValue according schema8" taggedAs JsonUtilsTag in {
    val requestJson = (
      """[
        |  {
        |    "field1": 11,
        |    "field2": 12,
        |    "field3": "field3-value-1",
        |    "field4": "field4-value-2",
        |    "field5": "field5-value-3",
        |    "field6": 16,
        |    "field7": "field7-value-4",
        |    "field8": "field8-value-5",
        |    "pet_entity_id": "b57c3eed-9726-4aa0-aba2-d0dcc4f45a9e"
        |  },
        |  {
        |    "field1": 21,
        |    "field2": 22,
        |    "field3": "field3-value-2",
        |    "field4": "field4-value-2",
        |    "field5": "field5-value-2",
        |    "field6": 26,
        |    "field7": "field7-value-2",
        |    "field8": "field8-value-2",
        |    "pet_entity_id": "babb259e-859b-47d8-b40c-e77cd6cb7b12"
        |  }
        |]
        |""".stripMargin)
    val mapping = ("""{
                     |  "$root[]": {
                     |    "id": "field1",
                     |    "category[]": {
                     |      "id": "field2",
                     |      "name": "field3"
                     |    },
                     |    "name": "field4",
                     |    "photoUrls[]": "field5",
                     |    "tags[]": {
                     |      "id": "field6",
                     |      "name": "field7"
                     |    },
                     |    "status": "field8"
                     |  }
                     |}""".stripMargin)
    val expectedJson = json.parse(
      """[
        |    {
        |      "id":11,
        |      "category":{
        |        "id":12,
        |        "name":"field3-value-1"
        |      },
        |      "name":"field4-value-2",
        |      "photoUrls":"field5-value-3",
        |      "tags":{
        |        "id":16,
        |        "name":"field7-value-4"
        |      },
        |      "status":"field8-value-5"
        |    },
        |    {
        |      "id":21,
        |      "category":{
        |        "id":22,
        |        "name":"field3-value-2"
        |      },
        |      "name":"field4-value-2",
        |      "photoUrls":"field5-value-2",
        |      "tags":{
        |        "id":26,
        |        "name":"field7-value-2"
        |      },
        |      "status":"field8-value-2"
        |    }
        |  ]
        """.stripMargin)
   
    val resultJson = buildJson(requestJson, mapping)
    val str1 = json.prettyRender(resultJson)
    
    val str2 = json.prettyRender(expectedJson)
    str1 shouldEqual str2
  }

  "buildJson - Array " should "generate JValue according schema9" taggedAs JsonUtilsTag in {
      val requestJson = (
        """{
          |  "field5": "field5-value"
          |}
          |""".stripMargin)
      val mapping = ("""{
                       |  "photoUrls[]": "field5"
                       |}""".stripMargin)
      val expectedJson= json.parse("""{
                                     |  "photoUrls":[
                                     |    "field5-value"
                                     |  ]
                                     |}""".stripMargin)
      val resultJson = buildJson(requestJson, mapping)
    
      val str1 = json.prettyRender(resultJson)
      val str2 = json.prettyRender(expectedJson)
      str1 shouldEqual str2
  }
  
  "buildJson - get Array type json given index, when exists" should "get given item" taggedAs JsonUtilsTag in {
    //This Array contains different class structures, we can use the index to map the fields.
    val requestJson = json.parse(
      """
        |[
        | {"name": "Shuang", "age": 10},
        | {"name": "Sean", "age": 11},
        | ["running", "coding"]
        |]
        |""".stripMargin)

    val mapping = json.parse(
      """
        |{
        | "firstPerson": "[0]"
        | "secondName": "[1].name",
        | "secondHobby": "[2][1]",
        | "noPerson": "[3]",
        |}
        |""".stripMargin)
    
    val resultJson = buildJson(requestJson, mapping)

    val str1 = json.prettyRender(resultJson)
    

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

  "buildJson - list type fields" should "properly be convert" taggedAs JsonUtilsTag in {

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
    
    val resultJson = buildJson(jsonList, jsonListSchema)

    val str1 = json.prettyRender(resultJson)
    val str2 = json.prettyRender(expectListResult)
    str1 shouldEqual str2
  }

  "$root name field" should "properly be converted" taggedAs JsonUtilsTag in {
    val schema = json.parse(
      """
        |{
        | "$root":{
        |     "bankId":{
        |      "value":"id"
        |    },
        |    "shortName":"short_name",
        |    "fullName":"full_name"
        | }
        |}
        |""".stripMargin)
    val sourceValue = json.parse(
      """
        |{
        |    "id":"dmo.01.uk.uk",
        |    "short_name":"uk",
        |    "full_name":"uk"
        |}""".stripMargin)

    val expectedJson =
      """{
        |  "bankId":{
        |    "value":"dmo.01.uk.uk"
        |  },
        |  "shortName":"uk",
        |  "fullName":"uk"
        |}""".stripMargin
    val resultJson = buildJson(sourceValue, schema)

    val str1 = json.prettyRender(resultJson)

    str1 shouldEqual expectedJson

  }
  
  "$root[] name field and subField[][] type field" should "properly be converted" taggedAs JsonUtilsTag in {
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
    
    val schema = json.parse(
      """
        |{
        | "$root[]":{
        |     "bankId[]":{
        |      "value":"id"
        |    },
        |    "shortName[]":"shortName",
        |    "fullName[]":"fullName",
        |    "nationalIdentifier[][]":"swiftBic"
        | }
        |}
        |""".stripMargin)
    val expectedJson = json.parse(
      """
        |[
        |  {
        |    "bankId":{
        |      "value":"xrest-bank-x--uk"
        |    },
        |    "shortName":"bank shortName string",
        |    "fullName":"bank fullName x from rest connector",
        |    "nationalIdentifier":[
        |      "bank swiftBic string"
        |    ]
        |  },
        |  {
        |    "bankId":{
        |      "value":"xrest-bank-y--uk"
        |    },
        |    "shortName":"bank shortName y",
        |    "fullName":"bank fullName y  from rest connector",
        |    "nationalIdentifier":[
        |      "bank swiftBic string"
        |    ]
        |  }
        |]""".stripMargin)
    val resultJson = buildJson(jsonList, schema)
    val str1 = json.prettyRender(resultJson)
    val str2 = json.prettyRender(expectedJson)
    str1 shouldEqual str2

  }

  "buildJson - request is Array, mapping is []nest object" should "work well" taggedAs JsonUtilsTag in {
    val requestJson = (
      """[
        |  {
        |    "field1": 1
        |  },
        |  {
        |    "field1": 2
        |  }
        |]
        |""".stripMargin)
    val mapping = ("""{
                     |  "$root[]": {
                     |    "category": {
                     |      "id": "field1"
                     |    }
                     |  }
                     |}""".stripMargin)
    val expectedJson = json.parse(
      """[
        |  {
        |    "category":{
        |      "id":1
        |    }
        |  },
        |  {
        |    "category":{
        |      "id":2
        |    }
        |  }
        |]
        """.stripMargin)

    val resultJson = buildJson(requestJson, mapping)
    val str1 = json.prettyRender(resultJson)
    val str2 = json.prettyRender(expectedJson)
    str1 shouldEqual str2
  }
  
  "buildJson - request is Array, mapping is []object" should "work well" taggedAs JsonUtilsTag in {
    val requestJson = (
      """[
        |  {
        |    "field1": 1
        |  },
        |  {
        |    "field1": 2
        |  }
        |]
        |""".stripMargin)
    val mapping = ("""{
                     |  "$root[]": {
                     |    "category[][]":  "field1"
                     |  }
                     |}""".stripMargin)
    val expectedJson = json.parse(
      """[
        |  {
        |    "category":[1]
        |  },
        |  {
        |    "category":[2]
        |  }
        |]""".stripMargin)

    val resultJson = buildJson(requestJson, mapping)
    val str1 = json.prettyRender(resultJson)
    val str2 = json.prettyRender(expectedJson)
    str1 shouldEqual str2
  }
  
  "buildJson - request is Array1, mapping is []object" should "work well" taggedAs JsonUtilsTag in {
    val requestJson = (
      """[
        |  {
        |    "name": "family account200",
        |    "number": 200,
        |    "sample_entity_id": "9b344781-32f5-4afb-a4f1-2c93087e6e71"
        |  },
        |  {
        |    "name": "family account201",
        |    "number": 201,
        |    "sample_entity_id": "38ff936d-6780-444f-81b9-ac7ab8565035"
        |  }
        |]""".stripMargin)
    val mapping = ("""{
                     |  "$root[]": {
                     |    "name": "name",
                     |    "balance": "number"
                     |  }
                     |}""".stripMargin)
    val expectedJson = json.parse(
      """[
        |  {
        |    "name":"family account200",
        |    "balance":200,
        |  },
        |  {
        |    "name":"family account201",
        |    "balance":201
        |  }
        |]""".stripMargin)

    val resultJson = buildJson(requestJson, mapping)
    val str1 = json.prettyRender(resultJson)
    val str2 = json.prettyRender(expectedJson)
    str1 shouldEqual str2
  }

  "buildJson - request is JBool, mapping is {}" should "work well" taggedAs JsonUtilsTag in {
    val requestJson: JValue = JBool(true)
    val mapping = ("""{}""".stripMargin)
    val expectedJson = json.parse("""{}""".stripMargin)

    val resultJson = buildJson(requestJson, mapping)
    val str1 = json.prettyRender(resultJson)
    val str2 = json.prettyRender(expectedJson)
    str1 shouldEqual str2
  }
}
