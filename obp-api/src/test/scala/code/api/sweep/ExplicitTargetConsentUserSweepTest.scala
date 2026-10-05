/**
Open Bank Project - API
Copyright (C) 2011-2026, TESOBE GmbH.

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.

Email: contact@tesobe.com
TESOBE GmbH.
Osloer Strasse 16/17
Berlin 13359, Germany

This product includes software developed at
TESOBE (http://www.tesobe.com/)

  */

package code.api.sweep

import java.io.File
import java.util.Date

import cats.effect.unsafe.IORuntime
import code.accountapplication.AccountApplicationX
import code.accountaccessrequest.AccountAccessRequestTrait
import code.api.util.APIUtil.generateUUID
import code.api.util.ErrorMessages.InvalidUserId
import code.api.v1_4_0.JSONFactory1_4_0.CustomerFaceImageJson
import code.api.v2_0_0.{CreateAccountJSON, CreateCustomerJson, CreateEntitlementJSON, CreateUserCustomerLinkJson}
import code.api.v2_1_0.{CustomerCreditRatingJSON, PostCustomerJsonV210}
import code.api.v3_1_0.{AccountApplicationJson, AccountApplicationUpdateStatusJson, CreateAccountRequestJsonV310}
import code.api.v4_0_0.SettlementAccountRequestJson
import code.api.v5_0_0.CreateAccountRequestJsonV500
import code.api.v5_1_0.PostAccountAccessJsonV510
import code.api.v6_0_0.JSONFactory600.{PostAccountAccessRequestJsonV600, PostApproveAccountAccessRequestJsonV600, PostGroupMembershipJsonV600}
import code.api.v7_0_0.JSONFactory700.CreateAccountRequestJsonV700
import code.model.dataAccess.ResourceUser
import code.setup.{DefaultUsers, ServerSetupWithTestData}
import code.users.Users
import com.openbankproject.commons.model.{AmountOfMoneyJsonV121, ProductCode}
import org.json4s.{Extraction, JValue}
import org.json4s.native.JsonMethods.{compact, render}
import org.scalatest.Tag

import scala.io.Source

/**
 * This suite checks that a request naming a consent user explicitly, by putting its id in the body
 * or the path, is refused rather than quietly rewritten. It is Phase 3 of
 * ON_BEHALF_OF_USER_ID_PLAN.md.
 *
 * The plan splits every user-reference column two ways. Where the caller means "me", the provider
 * quietly redirects the write to the human the caller acts for (Phase 2) -- an agent that creates
 * an account ends up with the human holding it, not the per-consent identity that dies with the
 * Consent. Where the caller names someone ELSE by id, there is nothing to redirect to and silence
 * would be wrong: the caller asked for a specific target, and if that target is an agent identity
 * the request is a mistake the caller needs told about. So the endpoint answers 400 OBP-30107.
 *
 * Why a sweep and not a scenario per version file: the guard is nineteen near-identical three-line
 * blocks spread over ten Http4s objects, and the failure mode that matters is not one of them
 * being wrong -- it is a twentieth being added, or an existing one being dropped, with nobody
 * noticing. So this suite pairs each probe with a source scan (the last scenario) that counts the
 * guards in main and fails if the count and this table disagree.
 *
 * ── What each probe sends ──
 *
 * The caller is an ordinary human holding every role (SweepFixtures.omniscientCaller), NOT a
 * consent user. That is the point: the guard is about the id in the request, not about who is
 * asking. Everything else in the request is valid, so a 400 for any OTHER reason is a failed
 * probe -- which is why the assertion checks the message and not just the status. Several of
 * these guards sit behind other checks that fire first (v1.4.0/v2.0.0/v2.1.0 customer creation
 * validate the customer number, v5.1.0 checks grant permission, v6.0.0 checks the maker/checker
 * rule), and a probe that trips one of those would otherwise pass while testing nothing.
 *
 * ── The two guards that read a stored row ──
 *
 * updateAccountApplicationStatus and approveAccountAccessRequest do not take the target id from
 * the request at all: they read it off a row written earlier, and refuse to act on it. Those
 * rows are written here through their providers, because the creation-side guard means the API
 * can no longer produce one -- which is exactly the situation those two guards exist for (a row
 * that predates the guard, or arrived another way).
 */
class ExplicitTargetConsentUserSweepTest extends ServerSetupWithTestData with DefaultUsers with SweepFixtures {

  object ExplicitTargetGuard extends Tag("ExplicitTargetGuard")

  implicit val runtime: IORuntime = IORuntime.global

  /**
   * The phrase every one of the nineteen guard messages shares.
   *
   * The messages differ in their subject ("user_id", "target_user_id", "USER_ID", "The
   * application's user") and in the sentence that follows, so the assertion anchors on the part
   * that is common and on the error code. Asserting each message verbatim would make this suite
   * fail on a reworded sentence, which is not the contract; the code and the reason are.
   */
  private val consentUserPhrase = "an agent identity minted by a Consent"

  // ── fixtures ───────────────────────────────────────────────────────────────

  /** A user minted by a Consent: CreatedByConsentId set, which is what isConsentUser reads. */
  private def newConsentUser(): ResourceUser = {
    val consent = code.consent.MappedConsent.create.mUserId(resourceUser1.userId).saveMe()
    Users.users.vend.createResourceUser(
      provider = "explicit-target-sweep-provider",
      providerId = Some(generateUUID()),
      createdByConsentId = Some(consent.consentId),
      name = Some("explicit-target-sweep-agent"),
      email = None,
      userId = None,
      createdByUserInvitationId = None,
      company = None,
      lastMarketingAgreementSignedDate = None
    ).openOrThrowException("expected the consent user to be created")
  }

  private def bankId: String = realBankId.getOrElse(
    fail("the fixtures created no bank, so no bank-scoped probe can reach its guard"))

  private def accountId: String = realAccountId(bankId).getOrElse(
    fail(s"the fixtures created no account at bank $bankId"))

  private def faceImage = CustomerFaceImageJson("http://example.com/face.png", new Date())

  private def money = AmountOfMoneyJsonV121("EUR", "0")

  /** A customer number no earlier probe or fixture has used: checked before three of the guards. */
  private def freshCustomerNumber: String = generateUUID().take(12)

  // ── one probe ──────────────────────────────────────────────────────────────

  private case class Probe(id: String, verb: String, path: String, body: Any)

  private def bodyOf(probe: Probe): String = probe.body match {
    case "" => ""
    case b  => compact(render(Extraction.decompose(b)))
  }

  /** A failure line, or None when the endpoint refused the consent user as it should. */
  private def check(probe: Probe, headers: Map[String, String]): Option[String] = {
    val (status, json) = callApi(probe.verb, probe.path, headers, bodyOf(probe))
    val message = (json \ "message").extractOpt[String].getOrElse("<no message>")
    val where   = s"${probe.id} ${probe.verb} ${probe.path}"
    if (status != 400)
      Some(s"$where -> HTTP $status (expected 400): $message")
    else if (!message.startsWith(InvalidUserId))
      Some(s"$where -> 400 but for another reason, so the guard was never reached: $message")
    else if (!message.contains(consentUserPhrase))
      Some(s"$where -> 400 $InvalidUserId but not the consent-user guard: $message")
    else None
  }

  private def sweep(probes: List[Probe]): Unit = {
    // Granted per scenario, not per class: beforeEach wipes the entitlement table, so roles
    // granted during an earlier scenario leave this one calling as an unentitled user -- which
    // stops at 403 before any guard runs.
    val headers  = omniscientCaller
    val failures = probes.flatMap(check(_, headers))
    withClue(s"${failures.size} of ${probes.size} explicit-target guards did not refuse a " +
             s"consent user. Each line is the endpoint, what it answered, and why that is not " +
             s"the guard firing:\n${failures.mkString("\n")}\n") {
      failures shouldBe empty
    }
  }

  // ── the probes ─────────────────────────────────────────────────────────────

  feature("An explicit user id naming a consent user is refused with 400 OBP-30107") {

    scenario("entitlements and group membership", ExplicitTargetGuard) {
      val agentId = newConsentUser().userId
      sweep(List(
        Probe("OBPv2.0.0-addEntitlement", "POST", s"/obp/v2.0.0/users/$agentId/entitlements",
          CreateEntitlementJSON(bank_id = "", role_name = "CanGetAnyUser")),
        Probe("OBPv7.0.0-addEntitlement", "POST", s"/obp/v7.0.0/users/$agentId/entitlements",
          CreateEntitlementJSON(bank_id = "", role_name = "CanGetAnyUser")),
        // The group does not exist; the guard runs before the group is looked up, which is the
        // ordering the endpoint intends -- a bad target is the caller's error either way.
        Probe("OBPv6.0.0-addUserToGroup", "POST", s"/obp/v6.0.0/users/$agentId/group-entitlements",
          PostGroupMembershipJsonV600(group_id = generateUUID()))
      ))
    }

    scenario("customer creation", ExplicitTargetGuard) {
      val agentId = newConsentUser().userId
      def v200Body = CreateCustomerJson(
        title = "Dr", branchId = "", nameSuffix = "", user_id = agentId,
        customer_number = freshCustomerNumber, legal_name = "Explicit Target Sweep",
        mobile_phone_number = "+49 123 456", email = "explicit-target-sweep@example.com",
        face_image = faceImage, date_of_birth = new Date(), relationship_status = "single",
        dependants = 0, dob_of_dependants = Nil, highest_education_attained = "Bachelor",
        employment_status = "employed", kyc_status = true, last_ok_date = new Date())
      sweep(List(
        Probe("OBPv1.4.0-addCustomer", "POST", s"/obp/v1.4.0/banks/$bankId/customer", v200Body),
        Probe("OBPv2.0.0-createCustomer", "POST", s"/obp/v2.0.0/banks/$bankId/customers", v200Body),
        Probe("OBPv2.1.0-createCustomer", "POST", s"/obp/v2.1.0/banks/$bankId/customers",
          PostCustomerJsonV210(
            user_id = agentId, customer_number = freshCustomerNumber,
            legal_name = "Explicit Target Sweep", mobile_phone_number = "+49 123 456",
            email = "explicit-target-sweep@example.com", face_image = faceImage,
            date_of_birth = new Date(), relationship_status = "single", dependants = 0,
            dob_of_dependants = Nil, credit_rating = CustomerCreditRatingJSON("OBP", "OBP"),
            credit_limit = money, highest_education_attained = "Bachelor",
            employment_status = "employed", kyc_status = true, last_ok_date = new Date()))
      ))
    }

    scenario("user-customer links", ExplicitTargetGuard) {
      val agentId = newConsentUser().userId
      // customer_id is present but nonexistent: both versions check the user before the customer,
      // which is what makes a link to an agent identity impossible to create even by accident.
      val body = CreateUserCustomerLinkJson(user_id = agentId, customer_id = generateUUID())
      sweep(List(
        Probe("OBPv2.0.0-createUserCustomerLinks", "POST", s"/obp/v2.0.0/banks/$bankId/user_customer_links", body),
        Probe("OBPv4.0.0-createUserCustomerLinks", "POST", s"/obp/v4.0.0/banks/$bankId/user_customer_links", body)
      ))
    }

    scenario("account creation", ExplicitTargetGuard) {
      val agentId = newConsentUser().userId
      def v310Body = CreateAccountRequestJsonV310(
        user_id = agentId, label = "explicit target sweep", product_code = "1234BW",
        balance = money, branch_id = "", account_routings = Nil)
      sweep(List(
        Probe("OBPv2.0.0-createAccount", "PUT", s"/obp/v2.0.0/banks/$bankId/accounts/${generateUUID()}",
          CreateAccountJSON(user_id = agentId, label = "explicit target sweep", `type` = "CURRENT", balance = money)),
        Probe("OBPv3.1.0-createAccount", "PUT", s"/obp/v3.1.0/banks/$bankId/accounts/${generateUUID()}", v310Body),
        Probe("OBPv4.0.0-addAccount", "POST", s"/obp/v4.0.0/banks/$bankId/accounts", v310Body),
        Probe("OBPv4.0.0-createSettlementAccount", "POST", s"/obp/v4.0.0/banks/$bankId/settlement-accounts",
          SettlementAccountRequestJson(
            user_id = agentId, payment_system = "SEPA", balance = money,
            label = "explicit target sweep", branch_id = "", account_routings = Nil)),
        Probe("OBPv5.0.0-createAccount", "PUT", s"/obp/v5.0.0/banks/$bankId/accounts/${generateUUID()}",
          CreateAccountRequestJsonV500(
            user_id = Some(agentId), label = "explicit target sweep", product_code = "1234BW",
            balance = Some(money), branch_id = Some(""), account_routings = Some(Nil))),
        // v7 registers the same handler twice, POST without an account id and PUT with one, and
        // the PUT arm runs an account-id check before the guard -- so both arms are probed.
        Probe("OBPv7.0.0-createAccountV700", "POST", s"/obp/v7.0.0/banks/$bankId/accounts",
          CreateAccountRequestJsonV700(
            user_id = Some(agentId), label = "explicit target sweep", product_code = "1234BW",
            balance = money, branch_id = Some(""), account_routings = Some(Nil))),
        Probe("OBPv7.0.0-createAccountWithIdV700", "PUT", s"/obp/v7.0.0/banks/$bankId/accounts/${generateUUID()}",
          CreateAccountRequestJsonV700(
            user_id = Some(agentId), label = "explicit target sweep", product_code = "1234BW",
            balance = money, branch_id = Some(""), account_routings = Some(Nil)))
      ))
    }

    scenario("account applications, including one already stored against a consent user", ExplicitTargetGuard) {
      val agentId = newConsentUser().userId

      // The update-side guard reads the application's user, not the request, so the row has to
      // exist first -- and the creation-side guard below means the API can no longer write one.
      val storedApplication = scala.concurrent.Await.result(
        AccountApplicationX.accountApplication.vend.createAccountApplication(
          ProductCode("1234BW"), Some(agentId), None),
        scala.concurrent.duration.Duration(30, "seconds")
      ).openOrThrowException("expected the account application to be created")

      sweep(List(
        Probe("OBPv3.1.0-createAccountApplication", "POST", s"/obp/v3.1.0/banks/$bankId/account-applications",
          AccountApplicationJson(product_code = "1234BW", user_id = Some(agentId), customer_id = None)),
        Probe("OBPv3.1.0-updateAccountApplicationStatus", "PUT",
          s"/obp/v3.1.0/banks/$bankId/account-applications/${storedApplication.accountApplicationId}",
          AccountApplicationUpdateStatusJson(status = "ACCEPTED"))
      ))
    }

    scenario("account access, including a request already stored against a consent user", ExplicitTargetGuard) {
      val agentId = newConsentUser().userId

      // requestorUserId is user2 so the approval probe passes the maker/checker check as user1,
      // which runs before the guard it is aiming at.
      val storedRequest = AccountAccessRequestTrait.accountAccessRequest.vend.createAccountAccessRequest(
        bankId = bankId, accountId = accountId, viewId = "owner", isSystemView = true,
        requestorUserId = resourceUser2.userId, targetUserId = agentId,
        businessJustification = "explicit target sweep"
      ).openOrThrowException("expected the account access request to be created")

      sweep(List(
        Probe("OBPv5.1.0-grantUserAccessToViewById", "POST",
          s"/obp/v5.1.0/banks/$bankId/accounts/$accountId/views/owner/account-access/grant",
          PostAccountAccessJsonV510(user_id = agentId, view_id = "owner")),
        Probe("OBPv6.0.0-createAccountAccessRequest", "POST",
          s"/obp/v6.0.0/banks/$bankId/accounts/$accountId/account-access-requests",
          PostAccountAccessRequestJsonV600(
            target_user_id = agentId, view_id = "owner", is_system_view = true,
            business_justification = "explicit target sweep")),
        Probe("OBPv6.0.0-approveAccountAccessRequest", "POST",
          s"/obp/v6.0.0/banks/$bankId/accounts/$accountId/account-access-requests/" +
            s"${storedRequest.accountAccessRequestId}/approval",
          PostApproveAccountAccessRequestJsonV600(comment = Some("explicit target sweep")))
      ))
    }
  }

  // ── completeness ───────────────────────────────────────────────────────────

  feature("Every explicit-target guard in the codebase is probed above") {

    /**
     * The scenarios above are a hand-written table, and a hand-written table of nineteen
     * near-identical things drifts. A twentieth guard added next month is not covered by
     * anything here, and nothing says so -- the suite stays green while its subject grows.
     *
     * So the guards are counted at their source. Every one is a booleanToFuture whose message
     * interpolates InvalidUserId and names the agent identity, which is a shape no other code in
     * the tree has: the only two other uses of that phrase are endpoint descriptions, and they
     * carry no error code. Counting is enough -- WHICH guards exist is pinned by the probes
     * themselves, each of which fails if its endpoint stops refusing.
     */
    scenario("the guard count in main matches the number of probes", ExplicitTargetGuard) {
      val root = List(new File("src/main/scala/code/api"), new File("obp-api/src/main/scala/code/api"))
        .find(_.isDirectory)
        .getOrElse(fail("cannot locate the api sources - this guard must not pass by failing to look"))

      def scalaFilesUnder(dir: File): List[File] =
        Option(dir.listFiles).toList.flatten.flatMap { f =>
          if (f.isDirectory) scalaFilesUnder(f)
          else if (f.getName.endsWith(".scala")) List(f) else Nil
        }

      val guardLines = scalaFilesUnder(root).flatMap { f =>
        val source = Source.fromFile(f, "UTF-8")
        try source.getLines().toList.zipWithIndex
          .filter { case (line, _) => line.contains(consentUserPhrase) && line.contains("InvalidUserId") }
          .map { case (_, i) => s"${f.getPath}:${i + 1}" }
        finally source.close()
      }

      // Twenty probes cover nineteen guards: v7.0.0 registers createAccountV700 (POST) and
      // createAccountWithIdV700 (PUT) over one shared createAccountCommon, so the two probes
      // reach the same guard by two different routes -- the PUT arm validates the account id
      // first, which is the part worth probing separately.
      val guardsInMain = 19

      withClue(s"found ${guardLines.size} explicit-target guards in main, but this suite is " +
               s"written against $guardsInMain. A guard added without a probe is untested; a " +
               s"probe left behind after a guard is removed asserts nothing. Guards found:\n" +
               s"${guardLines.mkString("\n")}\n") {
        guardLines.size shouldBe guardsInMain
      }
    }
  }
}
