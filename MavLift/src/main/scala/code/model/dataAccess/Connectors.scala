package code.model.dataAccess

import code.model.traits._
import code.model.implementedTraits._
import net.liftweb.common.{ Box, Empty, Full }
import net.liftweb.mongodb.BsonDSL._
import net.liftweb.json.JsonDSL._
import net.liftweb.common.Loggable
import code.model.dataAccess.OBPEnvelope.OBPQueryParam

object LocalStorage extends MongoDBLocalStorage

trait LocalStorage extends Loggable {
  def getModeratedTransactions(bank: String, account: String)
  	(moderate: Transaction => ModeratedTransaction): List[ModeratedTransaction] = {
    val rawTransactions = getTransactions(bank, account) getOrElse Nil
    rawTransactions.map(moderate)
  }

  def getModeratedTransactions(bank: String, account: String, queryParams: OBPQueryParam*)
  	(moderate: Transaction => ModeratedTransaction): List[ModeratedTransaction] = {
    val rawTransactions = getTransactions(bank, account, queryParams: _*) getOrElse Nil
    rawTransactions.map(moderate)
  }
  
  def getTransactions(bank: String, account: String, queryParams: OBPQueryParam*) : Box[List[Transaction]] = {
    val envelopesForAccount = (acc: Account) => acc.envelopes(queryParams: _*)
    getTransactions(bank, account, envelopesForAccount)
  }

  def getTransactions(bank: String, account: String): Box[List[Transaction]] = {
    val envelopesForAccount = (acc: Account) => acc.allEnvelopes
    getTransactions(bank, account, envelopesForAccount)
  }

  def getTransactions(bank: String, account: String, envelopesForAccount: Account => List[OBPEnvelope]): Box[List[Transaction]]

  def getBank(name: String): Box[Bank]

  def correctBankAndAccount(bank: String, account: String): Boolean

  def getAccount(bankpermalink: String, account: String): Box[Account]
}

class MongoDBLocalStorage extends LocalStorage {

  //For the moment there is only one bank 
  //but for multiple banks we should look in the
  //data base to check if the bank exists or not
  def getTransactions(bank: String, account: String, envelopesForAccount: Account => List[OBPEnvelope]): Box[List[Transaction]] =
    {
      logger.debug("getTransactions for " + bank + "/" + account)
      def createTransaction(env: OBPEnvelope, theAccount: Box[Account]): Transaction =
        {
          import net.liftweb.json.JsonDSL._
          val transaction: OBPTransaction = env.obp_transaction.get
          val thisAccount = transaction.this_account
          val otherAccount_ = transaction.other_account.get
          val otherUnmediatedHolder = otherAccount_.holder.get

          val oAccs = theAccount.get.otherAccounts.get
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
          val metadata = new TransactionMetadataImpl(env.narrative.get, env.obp_comments.get.map(new CommentImpl(_)),
            (text => env.narrative(text).save), env.addComment _)
          val transactionType = env.obp_transaction.get.details.get.type_en.get
          val amount = env.obp_transaction.get.details.get.value.get.amount.get
          val currency = env.obp_transaction.get.details.get.value.get.currency.get
          val label = None
          val startDate = env.obp_transaction.get.details.get.posted.get
          val finishDate = env.obp_transaction.get.details.get.completed.get
          val balance = env.obp_transaction.get.details.get.new_balance.get.amount.get
          new TransactionImpl(id, null, otherAccount, metadata, transactionType, amount, currency,
            label, startDate, finishDate, balance)
        }
      Account.find(("permalink" -> account) ~ ("bankPermalink" -> bank)) match {
        case Full(account) => {
          val envs = envelopesForAccount(account)
          Full(envs.map(createTransaction(_, Full(account))))
        }
        case _ => Empty
      }
    }

  def getBank(name: String): Box[Bank] =
    {
      if (name == "postbank")
        Full(new BankImpl("01", "Post Bank", Set((getTransactions("postbank", "tesobe")).get(0).thisAccount)))
      else
        Empty
    }
  //check if the bank and the accounts exist in the database
  def correctBankAndAccount(bank: String, account: String): Boolean =
    {
      Account.find(("permalink" -> account) ~ ("bankPermalink" -> bank)) match {
        case Full(account) => true
        case _ => false
      }
    }
  def getAccount(bankpermalink: String, account: String): Box[Account] =
    Account.find(("permalink" -> account) ~ ("bankPermalink" -> bankpermalink))
}