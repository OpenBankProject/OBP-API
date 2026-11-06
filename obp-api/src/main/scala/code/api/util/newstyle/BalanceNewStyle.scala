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
import code.api.util.{APIUtil, CallContext}
import code.bankconnectors.Connector
import code.views.Views
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model._

import scala.concurrent.Future

object BalanceNewStyle {

  import com.openbankproject.commons.ExecutionContext.Implicits.global

  def getAccountAccessAtBankThroughView(user: User,
                                        bankId: BankId,
                                        viewId: ViewId,
                                        callContext: Option[CallContext]): OBPReturnType[List[BankIdAccountId]] = {
    Future {
      val (views, accountAccesses) = Views.views.vend.getAccountAccessAtBankThroughView(user, bankId, viewId)
      // Filter views which can read the balance
      val canSeeBankAccountBalanceViews = views.filter(_.allowed_actions.exists( _ == CAN_SEE_BANK_ACCOUNT_BALANCE))
      // Filter accounts the user has permission to see balances and remove duplicates
      val allowedAccounts = APIUtil.intersectAccountAccessAndView(accountAccesses, canSeeBankAccountBalanceViews)
      allowedAccounts
    }  map {
      (_, callContext)
    }
  }

  def getAccountAccessAtBank(user: User,
                             bankId: BankId,
                             callContext: Option[CallContext]): OBPReturnType[List[BankIdAccountId]] = {
    Future {
      val (views, accountAccesses) = Views.views.vend.privateViewsUserCanAccessAtBank(user, bankId)
      // Filter views which can read the balance
      
      val viewsWithActions = views.map(view => (view, view.allowed_actions))
      val canSeeBankAccountBalanceViews = viewsWithActions.filter {
        case (_, actions) => actions.contains(CAN_SEE_BANK_ACCOUNT_BALANCE)
      }.map(_._1)
      val allowedAccounts = APIUtil.intersectAccountAccessAndView(accountAccesses, canSeeBankAccountBalanceViews)
      allowedAccounts
    }  map {
      (_, callContext)
    }
  }

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
