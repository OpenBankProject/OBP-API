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

package code.users

import code.api.util.DoobieUtil
import code.setup.ServerSetup
import org.scalatest.Tag

/**
 * This test guards the attribution policy table in `UserReference` against the database schema
 * drifting away from it. The policy file must name every column in the schema that holds a user
 * id, and must not name a column that does not exist.
 *
 * Why it exists. Attribution is enforced in the providers, one `UserReference` at a time
 * (ON_BEHALF_OF_USER_ID_PLAN.md Phase 2). Doing that by hand is only safe if the map of what needs
 * doing is provably complete — otherwise a table added next week grows a `UserId` column that
 * silently strands rows on consent users, and nothing anywhere says so. This test is that proof.
 * It does NOT check that a provider actually applies the policy; that is
 * `OnBehalfOfOwnershipSweepTest` (Phase 4 item 3).
 *
 * Upstream reflects over the Lift Mapper entities in ToSchemify.models. This branch has none: the
 * schema is the Liquibase changelog, so the test reads the columns of the database the suite has just
 * built, through JDBC metadata, and `storeTables` says which table each `UserReference` class writes.
 * A UserReference names its fields by the Mapper field name or by the column; both are matched
 * against the column, case-insensitively, also as snake_case and with Mapper's `_c` suffix for a
 * reserved word.
 *
 * When this fails on a new table, the fix is to add a `UserReference` for the column with the right
 * policy — or, if the column is not a user reference at all, to list it in
 * `UserReference.notUserIdColumns` with the reason — and, for a new class, an entry in `storeTables`.
 */
class UserReferenceAttributionPolicyTest extends ServerSetup {

  object UserReferenceTag extends Tag("UserReferenceAttributionPolicy")

  /** This is how the test decides a column holds a user id: by its name. It is the same pattern
   *  the plan uses. */
  private val userReferenceNamePattern = "(?i).*(userid|user_id|createdby|created_by|grantedby|granted_by|holder).*".r

  /** The table each class a UserReference names writes to. */
  private val storeTables: Map[String, String] = Map(
    "code.DynamicData.DynamicData" -> "dynamicdata",
    "code.DynamicData.DynamicDataAccess" -> "dynamicdataaccess",
    "code.DynamicEndpoint.DynamicEndpoint" -> "dynamicendpoint",
    "code.UserRefreshes.DoobieUserRefreshesProvider" -> "mappeduserrefreshes",
    "code.abacrule.AbacRule" -> "abacrule",
    "code.accountaccessrequest.AccountAccessRequest" -> "accountaccessrequest",
    "code.accountapplication.MappedAccountApplication" -> "mappedaccountapplication",
    "code.accountcreation.AccountCreation" -> "account_creation",
    "code.accountholders.MapperAccountHolders" -> "mapperaccountholders",
    "code.apicollection.DoobieApiCollectionsProvider" -> "apicollection",
    "code.apiproductsubscription.ApiProductSubscription" -> "apiproductsubscription",
    "code.asset.Asset" -> "asset",
    "code.asset.AssetStatusHistory" -> "assetstatushistory",
    "code.chat.ChatEmailDigestState" -> "chat_email_digest_state",
    "code.chat.ChatMessage" -> "chatmessage",
    "code.chat.ChatRoom" -> "chatroom",
    "code.chat.Participant" -> "participant",
    "code.chat.Reaction" -> "reaction",
    "code.connectormethod.DoobieConnectorMethodProvider" -> "connectormethod",
    "code.consent.MappedConsent" -> "mappedconsent",
    "code.consumeraccountaccess.ConsumerAccountAccess" -> "consumer_account_access",
    "code.context.DoobieUserAuthContextProvider" -> "mappeduserauthcontext",
    "code.context.MappedUserAuthContextUpdateProvider" -> "mappeduserauthcontextupdate",
    "code.crm.DoobieCrmEventProvider" -> "mappedcrmevent",
    "code.customer.MappedCustomerMessage" -> "mappedcustomermessage",
    "code.directdebit.DirectDebit" -> "directdebit",
    "code.domainapi.DomainApi" -> "domain_api",
    "code.dynamicEntity.DynamicEntity" -> "dynamicentity",
    "code.dynamicMessageDoc.DynamicMessageDoc" -> "dynamicmessagedoc",
    "code.dynamicResourceDoc.DynamicResourceDoc" -> "dynamicresourcedoc",
    "code.dynamicchangerequest.DynamicChangeRequest" -> "dynamicchangerequest",
    "code.dynamicentitywebhook.DynamicEntityWebhook" -> "dynamic_entity_webhook",
    "code.entitlement.MappedEntitlement" -> "mappedentitlement",
    "code.entitlementrequest.MappedEntitlementRequest" -> "mappedentitlementrequest",
    "code.files.File" -> "file_t",
    "code.files.FileAccess" -> "fileaccess",
    "code.files.FileAttachment" -> "fileattachment",
    "code.glossaryitem.DynamicGlossaryItem" -> "dynamicglossaryitem",
    "code.group.GroupMembership" -> "groupmembership",
    "code.kycchecks.MappedKycCheck" -> "mappedkyccheck",
    "code.kycdocuments.MappedKycDocument" -> "mappedkycdocument",
    "code.kycstatuses.MappedKycStatus" -> "mappedkycstatus",
    "code.mandate.Mandate" -> "mandate",
    "code.mandate.SignatoryPanel" -> "signatorypanel",
    "code.meetings.MappedMeeting" -> "mappedmeeting",
    "code.metadata.counterparties.MappedCounterparty" -> "mappedcounterparty",
    "code.metadata.counterparties.MappedCounterpartyWhereTag" -> "mappedcounterpartywheretag",
    "code.metadata.tags.DoobieTags" -> "mappedtag",
    "code.metadata.transactionimages.DoobieTransactionImages" -> "mappedtransactionimage",
    "code.metadata.wheretags.DoobieWhereTags" -> "mappedwheretag",
    "code.metrics.DoobieConnectorTrace" -> "connector_trace",
    "code.metrics.MappedMetric" -> "metric",
    "code.metrics.MetricArchive" -> "metricarchive",
    "code.model.Consumer" -> "consumer",
    "code.model.Token" -> "token",
    "code.model.dataAccess.AuthUser" -> "authuser",
    "code.model.dataAccess.MappedBank" -> "mappedbank",
    "code.model.dataAccess.MappedBankAccount" -> "mappedbankaccount",
    "code.model.dataAccess.ResourceUser" -> "resourceuser",
    "code.model.dataAccess.SelfServiceBank" -> "selfservicebank",
    "code.payeelookup.DoobiePayeeLookupProvider" -> "payeelookup",
    "code.platformapp.PlatformApp" -> "platform_app",
    "code.retention.RetentionArchive" -> "retentionarchive",
    "code.retention.RetentionHold" -> "retentionhold",
    "code.retention.RetentionPolicy" -> "retentionpolicy",
    "code.retention.RetentionRun" -> "retentionrun",
    "code.retention.RetentionRunItem" -> "retentionrunitem",
    "code.routingscheme.RoutingScheme" -> "routingscheme",
    "code.socialmedia.MappedSocialMedia" -> "mappedsocialmedia",
    "code.standingorders.StandingOrder" -> "standingorder",
    "code.tenant.Tenant" -> "tenant",
    "code.token.OpenIDConnectToken" -> "openidconnecttoken",
    "code.transaction.MappedTransaction" -> "mappedtransaction",
    "code.transactionChallenge.MappedExpectedChallengeAnswer" -> "expectedchallengeanswer",
    "code.transactionrequests.MappedTransactionRequest" -> "mappedtransactionrequest",
    "code.usercustomerlinks.DoobieUserCustomerLinkProvider" -> "mappedusercustomerlink",
    "code.userlocks.UserLocksProvider" -> "userlocks",
    "code.users.UserAgreement" -> "useragreement",
    "code.users.UserAttribute" -> "userattribute",
    "code.users.UserInitActionProvider" -> "userinitaction",
    "code.utilitypayment.DoobieUtilityPaymentCallbackProvider" -> "utilitypaymentcallback",
    "code.views.system.AccountAccess" -> "accountaccess",
    "code.webhook.BankAccountNotificationWebhook" -> "bankaccountnotificationwebhook",
    "code.webhook.MappedAccountWebhook" -> "mappedaccountwebhook",
    "code.webhook.SystemAccountNotificationWebhook" -> "systemaccountnotificationwebhook"
  )

  /** Every column of every application table, lower case, read from the database the suite built. */
  private lazy val schemaColumns: Map[String, Set[String]] = DoobieUtil.runQuery(
    doobie.free.connection.raw { connection =>
      val metadata = connection.getMetaData
      val rows = metadata.getColumns(connection.getCatalog, connection.getSchema, null, null)
      val found = scala.collection.mutable.ListBuffer[(String, String)]()
      try while (rows.next()) found += ((rows.getString("TABLE_NAME").toLowerCase, rows.getString("COLUMN_NAME").toLowerCase))
      finally rows.close()
      found.toList
    }
  ).groupBy(_._1).map { case (table, pairs) => table -> pairs.map(_._2).toSet }
    .filterNot { case (table, _) => table.startsWith("databasechangelog") || table == "migrationscriptlog" }

  private def snake(name: String): String =
    name.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase

  /** The column of `table` a field name refers to, if the table has one. */
  private def columnOf(table: String, field: String): Option[String] =
    schemaColumns.get(table).flatMap { columns =>
      // Mapper's `mUserId` style names carry a leading "m" the column does not.
      val unprefixed = if (field.length > 1 && field.head == 'm' && field(1).isUpper) List(snake(field.tail)) else Nil
      (List(field.toLowerCase, snake(field), field.toLowerCase + "_c") ++ unprefixed).find(columns.contains)
    }

  /** The (table, column) each UserReference names, with the references that name it. */
  private lazy val declaredColumns: Map[(String, String), List[UserReference]] =
    UserReference.all
      .flatMap(ref => ref.fields.flatMap(field =>
        storeTables.get(ref.mapperClass).flatMap(table => columnOf(table, field)).map(column => (table(ref), column) -> ref)))
      .groupBy(_._1)
      .map { case (key, pairs) => key -> pairs.map(_._2) }

  private def table(ref: UserReference): String = storeTables(ref.mapperClass)

  private lazy val excludedColumns: Set[(String, String)] =
    UserReference.notUserIdColumns.flatMap { case (cls, field, _) =>
      storeTables.get(cls).flatMap(table => columnOf(table, field).map(column => (table, column)))
    }.toSet

  /** Every column of the schema whose name says it holds a user id. */
  private lazy val schemaUserReferenceColumns: List[(String, String)] =
    (for {
      (table, columns) <- schemaColumns.toList
      column <- columns.toList
      if userReferenceNamePattern.pattern.matcher(column).matches()
    } yield (table, column)).sorted

  Feature("UserReference is a complete map of the user-reference columns in the schema") {

    Scenario("every user-reference column of a table a UserReference covers has a policy, or a documented reason not to", UserReferenceTag) {
      val coveredTables = storeTables.values.toSet
      val unclaimed = schemaUserReferenceColumns
        .filter { case (table, _) => coveredTables.contains(table) }
        .filterNot(declaredColumns.contains)
        .filterNot(excludedColumns.contains)

      withClue(
        s"""|${unclaimed.size} column(s) look like a user reference but no UserReference names them.
            |Add a UserReference with the right attribution policy, or list the column in
            |UserReference.notUserIdColumns with the reason it is not a user id:
            |${unclaimed.map { case (t, c) => s"  $t.$c" }.mkString("\n")}
            |""".stripMargin) {
        unclaimed shouldBe empty
      }
    }

    Scenario("every UserReference names a class that exists and a table that is in the schema", UserReferenceTag) {
      val problems = UserReference.all.map(_.mapperClass).distinct.flatMap { cls =>
        if (scala.util.Try(Class.forName(cls)).isFailure) Some(s"  $cls -- no such class")
        else storeTables.get(cls) match {
          case None => Some(s"  $cls -- not in storeTables: add the table it writes")
          case Some(t) if !schemaColumns.contains(t) => Some(s"  $cls -- its table $t is not in the schema")
          case _ => None
        }
      }.sorted

      withClue(
        s"""|${problems.size} UserReference class(es) cannot be matched to the schema:
            |${problems.mkString("\n")}
            |""".stripMargin) {
        problems shouldBe empty
      }
    }

    Scenario("every UserReference names fields that exist as columns of its table", UserReferenceTag) {
      val unknownFields = for {
        ref <- UserReference.all
        table <- storeTables.get(ref.mapperClass).toList
        field <- ref.fields
        if columnOf(table, field).isEmpty
      } yield s"  ${ref.name}: $table.$field"

      withClue(
        s"""|${unknownFields.size} UserReference field name(s) are not columns of their table -- a rename
            |or a typo. The policy silently covers nothing until fixed:
            |${unknownFields.sorted.mkString("\n")}
            |""".stripMargin) {
        unknownFields shouldBe empty
      }
    }

    // mappedentitlement.muserid is the deliberate case: Entitlement_UserId (UseOnBehalfOfUserId) and
    // Entitlement_UserId_ConsentScope (UseAuthenticatedUserId) name the same column, chosen per createdByProcess.
    Scenario("a column named by more than one UserReference has references that differ by policy", UserReferenceTag) {
      val ambiguous = declaredColumns.toList
        .filter { case (_, refs) => refs.size > 1 && refs.map(_.policy).distinct.size == 1 }
        .map { case ((t, c), refs) => s"  $t.$c -> ${refs.map(_.name).sorted.mkString(", ")} (all ${refs.head.policy})" }
        .sorted

      withClue(
        s"""|${ambiguous.size} column(s) are named by several UserReferences with the SAME policy, so
            |which one a provider should pass is undecidable. Merge them, or make the distinction real:
            |${ambiguous.mkString("\n")}
            |""".stripMargin) {
        ambiguous shouldBe empty
      }
    }

    Scenario("no column is both given a policy and listed as not-a-user-id", UserReferenceTag) {
      val contradictory = declaredColumns.keySet.intersect(excludedColumns).toList
        .map { case (t, c) => s"  $t.$c" }.sorted

      withClue(
        s"""|${contradictory.size} column(s) appear in BOTH UserReference.all and notUserIdColumns.
            |The exclusion list is for columns the name pattern catches by accident; a column with a
            |policy must not be in it:
            |${contradictory.mkString("\n")}
            |""".stripMargin) {
        contradictory shouldBe empty
      }
    }

    Scenario("notUserIdColumns only lists columns that exist and that the pattern actually catches", UserReferenceTag) {
      val stale = UserReference.notUserIdColumns.flatMap { case (cls, field, _) =>
        storeTables.get(cls) match {
          case None => Some(s"  $cls.$field -- not in storeTables")
          case Some(t) => columnOf(t, field) match {
            case None => Some(s"  $cls.$field -- no such column in $t")
            case Some(c) if !userReferenceNamePattern.pattern.matcher(c).matches() =>
              Some(s"  $cls.$field -- the name pattern does not catch this, so the entry is dead weight")
            case _ => None
          }
        }
      }.sorted

      withClue(
        s"""|${stale.size} notUserIdColumns entr(ies) are stale. An exclusion that matches nothing hides
            |nothing, and will not be noticed when the real column comes back:
            |${stale.mkString("\n")}
            |""".stripMargin) {
        stale shouldBe empty
      }
    }
  }
}
