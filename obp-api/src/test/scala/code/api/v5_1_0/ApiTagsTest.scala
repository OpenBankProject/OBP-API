package code.api.v5_1_0

import code.api.util.ErrorMessages.AuthenticatedUserIsRequired
import org.json4s.jvalue2extractable
import code.api.v5_1_0.OBPAPI5_1_0.Implementations5_1_0
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.ApiVersion
import org.scalatest.Tag

class ApiTagsTest extends V510ServerSetup {
  /**
    * Test tags
    * Example: To run tests with tag "getPermissions":
    * 	mvn test -D tagsToInclude
    *
    *  This is made possible by the scalatest maven plugin
    */
  object VersionOfApi extends Tag(ApiVersion.v5_1_0.toString)
  object GetApiTags extends Tag(nameOf(Implementations5_1_0.getApiTags))

  Feature(s"test ${GetApiTags}") {
    Scenario(s"it should return all the api tags", GetApiTags, VersionOfApi) {

      val requestGet = (v5_1_0_Request / "tags").GET

      // Anonymous call fails
      val anonymousResponseGet = makeGetRequest(requestGet)
      anonymousResponseGet.code should equal(200)
      anonymousResponseGet.body.extract[APITags].tags.length > 0 shouldBe (true)
      
    }
  }
  
}