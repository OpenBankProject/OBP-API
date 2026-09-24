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

import code.api.util.ApiRole
import code.setup.ServerSetupWithTestData
import org.scalatest.Tag

/**
 * Two guards that stop the "any bank" problem growing while it is being worked through.
 *
 * A Role declared `requiresBankId = false` is read at the empty bank id whatever bank was asked
 * about (`APIUtil.hasEntitlement`), so a single Entitlement row authorises the action at every bank,
 * including banks created later. That is right for a Role whose subject is the instance, such as
 * CanReadMetrics, and wrong for a Role whose subject belongs to one bank. The long term plan for
 * undoing the second kind is ANY_BANK_ROLE_REMOVAL_PLAN.md; this suite is what keeps the list
 * finite while that happens.
 *
 * Each guard carries an allowlist of what exists today. **An allowlist only ever shrinks.** Adding
 * a line to one means adding a new Role or endpoint that can act on every bank at once, which is
 * the thing being removed; removing a line is what finishing a piece of the plan looks like. If a
 * guard fails, the fix is almost always the code, not the list.
 */
class AnyBankScopeSweepTest extends ServerSetupWithTestData {

  object AnyBankScope extends Tag("AnyBankScope")

  // ---------------------------------------------------------------------------------------------
  // Guard 1 — Roles whose name says they reach every bank
  // ---------------------------------------------------------------------------------------------

  /** A Role name that says out loud that it reaches more than one bank. */
  private val anyBankNamePattern = """(AtAnyBank|AnyBank|AtAllBanks|AllBanks)""".r

  /**
   * The Roles that say "any bank" in their name and are declared `requiresBankId = false` today.
   * Every line is a Role that ANY_BANK_ROLE_REMOVAL_PLAN.md exists to retire.
   */
  private val rolesThatReachEveryBank: Set[String] = Set(
    "CanAddUserToGroupAtAllBanks",
    "CanCreateAccountAccessRequestAtAnyBank",
    "CanCreateAtmAtAnyBank",
    "CanCreateAtmAttributeAtAnyBank",
    "CanCreateBranchAtAnyBank",
    "CanCreateCounterpartyAtAnyBank",
    "CanCreateCustomerAtAnyBank",
    "CanCreateCustomerAttributeAtAnyBank",
    "CanCreateEntitlementAtAnyBank",
    "CanCreateFxRateAtAnyBank",
    "CanCreateGroupAtAllBanks",
    "CanCreateProductAtAnyBank",
    "CanCreateScopeAtAnyBank",
    "CanCreateUserCustomerLinkAtAnyBank",
    "CanDeleteAtmAtAnyBank",
    "CanDeleteAtmAttributeAtAnyBank",
    "CanDeleteBranchAtAnyBank",
    "CanDeleteCounterpartyAtAnyBank",
    "CanDeleteCustomerAttributeAtAnyBank",
    "CanDeleteEntitlementAtAnyBank",
    "CanDeleteEntitlementRequestsAtAnyBank",
    "CanDeleteGroupAtAllBanks",
    "CanDeleteScopeAtAnyBank",
    "CanDeleteUserCustomerLinkAtAnyBank",
    "CanGetAccountAccessRequestsAtAnyBank",
    "CanGetAccountsHeldAtAnyBank",
    "CanGetAccountsMinimalForCustomerAtAnyBank",
    "CanGetAtmAttributeAtAnyBank",
    "CanGetConsentsAtAnyBank",
    "CanGetCorrelatedUsersInfoAtAnyBank",
    "CanGetCounterpartiesAtAnyBank",
    "CanGetCounterpartyAtAnyBank",
    "CanGetCustomerAttributeAtAnyBank",
    "CanGetCustomerAttributesAtAnyBank",
    "CanGetCustomersAtAllBanks",
    "CanGetCustomersMinimalAtAllBanks",
    "CanGetDoubleEntryTransactionAtAnyBank",
    "CanGetEntitlementRequestsAtAnyBank",
    "CanGetEntitlementsForAnyBank",
    "CanGetEntitlementsForAnyUserAtAnyBank",
    "CanGetGroupsAtAllBanks",
    "CanGetRolesWithEntitlementCountsAtAllBanks",
    "CanGetTransactionRequestAtAnyBank",
    "CanGetUserCustomerLinkAtAnyBank",
    "CanGetUserGroupMembershipsAtAllBanks",
    "CanGetViewPermissionsAtAllBanks",
    "CanRemoveUserFromGroupAtAllBanks",
    "CanUpdateAccountAccessRequestAtAnyBank",
    "CanUpdateAgentStatusAtAnyBank",
    "CanUpdateAtmAtAnyBank",
    "CanUpdateAtmAttributeAtAnyBank",
    "CanUpdateConsentAccountAccessAtAnyBank",
    "CanUpdateConsentStatusAtAnyBank",
    "CanUpdateConsentUserAtAnyBank",
    "CanUpdateCustomerAttributeAtAnyBank",
    "CanUpdateCustomerCreditRatingAndSourceAtAnyBank",
    "CanUpdateGroupAtAllBanks",
    "CanUpdateProductTagsAtAnyBank",
    "CanUpdateTransactionRequestStatusAtAnyBank",
    "CanUseAccountFirehoseAtAnyBank",
    "CanUseCustomerFirehoseAtAnyBank"
  )

  // ---------------------------------------------------------------------------------------------
  // Guard 2 — endpoints whose URL names a bank while their Roles ignore it
  // ---------------------------------------------------------------------------------------------

  /**
   * Endpoints whose URL carries BANK_ID while every Role they declare is system scoped, so the bank
   * in the path does not narrow the permission at all: holding the Role once authorises the call at
   * every bank. These are any-bank Roles without the name, which is why they are easy to miss.
   *
   * Keyed by "VERB requestUrl" as the catalog reports it.
   */
  private val endpointsWhoseBankIdDoesNotNarrow: Set[String] = Set(
    "GET /banks/BANK_ID/accounts/ACCOUNT_ID/views/TARGET_VIEW_ID/users/TARGET_USER_ID/account-access-trace",
    "GET /banks/BANK_ID/accounts/ACCOUNT_ID/views/VIEW_ID/users-with-access",
    "GET /management/banks/BANK_ID/dynamic-message-docs",
    "GET /management/system/integrity/banks/BANK_ID/account-currency-check",
    "GET /management/system/integrity/banks/BANK_ID/orphaned-account-check",
    "POST /banks/BANK_ID/utility-payments/UTILITY_TRANSACTION_REQUEST_ID/vend-result",
    "POST /management/banks/BANK_ID/accounts/ACCOUNT_ID/views",
    "PUT /management/banks/BANK_ID/dynamic-message-docs/DYNAMIC_MESSAGE_DOC_ID"
  )

  private def rolesNamingAnyBank: List[ApiRole] =
    ApiRole.availableRoles
      // A Dynamic Entity Role is named after an entity an operator created, not written here, so it
      // is not something this suite can hold anyone to.
      .filterNot(_.contains("_"))
      .filter(name => anyBankNamePattern.findFirstIn(name).isDefined)
      .map(ApiRole.valueOf)

  feature("The set of Roles that can act on every bank does not grow") {
    scenario("Every Role naming any bank is already on the list being retired", AnyBankScope) {
      val reachEveryBank = rolesNamingAnyBank.filterNot(_.requiresBankId).map(_.toString).toSet

      val added = (reachEveryBank -- rolesThatReachEveryBank).toList.sorted
      withClue(
        "These Roles say 'any bank' in their name and are declared requiresBankId = false, which " +
        "means one Entitlement row authorises them at every bank, now and in the future. See " +
        "ANY_BANK_ROLE_REMOVAL_PLAN.md: the answer is a per bank Role, not a line in this list.\n" +
        added.mkString("\n") + "\n") {
        added shouldBe empty
      }

      val retired = (rolesThatReachEveryBank -- reachEveryBank).toList.sorted
      withClue(
        "These Roles are on the list but no longer reach every bank, so the list is stale. " +
        "Delete these lines - that is what finishing a retirement looks like.\n" +
        retired.mkString("\n") + "\n") {
        retired shouldBe empty
      }
    }
  }

  feature("The set of endpoints whose BANK_ID does not narrow the permission does not grow") {
    scenario("Every endpoint naming a bank either scopes a Role to it or is already on the list", AnyBankScope) {
      val offenders = EndpointCatalog.all.filter { doc =>
        doc.requestUrl.contains("BANK_ID") &&
          doc.roles.exists(roles => roles.nonEmpty && roles.forall(!_.requiresBankId))
      }.map(doc => s"${doc.requestVerb} ${doc.requestUrl}").toSet

      val added = (offenders -- endpointsWhoseBankIdDoesNotNarrow).toList.sorted
      withClue(
        "These endpoints name a bank in the URL, but every Role they declare is system scoped, so " +
        "holding that Role once authorises the call at every bank. Declare a bank scoped Role " +
        "instead, or alongside.\n" + added.mkString("\n") + "\n") {
        added shouldBe empty
      }

      val fixed = (endpointsWhoseBankIdDoesNotNarrow -- offenders).toList.sorted
      withClue(
        "These endpoints are on the list but now scope a Role to the bank in their URL, so the " +
        "list is stale. Delete these lines.\n" + fixed.mkString("\n") + "\n") {
        fixed shouldBe empty
      }
    }
  }
}
