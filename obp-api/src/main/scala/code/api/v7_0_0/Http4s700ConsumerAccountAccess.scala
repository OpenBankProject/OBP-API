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
package code.api.v7_0_0

import code.api.Constant.{ApiPathZero, CAN_SEE_VIEWS_WITH_PERMISSIONS_FOR_ALL_USERS}
import code.api.util.APIUtil.{EmptyBody, Http4sRoute, ResourceDoc, canGrantAccessToView, canRevokeAccessToView, getUserLacksGrantPermissionErrorMessage, getUserLacksRevokePermissionErrorMessage, isOwnerView, isPublicView, isValidSystemViewId, userAuthenticationMessage}
import code.api.util.ApiTag._
import code.api.util.ErrorMessages._
import code.api.util.http4s.Http4sRequestAttributes.EndpointHelpers
import code.api.util.newstyle.ViewNewStyle
import code.api.util.{CallContext, CustomJsonFormats, Glossary, NewStyle}
import code.consumeraccountaccess.ConsumerAccountAccesses
import code.util.Helper
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.model.{BankIdAccountId, BankIdAccountIdViewId, ViewId}
import com.openbankproject.commons.util.ApiVersion
import net.liftweb.common.Box
import org.http4s._
import org.http4s.dsl.io._
import org.json4s.Formats

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Future

/**
 * The v7.0.0 Consumer account access endpoints: a view of an account granted to a Consumer, so that the
 * application, acting as itself (client credentials), can use that view on the endpoints that admit an
 * application. See [[code.consumeraccountaccess.ConsumerAccountAccesses]] and the Glossary item
 * "Consumer Account Access".
 */
object Http4s700ConsumerAccountAccess {

  implicit val formats: Formats = CustomJsonFormats.formats

  private val implementedInApiVersion = ApiVersion.v7_0_0
  private val prefixPath = Root / ApiPathZero.toString / implementedInApiVersion.toString

  val resourceDocs = ArrayBuffer[ResourceDoc]()

  private def provider = ConsumerAccountAccesses.consumerAccountAccessProvider.vend

  private def fullOrFail[T: Manifest](box: Box[T], cc: CallContext): T =
    code.api.util.APIUtil.unboxFullOrFail(box, Some(cc), UnknownError, 400)

  private def callerAndView(cc: CallContext) = (
    cc.user.openOrThrowException(AuthenticatedUserIsRequired),
    cc.bankAccount.getOrElse(throw new RuntimeException("BankAccount not found in CallContext")),
    cc.view.getOrElse(throw new RuntimeException("View not found in CallContext"))
  )

  private val description =
    s"""Account access can be granted to a Consumer as well as to a User. The application then uses the view
       |as itself, with an application token (OAuth2 client credentials), and no person is involved. A person
       |calling through the same Consumer does not get the Consumer's access, and an application identified
       |by its Consumer Key alone does not either.
       |
       |The access then works like a person's access to the view, on every endpoint that uses the view. For
       |example, the application can create an OPEN_CORRIDOR_PROMISE Transaction Request. The Consumer must be a
       |Platform App.
       |
       |Give the Consumer a view with only the permissions it needs, for example a custom view with
       |can_add_transaction_request_to_any_account, granted through the ManageCustomViews system view. The
       |owner view and public views can't be granted to a Consumer.
       |
       |For more information see ${Glossary.getGlossaryItemLink("Consumer Account Access")}""".stripMargin

  // Route: POST /obp/v7.0.0/banks/BANK_ID/accounts/ACCOUNT_ID/views/VIEW_ID/consumer-account-access (201)
  lazy val grantConsumerAccessToView: Http4sRoute = Http4sRoute {
    case req @ POST -> `prefixPath` / "banks" / _ / "accounts" / _ / "views" / _ / "consumer-account-access" =>
      EndpointHelpers.withViewAndBodyCreated[PostConsumerAccountAccessJsonV700, ConsumerAccountAccessJsonV700](req) {
        (user, account, view, body, cc) =>
          val targetViewId = ViewId(Option(body.view_id).map(_.trim).getOrElse(""))
          val bankIdAccountId = BankIdAccountId(account.bankId, account.accountId)
          for {
            _ <- Helper.booleanToFuture(s"$InvalidJsonFormat view_id and consumer_id are required.", failCode = 400, cc = Some(cc)) {
              targetViewId.value.nonEmpty && Option(body.consumer_id).exists(_.trim.nonEmpty)
            }
            _ <- Helper.booleanToFuture(CannotGrantConsumerAccountAccessToView, failCode = 400, cc = Some(cc)) {
              !isOwnerView(targetViewId)
            }
            _ <- Helper.booleanToFuture(getUserLacksGrantPermissionErrorMessage(view.viewId, targetViewId), failCode = 403, cc = Some(cc)) {
              canGrantAccessToView(BankIdAccountIdViewId(account.bankId, account.accountId, view.viewId), targetViewId, user, Some(cc))
            }
            targetView <- if (isValidSystemViewId(targetViewId.value)) ViewNewStyle.systemView(targetViewId, Some(cc))
                          else ViewNewStyle.customView(targetViewId, bankIdAccountId, Some(cc))
            _ <- Helper.booleanToFuture(CannotGrantConsumerAccountAccessToView, failCode = 400, cc = Some(cc)) {
              !targetView.isPublic && !isPublicView(targetView)
            }
            consumer <- NewStyle.function.getConsumerByConsumerId(body.consumer_id.trim, Some(cc))
            _ <- Helper.booleanToFuture(ConsumerAccountAccessAlreadyExists, failCode = 409, cc = Some(cc)) {
              provider.find(consumer.consumerId.get, account.bankId, account.accountId, targetView.viewId).isEmpty
            }
            granted <- Future(provider.grant(consumer.consumerId.get, account.bankId, account.accountId, targetView.viewId, user.userId)) map {
              x => fullOrFail(x, cc)
            }
          } yield JSONFactory700ConsumerAccountAccess.createConsumerAccountAccessJson(granted)
      }
  }

  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(grantConsumerAccessToView),
    "POST",
    "/banks/BANK_ID/accounts/ACCOUNT_ID/views/VIEW_ID/consumer-account-access",
    "Grant Consumer access to View",
    s"""Give the Consumer identified by `consumer_id` access to the view `view_id` of this account.
       |
       |The caller grants it through their own view VIEW_ID, with the same permissions as granting a view to a
       |User: for a system view, VIEW_ID must list it in `can_grant_access_to_views`; for a custom view, VIEW_ID
       |must have `can_grant_access_to_custom_views`. The grant records who gave it.
       |
       |$description
       |
       |${userAuthenticationMessage(true)}
       |""".stripMargin,
    JSONFactory700ConsumerAccountAccess.postConsumerAccountAccessJsonV700Example,
    JSONFactory700ConsumerAccountAccess.consumerAccountAccessJsonV700Example,
    List($AuthenticatedUserIsRequired, $BankNotFound, $BankAccountNotFound, $UserNoPermissionAccessView,
      InvalidJsonFormat, CannotGrantConsumerAccountAccessToView,
      UserLacksPermissionCanGrantAccessToSystemViewForTargetAccount,
      UserLacksPermissionCanGrantAccessToCustomViewForTargetAccount,
      SystemViewNotFound, ViewNotFound, ConsumerNotFoundByConsumerId, ConsumerAccountAccessAlreadyExists, UnknownError),
    List(apiTagAccountAccess, apiTagView, apiTagConsumer),
    None,
    http4sPartialFunction = Some(grantConsumerAccessToView)
  )

  // Route: GET /obp/v7.0.0/banks/BANK_ID/accounts/ACCOUNT_ID/views/VIEW_ID/consumer-account-access
  lazy val getConsumerAccountAccess: Http4sRoute = Http4sRoute {
    case req @ GET -> `prefixPath` / "banks" / _ / "accounts" / _ / "views" / _ / "consumer-account-access" =>
      EndpointHelpers.withView(req) { (_, account, view, cc) =>
        for {
          _ <- Helper.booleanToFuture(
            s"$UserNoPermissionAccessView The view ${view.viewId.value} needs the `$CAN_SEE_VIEWS_WITH_PERMISSIONS_FOR_ALL_USERS` permission.",
            failCode = 403, cc = Some(cc)) {
            view.allowed_actions.exists(_ == CAN_SEE_VIEWS_WITH_PERMISSIONS_FOR_ALL_USERS)
          }
          list <- Future(provider.getByAccount(account.bankId, account.accountId)) map { x => fullOrFail(x, cc) }
        } yield JSONFactory700ConsumerAccountAccess.createConsumerAccountAccessesJson(list)
      }
  }

  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getConsumerAccountAccess),
    "GET",
    "/banks/BANK_ID/accounts/ACCOUNT_ID/views/VIEW_ID/consumer-account-access",
    "Get Consumer access to Account",
    s"""The Consumers given access to views of this account: for each, the view, who granted it and when.
       |
       |The caller's view VIEW_ID needs the `$CAN_SEE_VIEWS_WITH_PERMISSIONS_FOR_ALL_USERS` permission, as for
       |seeing which Users have access to the account.
       |
       |$description
       |
       |${userAuthenticationMessage(true)}
       |""".stripMargin,
    EmptyBody,
    JSONFactory700ConsumerAccountAccess.consumerAccountAccessesJsonV700Example,
    List($AuthenticatedUserIsRequired, $BankNotFound, $BankAccountNotFound, $UserNoPermissionAccessView, UnknownError),
    List(apiTagAccountAccess, apiTagView, apiTagConsumer),
    None,
    http4sPartialFunction = Some(getConsumerAccountAccess)
  )

  // Route: DELETE /obp/v7.0.0/banks/BANK_ID/accounts/ACCOUNT_ID/views/VIEW_ID/consumer-account-access/CONSUMER_ACCOUNT_ACCESS_ID (204)
  lazy val revokeConsumerAccessToView: Http4sRoute = Http4sRoute {
    case req @ DELETE -> `prefixPath` / "banks" / _ / "accounts" / _ / "views" / _ / "consumer-account-access" / consumerAccountAccessId
      if consumerAccountAccessId.nonEmpty =>
      EndpointHelpers.executeDelete(req) { cc =>
        val (user, account, view) = callerAndView(cc)
        for {
          grant <- Future(provider.get(account.bankId, account.accountId, consumerAccountAccessId)) map {
            x => code.api.util.APIUtil.unboxFullOrFail(x, Some(cc), ConsumerAccountAccessNotFound, 404)
          }
          targetViewId = ViewId(grant.viewId)
          _ <- Helper.booleanToFuture(getUserLacksRevokePermissionErrorMessage(view.viewId, targetViewId), failCode = 403, cc = Some(cc)) {
            canRevokeAccessToView(BankIdAccountIdViewId(account.bankId, account.accountId, view.viewId), targetViewId, user, Some(cc))
          }
          _ <- Future(provider.revoke(grant.consumerAccountAccessId)) map { x => fullOrFail(x, cc) }
        } yield ()
      }
  }

  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(revokeConsumerAccessToView),
    "DELETE",
    "/banks/BANK_ID/accounts/ACCOUNT_ID/views/VIEW_ID/consumer-account-access/CONSUMER_ACCOUNT_ACCESS_ID",
    "Revoke Consumer access to View",
    s"""Take away a Consumer's access to a view of this account.
       |
       |The caller revokes it through their own view VIEW_ID, with the same permissions as revoking a view from a
       |User: for a system view, VIEW_ID must list it in `can_revoke_access_to_views`; for a custom view, VIEW_ID
       |must have `can_revoke_access_to_custom_views`.
       |
       |$description
       |
       |${userAuthenticationMessage(true)}
       |""".stripMargin,
    EmptyBody,
    EmptyBody,
    List($AuthenticatedUserIsRequired, $BankNotFound, $BankAccountNotFound, $UserNoPermissionAccessView,
      ConsumerAccountAccessNotFound,
      UserLacksPermissionCanRevokeAccessToSystemViewForTargetAccount,
      UserLacksPermissionCanRevokeAccessToCustomViewForTargetAccount, UnknownError),
    List(apiTagAccountAccess, apiTagView, apiTagConsumer),
    None,
    http4sPartialFunction = Some(revokeConsumerAccessToView)
  )
}
