package code.entitlement

import code.api.util.ApiRole._
import code.setup.ServerSetup
import net.liftweb.common.{Empty, Full}

class MappedEntitlementTest extends ServerSetup {

  val userId1 = "833b549e-50e8-49d3-9dcd-9dcdd18c26ec"
  val userId2 = "c562a9fa-85b3-41f3-9430-34c7153cc663"
  val bankId1 = "obp-bank-test1"
  val bankId2 = "obp-bank-test2"
  val role1 = CanCreateAccount

  def createEntitlement(bankId: String, userId: String, roleName: String) = Entitlement.entitlement.vend.addEntitlement(bankId, userId, roleName)

  private def delete(): Unit = {
    val found = Entitlement.entitlement.vend.getEntitlements().openOr(List())
    found.foreach {
      d => {
        Entitlement.entitlement.vend.deleteEntitlement(Full(d))
      }
    }
  }

  override def beforeAll() = {
    super.beforeAll()
    delete()
  }

  override def afterEach() = {
    super.afterEach()
    delete()
  }

  Feature("Getting Entitlement data") {
    Scenario("We try to get Entitlement") {
      Given("There is no entitlements at all but we try to get it")
      Entitlement.entitlement.vend.getEntitlements().openOr(List()).size should equal(0)

      When("We try to get it all")
      val found = Entitlement.entitlement.vend.getEntitlements().openOr(List())

      Then("We don't")
      found.size should equal(0)
    }
  }

  Scenario("A Entitlement exists for user and we try to get it") {
    Given("Create an entitlement")
    val entitlement1 = createEntitlement(bankId1, userId1, role1.toString)
    Entitlement.entitlement.vend.getEntitlement(bankId1, userId1, role1.toString).isDefined should equal(true)

    When("We try to get it by bank, user and role")
    val foundOpt = Entitlement.entitlement.vend.getEntitlement(bankId1, userId1, role1.toString)

    Then("We do")
    foundOpt.isDefined should equal(true)

    And("It is the right thing")
    val foundThing = foundOpt
    foundThing should equal(entitlement1)

    And("Primary id should be UUID")
    foundThing.map(_.entitlementId).mkString.replace("-", "").size should equal(32)
  }


  Scenario("We try to get all Entitlement rows and then delete they"){
    val entitlement1 = createEntitlement(bankId1, userId1, role1.toString)
    val entitlement2 = createEntitlement(bankId2, userId2, role1.toString)

    When("We try to get it all")
    val found = Entitlement.entitlement.vend.getEntitlements().openOr(List())

    Then("We don't")
    found.size should equal(2)

    And("We try to get it by user1, bank1 and role1")
    val foundThing1 = found.filter(_.userId == userId1).filter(_.bankId == bankId1).filter(_.roleName == role1.toString)
    foundThing1 should equal(entitlement1.toList)

    And("We try to get it by user2, bank2 and role2")
    val foundThing2 = found.filter(_.userId == userId2).filter(_.bankId == bankId2).filter(_.roleName == role1.toString)
    foundThing2 should equal(entitlement2.toList)

    And("We try to delete all rows")
    found.foreach {
      d => {
        Entitlement.entitlement.vend.deleteEntitlement(Full(d)) should equal(Full(true))
      }
    }
  }

  Feature("addEntitlement reports failure instead of swallowing it") {

    // The regression this pins was a malformed INSERT: `process` was listed among the columns
    // with a value of `""`, which SQL reads as a quoted identifier rather than an empty string,
    // so the statement never parsed. Nothing said so. addEntitlement wraps the write in `tryo`
    // and falls back to a lookup, so a grant that never happened returned Empty, test setup
    // carried on ungranted, and the symptom appeared far away as 403 from every role-gated
    // endpoint - 642 of them in one run, in suites that never mention entitlements.
    //
    // Asserting the returned Box is not enough on its own: the fallback lookup would return a
    // row that a previous test had committed. The role is therefore one nothing else grants,
    // and the check reads it back through the public query the authorisation path uses.
    Scenario("a granted role is actually persisted and readable, not silently dropped") {
      val userId = "e3f1c2d4-0000-4a11-9b22-addentitlement-persist".take(36)
      val role = CanCreateEntitlementAtOneBank

      Given("no such entitlement exists yet")
      Entitlement.entitlement.vend.getEntitlement(bankId1, userId, role.toString) should equal(Empty)

      When("the role is granted")
      val granted = Entitlement.entitlement.vend.addEntitlement(bankId1, userId, role.toString)

      Then("the call reports success rather than an empty Box")
      withClue("addEntitlement returned no row - the INSERT failed and tryo turned it into a " +
               "silent miss, which surfaces later as 403 from every endpoint needing this role ") {
        granted.isDefined should equal(true)
      }

      And("the row is readable through the query the authorisation path uses")
      val readBack = Entitlement.entitlement.vend.getEntitlement(bankId1, userId, role.toString)
      readBack.map(_.roleName) should equal(Full(role.toString))
      readBack.map(_.userId) should equal(Full(userId))

      Entitlement.entitlement.vend.deleteEntitlement(readBack.map(e => e)) should equal(Full(true))
    }
  }
}
