package code.api.v4_0_0

import code.DynamicData.DynamicDataT
import code.api.v4_0_0.dynamic.DynamicEndpointHelper
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
    val dynamicDataList = List(DataTest(Some("1"),"PetEntity",dataJsonString), DataTest(Some("2"),"PetEntity2",dataJsonString2))


    val expectedResult = ("PetEntity", "1")
    val result: (String, String) = DynamicEndpointHelper.findDynamicData(dynamicDataList: List[DynamicDataT], dynamicDataJson: JValue)

    result should equal(expectedResult)
  }

}
