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

import cats.effect.IO
import code.api.Constant
import code.api.Constant.ApiPathZero
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON.{bankAccountRoutingJson, moderatedAccountsJSON400}
import code.api.util.APIUtil.{Http4sRoute, _}
import code.api.util.ApiTag._
import code.api.util.ErrorMessages._
import code.api.util.http4s.Http4sRequestAttributes.EndpointHelpers
import code.api.util.newstyle.ViewNewStyle
import code.api.util.{CustomJsonFormats, NewStyle}
import code.api.v4_0_0.{BankAccountRoutingJson, JSONFactory400, ModeratedAccountsJSON400}
import code.metadata.tags.Tags
import code.views.Views
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.model.{BankId, BankIdAccountId, ViewId}
import com.openbankproject.commons.util.ApiVersion
import net.liftweb.common.Full
import org.http4s._
import org.http4s.dsl.io._
import org.json4s.Formats

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Future

/**
 * The caller's own accounts whose account routing matches a pattern.
 *
 * It replaces POST /obp/v4.0.0/management/accounts/account-routing-regex-query, which v7.0.0 does not serve
 * (Http4s700.excludeEndpoints): "management" in that path suggested it searched every account at the bank.
 *
 * Declared in its own object to keep Http4s700's initialiser under the JVM's 64KB method limit.
 */
object Http4s700MyAccounts {

  implicit val formats: Formats = CustomJsonFormats.formats

  private val implementedInApiVersion = ApiVersion.v7_0_0
  private val prefixPath = Root / ApiPathZero.toString / implementedInApiVersion.toString

  val resourceDocs = ArrayBuffer[ResourceDoc]()

  // Route: POST /obp/v7.0.0/my/accounts/account-routing-regex-query
  lazy val getMyAccountsByAccountRoutingRegex: Http4sRoute = Http4sRoute {
    case req @ POST -> `prefixPath` / "my" / "accounts" / "account-routing-regex-query" =>
      EndpointHelpers.withUserAndBody[BankAccountRoutingJson, ModeratedAccountsJSON400](req) { (user, postJson, cc) =>
        for {
          // a pattern that is not a valid regular expression is the caller's mistake, not a server error
          addressRegex <- NewStyle.function.tryons(InvalidAccountRoutingAddressRegex, 400, Some(cc)) {
            postJson.account_routing.address.r
          }
          (accountRoutings, _) <- NewStyle.function.getAccountRoutingsByScheme(
            postJson.bank_id.map(BankId(_)), postJson.account_routing.scheme, Some(cc))
          // only the caller's own accounts: those they reach through the owner view
          myAccounts <- Views.views.vend.getPrivateBankAccountsFuture(user, List(ViewId(Constant.SYSTEM_OWNER_VIEW_ID)))
          myAccountSet = myAccounts.toSet
          matching = accountRoutings
            .filter(routing => myAccountSet.contains(BankIdAccountId(routing.bankId, routing.accountId)))
            .filter(routing => addressRegex.findFirstIn(routing.accountRouting.address).isDefined)
          accountsJson <- Future.sequence(matching.map { routing =>
            val bankIdAccountId = BankIdAccountId(routing.bankId, routing.accountId)
            for {
              (account, _) <- NewStyle.function.getBankAccount(routing.bankId, routing.accountId, Some(cc))
              view <- ViewNewStyle.checkOwnerViewAccessAndReturnOwnerView(user, bankIdAccountId, Some(cc))
              moderatedAccount <- NewStyle.function.moderatedBankAccountCore(account, view, Full(user), Some(cc))
              (accountAttributes, _) <- NewStyle.function.getAccountAttributesByAccount(
                account.bankId, account.accountId, Some(cc))
              viewsAvailable = Views.views.vend.privateViewsUserCanAccessForAccount(user, bankIdAccountId)
                .map(code.api.v1_2_1.JSONFactory.createViewJSON).sortBy(_.short_name)
              tags = Tags.tags.vend.getTagsOnAccount(account.bankId, account.accountId)(view.viewId)
            } yield JSONFactory400.createBankAccountJSON(moderatedAccount, viewsAvailable, accountAttributes, tags)
          })
        } yield ModeratedAccountsJSON400(accountsJson)
      }
  }

  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getMyAccountsByAccountRoutingRegex),
    "POST",
    "/my/accounts/account-routing-regex-query",
    "Get My Accounts by Account Routing Regex",
    """Returns your own accounts whose account routing matches a pattern.
      |
      |Your own accounts are those you can see through the owner view. Other users' accounts are never searched,
      |and the response says nothing about them.
      |
      |Give the routing scheme and a regular expression for the routing address. The `bank_id` field is optional:
      |leave it out to search your accounts at every bank.
      |
      |Examples of a scheme: `IBAN`, `AccountNumber`.
      |Examples of an address pattern: `DE175.*`, `55897106215-[A-Z]{3}`.
      |
      |Use it to find accounts that share part of their routing address. For example, if your account numbers
      |are `123456789-EUR` and `123456789-USD`, this body finds both:
      |
      |```
      |{
      |   "bank_id": "BANK_ID",
      |   "account_routing": {
      |       "scheme": "AccountNumber",
      |       "address": "123456789-[A-Z]{3}"
      |   }
      |}
      |```
      |
      |If none of your accounts match, the list is empty.
      |
      |This replaces POST /obp/v4.0.0/management/accounts/account-routing-regex-query, which v7.0.0 does not serve.
      |
      |""".stripMargin,
    bankAccountRoutingJson,
    moderatedAccountsJSON400,
    List($AuthenticatedUserIsRequired, InvalidJsonFormat, InvalidAccountRoutingAddressRegex, $BankAccountNotFound,
      UnknownError),
    List(apiTagAccount),
    None,
    http4sPartialFunction = Some(getMyAccountsByAccountRoutingRegex)
  )
}
