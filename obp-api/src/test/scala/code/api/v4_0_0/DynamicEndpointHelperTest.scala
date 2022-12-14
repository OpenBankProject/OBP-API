package code.api.v4_0_0

import code.DynamicData.DynamicDataT
import code.api.dynamic.endpoint.helper.DynamicEndpointHelper
import code.api.util.APIUtil.defaultBankId
import code.connector.MockedCbsConnector.testBankId1
import net.liftweb.json
import net.liftweb.json.JsonAST.JValue
import net.liftweb.json.{Formats, JArray, prettyRender}
import org.scalatest.{FlatSpec, Matchers, Tag}

import scala.collection.immutable.List


class DynamicEndpointHelperTest extends FlatSpec with Matchers {
  object FunctionsTag extends Tag("DynamicEndpointHelper")
  implicit def formats: Formats = net.liftweb.json.DefaultFormats

  "prepareMappingFields single" should "work well" taggedAs FunctionsTag in {

    val orignalJson = """{
                        |  "operation_id": "OBPv4.0.0-dynamicEndpoint_GET_pet_PET_ID",
                        |  "request_mapping": {},
                        |  "response_mapping": {
                        |    "id": {
                        |      "entity": "PetEntity",
                        |      "field": "field1",
                        |      "query": "field1"
                        |    },
                        |    "category": {
                        |      "id": {
                        |        "entity": "PetEntity",
                        |        "field": "field2",
                        |        "query": "field1"
                        |      },
                        |      "name": {
                        |        "entity": "PetEntity",
                        |        "field": "field3",
                        |        "query": "field1"
                        |      }
                        |    },
                        |    "name": {
                        |      "entity": "PetEntity",
                        |      "field": "field4",
                        |      "query": "field1"
                        |    },
                        |    "photoUrls": [
                        |      {
                        |        "entity": "PetEntity",
                        |        "field": "field5",
                        |        "query": "field1"
                        |      }
                        |    ],
                        |    "tags": [
                        |      {
                        |        "id": {
                        |          "entity": "PetEntity",
                        |          "field": "field6",
                        |          "query": "field1"
                        |        },
                        |        "name": {
                        |          "entity": "PetEntity",
                        |          "field": "field7",
                        |          "query": "field1"
                        |        }
                        |      }
                        |    ],
                        |    "status": {
                        |      "entity": "PetEntity",
                        |      "field": "field8",
                        |      "query": "field1"
                        |    }
                        |  }
                        |}""".stripMargin
    val expectJson =
      """{
        |  "operation_id":"OBPv4.0.0-dynamicEndpoint_GET_pet_PET_ID",
        |  "request_mapping":{},
        |  "response_mapping":{
        |    "id":"field1",
        |    "category":{
        |      "id":"field2",
        |      "name":"field3"
        |    },
        |    "name":"field4",
        |    "photoUrls":[
        |      "field5"
        |    ],
        |    "tags":[
        |      {
        |        "id":"field6",
        |        "name":"field7"
        |      }
        |    ],
        |    "status":"field8"
        |  }
        |}""".stripMargin

    val expectJsonResult = prettyRender(DynamicEndpointHelper.prepareMappingFields(orignalJson))
    expectJson should equal(expectJsonResult)
  }
  "prepareMappingFields Array" should "work well" taggedAs FunctionsTag in {
    val orignalJson = """{
                        |  "operation_id": "OBPv4.0.0-dynamicEndpoint_GET_pet_PET_ID",
                        |  "request_mapping": {},
                        |  "response_mapping": {
                        |    "data[]": {
                        |      "id": {
                        |        "entity": "PetEntity",
                        |        "field": "field1",
                        |        "query": "field1"
                        |      },
                        |      "category[]": {
                        |        "id": {
                        |          "entity": "PetEntity",
                        |          "field": "field2",
                        |          "query": "field1"
                        |        },
                        |        "name": {
                        |          "entity": "PetEntity",
                        |          "field": "field3",
                        |          "query": "field1"
                        |        }
                        |      },
                        |      "name": {
                        |        "entity": "PetEntity",
                        |        "field": "field4",
                        |        "query": "field1"
                        |      },
                        |      "photoUrls[]": {
                        |        "entity": "PetEntity",
                        |        "field": "field5",
                        |        "query": "field1"
                        |      },
                        |      "tags[]": {
                        |        "id": {
                        |          "entity": "PetEntity",
                        |          "field": "field6",
                        |          "query": "field1"
                        |        },
                        |        "name": {
                        |          "entity": "PetEntity",
                        |          "field": "field7",
                        |          "query": "field1"
                        |        }
                        |      },
                        |      "status": {
                        |        "entity": "PetEntity",
                        |        "field": "field8",
                        |        "query": "field1"
                        |      }
                        |    }
                        |  }
                        |}""".stripMargin
    val expectJson =
      """{
        |  "operation_id":"OBPv4.0.0-dynamicEndpoint_GET_pet_PET_ID",
        |  "request_mapping":{},
        |  "response_mapping":{
        |    "data[]":{
        |      "id":"field1",
        |      "category[]":{
        |        "id":"field2",
        |        "name":"field3"
        |      },
        |      "name":"field4",
        |      "photoUrls[]":"field5",
        |      "tags[]":{
        |        "id":"field6",
        |        "name":"field7"
        |      },
        |      "status":"field8"
        |    }
        |  }
        |}""".stripMargin

    val resultJson = prettyRender(DynamicEndpointHelper.prepareMappingFields(orignalJson))
    println(resultJson)
    expectJson should equal(resultJson)
  }
  
  "getAllEntitiesFromMapping " should "work well" taggedAs FunctionsTag in {

    val orignalJson = """{
                        |  "operation_id": "OBPv4.0.0-dynamicEndpoint_GET_pet_PET_ID",
                        |  "request_mapping": {},
                        |  "response_mapping": {
                        |    "id": {
                        |      "entity": "PetEntity",
                        |      "field": "field1",
                        |      "query": "field1"
                        |    },
                        |    "category": {
                        |      "id": {
                        |        "entity": "PetEntity",
                        |        "field": "field2",
                        |        "query": "field1"
                        |      },
                        |      "name": {
                        |        "entity": "PetEntity",
                        |        "field": "field3",
                        |        "query": "field1"
                        |      }
                        |    },
                        |    "name": {
                        |      "entity": "PetEntity",
                        |      "field": "field4",
                        |      "query": "field1"
                        |    },
                        |    "photoUrls": [
                        |      {
                        |        "entity": "PetEntity",
                        |        "field": "field5",
                        |        "query": "field1"
                        |      }
                        |    ],
                        |    "tags": [
                        |      {
                        |        "id": {
                        |          "entity": "PetEntityTag",
                        |          "field": "field6",
                        |          "query": "field1"
                        |        },
                        |        "name": {
                        |          "entity": "PetEntity",
                        |          "field": "field7",
                        |          "query": "field1"
                        |        }
                        |      }
                        |    ],
                        |    "status": {
                        |      "entity": "PetEntityStatus",
                        |      "field": "field8",
                        |      "query": "field1"
                        |    }
                        |  }
                        |}""".stripMargin
    val expectJson = List("PetEntity","PetEntityTag","PetEntityStatus")

    val expectJsonResult = DynamicEndpointHelper.getAllEntitiesFromMappingJson(orignalJson)

    expectJson should equal(expectJsonResult)

  }

  "getEntityQueryIdsFromMapping " should "work well" taggedAs FunctionsTag in {

    val orignalJson = """{
                        |  "operation_id": "OBPv4.0.0-dynamicEndpoint_GET_pet_PET_ID",
                        |  "request_mapping": {},
                        |  "response_mapping": {
                        |    "id": {
                        |      "entity": "PetEntity",
                        |      "field": "field1",
                        |      "query": "field1"
                        |    },
                        |    "category": {
                        |      "id": {
                        |        "entity": "PetEntity",
                        |        "field": "field2",
                        |        "query": "field1"
                        |      },
                        |      "name": {
                        |        "entity": "PetEntity",
                        |        "field": "field3",
                        |        "query": "field1"
                        |      }
                        |    },
                        |    "name": {
                        |      "entity": "PetEntity",
                        |      "field": "field4",
                        |      "query": "field1"
                        |    },
                        |    "photoUrls": [
                        |      {
                        |        "entity": "PetEntity",
                        |        "field": "field5",
                        |        "query": "field1"
                        |      }
                        |    ],
                        |    "tags": [
                        |      {
                        |        "id": {
                        |          "entity": "PetEntityTag",
                        |          "field": "field6",
                        |          "query": "field1"
                        |        },
                        |        "name": {
                        |          "entity": "PetEntity",
                        |          "field": "field7",
                        |          "query": "field3"
                        |        }
                        |      }
                        |    ],
                        |    "status": {
                        |      "entity": "PetEntityStatus",
                        |      "field": "field8",
                        |      "query": "field2"
                        |    }
                        |  }
                        |}""".stripMargin
    val expectJson = List("field1","field3","field2")

    val expectJsonResult = DynamicEndpointHelper.getEntityQueryIdsFromMapping(orignalJson)

    expectJson should equal(expectJsonResult)

  }


  "getObjectByKeyValuePair " should "work well" taggedAs FunctionsTag in {

    val originalJson1 = """{
                          |  "field1": 1,
                          |  "field2": 2,
                          |  "field3": "field3-1",
                          |  "field4": "field4-1",
                          |  "field5": "field5-1",
                          |  "field6": 6,
                          |  "field7": "field7-1",
                          |  "field8": "field8-1",
                          |  "pet_entity_id": "bd1f083b-af72-42cf-8a70-21d7740f3861"
                          |}""".stripMargin
    val originalJson2 = """{
                          |  "field1": 2,
                          |  "field2": 22,
                          |  "field3": "field3-2",
                          |  "field4": "field4-2",
                          |  "field5": "field5-2",
                          |  "field6": 66,
                          |  "field7": "field7-2",
                          |  "field8": "field8-2",
                          |  "pet_entity_id": "33ca0384-5835-431e-9b88-4257f71f1482"
                          |}""".stripMargin
    val originalJson3 = """{
                          |  "field1": 3,
                          |  "field2": 222,
                          |  "field3": "field3-3",
                          |  "field4": "field4-3",
                          |  "field5": "field5-3",
                          |  "field6": 666,
                          |  "field7": "field7-3",
                          |  "field8": "field8-3",
                          |  "pet_entity_id": "33ca0384-5835-431e-9b88-4257f71f1483"
                          |}""".stripMargin


    val jValue1 = json.parse(originalJson1)
    val jValue2 = json.parse(originalJson2)
    val jValue3 = json.parse(originalJson3)
    val expectJson = jValue1

    val jArray = JArray(List(jValue1, jValue2, jValue3))

    val jsonResult = DynamicEndpointHelper.getObjectByKeyValuePair(jArray,"field1","1")

    val resultJson = prettyRender(jsonResult)
    println(resultJson)

    expectJson should equal(jsonResult)

  }

  "convertToMappingQueryParams one parameter" should "work well" taggedAs FunctionsTag in {

    val mapping = """{
                        |  "operation_id": "OBPv4.0.0-dynamicEndpoint_GET_pet_PET_ID",
                        |  "request_mapping": {},
                        |  "response_mapping": {
                        |    "id": {
                        |      "entity": "PetEntity",
                        |      "field": "field1",
                        |      "query": "field1"
                        |    },
                        |    "category": {
                        |      "id": {
                        |        "entity": "PetEntity",
                        |        "field": "field2",
                        |        "query": "field1"
                        |      },
                        |      "name": {
                        |        "entity": "PetEntity",
                        |        "field": "field3",
                        |        "query": "field1"
                        |      }
                        |    },
                        |    "name": {
                        |      "entity": "PetEntity",
                        |      "field": "field4",
                        |      "query": "field1"
                        |    },
                        |    "photoUrls": [
                        |      {
                        |        "entity": "PetEntity",
                        |        "field": "field5",
                        |        "query": "field1"
                        |      }
                        |    ],
                        |    "tags": [
                        |      {
                        |        "id": {
                        |          "entity": "PetEntityTag",
                        |          "field": "field6",
                        |          "query": "field1"
                        |        },
                        |        "name": {
                        |          "entity": "PetEntity",
                        |          "field": "field7",
                        |          "query": "field1"
                        |        }
                        |      }
                        |    ],
                        |    "status": {
                        |      "entity": "PetEntityStatus",
                        |      "field": "field8",
                        |      "query": "field1"
                        |    }
                        |  }
                        |}""".stripMargin
    val params = Map("status"->List("available"))

    val expectResult = Some(Map("field8"->List("available")))
    
    val result = DynamicEndpointHelper.convertToMappingQueryParams(mapping, params)

//    println(result)

    expectResult should equal(result)

  }
  "convertToMappingQueryParams wrong parameter" should "work well" taggedAs FunctionsTag in {

    val mapping = """{
                        |  "operation_id": "OBPv4.0.0-dynamicEndpoint_GET_pet_PET_ID",
                        |  "request_mapping": {},
                        |  "response_mapping": {
                        |    "id": {
                        |      "entity": "PetEntity",
                        |      "field": "field1",
                        |      "query": "field1"
                        |    },
                        |    "category": {
                        |      "id": {
                        |        "entity": "PetEntity",
                        |        "field": "field2",
                        |        "query": "field1"
                        |      },
                        |      "name": {
                        |        "entity": "PetEntity",
                        |        "field": "field3",
                        |        "query": "field1"
                        |      }
                        |    },
                        |    "name": {
                        |      "entity": "PetEntity",
                        |      "field": "field4",
                        |      "query": "field1"
                        |    },
                        |    "photoUrls": [
                        |      {
                        |        "entity": "PetEntity",
                        |        "field": "field5",
                        |        "query": "field1"
                        |      }
                        |    ],
                        |    "tags": [
                        |      {
                        |        "id": {
                        |          "entity": "PetEntityTag",
                        |          "field": "field6",
                        |          "query": "field1"
                        |        },
                        |        "name": {
                        |          "entity": "PetEntity",
                        |          "field": "field7",
                        |          "query": "field1"
                        |        }
                        |      }
                        |    ],
                        |    "status": {
                        |      "entity": "PetEntityStatus",
                        |      "field": "field8",
                        |      "query": "field1"
                        |    }
                        |  }
                        |}""".stripMargin
    val params = Map("status1"->List("available"))

    val expectResult = None
    
    val result = DynamicEndpointHelper.convertToMappingQueryParams(mapping, params)

//    println(result)

    expectResult should equal(result)

  }
  "convertToMappingQueryParams no parameters" should "work well" taggedAs FunctionsTag in {

    val mapping = """{
                    |  "operation_id": "OBPv4.0.0-dynamicEndpoint_GET_pet_PET_ID",
                    |  "request_mapping": {},
                    |  "response_mapping": {
                    |    "id": {
                    |      "entity": "PetEntity",
                    |      "field": "field1",
                    |      "query": "field1"
                    |    },
                    |    "category": {
                    |      "id": {
                    |        "entity": "PetEntity",
                    |        "field": "field2",
                    |        "query": "field1"
                    |      },
                    |      "name": {
                    |        "entity": "PetEntity",
                    |        "field": "field3",
                    |        "query": "field1"
                    |      }
                    |    },
                    |    "name": {
                    |      "entity": "PetEntity",
                    |      "field": "field4",
                    |      "query": "field1"
                    |    },
                    |    "photoUrls": [
                    |      {
                    |        "entity": "PetEntity",
                    |        "field": "field5",
                    |        "query": "field1"
                    |      }
                    |    ],
                    |    "tags": [
                    |      {
                    |        "id": {
                    |          "entity": "PetEntityTag",
                    |          "field": "field6",
                    |          "query": "field1"
                    |        },
                    |        "name": {
                    |          "entity": "PetEntity",
                    |          "field": "field7",
                    |          "query": "field1"
                    |        }
                    |      }
                    |    ],
                    |    "status": {
                    |      "entity": "PetEntityStatus",
                    |      "field": "field8",
                    |      "query": "field1"
                    |    }
                    |  }
                    |}""".stripMargin
    val params = Map.empty[String, List[String]]

    val expectResult = None

    val result = DynamicEndpointHelper.convertToMappingQueryParams(mapping, params)

    expectResult should equal(result)

  }
  "convertToMappingQueryParams more than one parameter" should "throw exception" taggedAs FunctionsTag in {

    val mapping = """{
                    |  "operation_id": "OBPv4.0.0-dynamicEndpoint_GET_pet_PET_ID",
                    |  "request_mapping": {},
                    |  "response_mapping": {
                    |    "id": {
                    |      "entity": "PetEntity",
                    |      "field": "field1",
                    |      "query": "field1"
                    |    },
                    |    "category": {
                    |      "id": {
                    |        "entity": "PetEntity",
                    |        "field": "field2",
                    |        "query": "field1"
                    |      },
                    |      "name": {
                    |        "entity": "PetEntity",
                    |        "field": "field3",
                    |        "query": "field1"
                    |      }
                    |    },
                    |    "name": {
                    |      "entity": "PetEntity",
                    |      "field": "field4",
                    |      "query": "field1"
                    |    },
                    |    "photoUrls": [
                    |      {
                    |        "entity": "PetEntity",
                    |        "field": "field5",
                    |        "query": "field1"
                    |      }
                    |    ],
                    |    "tags": [
                    |      {
                    |        "id": {
                    |          "entity": "PetEntityTag",
                    |          "field": "field6",
                    |          "query": "field1"
                    |        },
                    |        "name": {
                    |          "entity": "PetEntity",
                    |          "field": "field7",
                    |          "query": "field1"
                    |        }
                    |      }
                    |    ],
                    |    "status": {
                    |      "entity": "PetEntityStatus",
                    |      "field": "field8",
                    |      "query": "field1"
                    |    }
                    |  }
                    |}""".stripMargin
    val params = Map("status"->List("available"), "status2"->List("available2"))
    intercept[RuntimeException] {
      DynamicEndpointHelper.convertToMappingQueryParams(mapping, params)
    }
  }
  
  "getObjectsByKeyValuePair one param" should "work well" taggedAs FunctionsTag in {

    val originalJson1 = """{
                          |  "field1": 1,
                          |  "field2": 2,
                          |  "field3": "field3-1",
                          |  "field4": "field4-1",
                          |  "field5": "field5-1",
                          |  "field6": 6,
                          |  "field7": "field7-1",
                          |  "field8": "available",
                          |  "pet_entity_id": "bd1f083b-af72-42cf-8a70-21d7740f3861"
                          |}""".stripMargin
    val originalJson2 = """{
                          |  "field1": 2,
                          |  "field2": 22,
                          |  "field3": "field3-2",
                          |  "field4": "field4-2",
                          |  "field5": "field5-2",
                          |  "field6": 66,
                          |  "field7": "field7-2",
                          |  "field8": "field8-2",
                          |  "pet_entity_id": "33ca0384-5835-431e-9b88-4257f71f1482"
                          |}""".stripMargin
    val originalJson3 = """{
                          |  "field1": 3,
                          |  "field2": 222,
                          |  "field3": "field3-3",
                          |  "field4": "field4-3",
                          |  "field5": "field5-3",
                          |  "field6": 666,
                          |  "field7": "field7-3",
                          |  "field8": "available",
                          |  "pet_entity_id": "33ca0384-5835-431e-9b88-4257f71f1483"
                          |}""".stripMargin
    
    val jValue1 = json.parse(originalJson1)
    val jValue2 = json.parse(originalJson2)
    val jValue3 = json.parse(originalJson3)

    val jArray = JArray(List(jValue1, jValue2, jValue3))
    
    val params = Some(Map("field8"->List("available")))

    val result = DynamicEndpointHelper.getObjectsByParams(jArray, params)

    val str1 = prettyRender(result)
    println(str1)

    val expectString ="""[
                        |  {
                        |    "field1":1,
                        |    "field2":2,
                        |    "field3":"field3-1",
                        |    "field4":"field4-1",
                        |    "field5":"field5-1",
                        |    "field6":6,
                        |    "field7":"field7-1",
                        |    "field8":"available",
                        |    "pet_entity_id":"bd1f083b-af72-42cf-8a70-21d7740f3861"
                        |  },
                        |  {
                        |    "field1":3,
                        |    "field2":222,
                        |    "field3":"field3-3",
                        |    "field4":"field4-3",
                        |    "field5":"field5-3",
                        |    "field6":666,
                        |    "field7":"field7-3",
                        |    "field8":"available",
                        |    "pet_entity_id":"33ca0384-5835-431e-9b88-4257f71f1483"
                        |  }
                        |]""".stripMargin
    expectString should equal(str1)
  }
  "getObjectsByKeyValuePair no param" should "work well " taggedAs FunctionsTag in {

    val originalJson1 = """{
                          |  "field1": 1,
                          |  "field2": 2,
                          |  "field3": "field3-1",
                          |  "field4": "field4-1",
                          |  "field5": "field5-1",
                          |  "field6": 6,
                          |  "field7": "field7-1",
                          |  "field8": "available",
                          |  "pet_entity_id": "bd1f083b-af72-42cf-8a70-21d7740f3861"
                          |}""".stripMargin
    
    val jValue1 = json.parse(originalJson1)


    val jArray = JArray(List(jValue1))
    
    val params = None

    val result = DynamicEndpointHelper.getObjectsByParams(jArray,params)

    jArray should equal(result)
  }
  "getObjectsByKeyValuePair more than one param" should "throw exception" taggedAs FunctionsTag in {
    val originalJson1 = """{
                          |  "field1": 1,
                          |  "field2": 2,
                          |  "field3": "field3-1",
                          |  "field4": "field4-1",
                          |  "field5": "field5-1",
                          |  "field6": 6,
                          |  "field7": "field7-1",
                          |  "field8": "available",
                          |  "pet_entity_id": "bd1f083b-af72-42cf-8a70-21d7740f3861"
                          |}""".stripMargin


    val jValue1 = json.parse(originalJson1)


    val jArray = JArray(List(jValue1))

    val params = Some(Map("status"->List("available"), "status2"->List("available2")))
    intercept[RuntimeException] {DynamicEndpointHelper.getObjectsByParams(jArray,params)}
  }

  "getEntityNameKeyAndValue with proper input" should "work well" taggedAs FunctionsTag in {
    val mapping = """{
                          |"id": {
                          |    "entity": "PetEntity",
                          |    "field": "field1",
                          |    "query": "field1"
                          |}
                          |}""".stripMargin



    val params = Map("id"->"1")
    
    val expectedResult = ("PetEntity","field1",Some("1"))
    val result = DynamicEndpointHelper.getEntityNameKeyAndValue(mapping, params)
    
    result should equal(expectedResult)
  }

  "findDynamicData with proper input" should "work well" taggedAs FunctionsTag in {

    case class DataTest (
      dynamicDataId: Option[String],
      dynamicEntityName: String,
      dataJson: String,
      bankId: Option[String],
      userId: Option[String],
      isPersonalEntity: Boolean
    )extends DynamicDataT
    
    val dataJsonString = """{
                           |"id": {
                           |    "entity": "PetEntity",
                           |    "field": "field1",
                           |    "query": "field1"
                           |}
                           |}""".stripMargin
    val dataJsonString2 = """{
                           |"id": {
                           |    "entity": "PetEntity",
                           |    "field": "field2",
                           |    "query": "field2"
                           |}
                           |}""".stripMargin
    val dynamicDataJson = json.parse(dataJsonString)
    val dynamicDataList = List(DataTest(Some("1"),"PetEntity",dataJsonString, Some(testBankId1.value), None, false), DataTest(Some("2"),"PetEntity2",dataJsonString2, Some(testBankId1.value), None, false))


    val expectedResult = ("PetEntity", "1")
    val result: (String, String) = DynamicEndpointHelper.findDynamicData(dynamicDataList: List[DynamicDataT], dynamicDataJson: JValue)

    result should equal(expectedResult)
  }


  "addedBankToPath with one path swagger input" should "work well" taggedAs FunctionsTag in {

    val dataJsonString = """{
                           |  "swagger": "2.0",
                           |  "info": {
                           |    "description": "Entity Data Test bank Level",
                           |    "version": "1.0.0",
                           |    "title": "Entity Data Test bank Level"
                           |  },
                           |  "host": "obp_mock",
                           |  "basePath": "/obp/v4.0.0/dynamic",
                           |  "schemes": [
                           |    "https",
                           |    "http"
                           |  ],
                           |  "paths": {
                           |    "/fashion-brand-list": {
                           |      "get": {
                           |        "tags": [
                           |          "Dynamic Entity Data POC"
                           |        ],
                           |        "summary": "Get Brand  bank Level",
                           |        "description": "Get Brand  bank Level",
                           |        "produces": [
                           |          "application/json"
                           |        ],
                           |        "responses": {
                           |          "200": {
                           |            "description": "Success Response",
                           |            "schema": {
                           |              "$ref": "#/definitions/FashionBrandNames"
                           |            }
                           |          }
                           |        }
                           |      }
                           |    }
                           |  },
                           |  "definitions": {
                           |    "FashionBrandNames": {
                           |      "type": "object",
                           |      "properties": {
                           |        "name": {
                           |          "type": "string"
                           |        }
                           |      }
                           |    }
                           |  }
                           |}""".stripMargin
    val bankId = Some("gh.29.uk")

    val expectedJsonString = """{
                               |  "swagger": "2.0",
                               |  "info": {
                               |    "description": "Entity Data Test bank Level",
                               |    "version": "1.0.0",
                               |    "title": "Entity Data Test bank Level"
                               |  },
                               |  "host": "obp_mock",
                               |  "basePath": "/obp/v4.0.0/dynamic",
                               |  "schemes": [
                               |    "https",
                               |    "http"
                               |  ],
                               |  "paths": {
                               |    "/banks/gh.29.uk/fashion-brand-list": {
                               |      "get": {
                               |        "tags": [
                               |          "Dynamic Entity Data POC"
                               |        ],
                               |        "summary": "Get Brand  bank Level",
                               |        "description": "Get Brand  bank Level",
                               |        "produces": [
                               |          "application/json"
                               |        ],
                               |        "responses": {
                               |          "200": {
                               |            "description": "Success Response",
                               |            "schema": {
                               |              "$ref": "#/definitions/FashionBrandNames"
                               |            }
                               |          }
                               |        }
                               |      }
                               |    }
                               |  },
                               |  "definitions": {
                               |    "FashionBrandNames": {
                               |      "type": "object",
                               |      "properties": {
                               |        "name": {
                               |          "type": "string"
                               |        }
                               |      }
                               |    }
                               |  }
                               |}""".stripMargin
    
    val expectedJson = json.parse(expectedJsonString)
    
    val result= DynamicEndpointHelper.addedBankToPath(dataJsonString, bankId)

    expectedJson should equal(result)
  }

  "addedBankToPath with multiple paths swagger input" should "work well" taggedAs FunctionsTag in {

    val dataJsonString = """{
                           |  "swagger": "2.0",
                           |  "info": {
                           |    "description": "This is a sample server Petstore server.  You can find out more about Swagger at [http://swagger.io](http://swagger.io) or on [irc.freenode.net, #swagger](http://swagger.io/irc/).  For this sample, you can use the api key `special-key` to test the authorization filters.",
                           |    "version": "1.0.5",
                           |    "title": "Swagger Petstore",
                           |    "termsOfService": "http://swagger.io/terms/",
                           |    "contact": {
                           |      "email": "apiteam@swagger.io"
                           |    },
                           |    "license": {
                           |      "name": "Apache 2.0",
                           |      "url": "http://www.apache.org/licenses/LICENSE-2.0.html"
                           |    }
                           |  },
                           |  "host": "obp_mock",
                           |  "host": "DynamicEntity",
                           |  
                           |  "basePath": "/v2",
                           |  "tags": [
                           |    {
                           |      "name": "pet",
                           |      "description": "Everything about your Pets",
                           |      "externalDocs": {
                           |        "description": "Find out more",
                           |        "url": "http://swagger.io"
                           |      }
                           |    },
                           |    {
                           |      "name": "store",
                           |      "description": "Access to Petstore orders"
                           |    },
                           |    {
                           |      "name": "user",
                           |      "description": "Operations about user",
                           |      "externalDocs": {
                           |        "description": "Find out more about our store",
                           |        "url": "http://swagger.io"
                           |      }
                           |    }
                           |  ],
                           |  "schemes": [
                           |    "https",
                           |    "http"
                           |  ],
                           |  "paths": {
                           |    "/pet/{petId}/uploadImage": {
                           |      "post": {
                           |        "tags": [
                           |          "pet"
                           |        ],
                           |        "summary": "uploads an image",
                           |        "description": "",
                           |        "operationId": "uploadFile",
                           |        "consumes": [
                           |          "multipart/form-data"
                           |        ],
                           |        "produces": [
                           |          "application/json"
                           |        ],
                           |        "parameters": [
                           |          {
                           |            "name": "petId",
                           |            "in": "path",
                           |            "description": "ID of pet to update",
                           |            "required": true,
                           |            "type": "integer",
                           |            "format": "int64"
                           |          },
                           |          {
                           |            "name": "additionalMetadata",
                           |            "in": "formData",
                           |            "description": "Additional data to pass to server",
                           |            "required": false,
                           |            "type": "string"
                           |          },
                           |          {
                           |            "name": "file",
                           |            "in": "formData",
                           |            "description": "file to upload",
                           |            "required": false,
                           |            "type": "file"
                           |          }
                           |        ],
                           |        "responses": {
                           |          "200": {
                           |            "description": "successful operation",
                           |            "schema": {
                           |              "$ref": "#/definitions/ApiResponse"
                           |            }
                           |          }
                           |        },
                           |        "security": [
                           |          {
                           |            "petstore_auth": [
                           |              "write:pets",
                           |              "read:pets"
                           |            ]
                           |          }
                           |        ]
                           |      }
                           |    },
                           |    "/pet": {
                           |      "post": {
                           |        "tags": [
                           |          "pet"
                           |        ],
                           |        "summary": "Add a new pet to the store",
                           |        "description": "",
                           |        "operationId": "addPet",
                           |        "consumes": [
                           |          "application/json",
                           |          "application/xml"
                           |        ],
                           |        "produces": [
                           |          "application/json",
                           |          "application/xml"
                           |        ],
                           |        "parameters": [
                           |          {
                           |            "in": "body",
                           |            "name": "body",
                           |            "description": "Pet object that needs to be added to the store",
                           |            "required": true,
                           |            "schema": {
                           |              "$ref": "#/definitions/Pet"
                           |            }
                           |          }
                           |        ],
                           |        "responses": {
                           |          "405": {
                           |            "description": "Invalid input"
                           |          }
                           |        },
                           |        "security": [
                           |          {
                           |            "petstore_auth": [
                           |              "write:pets",
                           |              "read:pets"
                           |            ]
                           |          }
                           |        ]
                           |      },
                           |      "put": {
                           |        "tags": [
                           |          "pet"
                           |        ],
                           |        "summary": "Update an existing pet",
                           |        "description": "",
                           |        "operationId": "updatePet",
                           |        "consumes": [
                           |          "application/json",
                           |          "application/xml"
                           |        ],
                           |        "produces": [
                           |          "application/json",
                           |          "application/xml"
                           |        ],
                           |        "parameters": [
                           |          {
                           |            "in": "body",
                           |            "name": "body",
                           |            "description": "Pet object that needs to be added to the store",
                           |            "required": true,
                           |            "schema": {
                           |              "$ref": "#/definitions/Pet"
                           |            }
                           |          }
                           |        ],
                           |        "responses": {
                           |          "400": {
                           |            "description": "Invalid ID supplied"
                           |          },
                           |          "404": {
                           |            "description": "Pet not found"
                           |          },
                           |          "405": {
                           |            "description": "Validation exception"
                           |          }
                           |        },
                           |        "security": [
                           |          {
                           |            "petstore_auth": [
                           |              "write:pets",
                           |              "read:pets"
                           |            ]
                           |          }
                           |        ]
                           |      }
                           |    },
                           |    "/pet/findByStatus": {
                           |      "get": {
                           |        "tags": [
                           |          "pet"
                           |        ],
                           |        "summary": "Finds Pets by status",
                           |        "description": "Multiple status values can be provided with comma separated strings",
                           |        "operationId": "findPetsByStatus",
                           |        "produces": [
                           |          "application/json",
                           |          "application/xml"
                           |        ],
                           |        "parameters": [
                           |          {
                           |            "name": "status",
                           |            "in": "query",
                           |            "description": "Status values that need to be considered for filter",
                           |            "required": true,
                           |            "type": "array",
                           |            "items": {
                           |              "type": "string",
                           |              "enum": [
                           |                "available",
                           |                "pending",
                           |                "sold"
                           |              ],
                           |              "default": "available"
                           |            },
                           |            "collectionFormat": "multi"
                           |          }
                           |        ],
                           |        "responses": {
                           |          "200": {
                           |            "description": "successful operation",
                           |            "schema": {
                           |              "type": "array",
                           |              "items": {
                           |                "$ref": "#/definitions/Pet"
                           |              }
                           |            }
                           |          },
                           |          "400": {
                           |            "description": "Invalid status value"
                           |          }
                           |        },
                           |        "security": [
                           |          {
                           |            "petstore_auth": [
                           |              "write:pets",
                           |              "read:pets"
                           |            ]
                           |          }
                           |        ]
                           |      }
                           |    },
                           |    "/pet/findByTags": {
                           |      "get": {
                           |        "tags": [
                           |          "pet"
                           |        ],
                           |        "summary": "Finds Pets by tags",
                           |        "description": "Multiple tags can be provided with comma separated strings. Use tag1, tag2, tag3 for testing.",
                           |        "operationId": "findPetsByTags",
                           |        "produces": [
                           |          "application/json",
                           |          "application/xml"
                           |        ],
                           |        "parameters": [
                           |          {
                           |            "name": "tags",
                           |            "in": "query",
                           |            "description": "Tags to filter by",
                           |            "required": true,
                           |            "type": "array",
                           |            "items": {
                           |              "type": "string"
                           |            },
                           |            "collectionFormat": "multi"
                           |          }
                           |        ],
                           |        "responses": {
                           |          "200": {
                           |            "description": "successful operation",
                           |            "schema": {
                           |              "type": "array",
                           |              "items": {
                           |                "$ref": "#/definitions/Pet"
                           |              }
                           |            }
                           |          },
                           |          "400": {
                           |            "description": "Invalid tag value"
                           |          }
                           |        },
                           |        "security": [
                           |          {
                           |            "petstore_auth": [
                           |              "write:pets",
                           |              "read:pets"
                           |            ]
                           |          }
                           |        ],
                           |        "deprecated": true
                           |      }
                           |    },
                           |    "/pet/{petId}": {
                           |      "get": {
                           |        "tags": [
                           |          "pet"
                           |        ],
                           |        "summary": "Find pet by ID",
                           |        "description": "Returns a single pet",
                           |        "operationId": "getPetById",
                           |        "produces": [
                           |          "application/json",
                           |          "application/xml"
                           |        ],
                           |        "parameters": [
                           |          {
                           |            "name": "petId",
                           |            "in": "path",
                           |            "description": "ID of pet to return",
                           |            "required": true,
                           |            "type": "integer",
                           |            "format": "int64"
                           |          }
                           |        ],
                           |        "responses": {
                           |          "200": {
                           |            "description": "successful operation",
                           |            "schema": {
                           |              "$ref": "#/definitions/Pet"
                           |            }
                           |          },
                           |          "400": {
                           |            "description": "Invalid ID supplied"
                           |          },
                           |          "404": {
                           |            "description": "Pet not found"
                           |          }
                           |        },
                           |        "security": [
                           |          {
                           |            "api_key": []
                           |          }
                           |        ]
                           |      },
                           |      "post": {
                           |        "tags": [
                           |          "pet"
                           |        ],
                           |        "summary": "Updates a pet in the store with form data",
                           |        "description": "",
                           |        "operationId": "updatePetWithForm",
                           |        "consumes": [
                           |          "application/x-www-form-urlencoded"
                           |        ],
                           |        "produces": [
                           |          "application/json",
                           |          "application/xml"
                           |        ],
                           |        "parameters": [
                           |          {
                           |            "name": "petId",
                           |            "in": "path",
                           |            "description": "ID of pet that needs to be updated",
                           |            "required": true,
                           |            "type": "integer",
                           |            "format": "int64"
                           |          },
                           |          {
                           |            "name": "name",
                           |            "in": "formData",
                           |            "description": "Updated name of the pet",
                           |            "required": false,
                           |            "type": "string"
                           |          },
                           |          {
                           |            "name": "status",
                           |            "in": "formData",
                           |            "description": "Updated status of the pet",
                           |            "required": false,
                           |            "type": "string"
                           |          }
                           |        ],
                           |        "responses": {
                           |          "405": {
                           |            "description": "Invalid input"
                           |          }
                           |        },
                           |        "security": [
                           |          {
                           |            "petstore_auth": [
                           |              "write:pets",
                           |              "read:pets"
                           |            ]
                           |          }
                           |        ]
                           |      },
                           |      "delete": {
                           |        "tags": [
                           |          "pet"
                           |        ],
                           |        "summary": "Deletes a pet",
                           |        "description": "",
                           |        "operationId": "deletePet",
                           |        "produces": [
                           |          "application/json",
                           |          "application/xml"
                           |        ],
                           |        "parameters": [
                           |          {
                           |            "name": "api_key",
                           |            "in": "header",
                           |            "required": false,
                           |            "type": "string"
                           |          },
                           |          {
                           |            "name": "petId",
                           |            "in": "path",
                           |            "description": "Pet id to delete",
                           |            "required": true,
                           |            "type": "integer",
                           |            "format": "int64"
                           |          }
                           |        ],
                           |        "responses": {
                           |          "400": {
                           |            "description": "Invalid ID supplied"
                           |          },
                           |          "404": {
                           |            "description": "Pet not found"
                           |          }
                           |        },
                           |        "security": [
                           |          {
                           |            "petstore_auth": [
                           |              "write:pets",
                           |              "read:pets"
                           |            ]
                           |          }
                           |        ]
                           |      }
                           |    },
                           |    "/store/order": {
                           |      "post": {
                           |        "tags": [
                           |          "store"
                           |        ],
                           |        "summary": "Place an order for a pet",
                           |        "description": "",
                           |        "operationId": "placeOrder",
                           |        "consumes": [
                           |          "application/json"
                           |        ],
                           |        "produces": [
                           |          "application/json",
                           |          "application/xml"
                           |        ],
                           |        "parameters": [
                           |          {
                           |            "in": "body",
                           |            "name": "body",
                           |            "description": "order placed for purchasing the pet",
                           |            "required": true,
                           |            "schema": {
                           |              "$ref": "#/definitions/Order"
                           |            }
                           |          }
                           |        ],
                           |        "responses": {
                           |          "200": {
                           |            "description": "successful operation",
                           |            "schema": {
                           |              "$ref": "#/definitions/Order"
                           |            }
                           |          },
                           |          "400": {
                           |            "description": "Invalid Order"
                           |          }
                           |        }
                           |      }
                           |    },
                           |    "/store/order/{orderId}": {
                           |      "get": {
                           |        "tags": [
                           |          "store"
                           |        ],
                           |        "summary": "Find purchase order by ID",
                           |        "description": "For valid response try integer IDs with value >= 1 and <= 10. Other values will generated exceptions",
                           |        "operationId": "getOrderById",
                           |        "produces": [
                           |          "application/json",
                           |          "application/xml"
                           |        ],
                           |        "parameters": [
                           |          {
                           |            "name": "orderId",
                           |            "in": "path",
                           |            "description": "ID of pet that needs to be fetched",
                           |            "required": true,
                           |            "type": "integer",
                           |            "maximum": 10,
                           |            "minimum": 1,
                           |            "format": "int64"
                           |          }
                           |        ],
                           |        "responses": {
                           |          "200": {
                           |            "description": "successful operation",
                           |            "schema": {
                           |              "$ref": "#/definitions/Order"
                           |            }
                           |          },
                           |          "400": {
                           |            "description": "Invalid ID supplied"
                           |          },
                           |          "404": {
                           |            "description": "Order not found"
                           |          }
                           |        }
                           |      },
                           |      "delete": {
                           |        "tags": [
                           |          "store"
                           |        ],
                           |        "summary": "Delete purchase order by ID",
                           |        "description": "For valid response try integer IDs with positive integer value. Negative or non-integer values will generate API errors",
                           |        "operationId": "deleteOrder",
                           |        "produces": [
                           |          "application/json",
                           |          "application/xml"
                           |        ],
                           |        "parameters": [
                           |          {
                           |            "name": "orderId",
                           |            "in": "path",
                           |            "description": "ID of the order that needs to be deleted",
                           |            "required": true,
                           |            "type": "integer",
                           |            "minimum": 1,
                           |            "format": "int64"
                           |          }
                           |        ],
                           |        "responses": {
                           |          "400": {
                           |            "description": "Invalid ID supplied"
                           |          },
                           |          "404": {
                           |            "description": "Order not found"
                           |          }
                           |        }
                           |      }
                           |    },
                           |    "/store/inventory": {
                           |      "get": {
                           |        "tags": [
                           |          "store"
                           |        ],
                           |        "summary": "Returns pet inventories by status",
                           |        "description": "Returns a map of status codes to quantities",
                           |        "operationId": "getInventory",
                           |        "produces": [
                           |          "application/json"
                           |        ],
                           |        "parameters": [],
                           |        "responses": {
                           |          "200": {
                           |            "description": "successful operation",
                           |            "schema": {
                           |              "type": "object",
                           |              "additionalProperties": {
                           |                "type": "integer",
                           |                "format": "int32"
                           |              }
                           |            }
                           |          }
                           |        },
                           |        "security": [
                           |          {
                           |            "api_key": []
                           |          }
                           |        ]
                           |      }
                           |    },
                           |    "/user/createWithArray": {
                           |      "post": {
                           |        "tags": [
                           |          "user"
                           |        ],
                           |        "summary": "Creates list of users with given input array",
                           |        "description": "",
                           |        "operationId": "createUsersWithArrayInput",
                           |        "consumes": [
                           |          "application/json"
                           |        ],
                           |        "produces": [
                           |          "application/json",
                           |          "application/xml"
                           |        ],
                           |        "parameters": [
                           |          {
                           |            "in": "body",
                           |            "name": "body",
                           |            "description": "List of user object",
                           |            "required": true,
                           |            "schema": {
                           |              "type": "array",
                           |              "items": {
                           |                "$ref": "#/definitions/User"
                           |              }
                           |            }
                           |          }
                           |        ],
                           |        "responses": {
                           |          "default": {
                           |            "description": "successful operation"
                           |          }
                           |        }
                           |      }
                           |    },
                           |    "/user/createWithList": {
                           |      "post": {
                           |        "tags": [
                           |          "user"
                           |        ],
                           |        "summary": "Creates list of users with given input array",
                           |        "description": "",
                           |        "operationId": "createUsersWithListInput",
                           |        "consumes": [
                           |          "application/json"
                           |        ],
                           |        "produces": [
                           |          "application/json",
                           |          "application/xml"
                           |        ],
                           |        "parameters": [
                           |          {
                           |            "in": "body",
                           |            "name": "body",
                           |            "description": "List of user object",
                           |            "required": true,
                           |            "schema": {
                           |              "type": "array",
                           |              "items": {
                           |                "$ref": "#/definitions/User"
                           |              }
                           |            }
                           |          }
                           |        ],
                           |        "responses": {
                           |          "default": {
                           |            "description": "successful operation"
                           |          }
                           |        }
                           |      }
                           |    },
                           |    "/user/{username}": {
                           |      "get": {
                           |        "tags": [
                           |          "user"
                           |        ],
                           |        "summary": "Get user by user name",
                           |        "description": "",
                           |        "operationId": "getUserByName",
                           |        "produces": [
                           |          "application/json",
                           |          "application/xml"
                           |        ],
                           |        "parameters": [
                           |          {
                           |            "name": "username",
                           |            "in": "path",
                           |            "description": "The name that needs to be fetched. Use user1 for testing. ",
                           |            "required": true,
                           |            "type": "string"
                           |          }
                           |        ],
                           |        "responses": {
                           |          "200": {
                           |            "description": "successful operation",
                           |            "schema": {
                           |              "$ref": "#/definitions/User"
                           |            }
                           |          },
                           |          "400": {
                           |            "description": "Invalid username supplied"
                           |          },
                           |          "404": {
                           |            "description": "User not found"
                           |          }
                           |        }
                           |      },
                           |      "put": {
                           |        "tags": [
                           |          "user"
                           |        ],
                           |        "summary": "Updated user",
                           |        "description": "This can only be done by the logged in user.",
                           |        "operationId": "updateUser",
                           |        "consumes": [
                           |          "application/json"
                           |        ],
                           |        "produces": [
                           |          "application/json",
                           |          "application/xml"
                           |        ],
                           |        "parameters": [
                           |          {
                           |            "name": "username",
                           |            "in": "path",
                           |            "description": "name that need to be updated",
                           |            "required": true,
                           |            "type": "string"
                           |          },
                           |          {
                           |            "in": "body",
                           |            "name": "body",
                           |            "description": "Updated user object",
                           |            "required": true,
                           |            "schema": {
                           |              "$ref": "#/definitions/User"
                           |            }
                           |          }
                           |        ],
                           |        "responses": {
                           |          "400": {
                           |            "description": "Invalid user supplied"
                           |          },
                           |          "404": {
                           |            "description": "User not found"
                           |          }
                           |        }
                           |      },
                           |      "delete": {
                           |        "tags": [
                           |          "user"
                           |        ],
                           |        "summary": "Delete user",
                           |        "description": "This can only be done by the logged in user.",
                           |        "operationId": "deleteUser",
                           |        "produces": [
                           |          "application/json",
                           |          "application/xml"
                           |        ],
                           |        "parameters": [
                           |          {
                           |            "name": "username",
                           |            "in": "path",
                           |            "description": "The name that needs to be deleted",
                           |            "required": true,
                           |            "type": "string"
                           |          }
                           |        ],
                           |        "responses": {
                           |          "400": {
                           |            "description": "Invalid username supplied"
                           |          },
                           |          "404": {
                           |            "description": "User not found"
                           |          }
                           |        }
                           |      }
                           |    },
                           |    "/user/login": {
                           |      "get": {
                           |        "tags": [
                           |          "user"
                           |        ],
                           |        "summary": "Logs user into the system",
                           |        "description": "",
                           |        "operationId": "loginUser",
                           |        "produces": [
                           |          "application/json",
                           |          "application/xml"
                           |        ],
                           |        "parameters": [
                           |          {
                           |            "name": "username",
                           |            "in": "query",
                           |            "description": "The user name for login",
                           |            "required": true,
                           |            "type": "string"
                           |          },
                           |          {
                           |            "name": "password",
                           |            "in": "query",
                           |            "description": "The password for login in clear text",
                           |            "required": true,
                           |            "type": "string"
                           |          }
                           |        ],
                           |        "responses": {
                           |          "200": {
                           |            "description": "successful operation",
                           |            "headers": {
                           |              "X-Expires-After": {
                           |                "type": "string",
                           |                "format": "date-time",
                           |                "description": "date in UTC when token expires"
                           |              },
                           |              "X-Rate-Limit": {
                           |                "type": "integer",
                           |                "format": "int32",
                           |                "description": "calls per hour allowed by the user"
                           |              }
                           |            },
                           |            "schema": {
                           |              "type": "string"
                           |            }
                           |          },
                           |          "400": {
                           |            "description": "Invalid username/password supplied"
                           |          }
                           |        }
                           |      }
                           |    },
                           |    "/user/logout": {
                           |      "get": {
                           |        "tags": [
                           |          "user"
                           |        ],
                           |        "summary": "Logs out current logged in user session",
                           |        "description": "",
                           |        "operationId": "logoutUser",
                           |        "produces": [
                           |          "application/json",
                           |          "application/xml"
                           |        ],
                           |        "parameters": [],
                           |        "responses": {
                           |          "default": {
                           |            "description": "successful operation"
                           |          }
                           |        }
                           |      }
                           |    },
                           |    "/user": {
                           |      "post": {
                           |        "tags": [
                           |          "user"
                           |        ],
                           |        "summary": "Create user",
                           |        "description": "This can only be done by the logged in user.",
                           |        "operationId": "createUser",
                           |        "consumes": [
                           |          "application/json"
                           |        ],
                           |        "produces": [
                           |          "application/json",
                           |          "application/xml"
                           |        ],
                           |        "parameters": [
                           |          {
                           |            "in": "body",
                           |            "name": "body",
                           |            "description": "Created user object",
                           |            "required": true,
                           |            "schema": {
                           |              "$ref": "#/definitions/User"
                           |            }
                           |          }
                           |        ],
                           |        "responses": {
                           |          "default": {
                           |            "description": "successful operation"
                           |          }
                           |        }
                           |      }
                           |    }
                           |  },
                           |  "securityDefinitions": {
                           |    "api_key": {
                           |      "type": "apiKey",
                           |      "name": "api_key",
                           |      "in": "header"
                           |    },
                           |    "petstore_auth": {
                           |      "type": "oauth2",
                           |      "authorizationUrl": "https://petstore.swagger.io/oauth/authorize",
                           |      "flow": "implicit",
                           |      "scopes": {
                           |        "read:pets": "read your pets",
                           |        "write:pets": "modify pets in your account"
                           |      }
                           |    }
                           |  },
                           |  "definitions": {
                           |    "ApiResponse": {
                           |      "type": "object",
                           |      "properties": {
                           |        "code": {
                           |          "type": "integer",
                           |          "format": "int32"
                           |        },
                           |        "type": {
                           |          "type": "string"
                           |        },
                           |        "message": {
                           |          "type": "string"
                           |        }
                           |      }
                           |    },
                           |    "Category": {
                           |      "type": "object",
                           |      "properties": {
                           |        "id": {
                           |          "type": "integer",
                           |          "format": "int64"
                           |        },
                           |        "name": {
                           |          "type": "string"
                           |        }
                           |      },
                           |      "xml": {
                           |        "name": "Category"
                           |      }
                           |    },
                           |    "Pet": {
                           |      "type": "object",
                           |      "required": [
                           |        "name",
                           |        "photoUrls"
                           |      ],
                           |      "properties": {
                           |        "id": {
                           |          "type": "integer",
                           |          "format": "int64"
                           |        },
                           |        "category": {
                           |          "$ref": "#/definitions/Category"
                           |        },
                           |        "name": {
                           |          "type": "string",
                           |          "example": "doggie"
                           |        },
                           |        "photoUrls": {
                           |          "type": "array",
                           |          "xml": {
                           |            "wrapped": true
                           |          },
                           |          "items": {
                           |            "type": "string",
                           |            "xml": {
                           |              "name": "photoUrl"
                           |            }
                           |          }
                           |        },
                           |        "tags": {
                           |          "type": "array",
                           |          "xml": {
                           |            "wrapped": true
                           |          },
                           |          "items": {
                           |            "xml": {
                           |              "name": "tag"
                           |            },
                           |            "$ref": "#/definitions/Tag"
                           |          }
                           |        },
                           |        "status": {
                           |          "type": "string",
                           |          "description": "pet status in the store",
                           |          "enum": [
                           |            "available",
                           |            "pending",
                           |            "sold"
                           |          ]
                           |        }
                           |      },
                           |      "xml": {
                           |        "name": "Pet"
                           |      }
                           |    },
                           |    "Tag": {
                           |      "type": "object",
                           |      "properties": {
                           |        "id": {
                           |          "type": "integer",
                           |          "format": "int64"
                           |        },
                           |        "name": {
                           |          "type": "string"
                           |        }
                           |      },
                           |      "xml": {
                           |        "name": "Tag"
                           |      }
                           |    },
                           |    "Order": {
                           |      "type": "object",
                           |      "properties": {
                           |        "id": {
                           |          "type": "integer",
                           |          "format": "int64"
                           |        },
                           |        "petId": {
                           |          "type": "integer",
                           |          "format": "int64"
                           |        },
                           |        "quantity": {
                           |          "type": "integer",
                           |          "format": "int32"
                           |        },
                           |        "shipDate": {
                           |          "type": "string",
                           |          "format": "date-time"
                           |        },
                           |        "status": {
                           |          "type": "string",
                           |          "description": "Order Status",
                           |          "enum": [
                           |            "placed",
                           |            "approved",
                           |            "delivered"
                           |          ]
                           |        },
                           |        "complete": {
                           |          "type": "boolean"
                           |        }
                           |      },
                           |      "xml": {
                           |        "name": "Order"
                           |      }
                           |    },
                           |    "User": {
                           |      "type": "object",
                           |      "properties": {
                           |        "id": {
                           |          "type": "integer",
                           |          "format": "int64"
                           |        },
                           |        "username": {
                           |          "type": "string"
                           |        },
                           |        "firstName": {
                           |          "type": "string"
                           |        },
                           |        "lastName": {
                           |          "type": "string"
                           |        },
                           |        "email": {
                           |          "type": "string"
                           |        },
                           |        "password": {
                           |          "type": "string"
                           |        },
                           |        "phone": {
                           |          "type": "string"
                           |        },
                           |        "userStatus": {
                           |          "type": "integer",
                           |          "format": "int32",
                           |          "description": "User Status"
                           |        }
                           |      },
                           |      "xml": {
                           |        "name": "User"
                           |      }
                           |    }
                           |  },
                           |  "externalDocs": {
                           |    "description": "Find out more about Swagger",
                           |    "url": "http://swagger.io"
                           |  }
                           |}""".stripMargin
    val bankId = Some("gh.29.uk")

    val expectedJsonString = """{
                               |  "swagger": "2.0",
                               |  "info": {
                               |    "description": "This is a sample server Petstore server.  You can find out more about Swagger at [http://swagger.io](http://swagger.io) or on [irc.freenode.net, #swagger](http://swagger.io/irc/).  For this sample, you can use the api key `special-key` to test the authorization filters.",
                               |    "version": "1.0.5",
                               |    "title": "Swagger Petstore",
                               |    "termsOfService": "http://swagger.io/terms/",
                               |    "contact": {
                               |      "email": "apiteam@swagger.io"
                               |    },
                               |    "license": {
                               |      "name": "Apache 2.0",
                               |      "url": "http://www.apache.org/licenses/LICENSE-2.0.html"
                               |    }
                               |  },
                               |  "host": "obp_mock",
                               |  "host": "DynamicEntity",
                               |
                               |  "basePath": "/v2",
                               |  "tags": [
                               |    {
                               |      "name": "pet",
                               |      "description": "Everything about your Pets",
                               |      "externalDocs": {
                               |        "description": "Find out more",
                               |        "url": "http://swagger.io"
                               |      }
                               |    },
                               |    {
                               |      "name": "store",
                               |      "description": "Access to Petstore orders"
                               |    },
                               |    {
                               |      "name": "user",
                               |      "description": "Operations about user",
                               |      "externalDocs": {
                               |        "description": "Find out more about our store",
                               |        "url": "http://swagger.io"
                               |      }
                               |    }
                               |  ],
                               |  "schemes": [
                               |    "https",
                               |    "http"
                               |  ],
                               |  "paths": {
                               |    "/banks/gh.29.uk/pet/{petId}/uploadImage": {
                               |      "post": {
                               |        "tags": [
                               |          "pet"
                               |        ],
                               |        "summary": "uploads an image",
                               |        "description": "",
                               |        "operationId": "uploadFile",
                               |        "consumes": [
                               |          "multipart/form-data"
                               |        ],
                               |        "produces": [
                               |          "application/json"
                               |        ],
                               |        "parameters": [
                               |          {
                               |            "name": "petId",
                               |            "in": "path",
                               |            "description": "ID of pet to update",
                               |            "required": true,
                               |            "type": "integer",
                               |            "format": "int64"
                               |          },
                               |          {
                               |            "name": "additionalMetadata",
                               |            "in": "formData",
                               |            "description": "Additional data to pass to server",
                               |            "required": false,
                               |            "type": "string"
                               |          },
                               |          {
                               |            "name": "file",
                               |            "in": "formData",
                               |            "description": "file to upload",
                               |            "required": false,
                               |            "type": "file"
                               |          }
                               |        ],
                               |        "responses": {
                               |          "200": {
                               |            "description": "successful operation",
                               |            "schema": {
                               |              "$ref": "#/definitions/ApiResponse"
                               |            }
                               |          }
                               |        },
                               |        "security": [
                               |          {
                               |            "petstore_auth": [
                               |              "write:pets",
                               |              "read:pets"
                               |            ]
                               |          }
                               |        ]
                               |      }
                               |    },
                               |    "/banks/gh.29.uk/pet": {
                               |      "post": {
                               |        "tags": [
                               |          "pet"
                               |        ],
                               |        "summary": "Add a new pet to the store",
                               |        "description": "",
                               |        "operationId": "addPet",
                               |        "consumes": [
                               |          "application/json",
                               |          "application/xml"
                               |        ],
                               |        "produces": [
                               |          "application/json",
                               |          "application/xml"
                               |        ],
                               |        "parameters": [
                               |          {
                               |            "in": "body",
                               |            "name": "body",
                               |            "description": "Pet object that needs to be added to the store",
                               |            "required": true,
                               |            "schema": {
                               |              "$ref": "#/definitions/Pet"
                               |            }
                               |          }
                               |        ],
                               |        "responses": {
                               |          "405": {
                               |            "description": "Invalid input"
                               |          }
                               |        },
                               |        "security": [
                               |          {
                               |            "petstore_auth": [
                               |              "write:pets",
                               |              "read:pets"
                               |            ]
                               |          }
                               |        ]
                               |      },
                               |      "put": {
                               |        "tags": [
                               |          "pet"
                               |        ],
                               |        "summary": "Update an existing pet",
                               |        "description": "",
                               |        "operationId": "updatePet",
                               |        "consumes": [
                               |          "application/json",
                               |          "application/xml"
                               |        ],
                               |        "produces": [
                               |          "application/json",
                               |          "application/xml"
                               |        ],
                               |        "parameters": [
                               |          {
                               |            "in": "body",
                               |            "name": "body",
                               |            "description": "Pet object that needs to be added to the store",
                               |            "required": true,
                               |            "schema": {
                               |              "$ref": "#/definitions/Pet"
                               |            }
                               |          }
                               |        ],
                               |        "responses": {
                               |          "400": {
                               |            "description": "Invalid ID supplied"
                               |          },
                               |          "404": {
                               |            "description": "Pet not found"
                               |          },
                               |          "405": {
                               |            "description": "Validation exception"
                               |          }
                               |        },
                               |        "security": [
                               |          {
                               |            "petstore_auth": [
                               |              "write:pets",
                               |              "read:pets"
                               |            ]
                               |          }
                               |        ]
                               |      }
                               |    },
                               |    "/banks/gh.29.uk/pet/findByStatus": {
                               |      "get": {
                               |        "tags": [
                               |          "pet"
                               |        ],
                               |        "summary": "Finds Pets by status",
                               |        "description": "Multiple status values can be provided with comma separated strings",
                               |        "operationId": "findPetsByStatus",
                               |        "produces": [
                               |          "application/json",
                               |          "application/xml"
                               |        ],
                               |        "parameters": [
                               |          {
                               |            "name": "status",
                               |            "in": "query",
                               |            "description": "Status values that need to be considered for filter",
                               |            "required": true,
                               |            "type": "array",
                               |            "items": {
                               |              "type": "string",
                               |              "enum": [
                               |                "available",
                               |                "pending",
                               |                "sold"
                               |              ],
                               |              "default": "available"
                               |            },
                               |            "collectionFormat": "multi"
                               |          }
                               |        ],
                               |        "responses": {
                               |          "200": {
                               |            "description": "successful operation",
                               |            "schema": {
                               |              "type": "array",
                               |              "items": {
                               |                "$ref": "#/definitions/Pet"
                               |              }
                               |            }
                               |          },
                               |          "400": {
                               |            "description": "Invalid status value"
                               |          }
                               |        },
                               |        "security": [
                               |          {
                               |            "petstore_auth": [
                               |              "write:pets",
                               |              "read:pets"
                               |            ]
                               |          }
                               |        ]
                               |      }
                               |    },
                               |    "/banks/gh.29.uk/pet/findByTags": {
                               |      "get": {
                               |        "tags": [
                               |          "pet"
                               |        ],
                               |        "summary": "Finds Pets by tags",
                               |        "description": "Multiple tags can be provided with comma separated strings. Use tag1, tag2, tag3 for testing.",
                               |        "operationId": "findPetsByTags",
                               |        "produces": [
                               |          "application/json",
                               |          "application/xml"
                               |        ],
                               |        "parameters": [
                               |          {
                               |            "name": "tags",
                               |            "in": "query",
                               |            "description": "Tags to filter by",
                               |            "required": true,
                               |            "type": "array",
                               |            "items": {
                               |              "type": "string"
                               |            },
                               |            "collectionFormat": "multi"
                               |          }
                               |        ],
                               |        "responses": {
                               |          "200": {
                               |            "description": "successful operation",
                               |            "schema": {
                               |              "type": "array",
                               |              "items": {
                               |                "$ref": "#/definitions/Pet"
                               |              }
                               |            }
                               |          },
                               |          "400": {
                               |            "description": "Invalid tag value"
                               |          }
                               |        },
                               |        "security": [
                               |          {
                               |            "petstore_auth": [
                               |              "write:pets",
                               |              "read:pets"
                               |            ]
                               |          }
                               |        ],
                               |        "deprecated": true
                               |      }
                               |    },
                               |    "/banks/gh.29.uk/pet/{petId}": {
                               |      "get": {
                               |        "tags": [
                               |          "pet"
                               |        ],
                               |        "summary": "Find pet by ID",
                               |        "description": "Returns a single pet",
                               |        "operationId": "getPetById",
                               |        "produces": [
                               |          "application/json",
                               |          "application/xml"
                               |        ],
                               |        "parameters": [
                               |          {
                               |            "name": "petId",
                               |            "in": "path",
                               |            "description": "ID of pet to return",
                               |            "required": true,
                               |            "type": "integer",
                               |            "format": "int64"
                               |          }
                               |        ],
                               |        "responses": {
                               |          "200": {
                               |            "description": "successful operation",
                               |            "schema": {
                               |              "$ref": "#/definitions/Pet"
                               |            }
                               |          },
                               |          "400": {
                               |            "description": "Invalid ID supplied"
                               |          },
                               |          "404": {
                               |            "description": "Pet not found"
                               |          }
                               |        },
                               |        "security": [
                               |          {
                               |            "api_key": []
                               |          }
                               |        ]
                               |      },
                               |      "post": {
                               |        "tags": [
                               |          "pet"
                               |        ],
                               |        "summary": "Updates a pet in the store with form data",
                               |        "description": "",
                               |        "operationId": "updatePetWithForm",
                               |        "consumes": [
                               |          "application/x-www-form-urlencoded"
                               |        ],
                               |        "produces": [
                               |          "application/json",
                               |          "application/xml"
                               |        ],
                               |        "parameters": [
                               |          {
                               |            "name": "petId",
                               |            "in": "path",
                               |            "description": "ID of pet that needs to be updated",
                               |            "required": true,
                               |            "type": "integer",
                               |            "format": "int64"
                               |          },
                               |          {
                               |            "name": "name",
                               |            "in": "formData",
                               |            "description": "Updated name of the pet",
                               |            "required": false,
                               |            "type": "string"
                               |          },
                               |          {
                               |            "name": "status",
                               |            "in": "formData",
                               |            "description": "Updated status of the pet",
                               |            "required": false,
                               |            "type": "string"
                               |          }
                               |        ],
                               |        "responses": {
                               |          "405": {
                               |            "description": "Invalid input"
                               |          }
                               |        },
                               |        "security": [
                               |          {
                               |            "petstore_auth": [
                               |              "write:pets",
                               |              "read:pets"
                               |            ]
                               |          }
                               |        ]
                               |      },
                               |      "delete": {
                               |        "tags": [
                               |          "pet"
                               |        ],
                               |        "summary": "Deletes a pet",
                               |        "description": "",
                               |        "operationId": "deletePet",
                               |        "produces": [
                               |          "application/json",
                               |          "application/xml"
                               |        ],
                               |        "parameters": [
                               |          {
                               |            "name": "api_key",
                               |            "in": "header",
                               |            "required": false,
                               |            "type": "string"
                               |          },
                               |          {
                               |            "name": "petId",
                               |            "in": "path",
                               |            "description": "Pet id to delete",
                               |            "required": true,
                               |            "type": "integer",
                               |            "format": "int64"
                               |          }
                               |        ],
                               |        "responses": {
                               |          "400": {
                               |            "description": "Invalid ID supplied"
                               |          },
                               |          "404": {
                               |            "description": "Pet not found"
                               |          }
                               |        },
                               |        "security": [
                               |          {
                               |            "petstore_auth": [
                               |              "write:pets",
                               |              "read:pets"
                               |            ]
                               |          }
                               |        ]
                               |      }
                               |    },
                               |    "/banks/gh.29.uk/store/order": {
                               |      "post": {
                               |        "tags": [
                               |          "store"
                               |        ],
                               |        "summary": "Place an order for a pet",
                               |        "description": "",
                               |        "operationId": "placeOrder",
                               |        "consumes": [
                               |          "application/json"
                               |        ],
                               |        "produces": [
                               |          "application/json",
                               |          "application/xml"
                               |        ],
                               |        "parameters": [
                               |          {
                               |            "in": "body",
                               |            "name": "body",
                               |            "description": "order placed for purchasing the pet",
                               |            "required": true,
                               |            "schema": {
                               |              "$ref": "#/definitions/Order"
                               |            }
                               |          }
                               |        ],
                               |        "responses": {
                               |          "200": {
                               |            "description": "successful operation",
                               |            "schema": {
                               |              "$ref": "#/definitions/Order"
                               |            }
                               |          },
                               |          "400": {
                               |            "description": "Invalid Order"
                               |          }
                               |        }
                               |      }
                               |    },
                               |    "/banks/gh.29.uk/store/order/{orderId}": {
                               |      "get": {
                               |        "tags": [
                               |          "store"
                               |        ],
                               |        "summary": "Find purchase order by ID",
                               |        "description": "For valid response try integer IDs with value >= 1 and <= 10. Other values will generated exceptions",
                               |        "operationId": "getOrderById",
                               |        "produces": [
                               |          "application/json",
                               |          "application/xml"
                               |        ],
                               |        "parameters": [
                               |          {
                               |            "name": "orderId",
                               |            "in": "path",
                               |            "description": "ID of pet that needs to be fetched",
                               |            "required": true,
                               |            "type": "integer",
                               |            "maximum": 10,
                               |            "minimum": 1,
                               |            "format": "int64"
                               |          }
                               |        ],
                               |        "responses": {
                               |          "200": {
                               |            "description": "successful operation",
                               |            "schema": {
                               |              "$ref": "#/definitions/Order"
                               |            }
                               |          },
                               |          "400": {
                               |            "description": "Invalid ID supplied"
                               |          },
                               |          "404": {
                               |            "description": "Order not found"
                               |          }
                               |        }
                               |      },
                               |      "delete": {
                               |        "tags": [
                               |          "store"
                               |        ],
                               |        "summary": "Delete purchase order by ID",
                               |        "description": "For valid response try integer IDs with positive integer value. Negative or non-integer values will generate API errors",
                               |        "operationId": "deleteOrder",
                               |        "produces": [
                               |          "application/json",
                               |          "application/xml"
                               |        ],
                               |        "parameters": [
                               |          {
                               |            "name": "orderId",
                               |            "in": "path",
                               |            "description": "ID of the order that needs to be deleted",
                               |            "required": true,
                               |            "type": "integer",
                               |            "minimum": 1,
                               |            "format": "int64"
                               |          }
                               |        ],
                               |        "responses": {
                               |          "400": {
                               |            "description": "Invalid ID supplied"
                               |          },
                               |          "404": {
                               |            "description": "Order not found"
                               |          }
                               |        }
                               |      }
                               |    },
                               |    "/banks/gh.29.uk/store/inventory": {
                               |      "get": {
                               |        "tags": [
                               |          "store"
                               |        ],
                               |        "summary": "Returns pet inventories by status",
                               |        "description": "Returns a map of status codes to quantities",
                               |        "operationId": "getInventory",
                               |        "produces": [
                               |          "application/json"
                               |        ],
                               |        "parameters": [],
                               |        "responses": {
                               |          "200": {
                               |            "description": "successful operation",
                               |            "schema": {
                               |              "type": "object",
                               |              "additionalProperties": {
                               |                "type": "integer",
                               |                "format": "int32"
                               |              }
                               |            }
                               |          }
                               |        },
                               |        "security": [
                               |          {
                               |            "api_key": []
                               |          }
                               |        ]
                               |      }
                               |    },
                               |    "/banks/gh.29.uk/user/createWithArray": {
                               |      "post": {
                               |        "tags": [
                               |          "user"
                               |        ],
                               |        "summary": "Creates list of users with given input array",
                               |        "description": "",
                               |        "operationId": "createUsersWithArrayInput",
                               |        "consumes": [
                               |          "application/json"
                               |        ],
                               |        "produces": [
                               |          "application/json",
                               |          "application/xml"
                               |        ],
                               |        "parameters": [
                               |          {
                               |            "in": "body",
                               |            "name": "body",
                               |            "description": "List of user object",
                               |            "required": true,
                               |            "schema": {
                               |              "type": "array",
                               |              "items": {
                               |                "$ref": "#/definitions/User"
                               |              }
                               |            }
                               |          }
                               |        ],
                               |        "responses": {
                               |          "default": {
                               |            "description": "successful operation"
                               |          }
                               |        }
                               |      }
                               |    },
                               |    "/banks/gh.29.uk/user/createWithList": {
                               |      "post": {
                               |        "tags": [
                               |          "user"
                               |        ],
                               |        "summary": "Creates list of users with given input array",
                               |        "description": "",
                               |        "operationId": "createUsersWithListInput",
                               |        "consumes": [
                               |          "application/json"
                               |        ],
                               |        "produces": [
                               |          "application/json",
                               |          "application/xml"
                               |        ],
                               |        "parameters": [
                               |          {
                               |            "in": "body",
                               |            "name": "body",
                               |            "description": "List of user object",
                               |            "required": true,
                               |            "schema": {
                               |              "type": "array",
                               |              "items": {
                               |                "$ref": "#/definitions/User"
                               |              }
                               |            }
                               |          }
                               |        ],
                               |        "responses": {
                               |          "default": {
                               |            "description": "successful operation"
                               |          }
                               |        }
                               |      }
                               |    },
                               |    "/banks/gh.29.uk/user/{username}": {
                               |      "get": {
                               |        "tags": [
                               |          "user"
                               |        ],
                               |        "summary": "Get user by user name",
                               |        "description": "",
                               |        "operationId": "getUserByName",
                               |        "produces": [
                               |          "application/json",
                               |          "application/xml"
                               |        ],
                               |        "parameters": [
                               |          {
                               |            "name": "username",
                               |            "in": "path",
                               |            "description": "The name that needs to be fetched. Use user1 for testing. ",
                               |            "required": true,
                               |            "type": "string"
                               |          }
                               |        ],
                               |        "responses": {
                               |          "200": {
                               |            "description": "successful operation",
                               |            "schema": {
                               |              "$ref": "#/definitions/User"
                               |            }
                               |          },
                               |          "400": {
                               |            "description": "Invalid username supplied"
                               |          },
                               |          "404": {
                               |            "description": "User not found"
                               |          }
                               |        }
                               |      },
                               |      "put": {
                               |        "tags": [
                               |          "user"
                               |        ],
                               |        "summary": "Updated user",
                               |        "description": "This can only be done by the logged in user.",
                               |        "operationId": "updateUser",
                               |        "consumes": [
                               |          "application/json"
                               |        ],
                               |        "produces": [
                               |          "application/json",
                               |          "application/xml"
                               |        ],
                               |        "parameters": [
                               |          {
                               |            "name": "username",
                               |            "in": "path",
                               |            "description": "name that need to be updated",
                               |            "required": true,
                               |            "type": "string"
                               |          },
                               |          {
                               |            "in": "body",
                               |            "name": "body",
                               |            "description": "Updated user object",
                               |            "required": true,
                               |            "schema": {
                               |              "$ref": "#/definitions/User"
                               |            }
                               |          }
                               |        ],
                               |        "responses": {
                               |          "400": {
                               |            "description": "Invalid user supplied"
                               |          },
                               |          "404": {
                               |            "description": "User not found"
                               |          }
                               |        }
                               |      },
                               |      "delete": {
                               |        "tags": [
                               |          "user"
                               |        ],
                               |        "summary": "Delete user",
                               |        "description": "This can only be done by the logged in user.",
                               |        "operationId": "deleteUser",
                               |        "produces": [
                               |          "application/json",
                               |          "application/xml"
                               |        ],
                               |        "parameters": [
                               |          {
                               |            "name": "username",
                               |            "in": "path",
                               |            "description": "The name that needs to be deleted",
                               |            "required": true,
                               |            "type": "string"
                               |          }
                               |        ],
                               |        "responses": {
                               |          "400": {
                               |            "description": "Invalid username supplied"
                               |          },
                               |          "404": {
                               |            "description": "User not found"
                               |          }
                               |        }
                               |      }
                               |    },
                               |    "/banks/gh.29.uk/user/login": {
                               |      "get": {
                               |        "tags": [
                               |          "user"
                               |        ],
                               |        "summary": "Logs user into the system",
                               |        "description": "",
                               |        "operationId": "loginUser",
                               |        "produces": [
                               |          "application/json",
                               |          "application/xml"
                               |        ],
                               |        "parameters": [
                               |          {
                               |            "name": "username",
                               |            "in": "query",
                               |            "description": "The user name for login",
                               |            "required": true,
                               |            "type": "string"
                               |          },
                               |          {
                               |            "name": "password",
                               |            "in": "query",
                               |            "description": "The password for login in clear text",
                               |            "required": true,
                               |            "type": "string"
                               |          }
                               |        ],
                               |        "responses": {
                               |          "200": {
                               |            "description": "successful operation",
                               |            "headers": {
                               |              "X-Expires-After": {
                               |                "type": "string",
                               |                "format": "date-time",
                               |                "description": "date in UTC when token expires"
                               |              },
                               |              "X-Rate-Limit": {
                               |                "type": "integer",
                               |                "format": "int32",
                               |                "description": "calls per hour allowed by the user"
                               |              }
                               |            },
                               |            "schema": {
                               |              "type": "string"
                               |            }
                               |          },
                               |          "400": {
                               |            "description": "Invalid username/password supplied"
                               |          }
                               |        }
                               |      }
                               |    },
                               |    "/banks/gh.29.uk/user/logout": {
                               |      "get": {
                               |        "tags": [
                               |          "user"
                               |        ],
                               |        "summary": "Logs out current logged in user session",
                               |        "description": "",
                               |        "operationId": "logoutUser",
                               |        "produces": [
                               |          "application/json",
                               |          "application/xml"
                               |        ],
                               |        "parameters": [],
                               |        "responses": {
                               |          "default": {
                               |            "description": "successful operation"
                               |          }
                               |        }
                               |      }
                               |    },
                               |    "/banks/gh.29.uk/user": {
                               |      "post": {
                               |        "tags": [
                               |          "user"
                               |        ],
                               |        "summary": "Create user",
                               |        "description": "This can only be done by the logged in user.",
                               |        "operationId": "createUser",
                               |        "consumes": [
                               |          "application/json"
                               |        ],
                               |        "produces": [
                               |          "application/json",
                               |          "application/xml"
                               |        ],
                               |        "parameters": [
                               |          {
                               |            "in": "body",
                               |            "name": "body",
                               |            "description": "Created user object",
                               |            "required": true,
                               |            "schema": {
                               |              "$ref": "#/definitions/User"
                               |            }
                               |          }
                               |        ],
                               |        "responses": {
                               |          "default": {
                               |            "description": "successful operation"
                               |          }
                               |        }
                               |      }
                               |    }
                               |  },
                               |  "securityDefinitions": {
                               |    "api_key": {
                               |      "type": "apiKey",
                               |      "name": "api_key",
                               |      "in": "header"
                               |    },
                               |    "petstore_auth": {
                               |      "type": "oauth2",
                               |      "authorizationUrl": "https://petstore.swagger.io/oauth/authorize",
                               |      "flow": "implicit",
                               |      "scopes": {
                               |        "read:pets": "read your pets",
                               |        "write:pets": "modify pets in your account"
                               |      }
                               |    }
                               |  },
                               |  "definitions": {
                               |    "ApiResponse": {
                               |      "type": "object",
                               |      "properties": {
                               |        "code": {
                               |          "type": "integer",
                               |          "format": "int32"
                               |        },
                               |        "type": {
                               |          "type": "string"
                               |        },
                               |        "message": {
                               |          "type": "string"
                               |        }
                               |      }
                               |    },
                               |    "Category": {
                               |      "type": "object",
                               |      "properties": {
                               |        "id": {
                               |          "type": "integer",
                               |          "format": "int64"
                               |        },
                               |        "name": {
                               |          "type": "string"
                               |        }
                               |      },
                               |      "xml": {
                               |        "name": "Category"
                               |      }
                               |    },
                               |    "Pet": {
                               |      "type": "object",
                               |      "required": [
                               |        "name",
                               |        "photoUrls"
                               |      ],
                               |      "properties": {
                               |        "id": {
                               |          "type": "integer",
                               |          "format": "int64"
                               |        },
                               |        "category": {
                               |          "$ref": "#/definitions/Category"
                               |        },
                               |        "name": {
                               |          "type": "string",
                               |          "example": "doggie"
                               |        },
                               |        "photoUrls": {
                               |          "type": "array",
                               |          "xml": {
                               |            "wrapped": true
                               |          },
                               |          "items": {
                               |            "type": "string",
                               |            "xml": {
                               |              "name": "photoUrl"
                               |            }
                               |          }
                               |        },
                               |        "tags": {
                               |          "type": "array",
                               |          "xml": {
                               |            "wrapped": true
                               |          },
                               |          "items": {
                               |            "xml": {
                               |              "name": "tag"
                               |            },
                               |            "$ref": "#/definitions/Tag"
                               |          }
                               |        },
                               |        "status": {
                               |          "type": "string",
                               |          "description": "pet status in the store",
                               |          "enum": [
                               |            "available",
                               |            "pending",
                               |            "sold"
                               |          ]
                               |        }
                               |      },
                               |      "xml": {
                               |        "name": "Pet"
                               |      }
                               |    },
                               |    "Tag": {
                               |      "type": "object",
                               |      "properties": {
                               |        "id": {
                               |          "type": "integer",
                               |          "format": "int64"
                               |        },
                               |        "name": {
                               |          "type": "string"
                               |        }
                               |      },
                               |      "xml": {
                               |        "name": "Tag"
                               |      }
                               |    },
                               |    "Order": {
                               |      "type": "object",
                               |      "properties": {
                               |        "id": {
                               |          "type": "integer",
                               |          "format": "int64"
                               |        },
                               |        "petId": {
                               |          "type": "integer",
                               |          "format": "int64"
                               |        },
                               |        "quantity": {
                               |          "type": "integer",
                               |          "format": "int32"
                               |        },
                               |        "shipDate": {
                               |          "type": "string",
                               |          "format": "date-time"
                               |        },
                               |        "status": {
                               |          "type": "string",
                               |          "description": "Order Status",
                               |          "enum": [
                               |            "placed",
                               |            "approved",
                               |            "delivered"
                               |          ]
                               |        },
                               |        "complete": {
                               |          "type": "boolean"
                               |        }
                               |      },
                               |      "xml": {
                               |        "name": "Order"
                               |      }
                               |    },
                               |    "User": {
                               |      "type": "object",
                               |      "properties": {
                               |        "id": {
                               |          "type": "integer",
                               |          "format": "int64"
                               |        },
                               |        "username": {
                               |          "type": "string"
                               |        },
                               |        "firstName": {
                               |          "type": "string"
                               |        },
                               |        "lastName": {
                               |          "type": "string"
                               |        },
                               |        "email": {
                               |          "type": "string"
                               |        },
                               |        "password": {
                               |          "type": "string"
                               |        },
                               |        "phone": {
                               |          "type": "string"
                               |        },
                               |        "userStatus": {
                               |          "type": "integer",
                               |          "format": "int32",
                               |          "description": "User Status"
                               |        }
                               |      },
                               |      "xml": {
                               |        "name": "User"
                               |      }
                               |    }
                               |  },
                               |  "externalDocs": {
                               |    "description": "Find out more about Swagger",
                               |    "url": "http://swagger.io"
                               |  }
                               |}""".stripMargin

    val expectedJson = json.parse(expectedJsonString)

    val result= DynamicEndpointHelper.addedBankToPath(dataJsonString, bankId)

    expectedJson should equal(result)
  }

  "isDynamicEntityResponse" should "work well" taggedAs FunctionsTag in {

    // swagger with schemes:
    val serverUrl1 = "https://dynamic_entity"
    
    // swagger not schemes:
    val serverUrl2 = "//dynamic_entity"
    
    val result1= DynamicEndpointHelper.isDynamicEntityResponse(serverUrl1)
    val result2= DynamicEndpointHelper.isDynamicEntityResponse(serverUrl2)

    result1 should equal(true)
    result2 should equal(true)
    
  }

  "isMockedResponse" should "work well" taggedAs FunctionsTag in {

    // swagger with schemes:
    val serverUrl1 = "https://obp_mock:3/"

    // swagger not schemes:
    val serverUrl2 = "//obp_mock/"
    val serverUrl3 = "//obp_mock"

    val result1= DynamicEndpointHelper.isMockedResponse(serverUrl1)
    val result2= DynamicEndpointHelper.isMockedResponse(serverUrl2)
    val result3= DynamicEndpointHelper.isMockedResponse(serverUrl3)

    result1 should equal(true)
    result2 should equal(true)
    result3 should equal(true)

  }

  "Swagger getOpenApiVersion check" should "work well" taggedAs FunctionsTag in {
    //  OpenAPI3.0 
    //  https://github.com/OAI/OpenAPI-Specification/edit/main/examples/v3.0/uspto.json
    val openApiV301 ="""{
                       |  "openapi": "3.0.1",
                       |  "servers": [
                       |    {
                       |      "url": "{scheme}://developer.uspto.gov/ds-api",
                       |      "variables": {
                       |        "scheme": {
                       |          "description": "The Data Set API is accessible via https and http",
                       |          "enum": [
                       |            "https",
                       |            "http"
                       |          ],
                       |          "default": "https"
                       |        }
                       |      }
                       |    }
                       |  ],
                       |  "info": {
                       |    "description": "The Data Set API (DSAPI) allows the public users to discover and search USPTO exported data sets. This is a generic API that allows USPTO users to make any CSV based data files searchable through API. With the help of GET call, it returns the list of data fields that are searchable. With the help of POST call, data can be fetched based on the filters on the field names. Please note that POST call is used to search the actual data. The reason for the POST call is that it allows users to specify any complex search criteria without worry about the GET size limitations as well as encoding of the input parameters.",
                       |    "version": "1.0.0",
                       |    "title": "USPTO Data Set API",
                       |    "contact": {
                       |      "name": "Open Data Portal",
                       |      "url": "https://developer.uspto.gov",
                       |      "email": "developer@uspto.gov"
                       |    }
                       |  },
                       |  "tags": [
                       |    {
                       |      "name": "metadata",
                       |      "description": "Find out about the data sets"
                       |    },
                       |    {
                       |      "name": "search",
                       |      "description": "Search a data set"
                       |    }
                       |  ],
                       |  "paths": {
                       |    "/": {
                       |      "get": {
                       |        "tags": [
                       |          "metadata"
                       |        ],
                       |        "operationId": "list-data-sets",
                       |        "summary": "List available data sets",
                       |        "responses": {
                       |          "200": {
                       |            "description": "Returns a list of data sets",
                       |            "content": {
                       |              "application/json": {
                       |                "schema": {
                       |                  "$ref": "#/components/schemas/dataSetList"
                       |                },
                       |                "example": {
                       |                  "total": 2,
                       |                  "apis": [
                       |                    {
                       |                      "apiKey": "oa_citations",
                       |                      "apiVersionNumber": "v1",
                       |                      "apiUrl": "https://developer.uspto.gov/ds-api/oa_citations/v1/fields",
                       |                      "apiDocumentationUrl": "https://developer.uspto.gov/ds-api-docs/index.html?url=https://developer.uspto.gov/ds-api/swagger/docs/oa_citations.json"
                       |                    },
                       |                    {
                       |                      "apiKey": "cancer_moonshot",
                       |                      "apiVersionNumber": "v1",
                       |                      "apiUrl": "https://developer.uspto.gov/ds-api/cancer_moonshot/v1/fields",
                       |                      "apiDocumentationUrl": "https://developer.uspto.gov/ds-api-docs/index.html?url=https://developer.uspto.gov/ds-api/swagger/docs/cancer_moonshot.json"
                       |                    }
                       |                  ]
                       |                }
                       |              }
                       |            }
                       |          }
                       |        }
                       |      }
                       |    },
                       |    "/{dataset}/{version}/fields": {
                       |      "get": {
                       |        "tags": [
                       |          "metadata"
                       |        ],
                       |        "summary": "Provides the general information about the API and the list of fields that can be used to query the dataset.",
                       |        "description": "This GET API returns the list of all the searchable field names that are in the oa_citations. Please see the 'fields' attribute which returns an array of field names. Each field or a combination of fields can be searched using the syntax options shown below.",
                       |        "operationId": "list-searchable-fields",
                       |        "parameters": [
                       |          {
                       |            "name": "dataset",
                       |            "in": "path",
                       |            "description": "Name of the dataset.",
                       |            "required": true,
                       |            "example": "oa_citations",
                       |            "schema": {
                       |              "type": "string"
                       |            }
                       |          },
                       |          {
                       |            "name": "version",
                       |            "in": "path",
                       |            "description": "Version of the dataset.",
                       |            "required": true,
                       |            "example": "v1",
                       |            "schema": {
                       |              "type": "string"
                       |            }
                       |          }
                       |        ],
                       |        "responses": {
                       |          "200": {
                       |            "description": "The dataset API for the given version is found and it is accessible to consume.",
                       |            "content": {
                       |              "application/json": {
                       |                "schema": {
                       |                  "type": "string"
                       |                }
                       |              }
                       |            }
                       |          },
                       |          "404": {
                       |            "description": "The combination of dataset name and version is not found in the system or it is not published yet to be consumed by public.",
                       |            "content": {
                       |              "application/json": {
                       |                "schema": {
                       |                  "type": "string"
                       |                }
                       |              }
                       |            }
                       |          }
                       |        }
                       |      }
                       |    },
                       |    "/{dataset}/{version}/records": {
                       |      "post": {
                       |        "tags": [
                       |          "search"
                       |        ],
                       |        "summary": "Provides search capability for the data set with the given search criteria.",
                       |        "description": "This API is based on Solr/Lucene Search. The data is indexed using SOLR. This GET API returns the list of all the searchable field names that are in the Solr Index. Please see the 'fields' attribute which returns an array of field names. Each field or a combination of fields can be searched using the Solr/Lucene Syntax. Please refer https://lucene.apache.org/core/3_6_2/queryparsersyntax.html#Overview for the query syntax. List of field names that are searchable can be determined using above GET api.",
                       |        "operationId": "perform-search",
                       |        "parameters": [
                       |          {
                       |            "name": "version",
                       |            "in": "path",
                       |            "description": "Version of the dataset.",
                       |            "required": true,
                       |            "schema": {
                       |              "type": "string",
                       |              "default": "v1"
                       |            }
                       |          },
                       |          {
                       |            "name": "dataset",
                       |            "in": "path",
                       |            "description": "Name of the dataset. In this case, the default value is oa_citations",
                       |            "required": true,
                       |            "schema": {
                       |              "type": "string",
                       |              "default": "oa_citations"
                       |            }
                       |          }
                       |        ],
                       |        "responses": {
                       |          "200": {
                       |            "description": "successful operation",
                       |            "content": {
                       |              "application/json": {
                       |                "schema": {
                       |                  "type": "array",
                       |                  "items": {
                       |                    "type": "object",
                       |                    "additionalProperties": {
                       |                      "type": "object"
                       |                    }
                       |                  }
                       |                }
                       |              }
                       |            }
                       |          },
                       |          "404": {
                       |            "description": "No matching record found for the given criteria."
                       |          }
                       |        },
                       |        "requestBody": {
                       |          "content": {
                       |            "application/x-www-form-urlencoded": {
                       |              "schema": {
                       |                "type": "object",
                       |                "properties": {
                       |                  "criteria": {
                       |                    "description": "Uses Lucene Query Syntax in the format of propertyName:value, propertyName:[num1 TO num2] and date range format: propertyName:[yyyyMMdd TO yyyyMMdd]. In the response please see the 'docs' element which has the list of record objects. Each record structure would consist of all the fields and their corresponding values.",
                       |                    "type": "string",
                       |                    "default": "*:*"
                       |                  },
                       |                  "start": {
                       |                    "description": "Starting record number. Default value is 0.",
                       |                    "type": "integer",
                       |                    "default": 0
                       |                  },
                       |                  "rows": {
                       |                    "description": "Specify number of rows to be returned. If you run the search with default values, in the response you will see 'numFound' attribute which will tell the number of records available in the dataset.",
                       |                    "type": "integer",
                       |                    "default": 100
                       |                  }
                       |                },
                       |                "required": [
                       |                  "criteria"
                       |                ]
                       |              }
                       |            }
                       |          }
                       |        }
                       |      }
                       |    }
                       |  },
                       |  "components": {
                       |    "schemas": {
                       |      "dataSetList": {
                       |        "type": "object",
                       |        "properties": {
                       |          "total": {
                       |            "type": "integer"
                       |          },
                       |          "apis": {
                       |            "type": "array",
                       |            "items": {
                       |              "type": "object",
                       |              "properties": {
                       |                "apiKey": {
                       |                  "type": "string",
                       |                  "description": "To be used as a dataset parameter value"
                       |                },
                       |                "apiVersionNumber": {
                       |                  "type": "string",
                       |                  "description": "To be used as a version parameter value"
                       |                },
                       |                "apiUrl": {
                       |                  "type": "string",
                       |                  "format": "uriref",
                       |                  "description": "The URL describing the dataset's fields"
                       |                },
                       |                "apiDocumentationUrl": {
                       |                  "type": "string",
                       |                  "format": "uriref",
                       |                  "description": "A URL to the API console for each API"
                       |                }
                       |              }
                       |            }
                       |          }
                       |        }
                       |      }
                       |    }
                       |  }
                       |}""".stripMargin
    //  Swagger2.0
    //  https://github.com/OAI/OpenAPI-Specification/blob/main/examples/v2.0/json/petstore.json
    val openApiV20 ="""{
                      |  "swagger": "2.0",
                      |  "info": {
                      |    "version": "1.0.0",
                      |    "title": "Swagger Petstore",
                      |    "license": {
                      |      "name": "MIT"
                      |    }
                      |  },
                      |  "host": "petstore.swagger.io",
                      |  "basePath": "/v1",
                      |  "schemes": [
                      |    "http"
                      |  ],
                      |  "consumes": [
                      |    "application/json"
                      |  ],
                      |  "produces": [
                      |    "application/json"
                      |  ],
                      |  "paths": {
                      |    "/pets": {
                      |      "get": {
                      |        "summary": "List all pets",
                      |        "operationId": "listPets",
                      |        "tags": [
                      |          "pets"
                      |        ],
                      |        "parameters": [
                      |          {
                      |            "name": "limit",
                      |            "in": "query",
                      |            "description": "How many items to return at one time (max 100)",
                      |            "required": false,
                      |            "type": "integer",
                      |            "format": "int32"
                      |          }
                      |        ],
                      |        "responses": {
                      |          "200": {
                      |            "description": "An paged array of pets",
                      |            "headers": {
                      |              "x-next": {
                      |                "type": "string",
                      |                "description": "A link to the next page of responses"
                      |              }
                      |            },
                      |            "schema": {
                      |              "$ref": "#/definitions/Pets"
                      |            }
                      |          },
                      |          "default": {
                      |            "description": "unexpected error",
                      |            "schema": {
                      |              "$ref": "#/definitions/Error"
                      |            }
                      |          }
                      |        }
                      |      },
                      |      "post": {
                      |        "summary": "Create a pet",
                      |        "operationId": "createPets",
                      |        "tags": [
                      |          "pets"
                      |        ],
                      |        "responses": {
                      |          "201": {
                      |            "description": "Null response"
                      |          },
                      |          "default": {
                      |            "description": "unexpected error",
                      |            "schema": {
                      |              "$ref": "#/definitions/Error"
                      |            }
                      |          }
                      |        }
                      |      }
                      |    },
                      |    "/pets/{petId}": {
                      |      "get": {
                      |        "summary": "Info for a specific pet",
                      |        "operationId": "showPetById",
                      |        "tags": [
                      |          "pets"
                      |        ],
                      |        "parameters": [
                      |          {
                      |            "name": "petId",
                      |            "in": "path",
                      |            "required": true,
                      |            "description": "The id of the pet to retrieve",
                      |            "type": "string"
                      |          }
                      |        ],
                      |        "responses": {
                      |          "200": {
                      |            "description": "Expected response to a valid request",
                      |            "schema": {
                      |              "$ref": "#/definitions/Pets"
                      |            }
                      |          },
                      |          "default": {
                      |            "description": "unexpected error",
                      |            "schema": {
                      |              "$ref": "#/definitions/Error"
                      |            }
                      |          }
                      |        }
                      |      }
                      |    }
                      |  },
                      |  "definitions": {
                      |    "Pet": {
                      |      "required": [
                      |        "id",
                      |        "name"
                      |      ],
                      |      "properties": {
                      |        "id": {
                      |          "type": "integer",
                      |          "format": "int64"
                      |        },
                      |        "name": {
                      |          "type": "string"
                      |        },
                      |        "tag": {
                      |          "type": "string"
                      |        }
                      |      }
                      |    },
                      |    "Pets": {
                      |      "type": "array",
                      |      "items": {
                      |        "$ref": "#/definitions/Pet"
                      |      }
                      |    },
                      |    "Error": {
                      |      "required": [
                      |        "code",
                      |        "message"
                      |      ],
                      |      "properties": {
                      |        "code": {
                      |          "type": "integer",
                      |          "format": "int32"
                      |        },
                      |        "message": {
                      |          "type": "string"
                      |        }
                      |      }
                      |    }
                      |  }
                      |}""".stripMargin

    val result1 = DynamicEndpointHelper.getOpenApiVersion(openApiV301)
    result1 should equal("3.0.1")
    val result2 = DynamicEndpointHelper.getOpenApiVersion(openApiV20)
    result2 should equal("2.0")
  }

  "Swagger changeOpenApiVersionHost check" should "work well" taggedAs FunctionsTag in {
    //  OpenAPI3.0 
    //  https://github.com/OAI/OpenAPI-Specification/edit/main/examples/v3.0/uspto.json
    val openApiV301 ="""{
                       |  "openapi": "3.0.1",
                       |  "servers": [
                       |    {
                       |      "url": "{scheme}://developer.uspto.gov/ds-api",
                       |      "variables": {
                       |        "scheme": {
                       |          "description": "The Data Set API is accessible via https and http",
                       |          "enum": [
                       |            "https",
                       |            "http"
                       |          ],
                       |          "default": "https"
                       |        }
                       |      }
                       |    }
                       |  ],
                       |  "info": {
                       |    "description": "The Data Set API (DSAPI) allows the public users to discover and search USPTO exported data sets. This is a generic API that allows USPTO users to make any CSV based data files searchable through API. With the help of GET call, it returns the list of data fields that are searchable. With the help of POST call, data can be fetched based on the filters on the field names. Please note that POST call is used to search the actual data. The reason for the POST call is that it allows users to specify any complex search criteria without worry about the GET size limitations as well as encoding of the input parameters.",
                       |    "version": "1.0.0",
                       |    "title": "USPTO Data Set API",
                       |    "contact": {
                       |      "name": "Open Data Portal",
                       |      "url": "https://developer.uspto.gov",
                       |      "email": "developer@uspto.gov"
                       |    }
                       |  },
                       |  "tags": [
                       |    {
                       |      "name": "metadata",
                       |      "description": "Find out about the data sets"
                       |    },
                       |    {
                       |      "name": "search",
                       |      "description": "Search a data set"
                       |    }
                       |  ],
                       |  "paths": {
                       |    "/": {
                       |      "get": {
                       |        "tags": [
                       |          "metadata"
                       |        ],
                       |        "operationId": "list-data-sets",
                       |        "summary": "List available data sets",
                       |        "responses": {
                       |          "200": {
                       |            "description": "Returns a list of data sets",
                       |            "content": {
                       |              "application/json": {
                       |                "schema": {
                       |                  "$ref": "#/components/schemas/dataSetList"
                       |                },
                       |                "example": {
                       |                  "total": 2,
                       |                  "apis": [
                       |                    {
                       |                      "apiKey": "oa_citations",
                       |                      "apiVersionNumber": "v1",
                       |                      "apiUrl": "https://developer.uspto.gov/ds-api/oa_citations/v1/fields",
                       |                      "apiDocumentationUrl": "https://developer.uspto.gov/ds-api-docs/index.html?url=https://developer.uspto.gov/ds-api/swagger/docs/oa_citations.json"
                       |                    },
                       |                    {
                       |                      "apiKey": "cancer_moonshot",
                       |                      "apiVersionNumber": "v1",
                       |                      "apiUrl": "https://developer.uspto.gov/ds-api/cancer_moonshot/v1/fields",
                       |                      "apiDocumentationUrl": "https://developer.uspto.gov/ds-api-docs/index.html?url=https://developer.uspto.gov/ds-api/swagger/docs/cancer_moonshot.json"
                       |                    }
                       |                  ]
                       |                }
                       |              }
                       |            }
                       |          }
                       |        }
                       |      }
                       |    },
                       |    "/{dataset}/{version}/fields": {
                       |      "get": {
                       |        "tags": [
                       |          "metadata"
                       |        ],
                       |        "summary": "Provides the general information about the API and the list of fields that can be used to query the dataset.",
                       |        "description": "This GET API returns the list of all the searchable field names that are in the oa_citations. Please see the 'fields' attribute which returns an array of field names. Each field or a combination of fields can be searched using the syntax options shown below.",
                       |        "operationId": "list-searchable-fields",
                       |        "parameters": [
                       |          {
                       |            "name": "dataset",
                       |            "in": "path",
                       |            "description": "Name of the dataset.",
                       |            "required": true,
                       |            "example": "oa_citations",
                       |            "schema": {
                       |              "type": "string"
                       |            }
                       |          },
                       |          {
                       |            "name": "version",
                       |            "in": "path",
                       |            "description": "Version of the dataset.",
                       |            "required": true,
                       |            "example": "v1",
                       |            "schema": {
                       |              "type": "string"
                       |            }
                       |          }
                       |        ],
                       |        "responses": {
                       |          "200": {
                       |            "description": "The dataset API for the given version is found and it is accessible to consume.",
                       |            "content": {
                       |              "application/json": {
                       |                "schema": {
                       |                  "type": "string"
                       |                }
                       |              }
                       |            }
                       |          },
                       |          "404": {
                       |            "description": "The combination of dataset name and version is not found in the system or it is not published yet to be consumed by public.",
                       |            "content": {
                       |              "application/json": {
                       |                "schema": {
                       |                  "type": "string"
                       |                }
                       |              }
                       |            }
                       |          }
                       |        }
                       |      }
                       |    },
                       |    "/{dataset}/{version}/records": {
                       |      "post": {
                       |        "tags": [
                       |          "search"
                       |        ],
                       |        "summary": "Provides search capability for the data set with the given search criteria.",
                       |        "description": "This API is based on Solr/Lucene Search. The data is indexed using SOLR. This GET API returns the list of all the searchable field names that are in the Solr Index. Please see the 'fields' attribute which returns an array of field names. Each field or a combination of fields can be searched using the Solr/Lucene Syntax. Please refer https://lucene.apache.org/core/3_6_2/queryparsersyntax.html#Overview for the query syntax. List of field names that are searchable can be determined using above GET api.",
                       |        "operationId": "perform-search",
                       |        "parameters": [
                       |          {
                       |            "name": "version",
                       |            "in": "path",
                       |            "description": "Version of the dataset.",
                       |            "required": true,
                       |            "schema": {
                       |              "type": "string",
                       |              "default": "v1"
                       |            }
                       |          },
                       |          {
                       |            "name": "dataset",
                       |            "in": "path",
                       |            "description": "Name of the dataset. In this case, the default value is oa_citations",
                       |            "required": true,
                       |            "schema": {
                       |              "type": "string",
                       |              "default": "oa_citations"
                       |            }
                       |          }
                       |        ],
                       |        "responses": {
                       |          "200": {
                       |            "description": "successful operation",
                       |            "content": {
                       |              "application/json": {
                       |                "schema": {
                       |                  "type": "array",
                       |                  "items": {
                       |                    "type": "object",
                       |                    "additionalProperties": {
                       |                      "type": "object"
                       |                    }
                       |                  }
                       |                }
                       |              }
                       |            }
                       |          },
                       |          "404": {
                       |            "description": "No matching record found for the given criteria."
                       |          }
                       |        },
                       |        "requestBody": {
                       |          "content": {
                       |            "application/x-www-form-urlencoded": {
                       |              "schema": {
                       |                "type": "object",
                       |                "properties": {
                       |                  "criteria": {
                       |                    "description": "Uses Lucene Query Syntax in the format of propertyName:value, propertyName:[num1 TO num2] and date range format: propertyName:[yyyyMMdd TO yyyyMMdd]. In the response please see the 'docs' element which has the list of record objects. Each record structure would consist of all the fields and their corresponding values.",
                       |                    "type": "string",
                       |                    "default": "*:*"
                       |                  },
                       |                  "start": {
                       |                    "description": "Starting record number. Default value is 0.",
                       |                    "type": "integer",
                       |                    "default": 0
                       |                  },
                       |                  "rows": {
                       |                    "description": "Specify number of rows to be returned. If you run the search with default values, in the response you will see 'numFound' attribute which will tell the number of records available in the dataset.",
                       |                    "type": "integer",
                       |                    "default": 100
                       |                  }
                       |                },
                       |                "required": [
                       |                  "criteria"
                       |                ]
                       |              }
                       |            }
                       |          }
                       |        }
                       |      }
                       |    }
                       |  },
                       |  "components": {
                       |    "schemas": {
                       |      "dataSetList": {
                       |        "type": "object",
                       |        "properties": {
                       |          "total": {
                       |            "type": "integer"
                       |          },
                       |          "apis": {
                       |            "type": "array",
                       |            "items": {
                       |              "type": "object",
                       |              "properties": {
                       |                "apiKey": {
                       |                  "type": "string",
                       |                  "description": "To be used as a dataset parameter value"
                       |                },
                       |                "apiVersionNumber": {
                       |                  "type": "string",
                       |                  "description": "To be used as a version parameter value"
                       |                },
                       |                "apiUrl": {
                       |                  "type": "string",
                       |                  "format": "uriref",
                       |                  "description": "The URL describing the dataset's fields"
                       |                },
                       |                "apiDocumentationUrl": {
                       |                  "type": "string",
                       |                  "format": "uriref",
                       |                  "description": "A URL to the API console for each API"
                       |                }
                       |              }
                       |            }
                       |          }
                       |        }
                       |      }
                       |    }
                       |  }
                       |}""".stripMargin

    //no host case: https://github.com/OAI/OpenAPI-Specification/blob/main/examples/v3.0/api-with-examples.json
    val openApiV301NoHost = """{
                              |  "openapi": "3.0.0",
                              |  "info": {
                              |    "title": "Simple API overview",
                              |    "version": "2.0.0"
                              |  },
                              |  "paths": {
                              |    "/": {
                              |      "get": {
                              |        "operationId": "listVersionsv2",
                              |        "summary": "List API versions",
                              |        "responses": {
                              |          "200": {
                              |            "description": "200 response",
                              |            "content": {
                              |              "application/json": {
                              |                "examples": {
                              |                  "foo": {
                              |                    "value": {
                              |                      "versions": [
                              |                        {
                              |                          "status": "CURRENT",
                              |                          "updated": "2011-01-21T11:33:21Z",
                              |                          "id": "v2.0",
                              |                          "links": [
                              |                            {
                              |                              "href": "http://127.0.0.1:8774/v2/",
                              |                              "rel": "self"
                              |                            }
                              |                          ]
                              |                        },
                              |                        {
                              |                          "status": "EXPERIMENTAL",
                              |                          "updated": "2013-07-23T11:33:21Z",
                              |                          "id": "v3.0",
                              |                          "links": [
                              |                            {
                              |                              "href": "http://127.0.0.1:8774/v3/",
                              |                              "rel": "self"
                              |                            }
                              |                          ]
                              |                        }
                              |                      ]
                              |                    }
                              |                  }
                              |                }
                              |              }
                              |            }
                              |          },
                              |          "300": {
                              |            "description": "300 response",
                              |            "content": {
                              |              "application/json": {
                              |                "examples": {
                              |                  "foo": {
                              |                    "value": "{\n \"versions\": [\n       {\n         \"status\": \"CURRENT\",\n         \"updated\": \"2011-01-21T11:33:21Z\",\n         \"id\": \"v2.0\",\n         \"links\": [\n             {\n                 \"href\": \"http://127.0.0.1:8774/v2/\",\n                 \"rel\": \"self\"\n             }\n         ]\n     },\n     {\n         \"status\": \"EXPERIMENTAL\",\n         \"updated\": \"2013-07-23T11:33:21Z\",\n         \"id\": \"v3.0\",\n         \"links\": [\n             {\n                 \"href\": \"http://127.0.0.1:8774/v3/\",\n                 \"rel\": \"self\"\n             }\n         ]\n     }\n ]\n}\n"
                              |                  }
                              |                }
                              |              }
                              |            }
                              |          }
                              |        }
                              |      }
                              |    },
                              |    "/v2": {
                              |      "get": {
                              |        "operationId": "getVersionDetailsv2",
                              |        "summary": "Show API version details",
                              |        "responses": {
                              |          "200": {
                              |            "description": "200 response",
                              |            "content": {
                              |              "application/json": {
                              |                "examples": {
                              |                  "foo": {
                              |                    "value": {
                              |                      "version": {
                              |                        "status": "CURRENT",
                              |                        "updated": "2011-01-21T11:33:21Z",
                              |                        "media-types": [
                              |                          {
                              |                            "base": "application/xml",
                              |                            "type": "application/vnd.openstack.compute+xml;version=2"
                              |                          },
                              |                          {
                              |                            "base": "application/json",
                              |                            "type": "application/vnd.openstack.compute+json;version=2"
                              |                          }
                              |                        ],
                              |                        "id": "v2.0",
                              |                        "links": [
                              |                          {
                              |                            "href": "http://127.0.0.1:8774/v2/",
                              |                            "rel": "self"
                              |                          },
                              |                          {
                              |                            "href": "http://docs.openstack.org/api/openstack-compute/2/os-compute-devguide-2.pdf",
                              |                            "type": "application/pdf",
                              |                            "rel": "describedby"
                              |                          },
                              |                          {
                              |                            "href": "http://docs.openstack.org/api/openstack-compute/2/wadl/os-compute-2.wadl",
                              |                            "type": "application/vnd.sun.wadl+xml",
                              |                            "rel": "describedby"
                              |                          },
                              |                          {
                              |                            "href": "http://docs.openstack.org/api/openstack-compute/2/wadl/os-compute-2.wadl",
                              |                            "type": "application/vnd.sun.wadl+xml",
                              |                            "rel": "describedby"
                              |                          }
                              |                        ]
                              |                      }
                              |                    }
                              |                  }
                              |                }
                              |              }
                              |            }
                              |          },
                              |          "203": {
                              |            "description": "203 response",
                              |            "content": {
                              |              "application/json": {
                              |                "examples": {
                              |                  "foo": {
                              |                    "value": {
                              |                      "version": {
                              |                        "status": "CURRENT",
                              |                        "updated": "2011-01-21T11:33:21Z",
                              |                        "media-types": [
                              |                          {
                              |                            "base": "application/xml",
                              |                            "type": "application/vnd.openstack.compute+xml;version=2"
                              |                          },
                              |                          {
                              |                            "base": "application/json",
                              |                            "type": "application/vnd.openstack.compute+json;version=2"
                              |                          }
                              |                        ],
                              |                        "id": "v2.0",
                              |                        "links": [
                              |                          {
                              |                            "href": "http://23.253.228.211:8774/v2/",
                              |                            "rel": "self"
                              |                          },
                              |                          {
                              |                            "href": "http://docs.openstack.org/api/openstack-compute/2/os-compute-devguide-2.pdf",
                              |                            "type": "application/pdf",
                              |                            "rel": "describedby"
                              |                          },
                              |                          {
                              |                            "href": "http://docs.openstack.org/api/openstack-compute/2/wadl/os-compute-2.wadl",
                              |                            "type": "application/vnd.sun.wadl+xml",
                              |                            "rel": "describedby"
                              |                          }
                              |                        ]
                              |                      }
                              |                    }
                              |                  }
                              |                }
                              |              }
                              |            }
                              |          }
                              |        }
                              |      }
                              |    }
                              |  }
                              |}""".stripMargin
    
    
    //  Swagger2.0
    //  https://github.com/OAI/OpenAPI-Specification/blob/main/examples/v2.0/json/petstore.json
    val openApiV20 ="""{
                      |  "swagger": "2.0",
                      |  "info": {
                      |    "version": "1.0.0",
                      |    "title": "Swagger Petstore",
                      |    "license": {
                      |      "name": "MIT"
                      |    }
                      |  },
                      |  "host": "petstore.swagger.io",
                      |  "basePath": "/v1",
                      |  "schemes": [
                      |    "http"
                      |  ],
                      |  "consumes": [
                      |    "application/json"
                      |  ],
                      |  "produces": [
                      |    "application/json"
                      |  ],
                      |  "paths": {
                      |    "/pets": {
                      |      "get": {
                      |        "summary": "List all pets",
                      |        "operationId": "listPets",
                      |        "tags": [
                      |          "pets"
                      |        ],
                      |        "parameters": [
                      |          {
                      |            "name": "limit",
                      |            "in": "query",
                      |            "description": "How many items to return at one time (max 100)",
                      |            "required": false,
                      |            "type": "integer",
                      |            "format": "int32"
                      |          }
                      |        ],
                      |        "responses": {
                      |          "200": {
                      |            "description": "An paged array of pets",
                      |            "headers": {
                      |              "x-next": {
                      |                "type": "string",
                      |                "description": "A link to the next page of responses"
                      |              }
                      |            },
                      |            "schema": {
                      |              "$ref": "#/definitions/Pets"
                      |            }
                      |          },
                      |          "default": {
                      |            "description": "unexpected error",
                      |            "schema": {
                      |              "$ref": "#/definitions/Error"
                      |            }
                      |          }
                      |        }
                      |      },
                      |      "post": {
                      |        "summary": "Create a pet",
                      |        "operationId": "createPets",
                      |        "tags": [
                      |          "pets"
                      |        ],
                      |        "responses": {
                      |          "201": {
                      |            "description": "Null response"
                      |          },
                      |          "default": {
                      |            "description": "unexpected error",
                      |            "schema": {
                      |              "$ref": "#/definitions/Error"
                      |            }
                      |          }
                      |        }
                      |      }
                      |    },
                      |    "/pets/{petId}": {
                      |      "get": {
                      |        "summary": "Info for a specific pet",
                      |        "operationId": "showPetById",
                      |        "tags": [
                      |          "pets"
                      |        ],
                      |        "parameters": [
                      |          {
                      |            "name": "petId",
                      |            "in": "path",
                      |            "required": true,
                      |            "description": "The id of the pet to retrieve",
                      |            "type": "string"
                      |          }
                      |        ],
                      |        "responses": {
                      |          "200": {
                      |            "description": "Expected response to a valid request",
                      |            "schema": {
                      |              "$ref": "#/definitions/Pets"
                      |            }
                      |          },
                      |          "default": {
                      |            "description": "unexpected error",
                      |            "schema": {
                      |              "$ref": "#/definitions/Error"
                      |            }
                      |          }
                      |        }
                      |      }
                      |    }
                      |  },
                      |  "definitions": {
                      |    "Pet": {
                      |      "required": [
                      |        "id",
                      |        "name"
                      |      ],
                      |      "properties": {
                      |        "id": {
                      |          "type": "integer",
                      |          "format": "int64"
                      |        },
                      |        "name": {
                      |          "type": "string"
                      |        },
                      |        "tag": {
                      |          "type": "string"
                      |        }
                      |      }
                      |    },
                      |    "Pets": {
                      |      "type": "array",
                      |      "items": {
                      |        "$ref": "#/definitions/Pet"
                      |      }
                      |    },
                      |    "Error": {
                      |      "required": [
                      |        "code",
                      |        "message"
                      |      ],
                      |      "properties": {
                      |        "code": {
                      |          "type": "integer",
                      |          "format": "int32"
                      |        },
                      |        "message": {
                      |          "type": "string"
                      |        }
                      |      }
                      |    }
                      |  }
                      |}""".stripMargin

    val openApiV20NoHost ="""{
                      |  "swagger": "2.0",
                      |  "info": {
                      |    "version": "1.0.0",
                      |    "title": "Swagger Petstore",
                      |    "license": {
                      |      "name": "MIT"
                      |    }
                      |  },
                      |  "basePath": "/v1",
                      |  "schemes": [
                      |    "http"
                      |  ],
                      |  "consumes": [
                      |    "application/json"
                      |  ],
                      |  "produces": [
                      |    "application/json"
                      |  ],
                      |  "paths": {
                      |    "/pets": {
                      |      "get": {
                      |        "summary": "List all pets",
                      |        "operationId": "listPets",
                      |        "tags": [
                      |          "pets"
                      |        ],
                      |        "parameters": [
                      |          {
                      |            "name": "limit",
                      |            "in": "query",
                      |            "description": "How many items to return at one time (max 100)",
                      |            "required": false,
                      |            "type": "integer",
                      |            "format": "int32"
                      |          }
                      |        ],
                      |        "responses": {
                      |          "200": {
                      |            "description": "An paged array of pets",
                      |            "headers": {
                      |              "x-next": {
                      |                "type": "string",
                      |                "description": "A link to the next page of responses"
                      |              }
                      |            },
                      |            "schema": {
                      |              "$ref": "#/definitions/Pets"
                      |            }
                      |          },
                      |          "default": {
                      |            "description": "unexpected error",
                      |            "schema": {
                      |              "$ref": "#/definitions/Error"
                      |            }
                      |          }
                      |        }
                      |      },
                      |      "post": {
                      |        "summary": "Create a pet",
                      |        "operationId": "createPets",
                      |        "tags": [
                      |          "pets"
                      |        ],
                      |        "responses": {
                      |          "201": {
                      |            "description": "Null response"
                      |          },
                      |          "default": {
                      |            "description": "unexpected error",
                      |            "schema": {
                      |              "$ref": "#/definitions/Error"
                      |            }
                      |          }
                      |        }
                      |      }
                      |    },
                      |    "/pets/{petId}": {
                      |      "get": {
                      |        "summary": "Info for a specific pet",
                      |        "operationId": "showPetById",
                      |        "tags": [
                      |          "pets"
                      |        ],
                      |        "parameters": [
                      |          {
                      |            "name": "petId",
                      |            "in": "path",
                      |            "required": true,
                      |            "description": "The id of the pet to retrieve",
                      |            "type": "string"
                      |          }
                      |        ],
                      |        "responses": {
                      |          "200": {
                      |            "description": "Expected response to a valid request",
                      |            "schema": {
                      |              "$ref": "#/definitions/Pets"
                      |            }
                      |          },
                      |          "default": {
                      |            "description": "unexpected error",
                      |            "schema": {
                      |              "$ref": "#/definitions/Error"
                      |            }
                      |          }
                      |        }
                      |      }
                      |    }
                      |  },
                      |  "definitions": {
                      |    "Pet": {
                      |      "required": [
                      |        "id",
                      |        "name"
                      |      ],
                      |      "properties": {
                      |        "id": {
                      |          "type": "integer",
                      |          "format": "int64"
                      |        },
                      |        "name": {
                      |          "type": "string"
                      |        },
                      |        "tag": {
                      |          "type": "string"
                      |        }
                      |      }
                      |    },
                      |    "Pets": {
                      |      "type": "array",
                      |      "items": {
                      |        "$ref": "#/definitions/Pet"
                      |      }
                      |    },
                      |    "Error": {
                      |      "required": [
                      |        "code",
                      |        "message"
                      |      ],
                      |      "properties": {
                      |        "code": {
                      |          "type": "integer",
                      |          "format": "int32"
                      |        },
                      |        "message": {
                      |          "type": "string"
                      |        }
                      |      }
                      |    }
                      |  }
                      |}""".stripMargin
    
    val newHost ="obp_mock"

    val result1 = DynamicEndpointHelper.changeOpenApiVersionHost(openApiV301, newHost:String)
    result1 contains newHost shouldBe(true)
    
    val result2 = DynamicEndpointHelper.changeOpenApiVersionHost(openApiV301NoHost, newHost:String)
    result2 contains newHost shouldBe(true)
    
    val result3 = DynamicEndpointHelper.changeOpenApiVersionHost(openApiV20, newHost:String)
    result3 contains newHost shouldBe(true)

    val result4 = DynamicEndpointHelper.changeOpenApiVersionHost(openApiV20NoHost, newHost:String)
    result4 contains newHost shouldBe(true)
  }
}
