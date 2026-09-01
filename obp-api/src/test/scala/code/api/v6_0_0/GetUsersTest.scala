package code.api.v6_0_0

import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole.CanGetAnyUser
import code.api.util.ErrorMessages
import code.api.util.ErrorMessages.UserHasMissingRoles
import code.api.v6_0_0.Http4s600.Implementations6_0_0
import code.entitlement.Entitlement
import code.setup.DefaultUsers
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.ApiVersion
import org.scalatest.Tag
import net.liftweb.common.Full

/**
 * Validation / error-path tests for `GET /obp/v6.0.0/users`.
 *
 * These cover auth, role, and query-parameter validation. They deliberately
 * do NOT exercise the Doobie+PostgreSQL search path — data-driven tests
 * (filters actually matching rows, sort ordering, pagination results) need
 * a real PostgreSQL test DB and should live in a separate suite.
 */
class GetUsersTest extends V600ServerSetup with DefaultUsers {

  object VersionOfApi extends Tag(ApiVersion.v6_0_0.toString)
  object ApiEndpoint extends Tag(nameOf(Implementations6_0_0.getUsers))

  feature(s"Get all Users - GET /obp/v6.0.0/users - $VersionOfApi") {

    scenario("Anonymous access should fail with 401", ApiEndpoint, VersionOfApi) {
      When("We make the request without authentication")
      val request = (v6_0_0_Request / "users").GET
      val response = makeGetRequest(request)

      Then("We should get a 401")
      response.code should equal(401)
      And("The error message should indicate authentication is required")
      response.body.extract[ErrorMessage].message should equal(ErrorMessages.AuthenticatedUserIsRequired)
    }

    scenario("Authenticated user without CanGetAnyUser role should fail with 403", ApiEndpoint, VersionOfApi) {
      When("We make the request as an authenticated user without the required role")
      val request = (v6_0_0_Request / "users").GET <@ (user1)
      val response = makeGetRequest(request)

      Then("We should get a 403")
      response.code should equal(403)
      And("The error message should indicate missing role")
      response.body.extract[ErrorMessage].message should equal(UserHasMissingRoles + CanGetAnyUser)
    }

    scenario("sort_by not in global whitelist returns 400 OBP-10042", ApiEndpoint, VersionOfApi) {
      val addedEntitlement = Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanGetAnyUser.toString)

      When("We make the request with a bogus sort_by value")
      val request = (v6_0_0_Request / "users").GET <@ (user1) <<? List(("sort_by", "potato"))
      val response = try makeGetRequest(request) finally {
        Entitlement.entitlement.vend.deleteEntitlement(addedEntitlement)
      }

      Then("We should get a 400 with the global-whitelist error code")
      response.code should equal(400)
      // The framework wraps the Failure in OBP-10016 (InvalidFilterParameterFormat);
      // assert the inner code is present rather than that it starts the string.
      response.body.extract[ErrorMessage].message should include("OBP-10042")
    }

    scenario("sort_by in global whitelist but not allowed for /users returns 400 OBP-10043", ApiEndpoint, VersionOfApi) {
      val addedEntitlement = Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanGetAnyUser.toString)

      When("We make the request with sort_by=verb (valid global, not valid per-endpoint)")
      val request = (v6_0_0_Request / "users").GET <@ (user1) <<? List(("sort_by", "verb"))
      val response = try makeGetRequest(request) finally {
        Entitlement.entitlement.vend.deleteEntitlement(addedEntitlement)
      }

      Then("We should get a 400 with the per-endpoint error code")
      response.code should equal(400)
      response.body.extract[ErrorMessage].message should startWith("OBP-10043")
    }

    scenario("invalid sort_direction returns 400 OBP-10023", ApiEndpoint, VersionOfApi) {
      val addedEntitlement = Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanGetAnyUser.toString)

      When("We make the request with an invalid sort_direction")
      val request = (v6_0_0_Request / "users").GET <@ (user1) <<? List(("sort_direction", "sideways"))
      val response = try makeGetRequest(request) finally {
        Entitlement.entitlement.vend.deleteEntitlement(addedEntitlement)
      }

      Then("We should get a 400 with the sort-direction error code")
      response.code should equal(400)
      // Wrapped by OBP-10016 (InvalidFilterParameterFormat).
      response.body.extract[ErrorMessage].message should include("OBP-10023")
    }

    scenario("each sort_by value in the per-endpoint whitelist is accepted by validation", ApiEndpoint, VersionOfApi) {
      val addedEntitlement = Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanGetAnyUser.toString)

      try {
        // Every value in SortableColumns must either succeed (200) or fail with a NON-validation error.
        // A 400 with OBP-10042 or OBP-10043 would indicate the whitelist is drifting from the code.
        code.users.DoobieUserQueries.SortableColumns.keySet.foreach { sortBy =>
          When(s"We make the request with sort_by=$sortBy")
          val request = (v6_0_0_Request / "users").GET <@ (user1) <<? List(("sort_by", sortBy), ("limit", "1"))
          val response = makeGetRequest(request)

          Then(s"sort_by=$sortBy should not be rejected by the sort_by validators")
          withClue(s"sort_by=$sortBy body=${response.body}") {
            response.code should not equal (400)
          }
        }
      } finally {
        Entitlement.entitlement.vend.deleteEntitlement(addedEntitlement)
      }
    }

    // ---------- Combination scenarios ----------
    // These hit the real Doobie SQL path and verify that multiple filter/sort params compose correctly.

    scenario("email filter + sort_by user_id asc returns only the matching user", ApiEndpoint, VersionOfApi) {
      val addedEntitlement = Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanGetAnyUser.toString)

      When("We filter by resourceUser1's email and sort by user_id asc")
      val request = (v6_0_0_Request / "users").GET <@ (user1) <<? List(
        ("email", resourceUser1.emailAddress),
        ("sort_by", "user_id"),
        ("sort_direction", "asc"),
        ("limit", "100")
      )
      val response = try makeGetRequest(request) finally {
        Entitlement.entitlement.vend.deleteEntitlement(addedEntitlement)
      }

      Then("We should get a 200 and exactly one row, matching resourceUser1")
      response.code should equal(200)
      val users = (response.body \ "users").children
      users.size should equal(1)
      (users.head \ "user_id").extract[String] should equal(resourceUser1.userId)
    }

    scenario("username + email narrowing (both match same user) returns 1 row", ApiEndpoint, VersionOfApi) {
      val addedEntitlement = Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanGetAnyUser.toString)

      When("We filter by both username and email for the same user with sort_by")
      val request = (v6_0_0_Request / "users").GET <@ (user1) <<? List(
        ("username", resourceUser1.name),
        ("email", resourceUser1.emailAddress),
        ("sort_by", "user_id"),
        ("sort_direction", "asc")
      )
      val response = try makeGetRequest(request) finally {
        Entitlement.entitlement.vend.deleteEntitlement(addedEntitlement)
      }

      Then("We should get a 200 and one row")
      response.code should equal(200)
      val users = (response.body \ "users").children
      users.size should equal(1)
      (users.head \ "user_id").extract[String] should equal(resourceUser1.userId)
    }

    scenario("email that matches no user returns 200 with empty list, not 404", ApiEndpoint, VersionOfApi) {
      val addedEntitlement = Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanGetAnyUser.toString)

      When("We filter by an email that no user has, with a valid sort")
      val request = (v6_0_0_Request / "users").GET <@ (user1) <<? List(
        ("email", "nobody@example.invalid"),
        ("sort_by", "email"),
        ("sort_direction", "desc")
      )
      val response = try makeGetRequest(request) finally {
        Entitlement.entitlement.vend.deleteEntitlement(addedEntitlement)
      }

      Then("We should get a 200 with an empty users list")
      response.code should equal(200)
      (response.body \ "users").children should be (empty)
    }

    scenario("role_name filter + sort_by user_id asc returns all users with that role in sorted order", ApiEndpoint, VersionOfApi) {
      // Grant the SAME role to the caller AND a second user. The response should include both,
      // sorted deterministically by user_id asc.
      val callerEnt = Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanGetAnyUser.toString)
      val extraEnt  = Entitlement.entitlement.vend.addEntitlement("", resourceUser2.userId, CanGetAnyUser.toString)

      When("We filter by role_name=CanGetAnyUser and sort by user_id asc")
      val request = (v6_0_0_Request / "users").GET <@ (user1) <<? List(
        ("role_name", "CanGetAnyUser"),
        ("sort_by", "user_id"),
        ("sort_direction", "asc"),
        ("limit", "100")
      )
      val response = try makeGetRequest(request) finally {
        Entitlement.entitlement.vend.deleteEntitlement(callerEnt)
        Entitlement.entitlement.vend.deleteEntitlement(extraEnt)
      }

      Then("We should get a 200 and both users, in ascending user_id order")
      response.code should equal(200)
      val returned = (response.body \ "users").children.map(u => (u \ "user_id").extract[String])
      returned should contain allOf (resourceUser1.userId, resourceUser2.userId)
      returned should equal(returned.sorted)
    }

    scenario("role_name + username narrows to a single user", ApiEndpoint, VersionOfApi) {
      val callerEnt = Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanGetAnyUser.toString)
      val extraEnt  = Entitlement.entitlement.vend.addEntitlement("", resourceUser2.userId, CanGetAnyUser.toString)

      When("We filter by role_name AND username (matching only one of the two role-holders)")
      val request = (v6_0_0_Request / "users").GET <@ (user1) <<? List(
        ("role_name", "CanGetAnyUser"),
        ("username", resourceUser1.name),
        ("sort_by", "user_id"),
        ("sort_direction", "asc")
      )
      val response = try makeGetRequest(request) finally {
        Entitlement.entitlement.vend.deleteEntitlement(callerEnt)
        Entitlement.entitlement.vend.deleteEntitlement(extraEnt)
      }

      Then("We should get a 200 and exactly the one user matching both filters")
      response.code should equal(200)
      val users = (response.body \ "users").children
      users.size should equal(1)
      (users.head \ "user_id").extract[String] should equal(resourceUser1.userId)
    }

    scenario("sort_direction desc inverts the order compared to asc", ApiEndpoint, VersionOfApi) {
      val callerEnt = Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanGetAnyUser.toString)
      val extraEnt  = Entitlement.entitlement.vend.addEntitlement("", resourceUser2.userId, CanGetAnyUser.toString)

      try {
        When("We fetch with sort_by=user_id asc and then with sort_by=user_id desc")
        val asc = makeGetRequest((v6_0_0_Request / "users").GET <@ (user1) <<? List(
          ("role_name", "CanGetAnyUser"), ("sort_by", "user_id"), ("sort_direction", "asc"), ("limit", "100")
        ))
        val desc = makeGetRequest((v6_0_0_Request / "users").GET <@ (user1) <<? List(
          ("role_name", "CanGetAnyUser"), ("sort_by", "user_id"), ("sort_direction", "desc"), ("limit", "100")
        ))

        Then("Both succeed and the two orderings are reverses of each other")
        asc.code should equal(200)
        desc.code should equal(200)
        val ascIds = (asc.body \ "users").children.map(u => (u \ "user_id").extract[String])
        val descIds = (desc.body \ "users").children.map(u => (u \ "user_id").extract[String])
        ascIds should not be empty
        descIds should equal(ascIds.reverse)
      } finally {
        Entitlement.entitlement.vend.deleteEntitlement(callerEnt)
        Entitlement.entitlement.vend.deleteEntitlement(extraEnt)
      }
    }
  }
}
