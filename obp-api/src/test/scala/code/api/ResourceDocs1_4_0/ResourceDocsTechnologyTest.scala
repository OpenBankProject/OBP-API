package code.api.ResourceDocs1_4_0

import code.setup.{PropsReset, ServerSetup}
import net.liftweb.json.JsonAST.{JArray, JNothing, JNull, JString}

class ResourceDocsTechnologyTest extends ServerSetup with PropsReset {

  feature("ResourceDocs implemented_by.technology") {

    scenario("v6.0.0 resource-docs should include implemented_by.technology") {
      setPropsValues("resource_docs_requires_role" -> "false")

      val request = (baseRequest / "obp" / "v6.0.0" / "resource-docs" / "v6.0.0" / "obp").GET
      val response = makeGetRequest(request)

      response.code should equal(200)
      (response.body \ "resource_docs") match {
        case JArray(docs) =>
          val technology = docs.head \ "implemented_by" \ "technology"
          technology should equal(JString("lift"))
        case _ =>
          fail("Expected resource_docs field to be an array")
      }
    }

    scenario("v5.0.0 resource-docs should not include implemented_by.technology") {
      setPropsValues("resource_docs_requires_role" -> "false")

      val request = (baseRequest / "obp" / "v5.0.0" / "resource-docs" / "v5.0.0" / "obp").GET
      val response = makeGetRequest(request)

      response.code should equal(200)
      (response.body \ "resource_docs") match {
        case JArray(docs) =>
          val technology = docs.head \ "implemented_by" \ "technology"
          technology match {
            case JNothing | JNull => succeed
            case _ => fail("Expected implemented_by.technology to be absent for v5.0.0 resource-docs")
          }
        case _ =>
          fail("Expected resource_docs field to be an array")
      }
    }
  }
}

