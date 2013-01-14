/** 
Open Bank Project - Transparency / Social Finance Web Application
Copyright (C) 2011, 2012, TESOBE / Music Pictures Ltd

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

import code.model.traits._
import code.model.implementedTraits._
import net.liftweb.common.{ Box, Empty, Full }
import net.liftweb.mongodb.BsonDSL._
import net.liftweb.json.JsonDSL._
import net.liftweb.common.Loggable
import code.model.dataAccess.OBPEnvelope.OBPQueryParam
import net.liftweb.mapper.By
import net.liftweb.mongodb.MongoDB
import com.mongodb.BasicDBList
import java.util.ArrayList
import org.bson.types.ObjectId

object LocalStorage extends MongoDBLocalStorage

trait LocalStorage extends Loggable {
  def getModeratedTransactions(permalink: String, bankPermalink: String)
  	(moderate: Transaction => ModeratedTransaction): List[ModeratedTransaction] = {
    val rawTransactions = getTransactions(permalink, bankPermalink) getOrElse Nil
    rawTransactions.map(moderate)
  }

  def getModeratedTransactions(permalink: String, bankPermalink: String, queryParams: OBPQueryParam*)
  	(moderate: Transaction => ModeratedTransaction): List[ModeratedTransaction] = {
    val rawTransactions = getTransactions(permalink, bankPermalink, queryParams: _*) getOrElse Nil
    rawTransactions.map(moderate)
  }
  
  def getTransactions(permalink: String, bankPermalink: String, queryParams: OBPQueryParam*) : Box[List[Transaction]] = {
    val envelopesForAccount = (acc: Account) => acc.envelopes(queryParams: _*)
    getTransactions(permalink, bankPermalink, envelopesForAccount)
  }

  def getTransactions(permalink: String, bankPermalink: String): Box[List[Transaction]] = {
    val envelopesForAccount = (acc: Account) => acc.allEnvelopes
    getTransactions(permalink, bankPermalink, envelopesForAccount)
  }

  def getTransactions(bank: String, account: String, envelopesForAccount: Account => List[OBPEnvelope]): Box[List[Transaction]]

  def getBank(name: String): Box[Bank]

  def getBankAccounts(bank: Bank): Set[BankAccount]
  
  def correctBankAndAccount(bank: String, account: String): Boolean

  def getAccount(bankpermalink: String, account: String): Box[Account]
  def getTransaction(id : String, bankPermalink : String, accountPermalink : String) : Box[Transaction]  
}

class MongoDBLocalStorage extends LocalStorage {
  private def createTransaction(env: OBPEnvelope, theAccount: Account): Transaction =
  {
    import net.liftweb.json.JsonDSL._
    val transaction: OBPTransaction = env.obp_transaction.get
    val thisAccount = transaction.this_account
    val otherAccount_ = transaction.other_account.get
    val otherUnmediatedHolder = otherAccount_.holder.get

    val thisBankAccount = Account.toBankAccount(theAccount)
          
    val oAccs = theAccount.otherAccounts.get
    val oAccOpt = oAccs.find(o => {
      otherUnmediatedHolder.equals(o.holder.get)
    })

    val oAcc = oAccOpt getOrElse {
      OtherAccount.createRecord
    }

    val id = env.id.is.toString()
    val oSwiftBic = None
    val otherAccount = new OtherBankAccountImpl(
        id_ = "", 
        label_ = otherAccount_.holder.get,
        nationalIdentifier_ = otherAccount_.bank.get.national_identifier.get,
        swift_bic_ = None, //TODO: need to add this to the json/model
        iban_ = Some(otherAccount_.bank.get.IBAN.get),
        number_ = otherAccount_.number.get,
        bankName_ = "", //TODO: need to add this to the json/model
        metadata_ = new OtherBankAccountMetadataImpl(oAcc.publicAlias.get, oAcc.privateAlias.get, oAcc.moreInfo.get,
        oAcc.url.get, oAcc.imageUrl.get, oAcc.openCorporatesUrl.get))
    val metadata = new TransactionMetadataImpl(env.narrative.get, env.obp_comments.objs,
      (text => env.narrative(text).save), env.addComment _)
    val transactionType = env.obp_transaction.get.details.get.type_en.get
    val amount = env.obp_transaction.get.details.get.value.get.amount.get
    val currency = env.obp_transaction.get.details.get.value.get.currency.get
    val label = None
    val startDate = env.obp_transaction.get.details.get.posted.get
    val finishDate = env.obp_transaction.get.details.get.completed.get
    val balance = env.obp_transaction.get.details.get.new_balance.get.amount.get
    new TransactionImpl(id, thisBankAccount, otherAccount, metadata, transactionType, amount, currency,
      label, startDate, finishDate, balance)
  }
  
  def getTransactions(permalink: String, bankPermalink: String, envelopesForAccount: Account => List[OBPEnvelope]): Box[List[Transaction]] =
  {
      logger.debug("getTransactions for " + bankPermalink + "/" + permalink)
      HostedBank.find("permalink",bankPermalink) match {
        case Full (bank) => bank.getAccount(permalink) match {
            case Full(account) => {
              val envs = envelopesForAccount(account)
              Full(envs.map(createTransaction(_, account)))
            }
            case _ => Empty
          }
        case _ => Empty
      }      
  }

  def getBank(permalink: String): Box[Bank] = 
    HostedBank.find("permalink", permalink).
      map( bank => new BankImpl(bank.id.toString, bank.name.get, permalink))
  
  
  def allBanks : List[Bank] = 
  HostedBank.findAll.
    map(bank => new BankImpl(bank.id.toString, bank.name.get, bank.permalink.get))
  
  def getBankAccounts(bank: Bank): Set[BankAccount] = {
    val bankId = new ObjectId(bank.id)
    val rawAccounts = Account.findAll(("bankID" -> bankId)).toSet
    rawAccounts.map(Account.toBankAccount)
  }
  
  //check if the bank and the accounts exist in the database
  def correctBankAndAccount(bank: String, account: String): Boolean =
    HostedBank.find("permalink",bank) match {
        case Full(bank) => bank.isAccount(account)
        case _ => false
      }
  def getAccount(bankpermalink: String, account: String): Box[Account] =
    HostedBank.find("permalink",bankpermalink) match {
      case Full (bank) => bank.getAccount(account)
      case _ => Empty
    }
    
  def getTransaction(id : String, bankPermalink : String, accountPermalink : String) : Box[Transaction] = 
  {
    for{
      bank <- HostedBank.find("permalink",bankPermalink)
      account  <- bank.getAccount(accountPermalink)
      ifTransactionsIsInAccount <- Full(account.transactionsForAccount.put("_id").is(new ObjectId(id)).get)
      envelope <- OBPEnvelope.find(ifTransactionsIsInAccount)
    } yield createTransaction(envelope,account)
  }

  def getAllAccounts() : List[Account] = Account.findAll
  
  def getAllPublicAccounts() : List[Account] = Account.findAll("anonAccess", true)
}