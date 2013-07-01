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

import code.model._
import net.liftweb.common.{ Box, Empty, Full, Failure }
import net.liftweb.util.Helpers.tryo
import net.liftweb.mongodb.BsonDSL._
import net.liftweb.json.JsonDSL._
import net.liftweb.common.Loggable
import code.model.dataAccess.OBPEnvelope.OBPQueryParam
import net.liftweb.mapper.{By,IHaveValidatedThisSQL}
import net.liftweb.mongodb.MongoDB
import com.mongodb.BasicDBList
import java.util.ArrayList
import org.bson.types.ObjectId
import net.liftweb.mapper.BySql
import net.liftweb.db.DB

object LocalStorage extends MongoDBLocalStorage

trait LocalStorage extends Loggable {

  def getBank(name: String): Box[Bank]

  def allBanks : List[Bank]

  //TODO: remove after the split because useless
  def getAccount(bankpermalink: String, account: String): Box[Account]

  def getBankAccount(bankId : String, bankAccountId : String) : Box[BankAccount]

  def getAllPublicAccounts() : List[BankAccount]

  def getPublicBankAccounts(bank : Bank) : Box[List[BankAccount]]

  def getNonPublicBankAccounts(user : User) : Box[List[BankAccount]]

  def getNonPublicBankAccounts(user : User, bankID : String) : Box[List[BankAccount]]

  //TODO: remove after the split because useless
  def correctBankAndAccount(bank: String, account: String): Boolean

  def getModeratedOtherBankAccount(accountID : String, otherAccountID : String)
  	(moderate: OtherBankAccount => Option[ModeratedOtherBankAccount]) : Box[ModeratedOtherBankAccount]

  def getModeratedOtherBankAccounts(accountID : String)
  	(moderate: OtherBankAccount => Option[ModeratedOtherBankAccount]): Box[List[ModeratedOtherBankAccount]]

  def getModeratedTransactions(permalink: String, bankPermalink: String, queryParams: OBPQueryParam*)
    (moderate: Transaction => ModeratedTransaction): Box[List[ModeratedTransaction]]

  def getUser(id : String) : Box[User]

  def getCurrentUser : Box[User]

  def permissions(account : BankAccount) : Box[List[Permission]]

  def addPermission(bankAccountId : String, view : View, user : User) : Box[Boolean]

  def addPermissions(bankAccountId : String, views : List[View], user : User) : Box[Boolean]

  def revokePermission(bankAccountId : String, view : View, user : User) : Box[Boolean]

  def revokeAllPermission(bankAccountId : String, user : User) : Box[Boolean]

  def views(bankAccountID : String) : Box[List[View]]

}

class MongoDBLocalStorage extends LocalStorage {

  private val availableViews = List(Team, Board, Authorities, Public, OurNetwork, Owner, Management)

  private def createTransaction(env: OBPEnvelope, theAccount: Account): Transaction = {
    import net.liftweb.json.JsonDSL._
    val transaction: OBPTransaction = env.obp_transaction.get
    val thisAccount = transaction.this_account
    val otherAccount_ = transaction.other_account.get
    val otherUnmediatedHolder = otherAccount_.holder.get

    val thisBankAccount = Account.toBankAccount(theAccount)

    val oAcc = theAccount.otherAccounts.objs.find(o => {
      otherUnmediatedHolder.equals(o.holder.get)
    }).getOrElse {
      OtherAccount.createRecord
    }

    val id = env.id.is.toString()
    val uuid = id
    val otherAccountMetadata =
      new OtherBankAccountMetadata(
        publicAlias = oAcc.publicAlias.get,
        privateAlias = oAcc.privateAlias.get,
        moreInfo = oAcc.moreInfo.get,
        url = oAcc.url.get,
        imageURL = oAcc.imageUrl.get,
        openCorporatesURL = oAcc.openCorporatesUrl.get,
        corporateLocation = oAcc.corporateLocation.get,
        physicalLocation = oAcc.physicalLocation.get,
        addMoreInfo = (text => {
          oAcc.moreInfo(text).save
          //the save method does not return a Boolean to inform about the saving state,
          //so we a true
          true
        }),
        addURL = (text => {
          oAcc.url(text).save
          //the save method does not return a Boolean to inform about the saving state,
          //so we a true
          true
        }),
        addImageURL = (text => {
          oAcc.imageUrl(text).save
          //the save method does not return a Boolean to inform about the saving state,
          //so we a true
          true
        }),
        addOpenCorporatesURL = (text => {
          oAcc.openCorporatesUrl(text).save
          //the save method does not return a Boolean to inform about the saving state,
          //so we a true
          true
        }),
        addCorporateLocation = oAcc.addCorporateLocation,
        addPhysicalLocation = oAcc.addPhysicalLocation,
        addPublicAlias = (alias => {
          oAcc.publicAlias(alias).save
          //the save method does not return a Boolean to inform about the saving state,
          //so we a true
          true
        }),
        addPrivateAlias = (alias => {
          oAcc.privateAlias(alias).save
          //the save method does not return a Boolean to inform about the saving state,
          //so we a true
          true
        }),
        deleteCorporateLocation = oAcc.deleteCorporateLocation _,
        deletePhysicalLocation = oAcc.deletePhysicalLocation _
      )
    val otherAccount = new OtherBankAccount(
        id = oAcc.id.is.toString,
        label = otherAccount_.holder.get,
        nationalIdentifier = otherAccount_.bank.get.national_identifier.get,
        swift_bic = None, //TODO: need to add this to the json/model
        iban = Some(otherAccount_.bank.get.IBAN.get),
        number = otherAccount_.number.get,
        bankName = otherAccount_.bank.get.name.get,
        metadata = otherAccountMetadata,
        kind = ""
      )
    val metadata = new TransactionMetadata(
      env.narrative.get,
      (text => env.narrative(text).save),
      env.obp_comments.objs,
      env.addComment,
      env.deleteComment,
      env.tags.objs,
      env.addTag,
      env.deleteTag,
      env.images.objs,
      env.addImage,
      env.deleteImage,
      env.whereTags.get,
      env.addWhereTag,
      env.deleteWhereTag
    )
    val transactionType = transaction.details.get.type_en.get
    val amount = transaction.details.get.value.get.amount.get
    val currency = transaction.details.get.value.get.currency.get
    val label = Some(transaction.details.get.label.get)
    val startDate = transaction.details.get.posted.get
    val finishDate = transaction.details.get.completed.get
    val balance = transaction.details.get.new_balance.get.amount.get
    new Transaction(
      uuid,
      id,
      thisBankAccount,
      otherAccount,
      metadata,
      transactionType,
      amount,
      currency,
      label,
      startDate,
      finishDate,
      balance
    )
  }

  private def createOtherBankAccount(otherAccount : OtherAccount, otherAccountFromTransaction : OBPAccount) : OtherBankAccount = {
    val metadata =
      new OtherBankAccountMetadata(
        publicAlias = otherAccount.publicAlias.get,
        privateAlias = otherAccount.privateAlias.get,
        moreInfo = otherAccount.moreInfo.get,
        url = otherAccount.url.get,
        imageURL = otherAccount.imageUrl.get,
        openCorporatesURL = otherAccount.openCorporatesUrl.get,
        corporateLocation = otherAccount.corporateLocation.get,
        physicalLocation = otherAccount.physicalLocation.get,
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

  private def setPrivilegeFromView(privilege : Privilege, view : View, value : Boolean ) = {
    view match {
      case OurNetwork => privilege.ourNetworkPermission(value)
      case Team => privilege.teamPermission(value)
      case Board => privilege.boardPermission(value)
      case Authorities => privilege.authoritiesPermission(value)
      case Owner => privilege.ownerPermission(value)
      case Management => privilege.mangementPermission(value)
      case _ =>
    }
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
      bank <- getHostedBank(bankPermalink)
      account  <- bank.getAccount(accountPermalink)
      objectId <- tryo{new ObjectId(id)} ?~ {"Transaction "+id+" not found"}
      ifTransactionsIsInAccount <- Full(account.transactionsForAccount.put("_id").is(objectId).get)
      envelope <- OBPEnvelope.find(ifTransactionsIsInAccount)
    } yield createTransaction(envelope,account)
  }

  private def getTransactions(permalink: String, bankPermalink: String, queryParams: OBPQueryParam*): Box[List[Transaction]] = {
      logger.debug("getTransactions for " + bankPermalink + "/" + permalink)
      for{
        bank <- getHostedBank(bankPermalink)
        account <- bank.getAccount(permalink)
      } yield account.envelopes(queryParams: _*).map(createTransaction(_, account))
  }

  def getBank(permalink: String): Box[Bank] =
    for{
      bank <- getHostedBank(permalink)
    } yield createBank(bank)

  def allBanks : List[Bank] =
    HostedBank.findAll.map(createBank)

  //TODO: remove after the split because useless
  def getAccount(bankpermalink: String, account: String): Box[Account] =
    for{
      hostedBank <- getHostedBank(bankpermalink)
      account <- hostedBank.getAccount(account)
    } yield account

  def getBankAccount(bankId : String, bankAccountId : String) : Box[BankAccount] = {
    for{
      bank <- getHostedBank(bankId)
      account <- bank.getAccount(bankAccountId)
    } yield Account toBankAccount account
  }

  def getAllPublicAccounts() : List[BankAccount] = Account.findAll("anonAccess", true) map Account.toBankAccount

  def getPublicBankAccounts(bank : Bank) : Box[List[BankAccount]] = {
    for{
      id <- tryo{new ObjectId(bank.id)} ?~ {"bank " + bank.fullName + " not found"}
    } yield Account.findAll(("bankID",id) ~ ("anonAccess", true)).map(Account.toBankAccount)
  }

  private def moreThanAnonHostedAccounts(user : User) : Box[List[HostedAccount]] = {
    user match {
      case u : OBPUser => {
        val hostedAccountTable = HostedAccount._dbTableNameLC
        val privilegeTable = Privilege._dbTableNameLC
        val userTable = OBPUser._dbTableNameLC

        val hostedId = hostedAccountTable + "." + HostedAccount.id.dbColumnName
        val hostedAccId = hostedAccountTable + "." + HostedAccount.accountID.dbColumnName
        val privilegeAccId = privilegeTable + "." + Privilege.account.dbColumnName
        val privilegeUserId = privilegeTable + "." + Privilege.user.dbColumnName

        val ourNetworkPrivilege = privilegeTable + "." + Privilege.ourNetworkPermission.dbColumnName
        val teamPrivilege = privilegeTable + "." + Privilege.teamPermission.dbColumnName
        val boardPrivilege = privilegeTable + "." + Privilege.boardPermission.dbColumnName
        val authoritiesPrivilege = privilegeTable + "." + Privilege.authoritiesPermission.dbColumnName
        val ownerPrivilege = privilegeTable + "." + Privilege.ownerPermission.dbColumnName
        val managementPrivilege = privilegeTable + "." + Privilege.mangementPermission.dbColumnName

        val query = "SELECT DISTINCT " + hostedId + ", " + hostedAccId +
              " FROM " + hostedAccountTable + ", " + privilegeTable + ", " + userTable +
              " WHERE " + "( " + hostedId + " = " + privilegeAccId + ")" +
                " AND " + "( " + privilegeUserId + " = ? " + ")"+
                " AND " + "( " + ourNetworkPrivilege + " = true" +
                  " OR " + teamPrivilege + " = true" +
                  " OR " + boardPrivilege + " = true" +
                  " OR " + authoritiesPrivilege + " = true" +
                  " OR " + managementPrivilege + " = true" +
                  " OR " + ownerPrivilege + " = true)"

        Full(HostedAccount.findAllByPreparedStatement({
          superconn => {
            val statement = superconn.connection.prepareStatement(query)
            statement.setLong(1, u.id.get)
            statement
          }
        }))
      }
      case _ => {
        logger.error("OBPUser instance not found, could not execute the SQL query ")
        Failure("could not find non public bank accounts")

      }

    }
  }

  /**
  * @return the bank accounts where the user has at least access to a non public view (is_public==false)
  */
  def getNonPublicBankAccounts(user : User) :  Box[List[BankAccount]] = {

    user match {
      case u : OBPUser => {

        for {
          moreThanAnon <- moreThanAnonHostedAccounts(u)
        } yield {
          val mongoIds = moreThanAnon.map(hAcc => new ObjectId(hAcc.accountID.get))
          Account.findAll(mongoIds).map(Account.toBankAccount)
        }

      }
      case u: User => {
          logger.error("OBPUser instance not found, could not execute the SQL query ")
          Failure("could not find non public bank accounts")
      }
    }
  }

  /**
  * @return the bank accounts where the user has at least access to a non public view (is_public==false) for a specific bank
  */
  def getNonPublicBankAccounts(user : User, bankID : String) :  Box[List[BankAccount]] = {
    user match {
      case u : OBPUser => {

        for {
          moreThanAnon <- moreThanAnonHostedAccounts(u)
          bankObjectId <- tryo{new ObjectId(bankID)}
        } yield {

          def sameBank(account : Account) : Boolean =
            account.bankID.get == bankObjectId

          val mongoIds = moreThanAnon.map(hAcc => new ObjectId(hAcc.accountID.get))
          Account.findAll(mongoIds).filter(sameBank).map(Account.toBankAccount)
        }

      }
      case u : User => {
        logger.error("OBPUser instance not found, could not execute the SQL query ")
        Failure("could not find non public bank accounts")
      }
    }
  }

  //TODO: remove after the split because useless
  def correctBankAndAccount(bank: String, account: String): Boolean =
    getHostedBank(bank) match {
        case Full(bank) => bank.isAccount(account)
        case _ => false
      }

  def getModeratedOtherBankAccount(accountID : String, otherAccountID : String)
  	(moderate: OtherBankAccount => Option[ModeratedOtherBankAccount]): Box[ModeratedOtherBankAccount] = {
    for{
      id <- tryo{new ObjectId(accountID)} ?~ {"account " + accountID + " not found"}
      account <- Account.find("_id",id)
      otherAccount <- account.otherAccounts.objs.find(_.id.get.equals(otherAccountID))
    } yield{
        val otherAccountFromTransaction : OBPAccount = OBPEnvelope.find("obp_transaction.other_account.holder",otherAccount.holder.get) match {
          case Full(envelope) =>
            envelope.obp_transaction.get.other_account.get
          case _ => OBPAccount.createRecord
        }
        moderate(createOtherBankAccount(otherAccount, otherAccountFromTransaction)).get
      }
  }

  def getModeratedOtherBankAccounts(accountID : String)
    (moderate: OtherBankAccount => Option[ModeratedOtherBankAccount]): Box[List[ModeratedOtherBankAccount]] = {
    for{
      id <- tryo{new ObjectId(accountID)} ?~ {"account " + accountID + " not found"}
      account <- Account.find("_id",id)
    } yield{
        val otherBankAccounts = account.otherAccounts.objs.map(otherAccount => {
          //for legacy reasons some of the data about the "other account" are stored only on the transactions
          //so we need first to get a transaction that match to have the rest of the data
          val otherAccountFromTransaction : OBPAccount = OBPEnvelope.find("obp_transaction.other_account.holder",otherAccount.holder.get) match {
              case Full(envelope) =>
                envelope.obp_transaction.get.other_account.get
              case _ => OBPAccount.createRecord
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

  def getUser(id : String) : Box[User] =
    OBPUser.find(By(OBPUser.email,id)) match {
      case Full(u) => Full(u)
      case _ => Failure("user " + id + " not found")
    }

  def getModeratedTransaction(id : String, bankPermalink : String, accountPermalink : String)
    (moderate: Transaction => ModeratedTransaction) : Box[ModeratedTransaction] = {
    for{
      transaction <- getTransaction(id,bankPermalink,accountPermalink)
    } yield moderate(transaction)
  }

  def getCurrentUser : Box[User] = OBPUser.currentUser

  def permissions(account : BankAccount) : Box[List[Permission]] = {

    HostedAccount.find(By(HostedAccount.accountID,account.id)) match {
      case Full(acc) => {
        val privileges = Privilege.findAll(By(Privilege.account, acc.id.get)).sortWith((p1,p2) => p1.updatedAt.get after p2.updatedAt.get)
        val permissions : List[Box[Permission]] = privileges.map( p => {
            if(
              p.ourNetworkPermission.get != false
              | p.teamPermission.get != false
              | p.boardPermission.get != false
              | p.authoritiesPermission.get != false
              | p.ownerPermission.get != false
              | p.mangementPermission.get != false
            )
              p.user.obj.map(u => {
                  new Permission(
                    u,
                    u.permittedViews(account).toList
                  )
                })
            else
              Empty
          })
        Full(permissions.flatten)
      }
      case _ => Failure("Could not find the hostedAccount", Empty, Empty)
    }
  }

  def addPermission(bankAccountId : String, view : View, user : User) : Box[Boolean] = {
    user match {
      case u: OBPUser =>
        for{
          bankAccount <- HostedAccount.find(By(HostedAccount.accountID, bankAccountId))
        } yield {
            Privilege.find(By(Privilege.user, u.id), By(Privilege.account, bankAccount)) match {
              //update the existing privilege
              case Full(privilege) => {
                setPrivilegeFromView(privilege, view, true)
                privilege.save
              }
              //there is no privilege to this user, so we create one
              case _ => {
                val privilege =
                Privilege.create.
                  user(u.id).
                  account(bankAccount)
                setPrivilegeFromView(privilege, view, true)
                privilege.save
              }
            }
          }
      case u: User => {
          logger.error("OBPUser instance not found, could not grant access ")
          Empty
      }
    }
  }

  def addPermissions(bankAccountId : String, views : List[View], user : User) : Box[Boolean] ={
    user match {
      case u : OBPUser => {
        for{
          bankAccount <- HostedAccount.find(By(HostedAccount.accountID, bankAccountId))
        } yield {
            Privilege.find(By(Privilege.user, u.id), By(Privilege.account, bankAccount)) match {
              //update the existing privilege
              case Full(privilege) => {
                views.map(v => {
                    setPrivilegeFromView(privilege, v, true)
                })
                privilege.save
              }
              //there is no privilege to this user, so we create one
              case _ => {
                val privilege =
                Privilege.create.
                  user(u.id).
                  account(bankAccount)
                views.map(v => {
                    setPrivilegeFromView(privilege, v, true)
                })
                privilege.save
              }
            }
          }
      }
      case u: User => {
        logger.error("OBPUser instance not found, could not grant access ")
        Empty
      }
    }

  }
  def revokePermission(bankAccountId : String, view : View, user : User) : Box[Boolean] = {
    user match {
      case user:OBPUser =>
        for{
          bankAccount <- HostedAccount.find(By(HostedAccount.accountID, bankAccountId))
        } yield {
            Privilege.find(By(Privilege.user, user.id), By(Privilege.account, bankAccount)) match {
              case Full(privilege) => {
                setPrivilegeFromView(privilege, view, false)
                privilege.save
              }
              //there is no privilege to this user, so there is nothing to revoke
              case _ => true
            }
          }
      case u: User => {
        logger.error("OBPUser instance not found, could not revoke access ")
        Empty
      }
    }
  }

  def revokeAllPermission(bankAccountId : String, user : User) : Box[Boolean] = {
    user match {
      case user:OBPUser =>
        for{
          bankAccount <- HostedAccount.find(By(HostedAccount.accountID, bankAccountId))
        } yield {
            Privilege.find(By(Privilege.user, user.id), By(Privilege.account, bankAccount)) match {
              case Full(privilege) => {
                availableViews.foreach({view =>
                  setPrivilegeFromView(privilege, view, false)
                })
                privilege.save
              }
              //there is no privilege to this user, so there is nothing to revoke
              case _ => true
            }
          }
      case u: User => {
        logger.error("OBPUser instance not found, could not revoke access ")
        Empty
      }
    }
  }

  def views(bankAccountID : String) : Box[List[View]] = {
    Full(availableViews)
  }
}