package code.api.v6_0_0

import code.api.util.APIUtil._
import code.api.util.ErrorMessages._
import com.openbankproject.commons.util.ApiVersion
import org.scalatest.Tag

/**
 * Unit tests for EndpointAuthMode sealed trait and its integration with ResourceDoc.
 * Tests that the auth mode values are correctly defined and accessible.
 */
class EndpointAuthModeTest extends V600ServerSetup {

  object VersionOfApi extends Tag(ApiVersion.v6_0_0.toString)

  feature("EndpointAuthMode sealed trait") {

    scenario("All four auth modes should be defined", VersionOfApi) {
      val userOnly: EndpointAuthMode = UserOnly
      val appOnly: EndpointAuthMode = ApplicationOnly
      val userOrApp: EndpointAuthMode = UserOrApplication
      val userAndApp: EndpointAuthMode = UserAndApplication

      userOnly shouldBe a[EndpointAuthMode]
      appOnly shouldBe a[EndpointAuthMode]
      userOrApp shouldBe a[EndpointAuthMode]
      userAndApp shouldBe a[EndpointAuthMode]
    }

    scenario("verifyUserCredentials ResourceDoc should have UserOrApplication authMode", VersionOfApi) {
      val verifyCredsDocs = ResourceDoc.operationIdToResourceDoc.values().toArray
        .collect { case rd: ResourceDoc => rd }
        .filter(_.partialFunctionName == "verifyUserCredentials")

      verifyCredsDocs should not be empty
      verifyCredsDocs.foreach { doc =>
        doc.authMode should equal(UserOrApplication)
        doc.errorResponseBodies should contain(ApplicationNotIdentified)
      }
    }

    scenario("Default authMode should be UserOnly for existing endpoints", VersionOfApi) {
      // Pick a known existing endpoint that doesn't set authMode explicitly
      val getApiInfoDocs = ResourceDoc.operationIdToResourceDoc.values().toArray
        .collect { case rd: ResourceDoc => rd }
        .filter(_.partialFunctionName == "getApiInfo")

      getApiInfoDocs should not be empty
      getApiInfoDocs.foreach { doc =>
        doc.authMode should equal(UserOnly)
      }
    }

    scenario("handleAccessControlWithAuthMode should pass for empty roles", VersionOfApi) {
      val result = handleAccessControlWithAuthMode("", "", "", Nil, UserOnly)
      result should equal(true)
    }
  }
}
