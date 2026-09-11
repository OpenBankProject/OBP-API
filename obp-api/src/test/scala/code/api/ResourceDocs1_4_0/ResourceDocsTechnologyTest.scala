package code.api.ResourceDocs1_4_0

import org.json4s._
import code.api.Constant
import code.setup.{PropsReset, ServerSetup}
import com.openbankproject.commons.util.ApiVersion
import org.json4s.JsonAST.{JArray, JNothing, JNull, JString}

class ResourceDocsTechnologyTest extends ServerSetup with PropsReset {
  private val v600 = ApiVersion.v6_0_0.toString
  private val v500 = ApiVersion.v5_0_0.toString

  Feature("ResourceDocs implemented_by.technology") {

    Scenario(s"$v600 resource-docs should include implemented_by.technology") {
      setPropsValues("resource_docs_requires_role" -> "false")

      val request = (baseRequest / "obp" / v600 / "resource-docs" / v600 / "obp").GET
      val response = makeGetRequest(request)

      response.code should equal(200)
      (response.body \ "resource_docs") match {
        case JArray(docs) =>
          // At least one doc should have a technology field (http4s or liftweb).
          // v5.0.0 has no technology field at all; v6.0.0 exposes it for all docs.
          val hasTechnology = docs.exists { doc =>
            (doc \ "implemented_by" \ "technology") match {
              case JString(t) => t == Constant.TECHNOLOGY_HTTP4S || t == Constant.TECHNOLOGY_LIFTWEB
              case _          => false
            }
          }
          hasTechnology should be(true)
        case _ =>
          fail("Expected resource_docs field to be an array")
      }
    }

    Scenario(s"$v500 resource-docs should not include implemented_by.technology") {
      setPropsValues("resource_docs_requires_role" -> "false")

      val request = (baseRequest / "obp" / v500 / "resource-docs" / v500 / "obp").GET
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
