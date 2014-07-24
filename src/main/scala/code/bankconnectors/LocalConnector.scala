package code.bankconnectors

import net.liftweb.common.Box
import scala.concurrent.ops.spawn
import code.model._
import code.model.dataAccess._
import net.liftweb.mapper.By
import net.liftweb.common.Loggable
import org.bson.types.ObjectId
import net.liftweb.util.Helpers._
import net.liftweb.util.Props
import com.mongodb.QueryBuilder
import code.metadata.counterparties.Metadata
import scala.Some
import net.liftweb.common.Full
import com.tesobe.model.UpdateBankAccount
import scala.Some
import net.liftweb.common.Full
import com.tesobe.model.UpdateBankAccount

object LocalConnector extends Connector with Loggable {

  def getBank(permalink: String): Box[Bank] =
    for{
      bank <- getHostedBank(permalink)
    } yield {
      createBank(bank)
    }

  //gets banks handled by this connector
  def getBanks : List[Bank] =
    HostedBank.findAll.map(createBank)

  def getBankAccount(bankPermalink : String, accountId : String) : Box[BankAccount] = {
    for{
      bank <- getHostedBank(bankPermalink)
      account <- bank.getAccount(accountId)
    } yield Account toBankAccount account
  }

  def getAllPublicAccounts() : List[BankAccount] = {
    //TODO: do this more efficiently

    val bankAndAccountPermalinks : List[(String, String)] =
      ViewImpl.findAll(By(ViewImpl.isPublic_, true)).map(v =>
        (v.bankPermalink.get, v.accountPermalink.get)
      ).distinct //we remove duplicates here

    bankAndAccountPermalinks.map {
      case (bankPermalink, accountPermalink) => {
        getBankAccount(bankPermalink, accountPermalink)
      }
    }.flatten
  }

  def getPublicBankAccounts(bank : Bank) : List[BankAccount] = {
    //TODO: do this more efficiently

    val accountPermalinks : List[String] =
      ViewImpl.findAll(By(ViewImpl.isPublic_, true), By(ViewImpl.bankPermalink, bank.permalink)).map(v => {
        v.accountPermalink.get
      }).distinct //we remove duplicates here

    accountPermalinks.map(accPerma => {
      getBankAccount(bank.permalink, accPerma)
    }).flatten
  }
  
  /**
   * @param user
   * @return the bank accounts the @user can see (public + private if @user is Full, public if @user is Empty)
   */
  def getAllAccountsUserCanSee(user : Box[User]) : List[BankAccount] = {
    user match {
      case Full(theuser) => {
        //TODO: get rid of this match
        theuser match {
          case u : APIUser => {
            //TODO: this could be quite a bit more efficient...

            val publicViewBankAndAccountPermalinks = ViewImpl.findAll(By(ViewImpl.isPublic_, true)).map(v => {
              (v.bankPermalink.get, v.accountPermalink.get)
            }).distinct

            val userPrivileges : List[ViewPrivileges] = ViewPrivileges.findAll(By(ViewPrivileges.user, u))
            val userNonPublicViews : List[ViewImpl] = userPrivileges.map(_.view.obj).flatten.filter(!_.isPublic)

            val nonPublicViewBankAndAccountPermalinks = userNonPublicViews.map(v => {
              (v.bankPermalink.get, v.accountPermalink.get)
            }).distinct //we remove duplicates here

            val visibleBankAndAccountPermalinks =
              (publicViewBankAndAccountPermalinks ++ nonPublicViewBankAndAccountPermalinks).distinct

            visibleBankAndAccountPermalinks.map {
              case(bankPermalink, accountPermalink) => {
                getBankAccount(bankPermalink, accountPermalink)
              }
            }.flatten
          }
          case _ => {
            logger.error("APIUser instance not found, could not get all accounts user can see")
            Nil
          }
        }

      }
      case _ => getAllPublicAccounts()
    }
  }

  /**
  * @param user
  * @return the bank accounts at @bank the @user can see (public + private if @user is Full, public if @user is Empty)
  */
  //TODO: remove Box in result
  def getAllAccountsUserCanSee(bank: Bank, user : Box[User]) : Box[List[BankAccount]] = {
    user match {
      case Full(theuser) => {

        //TODO: get rid of this match
        theuser match {
          case u : APIUser => {
            //TODO: this could be quite a bit more efficient...

            val publicViewBankAndAccountPermalinks = ViewImpl.findAll(By(ViewImpl.isPublic_, true),
              By(ViewImpl.bankPermalink, bank.permalink)).map(v => {
              (v.bankPermalink.get, v.accountPermalink.get)
            }).distinct

            val userPrivileges : List[ViewPrivileges] = ViewPrivileges.findAll(By(ViewPrivileges.user, u))
            val userNonPublicViews : List[ViewImpl] = userPrivileges.map(_.view.obj).flatten.filter(v => {
              !v.isPublic && v.bankPermalink.get == bank.permalink
            })

            val nonPublicViewBankAndAccountPermalinks = userNonPublicViews.map(v => {
              (v.bankPermalink.get, v.accountPermalink.get)
            }).distinct //we remove duplicates here

            val visibleBankAndAccountPermalinks =
              (publicViewBankAndAccountPermalinks ++ nonPublicViewBankAndAccountPermalinks).distinct

            Full(visibleBankAndAccountPermalinks.map {
              case(bankPermalink, accountPermalink) => {
                getBankAccount(bankPermalink, accountPermalink)
              }
            }.flatten)
          }
          case _ => {
            logger.error("APIUser instance not found, could not get all accounts user can see")
            Full(Nil)
          }
        }
      }
      case _ => Full(getPublicBankAccounts(bank))
    }
  }

  /**
  * @return the bank accounts where the user has at least access to a non public view (is_public==false)
  */
  def getNonPublicBankAccounts(user : User) :  Box[List[BankAccount]] = {

    val accountsList =
      //TODO: get rid of this match statement
      user match {
        case u : APIUser => {
          //TODO: get rid of dependency on ViewPrivileges, ViewImpl
          //TODO: make this more efficient
          val userPrivileges : List[ViewPrivileges] = ViewPrivileges.findAll(By(ViewPrivileges.user, u))
          val userNonPublicViews : List[ViewImpl] = userPrivileges.map(_.view.obj).flatten.filter(!_.isPublic)

          val nonPublicViewBankAndAccountPermalinks = userNonPublicViews.map(v => {
            (v.bankPermalink.get, v.accountPermalink.get)
          }).distinct //we remove duplicates here

          nonPublicViewBankAndAccountPermalinks.map {
            case(bankPermalink, accountPermalink) => {
              getBankAccount(bankPermalink, accountPermalink)
            }
          }
        }
        case u: User => {
          logger.error("APIUser instance not found, could not find the non public accounts")
          Nil
        }
      }
    Full(accountsList.flatten)
  }

    /**
  * @return the bank accounts where the user has at least access to a non public view (is_public==false) for a specific bank
  */
  def getNonPublicBankAccounts(user : User, bankID : String) :  Box[List[BankAccount]] = {
    user match {
      case u : APIUser => {

        val userPrivileges : List[ViewPrivileges] = ViewPrivileges.findAll(By(ViewPrivileges.user, u))
        val userNonPublicViewsForBank : List[ViewImpl] =
          userPrivileges.map(_.view.obj).flatten.filter(v => !v.isPublic && v.bankPermalink.get == bankID)

        val nonPublicViewAccountPermalinks = userNonPublicViewsForBank.
          map(_.accountPermalink.get).distinct //we remove duplicates here

        Full(nonPublicViewAccountPermalinks.map {
          getBankAccount(bankID, _)
        }.flatten)
      }
      case u : User => {
        logger.error("APIUser instance not found, could not find the non public account ")
        Full(Nil)
      }
    }
  }

  def getModeratedOtherBankAccount(bankID: String, accountID : String, otherAccountID : String)
  (moderate: OtherBankAccount => Option[ModeratedOtherBankAccount]): Box[ModeratedOtherBankAccount] = {

    /**
     * In this implementation (for legacy reasons), the "otherAccountID" is actually the mongodb id of the
     * "other account metadata" object.
     */

      for{
        objId <- tryo{ new ObjectId(otherAccountID) }
        otherAccountmetadata <- {
          //"otherAccountID" is actually the mongodb id of the other account metadata" object.
          val query = QueryBuilder.start("_id").is(objId).get()
          Metadata.find(query)
        }
      } yield{
          val otherAccountFromTransaction : OBPAccount = OBPEnvelope.find("obp_transaction.other_account.metadata",otherAccountmetadata.id.is) match {
            case Full(envelope) => envelope.obp_transaction.get.other_account.get
            case _ => {
              logger.warn("no other account found")
              OBPAccount.createRecord
            }
          }
          moderate(createOtherBankAccount(bankID, accountID, otherAccountmetadata, otherAccountFromTransaction)).get
        }
  }

  def getModeratedOtherBankAccounts(bankID: String, accountID : String)
  (moderate: OtherBankAccount => Option[ModeratedOtherBankAccount]): Box[List[ModeratedOtherBankAccount]] = {

    /**
     * In this implementation (for legacy reasons), the "otherAccountID" is actually the mongodb id of the
     * "other account metadata" object.
     */

    val query = QueryBuilder.start("originalPartyBankId").is(bankID).put("originalPartyAccountId").is(accountID).get

    val moderatedCounterparties = Metadata.findAll(query).map(meta => {
      //for legacy reasons some of the data about the "other account" are stored only on the transactions
      //so we need first to get a transaction that match to have the rest of the data
      val otherAccountFromTransaction : OBPAccount = OBPEnvelope.find("obp_transaction.other_account.holder",meta.holder.get) match {
        case Full(envelope) => {
          envelope.obp_transaction.get.other_account.get
        }
        case _ => {
          logger.warn(s"envelope not found for other account ${meta.id.get}")
          OBPAccount.createRecord
        }
      }
      moderate(createOtherBankAccount(bankID, accountID, meta, otherAccountFromTransaction))
    })

    Full(moderatedCounterparties.flatten)
  }

  def getModeratedTransactions(bankId: String, accountId: String, queryParams: OBPQueryParam*)
  (moderate: Transaction => ModeratedTransaction): Box[List[ModeratedTransaction]] = {
    for{
      rawTransactions <- getTransactions(accountId, bankId, queryParams: _*)
    } yield rawTransactions.map(moderate)
  }

  def getModeratedTransaction(id : String, bankId : String, accountId : String)
  (moderate: Transaction => ModeratedTransaction) : Box[ModeratedTransaction] = {
    for{
      transaction <- getTransaction(id,bankId,accountId)
    } yield moderate(transaction)
  }


  private def getTransactions(bankId: String, accountId: String, queryParams: OBPQueryParam*): Box[List[Transaction]] = {
      logger.debug("getTransactions for " + bankId + "/" + accountId)
      for{
        bank <- getHostedBank(bankId)
        account <- bank.getAccount(accountId)
      } yield {
        updateAccountTransactions(bank, account)
        account.envelopes(queryParams: _*).flatMap(createTransaction(_, account))
      }
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
    private def createTransaction(env: OBPEnvelope, theAccount: Account): Option[Transaction] = {
    val transaction: OBPTransaction = env.obp_transaction.get
    val otherAccount_ = transaction.other_account.get

    val thisBankAccount = Account.toBankAccount(theAccount)
    val id = env.id.is.toString()
    val uuid = id

    //slight hack required: otherAccount id is, for legacy reasons, the mongodb id of its metadata object
    //so we have to find that
    val query = QueryBuilder.start("originalPartyBankId").is(theAccount.bankPermalink).
      put("originalPartyAccountId").is(theAccount.permalink.get).
      put("holder").is(otherAccount_.holder.get).get

    Metadata.find(query) match {
      case Full(m) => {
        val otherAccount = new OtherBankAccount(
          id = m.id.get.toString,
          label = otherAccount_.holder.get,
          nationalIdentifier = otherAccount_.bank.get.national_identifier.get,
          swift_bic = None, //TODO: need to add this to the json/model
          iban = Some(otherAccount_.bank.get.IBAN.get),
          number = otherAccount_.number.get,
          bankName = otherAccount_.bank.get.name.get,
          kind = "",
          originalPartyBankId = theAccount.bankPermalink,
          originalPartyAccountId = theAccount.permalink.get
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


  private def createOtherBankAccount(originalPartyBankId: String, originalPartyAccountId: String,
    otherAccount : Metadata, otherAccountFromTransaction : OBPAccount) : OtherBankAccount = {
    new OtherBankAccount(
      id = otherAccount.id.is.toString,
      label = otherAccount.holder.get,
      nationalIdentifier = otherAccountFromTransaction.bank.get.national_identifier.get,
      swift_bic = None, //TODO: need to add this to the json/model
      iban = Some(otherAccountFromTransaction.bank.get.IBAN.get),
      number = otherAccountFromTransaction.number.get,
      bankName = otherAccountFromTransaction.bank.get.name.get,
      kind = "",
      originalPartyBankId = originalPartyBankId,
      originalPartyAccountId = originalPartyAccountId
    )
  }

  private def getHostedBank(permalink : String) : Box[HostedBank] = {
    for{
      bank <- HostedBank.find("permalink", permalink) ?~ {"bank " + permalink + " not found"}
    } yield bank
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
}