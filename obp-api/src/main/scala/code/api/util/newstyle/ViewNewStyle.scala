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

import code.api.Constant
import code.api.util.APIUtil.{OBPReturnType, unboxFullOrFail}
import code.api.util.ErrorMessages._
import code.api.util.{APIUtil, CallContext}
import code.model._
import code.views.Views
import code.views.system.ViewPermission
import com.openbankproject.commons.model._
import net.liftweb.common._

import scala.concurrent.Future

object ViewNewStyle {

  import com.openbankproject.commons.ExecutionContext.Implicits.global

  def customView(viewId: ViewId, bankAccountId: BankIdAccountId, callContext: Option[CallContext]): Future[View] = {
    Views.views.vend.customViewFuture(viewId, bankAccountId) map {
      unboxFullOrFail(_, callContext, s"$ViewNotFound. Current ViewId is $viewId")
    }
  }

  def systemView(viewId: ViewId, callContext: Option[CallContext]): Future[View] = {
    Views.views.vend.systemViewFuture(viewId) map {
      unboxFullOrFail(_, callContext, s"$SystemViewNotFound. Current ViewId is $viewId")
    }
  }

  def systemViews(): Future[List[View]] = {
    Views.views.vend.getSystemViews()
  }


  def grantAccessToCustomView(view: View, user: User, callContext: Option[CallContext]): Future[View] = {
    view.isSystem match {
      case false =>
        Future(Views.views.vend.grantAccessToCustomView(BankIdAccountIdViewId(view.bankId, view.accountId, view.viewId), user)) map {
          unboxFullOrFail(_, callContext, s"$CannotGrantAccountAccess Current ViewId is ${view.viewId.value}")
        }
      case true =>
        Future(Empty) map {
          unboxFullOrFail(_, callContext, s"This function cannot be used for system views.")
        }
    }
  }

  def revokeAccessToCustomView(view: View, user: User, callContext: Option[CallContext]): Future[Boolean] = {
    view.isSystem match {
      case false =>
        Future(Views.views.vend.revokeAccess(BankIdAccountIdViewId(view.bankId, view.accountId, view.viewId), user)) map {
          unboxFullOrFail(_, callContext, s"$CannotRevokeAccountAccess Current ViewId is ${view.viewId.value}")
        }
      case true =>
        Future(Empty) map {
          unboxFullOrFail(_, callContext, s"This function cannot be used for system views.")
        }
    }
  }

  def grantAccessToSystemView(bankId: BankId, accountId: AccountId, view: View, user: User, callContext: Option[CallContext]): Future[View] = {
    view.isSystem match {
      case true =>
        Future(Views.views.vend.grantAccessToSystemView(bankId, accountId, view, user)) map {
          unboxFullOrFail(_, callContext, s"$CannotGrantAccountAccess Current ViewId is ${view.viewId.value}")
        }
      case false =>
        Future(Empty) map {
          unboxFullOrFail(_, callContext, s"This function cannot be used for custom views.")
        }
    }
  }

  def revokeAccessToSystemView(bankId: BankId, accountId: AccountId, view: View, user: User, callContext: Option[CallContext]): Future[Boolean] = {
    view.isSystem match {
      case true =>
        Future(Views.views.vend.revokeAccessToSystemView(bankId, accountId, view, user)) map {
          unboxFullOrFail(_, callContext, s"$CannotRevokeAccountAccess Current ViewId is ${view.viewId.value}")
        }
      case false =>
        Future(Empty) map {
          unboxFullOrFail(_, callContext, s"This function cannot be used for custom views.")
        }
    }
  }

  def createSystemView(view: CreateViewJson, callContext: Option[CallContext]): Future[View] = {
    Views.views.vend.createSystemView(view) map {
      unboxFullOrFail(_, callContext, s"$CreateSystemViewError")
    }
  }

  def updateSystemView(viewId: ViewId, view: UpdateViewJSON, callContext: Option[CallContext]): Future[View] = {
    Views.views.vend.updateSystemView(viewId, view) map {
      unboxFullOrFail(_, callContext, s"$UpdateSystemViewError")
    }
  }

  def deleteSystemView(viewId: ViewId, callContext: Option[CallContext]): Future[Boolean] = {
    Views.views.vend.removeSystemView(viewId) map {
      unboxFullOrFail(_, callContext, s"$DeleteSystemViewError")
    }
  }

  def factoryResetSystemView(viewId: ViewId, callContext: Option[CallContext]): Future[View] = {
    Future(Views.views.vend.factoryResetSystemView(viewId)) map {
      unboxFullOrFail(_, callContext, s"$SystemViewNotFound Current ViewId is ${viewId.value}", 404)
    }
  }

  def checkOwnerViewAccessAndReturnOwnerView(user: User, bankAccountId: BankIdAccountId, callContext: Option[CallContext]): Future[View] = {
    Future {
      user.checkOwnerViewAccessAndReturnOwnerView(bankAccountId, callContext)
    } map {
      unboxFullOrFail(_, callContext, s"$UserNoOwnerView" + "userId : " + user.userId + ". bankId : " + s"${bankAccountId.bankId}" + ". accountId : " + s"${bankAccountId.accountId}")
    }
  }

  def checkViewAccessAndReturnView(viewId: ViewId, bankAccountId: BankIdAccountId, user: Option[User], callContext: Option[CallContext]): Future[View] = {
    Future {
      APIUtil.checkViewAccessAndReturnView(viewId, bankAccountId, user, callContext)
    } map {
      unboxFullOrFail(_, callContext, s"$UserNoPermissionAccessView Current ViewId is ${viewId.value}")
    }
  }

  def checkAccountAccessAndGetView(viewId: ViewId, bankAccountId: BankIdAccountId, user: Option[User], callContext: Option[CallContext]): Future[View] = {
    Future {
      APIUtil.checkViewAccessAndReturnView(viewId, bankAccountId, user, callContext)
    } map {
      unboxFullOrFail(_, callContext, s"$UserNoPermissionAccessView Current ViewId is ${viewId.value}", 403)
    }
  }

  def checkViewsAccessAndReturnView(firstView: ViewId, secondView: ViewId, bankAccountId: BankIdAccountId, user: Option[User], callContext: Option[CallContext]): Future[View] = {
    Future {
      APIUtil.checkViewAccessAndReturnView(firstView, bankAccountId, user, callContext).or(
        APIUtil.checkViewAccessAndReturnView(secondView, bankAccountId, user, callContext)
      )
    } map {
      unboxFullOrFail(_, callContext, s"$UserNoPermissionAccessView Current ViewId is  ${firstView.value} or ${secondView.value}")
    }
  }

  def checkBalancingTransactionAccountAccessAndReturnView(doubleEntryTransaction: DoubleEntryTransaction, user: Option[User], callContext: Option[CallContext]): Future[View] = {
    val debitBankAccountId = BankIdAccountId(
      doubleEntryTransaction.debitTransactionBankId,
      doubleEntryTransaction.debitTransactionAccountId
    )
    val creditBankAccountId = BankIdAccountId(
      doubleEntryTransaction.creditTransactionBankId,
      doubleEntryTransaction.creditTransactionAccountId
    )
    val ownerViewId = ViewId(Constant.SYSTEM_OWNER_VIEW_ID)
    Future {
      APIUtil.checkViewAccessAndReturnView(ownerViewId, debitBankAccountId, user, callContext).or(
        APIUtil.checkViewAccessAndReturnView(ownerViewId, creditBankAccountId, user, callContext)
      )
    } map {
      unboxFullOrFail(_, callContext, s"$UserNoPermissionAccessView Current ViewId is ${ownerViewId.value}")
    }
  }


  def createCustomView(bankAccountId: BankIdAccountId, createViewJson: CreateViewJson, callContext: Option[CallContext]): OBPReturnType[View] =
    Future {
      Views.views.vend.createCustomView(bankAccountId, createViewJson)
    } map { i =>
      (unboxFullOrFail(i, callContext, s"$CreateCustomViewError"), callContext)
    }

  def updateCustomView(bankAccountId: BankIdAccountId, viewId: ViewId, viewUpdateJson: UpdateViewJSON, callContext: Option[CallContext]): OBPReturnType[View] =
    Future {
      Views.views.vend.updateCustomView(bankAccountId, viewId, viewUpdateJson)
    } map { i =>
      (unboxFullOrFail(i, callContext, s"$UpdateCustomViewError"), callContext)
    }

  def removeCustomView(viewId: ViewId, bankAccountId: BankIdAccountId, callContext: Option[CallContext]) =
    Future {
      Views.views.vend.removeCustomView(viewId, bankAccountId)
    } map { i =>
      (unboxFullOrFail(i, callContext, s"$DeleteCustomViewError"), callContext)
    }

  def grantAccessToView(account: BankAccount, u: User, bankIdAccountIdViewId: BankIdAccountIdViewId, provider: String, providerId: String, callContext: Option[CallContext]) = Future {
    account.grantAccessToView(u, bankIdAccountIdViewId, provider, providerId, callContext: Option[CallContext])
  } map {
    x =>
      (unboxFullOrFail(
        x,
        callContext,
        UserLacksPermissionCanGrantAccessToViewForTargetAccount + s"Current ViewId(${bankIdAccountIdViewId.viewId.value}) and current UserId(${u.userId})",
        403),
        callContext
      )
  }

  def grantAccessToMultipleViews(account: BankAccount, u: User, bankIdAccountIdViewIds: List[BankIdAccountIdViewId], provider: String, providerId: String, callContext: Option[CallContext]) = Future {
    account.grantAccessToMultipleViews(u, bankIdAccountIdViewIds, provider, providerId, callContext: Option[CallContext])
  } map {
    x =>
      (unboxFullOrFail(
        x,
        callContext,
        UserLacksPermissionCanGrantAccessToViewForTargetAccount + s"Current ViewIds(${bankIdAccountIdViewIds}) and current UserId(${u.userId})",
        403),
        callContext
      )
  }

  def revokeAccessToView(account: BankAccount, u: User, bankIdAccountIdViewId: BankIdAccountIdViewId, provider: String, providerId: String, callContext: Option[CallContext]) = Future {
    account.revokeAccessToView(u, bankIdAccountIdViewId, provider, providerId, callContext: Option[CallContext])
  } map {
    x =>
      (unboxFullOrFail(
        x,
        callContext,
        UserLacksPermissionCanRevokeAccessToViewForTargetAccount + s"Current ViewId(${bankIdAccountIdViewId.viewId.value}) and current UserId(${u.userId})",
        403),
        callContext
      )
  }
  
  
  def findSystemViewPermission(viewId: ViewId, permissionName: String, callContext: Option[CallContext]) = Future {
    ViewPermission.findSystemViewPermission(viewId: ViewId, permissionName: String)
  } map {
    x =>
      (unboxFullOrFail(
        x,
        callContext,
        ViewPermissionNotFound + s"Current System ViewId(${viewId.value}) and PermissionName (${permissionName})",
        403),
        callContext
      )
  }
  
  
  def createSystemViewPermission(viewId: ViewId, permissionName: String, extraData: Option[List[String]], callContext: Option[CallContext]) = Future {
    ViewPermission.createSystemViewPermission(viewId: ViewId, permissionName: String, extraData: Option[List[String]])
  } map {
    x =>
      (unboxFullOrFail(
        x,
        callContext,
        CreateViewPermissionError + s"Current System ViewId(${viewId.value}) and Permission (${permissionName})",
        403),
        callContext
      )
  }

}
