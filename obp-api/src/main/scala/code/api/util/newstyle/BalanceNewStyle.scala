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

package code.api.util.newstyle

import code.api.Constant._
import code.api.util.APIUtil.{OBPReturnType, unboxFullOrFail}
import code.api.util.ErrorMessages.InvalidConnectorResponse
import code.api.util.CallContext
import code.bankconnectors.Connector
import code.views.Views
import code.views.system.ViewPermission
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model._

import scala.concurrent.Future

object BalanceNewStyle {

  import com.openbankproject.commons.ExecutionContext.Implicits.global

  /**
   * Finds the accounts at a bank on which the user may see the balance, with the permissions the
   * user has on each one.
   *
   * A user can reach one account through several views. The permissions of all those views are
   * combined, so a field is shown if any of the views allows it. An account is kept only if at least
   * one of the views has CAN_SEE_BANK_ACCOUNT_BALANCE.
   *
   * Each view is matched to its own account. A custom view id such as "_team" can exist on several
   * accounts with different permissions, so matching on the view id alone is not enough.
   *
   * @param throughViewId when given, only the user's access through this view is used
   * @return the accounts in the order they were found, each with its combined permissions
   */
  def getAccountsWithBalancePermission(user: User,
                                       bankId: BankId,
                                       throughViewId: Option[ViewId],
                                       callContext: Option[CallContext]): OBPReturnType[List[(BankIdAccountId, Set[String])]] = {
    Future {
      // Each view object comes from one account access row, so it carries that row's bank and account
      val (views, _) = throughViewId match {
        case Some(viewId) => Views.views.vend.getAccountAccessAtBankThroughView(user, bankId, viewId)
        case None => Views.views.vend.privateViewsUserCanAccessAtBank(user, bankId)
      }
      val permissionsByView = ViewPermission.findPermissionsForViews(views)
      val accounts = views.map(view => BankIdAccountId(view.bankId, view.accountId)).distinct
      val permissionsByAccount = views
        .groupBy(view => BankIdAccountId(view.bankId, view.accountId))
        .map { case (account, accountViews) =>
          account -> accountViews.flatMap(view => permissionsByView.getOrElse(ViewPermission.viewKey(view), Set.empty[String])).toSet
        }
      accounts
        .map(account => account -> permissionsByAccount.getOrElse(account, Set.empty[String]))
        .filter { case (_, permissions) => permissions.contains(CAN_SEE_BANK_ACCOUNT_BALANCE) }
    } map {
      (_, callContext)
    }
  }

  /**
   * Removes what the user's views do not allow from the balances of several accounts.
   *
   * An account the user may not see the balance of is dropped. This is a safety net only: call
   * getAccountsWithBalancePermission first and send the connector only the accounts it returns,
   * because the connector works out the overall balance from the accounts it is given.
   */
  def moderateAccountsBalances(accountsBalances: AccountsBalances,
                               permissionsByAccount: Map[BankIdAccountId, Set[String]]): AccountsBalances = {
    val accounts = accountsBalances.accounts.flatMap { account =>
      val permissions = permissionsByAccount.getOrElse(BankIdAccountId(BankId(account.bankId), AccountId(account.id)), Set.empty[String])
      if (permissions.contains(CAN_SEE_BANK_ACCOUNT_BALANCE))
        Some(account.copy(
          label = moderateLabel(account.label, permissions),
          accountRoutings = moderateAccountRoutings(account.accountRoutings, permissions)
        ))
      else None
    }
    accountsBalances.copy(accounts = accounts)
  }

  /**
   * Removes what the user's views do not allow from the balances of one account. The caller must
   * already have checked that the permissions include CAN_SEE_BANK_ACCOUNT_BALANCE.
   */
  def moderateAccountBalances(accountBalances: AccountBalances, permissions: Set[String]): AccountBalances =
    accountBalances.copy(
      label = moderateLabel(accountBalances.label, permissions),
      accountRoutings = moderateAccountRoutings(accountBalances.accountRoutings, permissions)
    )

  // The same rules as View.moderateAccount uses for these fields
  private def moderateLabel(label: String, permissions: Set[String]): String =
    if (permissions.contains(CAN_SEE_BANK_ACCOUNT_LABEL)) label else ""

  private def moderateAccountRoutings(accountRoutings: List[AccountRouting], permissions: Set[String]): List[AccountRouting] =
    if (permissions.contains(CAN_SEE_BANK_ACCOUNT_ROUTING_SCHEME) && permissions.contains(CAN_SEE_BANK_ACCOUNT_ROUTING_ADDRESS)) accountRoutings
    else Nil

  def getBankAccountBalances(bankIdAccountId: BankIdAccountId, callContext: Option[CallContext]): OBPReturnType[AccountBalances] = {
    Connector.connector.vend.getBankAccountBalances(bankIdAccountId: BankIdAccountId, callContext: Option[CallContext]) map { i =>
      (unboxFullOrFail(i._1, callContext,s"$InvalidConnectorResponse ${nameOf(getBankAccountBalances _)} ", 400 ), i._2)
    }
  }

  def getBankAccountsBalances(bankIdAccountIds: List[BankIdAccountId], callContext: Option[CallContext]): OBPReturnType[AccountsBalances] = {
    Connector.connector.vend.getBankAccountsBalances(bankIdAccountIds: List[BankIdAccountId], callContext: Option[CallContext]) map { i =>
      (unboxFullOrFail(i._1, callContext,s"$InvalidConnectorResponse ${nameOf(getBankAccountsBalances _)}", 400 ), i._2)
    }
  }


}
