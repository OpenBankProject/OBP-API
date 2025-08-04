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
