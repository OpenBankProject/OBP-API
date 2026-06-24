/**
Open Bank Project - API
Copyright (C) 2011-2019, TESOBE GmbH.

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
package code.concurrency

import code.api.Constant.ALL_CONSUMERS
import code.views.Views
import code.views.system.{AccountAccess, ViewDefinition, ViewPermission}
import com.openbankproject.commons.model.{AccountId, BankId, ViewId}
import net.liftweb.mapper.By

import java.util.UUID

/**
 * Simulates the view-permission check-then-insert / delete-then-insert races. ViewPermission
 * carries UniqueIndex(bank_id, account_id, view_id, permission) but the insert paths call .save()
 * with NO tryo/try wrapper, so a concurrent duplicate insert throws an uncaught JDBC constraint
 * violation (a 500 at the HTTP layer) rather than resolving gracefully.
 *
 *  N. getOrCreateCustomPublicView check-then-insert — Views.getOrCreateCustomPublicView does
 *     find-then-create with no surrounding transaction; createAndSaveDefaultPublicCustomView calls
 *     .saveMe with no tryo. ViewDefinition's UniqueIndex(composite_unique_key) backs the natural key,
 *     so the second concurrent create throws an uncaught JDBC violation. (This is the same root cause
 *     as getOrCreateSystemView, which cannot be tested in isolation because system views are pinned
 *     to a global whitelist via ViewDefinition.beforeSave/isValidSystemViewId — a custom public view
 *     exercises the identical unguarded saveMe path on an isolated (bank,account) key.)
 *
 *  O. resetViewPermissions delete-then-insert — ViewPermission.resetViewPermissions deletes the
 *     view's permissions, then for each permission name does find-then-delete-then-insert (.save,
 *     no tryo). Two concurrent resets for the same view both clear the set, then both insert the
 *     same (bank,account,view,permission) tuple → the second INSERT violates the unique index,
 *     uncaught.
 *
 *  R. removeCustomView check-then-delete orphan — removeCustomView checks that no AccountAccess
 *     references the view, then deletes the view. The two steps are not atomic and there is no
 *     transaction, so a grant committing an AccountAccess in the window leaves a row pointing at a
 *     now-deleted view. This deterministically replays that window (the structural hazard).
 *
 * Asserts the correct (graceful, exactly-one-row, no-orphan) outcome, so EXPECTED TO FAIL while the
 * paths are unguarded. Tagged ConcurrencyRace.
 */
class ConcurrentViewPermissionRaceTest extends ConcurrentRaceSetup {

  feature("Concurrent view-permission mutation must stay graceful and consistent") {

    scenario("N: concurrent getOrCreateCustomPublicView must not throw and leave exactly one view", ConcurrencyRace) {
      Given("allow_public_views=true and an account with no _public view yet")
      setPropsValues("allow_public_views" -> "true")
      val bank      = createBank("__conc-pubview-bank")
      val bankId    = bank.bankId
      val accountId = AccountId("__conc_pubview_acc")
      createAccountRelevantResource(Some(resourceUser1), bankId, accountId, "EUR")

      def viewCount: Long = ViewDefinition.count(
        By(ViewDefinition.bank_id, bankId.value),
        By(ViewDefinition.account_id, accountId.value),
        By(ViewDefinition.view_id, "_public") // CUSTOM_PUBLIC_VIEW_ID
      )
      val before = viewCount
      val n      = 2

      When(s"$n threads concurrently getOrCreateCustomPublicView for the same account")
      val results = runConcurrentWithBarrier(n) { _ =>
        Views.views.vend.getOrCreateCustomPublicView(bankId, accountId, "conc public view")
      }

      Then("no call may throw, and exactly one _public view row must exist")
      val thrown   = results.collect { case scala.util.Failure(e) => e.getClass.getSimpleName + ": " + e.getMessage.take(120) }
      val created  = viewCount - before
      withClue(
        s"thrown=$thrown created=$created (expected: no throws, 1 row) — " +
        s"createAndSaveDefaultPublicCustomView .saveMe is unguarded against ViewDefinition's " +
        s"UniqueIndex(composite_unique_key); concurrent creates collide on the insert — "
      ) {
        thrown shouldBe empty
        created should equal(1L)
      }
    }

    scenario("O: concurrent resetViewPermissions on one view must not throw and must leave one row per permission", ConcurrencyRace) {
      Given("a dedicated custom view with a known permission set")
      val bank      = createBank("__conc-viewperm-bank")
      val bankId    = bank.bankId
      val accountId = AccountId("__conc_viewperm_acc")
      createAccountRelevantResource(Some(resourceUser1), bankId, accountId, "EUR")

      // A dedicated custom view row so the (bank,account,view) key is isolated from real test views.
      val viewIdStr = "__conc_o_view_" + UUID.randomUUID.toString.take(8)
      val view: ViewDefinition = ViewDefinition.create
        .isSystem_(false)
        .isFirehose_(false)
        .bank_id(bankId.value)
        .account_id(accountId.value)
        .view_id(viewIdStr)
        .name_("conc-o-view")
        .description_("conc-o")
        .isPublic_(false)
        .usePrivateAliasIfOneExists_(false)
        .usePublicAliasIfOneExists_(false)
        .hideOtherAccountMetadataIfAlias_(false)
        .saveMe()

      val permissionNames = List(
        "can_see_transaction_amount",
        "can_see_transaction_currency",
        "can_see_transaction_description"
      )

      def permCount: Long = ViewPermission.count(
        By(ViewPermission.bank_id, bankId.value),
        By(ViewPermission.account_id, accountId.value),
        By(ViewPermission.view_id, viewIdStr)
      )

      val n = 2

      When(s"$n threads concurrently resetViewPermissions for the same view")
      val results = runConcurrentWithBarrier(n) { _ =>
        ViewPermission.resetViewPermissions(view, permissionNames)
      }

      Then("no call may throw, and exactly one row per permission must remain")
      val thrown = results.collect { case scala.util.Failure(e) => e.getClass.getSimpleName + ": " + e.getMessage.take(120) }
      val finalCount = permCount
      withClue(
        s"thrown=$thrown finalCount=$finalCount (expected: no throws, ${permissionNames.size} rows) — " +
        s"resetViewPermissions .save() is unguarded against UniqueIndex(bank_id,account_id,view_id,permission); " +
        s"concurrent resets collide on the insert — "
      ) {
        thrown shouldBe empty
        finalCount should equal(permissionNames.size.toLong)
      }
    }

    scenario("R: removeCustomView's empty-check then delete must not orphan a concurrent grant", ConcurrencyRace) {
      Given("a custom view with no AccountAccess, so removeCustomView's emptiness guard would pass")
      val bank      = createBank("__conc-orphan-bank")
      val bankId    = bank.bankId
      val accountId = AccountId("__conc_orphan_acc")
      createAccountRelevantResource(Some(resourceUser1), bankId, accountId, "EUR")

      val viewIdStr = "__conc_r_view_" + UUID.randomUUID.toString.take(8)
      val view: ViewDefinition = ViewDefinition.create
        .isSystem_(false)
        .isFirehose_(false)
        .bank_id(bankId.value)
        .account_id(accountId.value)
        .view_id(viewIdStr)
        .name_("conc-r-view")
        .description_("conc-r")
        .isPublic_(false)
        .usePrivateAliasIfOneExists_(false)
        .usePublicAliasIfOneExists_(false)
        .hideOtherAccountMetadataIfAlias_(false)
        .saveMe()

      // removeCustomView (MapperViews.scala:502-517): (1) checks AccountAccess for the view is empty,
      // (2) then deletes the view. The two steps are not atomic and there is no transaction, so a grant
      // committing an AccountAccess in the window orphans a permission row. Replay that window deterministically.
      When("the emptiness check passes, then a concurrent grant commits an AccountAccess, then the view is deleted")
      val checkSawEmpty = AccountAccess.findAllByBankIdAccountIdViewId(bankId, accountId, ViewId(viewIdStr)).isEmpty
      AccountAccess.create
        .user_fk(resourceUser1.userPrimaryKey.value)
        .bank_id(bankId.value)
        .account_id(accountId.value)
        .view_id(viewIdStr)
        .consumer_id(ALL_CONSUMERS)
        .saveMe()
      view.delete_!

      Then("no AccountAccess may reference the now-deleted view (no orphaned permission row)")
      val orphans = AccountAccess.findAllByBankIdAccountIdViewId(bankId, accountId, ViewId(viewIdStr))
      withClue(
        s"checkSawEmpty=$checkSawEmpty orphans=${orphans.size} (expected 0): removeCustomView checks " +
        s"AccountAccess emptiness then deletes the view with no atomicity; a grant landing in the window " +
        s"leaves an AccountAccess pointing at a deleted view — "
      ) {
        orphans shouldBe empty
      }
    }
  }
}
