package code.api.UKOpenBanking.v3_1_0

import code.api.util.APIUtil.{canGrantAccessToViewCommon, canRevokeAccessToViewCommon}
import code.api.util.ErrorMessages.UserNoOwnerView
import code.views.Views
import com.openbankproject.commons.model.{User, ViewIdBankIdAccountId}
import net.liftweb.common.{Empty, Failure, Full}

import scala.collection.immutable.List

object UtilForUKV310 {
  def grantAccessToViews(user: User, views: List[ViewIdBankIdAccountId]): Full[Boolean] = {
    val result =
      for {
        view <- views
      } yield {
        if (canGrantAccessToViewCommon(view.bankId, view.accountId, user)) {
          val viewIdBankIdAccountId = ViewIdBankIdAccountId(view.viewId, view.bankId, view.accountId)
          Views.views.vend.systemView(view.viewId) match {
            case Full(systemView) =>
              Views.views.vend.grantAccessToSystemView(view.bankId, view.accountId, systemView, user)
            case _ => // It's not system view
              Views.views.vend.grantAccessToCustomView(viewIdBankIdAccountId, user)
          }
        } else {
          Failure(UserNoOwnerView+" user_id : " + user.userId + ". account : " + view.accountId.value, Empty, Empty)
        }
      }
    if (result.forall(_.isDefined))
      Full(true)
    else {
      println(result.filter(_.isDefined == false))
      Full(false)
    }
  }

  def revokeAccessToViews(user: User, views: List[ViewIdBankIdAccountId]): Full[Boolean] = {
    val result =
      for {
        view <- views
      } yield {
        if (canRevokeAccessToViewCommon(view.bankId, view.accountId, user)) {
          val viewIdBankIdAccountId = ViewIdBankIdAccountId(view.viewId, view.bankId, view.accountId)
          Views.views.vend.systemView(view.viewId) match {
            case Full(systemView) =>
              Views.views.vend.revokeAccessToSystemView(view.bankId, view.accountId, systemView, user)
            case _ => // It's not system view
              Views.views.vend.revokeAccess(viewIdBankIdAccountId, user)
          }
        } else {
          Failure(UserNoOwnerView+" user_id : " + user.userId + ". account : " + view.accountId.value, Empty, Empty)
        }
      }
    if (result.forall(_.isDefined))
      Full(true)
    else {
      println(result.filter(_.isDefined == false))
      Full(false)
    }
  }
}
