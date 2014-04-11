/**
Open Bank Project - API
Copyright (C) 2011, 2013, TESOBE / Music Pictures Ltd

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
TESOBE / Music Pictures Ltd
Osloerstrasse 16/17
Berlin 13359, Germany

  This product includes software developed at
  TESOBE (http://www.tesobe.com/)
  by
  Simon Redfern : simon AT tesobe DOT com
  Stefan Bethge : stefan AT tesobe DOT com
  Everett Sochowski : everett AT tesobe DOT com
  Ayoub Benali: ayoub AT tesobe DOT com

 */
package code.api.v1_2_1

import net.liftweb.http.JsonResponse
import net.liftweb.http.rest._
import net.liftweb.json.JsonDSL._
import net.liftweb.json.Printer._
import net.liftweb.json.Extraction
import net.liftweb.json.JsonAST._
import net.liftweb.common.{Failure,Full,Empty, Box, Loggable}
import net.liftweb.mongodb._
import com.mongodb.casbah.Imports._
import _root_.java.math.MathContext
import org.bson.types._
import _root_.net.liftweb.util._
import _root_.net.liftweb.mapper._
import _root_.net.liftweb.util.Helpers._
import _root_.scala.xml._
import _root_.net.liftweb.http.S._
import net.liftweb.mongodb.{ Skip, Limit }
import _root_.net.liftweb.mapper.view._
import com.mongodb._
import java.util.Date
import code.api.OAuthHandshake._
import code.model.dataAccess.OBPEnvelope.{OBPOrder, OBPLimit, OBPOffset, OBPOrdering, OBPFromDate, OBPToDate, OBPQueryParam}
import code.model._
import java.net.URL
import code.util.APIUtil._
import code.api.OBPRestHelper


object OBPAPI1_2_1 extends OBPRestHelper with Loggable {

  implicit def errorToJson(error: ErrorMessage): JValue = Extraction.decompose(error)
  implicit def successToJson(success: SuccessMessage): JValue = Extraction.decompose(success)

  
  val dateFormat = ModeratedTransaction.dateFormat
  val apiPrefix = "obp" / "v1.2.1" oPrefix _

  private def bankAccountsListToJson(bankAccounts: List[BankAccount], user : Box[User]): JValue = {
    val accJson : List[AccountJSON] = bankAccounts.map( account => {
        val views = account permittedViews user
        val viewsAvailable : List[ViewJSON] =
            views.map( v => {
              JSONFactory.createViewJSON(v)
            })
        JSONFactory.createAccountJSON(account,viewsAvailable)
      })

    val accounts = new AccountsJSON(accJson)
    Extraction.decompose(accounts)
  }

  private def booleanToBox(statement: Boolean, msg: String): Box[Unit] = {
    if(statement)
      Full()
    else
      Failure(msg)
  }


  private def moderatedTransactionMetadata(bankId : String, accountId : String, viewId : String, transactionID : String, user : Box[User]) : Box[ModeratedTransactionMetadata] ={
    for {
      account <- BankAccount(bankId, accountId)
      view <- View.fromUrl(viewId, account)
      moderatedTransaction <- account.moderatedTransaction(transactionID, view, user)
      metadata <- Box(moderatedTransaction.metadata) ?~ {"view " + viewId + " does not authorize metadata access"}
    } yield metadata
  }

  oauthServe(apiPrefix {
    case Nil JsonGet json => {
      user =>
        val apiDetails: JValue = {
          val hostedBy = new HostedBy("TESOBE", "contact@tesobe.com", "+49 (0)30 8145 3994")
          val apiInfoJSON = new APIInfoJSON("1.2.1", gitCommit, hostedBy)
          Extraction.decompose(apiInfoJSON)
        }

        Full(successJsonResponse(apiDetails, 200))
    }
  })

  oauthServe(apiPrefix {
  //get banks
    case "banks" :: Nil JsonGet json => {
      user =>
        def banksToJson(banksList: List[Bank]): JValue = {
          val banksJSON: List[BankJSON] = banksList.map(b => {
            JSONFactory.createBankJSON(b)
          })
          val banks = new BanksJSON(banksJSON)
          Extraction.decompose(banks)
        }

        Full(successJsonResponse(banksToJson(Bank.all)))
    }
  })

  oauthServe(apiPrefix{
  //get bank by id
    case "banks" :: bankId :: Nil JsonGet json => {
      user =>
        def bankToJson(bank : Bank) : JValue = {
          val bankJSON = JSONFactory.createBankJSON(bank)
          Extraction.decompose(bankJSON)
        }
        for(bank <- Bank(bankId))
          yield successJsonResponse(bankToJson(bank))
    }
  })

  oauthServe(apiPrefix {
  //get accounts
    case "banks" :: bankId :: "accounts" :: Nil JsonGet json => {
      user =>
        for{
         bank <- Bank(bankId)
         availableAccounts <- bank.accounts(user)
        } yield successJsonResponse(bankAccountsListToJson(availableAccounts, user))
    }
  })

  oauthServe(apiPrefix {
  //get private accounts
    case "banks" :: bankId :: "accounts" :: "private" :: Nil JsonGet json => {
      user =>
        for {
          u <- user ?~ "user not found"
          bank <- Bank(bankId)
          availableAccounts <- bank.nonPublicAccounts(u)
        } yield {
          successJsonResponse(bankAccountsListToJson(availableAccounts, Full(u)))
        }
    }
  })

  oauthServe(apiPrefix {
  //get public accounts
    case "banks" :: bankId :: "accounts" :: "public" :: Nil JsonGet json => {
      user =>
        for {
          bank <- Bank(bankId)
          availableAccounts <- bank.publicAccounts
        } yield {
          val publicAccountsJson = bankAccountsListToJson(availableAccounts, Empty)
          successJsonResponse(publicAccountsJson)
        }
    }
  })

  oauthServe(apiPrefix {
  //get account by id
    case "banks" :: bankId :: "accounts" :: accountId :: viewId :: "account" :: Nil JsonGet json => {
      user =>
        for {
          account <- BankAccount(bankId, accountId)
          availableviews <- Full(account.permittedViews(user))
          view <- View.fromUrl(viewId, account)
          moderatedAccount <- account.moderatedBankAccount(view, user)
        } yield {
            val viewsAvailable = availableviews.map(JSONFactory.createViewJSON)
            val moderatedAccountJson = JSONFactory.createBankAccountJSON(moderatedAccount, viewsAvailable)
            successJsonResponse(Extraction.decompose(moderatedAccountJson))
          }
    }
  })

  oauthServe(apiPrefix {
  //get the available views on an bank account
    case "banks" :: bankId :: "accounts" :: accountId :: "views" :: Nil JsonGet json => {
      user =>
        for {
          u <- user ?~ "user not found"
          account <- BankAccount(bankId, accountId)
          views <- account views u
        } yield {
            val viewsJSON = JSONFactory.createViewsJSON(views)
            successJsonResponse(Extraction.decompose(viewsJSON))
          }
    }
  })

  oauthServe(apiPrefix {
  //creates a view on an bank account
    case "banks" :: bankId :: "accounts" :: accountId :: "views" :: Nil JsonPost json -> _ => {
      user =>
        for {
          u <- user ?~ "user not found"
          json <- tryo{json.extract[ViewCreationJSON]} ?~ "wrong JSON format"
          account <- BankAccount(bankId, accountId)
          view <- account createView (u, json)
        } yield {
            val viewJSON = JSONFactory.createViewJSON(view)
            successJsonResponse(Extraction.decompose(viewJSON), 201)
          }
    }
  })

  oauthServe(apiPrefix {
    //updates a view on a bank account
    case "banks" :: bankId :: "accounts" :: accountId :: "views" :: viewId :: Nil JsonPut json -> _ => {
      user =>
        for {
          account <- BankAccount(bankId, accountId)
          u <- user ?~ "user not found"
          updateJson <- tryo{json.extract[ViewUpdateData]} ?~ "wrong JSON format"
          updatedView <- account.updateView(u, viewId, updateJson)
        } yield {
          val viewJSON = JSONFactory.createViewJSON(updatedView)
          successJsonResponse(Extraction.decompose(viewJSON), 200)
        }
    }
  })

  oauthServe(apiPrefix {
    //deletes a view on an bank account
    case "banks" :: bankId :: "accounts" :: accountId :: "views" :: viewId :: Nil JsonDelete json => {
      user =>
        for {
          u <- user ?~ "user not found"
          account <- BankAccount(bankId, accountId)
          view <- account removeView (u, viewId)
        } yield noContentJsonResponse
    }
  })

  oauthServe(apiPrefix {
  //get access
    case "banks" :: bankId :: "accounts" :: accountId :: "permissions" :: Nil JsonGet json => {
      user =>
        for {
          u <- user ?~ "user not found"
          account <- BankAccount(bankId, accountId)
          permissions <- account permissions u
        } yield {
            val permissionsJSON = JSONFactory.createPermissionsJSON(permissions)
            successJsonResponse(Extraction.decompose(permissionsJSON))
          }
    }
  })

  oauthServe(apiPrefix {
  //get access for specific user
    case "banks" :: bankId :: "accounts" :: accountId :: "permissions" :: providerId :: userId :: Nil JsonGet json => {
      user =>
        for {
          u <- user ?~ "user not found"
          account <- BankAccount(bankId, accountId)
          permission <- account permission(u, providerId, userId)
        } yield {
            val views = JSONFactory.createViewsJSON(permission.views)
            successJsonResponse(Extraction.decompose(views))
        }
    }
  })

  oauthServe(apiPrefix{
    //add access for specific user to a list of views
    case "banks" :: bankId :: "accounts" :: accountId :: "permissions" :: providerId :: userId :: "views" :: Nil JsonPost json -> _ => {
      user =>
        for {
          u <- user ?~ "user not found"
          account <- BankAccount(bankId, accountId)
          viewIds <- tryo{json.extract[ViewIdsJson]} ?~ "wrong format JSON"
          addedViews <- account addPermissions(u, viewIds.views, providerId, userId)
        } yield {
            val viewJson = JSONFactory.createViewsJSON(addedViews)
            successJsonResponse(Extraction.decompose(viewJson), 201)
          }
    }
  })

  oauthServe(apiPrefix{
  //add access for specific user to a specific view
    case "banks" :: bankId :: "accounts" :: accountId :: "permissions" :: providerId :: userId :: "views" :: viewId :: Nil JsonPost json -> _ => {
      user =>
        for {
          u <- user ?~ "user not found"
          account <- BankAccount(bankId, accountId)
          view <- View.fromUrl(viewId, account)
          isAdded <- account addPermission(u, viewId, providerId, userId)
          if(isAdded)
        } yield {
            val viewJson = JSONFactory.createViewJSON(view)
            successJsonResponse(Extraction.decompose(viewJson), 201)
          }
    }
  })

  oauthServe(apiPrefix{
  //delete access for specific user to one view
    case "banks" :: bankId :: "accounts" :: accountId :: "permissions" :: providerId :: userId :: "views" :: viewId :: Nil JsonDelete json => {
      user =>
        for {
          u <- user ?~ "user not found"
          account <- BankAccount(bankId, accountId)
          isRevoked <- account revokePermission(u, viewId, providerId, userId)
          if(isRevoked)
        } yield noContentJsonResponse
    }
  })

  oauthServe(apiPrefix{
    //delete access for specific user to all the views
    case "banks" :: bankId :: "accounts" :: accountId :: "permissions" :: providerId :: userId :: "views" :: Nil JsonDelete json => {
      user =>
        for {
          u <- user ?~ "user not found"
          account <- BankAccount(bankId, accountId)
          isRevoked <- account revokeAllPermissions(u, providerId, userId)
          if(isRevoked)
        } yield noContentJsonResponse
    }
  })

  oauthServe(apiPrefix{
  //get other accounts for one account
    case "banks" :: bankId :: "accounts" :: accountId :: viewId :: "other_accounts" :: Nil JsonGet json => {
      user =>
        for {
          account <- BankAccount(bankId, accountId)
          view <- View.fromUrl(viewId, account)
          otherBankAccounts <- account.moderatedOtherBankAccounts(view, user)
        } yield {
          val otherBankAccountsJson = JSONFactory.createOtherBankAccountsJSON(otherBankAccounts)
          successJsonResponse(Extraction.decompose(otherBankAccountsJson))
        }
    }
  })

  oauthServe(apiPrefix{
  //get one other account by id
    case "banks" :: bankId :: "accounts" :: accountId :: viewId :: "other_accounts":: other_account_id :: Nil JsonGet json => {
      user =>
        for {
          account <- BankAccount(bankId, accountId)
          view <- View.fromUrl(viewId, account)
          otherBankAccount <- account.moderatedOtherBankAccount(other_account_id, view, user)
        } yield {
          val otherBankAccountJson = JSONFactory.createOtherBankAccount(otherBankAccount)
          successJsonResponse(Extraction.decompose(otherBankAccountJson))
        }
    }
  })

  oauthServe(apiPrefix{
  //get metadata of one other account
    case "banks" :: bankId :: "accounts" :: accountId :: viewId :: "other_accounts":: other_account_id :: "metadata" :: Nil JsonGet json => {
      user =>
        for {
          account <- BankAccount(bankId, accountId)
          view <- View.fromUrl(viewId, account)
          otherBankAccount <- account.moderatedOtherBankAccount(other_account_id, view, user)
          metadata <- Box(otherBankAccount.metadata) ?~ {"the view " + viewId + "does not allow metadata access"}
        } yield {
          val metadataJson = JSONFactory.createOtherAccountMetaDataJSON(metadata)
          successJsonResponse(Extraction.decompose(metadataJson))
        }
    }
  })

  oauthServe(apiPrefix{
  //get public alias of other bank account
    case "banks" :: bankId :: "accounts" :: accountId :: viewId :: "other_accounts":: other_account_id :: "public_alias" :: Nil JsonGet json => {
      user =>
        for {
          account <- BankAccount(bankId, accountId)
          view <- View.fromUrl(viewId, account)
          otherBankAccount <- account.moderatedOtherBankAccount(other_account_id, view, user)
          metadata <- Box(otherBankAccount.metadata) ?~ {"the view " + viewId + "does not allow metadata access"}
          alias <- Box(metadata.publicAlias) ?~ {"the view " + viewId + "does not allow public alias access"}
        } yield {
          val aliasJson = JSONFactory.createAliasJSON(alias)
          successJsonResponse(Extraction.decompose(aliasJson))
        }
    }
  })

  oauthServe(apiPrefix{
  //add public alias to other bank account
    case "banks" :: bankId :: "accounts" :: accountId :: viewId :: "other_accounts":: other_account_id :: "public_alias" :: Nil JsonPost json -> _ => {
      user =>
        for {
          account <- BankAccount(bankId, accountId)
          view <- View.fromUrl(viewId, account)
          otherBankAccount <- account.moderatedOtherBankAccount(other_account_id, view, user)
          metadata <- Box(otherBankAccount.metadata) ?~ {"the view " + viewId + "does not allow metadata access"}
          addAlias <- Box(metadata.addPublicAlias) ?~ {"the view " + viewId + "does not allow adding a public alias"}
          aliasJson <- tryo{(json.extract[AliasJSON])} ?~ {"wrong JSON format"}
          if(addAlias(aliasJson.alias))
        } yield {
            successJsonResponse(Extraction.decompose(SuccessMessage("public alias added")), 201)
        }
    }
  })

  oauthServe(apiPrefix{
  //update public alias of other bank account
    case "banks" :: bankId :: "accounts" :: accountId :: viewId :: "other_accounts":: other_account_id :: "public_alias" :: Nil JsonPut json -> _ => {
      user =>
        for {
          account <- BankAccount(bankId, accountId)
          view <- View.fromUrl(viewId, account)
          otherBankAccount <- account.moderatedOtherBankAccount(other_account_id, view, user)
          metadata <- Box(otherBankAccount.metadata) ?~ {"the view " + viewId + "does not allow metadata access"}
          addAlias <- Box(metadata.addPublicAlias) ?~ {"the view " + viewId + "does not allow updating the public alias"}
          aliasJson <- tryo{(json.extract[AliasJSON])} ?~ {"wrong JSON format"}
          if(addAlias(aliasJson.alias))
        } yield {
            successJsonResponse(Extraction.decompose(SuccessMessage("public alias updated")))
        }
    }
  })

  oauthServe(apiPrefix{
  //delete public alias of other bank account
    case "banks" :: bankId :: "accounts" :: accountId :: viewId :: "other_accounts":: other_account_id :: "public_alias" :: Nil JsonDelete _ => {
      user =>
        for {
          account <- BankAccount(bankId, accountId)
          view <- View.fromUrl(viewId, account)
          otherBankAccount <- account.moderatedOtherBankAccount(other_account_id, view, user)
          metadata <- Box(otherBankAccount.metadata) ?~ {"the view " + viewId + "does not allow metadata access"}
          addAlias <- Box(metadata.addPublicAlias) ?~ {"the view " + viewId + "does not allow deleting the public alias"}
          if(addAlias(""))
        } yield noContentJsonResponse
    }
  })


  oauthServe(apiPrefix{
  //get private alias of other bank account
    case "banks" :: bankId :: "accounts" :: accountId :: viewId :: "other_accounts":: other_account_id :: "private_alias" :: Nil JsonGet json => {
      user =>
        for {
          account <- BankAccount(bankId, accountId)
          view <- View.fromUrl(viewId, account)
          otherBankAccount <- account.moderatedOtherBankAccount(other_account_id, view, user)
          metadata <- Box(otherBankAccount.metadata) ?~ {"the view " + viewId + "does not allow metadata access"}
          alias <- Box(metadata.privateAlias) ?~ {"the view " + viewId + "does not allow private alias access"}
        } yield {
          val aliasJson = JSONFactory.createAliasJSON(alias)
          successJsonResponse(Extraction.decompose(aliasJson))
        }
    }
  })

  oauthServe(apiPrefix{
  //add private alias to other bank account
    case "banks" :: bankId :: "accounts" :: accountId :: viewId :: "other_accounts":: other_account_id :: "private_alias" :: Nil JsonPost json -> _ => {
      user =>
        for {
          account <- BankAccount(bankId, accountId)
          view <- View.fromUrl(viewId, account)
          otherBankAccount <- account.moderatedOtherBankAccount(other_account_id, view, user)
          metadata <- Box(otherBankAccount.metadata) ?~ {"the view " + viewId + "does not allow metadata access"}
          addAlias <- Box(metadata.addPrivateAlias) ?~ {"the view " + viewId + "does not allow adding a private alias"}
          aliasJson <- tryo{(json.extract[AliasJSON])} ?~ {"wrong JSON format"}
          if(addAlias(aliasJson.alias))
        } yield {
            val successJson = SuccessMessage("private alias added")
            successJsonResponse(Extraction.decompose(successJson), 201)
        }
    }
  })

  oauthServe(apiPrefix{
  //update private alias of other bank account
    case "banks" :: bankId :: "accounts" :: accountId :: viewId :: "other_accounts":: other_account_id :: "private_alias" :: Nil JsonPut json -> _ => {
      user =>
        for {
          account <- BankAccount(bankId, accountId)
          view <- View.fromUrl(viewId, account)
          otherBankAccount <- account.moderatedOtherBankAccount(other_account_id, view, user)
          metadata <- Box(otherBankAccount.metadata) ?~ {"the view " + viewId + "does not allow metadata access"}
          addAlias <- Box(metadata.addPrivateAlias) ?~ {"the view " + viewId + "does not allow updating the private alias"}
          aliasJson <- tryo{(json.extract[AliasJSON])} ?~ {"wrong JSON format"}
          if(addAlias(aliasJson.alias))
        } yield {
            val successJson = SuccessMessage("private alias updated")
            successJsonResponse(Extraction.decompose(successJson))
        }
    }
  })

  oauthServe(apiPrefix{
  //delete private alias of other bank account
    case "banks" :: bankId :: "accounts" :: accountId :: viewId :: "other_accounts":: other_account_id :: "private_alias" :: Nil JsonDelete _ => {
      user =>
        for {
          account <- BankAccount(bankId, accountId)
          view <- View.fromUrl(viewId, account)
          otherBankAccount <- account.moderatedOtherBankAccount(other_account_id, view, user)
          metadata <- Box(otherBankAccount.metadata) ?~ {"the view " + viewId + "does not allow metadata access"}
          addAlias <- Box(metadata.addPrivateAlias) ?~ {"the view " + viewId + "does not allow deleting the private alias"}
          if(addAlias(""))
        } yield noContentJsonResponse
    }
  })

  oauthServe(apiPrefix{
  //add more info to other bank account
    case "banks" :: bankId :: "accounts" :: accountId :: viewId :: "other_accounts":: other_account_id :: "more_info" :: Nil JsonPost json -> _ => {
      user =>
        for {
          account <- BankAccount(bankId, accountId)
          view <- View.fromUrl(viewId, account)
          otherBankAccount <- account.moderatedOtherBankAccount(other_account_id, view, user)
          metadata <- Box(otherBankAccount.metadata) ?~ {"the view " + viewId + "does not allow metadata access"}
          addMoreInfo <- Box(metadata.addMoreInfo) ?~ {"the view " + viewId + "does not allow adding more info"}
          moreInfoJson <- tryo{(json.extract[MoreInfoJSON])} ?~ {"wrong JSON format"}
          if(addMoreInfo(moreInfoJson.more_info))
        } yield {
            val successJson = SuccessMessage("more info added")
            successJsonResponse(Extraction.decompose(successJson), 201)
        }
    }
  })

  oauthServe(apiPrefix{
  //update more info of other bank account
    case "banks" :: bankId :: "accounts" :: accountId :: viewId :: "other_accounts":: other_account_id :: "more_info" :: Nil JsonPut json -> _ => {
      user =>
        for {
          account <- BankAccount(bankId, accountId)
          view <- View.fromUrl(viewId, account)
          otherBankAccount <- account.moderatedOtherBankAccount(other_account_id, view, user)
          metadata <- Box(otherBankAccount.metadata) ?~ {"the view " + viewId + "does not allow metadata access"}
          addMoreInfo <- Box(metadata.addMoreInfo) ?~ {"the view " + viewId + "does not allow updating more info"}
          moreInfoJson <- tryo{(json.extract[MoreInfoJSON])} ?~ {"wrong JSON format"}
          if(addMoreInfo(moreInfoJson.more_info))
        } yield {
            val successJson = SuccessMessage("more info updated")
            successJsonResponse(Extraction.decompose(successJson))
        }
    }
  })

  oauthServe(apiPrefix{
  //delete more info of other bank account
    case "banks" :: bankId :: "accounts" :: accountId :: viewId :: "other_accounts":: other_account_id :: "more_info" :: Nil JsonDelete _ => {
      user =>
        for {
          account <- BankAccount(bankId, accountId)
          view <- View.fromUrl(viewId, account)
          otherBankAccount <- account.moderatedOtherBankAccount(other_account_id, view, user)
          metadata <- Box(otherBankAccount.metadata) ?~ {"the view " + viewId + "does not allow metadata access"}
          addMoreInfo <- Box(metadata.addMoreInfo) ?~ {"the view " + viewId + "does not allow deleting more info"}
          if(addMoreInfo(""))
        } yield noContentJsonResponse
    }
  })

  oauthServe(apiPrefix{
  //add url to other bank account
    case "banks" :: bankId :: "accounts" :: accountId :: viewId :: "other_accounts":: other_account_id :: "url" :: Nil JsonPost json -> _ => {
      user =>
        for {
          account <- BankAccount(bankId, accountId)
          view <- View.fromUrl(viewId, account)
          otherBankAccount <- account.moderatedOtherBankAccount(other_account_id, view, user)
          metadata <- Box(otherBankAccount.metadata) ?~ {"the view " + viewId + "does not allow metadata access"}
          addUrl <- Box(metadata.addURL) ?~ {"the view " + viewId + "does not allow adding a url"}
          urlJson <- tryo{(json.extract[UrlJSON])} ?~ {"wrong JSON format"}
          if(addUrl(urlJson.URL))
        } yield {
            val successJson = SuccessMessage("url added")
            successJsonResponse(Extraction.decompose(successJson), 201)
        }
    }
  })

  oauthServe(apiPrefix{
  //update url of other bank account
    case "banks" :: bankId :: "accounts" :: accountId :: viewId :: "other_accounts":: other_account_id :: "url" :: Nil JsonPut json -> _ => {
      user =>
        for {
          account <- BankAccount(bankId, accountId)
          view <- View.fromUrl(viewId, account)
          otherBankAccount <- account.moderatedOtherBankAccount(other_account_id, view, user)
          metadata <- Box(otherBankAccount.metadata) ?~ {"the view " + viewId + "does not allow metadata access"}
          addUrl <- Box(metadata.addURL) ?~ {"the view " + viewId + "does not allow updating a url"}
          urlJson <- tryo{(json.extract[UrlJSON])} ?~ {"wrong JSON format"}
          if(addUrl(urlJson.URL))
        } yield {
            val successJson = SuccessMessage("url updated")
            successJsonResponse(Extraction.decompose(successJson))
        }
    }
  })

  oauthServe(apiPrefix{
  //delete url of other bank account
    case "banks" :: bankId :: "accounts" :: accountId :: viewId :: "other_accounts":: other_account_id :: "url" :: Nil JsonDelete _ => {
      user =>
        for {
          account <- BankAccount(bankId, accountId)
          view <- View.fromUrl(viewId, account)
          otherBankAccount <- account.moderatedOtherBankAccount(other_account_id, view, user)
          metadata <- Box(otherBankAccount.metadata) ?~ {"the view " + viewId + "does not allow metadata access"}
          addUrl <- Box(metadata.addURL) ?~ {"the view " + viewId + "does not allow deleting a url"}
          if(addUrl(""))
        } yield noContentJsonResponse
    }
  })

  oauthServe(apiPrefix{
  //add image url to other bank account
    case "banks" :: bankId :: "accounts" :: accountId :: viewId :: "other_accounts":: other_account_id :: "image_url" :: Nil JsonPost json -> _ => {
      user =>
        for {
          account <- BankAccount(bankId, accountId)
          view <- View.fromUrl(viewId, account)
          otherBankAccount <- account.moderatedOtherBankAccount(other_account_id, view, user)
          metadata <- Box(otherBankAccount.metadata) ?~ {"the view " + viewId + "does not allow metadata access"}
          addImageUrl <- Box(metadata.addImageURL) ?~ {"the view " + viewId + "does not allow adding an image url"}
          imageUrlJson <- tryo{(json.extract[ImageUrlJSON])} ?~ {"wrong JSON format"}
          if(addImageUrl(imageUrlJson.image_URL))
        } yield {
            val successJson = SuccessMessage("image url added")
            successJsonResponse(Extraction.decompose(successJson), 201)
        }
    }
  })

  oauthServe(apiPrefix{
  //update image url of other bank account
    case "banks" :: bankId :: "accounts" :: accountId :: viewId :: "other_accounts":: other_account_id :: "image_url" :: Nil JsonPut json -> _ => {
      user =>
        for {
          account <- BankAccount(bankId, accountId)
          view <- View.fromUrl(viewId, account)
          otherBankAccount <- account.moderatedOtherBankAccount(other_account_id, view, user)
          metadata <- Box(otherBankAccount.metadata) ?~ {"the view " + viewId + "does not allow metadata access"}
          addImageUrl <- Box(metadata.addImageURL) ?~ {"the view " + viewId + "does not allow updating an image url"}
          imageUrlJson <- tryo{(json.extract[ImageUrlJSON])} ?~ {"wrong JSON format"}
          if(addImageUrl(imageUrlJson.image_URL))
        } yield {
            val successJson = SuccessMessage("image url updated")
            successJsonResponse(Extraction.decompose(successJson))
        }
    }
  })

  oauthServe(apiPrefix{
  //delete image url of other bank account
    case "banks" :: bankId :: "accounts" :: accountId :: viewId :: "other_accounts":: other_account_id :: "image_url" :: Nil JsonDelete _ => {
      user =>
        for {
          account <- BankAccount(bankId, accountId)
          view <- View.fromUrl(viewId, account)
          otherBankAccount <- account.moderatedOtherBankAccount(other_account_id, view, user)
          metadata <- Box(otherBankAccount.metadata) ?~ {"the view " + viewId + "does not allow metadata access"}
          addImageUrl <- Box(metadata.addImageURL) ?~ {"the view " + viewId + "does not allow deleting an image url"}
          if(addImageUrl(""))
        } yield noContentJsonResponse
    }
  })

  oauthServe(apiPrefix{
  //add open corporate url to other bank account
    case "banks" :: bankId :: "accounts" :: accountId :: viewId :: "other_accounts":: other_account_id :: "open_corporates_url" :: Nil JsonPost json -> _ => {
      user =>
        for {
          account <- BankAccount(bankId, accountId)
          view <- View.fromUrl(viewId, account)
          otherBankAccount <- account.moderatedOtherBankAccount(other_account_id, view, user)
          metadata <- Box(otherBankAccount.metadata) ?~ {"the view " + viewId + "does not allow metadata access"}
          addOpenCorpUrl <- Box(metadata.addOpenCorporatesURL) ?~ {"the view " + viewId + "does not allow adding an open corporate url"}
          opernCoprUrl <- tryo{(json.extract[OpenCorporateUrlJSON])} ?~ {"wrong JSON format"}
          if(addOpenCorpUrl(opernCoprUrl.open_corporates_URL))
        } yield {
            val successJson = SuccessMessage("open corporate url added")
            successJsonResponse(Extraction.decompose(successJson), 201)
        }
    }
  })

  oauthServe(apiPrefix{
  //update open corporate url of other bank account
    case "banks" :: bankId :: "accounts" :: accountId :: viewId :: "other_accounts":: other_account_id :: "open_corporates_url" :: Nil JsonPut json -> _ => {
      user =>
        for {
          account <- BankAccount(bankId, accountId)
          view <- View.fromUrl(viewId, account)
          otherBankAccount <- account.moderatedOtherBankAccount(other_account_id, view, user)
          metadata <- Box(otherBankAccount.metadata) ?~ {"the view " + viewId + "does not allow metadata access"}
          addOpenCorpUrl <- Box(metadata.addOpenCorporatesURL) ?~ {"the view " + viewId + "does not allow updating an open corporate url"}
          opernCoprUrl <- tryo{(json.extract[OpenCorporateUrlJSON])} ?~ {"wrong JSON format"}
          if(addOpenCorpUrl(opernCoprUrl.open_corporates_URL))
        } yield {
            val successJson = SuccessMessage("open corporate url updated")
            successJsonResponse(Extraction.decompose(successJson))
        }
    }
  })

  oauthServe(apiPrefix{
  //delete open corporate url of other bank account
    case "banks" :: bankId :: "accounts" :: accountId :: viewId :: "other_accounts":: other_account_id :: "open_corporates_url" :: Nil JsonDelete _ => {
      user =>
        for {
          account <- BankAccount(bankId, accountId)
          view <- View.fromUrl(viewId, account)
          otherBankAccount <- account.moderatedOtherBankAccount(other_account_id, view, user)
          metadata <- Box(otherBankAccount.metadata) ?~ {"the view " + viewId + "does not allow metadata access"}
          addOpenCorpUrl <- Box(metadata.addOpenCorporatesURL) ?~ {"the view " + viewId + "does not allow deleting an open corporate url"}
          if(addOpenCorpUrl(""))
        } yield noContentJsonResponse
    }
  })

  oauthServe(apiPrefix{
  //add corporate location to other bank account
    case "banks" :: bankId :: "accounts" :: accountId :: viewId :: "other_accounts" :: other_account_id :: "corporate_location" :: Nil JsonPost json -> _ => {
      user =>
        for {
          u <- user
          account <- BankAccount(bankId, accountId)
          view <- View.fromUrl(viewId, account)
          otherBankAccount <- account.moderatedOtherBankAccount(other_account_id, view, user)
          metadata <- Box(otherBankAccount.metadata) ?~ {"the view " + viewId + "does not allow metadata access"}
          addCorpLocation <- Box(metadata.addCorporateLocation) ?~ {"the view " + viewId + "does not allow adding a corporate location"}
          corpLocationJson <- tryo{(json.extract[CorporateLocationJSON])} ?~ {"wrong JSON format"}
          correctCoordinates <- checkIfLocationPossible(corpLocationJson.corporate_location.latitude, corpLocationJson.corporate_location.longitude)
          if(addCorpLocation(u.apiId, view.id, (now:TimeSpan), corpLocationJson.corporate_location.longitude, corpLocationJson.corporate_location.latitude))
        } yield {
            val successJson = SuccessMessage("corporate location added")
            successJsonResponse(Extraction.decompose(successJson), 201)
        }
    }
  })

  oauthServe(apiPrefix{
  //update corporate location of other bank account
    case "banks" :: bankId :: "accounts" :: accountId :: viewId :: "other_accounts":: other_account_id :: "corporate_location" :: Nil JsonPut json -> _ => {
      user =>
        for {
          u <- user
          account <- BankAccount(bankId, accountId)
          view <- View.fromUrl(viewId, account)
          otherBankAccount <- account.moderatedOtherBankAccount(other_account_id, view, user)
          metadata <- Box(otherBankAccount.metadata) ?~ {"the view " + viewId + "does not allow metadata access"}
          addCorpLocation <- Box(metadata.addCorporateLocation) ?~ {"the view " + viewId + "does not allow updating a corporate location"}
          corpLocationJson <- tryo{(json.extract[CorporateLocationJSON])} ?~ {"wrong JSON format"}
          correctCoordinates <- checkIfLocationPossible(corpLocationJson.corporate_location.latitude, corpLocationJson.corporate_location.longitude)
          if(addCorpLocation(u.apiId, view.id, now, corpLocationJson.corporate_location.longitude, corpLocationJson.corporate_location.latitude))
        } yield {
            val successJson = SuccessMessage("corporate location updated")
            successJsonResponse(Extraction.decompose(successJson))
        }
    }
  })

  oauthServe(apiPrefix{
  //delete corporate location of other bank account
    case "banks" :: bankId :: "accounts" :: accountId :: viewId :: "other_accounts":: other_account_id :: "corporate_location" :: Nil JsonDelete _ => {
      user =>
        for {
          u <- user
          account <- BankAccount(bankId, accountId)
          view <- View.fromUrl(viewId, account)
          otherBankAccount <- account.moderatedOtherBankAccount(other_account_id, view, user)
          metadata <- Box(otherBankAccount.metadata) ?~ {"the view " + viewId + "does not allow metadata access"}
          deleted <- Box(metadata.deleteCorporateLocation)
        } yield {
          if(deleted())
            noContentJsonResponse
          else
            errorJsonResponse("Delete not completed")
        }
    }
  })

def checkIfLocationPossible(lat:Double,lon:Double) : Box[Unit] = {
  if(scala.math.abs(lat) <= 90 & scala.math.abs(lon) <= 180)
    Full()
  else
    Failure("Coordinates not possible")
}

  oauthServe(apiPrefix{
  //add physical location to other bank account
    case "banks" :: bankId :: "accounts" :: accountId :: viewId :: "other_accounts" :: other_account_id :: "physical_location" :: Nil JsonPost json -> _ => {
      user =>
        for {
          u <- user
          account <- BankAccount(bankId, accountId)
          view <- View.fromUrl(viewId, account)
          otherBankAccount <- account.moderatedOtherBankAccount(other_account_id, view, user)
          metadata <- Box(otherBankAccount.metadata) ?~ {"the view " + viewId + "does not allow metadata access"}
          addPhysicalLocation <- Box(metadata.addPhysicalLocation) ?~ {"the view " + viewId + "does not allow adding a physical location"}
          physicalLocationJson <- tryo{(json.extract[PhysicalLocationJSON])} ?~ {"wrong JSON format"}
          correctCoordinates <- checkIfLocationPossible(physicalLocationJson.physical_location.latitude, physicalLocationJson.physical_location.longitude)
          if(addPhysicalLocation(u.apiId, view.id, now, physicalLocationJson.physical_location.longitude, physicalLocationJson.physical_location.latitude))
        } yield {
            val successJson = SuccessMessage("physical location added")
            successJsonResponse(Extraction.decompose(successJson), 201)
        }
    }
  })

  oauthServe(apiPrefix{
  //update physical location to other bank account
    case "banks" :: bankId :: "accounts" :: accountId :: viewId :: "other_accounts":: other_account_id :: "physical_location" :: Nil JsonPut json -> _ => {
      user =>
        for {
          u <- user
          account <- BankAccount(bankId, accountId)
          view <- View.fromUrl(viewId, account)
          otherBankAccount <- account.moderatedOtherBankAccount(other_account_id, view, user)
          metadata <- Box(otherBankAccount.metadata) ?~ {"the view " + viewId + "does not allow metadata access"}
          addPhysicalLocation <- Box(metadata.addPhysicalLocation) ?~ {"the view " + viewId + "does not allow updating a physical location"}
          physicalLocationJson <- tryo{(json.extract[PhysicalLocationJSON])} ?~ {"wrong JSON format"}
          correctCoordinates <- checkIfLocationPossible(physicalLocationJson.physical_location.latitude, physicalLocationJson.physical_location.longitude)
         if(addPhysicalLocation(u.apiId, view.id, now, physicalLocationJson.physical_location.longitude, physicalLocationJson.physical_location.latitude))
        } yield {
            val successJson = SuccessMessage("physical location updated")
            successJsonResponse(Extraction.decompose(successJson))
        }
    }
  })

  oauthServe(apiPrefix{
  //delete physical location of other bank account
    case "banks" :: bankId :: "accounts" :: accountId :: viewId :: "other_accounts":: other_account_id :: "physical_location" :: Nil JsonDelete _ => {
      user =>
        for {
          u <- user
          account <- BankAccount(bankId, accountId)
          view <- View.fromUrl(viewId, account)
          otherBankAccount <- account.moderatedOtherBankAccount(other_account_id, view, user)
          metadata <- Box(otherBankAccount.metadata) ?~ {"the view " + viewId + "does not allow metadata access"}
          deleted <- Box(metadata.deletePhysicalLocation)
        } yield {
            if(deleted())
              noContentJsonResponse
            else
              errorJsonResponse("Delete not completed")
        }
    }
  })
  
  import code.model.dataAccess.OBPEnvelope
  def createTransaction(account : BankAccount, otherBankId : String,
      otherAccountId : String, amount : String) : Box[OBPEnvelope] = {

    val oldFromBalance = account.balance
    
    import code.model.dataAccess.Account
    import code.model.dataAccess.HostedBank
    import java.text.SimpleDateFormat
    for {
      otherBank <- HostedBank.find("permalink" -> otherBankId) ?~! "no other bank found"
      //yeah dumb, but blame the bad mongodb structure that attempts to use foreign keys
      val otherAccs = Account.findAll(("permalink" -> otherAccountId))
      otherAcc <- Box(otherAccs.filter(_.bankPermalink == otherBank.permalink.get).headOption) ?~! s"no other acc found. ${otherAccs.size} searched for matching bank ${otherBank.id.get.toString} :: ${otherAccs.map(_.toString)}"
      amt <- tryo {BigDecimal(amount)} ?~! "amount not convertable to number"//TODO: this completely ignores currency
      val transTime = now
      val thisAccs = Account.findAll(("permalink" -> account.permalink))
      thisAcc <- Box(thisAccs.filter(_.bankPermalink == account.bankPermalink).headOption) ?~! s"no this acc found. ${thisAccs.size} searched for matching bank ${account.bankPermalink}?"
      //mongodb/the lift mongo thing wants a literal Z in the timestamp, apparently
      val envJsonDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
      val envJson =
        ("obp_transaction" ->
          ("this_account" ->
            ("holder" -> account.owners.headOption.map(_.name).getOrElse("")) ~ //TODO: this is rather fragile...
            ("number" -> account.number) ~
            ("kind" -> thisAcc.kind.get) ~
            ("bank" ->
              ("IBAN" -> account.iban.getOrElse("")) ~
              ("national_identifier" -> account.nationalIdentifier) ~
              ("name" -> account.bankPermalink))) ~
          ("other_account" ->
            ("holder" -> otherAcc.holder.get) ~
            ("number" -> otherAcc.number.get) ~
            ("kind" -> otherAcc.kind.get) ~
            ("bank" ->
              ("IBAN" -> "") ~
              ("national_identifier" -> otherBank.national_identifier.get) ~
              ("name" -> otherBank.name.get))) ~
          ("details" ->
            ("type_en" -> "") ~
            ("type_de" -> "") ~
            ("posted" ->
              ("$dt" -> envJsonDateFormat.format(transTime))
            ) ~
            ("completed" ->
              ("$dt" -> envJsonDateFormat.format(transTime))
            ) ~
            ("new_balance" ->
              ("currency" -> account.currency) ~
              ("amount" -> (oldFromBalance + amt).toString)) ~
            ("value" ->
              ("currency" -> account.currency) ~
              ("amount" -> amt.toString))))   
      saved <- saveTransaction(envJson)
    } yield saved
  }

  def saveTransaction(transactionJS : JValue) : Box[OBPEnvelope] = {
    import code.model.dataAccess.OBPEnvelope
    
    val envelope: Box[OBPEnvelope] = OBPEnvelope.envlopesFromJvalue(transactionJS)

    if(envelope.isDefined) {
      val e : OBPEnvelope = envelope.get
      val accountNumber = e.obp_transaction.get.this_account.get.number.get
      val bankName = e.obp_transaction.get.this_account.get.bank.get.name.get
      val accountKind = e.obp_transaction.get.this_account.get.kind.get
      val holder = e.obp_transaction.get.this_account.get.holder.get
      //Get all accounts with this account number and kind
      import code.model.dataAccess.Account
      val accounts = Account.findAll(("number" -> accountNumber) ~ ("kind" -> accountKind) ~ ("holder" -> holder))
      //Now get the one that actually belongs to the right bank
      val findFunc = (x : Account) => {
        x.bankPermalink == bankName
      }
      val wantedAccount = accounts.find(findFunc)
      wantedAccount match {
        case Some(account) => {
          def updateAccountBalance() = {
            logger.debug("Updating current balance for " + bankName + "/" + accountNumber + "/" + accountKind)
            account.balance(e.obp_transaction.get.details.get.new_balance.get.amount.get).save
            logger.debug("Saving new transaction")
            val metadataCreated = e.createMetadataReference
            if(metadataCreated.isDefined) e.save
            else Failure("Server error, problem creating transaction metadata")
          }
          account.lastUpdate(new Date)
          updateAccountBalance()
          Full(e)
        }
        case _ => Failure("couldn't save transaction: no account balance to update")
      }
    } else {
      Failure("couldn't save transaction")
    }
  }
  
  case class MakeTransactionJson(bank_id : String, account_id : String, amount : String)
  
  oauthServe(apiPrefix {
    
    //post transaction TODO: this is a work in progress/hackathon hack! most of this
    //code needs to be moved into a connector function
    case "banks" :: bankId :: "accounts" :: accountId :: viewId :: "transactions" :: Nil JsonPost json -> _ => {
      user =>

        if (Props.get("is_hackathon_mode", "") != "true") {
          Full(errorJsonResponse("nothing to see here!"))
        } else {
          for {
            u <- user ?~ "asdad"
            fromAccount <- BankAccount(bankId, accountId) ?~ s"account $accountId not found at bank $bankId"
            owner <- booleanToBox(u.ownerAccess(fromAccount), "user does not have access to owner view")
            view <- View.fromUrl(viewId, fromAccount) ?~ s"view $viewId not found"//TODO: this isn't actually used, unlike for GET transactions
            makeTransJson <- tryo{json.extract[MakeTransactionJson]} ?~ {"wrong json format"}
            toAccount <- {
              BankAccount(makeTransJson.bank_id, makeTransJson.account_id) ?~! {"Intended recipient with " +
                s" account id ${makeTransJson.account_id} at bank ${makeTransJson.bank_id}" +
                " not found"}
            }
            sameCurrency <- booleanToBox(fromAccount.currency == toAccount.currency, {
              s"Cannot send payment to account with different currency (From ${fromAccount.currency} to ${toAccount.currency}"
            })
            rawAmt <- tryo {BigDecimal(makeTransJson.amount)} ?~! s"amount ${makeTransJson.amount} not convertible to number"
            isPositiveAmtToSend <- booleanToBox(rawAmt > BigDecimal("0"), s"Can't send a payment with a value of 0 or less. (${makeTransJson.amount})")
          } yield {
            val fromTransAmt = -rawAmt //from account balance should decrease
            val toTransAmt = rawAmt //to account balance should increase
            val createdFromTrans = createTransaction(fromAccount, makeTransJson.bank_id, 
                                     makeTransJson.account_id, fromTransAmt.toString)
            // other party in the transaction for the "to" account is the "from" account
            val createToTrans = createTransaction(toAccount, bankId, accountId, toTransAmt.toString)
            
            /**
             * WARNING!!! There is no check currently being done that the new transaction + update balance 
             *  of the account receiving the money were properly saved. This payment implementation is for
             *  demo/sandbox/test purposes ONLY!
             *  
             *  I have not bothered to spend time doing anything about this. I see no point in trying to
             *  implement ACID transactions in mongodb when a real payment system will not use mongodb.
             */
            
            createdFromTrans match {
              case Full(c) => {
                val successJson : JValue = ("transaction_id" -> c.id.get.toString)
                successJsonResponse(successJson)
              }
              case Failure(msg, _, _) => errorJsonResponse(msg)
              case _ => errorJsonResponse(":(")
            }
          }
        }

    } 
    
  })
  
  
  oauthServe(apiPrefix {
  //get transactions
    case "banks" :: bankId :: "accounts" :: accountId :: viewId :: "transactions" :: Nil JsonGet json => {
      user =>

      def asInt(s: Box[String], default: Int): Int = {
        s match {
          case Full(str) => tryo { str.toInt } getOrElse default
          case _ => default
        }
      }

      val limit = asInt(json.header("obp_limit"), 50)
      val offset = asInt(json.header("obp_offset"), 0)

       /**
       * sortBy is currently disabled as it would open up a security hole:
       *
       * sortBy as currently implemented will take in a parameter that searches on the mongo field names. The issue here
       * is that it will sort on the true value, and not the moderated output. So if a view is supposed to return an alias name
       * rather than the true value, but someone uses sortBy on the other bank account name/holder, not only will the returned data
       * have the wrong order, but information about the true account holder name will be exposed due to its position in the sorted order
       *
       * This applies to all fields that can have their data concealed... which in theory will eventually be most/all
       *
       */
      //val sortBy = json.header("obp_sort_by")
      val sortBy = None
      val sortDirection = OBPOrder(json.header("obp_sort_by"))
      val fromDate = tryo{dateFormat.parse(json.header("obp_from_date") getOrElse "")}.map(OBPFromDate(_))
      val toDate = tryo{dateFormat.parse(json.header("obp_to_date") getOrElse "")}.map(OBPToDate(_))

      val basicParams =
        List(
          OBPLimit(limit),
          OBPOffset(offset),
          OBPOrdering(sortBy, sortDirection)
        )
      val params : List[OBPQueryParam] = fromDate.toList ::: toDate.toList ::: basicParams
      for {
        bankAccount <- BankAccount(bankId, accountId)
        view <- View.fromUrl(viewId, bankAccount)
        transactions <- bankAccount.getModeratedTransactions(user, view, params : _*)
      } yield {
        val json = JSONFactory.createTransactionsJSON(transactions)
        successJsonResponse(Extraction.decompose(json))
      }
    }
  })

  oauthServe(apiPrefix {
    //get transaction by id
    case "banks" :: bankId :: "accounts" :: accountId :: viewId :: "transactions" :: transactionId :: "transaction" :: Nil JsonGet json => {
      user =>
        for {
          account <- BankAccount(bankId, accountId) ?~! s"Bank account $accountId not found at bank $bankId"
          view <- View.fromUrl(viewId, account) ?~! s"View $viewId not found for account"
          moderatedTransaction <- account.moderatedTransaction(transactionId, view, user)
        } yield {
          val json = JSONFactory.createTransactionJSON(moderatedTransaction)
          successJsonResponse(Extraction.decompose(json))
        }
    }
  })

  oauthServe(apiPrefix {
  //get narrative
    case "banks" :: bankId :: "accounts" :: accountId :: viewId :: "transactions" :: transactionId :: "metadata" :: "narrative" :: Nil JsonGet json => {
      user =>
        for {
          metadata <- moderatedTransactionMetadata(bankId, accountId, viewId, transactionId, user)
          narrative <- Box(metadata.ownerComment) ?~ { "view " + viewId + " does not authorize narrative access" }
        } yield {
          val narrativeJson = JSONFactory.createTransactionNarrativeJSON(narrative)
          successJsonResponse(Extraction.decompose(narrativeJson))
        }
    }
  })

  oauthServe(apiPrefix {
  //add narrative
    case "banks" :: bankId :: "accounts" :: accountId :: viewId :: "transactions" :: transactionId :: "metadata" :: "narrative" :: Nil JsonPost json -> _ => {
      user =>
        for {
          u <- user
          narrativeJson <- tryo{json.extract[TransactionNarrativeJSON]} ?~ {"wrong json format"}
          view <- View.fromUrl(viewId, accountId, bankId)
          metadata <- moderatedTransactionMetadata(bankId, accountId, view.permalink, transactionId, Full(u))
          addNarrative <- Box(metadata.addOwnerComment) ?~ {"view " + viewId + " does not allow adding a narrative"}
        } yield {
          addNarrative(narrativeJson.narrative)
          val successJson = SuccessMessage("narrative added")
          successJsonResponse(Extraction.decompose(successJson), 201)
        }
    }
  })

  oauthServe(apiPrefix {
  //update narrative
    case "banks" :: bankId :: "accounts" :: accountId :: viewId :: "transactions" :: transactionId :: "metadata" :: "narrative" :: Nil JsonPut json -> _ => {
      user =>
        for {
          u <- user
          narrativeJson <- tryo{json.extract[TransactionNarrativeJSON]} ?~ {"wrong json format"}
          view <- View.fromUrl(viewId, accountId, bankId)
          metadata <- moderatedTransactionMetadata(bankId, accountId, view.permalink, transactionId, Full(u))
          addNarrative <- Box(metadata.addOwnerComment) ?~ {"view " + viewId + " does not allow updating a narrative"}
        } yield {
          addNarrative(narrativeJson.narrative)
          val successJson = SuccessMessage("narrative updated")
          successJsonResponse(Extraction.decompose(successJson))
        }
    }
  })

  oauthServe(apiPrefix {
  //delete narrative
    case "banks" :: bankId :: "accounts" :: accountId :: viewId :: "transactions" :: transactionId :: "metadata" :: "narrative" :: Nil JsonDelete _ => {
      user =>
        for {
          metadata <- moderatedTransactionMetadata(bankId, accountId, viewId, transactionId, user)
          addNarrative <- Box(metadata.addOwnerComment) ?~ {"view " + viewId + " does not allow deleting the narrative"}
        } yield {
          addNarrative("")
          noContentJsonResponse
        }
    }
  })

  oauthServe(apiPrefix {
  //get comments
    case "banks" :: bankId :: "accounts" :: accountId :: viewId :: "transactions" :: transactionId :: "metadata" :: "comments" :: Nil JsonGet json => {
      user =>
        for {
          metadata <- moderatedTransactionMetadata(bankId, accountId, viewId, transactionId, user)
          comments <- Box(metadata.comments) ?~ { "view " + viewId + " does not authorize comments access" }
        } yield {
          val json = JSONFactory.createTransactionCommentsJSON(comments)
          successJsonResponse(Extraction.decompose(json))
        }
    }
  })

  oauthServe(apiPrefix {
  //add comment
    case "banks" :: bankId :: "accounts" :: accountId :: viewId :: "transactions" :: transactionId :: "metadata" :: "comments" :: Nil JsonPost json -> _ => {
      user =>
        for {
          u <- user
          commentJson <- tryo{json.extract[PostTransactionCommentJSON]} ?~ {"wrong json format"}
          view <- View.fromUrl(viewId, accountId, bankId)
          metadata <- moderatedTransactionMetadata(bankId, accountId, view.permalink, transactionId, Full(u))
          addCommentFunc <- Box(metadata.addComment) ?~ {"view " + viewId + " does not authorize adding comments"}
          postedComment <- Full(addCommentFunc(u.apiId, view.id, commentJson.value, now))
        } yield {
          successJsonResponse(Extraction.decompose(JSONFactory.createTransactionCommentJSON(postedComment)),201)
        }
    }
  })

  oauthServe(apiPrefix {
  //delete comment
    case "banks" :: bankId :: "accounts" :: accountId :: viewId :: "transactions" :: transactionId :: "metadata" :: "comments":: commentId :: Nil JsonDelete _ => {
      user =>
        for {
          account <- BankAccount(bankId, accountId)
          metadata <- moderatedTransactionMetadata(bankId, accountId, viewId, transactionId, user)
          delete <- metadata.deleteComment(commentId, user, account)
        } yield {
          noContentJsonResponse
        }
    }
  })

  oauthServe(apiPrefix {
  //get tags
    case "banks" :: bankId :: "accounts" :: accountId :: viewId :: "transactions" :: transactionId :: "metadata" :: "tags" :: Nil JsonGet json => {
      user =>
        for {
          metadata <- moderatedTransactionMetadata(bankId, accountId, viewId, transactionId, user)
          tags <- Box(metadata.tags) ?~ { "view " + viewId + " does not authorize tag access" }
        } yield {
          val json = JSONFactory.createTransactionTagsJSON(tags)
          successJsonResponse(Extraction.decompose(json))
        }
    }
  })

  oauthServe(apiPrefix {
    //add a tag
    case "banks" :: bankId :: "accounts" :: accountId :: viewId :: "transactions" :: transactionID :: "metadata" :: "tags" :: Nil JsonPost json -> _ => {

      user =>
        for {
          u <- user
          tagJson <- tryo{json.extract[PostTransactionTagJSON]}
          view <- View.fromUrl(viewId, accountId, bankId)
          metadata <- moderatedTransactionMetadata(bankId, accountId, view.permalink, transactionID, Full(u))
          addTagFunc <- Box(metadata.addTag) ?~ {"view " + viewId + " does not authorize adding tags"}
          postedTag <- Full(addTagFunc(u.apiId, view.id, tagJson.value, now))
        } yield {
          successJsonResponse(Extraction.decompose(JSONFactory.createTransactionTagJSON(postedTag)), 201)
        }
    }
  })

  oauthServe(apiPrefix {
    //delete a tag
    case "banks" :: bankId :: "accounts" :: accountId :: viewId :: "transactions" :: transactionId :: "metadata" :: "tags" :: tagId :: Nil JsonDelete _ => {

      user =>
        for {
          metadata <- moderatedTransactionMetadata(bankId, accountId, viewId, transactionId, user)
          bankAccount <- BankAccount(bankId, accountId)
          deleted <- metadata.deleteTag(tagId, user, bankAccount)
        } yield {
          noContentJsonResponse
        }
    }
  })

  oauthServe(apiPrefix {
  //get images
    case "banks" :: bankId :: "accounts" :: accountId :: viewId :: "transactions" :: transactionId :: "metadata" :: "images" :: Nil JsonGet json => {
      user =>
        for {
          metadata <- moderatedTransactionMetadata(bankId, accountId, viewId, transactionId, user)
          images <- Box(metadata.images) ?~ { "view " + viewId + " does not authorize images access" }
        } yield {
          val json = JSONFactory.createTransactionImagesJSON(images)
          successJsonResponse(Extraction.decompose(json))
        }
    }
  })

  oauthServe(apiPrefix {
  //add an image
    case "banks" :: bankId :: "accounts" :: accountId :: viewId :: "transactions" :: transactionID :: "metadata" :: "images" :: Nil JsonPost json -> _ => {
      user =>
        for {
          u <- user
          imageJson <- tryo{json.extract[PostTransactionImageJSON]}
          view <- View.fromUrl(viewId, accountId, bankId)
          metadata <- moderatedTransactionMetadata(bankId, accountId, view.permalink, transactionID, Full(u))
          addImageFunc <- Box(metadata.addImage) ?~ {"view " + viewId + " does not authorize adding images"}
          url <- tryo{new URL(imageJson.URL)} ?~! "Could not parse url string as a valid URL"
          postedImage <- Full(addImageFunc(u.apiId, view.id, imageJson.label, now, url))
        } yield {
          successJsonResponse(Extraction.decompose(JSONFactory.createTransactionImageJSON(postedImage)),201)
        }
    }
  })

  oauthServe(apiPrefix {
  //delete an image
    case "banks" :: bankId :: "accounts" :: accountId :: viewId :: "transactions" :: transactionId :: "metadata" :: "images" :: imageId :: Nil JsonDelete _ => {
      user =>
        for {
          metadata <- moderatedTransactionMetadata(bankId, accountId, viewId, transactionId, user)
          bankAccount <- BankAccount(bankId, accountId)
          deleted <- Box(metadata.deleteImage(imageId, user, bankAccount))
        } yield {
          noContentJsonResponse
        }
    }
  })

  oauthServe(apiPrefix {
  //get where tag
    case "banks" :: bankId :: "accounts" :: accountId :: viewId :: "transactions" :: transactionId :: "metadata" :: "where" :: Nil JsonGet json => {
      user =>
        for {
          metadata <- moderatedTransactionMetadata(bankId, accountId, viewId, transactionId, user)
          where <- Box(metadata.whereTag) ?~ { "view " + viewId + " does not authorize where tag access" }
        } yield {
          val json = JSONFactory.createLocationJSON(where)
          val whereJson = TransactionWhereJSON(json)
          successJsonResponse(Extraction.decompose(whereJson))
        }
    }
  })

  oauthServe(apiPrefix{
  //add where tag
    case "banks" :: bankId :: "accounts" :: accountId :: viewId :: "transactions" :: transactionId :: "metadata" :: "where" :: Nil JsonPost json -> _ => {
      user =>
        for {
          u <- user
          view <- View.fromUrl(viewId, accountId, bankId)
          metadata <- moderatedTransactionMetadata(bankId, accountId, viewId, transactionId, user)
          addWhereTag <- Box(metadata.addWhereTag) ?~ {"the view " + viewId + "does not allow adding a where tag"}
          whereJson <- tryo{(json.extract[PostTransactionWhereJSON])} ?~ {"wrong JSON format"}
          correctCoordinates <- checkIfLocationPossible(whereJson.where.latitude, whereJson.where.longitude)
          if(addWhereTag(u.apiId, view.id, now, whereJson.where.longitude, whereJson.where.latitude))
        } yield {
            val successJson = SuccessMessage("where tag added")
            successJsonResponse(Extraction.decompose(successJson), 201)
        }
    }
  })

  oauthServe(apiPrefix{
  //update where tag
    case "banks" :: bankId :: "accounts" :: accountId :: viewId :: "transactions" :: transactionId :: "metadata" :: "where" :: Nil JsonPut json -> _ => {
      user =>
        for {
          u <- user
          view <- View.fromUrl(viewId, accountId, bankId)
          metadata <- moderatedTransactionMetadata(bankId, accountId, viewId, transactionId, user)
          addWhereTag <- Box(metadata.addWhereTag) ?~ {"the view " + viewId + "does not allow updating a where tag"}
          whereJson <- tryo{(json.extract[PostTransactionWhereJSON])} ?~ {"wrong JSON format"}
          correctCoordinates <- checkIfLocationPossible(whereJson.where.latitude, whereJson.where.longitude)
         if(addWhereTag(u.apiId, view.id, now, whereJson.where.longitude, whereJson.where.latitude))
        } yield {
            val successJson = SuccessMessage("where tag updated")
            successJsonResponse(Extraction.decompose(successJson))
        }
    }
  })

  oauthServe(apiPrefix{
  //delete where tag
    case "banks" :: bankId :: "accounts" :: accountId :: viewId :: "transactions" :: transactionId :: "metadata" :: "where" :: Nil JsonDelete _ => {
      user =>
        for {
          bankAccount <- BankAccount(bankId, accountId)
          view <- View.fromUrl(viewId, bankAccount)
          metadata <- moderatedTransactionMetadata(bankId, accountId, viewId, transactionId, user)
          deleted <- metadata.deleteWhereTag(view.id, user, bankAccount)
        } yield {
            if(deleted)
              noContentJsonResponse
            else
              errorJsonResponse("Delete not completed")
        }
    }
  })

  oauthServe(apiPrefix{
  //get other account of a transaction
    case "banks" :: bankId :: "accounts" :: accountId :: viewId :: "transactions":: transactionId :: "other_account" :: Nil JsonGet json => {
      user =>
        for {
          account <- BankAccount(bankId, accountId)
          view <- View.fromUrl(viewId, account)
          transaction <- account.moderatedTransaction(transactionId, view, user)
          moderatedOtherBankAccount <- transaction.otherBankAccount
        } yield {
          val otherBankAccountJson = JSONFactory.createOtherBankAccount(moderatedOtherBankAccount)
          successJsonResponse(Extraction.decompose(otherBankAccountJson))
        }

    }
  })
}