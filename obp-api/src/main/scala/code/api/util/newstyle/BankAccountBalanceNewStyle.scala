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

import code.api.util.APIUtil.{OBPReturnType, unboxFullOrFail}
import code.api.util.ErrorMessages.BankAccountBalanceNotFoundById
import code.api.util.{APIUtil, CallContext}
import code.bankconnectors.Connector
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.model.{AccountId, BalanceId, BankAccountBalanceTrait, BankId}


object BankAccountBalanceNewStyle {

  def getBankAccountBalances(
    accountId: AccountId,
    callContext: Option[CallContext]
  ): OBPReturnType[List[BankAccountBalanceTrait]] = {
    Connector.connector.vend.getBankAccountBalancesByAccountId(
      accountId: AccountId,
      callContext: Option[CallContext]
    ) map {
      i => (APIUtil.connectorEmptyResponse(i._1, callContext), i._2)
    }
  }
  
  def getBankAccountsBalances(
    accountIds: List[AccountId],
    callContext: Option[CallContext]
  ): OBPReturnType[List[BankAccountBalanceTrait]] = {
    Connector.connector.vend.getBankAccountsBalancesByAccountIds(
      accountIds: List[AccountId],
      callContext: Option[CallContext]
    ) map {
      i => (APIUtil.connectorEmptyResponse(i._1, callContext), i._2)
    }
  }

  def getBankAccountBalanceById(
    balanceId: BalanceId,
    callContext: Option[CallContext]
  ): OBPReturnType[BankAccountBalanceTrait] = {
    Connector.connector.vend.getBankAccountBalanceById(
      balanceId: BalanceId,
      callContext: Option[CallContext]
    ).map {
      result =>
        (
          unboxFullOrFail(
            result._1,
            result._2,
            s"$BankAccountBalanceNotFoundById Current BALANCE_ID(${balanceId.value})",
            404),
          callContext
        )
    }
  }

  def createOrUpdateBankAccountBalance(
    bankId: BankId,
    accountId: AccountId,
    balanceId: Option[BalanceId],
    balanceType: String,
    balanceAmount: BigDecimal,
    callContext: Option[CallContext]
  ): OBPReturnType[BankAccountBalanceTrait] = {
    Connector.connector.vend.createOrUpdateBankAccountBalance(
      bankId: BankId,
      accountId: AccountId,
      balanceId: Option[BalanceId],
      balanceType: String,
      balanceAmount: BigDecimal,
      callContext: Option[CallContext]
    ) map {
      i => (APIUtil.connectorEmptyResponse(i._1, callContext), i._2)
    }
  }

  def deleteBankAccountBalance(
    balanceId: BalanceId,
    callContext: Option[CallContext]
  ): OBPReturnType[Boolean] = {
    Connector.connector.vend.deleteBankAccountBalance(
      balanceId: BalanceId,
      callContext: Option[CallContext]
    ) map {
      i => (APIUtil.connectorEmptyResponse(i._1, callContext), i._2)
    }
  }
  
}