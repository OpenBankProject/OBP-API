package code.api.v4_0_0

import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole._
import code.api.util.ErrorMessages.{DynamicEndpointExists, EndpointMappingNotFoundByOperationId, InvalidMyDynamicEndpointUser, UserHasMissingRoles, UserNotLoggedIn}
import code.api.util.ExampleValue
import code.api.v1_4_0.JSONFactory1_4_0.ResourceDocsJson
import code.api.v4_0_0.OBPAPI4_0_0.Implementations4_0_0
import code.api.dynamic.endpoint.APIMethodsDynamicEndpoint.ImplementationsDynamicEndpoint
import code.entitlement.Entitlement
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.ApiVersion
import net.liftweb.json.JArray
import net.liftweb.json.JsonAST.{JField, JString}
import net.liftweb.json.Serialization.write
import org.scalatest.Tag

class DynamicEndpointsTest extends V400ServerSetup {
  /**
    * Test tags
    * Example: To run tests with tag "getPermissions":
    * 	mvn test -D tagsToInclude
    *
    *  This is made possible by the scalatest maven plugin
    */
  object VersionOfApi extends Tag(ApiVersion.v4_0_0.toString)
  object ApiEndpoint1 extends Tag(nameOf(Implementations4_0_0.createDynamicEndpoint))
  object ApiEndpoint2 extends Tag(nameOf(Implementations4_0_0.getDynamicEndpoints))
  object ApiEndpoint3 extends Tag(nameOf(Implementations4_0_0.getDynamicEndpoint))
  object ApiEndpoint4 extends Tag(nameOf(Implementations4_0_0.deleteDynamicEndpoint))
  object ApiEndpoint5 extends Tag(nameOf(Implementations4_0_0.getMyDynamicEndpoints))
  object ApiEndpoint6 extends Tag(nameOf(Implementations4_0_0.deleteMyDynamicEndpoint))
  object ApiEndpoint7 extends Tag(nameOf(Implementations4_0_0.updateDynamicEndpointHost))
  object ApiEndpoint8 extends Tag(nameOf(ImplementationsDynamicEndpoint.dynamicEndpoint))
  object ApiEndpoint9 extends Tag(nameOf(Implementations4_0_0.createBankLevelDynamicEndpoint))
  object ApiEndpoint10 extends Tag(nameOf(Implementations4_0_0.getBankLevelDynamicEndpoints))
  object ApiEndpoint11 extends Tag(nameOf(Implementations4_0_0.getBankLevelDynamicEndpoint))
  object ApiEndpoint12 extends Tag(nameOf(Implementations4_0_0.deleteBankLevelDynamicEndpoint))

  val postDynamicEndpointSwagger = ExampleValue.dynamicEndpointSwagger

  feature(s"test $ApiEndpoint9, $ApiEndpoint10, $ApiEndpoint11, $ApiEndpoint12 version $VersionOfApi") {

    scenario(s"If we create one entity for system, we should not allow to create the bank level as the same entity," +
      s" otherwise it will break the roles", ApiEndpoint1,ApiEndpoint9, VersionOfApi) {
      When("We make a request v4.0.0")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, canCreateDynamicEndpoint.toString)
      val requestSystemLevel = (v4_0_0_Request / "management" / "dynamic-endpoints").POST<@ (user1)
      val responseSystemLevel = makePostRequest(requestSystemLevel, postDynamicEndpointSwagger)
      Then("We should get a 201")
      responseSystemLevel.code should equal(201)
      responseSystemLevel.body.toString contains("dynamic_endpoint_id") should be (true)
      
//      TODO , Need to think about if we should allow to create same entity as the bank level.
//      When("We make a request v4.0.0")
//      Entitlement.entitlement.vend.addEntitlement(testBankId1.value, resourceUser1.userId, canCreateBankLevelDynamicEndpoint.toString)
//      val request = (v4_0_0_Request / "management" /"banks"/testBankId1.value/ "dynamic-endpoints").POST<@ (user1)
//      val responseWithRole = makePostRequest(request, postDynamicEndpointSwagger)
//      Then("We should get a 400")
//      responseWithRole.code should equal(400)
//      responseWithRole.body.toString contains(DynamicEndpointExists) should be (true)
    }

    scenario(s"added the test case api-with-examples.json", ApiEndpoint1,VersionOfApi) {
//      https://github.com/OAI/OpenAPI-Specification/blob/main/examples/v3.0/api-with-examples.json
      val openApi301= """{
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
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, canCreateDynamicEndpoint.toString)
      val requestSystemLevel = (v4_0_0_Request / "management" / "dynamic-endpoints").POST<@ (user1)
      val responseSystemLevel = makePostRequest(requestSystemLevel, openApi301)
      Then("We should get a 201")
      responseSystemLevel.code should equal(201)
      responseSystemLevel.body.toString contains("dynamic_endpoint_id") should be (true)

      val requestObpResourceDoc = (v4_0_0_Request / "resource-docs" / "v4.0.0" / "obp").GET
      val responseObpResourceDoc = makeGetRequest(requestObpResourceDoc)
      responseObpResourceDoc.code should equal(200)
      
    }

    scenario(s"added the test case callback-example.json", ApiEndpoint1,VersionOfApi) {
      // https://github.com/OAI/OpenAPI-Specification/blob/main/examples/v3.0/callback-example.json
      val openApi301= """{
                        |  "openapi": "3.0.0",
                        |  "info": {
                        |    "title": "Callback Example",
                        |    "version": "1.0.0"
                        |  },
                        |  "paths": {
                        |    "/streams": {
                        |      "post": {
                        |        "description": "subscribes a client to receive out-of-band data",
                        |        "parameters": [
                        |          {
                        |            "name": "callbackUrl",
                        |            "in": "query",
                        |            "required": true,
                        |            "description": "the location where data will be sent.  Must be network accessible\nby the source server\n",
                        |            "schema": {
                        |              "type": "string",
                        |              "format": "uri",
                        |              "example": "https://tonys-server.com"
                        |            }
                        |          }
                        |        ],
                        |        "responses": {
                        |          "201": {
                        |            "description": "subscription successfully created",
                        |            "content": {
                        |              "application/json": {
                        |                "schema": {
                        |                  "description": "subscription information",
                        |                  "required": [
                        |                    "subscriptionId"
                        |                  ],
                        |                  "properties": {
                        |                    "subscriptionId": {
                        |                      "description": "this unique identifier allows management of the subscription",
                        |                      "type": "string",
                        |                      "example": "2531329f-fb09-4ef7-887e-84e648214436"
                        |                    }
                        |                  }
                        |                }
                        |              }
                        |            }
                        |          }
                        |        },
                        |        "callbacks": {
                        |          "onData": {
                        |            "{$request.query.callbackUrl}/data": {
                        |              "post": {
                        |                "requestBody": {
                        |                  "description": "subscription payload",
                        |                  "content": {
                        |                    "application/json": {
                        |                      "schema": {
                        |                        "type": "object",
                        |                        "properties": {
                        |                          "timestamp": {
                        |                            "type": "string",
                        |                            "format": "date-time"
                        |                          },
                        |                          "userData": {
                        |                            "type": "string"
                        |                          }
                        |                        }
                        |                      }
                        |                    }
                        |                  }
                        |                },
                        |                "responses": {
                        |                  "202": {
                        |                    "description": "Your server implementation should return this HTTP status code\nif the data was received successfully\n"
                        |                  },
                        |                  "204": {
                        |                    "description": "Your server should return this HTTP status code if no longer interested\nin further updates\n"
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
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, canCreateDynamicEndpoint.toString)
      val requestSystemLevel = (v4_0_0_Request / "management" / "dynamic-endpoints").POST<@ (user1)
      val responseSystemLevel = makePostRequest(requestSystemLevel, openApi301)
      Then("We should get a 201")
      responseSystemLevel.code should equal(201)
      responseSystemLevel.body.toString contains("dynamic_endpoint_id") should be (true)


      val requestObpResourceDoc = (v4_0_0_Request / "resource-docs" / "v4.0.0" / "obp").GET
      val responseObpResourceDoc = makeGetRequest(requestObpResourceDoc)
      responseObpResourceDoc.code should equal(200)
      
    }

    scenario(s"added the test case link-example.json", ApiEndpoint1,VersionOfApi) {
      // https://github.com/OAI/OpenAPI-Specification/blob/main/examples/v3.0/link-example.json
      val openApi301= """{
                        |  "openapi": "3.0.0",
                        |  "info": {
                        |    "title": "Link Example",
                        |    "version": "1.0.0"
                        |  },
                        |  "paths": {
                        |    "/2.0/users/{username}": {
                        |      "get": {
                        |        "operationId": "getUserByName",
                        |        "parameters": [
                        |          {
                        |            "name": "username",
                        |            "in": "path",
                        |            "required": true,
                        |            "schema": {
                        |              "type": "string"
                        |            }
                        |          }
                        |        ],
                        |        "responses": {
                        |          "200": {
                        |            "description": "The User",
                        |            "content": {
                        |              "application/json": {
                        |                "schema": {
                        |                  "$ref": "#/components/schemas/user"
                        |                }
                        |              }
                        |            },
                        |            "links": {
                        |              "userRepositories": {
                        |                "$ref": "#/components/links/UserRepositories"
                        |              }
                        |            }
                        |          }
                        |        }
                        |      }
                        |    },
                        |    "/2.0/repositories/{username}": {
                        |      "get": {
                        |        "operationId": "getRepositoriesByOwner",
                        |        "parameters": [
                        |          {
                        |            "name": "username",
                        |            "in": "path",
                        |            "required": true,
                        |            "schema": {
                        |              "type": "string"
                        |            }
                        |          }
                        |        ],
                        |        "responses": {
                        |          "200": {
                        |            "description": "repositories owned by the supplied user",
                        |            "content": {
                        |              "application/json": {
                        |                "schema": {
                        |                  "type": "array",
                        |                  "items": {
                        |                    "$ref": "#/components/schemas/repository"
                        |                  }
                        |                }
                        |              }
                        |            },
                        |            "links": {
                        |              "userRepository": {
                        |                "$ref": "#/components/links/UserRepository"
                        |              }
                        |            }
                        |          }
                        |        }
                        |      }
                        |    },
                        |    "/2.0/repositories/{username}/{slug}": {
                        |      "get": {
                        |        "operationId": "getRepository",
                        |        "parameters": [
                        |          {
                        |            "name": "username",
                        |            "in": "path",
                        |            "required": true,
                        |            "schema": {
                        |              "type": "string"
                        |            }
                        |          },
                        |          {
                        |            "name": "slug",
                        |            "in": "path",
                        |            "required": true,
                        |            "schema": {
                        |              "type": "string"
                        |            }
                        |          }
                        |        ],
                        |        "responses": {
                        |          "200": {
                        |            "description": "The repository",
                        |            "content": {
                        |              "application/json": {
                        |                "schema": {
                        |                  "$ref": "#/components/schemas/repository"
                        |                }
                        |              }
                        |            },
                        |            "links": {
                        |              "repositoryPullRequests": {
                        |                "$ref": "#/components/links/RepositoryPullRequests"
                        |              }
                        |            }
                        |          }
                        |        }
                        |      }
                        |    },
                        |    "/2.0/repositories/{username}/{slug}/pullrequests": {
                        |      "get": {
                        |        "operationId": "getPullRequestsByRepository",
                        |        "parameters": [
                        |          {
                        |            "name": "username",
                        |            "in": "path",
                        |            "required": true,
                        |            "schema": {
                        |              "type": "string"
                        |            }
                        |          },
                        |          {
                        |            "name": "slug",
                        |            "in": "path",
                        |            "required": true,
                        |            "schema": {
                        |              "type": "string"
                        |            }
                        |          },
                        |          {
                        |            "name": "state",
                        |            "in": "query",
                        |            "schema": {
                        |              "type": "string",
                        |              "enum": [
                        |                "open",
                        |                "merged",
                        |                "declined"
                        |              ]
                        |            }
                        |          }
                        |        ],
                        |        "responses": {
                        |          "200": {
                        |            "description": "an array of pull request objects",
                        |            "content": {
                        |              "application/json": {
                        |                "schema": {
                        |                  "type": "array",
                        |                  "items": {
                        |                    "$ref": "#/components/schemas/pullrequest"
                        |                  }
                        |                }
                        |              }
                        |            }
                        |          }
                        |        }
                        |      }
                        |    },
                        |    "/2.0/repositories/{username}/{slug}/pullrequests/{pid}": {
                        |      "get": {
                        |        "operationId": "getPullRequestsById",
                        |        "parameters": [
                        |          {
                        |            "name": "username",
                        |            "in": "path",
                        |            "required": true,
                        |            "schema": {
                        |              "type": "string"
                        |            }
                        |          },
                        |          {
                        |            "name": "slug",
                        |            "in": "path",
                        |            "required": true,
                        |            "schema": {
                        |              "type": "string"
                        |            }
                        |          },
                        |          {
                        |            "name": "pid",
                        |            "in": "path",
                        |            "required": true,
                        |            "schema": {
                        |              "type": "string"
                        |            }
                        |          }
                        |        ],
                        |        "responses": {
                        |          "200": {
                        |            "description": "a pull request object",
                        |            "content": {
                        |              "application/json": {
                        |                "schema": {
                        |                  "$ref": "#/components/schemas/pullrequest"
                        |                }
                        |              }
                        |            },
                        |            "links": {
                        |              "pullRequestMerge": {
                        |                "$ref": "#/components/links/PullRequestMerge"
                        |              }
                        |            }
                        |          }
                        |        }
                        |      }
                        |    },
                        |    "/2.0/repositories/{username}/{slug}/pullrequests/{pid}/merge": {
                        |      "post": {
                        |        "operationId": "mergePullRequest",
                        |        "parameters": [
                        |          {
                        |            "name": "username",
                        |            "in": "path",
                        |            "required": true,
                        |            "schema": {
                        |              "type": "string"
                        |            }
                        |          },
                        |          {
                        |            "name": "slug",
                        |            "in": "path",
                        |            "required": true,
                        |            "schema": {
                        |              "type": "string"
                        |            }
                        |          },
                        |          {
                        |            "name": "pid",
                        |            "in": "path",
                        |            "required": true,
                        |            "schema": {
                        |              "type": "string"
                        |            }
                        |          }
                        |        ],
                        |        "responses": {
                        |          "204": {
                        |            "description": "the PR was successfully merged"
                        |          }
                        |        }
                        |      }
                        |    }
                        |  },
                        |  "components": {
                        |    "links": {
                        |      "UserRepositories": {
                        |        "operationId": "getRepositoriesByOwner",
                        |        "parameters": {
                        |          "username": "$response.body#/username"
                        |        }
                        |      },
                        |      "UserRepository": {
                        |        "operationId": "getRepository",
                        |        "parameters": {
                        |          "username": "$response.body#/owner/username",
                        |          "slug": "$response.body#/slug"
                        |        }
                        |      },
                        |      "RepositoryPullRequests": {
                        |        "operationId": "getPullRequestsByRepository",
                        |        "parameters": {
                        |          "username": "$response.body#/owner/username",
                        |          "slug": "$response.body#/slug"
                        |        }
                        |      },
                        |      "PullRequestMerge": {
                        |        "operationId": "mergePullRequest",
                        |        "parameters": {
                        |          "username": "$response.body#/author/username",
                        |          "slug": "$response.body#/repository/slug",
                        |          "pid": "$response.body#/id"
                        |        }
                        |      }
                        |    },
                        |    "schemas": {
                        |      "user": {
                        |        "type": "object",
                        |        "properties": {
                        |          "username": {
                        |            "type": "string"
                        |          },
                        |          "uuid": {
                        |            "type": "string"
                        |          }
                        |        }
                        |      },
                        |      "repository": {
                        |        "type": "object",
                        |        "properties": {
                        |          "slug": {
                        |            "type": "string"
                        |          },
                        |          "owner": {
                        |            "$ref": "#/components/schemas/user"
                        |          }
                        |        }
                        |      },
                        |      "pullrequest": {
                        |        "type": "object",
                        |        "properties": {
                        |          "id": {
                        |            "type": "integer"
                        |          },
                        |          "title": {
                        |            "type": "string"
                        |          },
                        |          "repository": {
                        |            "$ref": "#/components/schemas/repository"
                        |          },
                        |          "author": {
                        |            "$ref": "#/components/schemas/user"
                        |          }
                        |        }
                        |      }
                        |    }
                        |  }
                        |}""".stripMargin
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, canCreateDynamicEndpoint.toString)
      val requestSystemLevel = (v4_0_0_Request / "management" / "dynamic-endpoints").POST<@ (user1)
      val responseSystemLevel = makePostRequest(requestSystemLevel, openApi301)
      Then("We should get a 201")
      responseSystemLevel.code should equal(201)
      responseSystemLevel.body.toString contains("dynamic_endpoint_id") should be (true)


      val requestObpResourceDoc = (v4_0_0_Request / "resource-docs" / "v4.0.0" / "obp").GET
      val responseObpResourceDoc = makeGetRequest(requestObpResourceDoc)
      responseObpResourceDoc.code should equal(200)
      
    }

    scenario(s"added the test case petstore-expanded.json", ApiEndpoint1,VersionOfApi) {
      // https://github.com/OAI/OpenAPI-Specification/blob/main/examples/v3.0/petstore-expanded.json
      val openApi301= """{
                        |  "openapi": "3.0.0",
                        |  "info": {
                        |    "version": "1.0.0",
                        |    "title": "Swagger Petstore",
                        |    "description": "A sample API that uses a petstore as an example to demonstrate features in the OpenAPI 3.0 specification",
                        |    "termsOfService": "http://swagger.io/terms/",
                        |    "contact": {
                        |      "name": "Swagger API Team",
                        |      "email": "apiteam@swagger.io",
                        |      "url": "http://swagger.io"
                        |    },
                        |    "license": {
                        |      "name": "Apache 2.0",
                        |      "url": "https://www.apache.org/licenses/LICENSE-2.0.html"
                        |    }
                        |  },
                        |  "servers": [
                        |    {
                        |      "url": "http://petstore.swagger.io/api"
                        |    }
                        |  ],
                        |  "paths": {
                        |    "/pets": {
                        |      "get": {
                        |        "description": "Returns all pets from the system that the user has access to\nNam sed condimentum est. Maecenas tempor sagittis sapien, nec rhoncus sem sagittis sit amet. Aenean at gravida augue, ac iaculis sem. Curabitur odio lorem, ornare eget elementum nec, cursus id lectus. Duis mi turpis, pulvinar ac eros ac, tincidunt varius justo. In hac habitasse platea dictumst. Integer at adipiscing ante, a sagittis ligula. Aenean pharetra tempor ante molestie imperdiet. Vivamus id aliquam diam. Cras quis velit non tortor eleifend sagittis. Praesent at enim pharetra urna volutpat venenatis eget eget mauris. In eleifend fermentum facilisis. Praesent enim enim, gravida ac sodales sed, placerat id erat. Suspendisse lacus dolor, consectetur non augue vel, vehicula interdum libero. Morbi euismod sagittis libero sed lacinia.\n\nSed tempus felis lobortis leo pulvinar rutrum. Nam mattis velit nisl, eu condimentum ligula luctus nec. Phasellus semper velit eget aliquet faucibus. In a mattis elit. Phasellus vel urna viverra, condimentum lorem id, rhoncus nibh. Ut pellentesque posuere elementum. Sed a varius odio. Morbi rhoncus ligula libero, vel eleifend nunc tristique vitae. Fusce et sem dui. Aenean nec scelerisque tortor. Fusce malesuada accumsan magna vel tempus. Quisque mollis felis eu dolor tristique, sit amet auctor felis gravida. Sed libero lorem, molestie sed nisl in, accumsan tempor nisi. Fusce sollicitudin massa ut lacinia mattis. Sed vel eleifend lorem. Pellentesque vitae felis pretium, pulvinar elit eu, euismod sapien.\n",
                        |        "operationId": "findPets",
                        |        "parameters": [
                        |          {
                        |            "name": "tags",
                        |            "in": "query",
                        |            "description": "tags to filter by",
                        |            "required": false,
                        |            "style": "form",
                        |            "schema": {
                        |              "type": "array",
                        |              "items": {
                        |                "type": "string"
                        |              }
                        |            }
                        |          },
                        |          {
                        |            "name": "limit",
                        |            "in": "query",
                        |            "description": "maximum number of results to return",
                        |            "required": false,
                        |            "schema": {
                        |              "type": "integer",
                        |              "format": "int32"
                        |            }
                        |          }
                        |        ],
                        |        "responses": {
                        |          "200": {
                        |            "description": "pet response",
                        |            "content": {
                        |              "application/json": {
                        |                "schema": {
                        |                  "type": "array",
                        |                  "items": {
                        |                    "$ref": "#/components/schemas/Pet"
                        |                  }
                        |                }
                        |              }
                        |            }
                        |          },
                        |          "default": {
                        |            "description": "unexpected error",
                        |            "content": {
                        |              "application/json": {
                        |                "schema": {
                        |                  "$ref": "#/components/schemas/Error"
                        |                }
                        |              }
                        |            }
                        |          }
                        |        }
                        |      },
                        |      "post": {
                        |        "description": "Creates a new pet in the store. Duplicates are allowed",
                        |        "operationId": "addPet",
                        |        "requestBody": {
                        |          "description": "Pet to add to the store",
                        |          "required": true,
                        |          "content": {
                        |            "application/json": {
                        |              "schema": {
                        |                "$ref": "#/components/schemas/NewPet"
                        |              }
                        |            }
                        |          }
                        |        },
                        |        "responses": {
                        |          "200": {
                        |            "description": "pet response",
                        |            "content": {
                        |              "application/json": {
                        |                "schema": {
                        |                  "$ref": "#/components/schemas/Pet"
                        |                }
                        |              }
                        |            }
                        |          },
                        |          "default": {
                        |            "description": "unexpected error",
                        |            "content": {
                        |              "application/json": {
                        |                "schema": {
                        |                  "$ref": "#/components/schemas/Error"
                        |                }
                        |              }
                        |            }
                        |          }
                        |        }
                        |      }
                        |    },
                        |    "/pets/{id}": {
                        |      "get": {
                        |        "description": "Returns a user based on a single ID, if the user does not have access to the pet",
                        |        "operationId": "find pet by id",
                        |        "parameters": [
                        |          {
                        |            "name": "id",
                        |            "in": "path",
                        |            "description": "ID of pet to fetch",
                        |            "required": true,
                        |            "schema": {
                        |              "type": "integer",
                        |              "format": "int64"
                        |            }
                        |          }
                        |        ],
                        |        "responses": {
                        |          "200": {
                        |            "description": "pet response",
                        |            "content": {
                        |              "application/json": {
                        |                "schema": {
                        |                  "$ref": "#/components/schemas/Pet"
                        |                }
                        |              }
                        |            }
                        |          },
                        |          "default": {
                        |            "description": "unexpected error",
                        |            "content": {
                        |              "application/json": {
                        |                "schema": {
                        |                  "$ref": "#/components/schemas/Error"
                        |                }
                        |              }
                        |            }
                        |          }
                        |        }
                        |      },
                        |      "delete": {
                        |        "description": "deletes a single pet based on the ID supplied",
                        |        "operationId": "deletePet",
                        |        "parameters": [
                        |          {
                        |            "name": "id",
                        |            "in": "path",
                        |            "description": "ID of pet to delete",
                        |            "required": true,
                        |            "schema": {
                        |              "type": "integer",
                        |              "format": "int64"
                        |            }
                        |          }
                        |        ],
                        |        "responses": {
                        |          "204": {
                        |            "description": "pet deleted"
                        |          },
                        |          "default": {
                        |            "description": "unexpected error",
                        |            "content": {
                        |              "application/json": {
                        |                "schema": {
                        |                  "$ref": "#/components/schemas/Error"
                        |                }
                        |              }
                        |            }
                        |          }
                        |        }
                        |      }
                        |    }
                        |  },
                        |  "components": {
                        |    "schemas": {
                        |      "Pet": {
                        |        "allOf": [
                        |          {
                        |            "$ref": "#/components/schemas/NewPet"
                        |          },
                        |          {
                        |            "type": "object",
                        |            "required": [
                        |              "id"
                        |            ],
                        |            "properties": {
                        |              "id": {
                        |                "type": "integer",
                        |                "format": "int64"
                        |              }
                        |            }
                        |          }
                        |        ]
                        |      },
                        |      "NewPet": {
                        |        "type": "object",
                        |        "required": [
                        |          "name"
                        |        ],
                        |        "properties": {
                        |          "name": {
                        |            "type": "string"
                        |          },
                        |          "tag": {
                        |            "type": "string"
                        |          }
                        |        }
                        |      },
                        |      "Error": {
                        |        "type": "object",
                        |        "required": [
                        |          "code",
                        |          "message"
                        |        ],
                        |        "properties": {
                        |          "code": {
                        |            "type": "integer",
                        |            "format": "int32"
                        |          },
                        |          "message": {
                        |            "type": "string"
                        |          }
                        |        }
                        |      }
                        |    }
                        |  }
                        |}""".stripMargin
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, canCreateDynamicEndpoint.toString)
      val requestSystemLevel = (v4_0_0_Request / "management" / "dynamic-endpoints").POST<@ (user1)
      val responseSystemLevel = makePostRequest(requestSystemLevel, openApi301)
      Then("We should get a 201")
      responseSystemLevel.code should equal(201)
      responseSystemLevel.body.toString contains("dynamic_endpoint_id") should be (true)


      val requestObpResourceDoc = (v4_0_0_Request / "resource-docs" / "v4.0.0" / "obp").GET
      val responseObpResourceDoc = makeGetRequest(requestObpResourceDoc)
      responseObpResourceDoc.code should equal(200)
      
    }

    scenario(s"added the test case petstore.json", ApiEndpoint1,VersionOfApi) {
      // https://github.com/OAI/OpenAPI-Specification/blob/main/examples/v3.0/petstore.json
      val openApi301= """{
                        |  "openapi": "3.0.0",
                        |  "info": {
                        |    "version": "1.0.0",
                        |    "title": "Swagger Petstore",
                        |    "license": {
                        |      "name": "MIT"
                        |    }
                        |  },
                        |  "servers": [
                        |    {
                        |      "url": "http://petstore.swagger.io/v1"
                        |    }
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
                        |            "schema": {
                        |              "type": "integer",
                        |              "format": "int32"
                        |            }
                        |          }
                        |        ],
                        |        "responses": {
                        |          "200": {
                        |            "description": "A paged array of pets",
                        |            "headers": {
                        |              "x-next": {
                        |                "description": "A link to the next page of responses",
                        |                "schema": {
                        |                  "type": "string"
                        |                }
                        |              }
                        |            },
                        |            "content": {
                        |              "application/json": {
                        |                "schema": {
                        |                  "$ref": "#/components/schemas/Pets"
                        |                }
                        |              }
                        |            }
                        |          },
                        |          "default": {
                        |            "description": "unexpected error",
                        |            "content": {
                        |              "application/json": {
                        |                "schema": {
                        |                  "$ref": "#/components/schemas/Error"
                        |                }
                        |              }
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
                        |            "content": {
                        |              "application/json": {
                        |                "schema": {
                        |                  "$ref": "#/components/schemas/Error"
                        |                }
                        |              }
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
                        |            "schema": {
                        |              "type": "string"
                        |            }
                        |          }
                        |        ],
                        |        "responses": {
                        |          "200": {
                        |            "description": "Expected response to a valid request",
                        |            "content": {
                        |              "application/json": {
                        |                "schema": {
                        |                  "$ref": "#/components/schemas/Pet"
                        |                }
                        |              }
                        |            }
                        |          },
                        |          "default": {
                        |            "description": "unexpected error",
                        |            "content": {
                        |              "application/json": {
                        |                "schema": {
                        |                  "$ref": "#/components/schemas/Error"
                        |                }
                        |              }
                        |            }
                        |          }
                        |        }
                        |      }
                        |    }
                        |  },
                        |  "components": {
                        |    "schemas": {
                        |      "Pet": {
                        |        "type": "object",
                        |        "required": [
                        |          "id",
                        |          "name"
                        |        ],
                        |        "properties": {
                        |          "id": {
                        |            "type": "integer",
                        |            "format": "int64"
                        |          },
                        |          "name": {
                        |            "type": "string"
                        |          },
                        |          "tag": {
                        |            "type": "string"
                        |          }
                        |        }
                        |      },
                        |      "Pets": {
                        |        "type": "array",
                        |        "items": {
                        |          "$ref": "#/components/schemas/Pet"
                        |        }
                        |      },
                        |      "Error": {
                        |        "type": "object",
                        |        "required": [
                        |          "code",
                        |          "message"
                        |        ],
                        |        "properties": {
                        |          "code": {
                        |            "type": "integer",
                        |            "format": "int32"
                        |          },
                        |          "message": {
                        |            "type": "string"
                        |          }
                        |        }
                        |      }
                        |    }
                        |  }
                        |}""".stripMargin
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, canCreateDynamicEndpoint.toString)
      val requestSystemLevel = (v4_0_0_Request / "management" / "dynamic-endpoints").POST<@ (user1)
      val responseSystemLevel = makePostRequest(requestSystemLevel, openApi301)
      Then("We should get a 201")
      responseSystemLevel.code should equal(201)
      responseSystemLevel.body.toString contains("dynamic_endpoint_id") should be (true)


      val requestObpResourceDoc = (v4_0_0_Request / "resource-docs" / "v4.0.0" / "obp").GET
      val responseObpResourceDoc = makeGetRequest(requestObpResourceDoc)
      responseObpResourceDoc.code should equal(200)
      
    }

    scenario(s"added the test case uspto.json", ApiEndpoint1,VersionOfApi) {
      //      https://github.com/OAI/OpenAPI-Specification/blob/main/examples/v3.0/uspto.json
      val openApi301= """{
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
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, canCreateDynamicEndpoint.toString)
      val requestSystemLevel = (v4_0_0_Request / "management" / "dynamic-endpoints").POST<@ (user1)
      val responseSystemLevel = makePostRequest(requestSystemLevel, openApi301)
      Then("We should get a 201")
      responseSystemLevel.code should equal(201)
      responseSystemLevel.body.toString contains("dynamic_endpoint_id") should be (true)


      val requestObpResourceDoc = (v4_0_0_Request / "resource-docs" / "v4.0.0" / "obp").GET
      val responseObpResourceDoc = makeGetRequest(requestObpResourceDoc)
      responseObpResourceDoc.code should equal(200)
      
    }

    scenario(s"$ApiEndpoint9 $ApiEndpoint10 $ApiEndpoint11 $ApiEndpoint12 test the bank level role", ApiEndpoint1, VersionOfApi) {
      When("We make a request v4.0.0")
      Entitlement.entitlement.vend.addEntitlement(testBankId1.value, resourceUser1.userId, canCreateBankLevelDynamicEndpoint.toString)
      val request = (v4_0_0_Request / "management" /"banks"/testBankId1.value/ "dynamic-endpoints").POST<@ (user1)
      val responseWithRole = makePostRequest(request, postDynamicEndpointSwagger)
      Then("We should get a 201")
      responseWithRole.code should equal(201)
      responseWithRole.body.toString contains("dynamic_endpoint_id") should be (true)
      val dynamicEndpointId = (responseWithRole.body \"dynamic_endpoint_id").asInstanceOf[JString].s
      

      Then(s"We call $ApiEndpoint10 - missing role ")
      val requestGet = (v4_0_0_Request / "management" /"banks"/testBankId1.value/ "dynamic-endpoints"/ dynamicEndpointId).GET<@ (user1)
      val responseGet = makeGetRequest(requestGet)
      responseGet.code should equal(403)
      responseGet.body.toString contains(UserHasMissingRoles) should be (true)

      Then(s"We call $ApiEndpoint10 - grant role ")
      
      {
        Entitlement.entitlement.vend.addEntitlement(testBankId1.value, resourceUser1.userId, canGetBankLevelDynamicEndpoint.toString)
        val requestGet = (v4_0_0_Request / "management" /"banks"/testBankId1.value/ "dynamic-endpoints"/ dynamicEndpointId).GET<@ (user1)
        val responseGet = makeGetRequest(requestGet)
        responseGet.code should equal(200)
        responseWithRole.body.toString contains("dynamic_endpoint_id") should be (true)
        responseWithRole.body.toString contains(s"/banks/${testBankId1.value}/accounts") should be (true)
        responseWithRole.body.toString contains(s"/banks/${testBankId1.value}/accounts/{account_id}") should be (true)
      }
      
      Then(s"We call $ApiEndpoint11 -- missing role")
      val requestGetAll = (v4_0_0_Request / "management" /"banks"/testBankId1.value/ "dynamic-endpoints").GET<@ (user1)
      val responseGetAll = makeGetRequest(requestGetAll)
      responseGetAll.code should equal(403)
      responseGetAll.body.toString contains(UserHasMissingRoles) should be (true)
      
      Then(s"We call $ApiEndpoint11 -- grant role")
      
      {
        Entitlement.entitlement.vend.addEntitlement(testBankId1.value, resourceUser1.userId, canGetBankLevelDynamicEndpoints.toString)
        val requestGetAll = (v4_0_0_Request / "management" /"banks"/testBankId1.value/ "dynamic-endpoints").GET<@ (user1)
        val responseGetAll = makeGetRequest(requestGetAll)
        responseGetAll.code should equal(200)
        responseWithRole.body.toString contains("dynamic_endpoint_id") should be (true)
        responseWithRole.body.toString contains(s"/banks/${testBankId1.value}/accounts") should be (true)
        responseWithRole.body.toString contains(s"/banks/${testBankId1.value}/accounts/{account_id}") should be (true)
      }
      
      Then(s"We call $ApiEndpoint12 -- Missing Role ")
      val requestDelete = (v4_0_0_Request / "management" /"banks"/testBankId1.value/ "dynamic-endpoints"/ dynamicEndpointId).DELETE<@ (user1)
      val responseDelete = makeDeleteRequest(requestDelete)
      responseDelete.code should equal(403)
      responseDelete.body.toString contains(UserHasMissingRoles) should be (true)
      
      
      Then("We call $ApiEndpoint12 -- grant Role ")
      
      {
        Entitlement.entitlement.vend.addEntitlement(testBankId1.value, resourceUser1.userId, canDeleteBankLevelDynamicEndpoint.toString)
        val requestDelete = (v4_0_0_Request / "management" /"banks"/testBankId1.value/ "dynamic-endpoints"/ dynamicEndpointId).DELETE<@ (user1)
        val responseDelete = makeDeleteRequest(requestDelete)
        responseDelete.code should equal(204)
      }
      
    }
  
    scenario(s"$ApiEndpoint9 test the bank level role", ApiEndpoint1, VersionOfApi) {
      When("We make a request v4.0.0")
      Entitlement.entitlement.vend.addEntitlement(testBankId1.value, resourceUser1.userId, canCreateBankLevelDynamicEndpoint.toString)
      val request = (v4_0_0_Request / "management" /"banks"/testBankId1.value/ "dynamic-endpoints").POST<@ (user1)
      val responseWithRole = makePostRequest(request, postDynamicEndpointSwagger)
      Then("We should get a 201")
      responseWithRole.code should equal(201)
      responseWithRole.body.toString contains("dynamic_endpoint_id") should be (true)

      Then("When we create the endpoint properly, then we can call the `accounts` endpoint")

      {
        val request = (dynamicEndpoint_Request / "banks"/testBankId1.value/  "accounts").POST<@ (user1)
        val response = makePostRequest(request, postDynamicEndpointSwagger)
        response.code should equal(201)
        response.body.toString contains("name") should be (true)
        response.body.toString contains("String") should be (true)
      }

      Then("we test the other user missing roles.")

      {
        val request = (dynamicEndpoint_Request / "banks"/testBankId1.value/  "accounts").POST<@ (user2)
        val response = makePostRequest(request, postDynamicEndpointSwagger)
        response.code should equal(403)
        response.body.toString contains(UserHasMissingRoles) should be (true)
      }


      Then("we test the duplicated urls.")

      {
        val duplicatedRequest = makePostRequest(request, postDynamicEndpointSwagger)
        Then("We should get a 400")
        duplicatedRequest.code should equal(400)
        duplicatedRequest.body.extract[ErrorMessage].message contains (DynamicEndpointExists) should be (true)
      }

    }

    scenario(s" $ApiEndpoint9 the the system level role", ApiEndpoint1, VersionOfApi) {
      Then("We grant the role to the user1")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, canCreateDynamicEndpoint.toString)
      When("We make a request v4.0.0")
      val request = (v4_0_0_Request / "management" /"banks"/testBankId1.value / "dynamic-endpoints").POST<@ (user1)

      val responseWithRole = makePostRequest(request, postDynamicEndpointSwagger)
      Then("We should get a 201")
      responseWithRole.code should equal(201)
      responseWithRole.body.toString contains("dynamic_endpoint_id") should be (true)

      {
        val request = (dynamicEndpoint_Request / "banks"/testBankId1.value/  "accounts").POST<@ (user1)
        val response = makePostRequest(request, postDynamicEndpointSwagger)
        response.code should equal(201)
        response.body.toString contains("name") should be (true)
        response.body.toString contains("String") should be (true)
      }

      val duplicatedRequest = makePostRequest(request, postDynamicEndpointSwagger)
      Then("We should get a 400")
      duplicatedRequest.code should equal(400)
      duplicatedRequest.body.extract[ErrorMessage].message.toString contains (DynamicEndpointExists) should be (true)
    }
    
  }
  

  feature(s"test $ApiEndpoint1 version $VersionOfApi - Unauthorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint1, VersionOfApi) {
      val postDynamicEndpointRequestBodyExample = ExampleValue.dynamicEndpointRequestBodyExample

      When("We make a request v4.0.0")
      val request400 = (v4_0_0_Request / "management" / "dynamic-endpoints").POST
      val response400 = makePostRequest(request400, write(postDynamicEndpointRequestBodyExample))
      Then("We should get a 401")
      response400.code should equal(401)
      response400.body.extract[ErrorMessage].message should equal(UserNotLoggedIn)
    }
  }

  feature(s"test $ApiEndpoint1 version $VersionOfApi - authorized access- missing role") {
    scenario("We will call the endpoint with user credentials", ApiEndpoint1, VersionOfApi) {
      val postDynamicEndpointRequestBodyExample = ExampleValue.dynamicEndpointRequestBodyExample

      When("We make a request v4.0.0")
      val request = (v4_0_0_Request / "management" / "dynamic-endpoints").POST<@ (user1)
      val response = makePostRequest(request, write(postDynamicEndpointRequestBodyExample))
      Then("We should get a 403")
      response.code should equal(403)
      response.body.extract[ErrorMessage].message.toString contains (UserHasMissingRoles) should be (true)
    }
  }

  feature(s"test $ApiEndpoint1 version $VersionOfApi - authorized access - with role - should be success!") {
    scenario("We will call the endpoint with user credentials", ApiEndpoint1, VersionOfApi) {
      When("We make a request v4.0.0")
      val postDynamicEndpointRequestBodyExample = ExampleValue.dynamicEndpointRequestBodyExample

      When("We make a request v4.0.0")
      val request = (v4_0_0_Request / "management" / "dynamic-endpoints").POST<@ (user1)
      val response = makePostRequest(request, write(postDynamicEndpointRequestBodyExample))
      Then("We should get a 403")
      response.code should equal(403)
      response.body.extract[ErrorMessage].message.toString contains (UserHasMissingRoles) should be (true)

      Then("We grant the role to the user1")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, canCreateDynamicEndpoint.toString)

      val responseWithRole = makePostRequest(request, write(postDynamicEndpointRequestBodyExample))
      Then("We should get a 201")
      responseWithRole.code should equal(201)
      responseWithRole.body.toString contains("dynamic_endpoint_id") should be (true)
    
    }
  }

  feature(s"test $ApiEndpoint2 version $VersionOfApi - Unauthorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint2, VersionOfApi) {
      When("We make a request v4.0.0")
      val request400 = (v4_0_0_Request / "management" / "dynamic-endpoints").GET
      val response400 = makeGetRequest(request400)
      Then("We should get a 401")
      response400.code should equal(401)
      response400.body.extract[ErrorMessage].message should equal(UserNotLoggedIn)
    }
  }

  feature(s"test $ApiEndpoint2 version $VersionOfApi - authorized access- missing role") {
    scenario("We will call the endpoint with user credentials", ApiEndpoint2, VersionOfApi) {
      When("We make a request v4.0.0")
      val request = (v4_0_0_Request / "management" / "dynamic-endpoints").GET<@ (user1)
      val response = makeGetRequest(request)
      Then("We should get a 400")
      response.code should equal(403)
      response.body.extract[ErrorMessage].message.toString contains (UserHasMissingRoles) should be (true)
    }
  }

  feature(s"test $ApiEndpoint2 version $VersionOfApi - authorized access - with role - should be success!") {
    scenario("We will call the endpoint with user credentials", ApiEndpoint2, VersionOfApi) {
      When("We make a request v4.0.0")
      val postDynamicEndpointRequestBodyExample = ExampleValue.dynamicEndpointRequestBodyExample

      When("We make a request v4.0.0")
      val request = (v4_0_0_Request / "management" / "dynamic-endpoints").POST<@ (user1)
      val response = makePostRequest(request, write(postDynamicEndpointRequestBodyExample))
      Then("We should get a 403")
      response.code should equal(403)
      response.body.extract[ErrorMessage].message.toString contains (UserHasMissingRoles) should be (true)

      Then("We grant the role to the user1")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanGetDynamicEndpoints.toString)
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanCreateDynamicEndpoint.toString)


      val newSwagger = postDynamicEndpointRequestBodyExample.transformField {
        case JField(name, value) if name.startsWith("/") => JField(s"$name/abc", value)
      }

      val responseWithRole = makePostRequest(request, write(newSwagger))
      Then("We should get a 201")
      responseWithRole.code should equal(201)


      val duplicatedRequest = makePostRequest(request, write(newSwagger))
      Then("We should get a 400")
      duplicatedRequest.code should equal(400)
      duplicatedRequest.body.extract[ErrorMessage].message.toString contains (DynamicEndpointExists) should be (true)


      val request400 = (v4_0_0_Request / "management" / "dynamic-endpoints").GET<@ (user1)
      val response400 = makeGetRequest(request400)
      response400.code should be (200)
    }
  }

  feature(s"test $ApiEndpoint3 version $VersionOfApi - Unauthorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint3, VersionOfApi) {
      When("We make a request v4.0.0")
      val request400 = (v4_0_0_Request / "management" / "dynamic-endpoints"/ "some-id").GET
      val response400 = makeGetRequest(request400)
      Then("We should get a 401")
      response400.code should equal(401)
      response400.body.extract[ErrorMessage].message should equal(UserNotLoggedIn)
    }
  }

  feature(s"test $ApiEndpoint3 version $VersionOfApi - authorized access- missing role") {
    scenario("We will call the endpoint with user credentials", ApiEndpoint3, VersionOfApi) {
      When("We make a request v4.0.0")
      val request = (v4_0_0_Request / "management" / "dynamic-endpoints" /"some-id").GET<@ (user1)
      val response = makeGetRequest(request)
      Then("We should get a 400")
      response.code should equal(403)
      response.body.extract[ErrorMessage].message.toString contains (UserHasMissingRoles) should be (true)
    }
  }

  feature(s"test $ApiEndpoint3 version $VersionOfApi - authorized access - with role - should be success!") {
    scenario("We will call the endpoint with user credentials", ApiEndpoint3, VersionOfApi) {
      When("We make a request v4.0.0")
      val postDynamicEndpointRequestBodyExample = ExampleValue.dynamicEndpointRequestBodyExample

      When("We make a request v4.0.0")
      val request = (v4_0_0_Request / "management" / "dynamic-endpoints").POST<@ (user1)
      val response = makePostRequest(request, write(postDynamicEndpointRequestBodyExample))
      Then("We should get a 403")
      response.code should equal(403)
      response.body.extract[ErrorMessage].message.toString contains (UserHasMissingRoles) should be (true)

      Then("We grant the role to the user1")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanGetDynamicEndpoint.toString)
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanCreateDynamicEndpoint.toString)

      val newSwagger = postDynamicEndpointRequestBodyExample.transformField {
        case JField(name, value) if name.startsWith("/") => JField(s"$name/def", value)
      }

      val responseWithRole = makePostRequest(request, write(newSwagger))
      Then("We should get a 201")
      responseWithRole.code should equal(201)


      val duplicatedRequest = makePostRequest(request, write(newSwagger))
      Then("We should get a 400")
      duplicatedRequest.code should equal(400)
      duplicatedRequest.body.extract[ErrorMessage].message.toString contains (DynamicEndpointExists) should be (true)


      val id = responseWithRole.body.\\("dynamic_endpoint_id").values.get("dynamic_endpoint_id").head.toString

      val request400 = (v4_0_0_Request / "management" / "dynamic-endpoints" /id).GET<@ (user1)
      val response400 = makeGetRequest(request400)
      response400.code should be (200)
      response400.body.toString contains("dynamic_endpoint_id") should be (true)


    }
  }

  feature(s"test $ApiEndpoint4 version $VersionOfApi - Unauthorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint4, VersionOfApi) {
      When("We make a request v4.0.0")
      val request400 = (v4_0_0_Request / "management" / "dynamic-endpoints"/ "some-id").DELETE
      val response400 = makeDeleteRequest(request400)
      Then("We should get a 401")
      response400.code should equal(401)
      response400.body.extract[ErrorMessage].message should equal(UserNotLoggedIn)
    }
  }

  feature(s"test $ApiEndpoint4 version $VersionOfApi - authorized access- missing role") {
    scenario("We will call the endpoint with user credentials", ApiEndpoint4, VersionOfApi) {
      When("We make a request v4.0.0")
      val request = (v4_0_0_Request / "management" / "dynamic-endpoints" /"some-id").DELETE<@ (user1)
      val response = makeDeleteRequest(request)
      Then("We should get a 400")
      response.code should equal(403)
      response.body.extract[ErrorMessage].message.toString contains (UserHasMissingRoles) should be (true)
    }
  }

  feature(s"test $ApiEndpoint4 version $VersionOfApi - authorized access - with role - should be success!") {
    scenario("We will call the endpoint with user credentials", ApiEndpoint4, VersionOfApi) {
      When("We make a request v4.0.0")
      val postDynamicEndpointRequestBodyExample = ExampleValue.dynamicEndpointRequestBodyExample

      When("We make a request v4.0.0")
      val request = (v4_0_0_Request / "management" / "dynamic-endpoints").POST<@ (user1)
      val response = makePostRequest(request, write(postDynamicEndpointRequestBodyExample))
      Then("We should get a 403")
      response.code should equal(403)
      response.body.extract[ErrorMessage].message.toString contains (UserHasMissingRoles) should be (true)

      Then("We grant the role to the user1")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanGetDynamicEndpoint.toString)
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanCreateDynamicEndpoint.toString)
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanDeleteDynamicEndpoint.toString)

      val newSwagger = postDynamicEndpointRequestBodyExample.transformField {
        case JField(name, value) if name.startsWith("/") => JField(s"$name/def2", value)
      }

      val responseWithRole = makePostRequest(request, write(newSwagger))
      Then("We should get a 201")
      responseWithRole.code should equal(201)


      val id = responseWithRole.body.\\("dynamic_endpoint_id").values.get("dynamic_endpoint_id").head.toString

      val request400 = (v4_0_0_Request / "management" / "dynamic-endpoints" /id).GET<@ (user1)
      val response400 = makeGetRequest(request400)
      response400.code should be (200)
      response400.body.toString contains("dynamic_endpoint_id") should be (true)
  


      val requestDelete = (v4_0_0_Request / "management" / "dynamic-endpoints" /id).DELETE<@ (user1)
      val responseDelete = makeDeleteRequest(requestDelete)
      responseDelete.code should be (204)

      val responseGetAgain = makeGetRequest(request400)
      responseGetAgain.code should be (404)


    }
  }

  feature(s"test $ApiEndpoint5 and $ApiEndpoint6 version $VersionOfApi - authorized access - should be success!") {
    scenario("We will call the endpoint with user credentials", ApiEndpoint5, ApiEndpoint6, VersionOfApi) {
      When("We make a request v4.0.0")
      val postDynamicEndpointRequestBodyExample = ExampleValue.dynamicEndpointRequestBodyExample

      val request = (v4_0_0_Request / "management" / "dynamic-endpoints").POST<@ (user1)

      Then("We grant the role to the user1")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanCreateDynamicEndpoint.toString)
      val newSwagger = postDynamicEndpointRequestBodyExample.transformField {
        case JField(name, value) if name.startsWith("/") => JField(s"$name/def2", value)
      }

      val responseWithRole = makePostRequest(request, write(newSwagger))
      Then("We should get a 201")
      responseWithRole.code should equal(201)


      val id = responseWithRole.body.\\("dynamic_endpoint_id").values.get("dynamic_endpoint_id").head.toString

      val request400 = (v4_0_0_Request / "my" / "dynamic-endpoints").GET<@ (user1)
      val response400 = makeGetRequest(request400)
      response400.code should be (200)
      response400.body.toString contains("dynamic_endpoint_id") should be (true)

      {
        // we use the wrong user2 to get the dynamic-endpoints
        val request400 = (v4_0_0_Request / "my" / "dynamic-endpoints").GET<@ (user2)
        val response400 = makeGetRequest(request400)
        Then("We should get a 200")
        response400.code should equal(200)
        val json = response400.body \ "dynamic_endpoints"
        val dynamicEntitiesGetJson = json.asInstanceOf[JArray]
        dynamicEntitiesGetJson.values should have size 0

      }

      {
        val requestDelete = (v4_0_0_Request / "my" / "dynamic-endpoints" /id).DELETE<@ (user2)
        val responseDelete = makeDeleteRequest(requestDelete)
        Then("We should get a 400")
        responseDelete.code should equal(400)
        responseDelete.body.extract[ErrorMessage].message should startWith (InvalidMyDynamicEndpointUser)
      }
      val requestDelete = (v4_0_0_Request / "my" / "dynamic-endpoints" /id).DELETE<@ (user1)
      val responseDelete = makeDeleteRequest(requestDelete)
      responseDelete.code should be (204)

      val responseDeleteAgain = makeDeleteRequest(requestDelete)
      responseDeleteAgain.code should be (404)
    }
  }

  feature(s"test $ApiEndpoint7 version $VersionOfApi - - Unauthorized access") {
    scenario("We will call the endpoint with user credentials", ApiEndpoint7, VersionOfApi) {
      When("We make a request v4.0.0")
      val postDynamicEndpointRequestBodyExample = ExampleValue.dynamicEndpointRequestBodyExample

      When("We make a request v4.0.0")
      val request = (v4_0_0_Request / "management" / "dynamic-endpoints").POST<@ (user1)
      val response = makePostRequest(request, write(postDynamicEndpointRequestBodyExample))
      Then("We should get a 403")
      response.code should equal(403)
      response.body.extract[ErrorMessage].message.toString contains (UserHasMissingRoles) should be (true)

      Then("We grant the role to the user1")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, canCreateDynamicEndpoint.toString)

      val responseWithRole = makePostRequest(request, write(postDynamicEndpointRequestBodyExample))
      Then("We should get a 201")
      responseWithRole.code should equal(201)
      responseWithRole.body.toString contains("dynamic_endpoint_id") should be (true)
      val dynamicEndpointId = (responseWithRole.body \"dynamic_endpoint_id").asInstanceOf[JString].s

      Then("We update the host")
      val dynamicEndpointHostJson = SwaggerDefinitionsJSON.dynamicEndpointHostJson400

      When("We make a request v4.0.0")
      val requestPut = (v4_0_0_Request / "management" / "dynamic-endpoints"/dynamicEndpointId/ "host").PUT
      val responsePut = makePutRequest(requestPut, write(postDynamicEndpointRequestBodyExample))
      Then("We should get a 401")
      responsePut.code should equal(401)
      responsePut.body.extract[ErrorMessage].message should equal(UserNotLoggedIn)
    }
  }


  feature(s"test $ApiEndpoint7 version $VersionOfApi - authorized access - missing role!") {
    scenario("We will call the endpoint with user credentials", ApiEndpoint7, VersionOfApi) {
      When("We make a request v4.0.0")
      val postDynamicEndpointRequestBodyExample = ExampleValue.dynamicEndpointRequestBodyExample

      When("We make a request v4.0.0")
      val request = (v4_0_0_Request / "management" / "dynamic-endpoints").POST<@ (user1)
      val response = makePostRequest(request, write(postDynamicEndpointRequestBodyExample))
      Then("We should get a 403")
      response.code should equal(403)
      response.body.extract[ErrorMessage].message.toString contains (UserHasMissingRoles) should be (true)

      Then("We grant the role to the user1")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, canCreateDynamicEndpoint.toString)

      val responseWithRole = makePostRequest(request, write(postDynamicEndpointRequestBodyExample))
      Then("We should get a 201")
      responseWithRole.code should equal(201)
      responseWithRole.body.toString contains("dynamic_endpoint_id") should be (true)
      val dynamicEndpointId = (responseWithRole.body \"dynamic_endpoint_id").asInstanceOf[JString].s

      Then("We update the host")
      val dynamicEndpointHostJson = SwaggerDefinitionsJSON.dynamicEndpointHostJson400

      When("We make a request v4.0.0")
      val requestPut = (v4_0_0_Request / "management" / "dynamic-endpoints"/dynamicEndpointId/ "host").PUT<@ (user1)
      val responsePut = makePutRequest(requestPut, write(postDynamicEndpointRequestBodyExample))
      Then("We should get a 403")
      responsePut.code should equal(403)
      responsePut.body.extract[ErrorMessage].message.toString contains (UserHasMissingRoles) should be (true)
      responsePut.body.extract[ErrorMessage].message.toString contains (CanUpdateDynamicEndpoint.toString()) should be (true)
    }
  }

  feature(s"test $ApiEndpoint7 version $VersionOfApi - authorized access - with role - should be success!") {
    scenario("We will call the endpoint with user credentials", ApiEndpoint7, VersionOfApi) {
      When("We make a request v4.0.0")
      val postDynamicEndpointRequestBodyExample = ExampleValue.dynamicEndpointRequestBodyExample

      When("We make a request v4.0.0")
      val request = (v4_0_0_Request / "management" / "dynamic-endpoints").POST<@ (user1)
      val response = makePostRequest(request, write(postDynamicEndpointRequestBodyExample))
      Then("We should get a 403")
      response.code should equal(403)
      response.body.extract[ErrorMessage].message.toString contains (UserHasMissingRoles) should be (true)

      Then("We grant the role to the user1")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, canCreateDynamicEndpoint.toString)

      val responseWithRole = makePostRequest(request, write(postDynamicEndpointRequestBodyExample))
      Then("We should get a 201")
      responseWithRole.code should equal(201)
      responseWithRole.body.toString contains("dynamic_endpoint_id") should be (true)
      val dynamicEndpointId = (responseWithRole.body \"dynamic_endpoint_id").asInstanceOf[JString].s

      Then("We update the host")
      val dynamicEndpointHostJson = SwaggerDefinitionsJSON.dynamicEndpointHostJson400

      When("We make a request v4.0.0")
      val requestPut = (v4_0_0_Request / "management" / "dynamic-endpoints"/dynamicEndpointId/ "host").PUT<@ (user1)
      val responsePut = makePutRequest(requestPut, write(postDynamicEndpointRequestBodyExample))
      Then("We should get a 403")
      response.code should equal(403)
      response.body.extract[ErrorMessage].message.toString contains (UserHasMissingRoles) should be (true)

      Then("We grant the role to the user1")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, canUpdateDynamicEndpoint.toString)

      When("We make a request v4.0.0")
      val responseWithRolePut = makePutRequest(requestPut, write(dynamicEndpointHostJson))
      Then("We should get a 201")
      responseWithRolePut.code should equal(201)
      (responseWithRolePut.body \ "host").asInstanceOf[JString].s shouldEqual (dynamicEndpointHostJson.host)

    }

    scenario("We will call the endpoint with user credentials - OpenAPI3.0", ApiEndpoint7, VersionOfApi) {
      When("We make a request v4.0.0")
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

      When("We make a request v4.0.0")
      val request = (v4_0_0_Request / "management" / "dynamic-endpoints").POST<@ (user1)
   
      Then("We grant the role to the user1")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, canCreateDynamicEndpoint.toString)

      val responseWithRole = makePostRequest(request, openApiV301)
      Then("We should get a 201")
      responseWithRole.code should equal(201)
      responseWithRole.body.toString contains("dynamic_endpoint_id") should be (true)
      val dynamicEndpointId = (responseWithRole.body \"dynamic_endpoint_id").asInstanceOf[JString].s

      Then("We update the host")
      val dynamicEndpointHostJson = SwaggerDefinitionsJSON.dynamicEndpointHostJson400

      When("We make a request v4.0.0")
      val requestPut = (v4_0_0_Request / "management" / "dynamic-endpoints"/dynamicEndpointId/ "host").PUT<@ (user1)

      Then("We grant the role to the user1")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, canUpdateDynamicEndpoint.toString)

      When("We make a request v4.0.0")
      val responseWithRolePut = makePutRequest(requestPut, write(dynamicEndpointHostJson))
      Then("We should get a 201")
      responseWithRolePut.code should equal(201)
      responseWithRolePut.body.toString contains dynamicEndpointHostJson.host should be (true)
    }

    scenario("We will call the endpoint with user credentials - OpenAPI3.0 no host in json", ApiEndpoint7, VersionOfApi) {
      When("We make a request v4.0.0")
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

      When("We make a request v4.0.0")
      val request = (v4_0_0_Request / "management" / "dynamic-endpoints").POST<@ (user1)

      Then("We grant the role to the user1")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, canCreateDynamicEndpoint.toString)

      val responseWithRole = makePostRequest(request, openApiV301NoHost)
      Then("We should get a 201")
      responseWithRole.code should equal(201)
      responseWithRole.body.toString contains("dynamic_endpoint_id") should be (true)
      val dynamicEndpointId = (responseWithRole.body \"dynamic_endpoint_id").asInstanceOf[JString].s

      Then("We update the host")
      val dynamicEndpointHostJson = SwaggerDefinitionsJSON.dynamicEndpointHostJson400

      When("We make a request v4.0.0")
      val requestPut = (v4_0_0_Request / "management" / "dynamic-endpoints"/dynamicEndpointId/ "host").PUT<@ (user1)

      Then("We grant the role to the user1")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, canUpdateDynamicEndpoint.toString)

      When("We make a request v4.0.0")
      val responseWithRolePut = makePutRequest(requestPut, write(dynamicEndpointHostJson))
      Then("We should get a 201")
      responseWithRolePut.code should equal(201)
      responseWithRolePut.body.toString contains dynamicEndpointHostJson.host should be (true)
    }
  }

  feature(s"test $ApiEndpoint1 and $ApiEndpoint8 version $VersionOfApi - authorized access - with role - should be success!") {
    scenario("we test new endpoints - system level", ApiEndpoint8, VersionOfApi) {
      When("We make a request v4.0.0")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, canCreateDynamicEndpoint.toString)
      val request = (v4_0_0_Request / "management" / "dynamic-endpoints").POST<@ (user1)
      val responseWithRole = makePostRequest(request, postDynamicEndpointSwagger)
      Then("We should get a 201")
      responseWithRole.code should equal(201)
      val dynamicEndpointId = (responseWithRole.body \"dynamic_endpoint_id").asInstanceOf[JString].s

      Then("we test authentication error")

      {
        val request = (dynamicEndpoint_Request / "accounts").POST
        val response = makePostRequest(request, postDynamicEndpointSwagger)
        response.code should equal(401)
        response.body.toString contains(UserNotLoggedIn) should be (true)
      }

      Then("we test missing role error")

      {
        val request = (dynamicEndpoint_Request / "accounts").POST<@ (user2)
        val response = makePostRequest(request, postDynamicEndpointSwagger)
        response.code should equal(403)
        response.body.toString contains(UserHasMissingRoles) should be (true)
      }

      Then("we test successful cases")

      {
        val request = (dynamicEndpoint_Request / "accounts").POST<@ (user1)
        val response = makePostRequest(request, postDynamicEndpointSwagger)
        response.code should equal(201)
        response.body.toString contains("name") should be (true)
        response.body.toString contains("String") should be (true)
      }


      Then(s"we test $ApiEndpoint7, if we change the host, then the response is different")

      {
        val dynamicEndpointHostJson = SwaggerDefinitionsJSON.dynamicEndpointHostJson400
        Then("We grant the role to the user1 and update the host")
        Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, canUpdateDynamicEndpoint.toString)
        val requestPut = (v4_0_0_Request / "management" / "dynamic-endpoints"/dynamicEndpointId/ "host").PUT<@ (user1)
        val responsePut = makePutRequest(requestPut, write(dynamicEndpointHostJson))

        Then("if we changed the host, the response should be the errors")
        val request = (dynamicEndpoint_Request / "accounts").POST<@ (user1)
        val response = makePostRequest(request, postDynamicEndpointSwagger)
        response.code should equal(404)
        response.body.toString contains(EndpointMappingNotFoundByOperationId) should be (true)
      }

    }

    scenario("we test new endpoints - bank level", ApiEndpoint8, VersionOfApi) {
      When("We make a request v4.0.0 with the role canCreateDynamicEndpoint")

      Then("First test the system Level role")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, canCreateDynamicEndpoint.toString)
      val request = (v4_0_0_Request / "management" /"banks"/testBankId1.value/ "dynamic-endpoints").POST<@ (user1)
      val responseWithRole = makePostRequest(request, postDynamicEndpointSwagger)
      Then("We should get a 201")
      responseWithRole.code should equal(201)
      val dynamicEndpointId = (responseWithRole.body \"dynamic_endpoint_id").asInstanceOf[JString].s

      Then("we test authentication error")

      {
        val request = (dynamicEndpoint_Request/"banks"/testBankId1.value / "accounts").POST
        val response = makePostRequest(request, postDynamicEndpointSwagger)
        response.code should equal(401)
        response.body.toString contains(UserNotLoggedIn) should be (true)
      }

      Then("we test missing role error")

      {
        val request = (dynamicEndpoint_Request/"banks"/testBankId1.value / "accounts").POST<@ (user2)
        val response = makePostRequest(request, postDynamicEndpointSwagger)
        response.code should equal(403)
        response.body.toString contains(UserHasMissingRoles) should be (true)
      }

      Then("we test successful cases")

      {
        val request = (dynamicEndpoint_Request /"banks"/testBankId1.value / "accounts").POST<@ (user1)
        val response = makePostRequest(request, postDynamicEndpointSwagger)
        response.code should equal(201)
        response.body.toString contains("name") should be (true)
        response.body.toString contains("String") should be (true)
      }


      Then(s"we test $ApiEndpoint7, if we change the host, then the response is different")

      {
        val dynamicEndpointHostJson = SwaggerDefinitionsJSON.dynamicEndpointHostJson400
        Then("We grant the role to the user1 and update the host")
        Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, canUpdateDynamicEndpoint.toString)
        val requestPut = (v4_0_0_Request / "management" / "dynamic-endpoints"/dynamicEndpointId/ "host").PUT<@ (user1)
        val responsePut = makePutRequest(requestPut, write(dynamicEndpointHostJson))

        Then("if we changed the host, the response should be the errors")
        val request = (dynamicEndpoint_Request /"banks"/testBankId1.value / "accounts").POST<@ (user1)
        val response = makePostRequest(request, postDynamicEndpointSwagger)
        response.code should equal(404)
        response.body.toString contains(EndpointMappingNotFoundByOperationId) should be (true)
      }

    }
  }


}
