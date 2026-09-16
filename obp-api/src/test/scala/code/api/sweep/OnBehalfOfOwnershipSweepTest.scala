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

import bootstrap.liftweb.ToSchemify
import cats.effect.unsafe.IORuntime
import code.accountholders.AccountHolders
import code.api.RequestHeader
import code.api.util.APIUtil.generateUUID
import code.api.util.{Consent, DBUtil}
import code.api.v3_1_0.ConsentJsonV310
import code.api.v4_0_0.PostApiCollectionJson400
import code.api.v7_0_0.JSONFactory700.CreateAccountRequestJsonV700
import code.setup.{DefaultUsers, ServerSetupWithTestData}
import code.users.{AttributionPolicy, UserReference}
import com.openbankproject.commons.model.{AccountId, AmountOfMoneyJsonV121, BankId}
import net.liftweb.mapper.MetaMapper
import org.json4s.{Extraction, JValue}
import org.json4s.JsonAST.{JArray, JObject}
import org.json4s.JsonDSL._
import org.json4s.native.JsonMethods.{compact, render}
import org.scalatest.Tag

import scala.io.Source

/**
 * Phase 4 item 3 of ON_BEHALF_OF_USER_ID_PLAN.md: what a consent user creates is owned by the human.
 *
 * The complement of ExplicitTargetConsentUserSweepTest. That suite covers the half of the doctrine
 * where the caller names a target by id and is refused; this one covers the half where the caller
 * means "me" and the provider silently redirects the write to the human the Consent was granted by.
 * The caller here IS a consent user, driven by a real Consent JWT minted over the wire.
 *
 * ── Why this is a ratchet and not the sweep the plan described ──
 *
 * The plan says: "call every UseOnBehalfOfUserId create endpoint with the consent JWT; assert no
 * row in any such table references the consent user's id". Taken literally that suite is red on the
 * day it is written and stays red for months: of the 53 UseOnBehalfOfUserId references,
 * EIGHT are wired today (TransactionRequestUserIdOnBehalfOfUserId, EntitlementUserId,
 * AccountHoldersUser, UserCustomerLinkUserId, DynamicEntityUserId/DynamicDataUserId,
 * BankCreatedByUserId, CounterpartyCreatedByUserIdCreatedByOnBehalfOfUserId -- Phase 2 rows 1 to 5).
 * A permanently
 * red suite is one people learn to ignore, which is the same reasoning AuthSweepTest's
 * expectedAuthDeviation records for its own two entries.
 *
 * So the shape is: an explicit inventory of what is NOT yet wired, which may only shrink. Three
 * scenarios, each answering a different question:
 *
 *  1. INVENTORY (source scan). Every reference is either used somewhere in main, or listed in
 *     notYetWired with a reason. A reference that is neither fails -- that is a new table nobody
 *     decided about. A listed reference that HAS become used also fails, so wiring one up forces
 *     the list to shrink and the list can never quietly outlive the gap it describes.
 *
 *  2. OWNERSHIP (runtime, the real property). A consent user creates an account over HTTP; the
 *     account holder must be the human, and no row anywhere in any UseOnBehalfOfUserId column may
 *     reference the consent user -- except in the tables the inventory says are not wired yet.
 *
 *  3. THE SCAN ITSELF WORKS (runtime, negative control). The same consent user creates an API
 *     collection, whose reference (ApiCollectionUserId) is NOT wired. The scan must find that row.
 *     Without this, scenario 2 passes just as happily if the scan is silently looking at nothing --
 *     which is the failure mode a table-driven assertion has and a hand-written one does not.
 */
class OnBehalfOfOwnershipSweepTest extends ServerSetupWithTestData with DefaultUsers with SweepFixtures {

  object OnBehalfOfOwnership extends Tag("OnBehalfOfOwnership")

  implicit val runtime: IORuntime = IORuntime.global

  private val mechanicalBatch =
    "Phase 2 mechanical batch: the provider does not call attributionOf yet, so a consent user's " +
    "row is stored against the consent user and dies with the Consent."

  /**
   * References whose provider does not consult the resolver yet (Phase 2 is incremental).
   *
   * Written out by hand rather than derived from "what main does not reference". A derived list
   * would agree with reality by construction: the undecided check could never fail, because
   * anything missing would be added to the list at the same moment; and the staleness check could
   * never fail either. Both assertions would be checking the list against a copy of itself, which
   * is the failure mode SweepCoverageDriftCheckTest exists to prevent elsewhere in this package.
   * Spelling the names out is what makes adding a table a decision somebody has to take.
   *
   * This list may only shrink.
   */
  private val notYetWired: Map[String, String] = Map(
    "AccountApplicationUserId"                  -> mechanicalBatch,
    "AccountAccessRequestRequestorUserId"           -> mechanicalBatch,
    "AccountAccessRequestCheckerUserId"             -> mechanicalBatch,
    "DynamicChangeRequestRequestorUserId"           -> mechanicalBatch,
    "DynamicChangeRequestCheckerUserId"             -> mechanicalBatch,
    "EntitlementRequestUserId"                  -> mechanicalBatch,
    "UserScopeUserId"                           -> mechanicalBatch,
    "ApiCollectionUserId"                       -> mechanicalBatch,
    "UserAttributeUserId"                       -> mechanicalBatch,
    "UserAgreementUserId"                       -> mechanicalBatch,
    "UserInitActionUserId"                      -> mechanicalBatch,
    "UserAuthContextUserId"                     -> mechanicalBatch,
    "UserAuthContextUpdateUserId"               -> mechanicalBatch,
    "DynamicDataAccessUserId"                   -> mechanicalBatch,
    "DynamicEndpointUserId"                     -> mechanicalBatch,
    "DynamicResourceDocCreatedByUserIdUpdatedByUserId"               -> mechanicalBatch,
    "DynamicMessageDocCreatedByUserIdUpdatedByUserId"                -> mechanicalBatch,
    "ConnectorMethodCreatedByUserIdUpdatedByUserId"                  -> mechanicalBatch,
    "AbacRuleCreatedByUserIdUpdatedByUserId"                         -> mechanicalBatch,
    "CounterpartyWhereTagUser"                -> mechanicalBatch,
    "ApiProductSubscriptionCreatedByUserId"           -> mechanicalBatch,
    "DynamicGlossaryItemCreatedByUserId"              -> mechanicalBatch,
    "OrganisationCreatedByUserId"                     -> mechanicalBatch,
    "PayeeLookupCreatedByUserId"                      -> mechanicalBatch,
    "RoutingSchemeCreatedByUserId"                    -> mechanicalBatch,
    "UtilityPaymentCallbackCreatedByUserId"           -> mechanicalBatch,
    "StandingOrderUserId"                       -> mechanicalBatch,
    "DirectDebitUserId"                         -> mechanicalBatch,
    "MandateCreatedByUserIdUpdatedByUserId"                          -> mechanicalBatch,
    "SignatoryPanelUserIds"                     -> mechanicalBatch,
    "AccountWebhookCreatedByUserId"                   -> mechanicalBatch,
    "SystemAccountNotificationWebhookCreatedByUserId" -> mechanicalBatch,
    "BankAccountNotificationWebhookCreatedByUserId"   -> mechanicalBatch,
    "ChatRoomCreatedByUserId"                         -> mechanicalBatch,
    "ParticipantUserId"                     -> mechanicalBatch,
    "ReactionUserId"                        -> mechanicalBatch,
    "ChatEmailDigestStateUserId"                -> mechanicalBatch,
    "ChatMessageMentionedUserIds"               -> mechanicalBatch,
    "CrmEventUserId"                            -> mechanicalBatch,
    "KycCheckUser"                            -> mechanicalBatch,
    "KycCheckStaffUserId"                           -> mechanicalBatch,
    "KycDocumentUser"                         -> mechanicalBatch,
    "KycStatusUser"                           -> mechanicalBatch,
    "SocialMediaUser"                         -> mechanicalBatch,
    "CustomerMessageUser"                     -> mechanicalBatch,
    "MeetingCustomerUserId"                     -> mechanicalBatch,
    "MeetingStaffUserId"                        -> mechanicalBatch,
    "TagUser"                                 -> mechanicalBatch,
    "WhereTagUser"                            -> mechanicalBatch,
    "TransactionImageUser"                    -> mechanicalBatch,
    "AccountAccessRequestTargetUserId" ->
      ("explicit target, so the endpoint refuses a consent user rather than redirecting -- covered " +
       "by ExplicitTargetConsentUserSweepTest. The provider redirect is unreachable from the API."),
    "ConsentUserId" ->
      ("Reject, not yet enforced: a consent user can still create a Consent (nested delegation). " +
       "attributionOf already returns Failure for it -- AgentDelegationTest pins that -- but no " +
       "caller consults it. ON_BEHALF_OF_USER_ID_PLAN.md Phase 3, still open."),
    "ConsumerCreatedByUserId" -> "Reject, not yet enforced: no caller consults attributionOf.",
    "TokenUserForeignKey"       -> "Reject, not yet enforced: no caller consults attributionOf."
  )

  // ── which references main actually uses ────────────────────────────────────

  private def mainSourceRoot: File =
    List(new File("src/main/scala/code"), new File("obp-api/src/main/scala/code"))
      .find(_.isDirectory)
      .getOrElse(fail("cannot locate the main sources - this guard must not pass by failing to look"))

  private def scalaFilesUnder(dir: File): List[File] =
    Option(dir.listFiles).toList.flatten.flatMap { f =>
      if (f.isDirectory) scalaFilesUnder(f)
      else if (f.getName.endsWith(".scala")) List(f) else Nil
    }

  /**
   * Reference names mentioned anywhere in main outside the policy file itself.
   *
   * "Mentioned" is weaker than "applied correctly" -- a provider could name the reference and still
   * store the wrong id. That stronger question is what scenario 2 answers at runtime; this one only
   * has to separate "somebody has been here" from "nobody has", which is exactly what the ratchet
   * needs to know.
   *
   * Comments are stripped first, and that is load-bearing rather than tidiness. Documenting a
   * reference is not wiring it: on 2026-09-15 a doc comment on LiftUsers.attributionOf explaining
   * WHY a consent user cannot create a Consent named `UserReference.ConsentUserId` in prose, and
   * the ratchet promptly reported the (entirely correct) ConsentUserId exemption as stale. A guard
   * that fires when someone writes a comment teaches people to write fewer comments, and worse,
   * lets a reference be marked wired without a single call site.
   */
  private lazy val usedInMain: Set[String] = {
    val names = UserReference.all.map(_.name).toSet
    val pattern = """UserReference\.([A-Za-z]+)""".r
    scalaFilesUnder(mainSourceRoot)
      .filterNot(_.getPath.endsWith("code/users/UserReference.scala"))
      .flatMap { f =>
        val source = Source.fromFile(f, "UTF-8")
        try pattern.findAllMatchIn(withoutComments(source.mkString)).map(_.group(1)).toList
        finally source.close()
      }
      .toSet
      .intersect(names)
  }

  /**
   * Scala source with block, scaladoc and line comments blanked out.
   *
   * Deliberately a pair of regexes rather than a lexer: the only thing read out of the result is
   * `UserReference.Xxx`, so the one way this can be wrong -- a `//` inside a string literal
   * truncating the rest of that line -- would have to be followed by a UserReference mention in
   * the same literal to matter, which no call site looks like.
   */
  private def withoutComments(source: String): String =
    source
      .replaceAll("(?s)/\\*.*?\\*/", " ")
      .replaceAll("//[^\n]*", " ")

  // ── the leak scan ──────────────────────────────────────────────────────────

  private lazy val metaByClassName: Map[String, MetaMapper[_]] =
    ToSchemify.models.map(meta => meta.getClass.getName.stripSuffix("$") -> meta).toMap

  /**
   * How to tell two references apart when they name the SAME column with different policies.
   *
   * MappedEntitlement.mUserId is the one case in the policy file: EntitlementUserId
   * (UseOnBehalfOfUserId, the role holder) and EntitlementUserIdConsentScope (UseAuthenticatedUserId, the consent
   * engine copying the Consent's own scope onto the consent user). MappedEntitlements.addEntitlement
   * picks between them on createdByProcess, so a scan of that column that does not apply the same
   * discriminator reports every consent's own materialised scope as a leak -- which it is not; those
   * rows are meant to be on the consent user and are revoked with it.
   *
   * Reference name -> (discriminator field, the value that belongs to the OTHER reference).
   */
  private val sharedColumnDiscriminator: Map[String, (String, String)] = Map(
    "EntitlementUserId" -> ("mCreatedByProcess", code.api.Constant.consent_user)
  )

  /** Columns named by references that disagree on policy, so the scan must be told how to split them. */
  private lazy val sharedColumns: List[(String, List[UserReference])] =
    UserReference.all
      .flatMap(ref => ref.fields.map(field => s"${ref.mapperClass}.$field" -> ref))
      .groupBy(_._1).toList
      .map { case (column, pairs) => column -> pairs.map(_._2) }
      .filter { case (_, refs) => refs.map(_.policy).distinct.size > 1 }
      .sortBy(_._1)

  /**
   * Every reference whose column holds a value pointing at `user`.
   *
   * Read as SQL rather than through the Mapper because the columns are not one type: some hold the
   * user_id string, some are a MappedLongForeignKey to ResourceUser's primary key
   * (MapperAccountHolders.user is the one that matters here), and one holds a list of ids
   * (SignatoryPanel.UserIds). DBUtil.runQuery stringifies every column, so one comparison covers
   * all three: equal to the primary key, or containing the user_id.
   */
  private def referencesOwnedBy(userId: String, primaryKey: String): List[String] =
    for {
      ref   <- UserReference.byPolicy(AttributionPolicy.UseOnBehalfOfUserId)
      meta  <- metaByClassName.get(ref.mapperClass).toList
      field <- ref.fields.flatMap(name => meta.mappedFields.find(_.name == name))
      where  = sharedColumnDiscriminator.get(ref.name).flatMap { case (discriminator, otherValue) =>
                 meta.mappedFields.find(_.name == discriminator).map(d =>
                   s" WHERE ${d.dbColumnName} IS NULL OR ${d.dbColumnName} <> '$otherValue'")
               }.getOrElse("")
      values = DBUtil.runQuery(s"SELECT ${field.dbColumnName} FROM ${meta.dbTableName}$where")._2.flatten
      if values.exists(v => v == primaryKey || v.contains(userId))
    } yield ref.name

  // ── minting a consent user over the wire ───────────────────────────────────

  private def consumerKeyHeader: Map[String, String] =
    Map(RequestHeader.`Consumer-Key` -> testConsumer.key.get)

  private def consentBody(entitlements: List[(String, String)]): String = {
    val json: JObject =
      ("everything"   -> false) ~
      ("views"        -> JArray(Nil)) ~
      ("entitlements" -> JArray(entitlements.map { case (bank, role) =>
                            ("bank_id" -> bank) ~ ("role_name" -> role) })) ~
      ("consumer_id"  -> testConsumer.consumerId.get) ~
      ("time_to_live" -> 3600)
    compact(render(json))
  }

  /**
   * A Consent granted by resourceUser1, answered, as request headers.
   *
   * A Consent can only carry roles its granting User already holds, so omniscientCaller runs first.
   */
  private def consentUserHeaders(entitlements: List[(String, String)]): Map[String, String] = {
    setPropsValues(
      "consents.allowed" -> "true",
      "consumer_validation_method_for_consent" -> "CONSUMER_KEY_VALUE")
    val humanHeaders = omniscientCaller ++ consumerKeyHeader

    val (createdStatus, createdBody) = callApi(
      "POST", "/obp/v6.0.0/my/consents/IMPLICIT", humanHeaders, consentBody(entitlements))
    withClue(s"could not create the Consent: $createdBody ") { createdStatus should equal(201) }
    val consent = createdBody.extract[ConsentJsonV310]

    val (answeredStatus, answeredBody) = callApi(
      "POST", s"/obp/v5.1.0/banks/$bankId/consents/${consent.consent_id}/challenge", humanHeaders,
      compact(render(("answer" -> Consent.challengeAnswerAtTestEnvironment): JObject)))
    withClue(s"could not answer the Consent challenge: $answeredBody ") { answeredStatus should equal(201) }

    Map(RequestHeader.`Consent-JWT` -> consent.jwt) ++ consumerKeyHeader
  }

  /** The consent user's own id, asked for the way an agent would ask: whoami. */
  private def currentUserIdOf(headers: Map[String, String]): String = {
    val (status, body) = callApi("GET", "/obp/v7.0.0/users/current", headers)
    withClue(s"could not read the consent user's own id: $body ") { status should equal(200) }
    (body \ "user_id").extract[String]
  }

  private def primaryKeyOf(userId: String): String =
    code.model.dataAccess.ResourceUser
      .find(net.liftweb.mapper.By(code.model.dataAccess.ResourceUser.userId_, userId))
      .map(_.id.get.toString)
      .openOrThrowException(s"expected a ResourceUser row for $userId")

  private def bankId: String = realBankId.getOrElse(
    fail("the fixtures created no bank, so the consent cannot be scoped to one"))

  private def bodyJson(value: Any): String = compact(render(Extraction.decompose(value)))

  // ── scenarios ──────────────────────────────────────────────────────────────

  feature("What a consent user creates is owned by the human it acts for") {

    scenario("every UseOnBehalfOfUserId and Reject reference is wired, or listed as not yet wired", OnBehalfOfOwnership) {
      val inScope = UserReference.all
        .filter(ref => ref.policy == AttributionPolicy.UseOnBehalfOfUserId || ref.policy == AttributionPolicy.Reject)

      val undecided = inScope.map(_.name)
        .filterNot(usedInMain.contains)
        .filterNot(notYetWired.contains)
        .sorted
      withClue(
        s"""|${undecided.size} reference(s) are neither wired nor listed as not-yet-wired. A new
            |table whose owner column nobody has decided about strands rows on consent users
            |silently. Wire the provider, or add the reference to notYetWired with the reason:
            |${undecided.map(n => s"  $n").mkString("\n")}
            |""".stripMargin) {
        undecided shouldBe empty
      }

      val stale = notYetWired.keys.toList.filter(usedInMain.contains).sorted
      withClue(
        s"""|${stale.size} notYetWired entr(ies) name a reference that main now uses. This list may
            |only shrink -- a stale exemption is an assertion that has stopped asserting. Remove
            |these, and check scenario 2's allowlist no longer needs them either:
            |${stale.map(n => s"  $n").mkString("\n")}
            |""".stripMargin) {
        stale shouldBe empty
      }

      info(s"${usedInMain.size} of ${UserReference.all.size} references are wired; " +
           s"${notYetWired.size} are listed as not yet wired")
    }

    // UserReferenceAttributionPolicyTest already refuses two references on one column with the SAME
    // policy. This is the other half of that: where they differ, the leak scan cannot read the
    // column as a whole and has to be told the discriminator the provider uses.
    scenario("the leak scan knows how to split every column shared by two policies", OnBehalfOfOwnership) {
      val unsplit = sharedColumns.filterNot { case (_, refs) =>
        refs.exists(ref => sharedColumnDiscriminator.contains(ref.name))
      }.map { case (column, refs) => s"  $column -> ${refs.map(r => s"${r.name} (${r.policy})").sorted.mkString(", ")}" }

      withClue(
        s"""|${unsplit.size} column(s) are named by references with different policies, but the scan
            |has no discriminator for them -- so it would read the UseAuthenticatedUserId rows as leaks from the
            |UseOnBehalfOfUserId reference, or miss real leaks by widening the exemption. Add an entry
            |to sharedColumnDiscriminator naming the field the provider branches on:
            |${unsplit.mkString("\n")}
            |""".stripMargin) {
        unsplit shouldBe empty
      }
    }

    scenario("an account created by a consent user is held by the human, and strands nothing", OnBehalfOfOwnership) {
      val headers   = consentUserHeaders(List(bankId -> "CanCreateAccount"))
      val agentId   = currentUserIdOf(headers)
      val agentKey  = primaryKeyOf(agentId)

      withClue("the Consent JWT must authenticate as a DIFFERENT user from the human that granted " +
               "it, otherwise this scenario is testing nothing: ") {
        agentId should not equal resourceUser1.userId
      }

      val (status, body) = callApi("POST", s"/obp/v7.0.0/banks/$bankId/accounts", headers,
        bodyJson(CreateAccountRequestJsonV700(
          user_id = None,                       // implicit self: the redirect is the whole point
          label = "on behalf of ownership sweep",
          product_code = "1234BW",
          balance = AmountOfMoneyJsonV121("EUR", "0"),
          branch_id = Some(""),
          account_routings = Some(Nil))))
      withClue(s"the consent user could not create an account: $body ") { status should equal(201) }

      val accountId = (body \ "account_id").extract[String]
      val holders   = AccountHolders.accountHolders.vend
        .getAccountHolders(BankId(bankId), AccountId(accountId)).map(_.userId)

      withClue(s"the account the agent created is held by $holders: ") {
        holders should contain(resourceUser1.userId)
        holders should not contain agentId
      }

      val leaked = referencesOwnedBy(agentId, agentKey).filterNot(notYetWired.contains)
      withClue(
        s"""|${leaked.size} wired reference(s) still hold rows owned by the consent user
            |$agentId (primary key $agentKey). Those rows die with the Consent:
            |${leaked.map(n => s"  $n").mkString("\n")}
            |""".stripMargin) {
        leaked shouldBe empty
      }
    }

    // Without this, the scenario above passes just as happily when the scan reads nothing at all.
    scenario("the scan detects a row that IS owned by the consent user", OnBehalfOfOwnership) {
      val headers  = consentUserHeaders(Nil)
      val agentId  = currentUserIdOf(headers)
      val agentKey = primaryKeyOf(agentId)

      withClue("ApiCollectionUserId must still be unwired for this control to mean anything; " +
               "once it is wired, replace it here with another unwired reference: ") {
        usedInMain should not contain "ApiCollectionUserId"
      }

      val (status, body) = callApi("POST", "/obp/v4.0.0/my/api-collections", headers,
        bodyJson(PostApiCollectionJson400(
          api_collection_name = generateUUID().take(12),
          is_sharable = true,
          description = Some("on behalf of ownership sweep"))))
      withClue(s"the consent user could not create an api collection: $body ") { status should equal(201) }

      withClue("the api collection was stored against the consent user, but the scan did not see " +
               "it -- so the scan in the scenario above is not looking where it claims to: ") {
        referencesOwnedBy(agentId, agentKey) should contain("ApiCollectionUserId")
      }
    }
  }
}
