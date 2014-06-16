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
package code.model.dataAccess

import com.mongodb.BasicDBList
import com.mongodb.QueryBuilder
import com.tesobe.model.UpdateBankAccount
import net.liftweb.common.{Box, Empty, Full, Failure, Loggable}
import net.liftweb.db.DB
import net.liftweb.json.JsonDSL._
import net.liftweb.mapper.{By,SelectableField, ByList}
import net.liftweb.mongodb.MongoDB
import net.liftweb.mongodb.JsonObject
import net.liftweb.util.Helpers.{tryo, now, hours,minutes, time, asLong}
import net.liftweb.util.Props
import org.bson.types.ObjectId
import scala.concurrent.ops.spawn

import code.model._
import code.model.dataAccess.OBPEnvelope.OBPQueryParam
import code.api.APIFailure


object LocalStorage extends MongoDBLocalStorage

trait LocalStorage extends Loggable {

  def getBank(name: String): Box[Bank]
  def allBanks : List[Bank]

  def getBankAccount(bankId : String, bankAccountId : String) : Box[BankAccount]
  def getAllPublicAccounts() : List[BankAccount]
  def getPublicBankAccounts(bank : Bank) : Box[List[BankAccount]]
  def getAllAccountsUserCanSee(bank: Bank, user : Box[User]) : Box[List[BankAccount]]
  def getNonPublicBankAccounts(user : User) : Box[List[BankAccount]]
  def getNonPublicBankAccounts(user : User, bankID : String) : Box[List[BankAccount]]
  def getModeratedOtherBankAccount(accountID : String, otherAccountID : String)
  	(moderate: OtherBankAccount => Option[ModeratedOtherBankAccount]) : Box[ModeratedOtherBankAccount]
  def getModeratedOtherBankAccounts(accountID : String)
  	(moderate: OtherBankAccount => Option[ModeratedOtherBankAccount]): Box[List[ModeratedOtherBankAccount]]
  def getModeratedTransactions(permalink: String, bankPermalink: String, queryParams: OBPQueryParam*)
    (moderate: Transaction => ModeratedTransaction): Box[List[ModeratedTransaction]]
  def getModeratedTransaction(id : String, bankPermalink : String, accountPermalink : String)
    (moderate: Transaction => ModeratedTransaction) : Box[ModeratedTransaction]

  def getUserByApiId(id : String) : Box[User]
  def getUserByProviderId(provider : String, idGivenByProvider : String) : Box[User]

  def permissions(account : BankAccount) : Box[List[Permission]]
  def permission(account : BankAccount, user: User) : Box[Permission]
  def addPermission(bankAccountId : String, view : View, user : User) : Box[Boolean]
  def addPermissions(bankAccountId : String, views : List[View], user : User) : Box[Boolean]
  def revokePermission(bankAccountId : String, view : View, user : User) : Box[Boolean]
  def revokeAllPermission(bankAccountId : String, user : User) : Box[Boolean]

  def view(viewPermalink : String, bankAccount: BankAccount) : Box[View]
  def view(viewPermalink : String, accountId: String, bankId: String) : Box[View]

  def createView(bankAccount : BankAccount, view: ViewCreationJSON) : Box[View]
  def removeView(viewId: String, bankAccount: BankAccount): Box[Unit]
  def views(bankAccount : BankAccount) : Box[List[View]]
  def permittedViews(user: User, bankAccount: BankAccount): List[View]
  def publicViews(bankAccount : BankAccount) : Box[List[View]]

}

class MongoDBLocalStorage extends LocalStorage {

  /**
  *  Checks if the last update of the account was made more than one hour ago.
  *  if it is the case we put a message in the message queue to ask for
  *  transactions updates
  *
  *  It will be used each time we fetch transactions from the DB. But the test
  *  is performed in a different thread.
  */

  private def updateAccountTransactions(bank: HostedBank, account: Account): Unit = {
    spawn{
      val useMessageQueue = Props.getBool("messageQueue.updateBankAccountsTransaction", false)
      val outDatedTransactions = now after time(account.lastUpdate.get.getTime + hours(1))
      if(outDatedTransactions && useMessageQueue) {
        UpdatesRequestSender.sendMsg(UpdateBankAccount(account.number.get, bank.national_identifier.get))
      }
    }
  }

  private def locatationTag(loc: OBPGeoTag): Option[GeoTag]={
    if(loc.longitude==0 && loc.latitude==0 && loc.userId.get.isEmpty)
      None
    else
      Some(loc)
  }

  private def createOtherBankAccountMetadata(otherAccountMetadata : Metadata): OtherBankAccountMetadata = {
    new OtherBankAccountMetadata(
      publicAlias = otherAccountMetadata.publicAlias.get,
      privateAlias = otherAccountMetadata.privateAlias.get,
      moreInfo = otherAccountMetadata.moreInfo.get,
      url = otherAccountMetadata.url.get,
      imageURL = otherAccountMetadata.imageUrl.get,
      openCorporatesURL = otherAccountMetadata.openCorporatesUrl.get,
      corporateLocation = locatationTag(otherAccountMetadata.corporateLocation.get),
      physicalLocation = locatationTag(otherAccountMetadata.physicalLocation.get),
      addMoreInfo = (text => {
        otherAccountMetadata.moreInfo(text).save
        //the save method does not return a Boolean to inform about the saving state,
        //so we a true
        true
      }),
      addURL = (text => {
        otherAccountMetadata.url(text).save
        //the save method does not return a Boolean to inform about the saving state,
        //so we a true
        true
      }),
      addImageURL = (text => {
        otherAccountMetadata.imageUrl(text).save
        //the save method does not return a Boolean to inform about the saving state,
        //so we a true
        true
      }),
      addOpenCorporatesURL = (text => {
        otherAccountMetadata.openCorporatesUrl(text).save
        //the save method does not return a Boolean to inform about the saving state,
        //so we a true
        true
      }),
      addCorporateLocation = otherAccountMetadata.addCorporateLocation,
      addPhysicalLocation = otherAccountMetadata.addPhysicalLocation,
      addPublicAlias = (alias => {
        otherAccountMetadata.publicAlias(alias).save
        //the save method does not return a Boolean to inform about the saving state,
        //so we a true
        true
      }),
      addPrivateAlias = (alias => {
        otherAccountMetadata.privateAlias(alias).save
        //the save method does not return a Boolean to inform about the saving state,
        //so we a true
        true
      }),
      deleteCorporateLocation = otherAccountMetadata.deleteCorporateLocation _,
      deletePhysicalLocation = otherAccountMetadata.deletePhysicalLocation _
    )
  }

  private def createTransaction(env: OBPEnvelope, theAccount: Account): Option[Transaction] = {
    import net.liftweb.json.JsonDSL._
    val transaction: OBPTransaction = env.obp_transaction.get
    val thisAccount = transaction.this_account
    val otherAccount_ = transaction.other_account.get

    otherAccount_.metadata.obj match {
      case Full(oaccMetadata) =>{
        val thisBankAccount = Account.toBankAccount(theAccount)
        val id = env.id.is.toString()
        val uuid = id
        val otherAccountMetadata = createOtherBankAccountMetadata(oaccMetadata)

        val otherAccount = new OtherBankAccount(
            id = oaccMetadata.id.is.toString,
            label = otherAccount_.holder.get,
            nationalIdentifier = otherAccount_.bank.get.national_identifier.get,
            swift_bic = None, //TODO: need to add this to the json/model
            iban = Some(otherAccount_.bank.get.IBAN.get),
            number = otherAccount_.number.get,
            bankName = otherAccount_.bank.get.name.get,
            metadata = otherAccountMetadata,
            kind = ""
          )
        val transactionType = transaction.details.get.kind.get
        val amount = transaction.details.get.value.get.amount.get
        val currency = transaction.details.get.value.get.currency.get
        val label = Some(transaction.details.get.label.get)
        val startDate = transaction.details.get.posted.get
        val finishDate = transaction.details.get.completed.get
        val balance = transaction.details.get.new_balance.get.amount.get
        val t =
          new Transaction(
            uuid,
            id,
            thisBankAccount,
            otherAccount,
            transactionType,
            amount,
            currency,
            label,
            startDate,
            finishDate,
            balance
          )
        Some(t)
      }
      case _ => {
        logger.warn(s"no metadata reference found for envelope ${env.id.get}")
        None
      }
    }
  }

  private def createOtherBankAccount(otherAccount : Metadata, otherAccountFromTransaction : OBPAccount) : OtherBankAccount = {
    val metadata =
      new OtherBankAccountMetadata(
        publicAlias = otherAccount.publicAlias.get,
        privateAlias = otherAccount.privateAlias.get,
        moreInfo = otherAccount.moreInfo.get,
        url = otherAccount.url.get,
        imageURL = otherAccount.imageUrl.get,
        openCorporatesURL = otherAccount.openCorporatesUrl.get,
        corporateLocation = locatationTag(otherAccount.corporateLocation.get),
        physicalLocation = locatationTag(otherAccount.physicalLocation.get),
        addMoreInfo = (text => {
          otherAccount.moreInfo(text).save
          //the save method does not return a Boolean to inform about the saving state,
          //so we a true
          true
        }),
        addURL = (text => {
          otherAccount.url(text).save
          //the save method does not return a Boolean to inform about the saving state,
          //so we a true
          true
        }),
        addImageURL = (text => {
          otherAccount.imageUrl(text).save
          //the save method does not return a Boolean to inform about the saving state,
          //so we a true
          true
        }),
        addOpenCorporatesURL = (text => {
          otherAccount.openCorporatesUrl(text).save
          //the save method does not return a Boolean to inform about the saving state,
          //so we a true
          true
        }),
        addCorporateLocation = otherAccount.addCorporateLocation,
        addPhysicalLocation = otherAccount.addPhysicalLocation,
        addPublicAlias = (alias => {
          otherAccount.publicAlias(alias).save
          //the save method does not return a Boolean to inform about the saving state,
          //so we a true
          true
        }),
        addPrivateAlias = (alias => {
          otherAccount.privateAlias(alias).save
          //the save method does not return a Boolean to inform about the saving state,
          //so we a true
          true
        }),
        deleteCorporateLocation = otherAccount.deleteCorporateLocation _,
        deletePhysicalLocation = otherAccount.deletePhysicalLocation _
      )

    new OtherBankAccount(
      id = otherAccount.id.is.toString,
      label = otherAccount.holder.get,
      nationalIdentifier = otherAccountFromTransaction.bank.get.national_identifier.get,
      swift_bic = None, //TODO: need to add this to the json/model
      iban = Some(otherAccountFromTransaction.bank.get.IBAN.get),
      number = otherAccountFromTransaction.number.get,
      bankName = otherAccountFromTransaction.bank.get.name.get,
      metadata = metadata,
      kind = ""
    )
  }

  private def createBank(bank : HostedBank) : Bank = {
    new Bank(
      bank.id.is.toString,
      bank.alias.is,
      bank.name.is,
      bank.permalink.is,
      bank.logoURL.is,
      bank.website.is
    )
  }

  private def getHostedBank(permalink : String) : Box[HostedBank] = {
    for{
      bank <- HostedBank.find("permalink", permalink) ?~ {"bank " + permalink + " not found"}
    } yield bank
  }

  private def getTransaction(id : String, bankPermalink : String, accountPermalink : String) : Box[Transaction] = {
    for{
      bank <- getHostedBank(bankPermalink) ?~! s"Transaction not found: bank $bankPermalink not found"
      account  <- bank.getAccount(accountPermalink) ?~! s"Transaction not found: account $accountPermalink not found"
      objectId <- tryo{new ObjectId(id)} ?~ {"Transaction "+id+" not found"}
      envelope <- OBPEnvelope.find(account.transactionsForAccount.put("_id").is(objectId).get)
      transaction <- createTransaction(envelope,account)
    } yield {
      updateAccountTransactions(bank, account)
      transaction
    }
  }

  private def getTransactions(permalink: String, bankPermalink: String, queryParams: OBPQueryParam*): Box[List[Transaction]] = {
      logger.debug("getTransactions for " + bankPermalink + "/" + permalink)
      for{
        bank <- getHostedBank(bankPermalink)
        account <- bank.getAccount(permalink)
      } yield {
        updateAccountTransactions(bank, account)
        account.envelopes(queryParams: _*).flatMap(createTransaction(_, account))
      }
  }

  def getBank(permalink: String): Box[Bank] =
    for{
      bank <- getHostedBank(permalink)
    } yield {
      createBank(bank)
    }

  def allBanks : List[Bank] =
    HostedBank.findAll.map(createBank)

  def getBankAccount(bankId : String, bankAccountId : String) : Box[BankAccount] = {
    for{
      bank <- getHostedBank(bankId)
      account <- bank.getAccount(bankAccountId)
    } yield Account toBankAccount account
  }

  def getAllPublicAccounts() : List[BankAccount] = {
    ViewImpl.findAll(By(ViewImpl.isPublic_, true)).
      map{_.account.obj}.
      collect{case Full(a) => a.theAccount}.
      collect{case Full(a) => Account.toBankAccount(a)}
  }

  def getPublicBankAccounts(bank : Bank) : Box[List[BankAccount]] = {
    val bankAccounts = ViewImpl.findAll(By(ViewImpl.isPublic_, true)).
      map{_.account.obj}.
      collect{case Full(a) if a.bank==bank.fullName => a.theAccount}.
      collect{case Full(a) => Account.toBankAccount(a)}
    Full(bankAccounts)
  }

  private def moreThanAnonHostedAccounts(user : User) : List[HostedAccount] = {
    user match {
      case u : APIUser => {
        u.views_.toList.
        filterNot(_.isPublic_).
        map(_.account.obj.get)
      }
      case _ => {
        logger.error("APIUser instance not found, could not find the accounts")
        Nil
      }
    }
  }

  /**
   * @param user
   * @return the bank accounts the @user can see (public + private if @user is Full, public if @user is Empty)
   */
  def getAllAccountsUserCanSee(user : Box[User]) : List[BankAccount] = {
    user match {
      case Full(u) => {
        val moreThanAnonHosted = moreThanAnonHostedAccounts(u)
        val mongoIds = moreThanAnonHosted.map(hAcc => new ObjectId(hAcc.accountID.get))
        val moreThanAnonAccounts = Account.findAll(mongoIds).map(Account.toBankAccount)

        val publicAccountsThatUserDoesNotHaveMoreThanAnon = ViewImpl.findAll(By(ViewImpl.isPublic_, true)).
          map{_.account.obj}.
          collect{case Full(a) => a.theAccount}.
          collect{case Full(a)
          //Throw out those that are already counted in moreThanAnonAccounts
          if(!moreThanAnonAccounts.exists(x => sameAccount(a, x))) => Account.toBankAccount(a)
        }

        moreThanAnonAccounts ++ publicAccountsThatUserDoesNotHaveMoreThanAnon
      }
      case _ => getAllPublicAccounts()
    }
  }

  /**
   * Checks if an Account and BankAccount represent the same thing (to avoid converting between the two if
   * it's not required)
   */
  private def sameAccount(account : Account, bankAccount : BankAccount) : Boolean = {
    //important: account.permalink.get (if you just use account.permalink it compares a StringField
    // to a String, which will always be false
    (account.bankPermalink == bankAccount.bankPermalink) && (account.permalink.get == bankAccount.permalink)
  }

  /**
  * @param user
  * @return the bank accounts at @bank the @user can see (public + private if @user is Full, public if @user is Empty)
  */
  def getAllAccountsUserCanSee(bank: Bank, user : Box[User]) : Box[List[BankAccount]] = {
    user match {
      case Full(u) => {
        //TODO: this could be quite a bit more efficient...
        for {
          bankObjectId <- tryo{new ObjectId(bank.id)}
        } yield {
          def sameBank(account : Account) : Boolean =
            account.bankID.get == bankObjectId

          val moreThanAnonHosted = moreThanAnonHostedAccounts(u)
          val mongoIds = moreThanAnonHosted.map(hAcc => new ObjectId(hAcc.accountID.get))
          val moreThanAnonAccounts = Account.findAll(mongoIds).filter(sameBank).map(Account.toBankAccount)

          val publicAccountsThatUserDoesNotHaveMoreThanAnon = ViewImpl.findAll(By(ViewImpl.isPublic_, true)).
            map{_.account.obj}.
            collect{case Full(a) if a.bank==bank.fullName => a.theAccount}. //throw out with the wrong bank
            collect{case Full(a)
              //Throw out those that are already counted in moreThanAnonAccounts
              if(!moreThanAnonAccounts.exists(x => sameAccount(a, x))) => Account.toBankAccount(a)
          }

          moreThanAnonAccounts ++ publicAccountsThatUserDoesNotHaveMoreThanAnon
        }
      }
      case _ => getPublicBankAccounts(bank)
    }
  }

  /**
  * @return the bank accounts where the user has at least access to a non public view (is_public==false)
  */
  def getNonPublicBankAccounts(user : User) :  Box[List[BankAccount]] = {

    val accountsList =
      user match {
        case u : APIUser => {
          val moreThanAnon = moreThanAnonHostedAccounts(u)
          val mongoIds = moreThanAnon.map(hAcc => new ObjectId(hAcc.accountID.get))
          Account.findAll(mongoIds).map(Account.toBankAccount)
        }
        case u: User => {
          logger.error("APIUser instance not found, could not find the non public accounts")
          Nil
        }
      }
    Full(accountsList)
  }

  /**
  * @return the bank accounts where the user has at least access to a non public view (is_public==false) for a specific bank
  */
  def getNonPublicBankAccounts(user : User, bankID : String) :  Box[List[BankAccount]] = {
    user match {
      case u : APIUser => {
        for {
          bankObjectId <- tryo{new ObjectId(bankID)}
        } yield {
          def sameBank(account : Account) : Boolean =
            account.bankID.get == bankObjectId

          val moreThanAnon = moreThanAnonHostedAccounts(u)
          val mongoIds = moreThanAnon.map(hAcc => new ObjectId(hAcc.accountID.get))
          Account.findAll(mongoIds).filter(sameBank).map(Account.toBankAccount)
        }
      }
      case u : User => {
        logger.error("APIUser instance not found, could not find the non public account ")
        Full(Nil)
      }
    }
  }

  def getModeratedOtherBankAccount(accountID : String, otherAccountID : String)
  (moderate: OtherBankAccount => Option[ModeratedOtherBankAccount]): Box[ModeratedOtherBankAccount] = {
      for{
        id <- tryo{new ObjectId(accountID)} ?~ {"account " + accountID + " not found"}
        account <- Account.find("_id",id)
        otherAccountmetadata <- account.otherAccountsMetadata.objs.find(_.id.get.equals(otherAccountID))
      } yield{
          val otherAccountFromTransaction : OBPAccount = OBPEnvelope.find("obp_transaction.other_account.metadata",otherAccountmetadata.id.is) match {
            case Full(envelope) => envelope.obp_transaction.get.other_account.get
            case _ => {
              logger.warn("no other account found")
              OBPAccount.createRecord
            }
          }
          moderate(createOtherBankAccount(otherAccountmetadata, otherAccountFromTransaction)).get
        }
  }

  def getModeratedOtherBankAccounts(accountID : String)
  (moderate: OtherBankAccount => Option[ModeratedOtherBankAccount]): Box[List[ModeratedOtherBankAccount]] = {
    for{
      id <- tryo{new ObjectId(accountID)} ?~ {"account " + accountID + " not found"}
      account <- Account.find("_id",id)
    } yield{
        val otherBankAccounts = account.otherAccountsMetadata.objs.map(otherAccount => {
          //for legacy reasons some of the data about the "other account" are stored only on the transactions
          //so we need first to get a transaction that match to have the rest of the data
          val otherAccountFromTransaction : OBPAccount = OBPEnvelope.find("obp_transaction.other_account.holder",otherAccount.holder.get) match {
              case Full(envelope) =>
                envelope.obp_transaction.get.other_account.get
              case _ => {
                logger.warn(s"envelope not found for other account ${otherAccount.id.get}")
                OBPAccount.createRecord
              }
            }
          createOtherBankAccount(otherAccount, otherAccountFromTransaction)
        })

        (otherBankAccounts.map(moderate)).collect{case Some(t) => t}
      }
  }

  def getModeratedTransactions(permalink: String, bankPermalink: String, queryParams: OBPQueryParam*)
  (moderate: Transaction => ModeratedTransaction): Box[List[ModeratedTransaction]] = {
    for{
      rawTransactions <- getTransactions(permalink, bankPermalink, queryParams: _*)
    } yield rawTransactions.map(moderate)
  }

  def getUserByApiId(id : String) : Box[User] = {
    for{
      idAsLong <- tryo { id.toLong } ?~ "Invalid id: long required"
      user <- APIUser.find(By(APIUser.id, idAsLong)) ?~ { s"user $id not found"}
    } yield user
  }

  def getUserByProviderId(provider : String, idGivenByProvider : String) : Box[User] = {
    APIUser.find(By(APIUser.provider_, provider), By(APIUser.providerId, idGivenByProvider))
  }


  def getModeratedTransaction(id : String, bankPermalink : String, accountPermalink : String)
  (moderate: Transaction => ModeratedTransaction) : Box[ModeratedTransaction] = {
    for{
      transaction <- getTransaction(id,bankPermalink,accountPermalink)
    } yield moderate(transaction)
  }

  def permissions(account : BankAccount) : Box[List[Permission]] = {
    for{
      acc <- HostedAccount.find(By(HostedAccount.accountID,account.id))
    } yield {

        val views: List[ViewImpl] = ViewImpl.findAll(By(ViewImpl.account, acc), By(ViewImpl.isPublic_, false))
        //all the user that have access to at least to a view
        val users = views.map(_.users.toList).flatten.distinct
        val usersPerView = views.map(v  =>(v, v.users.toList))
        users.map(u => {
          new Permission(
            u,
            usersPerView.filter(_._2.contains(u)).map(_._1)
          )
        })
      }
  }

  def permission(account : BankAccount, user: User) : Box[Permission] = {

    for{
      acc <- HostedAccount.find(By(HostedAccount.accountID,account.id))
    } yield {
      val viewsOfAccount = acc.views.toList
      val viewsOfUser = user.views.toList
      val views = viewsOfAccount.filter(v => viewsOfUser.contains(v))
      Permission(user, views)
    }
  }

  def addPermission(bankAccountId : String, view: View, user : User) : Box[Boolean] = {
    user match {
      case u: APIUser =>
        for{
          bankAccount <- HostedAccount.find(By(HostedAccount.accountID, bankAccountId))
        } yield {
            if(ViewPrivileges.count(By(ViewPrivileges.user,u), By(ViewPrivileges.view,view.id))==0)
              ViewPrivileges.create.
                user(u).
                view(view.id).
                save
            else
              true
          }
      case u: User => {
          logger.error("APIUser instance not found, could not grant access ")
          Empty
      }
    }
  }

  def addPermissions(bankAccountId : String, views : List[View], user : User) : Box[Boolean] ={
    user match {
      case u : APIUser => {
        views.foreach(v => {
          if(ViewPrivileges.count(By(ViewPrivileges.user,u), By(ViewPrivileges.view,v.id))==0){
            ViewPrivileges.create.
              user(u).
              view(v.id).
              save
          }
        })
        Full(true)
      }
      case u: User => {
        logger.error("APIUser instance not found, could not grant access ")
        Empty
      }
    }

  }
  def revokePermission(bankAccountId : String, view : View, user : User) : Box[Boolean] = {
    user match {
      case u:APIUser =>
        for{
          bankAccount <- HostedAccount.find(By(HostedAccount.accountID, bankAccountId))
          vp <- ViewPrivileges.find(By(ViewPrivileges.user, u), By(ViewPrivileges.view, view.id))
          deletable <- checkIfOwnerViewAndHasMoreThanOneUser(view)
        } yield {
            vp.delete_!
          }
      case u: User => {
        logger.error("APIUser instance not found, could not revoke access")
        Empty
      }
    }
  }

  def checkIfOwnerViewAndHasMoreThanOneUser(view: View): Box[Unit] = {
    if((view.name=="Owner") && (view.users.length <= 1)){
      Failure("only person with owner view permission, access cannot be revoked")
    }
    else{
      Full(Unit)
    }
  }

  def revokeAllPermission(bankAccountId : String, user : User) : Box[Boolean] = {
    user match {
      case u:APIUser =>{
        for{
          bankAccount <- HostedAccount.find(By(HostedAccount.accountID, bankAccountId))
        } yield {
          val views = ViewImpl.findAll(By(ViewImpl.account, bankAccount)).map(_.id_.get)
          ViewPrivileges.findAll(By(ViewPrivileges.user, u), ByList(ViewPrivileges.view, views)).map(_.delete_!)
          true
        }
      }
      case u: User => {
        logger.error("APIUser instance not found, could not revoke access ")
        Empty
      }
    }
  }

  def view(viewPermalink : String, account: BankAccount) : Box[View] = {
    for{
      acc <- HostedAccount.find(By(HostedAccount.accountID, account.id))
      v <- ViewImpl.find(By(ViewImpl.permalink_, viewPermalink), By(ViewImpl.account, acc))
    } yield v
  }

  def view(viewPermalink : String, accountId: String, bankId: String) : Box[View] = {
    for{
      account <- getBankAccount(bankId, accountId)
      acc <- HostedAccount.find(By(HostedAccount.accountID, account.id))
      v <- ViewImpl.find(By(ViewImpl.permalink_, viewPermalink), By(ViewImpl.account, acc))
    } yield v
  }

  def createView(bankAccount: BankAccount, view: ViewCreationJSON): Box[View] = {
    val newViewPermalink = {
      view.name.replaceAllLiterally(" ","").toLowerCase
    }

    HostedAccount.find(By(HostedAccount.accountID,bankAccount.id)) match {
      case Full(account) => {
        val existing = ViewImpl.find(
        By(ViewImpl.permalink_, newViewPermalink),
        By(ViewImpl.account, account))

        if(existing.isDefined)
          Failure(s"There is already a view with permalink $newViewPermalink on this bank account")
        else{
          val createdView = ViewImpl.create.
            name_(view.name).
            permalink_(newViewPermalink).
            account(account)

          createdView.setFromViewData(view)
          Full(createdView.saveMe)
        }

      }
      case _ => Failure(s"Account ${bankAccount.id} not found")
    }


  }

  def updateView(bankAccount : BankAccount, viewId: String, viewUpdateJson : ViewUpdateData) : Box[View] = {

    for {
      account <- HostedAccount.find(By(HostedAccount.accountID, bankAccount.id)) ~> new APIFailure {
        override val responseCode: Int = 404
        override val msg: String = s"Account ${bankAccount.id} not found"
      }
      view <- ViewImpl.find(
        By(ViewImpl.permalink_, viewId),
        By(ViewImpl.account, account)) ~> new APIFailure {
          override val responseCode: Int = 404
          override val msg: String = s"View $viewId not found"
        }
    } yield {
      view.setFromViewData(viewUpdateJson)
      view.saveMe
    }
  }

  def removeView(viewId: String, bankAccount: BankAccount): Box[Unit] = {
    if(viewId=="Owner")
      Failure("you cannot delete the Owner view")
    else
      for{
        v <- ViewImpl.find(By(ViewImpl.permalink_,viewId)) ?~ "view not found"
        if(v.delete_!)
      } yield {}
  }

  def views(bankAccount : BankAccount) : Box[List[View]] = {
    for(account <- HostedAccount.find(By(HostedAccount.accountID, bankAccount.id)))
      yield {
        account.views.toList
      }
  }

  def permittedViews(user: User, bankAccount: BankAccount): List[View] = {
    user match {
      case u: APIUser=> {
        val nonPublic: List[View] =
          HostedAccount.find(By(HostedAccount.accountID, bankAccount.id)) match {
            case Full(account) =>{
              val accountViews = account.views.toList
              val userViews = u.views
              accountViews.filter(v => userViews.contains(v))
            }
            case _ => Nil
          }

        nonPublic ::: bankAccount.publicViews
      }
      case _ => {
        logger.error("APIUser instance not found, could not get Permitted views")
        Nil
      }
    }
  }

  def publicViews(bankAccount : BankAccount) : Box[List[View]] = {
    for{account <- HostedAccount.find(By(HostedAccount.accountID, bankAccount.id))}
      yield{
        account.views.toList.filter(v => v.isPublic==true)
      }
  }
}