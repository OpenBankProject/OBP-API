///**
//Open Bank Project - API
//Copyright (C) 2011-2018, TESOBE Ltd
//
//This program is free software: you can redistribute it and/or modify
//it under the terms of the GNU Affero General Public License as published by
//the Free Software Foundation, either version 3 of the License, or
//(at your option) any later version.
//
//This program is distributed in the hope that it will be useful,
//but WITHOUT ANY WARRANTY; without even the implied warranty of
//MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//GNU Affero General Public License for more details.
//
//You should have received a copy of the GNU Affero General Public License
//along with this program.  If not, see <http://www.gnu.org/licenses/>.
//
//Email: contact@tesobe.com
//TESOBE Ltd
//Osloerstrasse 16/17
//Berlin 13359, Germany
//
//  This product includes software developed at
//  TESOBE (http://www.tesobe.com/)
//  by
//  Simon Redfern : simon AT tesobe DOT com
//  Stefan Bethge : stefan AT tesobe DOT com
//  Everett Sochowski : everett AT tesobe DOT com
//  Ayoub Benali: ayoub AT tesobe DOT com
//
// */
//package code.api.v1_2
//
//import code.api.util.APIUtil
//import net.liftweb.http.rest._
//import net.liftweb.json.Extraction
//import net.liftweb.json.JsonAST._
//import net.liftweb.common.{Box, Empty, Failure, Full}
//import net.liftweb.mongodb._
//import _root_.java.math.MathContext
//
//import _root_.net.liftweb.util._
//import _root_.net.liftweb.util.Helpers._
//
//import _root_.scala.xml._
//import _root_.net.liftweb.http.S._
//import net.liftweb.mongodb.Skip
//import com.mongodb._
//import code.bankconnectors.{OBPFromDate, OBPLimit, OBPOffset, OBPOrder, OBPOrdering, OBPQueryParam, OBPToDate}
//import code.model._
//import java.net.URL
//
//import APIUtil._
//import code.api.OBPRestHelper
//import code.metadata.counterparties.Counterparties
//import code.util.Helper.MdcLoggable
//
//
//object OBPAPI1_2 extends OBPRestHelper with MdcLoggable {
//
//  //we now identify users by a combination of auth provider and the id given to them by their auth provider
//  // in v1.2 only one auth provider (the api itself) was possible. Because many functions now require both
//  //provider and id from the provider as arguments, we just use this value here as the provider.
//  val authProvider = APIUtil.getPropsValue("hostname","")
//  val version = "1.2"
//  val versionStatus = "DEPRECIATED"
//
//  private def bankAccountsListToJson(bankAccounts: List[BankAccount], user : Box[User]): JValue = {
//    val accJson : List[AccountJSON] = bankAccounts.map( account => {
//        val views = account permittedViews user
//        val viewsAvailable : List[ViewJSON] =
//            views.map( v => {
//              JSONFactory.createViewJSON(v)
//            })
//        JSONFactory.createAccountJSON(account,viewsAvailable)
//      })
//
//    val accounts = new AccountsJSON(accJson)
//    Extraction.decompose(accounts)
//  }
//
//  private def moderatedTransactionMetadata(bankId : BankId, accountId : AccountId, viewId : ViewId, transactionId : TransactionId, user : Box[User]) : Box[ModeratedTransactionMetadata] =
//    for {
//      account <- BankAccount(bankId, accountId)
//      view <- Views.views.vend.view(viewId, BankIdAccountId(account.bankId, account.accountId))
//      moderatedTransaction <- account.moderatedTransaction(transactionId, view, user)
//      metadata <- Box(moderatedTransaction.metadata) ?~ {"view " + viewId + " does not authorize metadata access"}
//    } yield metadata
//
//  oauthServe(apiPrefix {
//    case Nil JsonGet req => {
//      cc =>
//        val apiDetails: JValue = {
//          val hostedBy = new HostedBy("TESOBE", "contact@tesobe.com", "+49 (0)30 8145 3994")
//          val apiInfoJSON = new APIInfoJSON("1.2", gitCommit, hostedBy)
//          Extraction.decompose(apiInfoJSON)
//        }
//
//        Full(successJsonResponse(apiDetails, 200))
//    }
//  })
//
//  oauthServe(apiPrefix {
//  //get banks
//    case "banks" :: Nil JsonGet req => {
//      cc =>
//        def banksToJson(banksList: List[Bank]): JValue = {
//          val banksJSON: List[BankJSON] = banksList.map(b => {
//            JSONFactory.createBankJSON(b)
//          })
//          val banks = new BanksJSON(banksJSON)
//          Extraction.decompose(banks)
//        }
//
//        Full(successJsonResponse(banksToJson(Bank.all.get)))
//    }
//  })
//
//  oauthServe(apiPrefix{
//  //get bank by id
//    case "banks" :: BankId(bankId) :: Nil JsonGet req => {
//      cc =>
//        def bankToJson(bank : Bank) : JValue = {
//          val bankJSON = JSONFactory.createBankJSON(bank)
//          Extraction.decompose(bankJSON)
//        }
//        for((bank, callContext) <- Bank(bankId, Some(cc)))
//          yield successJsonResponse(bankToJson(bank))
//    }
//  })
//
//  /**
//   * This call is technically incorrect, but being kept this way for backwards compatibility. The original
//   * intent was to return Private accounts the user has access to AND the public accounts. When a user is logged
//   * in, this in fact only returns the Private accounts. Fixed in v1.2.1
//   */
//  oauthServe(apiPrefix {
//  //get accounts for a single bank
//    case "banks" :: BankId(bankId) :: "accounts" :: Nil JsonGet req => {
//      cc =>
//        for{
//         (bank, callContext) <- Bank(bankId, Some(cc))
//         availableAccounts <- bank.accountv12AndBelow(user)
//        } yield successJsonResponse(bankAccountsListToJson(availableAccounts, user))
//    }
//  })
//
//  oauthServe(apiPrefix {
//  //get private accounts for a single bank
//    case "banks" :: BankId(bankId) :: "accounts" :: "private" :: Nil JsonGet req => {
//      cc =>
//        for {
//          u <- user ?~ "user not found"
//          (bank, callContext) <- Bank(bankId, Some(cc))
//        } yield {
//          val availableAccounts = bank.PrivateAccounts(u)
//          successJsonResponse(bankAccountsListToJson(availableAccounts, Full(u)))
//        }
//    }
//  })
//
//  oauthServe(apiPrefix {
//  //get public accounts for a single bank
//    case "banks" :: BankId(bankId) :: "accounts" :: "public" :: Nil JsonGet req => {
//      cc =>
//        for {
//          (bank, callContext) <- Bank(bankId, Some(cc))
//        } yield {
//          val publicAccountsJson = bankAccountsListToJson(bank.publicAccounts, Empty)
//          successJsonResponse(publicAccountsJson)
//        }
//    }
//  })
//
//  oauthServe(apiPrefix {
//  //get account by id
//    case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "account" :: Nil JsonGet req => {
//      cc =>
//        for {
//          account <- BankAccount(bankId, accountId)
//          availableviews <- Full(account.permittedViews(user))
//          view <- Views.views.vend.view(viewId, BankIdAccountId(account.bankId, account.accountId))
//          moderatedAccount <- account.moderatedBankAccount(view, user)
//        } yield {
//            val viewsAvailable = availableviews.map(JSONFactory.createViewJSON)
//            val moderatedAccountJson = JSONFactory.createBankAccountJSON(moderatedAccount, viewsAvailable)
//            successJsonResponse(Extraction.decompose(moderatedAccountJson))
//          }
//    }
//  })
//
//  oauthServe(apiPrefix {
//  //get the available views on an bank account
//    case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: "views" :: Nil JsonGet req => {
//      cc =>
//        for {
//          account <- BankAccount(bankId, accountId)
//          u <- user ?~ "user not found"
//          views <- account views u
//        } yield {
//            val viewsJSON = JSONFactory.createViewsJSON(views)
//            successJsonResponse(Extraction.decompose(viewsJSON))
//          }
//    }
//  })
//
//  oauthServe(apiPrefix {
//  //creates a view on an bank account
//    case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: "views" :: Nil JsonPost json -> _ => {
//      cc =>
//        for {
//          json <- tryo{json.extract[CreateViewJson]} ?~ "wrong JSON format"
//          u <- user ?~ "user not found"
//          account <- BankAccount(bankId, accountId)
//          view <- account createView (u, json)
//        } yield {
//            val viewJSON = JSONFactory.createViewJSON(view)
//            successJsonResponse(Extraction.decompose(viewJSON), 201)
//          }
//    }
//  })
//
//  oauthServe(apiPrefix {
//    //updates a view on a bank account
//    case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: "views" :: ViewId(viewId) :: Nil JsonPut json -> _ => {
//      cc =>
//        for {
//          account <- BankAccount(bankId, accountId)
//          u <- user ?~ "user not found"
//          updateJson <- tryo{json.extract[UpdateViewJSON]} ?~ "wrong JSON format"
//          updatedView <- account.updateView(u, viewId, updateJson)
//        } yield {
//          val viewJSON = JSONFactory.createViewJSON(updatedView)
//          successJsonResponse(Extraction.decompose(viewJSON), 200)
//        }
//    }
//  })
//
//  oauthServe(apiPrefix {
//    //deletes a view on an bank account
//    case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: "views" :: ViewId(viewId) :: Nil JsonDelete req => {
//      cc =>
//        for {
//          u <- user ?~ "user not found"
//          account <- BankAccount(bankId, accountId)
//          view <- account removeView (u, viewId)
//        } yield noContentJsonResponse
//    }
//  })
//
//  oauthServe(apiPrefix {
//  //get access
//    case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: "permissions" :: Nil JsonGet req => {
//      cc =>
//        for {
//          account <- BankAccount(bankId, accountId)
//          u <- user ?~ "user not found"
//          permissions <- account permissions u
//        } yield {
//            val permissionsJSON = JSONFactory.createPermissionsJSON(permissions)
//            successJsonResponse(Extraction.decompose(permissionsJSON))
//          }
//    }
//  })
//
//  oauthServe(apiPrefix {
//  //get access for specific user
//    case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: "permissions" :: userId :: Nil JsonGet req => {
//      cc =>
//        for {
//          u <- user ?~ "user not found"
//          account <- BankAccount(bankId, accountId)
//          permission <- account permission(u, authProvider, userId)
//        } yield {
//            val views = JSONFactory.createViewsJSON(permission.views)
//            successJsonResponse(Extraction.decompose(views))
//        }
//    }
//  })
//
//  oauthServe(apiPrefix{
//    //add access for specific user to a list of views
//    case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: "permissions" :: userId :: "views" :: Nil JsonPost json -> _ => {
//      cc =>
//        for {
//          account <- BankAccount(bankId, accountId)
//          u <- user ?~ "user not found"
//          viewIds <- tryo{json.extract[ViewIdsJson]} ?~ "wrong format JSON"
//          addedViews <- account addPermissions(u, viewIds.views.map(viewIdString => ViewIdBankIdAccountId(ViewId(viewIdString), bankId, accountId)), authProvider, userId)
//        } yield {
//            val viewJson = JSONFactory.createViewsJSON(addedViews)
//            successJsonResponse(Extraction.decompose(viewJson), 201)
//          }
//    }
//  })
//
//  oauthServe(apiPrefix{
//  //add access for specific user to a specific view
//    case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: "permissions" :: userId :: "views" :: ViewId(viewId) :: Nil JsonPost json -> _ => {
//      cc =>
//        for {
//          account <- BankAccount(bankId, accountId)
//          u <- user ?~ "user not found"
//          addedView <- account addPermission(u, ViewIdBankIdAccountId(viewId, bankId, accountId), authProvider, userId)
//        } yield {
//            val viewJson = JSONFactory.createViewJSON(addedView)
//            successJsonResponse(Extraction.decompose(viewJson), 201)
//          }
//    }
//  })
//
//  oauthServe(apiPrefix{
//  //delete access for specific user to one view
//    case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: "permissions" :: userId :: "views" :: ViewId(viewId) :: Nil JsonDelete req => {
//      cc =>
//        for {
//          account <- BankAccount(bankId, accountId)
//          u <- user ?~ "user not found"
//          isRevoked <- account revokePermission(u, ViewIdBankIdAccountId(viewId, bankId, accountId), authProvider, userId)
//          if(isRevoked)
//        } yield noContentJsonResponse
//    }
//  })
//
//  oauthServe(apiPrefix{
//    //delete access for specific user to all the views
//    case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: "permissions" :: userId :: "views" :: Nil JsonDelete req => {
//      cc =>
//        for {
//          account <- BankAccount(bankId, accountId)
//          u <- user ?~ "user not found"
//          isRevoked <- account revokeAllPermissions(u, authProvider, userId)
//          if(isRevoked)
//        } yield noContentJsonResponse
//    }
//  })
//
//  oauthServe(apiPrefix{
//  //get other accounts for one account
//    case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "other_accounts" :: Nil JsonGet req => {
//      cc =>
//        for {
//          account <- BankAccount(bankId, accountId)
//          view <- Views.views.vend.view(viewId, BankIdAccountId(account.bankId, account.accountId))
//          otherBankAccounts <- account.moderatedOtherBankAccounts(view, user)
//        } yield {
//          val otherBankAccountsJson = JSONFactory.createOtherBankAccountsJSON(otherBankAccounts)
//          successJsonResponse(Extraction.decompose(otherBankAccountsJson))
//        }
//    }
//  })
//
//  oauthServe(apiPrefix{
//  //get one other account by id
//    case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "other_accounts":: other_account_id :: Nil JsonGet req => {
//      cc =>
//        for {
//          account <- BankAccount(bankId, accountId)
//          view <- Views.views.vend.view(viewId, BankIdAccountId(account.bankId, account.accountId))
//          otherBankAccount <- account.moderatedOtherBankAccount(other_account_id, view, user)
//        } yield {
//          val otherBankAccountJson = JSONFactory.createOtherBankAccount(otherBankAccount)
//          successJsonResponse(Extraction.decompose(otherBankAccountJson))
//        }
//    }
//  })
//
//  oauthServe(apiPrefix{
//  //get metadata of one other account
//    case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "other_accounts":: other_account_id :: "metadata" :: Nil JsonGet req => {
//      cc =>
//        for {
//          account <- BankAccount(bankId, accountId)
//          view <- Views.views.vend.view(viewId, BankIdAccountId(account.bankId, account.accountId))
//          otherBankAccount <- account.moderatedOtherBankAccount(other_account_id, view, user)
//          metadata <- Box(otherBankAccount.metadata) ?~ {"the view " + viewId + "does not allow metadata access"}
//        } yield {
//          val metadataJson = JSONFactory.createOtherAccountMetaDataJSON(metadata)
//          successJsonResponse(Extraction.decompose(metadataJson))
//        }
//    }
//  })
//
//  oauthServe(apiPrefix{
//  //get public alias of other bank account
//    case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "other_accounts":: other_account_id :: "public_alias" :: Nil JsonGet req => {
//      cc =>
//        for {
//          account <- BankAccount(bankId, accountId)
//          view <- Views.views.vend.view(viewId, BankIdAccountId(account.bankId, account.accountId))
//          otherBankAccount <- account.moderatedOtherBankAccount(other_account_id, view, user)
//          metadata <- Box(otherBankAccount.metadata) ?~ {"the view " + viewId + "does not allow metadata access"}
//          alias <- Box(metadata.publicAlias) ?~ {"the view " + viewId + "does not allow public alias access"}
//        } yield {
//          val aliasJson = JSONFactory.createAliasJSON(alias)
//          successJsonResponse(Extraction.decompose(aliasJson))
//        }
//    }
//  })
//
//  oauthServe(apiPrefix{
//  //add public alias to other bank account
//    case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "other_accounts":: other_account_id :: "public_alias" :: Nil JsonPost json -> _ => {
//      cc =>
//        for {
//          account <- BankAccount(bankId, accountId)
//          view <- Views.views.vend.view(viewId, BankIdAccountId(account.bankId, account.accountId))
//          otherBankAccount <- account.moderatedOtherBankAccount(other_account_id, view, user)
//          metadata <- Box(otherBankAccount.metadata) ?~ {"the view " + viewId + "does not allow metadata access"}
//          addAlias <- Box(metadata.addPublicAlias) ?~ {"the view " + viewId + "does not allow adding a public alias"}
//          aliasJson <- tryo{(json.extract[AliasJSON])} ?~ {"wrong JSON format"}
//          added <- Counterparties.counterparties.vend.addPublicAlias(other_account_id, aliasJson.alias) ?~ {"Alias cannot be added"}
//          if(added)
//        } yield {
//            successJsonResponse(Extraction.decompose(SuccessMessage("public alias added")), 201)
//        }
//    }
//  })
//
//  oauthServe(apiPrefix{
//  //update public alias of other bank account
//    case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "other_accounts":: other_account_id :: "public_alias" :: Nil JsonPut json -> _ => {
//      cc =>
//        for {
//          account <- BankAccount(bankId, accountId)
//          view <- Views.views.vend.view(viewId, BankIdAccountId(account.bankId, account.accountId))
//          otherBankAccount <- account.moderatedOtherBankAccount(other_account_id, view, user)
//          metadata <- Box(otherBankAccount.metadata) ?~ {"the view " + viewId + "does not allow metadata access"}
//          addAlias <- Box(metadata.addPublicAlias) ?~ {"the view " + viewId + "does not allow updating the public alias"}
//          aliasJson <- tryo{(json.extract[AliasJSON])} ?~ {"wrong JSON format"}
//          added <- Counterparties.counterparties.vend.addPublicAlias(other_account_id, aliasJson.alias) ?~ {"Alias cannot be updated"}
//          if(added)
//        } yield {
//            successJsonResponse(Extraction.decompose(SuccessMessage("public alias updated")))
//        }
//    }
//  })
//
//  oauthServe(apiPrefix{
//  //delete public alias of other bank account
//    case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "other_accounts":: other_account_id :: "public_alias" :: Nil JsonDelete _ => {
//      cc =>
//        for {
//          account <- BankAccount(bankId, accountId)
//          view <- Views.views.vend.view(viewId, BankIdAccountId(account.bankId, account.accountId))
//          otherBankAccount <- account.moderatedOtherBankAccount(other_account_id, view, user)
//          metadata <- Box(otherBankAccount.metadata) ?~ {"the view " + viewId + "does not allow metadata access"}
//          addAlias <- Box(metadata.addPublicAlias) ?~ {"the view " + viewId + "does not allow deleting the public alias"}
//          added <- Counterparties.counterparties.vend.addPublicAlias(other_account_id, "") ?~ {"Alias cannot be deleted"}
//          if(added)
//        } yield noContentJsonResponse
//    }
//  })
//
//
//  oauthServe(apiPrefix{
//  //get private alias of other bank account
//    case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "other_accounts":: other_account_id :: "private_alias" :: Nil JsonGet req => {
//      cc =>
//        for {
//          account <- BankAccount(bankId, accountId)
//          view <- Views.views.vend.view(viewId, BankIdAccountId(account.bankId, account.accountId))
//          otherBankAccount <- account.moderatedOtherBankAccount(other_account_id, view, user)
//          metadata <- Box(otherBankAccount.metadata) ?~ {"the view " + viewId + "does not allow metadata access"}
//          alias <- Box(metadata.privateAlias) ?~ {"the view " + viewId + "does not allow private alias access"}
//        } yield {
//          val aliasJson = JSONFactory.createAliasJSON(alias)
//          successJsonResponse(Extraction.decompose(aliasJson))
//        }
//    }
//  })
//
//  oauthServe(apiPrefix{
//  //add private alias to other bank account
//    case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "other_accounts":: other_account_id :: "private_alias" :: Nil JsonPost json -> _ => {
//      cc =>
//        for {
//          account <- BankAccount(bankId, accountId)
//          view <- Views.views.vend.view(viewId, BankIdAccountId(account.bankId, account.accountId))
//          otherBankAccount <- account.moderatedOtherBankAccount(other_account_id, view, user)
//          metadata <- Box(otherBankAccount.metadata) ?~ {"the view " + viewId + "does not allow metadata access"}
//          addAlias <- Box(metadata.addPrivateAlias) ?~ {"the view " + viewId + "does not allow adding a private alias"}
//          aliasJson <- tryo{(json.extract[AliasJSON])} ?~ {"wrong JSON format"}
//          added <- Counterparties.counterparties.vend.addPrivateAlias(other_account_id, aliasJson.alias) ?~ {"Alias cannot be added"}
//          if(added)
//        } yield {
//            val successJson = SuccessMessage("private alias added")
//            successJsonResponse(Extraction.decompose(successJson), 201)
//        }
//    }
//  })
//
//  oauthServe(apiPrefix{
//  //update private alias of other bank account
//    case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "other_accounts":: other_account_id :: "private_alias" :: Nil JsonPut json -> _ => {
//      cc =>
//        for {
//          account <- BankAccount(bankId, accountId)
//          view <- Views.views.vend.view(viewId, BankIdAccountId(account.bankId, account.accountId))
//          otherBankAccount <- account.moderatedOtherBankAccount(other_account_id, view, user)
//          metadata <- Box(otherBankAccount.metadata) ?~ {"the view " + viewId + "does not allow metadata access"}
//          addAlias <- Box(metadata.addPrivateAlias) ?~ {"the view " + viewId + "does not allow updating the private alias"}
//          aliasJson <- tryo{(json.extract[AliasJSON])} ?~ {"wrong JSON format"}
//          added <- Counterparties.counterparties.vend.addPrivateAlias(other_account_id, aliasJson.alias) ?~ {"Alias cannot be updated"}
//          if(added)
//        } yield {
//            val successJson = SuccessMessage("private alias updated")
//            successJsonResponse(Extraction.decompose(successJson))
//        }
//    }
//  })
//
//  oauthServe(apiPrefix{
//  //delete private alias of other bank account
//    case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "other_accounts":: other_account_id :: "private_alias" :: Nil JsonDelete _ => {
//      cc =>
//        for {
//          account <- BankAccount(bankId, accountId)
//          view <- Views.views.vend.view(viewId, BankIdAccountId(account.bankId, account.accountId))
//          otherBankAccount <- account.moderatedOtherBankAccount(other_account_id, view, user)
//          metadata <- Box(otherBankAccount.metadata) ?~ {"the view " + viewId + "does not allow metadata access"}
//          addAlias <- Box(metadata.addPrivateAlias) ?~ {"the view " + viewId + "does not allow deleting the private alias"}
//          added <- Counterparties.counterparties.vend.addPrivateAlias(other_account_id, "") ?~ {"Alias cannot be deleted"}
//          if(added)
//        } yield noContentJsonResponse
//    }
//  })
//
//  oauthServe(apiPrefix{
//  //add more info to other bank account
//    case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "other_accounts":: other_account_id :: "more_info" :: Nil JsonPost json -> _ => {
//      cc =>
//        for {
//          account <- BankAccount(bankId, accountId)
//          view <- Views.views.vend.view(viewId, BankIdAccountId(account.bankId, account.accountId))
//          otherBankAccount <- account.moderatedOtherBankAccount(other_account_id, view, user)
//          metadata <- Box(otherBankAccount.metadata) ?~ {"the view " + viewId + "does not allow metadata access"}
//          addMoreInfo <- Box(metadata.addMoreInfo) ?~ {"the view " + viewId + "does not allow adding more info"}
//          moreInfoJson <- tryo{(json.extract[MoreInfoJSON])} ?~ {"wrong JSON format"}
//          added <- Counterparties.counterparties.vend.addMoreInfo(other_account_id, moreInfoJson.more_info) ?~ {"More Info cannot be added"}
//          if(added)
//        } yield {
//            val successJson = SuccessMessage("more info added")
//            successJsonResponse(Extraction.decompose(successJson), 201)
//        }
//    }
//  })
//
//  oauthServe(apiPrefix{
//  //update more info of other bank account
//    case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "other_accounts":: other_account_id :: "more_info" :: Nil JsonPut json -> _ => {
//      cc =>
//        for {
//          account <- BankAccount(bankId, accountId)
//          view <- Views.views.vend.view(viewId, BankIdAccountId(account.bankId, account.accountId))
//          otherBankAccount <- account.moderatedOtherBankAccount(other_account_id, view, user)
//          metadata <- Box(otherBankAccount.metadata) ?~ {"the view " + viewId + "does not allow metadata access"}
//          addMoreInfo <- Box(metadata.addMoreInfo) ?~ {"the view " + viewId + "does not allow updating more info"}
//          moreInfoJson <- tryo{(json.extract[MoreInfoJSON])} ?~ {"wrong JSON format"}
//          updated <- Counterparties.counterparties.vend.addMoreInfo(other_account_id, moreInfoJson.more_info) ?~ {"More Info cannot be updated"}
//          if(updated)
//        } yield {
//            val successJson = SuccessMessage("more info updated")
//            successJsonResponse(Extraction.decompose(successJson))
//        }
//    }
//  })
//
//  oauthServe(apiPrefix{
//  //delete more info of other bank account
//    case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "other_accounts":: other_account_id :: "more_info" :: Nil JsonDelete _ => {
//      cc =>
//        for {
//          account <- BankAccount(bankId, accountId)
//          view <- Views.views.vend.view(viewId, BankIdAccountId(account.bankId, account.accountId))
//          otherBankAccount <- account.moderatedOtherBankAccount(other_account_id, view, user)
//          metadata <- Box(otherBankAccount.metadata) ?~ {"the view " + viewId + "does not allow metadata access"}
//          addMoreInfo <- Box(metadata.addMoreInfo) ?~ {"the view " + viewId + "does not allow deleting more info"}
//          deleted <- Counterparties.counterparties.vend.addMoreInfo(other_account_id, "") ?~ {"More Info cannot be deleted"}
//          if(deleted)
//        } yield noContentJsonResponse
//    }
//  })
//
//  oauthServe(apiPrefix{
//  //add url to other bank account
//    case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "other_accounts":: other_account_id :: "url" :: Nil JsonPost json -> _ => {
//      cc =>
//        for {
//          account <- BankAccount(bankId, accountId)
//          view <- Views.views.vend.view(viewId, BankIdAccountId(account.bankId, account.accountId))
//          otherBankAccount <- account.moderatedOtherBankAccount(other_account_id, view, user)
//          metadata <- Box(otherBankAccount.metadata) ?~ {"the view " + viewId + "does not allow metadata access"}
//          addUrl <- Box(metadata.addURL) ?~ {"the view " + viewId + "does not allow adding a url"}
//          urlJson <- tryo{(json.extract[UrlJSON])} ?~ {"wrong JSON format"}
//          added <- Counterparties.counterparties.vend.addURL(other_account_id, urlJson.URL) ?~ {"URL cannot be added"}
//          if(added)
//        } yield {
//            val successJson = SuccessMessage("url added")
//            successJsonResponse(Extraction.decompose(successJson), 201)
//        }
//    }
//  })
//
//  oauthServe(apiPrefix{
//  //update url of other bank account
//    case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "other_accounts":: other_account_id :: "url" :: Nil JsonPut json -> _ => {
//      cc =>
//        for {
//          account <- BankAccount(bankId, accountId)
//          view <- Views.views.vend.view(viewId, BankIdAccountId(account.bankId, account.accountId))
//          otherBankAccount <- account.moderatedOtherBankAccount(other_account_id, view, user)
//          metadata <- Box(otherBankAccount.metadata) ?~ {"the view " + viewId + "does not allow metadata access"}
//          addUrl <- Box(metadata.addURL) ?~ {"the view " + viewId + "does not allow updating a url"}
//          urlJson <- tryo{(json.extract[UrlJSON])} ?~ {"wrong JSON format"}
//          added <- Counterparties.counterparties.vend.addURL(other_account_id, urlJson.URL) ?~ {"URL cannot be updated"}
//          if(added)
//        } yield {
//            val successJson = SuccessMessage("url updated")
//            successJsonResponse(Extraction.decompose(successJson))
//        }
//    }
//  })
//
//  oauthServe(apiPrefix{
//  //delete url of other bank account
//    case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "other_accounts":: other_account_id :: "url" :: Nil JsonDelete _ => {
//      cc =>
//        for {
//          account <- BankAccount(bankId, accountId)
//          view <- Views.views.vend.view(viewId, BankIdAccountId(account.bankId, account.accountId))
//          otherBankAccount <- account.moderatedOtherBankAccount(other_account_id, view, user)
//          metadata <- Box(otherBankAccount.metadata) ?~ {"the view " + viewId + "does not allow metadata access"}
//          addUrl <- Box(metadata.addURL) ?~ {"the view " + viewId + "does not allow deleting a url"}
//          added <- Counterparties.counterparties.vend.addURL(other_account_id, "") ?~ {"URL cannot be deleted"}
//          if(added)
//        } yield noContentJsonResponse
//    }
//  })
//
//  oauthServe(apiPrefix{
//  //add image url to other bank account
//    case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "other_accounts":: other_account_id :: "image_url" :: Nil JsonPost json -> _ => {
//      cc =>
//        for {
//          account <- BankAccount(bankId, accountId)
//          view <- Views.views.vend.view(viewId, BankIdAccountId(account.bankId, account.accountId))
//          otherBankAccount <- account.moderatedOtherBankAccount(other_account_id, view, user)
//          metadata <- Box(otherBankAccount.metadata) ?~ {"the view " + viewId + "does not allow metadata access"}
//          addImageUrl <- Box(metadata.addImageURL) ?~ {"the view " + viewId + "does not allow adding an image url"}
//          imageUrlJson <- tryo{(json.extract[ImageUrlJSON])} ?~ {"wrong JSON format"}
//          added <- Counterparties.counterparties.vend.addImageURL(other_account_id, imageUrlJson.image_URL) ?~ {"URL cannot be added"}
//          if(added)
//        } yield {
//            val successJson = SuccessMessage("image url added")
//            successJsonResponse(Extraction.decompose(successJson), 201)
//        }
//    }
//  })
//
//  oauthServe(apiPrefix{
//  //update image url of other bank account
//    case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "other_accounts":: other_account_id :: "image_url" :: Nil JsonPut json -> _ => {
//      cc =>
//        for {
//          account <- BankAccount(bankId, accountId)
//          view <- Views.views.vend.view(viewId, BankIdAccountId(account.bankId, account.accountId))
//          otherBankAccount <- account.moderatedOtherBankAccount(other_account_id, view, user)
//          metadata <- Box(otherBankAccount.metadata) ?~ {"the view " + viewId + "does not allow metadata access"}
//          addImageUrl <- Box(metadata.addImageURL) ?~ {"the view " + viewId + "does not allow updating an image url"}
//          imageUrlJson <- tryo{(json.extract[ImageUrlJSON])} ?~ {"wrong JSON format"}
//          updated <- Counterparties.counterparties.vend.addImageURL(other_account_id, imageUrlJson.image_URL) ?~ {"URL cannot be updated"}
//          if(updated)
//        } yield {
//            val successJson = SuccessMessage("image url updated")
//            successJsonResponse(Extraction.decompose(successJson))
//        }
//    }
//  })
//
//  oauthServe(apiPrefix{
//  //delete image url of other bank account
//    case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "other_accounts":: other_account_id :: "image_url" :: Nil JsonDelete _ => {
//      cc =>
//        for {
//          account <- BankAccount(bankId, accountId)
//          view <- Views.views.vend.view(viewId, BankIdAccountId(account.bankId, account.accountId))
//          otherBankAccount <- account.moderatedOtherBankAccount(other_account_id, view, user)
//          metadata <- Box(otherBankAccount.metadata) ?~ {"the view " + viewId + "does not allow metadata access"}
//          addImageUrl <- Box(metadata.addImageURL) ?~ {"the view " + viewId + "does not allow deleting an image url"}
//          deleted <- Counterparties.counterparties.vend.addImageURL(other_account_id, "") ?~ {"URL cannot be deleted"}
//          if(deleted)
//        } yield noContentJsonResponse
//    }
//  })
//
//  oauthServe(apiPrefix{
//  //add open corporate url to other bank account
//    case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "other_accounts":: other_account_id :: "open_corporates_url" :: Nil JsonPost json -> _ => {
//      cc =>
//        for {
//          account <- BankAccount(bankId, accountId)
//          view <- Views.views.vend.view(viewId, BankIdAccountId(account.bankId, account.accountId))
//          otherBankAccount <- account.moderatedOtherBankAccount(other_account_id, view, user)
//          metadata <- Box(otherBankAccount.metadata) ?~ {"the view " + viewId + "does not allow metadata access"}
//          addOpenCorpUrl <- Box(metadata.addOpenCorporatesURL) ?~ {"the view " + viewId + "does not allow adding an open corporate url"}
//          openCorpUrl <- tryo{(json.extract[OpenCorporateUrlJSON])} ?~ {"wrong JSON format"}
//          added <- Counterparties.counterparties.vend.addOpenCorporatesURL(other_account_id, openCorpUrl.open_corporates_URL) ?~ {"URL cannot be added"}
//          if(added)
//        } yield {
//            val successJson = SuccessMessage("open corporate url added")
//            successJsonResponse(Extraction.decompose(successJson), 201)
//        }
//    }
//  })
//
//  oauthServe(apiPrefix{
//  //update open corporate url of other bank account
//    case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "other_accounts":: other_account_id :: "open_corporates_url" :: Nil JsonPut json -> _ => {
//      cc =>
//        for {
//          account <- BankAccount(bankId, accountId)
//          view <- Views.views.vend.view(viewId, BankIdAccountId(account.bankId, account.accountId))
//          otherBankAccount <- account.moderatedOtherBankAccount(other_account_id, view, user)
//          metadata <- Box(otherBankAccount.metadata) ?~ {"the view " + viewId + "does not allow metadata access"}
//          addOpenCorpUrl <- Box(metadata.addOpenCorporatesURL) ?~ {"the view " + viewId + "does not allow updating an open corporate url"}
//          openCorpUrl <- tryo{(json.extract[OpenCorporateUrlJSON])} ?~ {"wrong JSON format"}
//          updated <- Counterparties.counterparties.vend.addOpenCorporatesURL(other_account_id, openCorpUrl.open_corporates_URL) ?~ {"URL cannot be updated"}
//          if(updated)
//        } yield {
//            val successJson = SuccessMessage("open corporate url updated")
//            successJsonResponse(Extraction.decompose(successJson))
//        }
//    }
//  })
//
//  oauthServe(apiPrefix{
//  //delete open corporate url of other bank account
//    case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "other_accounts":: other_account_id :: "open_corporates_url" :: Nil JsonDelete _ => {
//      cc =>
//        for {
//          account <- BankAccount(bankId, accountId)
//          view <- Views.views.vend.view(viewId, BankIdAccountId(account.bankId, account.accountId))
//          otherBankAccount <- account.moderatedOtherBankAccount(other_account_id, view, user)
//          metadata <- Box(otherBankAccount.metadata) ?~ {"the view " + viewId + "does not allow metadata access"}
//          addOpenCorpUrl <- Box(metadata.addOpenCorporatesURL) ?~ {"the view " + viewId + "does not allow deleting an open corporate url"}
//          deleted <- Counterparties.counterparties.vend.addOpenCorporatesURL(other_account_id, "") ?~ {"URL cannot be deleted"}
//          if(deleted)
//        } yield noContentJsonResponse
//    }
//  })
//
//  oauthServe(apiPrefix{
//  //add corporate location to other bank account
//    case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "other_accounts" :: other_account_id :: "corporate_location" :: Nil JsonPost json -> _ => {
//      cc =>
//        for {
//          u <- user
//          account <- BankAccount(bankId, accountId)
//          view <- Views.views.vend.view(viewId, BankIdAccountId(account.bankId, account.accountId))
//          otherBankAccount <- account.moderatedOtherBankAccount(other_account_id, view, user)
//          metadata <- Box(otherBankAccount.metadata) ?~ {"the view " + viewId + "does not allow metadata access"}
//          addCorpLocation <- Box(metadata.addCorporateLocation) ?~ {"the view " + viewId + "does not allow adding a corporate location"}
//          corpLocationJson <- tryo{(json.extract[CorporateLocationJSON])} ?~ {"wrong JSON format"}
//          correctCoordinates <- checkIfLocationPossible(corpLocationJson.corporate_location.latitude, corpLocationJson.corporate_location.longitude)
//          added <- Counterparties.counterparties.vend.addCorporateLocation(other_account_id, u.resourceUserId, (now:TimeSpan), corpLocationJson.corporate_location.longitude, corpLocationJson.corporate_location.latitude) ?~ {"Corporate Location cannot be deleted"}
//          if(added)
//        } yield {
//            val successJson = SuccessMessage("corporate location added")
//            successJsonResponse(Extraction.decompose(successJson), 201)
//        }
//    }
//  })
//
//  oauthServe(apiPrefix{
//  //update corporate location of other bank account
//    case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "other_accounts":: other_account_id :: "corporate_location" :: Nil JsonPut json -> _ => {
//      cc =>
//        for {
//          u <- user
//          account <- BankAccount(bankId, accountId)
//          view <- Views.views.vend.view(viewId, BankIdAccountId(account.bankId, account.accountId))
//          otherBankAccount <- account.moderatedOtherBankAccount(other_account_id, view, user)
//          metadata <- Box(otherBankAccount.metadata) ?~ {"the view " + viewId + "does not allow metadata access"}
//          addCorpLocation <- Box(metadata.addCorporateLocation) ?~ {"the view " + viewId + "does not allow updating a corporate location"}
//          corpLocationJson <- tryo{(json.extract[CorporateLocationJSON])} ?~ {"wrong JSON format"}
//          correctCoordinates <- checkIfLocationPossible(corpLocationJson.corporate_location.latitude, corpLocationJson.corporate_location.longitude)
//          updated <- Counterparties.counterparties.vend.addCorporateLocation(other_account_id, u.resourceUserId, (now:TimeSpan), corpLocationJson.corporate_location.longitude, corpLocationJson.corporate_location.latitude) ?~ {"Corporate Location cannot be updated"}
//          if(updated)
//        } yield {
//            val successJson = SuccessMessage("corporate location updated")
//            successJsonResponse(Extraction.decompose(successJson))
//        }
//    }
//  })
//
//  oauthServe(apiPrefix{
//  //delete corporate location of other bank account
//    case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "other_accounts":: other_account_id :: "corporate_location" :: Nil JsonDelete _ => {
//      cc =>
//        for {
//          u <- user
//          account <- BankAccount(bankId, accountId)
//          view <- Views.views.vend.view(viewId, BankIdAccountId(account.bankId, account.accountId))
//          otherBankAccount <- account.moderatedOtherBankAccount(other_account_id, view, user)
//          metadata <- Box(otherBankAccount.metadata) ?~ {"the view " + viewId + "does not allow metadata access"}
//          deleted <- Counterparties.counterparties.vend.deleteCorporateLocation(other_account_id) ?~ {"Corporate Location cannot be deleted"}
//        } yield {
//          if(deleted)
//            noContentJsonResponse
//          else
//            errorJsonResponse("Delete not completed")
//        }
//    }
//  })
//
//def checkIfLocationPossible(lat:Double,lon:Double) : Box[Unit] = {
//  if(scala.math.abs(lat) <= 90 & scala.math.abs(lon) <= 180)
//    Full()
//  else
//    Failure("Coordinates not possible")
//}
//
//  oauthServe(apiPrefix{
//  //add physical location to other bank account
//    case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "other_accounts" :: other_account_id :: "physical_location" :: Nil JsonPost json -> _ => {
//      cc =>
//        for {
//          u <- user
//          account <- BankAccount(bankId, accountId)
//          view <- Views.views.vend.view(viewId, BankIdAccountId(account.bankId, account.accountId))
//          otherBankAccount <- account.moderatedOtherBankAccount(other_account_id, view, user)
//          metadata <- Box(otherBankAccount.metadata) ?~ {"the view " + viewId + "does not allow metadata access"}
//          addPhysicalLocation <- Box(metadata.addPhysicalLocation) ?~ {"the view " + viewId + "does not allow adding a physical location"}
//          physicalLocationJson <- tryo{(json.extract[PhysicalLocationJSON])} ?~ {"wrong JSON format"}
//          correctCoordinates <- checkIfLocationPossible(physicalLocationJson.physical_location.latitude, physicalLocationJson.physical_location.longitude)
//          added <- Counterparties.counterparties.vend.addPhysicalLocation(other_account_id, u.resourceUserId, (now:TimeSpan), physicalLocationJson.physical_location.longitude, physicalLocationJson.physical_location.latitude) ?~ {"Physical Location cannot be added"}
//          if(added)
//        } yield {
//            val successJson = SuccessMessage("physical location added")
//            successJsonResponse(Extraction.decompose(successJson), 201)
//        }
//    }
//  })
//
//  oauthServe(apiPrefix{
//  //update physical location to other bank account
//    case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "other_accounts":: other_account_id :: "physical_location" :: Nil JsonPut json -> _ => {
//      cc =>
//        for {
//          u <- user
//          account <- BankAccount(bankId, accountId)
//          view <- Views.views.vend.view(viewId, BankIdAccountId(account.bankId, account.accountId))
//          otherBankAccount <- account.moderatedOtherBankAccount(other_account_id, view, user)
//          metadata <- Box(otherBankAccount.metadata) ?~ {"the view " + viewId + "does not allow metadata access"}
//          addPhysicalLocation <- Box(metadata.addPhysicalLocation) ?~ {"the view " + viewId + "does not allow updating a physical location"}
//          physicalLocationJson <- tryo{(json.extract[PhysicalLocationJSON])} ?~ {"wrong JSON format"}
//          correctCoordinates <- checkIfLocationPossible(physicalLocationJson.physical_location.latitude, physicalLocationJson.physical_location.longitude)
//          updated <- Counterparties.counterparties.vend.addPhysicalLocation(other_account_id, u.resourceUserId, (now:TimeSpan), physicalLocationJson.physical_location.longitude, physicalLocationJson.physical_location.latitude) ?~ {"Physical Location cannot be updated"}
//          if(updated)
//        } yield {
//            val successJson = SuccessMessage("physical location updated")
//            successJsonResponse(Extraction.decompose(successJson))
//        }
//    }
//  })
//
//  oauthServe(apiPrefix{
//  //delete physical location of other bank account
//    case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "other_accounts":: other_account_id :: "physical_location" :: Nil JsonDelete _ => {
//      cc =>
//        for {
//          u <- user
//          account <- BankAccount(bankId, accountId)
//          view <- Views.views.vend.view(viewId, BankIdAccountId(account.bankId, account.accountId))
//          otherBankAccount <- account.moderatedOtherBankAccount(other_account_id, view, user)
//          metadata <- Box(otherBankAccount.metadata) ?~ {"the view " + viewId + "does not allow metadata access"}
//          deleted <- Counterparties.counterparties.vend.deletePhysicalLocation(other_account_id) ?~ {"Physical Location cannot be deleted"}
//        } yield {
//            if(deleted)
//              noContentJsonResponse
//            else
//              errorJsonResponse("Delete not completed")
//        }
//    }
//  })
//
//  oauthServe(apiPrefix {
//  //get transactions
//    case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "transactions" :: Nil JsonGet req => {
//      cc =>
//      import code.api.util.APIUtil.getTransactionParams
//
//      for {
//        params <- getTransactionParams(json)
//        bankAccount <- BankAccount(bankId, accountId)
//        view <- Views.views.vend.view(viewId, bankAccount)
//        transactions <- bankAccount.getModeratedTransactions(user, view, params : _*)
//      } yield {
//        val json = JSONFactory.createTransactionsJSON(transactions)
//        successJsonResponse(Extraction.decompose(json))
//      }
//    }
//  })
//
//  oauthServe(apiPrefix {
//  //get transaction by id
//    case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "transactions" :: TransactionId(transactionId) :: "transaction" :: Nil JsonGet req => {
//      cc =>
//        for {
//          account <- BankAccount(bankId, accountId)
//          view <- Views.views.vend.view(viewId, BankIdAccountId(account.bankId, account.accountId))
//          moderatedTransaction <- account.moderatedTransaction(transactionId, view, user)
//        } yield {
//            val json = JSONFactory.createTransactionJSON(moderatedTransaction)
//            successJsonResponse(Extraction.decompose(json))
//          }
//    }
//  })
//
//  oauthServe(apiPrefix {
//  //get narrative
//    case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "transactions" :: TransactionId(transactionId) :: "metadata" :: "narrative" :: Nil JsonGet req => {
//      cc =>
//        for {
//          metadata <- moderatedTransactionMetadata(bankId, accountId, viewId, transactionId, user)
//          narrative <- Box(metadata.ownerComment) ?~ { "view " + viewId + " does not authorize narrative access" }
//        } yield {
//          val narrativeJson = JSONFactory.createTransactionNarrativeJSON(narrative)
//          successJsonResponse(Extraction.decompose(narrativeJson))
//        }
//    }
//  })
//
//  oauthServe(apiPrefix {
//  //add narrative
//    case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "transactions" :: TransactionId(transactionId) :: "metadata" :: "narrative" :: Nil JsonPost json -> _ => {
//      cc =>
//        for {
//          narrativeJson <- tryo{json.extract[TransactionNarrativeJSON]} ?~ {"wrong json format"}
//          u <- user
//          metadata <- moderatedTransactionMetadata(bankId, accountId, viewId, transactionId, Full(u))
//          addNarrative <- Box(metadata.addOwnerComment) ?~ {"view " + viewId + " does not allow adding a narrative"}
//        } yield {
//          addNarrative(narrativeJson.narrative)
//          val successJson = SuccessMessage("narrative added")
//          successJsonResponse(Extraction.decompose(successJson), 201)
//        }
//    }
//  })
//
//  oauthServe(apiPrefix {
//  //update narrative
//    case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "transactions" :: TransactionId(transactionId) :: "metadata" :: "narrative" :: Nil JsonPut json -> _ => {
//      cc =>
//        for {
//          narrativeJson <- tryo{json.extract[TransactionNarrativeJSON]} ?~ {"wrong json format"}
//          u <- user
//          metadata <- moderatedTransactionMetadata(bankId, accountId, viewId, transactionId, Full(u))
//          addNarrative <- Box(metadata.addOwnerComment) ?~ {"view " + viewId + " does not allow updating a narrative"}
//        } yield {
//          addNarrative(narrativeJson.narrative)
//          val successJson = SuccessMessage("narrative updated")
//          successJsonResponse(Extraction.decompose(successJson))
//        }
//    }
//  })
//
//  oauthServe(apiPrefix {
//  //delete narrative
//    case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "transactions" :: TransactionId(transactionId) :: "metadata" :: "narrative" :: Nil JsonDelete _ => {
//      cc =>
//        for {
//          metadata <- moderatedTransactionMetadata(bankId, accountId, viewId, transactionId, user)
//          addNarrative <- Box(metadata.addOwnerComment) ?~ {"view " + viewId + " does not allow deleting the narrative"}
//        } yield {
//          addNarrative("")
//          noContentJsonResponse
//        }
//    }
//  })
//
//  oauthServe(apiPrefix {
//  //get comments
//    case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "transactions" :: TransactionId(transactionId) :: "metadata" :: "comments" :: Nil JsonGet req => {
//      cc =>
//        for {
//          metadata <- moderatedTransactionMetadata(bankId, accountId, viewId, transactionId, user)
//          comments <- Box(metadata.comments) ?~ { "view " + viewId + " does not authorize comments access" }
//        } yield {
//          val json = JSONFactory.createTransactionCommentsJSON(comments)
//          successJsonResponse(Extraction.decompose(json))
//        }
//    }
//  })
//
//  oauthServe(apiPrefix {
//  //add comment
//    case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "transactions" :: TransactionId(transactionId) :: "metadata" :: "comments" :: Nil JsonPost json -> _ => {
//      cc =>
//        for {
//          commentJson <- tryo{json.extract[PostTransactionCommentJSON]} ?~ {"wrong json format"}
//          u <- user
//          metadata <- moderatedTransactionMetadata(bankId, accountId, viewId, transactionId, Full(u))
//          addCommentFunc <- Box(metadata.addComment) ?~ {"view " + viewId + " does not authorize adding comments"}
//          postedComment <- addCommentFunc(u.resourceUserId, viewId, commentJson.value, now)
//        } yield {
//          successJsonResponse(Extraction.decompose(JSONFactory.createTransactionCommentJSON(postedComment)),201)
//        }
//    }
//  })
//
//  oauthServe(apiPrefix {
//  //delete comment
//    case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "transactions" :: TransactionId(transactionId) :: "metadata" :: "comments":: commentId :: Nil JsonDelete _ => {
//      cc =>
//        for {
//          account <- BankAccount(bankId, accountId)
//          metadata <- moderatedTransactionMetadata(bankId, accountId, viewId, transactionId, user)
//          delete <- metadata.deleteComment(commentId, user, account)
//        } yield {
//          noContentJsonResponse
//        }
//    }
//  })
//
//  oauthServe(apiPrefix {
//  //get tags
//    case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "transactions" :: TransactionId(transactionId) :: "metadata" :: "tags" :: Nil JsonGet req => {
//      cc =>
//        for {
//          metadata <- moderatedTransactionMetadata(bankId, accountId, viewId, transactionId, user)
//          tags <- Box(metadata.tags) ?~ { "view " + viewId + " does not authorize tag access" }
//        } yield {
//          val json = JSONFactory.createTransactionTagsJSON(tags)
//          successJsonResponse(Extraction.decompose(json))
//        }
//    }
//  })
//
//  oauthServe(apiPrefix {
//    //add a tag
//    case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "transactions" :: TransactionId(transactionId) :: "metadata" :: "tags" :: Nil JsonPost json -> _ => {
//
//      cc =>
//        for {
//          tagJson <- tryo{json.extract[PostTransactionTagJSON]}
//          u <- user
//          metadata <- moderatedTransactionMetadata(bankId, accountId, viewId, transactionId, Full(u))
//          addTagFunc <- Box(metadata.addTag) ?~ {"view " + viewId + " does not authorize adding tags"}
//          postedTag <- addTagFunc(u.resourceUserId, viewId, tagJson.value, now)
//        } yield {
//          successJsonResponse(Extraction.decompose(JSONFactory.createTransactionTagJSON(postedTag)), 201)
//        }
//    }
//  })
//
//  oauthServe(apiPrefix {
//    //delete a tag
//    case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "transactions" :: TransactionId(transactionId) :: "metadata" :: "tags" :: tagId :: Nil JsonDelete _ => {
//
//      cc =>
//        for {
//          metadata <- moderatedTransactionMetadata(bankId, accountId, viewId, transactionId, user)
//          bankAccount <- BankAccount(bankId, accountId)
//          deleted <- metadata.deleteTag(tagId, user, bankAccount)
//        } yield {
//          noContentJsonResponse
//        }
//    }
//  })
//
//  oauthServe(apiPrefix {
//  //get images
//    case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "transactions" :: TransactionId(transactionId) :: "metadata" :: "images" :: Nil JsonGet req => {
//      cc =>
//        for {
//          metadata <- moderatedTransactionMetadata(bankId, accountId, viewId, transactionId, user)
//          images <- Box(metadata.images) ?~ { "view " + viewId + " does not authorize images access" }
//        } yield {
//          val json = JSONFactory.createTransactionImagesJSON(images)
//          successJsonResponse(Extraction.decompose(json))
//        }
//    }
//  })
//
//  oauthServe(apiPrefix {
//  //add an image
//    case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "transactions" :: TransactionId(transactionId) :: "metadata" :: "images" :: Nil JsonPost json -> _ => {
//      cc =>
//        for {
//          imageJson <- tryo{json.extract[PostTransactionImageJSON]}
//          u <- user
//          metadata <- moderatedTransactionMetadata(bankId, accountId, viewId, transactionId, Full(u))
//          addImageFunc <- Box(metadata.addImage) ?~ {"view " + viewId + " does not authorize adding images"}
//          url <- tryo{new URL(imageJson.URL)} ?~! "Could not parse url string as a valid URL"
//          postedImage <- addImageFunc(u.resourceUserId, viewId, imageJson.label, now, url.toString)
//        } yield {
//          successJsonResponse(Extraction.decompose(JSONFactory.createTransactionImageJSON(postedImage)),201)
//        }
//    }
//  })
//
//  oauthServe(apiPrefix {
//  //delete an image
//    case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "transactions" :: TransactionId(transactionId) :: "metadata" :: "images" :: imageId :: Nil JsonDelete _ => {
//      cc =>
//        for {
//          metadata <- moderatedTransactionMetadata(bankId, accountId, viewId, transactionId, user)
//          bankAccount <- BankAccount(bankId, accountId)
//          deleted <- Box(metadata.deleteImage(imageId, user, bankAccount))
//        } yield {
//          noContentJsonResponse
//        }
//    }
//  })
//
//  oauthServe(apiPrefix {
//  //get where tag
//    case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "transactions" :: TransactionId(transactionId) :: "metadata" :: "where" :: Nil JsonGet req => {
//      cc =>
//        for {
//          metadata <- moderatedTransactionMetadata(bankId, accountId, viewId, transactionId, user)
//          where <- Box(metadata.whereTag) ?~ { "view " + viewId + " does not authorize where tag access" }
//        } yield {
//          val json = JSONFactory.createLocationJSON(where)
//          val whereJson = TransactionWhereJSON(json)
//          successJsonResponse(Extraction.decompose(whereJson))
//        }
//    }
//  })
//
//  oauthServe(apiPrefix{
//  //add where tag
//    case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "transactions" :: TransactionId(transactionId) :: "metadata" :: "where" :: Nil JsonPost json -> _ => {
//      cc =>
//        for {
//          u <- user
//          metadata <- moderatedTransactionMetadata(bankId, accountId, viewId, transactionId, user)
//          addWhereTag <- Box(metadata.addWhereTag) ?~ {"the view " + viewId + "does not allow adding a where tag"}
//          whereJson <- tryo{(json.extract[PostTransactionWhereJSON])} ?~ {"wrong JSON format"}
//          correctCoordinates <- checkIfLocationPossible(whereJson.where.latitude, whereJson.where.longitude)
//          if(addWhereTag(u.resourceUserId, viewId, now, whereJson.where.longitude, whereJson.where.latitude))
//        } yield {
//            val successJson = SuccessMessage("where tag added")
//            successJsonResponse(Extraction.decompose(successJson), 201)
//        }
//    }
//  })
//
//  oauthServe(apiPrefix{
//  //update where tag
//    case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "transactions" :: TransactionId(transactionId) :: "metadata" :: "where" :: Nil JsonPut json -> _ => {
//      cc =>
//        for {
//          u <- user
//          metadata <- moderatedTransactionMetadata(bankId, accountId, viewId, transactionId, user)
//          addWhereTag <- Box(metadata.addWhereTag) ?~ {"the view " + viewId + "does not allow updating a where tag"}
//          whereJson <- tryo{(json.extract[PostTransactionWhereJSON])} ?~ {"wrong JSON format"}
//          correctCoordinates <- checkIfLocationPossible(whereJson.where.latitude, whereJson.where.longitude)
//         if(addWhereTag(u.resourceUserId, viewId, now, whereJson.where.longitude, whereJson.where.latitude))
//        } yield {
//            val successJson = SuccessMessage("where tag updated")
//            successJsonResponse(Extraction.decompose(successJson))
//        }
//    }
//  })
//
//  oauthServe(apiPrefix{
//  //delete where tag
//    case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "transactions" :: TransactionId(transactionId) :: "metadata" :: "where" :: Nil JsonDelete _ => {
//      cc =>
//        for {
//          bankAccount <- BankAccount(bankId, accountId)
//          metadata <- moderatedTransactionMetadata(bankId, accountId, viewId, transactionId, user)
//          deleted <- metadata.deleteWhereTag(viewId, user, bankAccount)
//        } yield {
//            if(deleted)
//              noContentJsonResponse
//            else
//              errorJsonResponse("Delete not completed")
//        }
//    }
//  })
//
//  oauthServe(apiPrefix{
//  //get other account of a transaction
//    case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "transactions":: TransactionId(transactionId) :: "other_account" :: Nil JsonGet req => {
//      cc =>
//        for {
//          account <- BankAccount(bankId, accountId)
//          view <- Views.views.vend.view(viewId, BankIdAccountId(account.bankId, account.accountId))
//          transaction <- account.moderatedTransaction(transactionId, view, user)
//          moderatedOtherBankAccount <- transaction.otherBankAccount
//        } yield {
//          val otherBankAccountJson = JSONFactory.createOtherBankAccount(moderatedOtherBankAccount)
//          successJsonResponse(Extraction.decompose(otherBankAccountJson))
//        }
//
//    }
//  })
//}