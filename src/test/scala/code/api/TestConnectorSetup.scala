package code.api

import java.util.{Calendar, Date}

import code.bankconnectors.{Connector, OBPLimit, OBPOffset}
import code.metadata.counterparties.{CounterpartyTrait, MappedCounterparty}
import code.model._
import net.liftweb.util.Helpers._

trait TestConnectorSetup {

  //TODO: implement these right here using Connector.connector.vend and get rid of specific connector setup files
  protected def createBank(id : String) : Bank
  protected def createAccount(bankId: BankId, accountId : AccountId, currency : String) : BankAccount
  protected def createTransaction(account : BankAccount, startDate : Date, finishDate : Date)

  protected def createCounterparty(bankId: String, accountId: String, iban: String, isBeneficiary: Boolean, counterpartyId: String): CounterpartyTrait

  final protected def createAccountAndOwnerView(accountOwner: Option[User], bankId: BankId, accountId : AccountId, currency : String) : BankAccount = {
    val account = createAccount(bankId, accountId, currency)
    val ownerView = createOwnerView(bankId, accountId)
    accountOwner.foreach(owner => {
      grantAccessToView(owner, ownerView)
    })
    account
  }

  final protected def createBanks() : Traversable[Bank] = {
    for{i <- 0 until 3} yield {
      createBank("testBank"+i)
    }
  }

  final protected def createAccounts(banks : Traversable[Bank]) : Traversable[BankAccount] = {
    val accounts = banks.flatMap(bank => {
      for { i <- 0 until 2 } yield {
        createAccountAndOwnerView(None, bank.bankId, AccountId(randomString(4)), randomString(4))
      }
    })

    accounts.foreach(account => {
      //create public view and another random view (owner view has already been created
      createPublicView(account.bankId, account.accountId)
      createRandomView(account.bankId, account.accountId)
    })

    accounts
  }

  final protected def createTransactions(accounts : Traversable[BankAccount]) = {
    val NUM_TRANSACTIONS = 10

    accounts.foreach(account => {

      def add10Minutes(d: Date): Date = {
        val calendar = Calendar.getInstance
        calendar.setTime(d)
        calendar.add(Calendar.MINUTE, 10)
        calendar.getTime
      }

      val initialDate: Date = {
        val calendar = Calendar.getInstance
        calendar.setTime(new Date())
        calendar.add(Calendar.YEAR, -1)
        calendar.getTime
      }

      object InitialDateFactory{
        val calendar = Calendar.getInstance
        calendar.setTime(initialDate)
        var i = 0
        def date: Date = {
          //makes it so the transactions aren't created in date order
          if(i % 2 == 0) calendar.add(Calendar.HOUR, 10)
          else calendar.add(Calendar.HOUR, -5)
          i = i + 1
          calendar.getTime
        }
      }

      for(i <- 0 until NUM_TRANSACTIONS){
        val postedDate = InitialDateFactory.date
        val completedDate = add10Minutes(postedDate)
        createTransaction(account, postedDate, completedDate)
      }

      //load all transactions for the account to generate the counterparty metadata
      Connector.connector.vend.getTransactions(account.bankId, account.accountId, OBPOffset(0), OBPLimit(NUM_TRANSACTIONS))
    })
  }

  final protected def createPaymentTestBank() : Bank =
    createBank("payment-test-bank")

  protected def createOwnerView(bankId: BankId, accountId: AccountId) : View
  protected def createPublicView(bankId: BankId, accountId: AccountId) : View
  protected def createRandomView(bankId: BankId, accountId: AccountId) : View

  protected def setAccountHolder(user: User, bankId : BankId, accountId : AccountId)
  protected def grantAccessToAllExistingViews(user : User)
  protected def grantAccessToView(user : User, view : View)

  protected def wipeTestData()
}
