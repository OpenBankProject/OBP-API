package code.setup

import java.util.{Calendar, Date}

import code.api.util.APIUtil
import code.bankconnectors.{Connector, OBPLimit, OBPOffset}
import code.metadata.counterparties.CounterpartyTrait
import code.model._
import net.liftweb.util.Helpers._

trait TestConnectorSetup {

  //TODO: implement these right here using Connector.connector.vend and get rid of specific connector setup files
  protected def createBank(id : String) : Bank
  @deprecated("Please use `createAccountAndOwnerView` instead, we need owner view for each account! ","2018-02-23")
  protected def createAccount(bankId: BankId, accountId : AccountId, currency : String) : BankAccount
  protected def createTransaction(account : BankAccount, startDate : Date, finishDate : Date)
  protected def createTransactionRequest(account: BankAccount)
  protected def updateAccountCurrency(bankId: BankId, accountId : AccountId, currency : String) : BankAccount

  protected def createCounterparty(bankId: String, accountId: String, accountRoutingAddress: String, otherAccountRoutingScheme: String, isBeneficiary: Boolean, createdByUserId:String): CounterpartyTrait
  
  /**
    * This method, will do three things:
    * 1 create account by (bankId, accountId, currency), note: no user here.
    * 2 create the `owner-view` for the created account, note: no user here.
    * 3 grant the `owner-view` access to the User. 
    * @param accountOwner it is just a random user here, the user will have the access to the `owner view`
    * @param bankId one bankId
    * @param accountId one accountId
    * @param currency the currency to create account.
    *                 
    * @return this method will return a bankAccount, which contains `owner view` and grant the access to the input user.
    *         
    */
  final protected def createAccountAndOwnerView(accountOwner: Option[User], bankId: BankId, accountId : AccountId, currency : String) : BankAccount = {
    val account = createAccount(bankId, accountId, currency) //In the test, account has no relevant with owner.Just need bankId, accountId and currency. 
    val ownerView = createOwnerView(bankId, accountId)//You can create the `owner-view` for the created account.
    accountOwner.foreach(grantAccessToView(_, ownerView)) //grant access to one user. (Here, this user is not owner for the account, just grant `owner-view` to the user)
    account
  }

  final protected def createBanks() : Traversable[Bank] = {
    val defaultBank = createBank(APIUtil.defaultBankId)
    val banks = for{i <- 0 until 4} yield {
      if (i==3) createBank("testBankWithoutBranches") else createBank("testBank"+i)
    }
    banks ++ Seq(defaultBank)
  }
  
  /**
    * This will create the test accounts for testBanks.
    * It will also create ownerView, PublicView and RandomView ...
    */
  final protected def createAccounts(user: User, banks : Traversable[Bank]) : Traversable[BankAccount] = {
    val testAccountCurrency = "EUR"
    val accounts = banks.flatMap(bank => {
      for { i <- 0 until 2 } yield {
        createAccountAndOwnerView(Some(user), bank.bankId, AccountId("testAccount"+i), testAccountCurrency)
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
  
  final protected def createTransactionRequests(accounts : Traversable[BankAccount]) = {
    val NUM_TRANSACTIONS = 10

    accounts.foreach(account => {
      for(i <- 0 until NUM_TRANSACTIONS)
        createTransactionRequest(account)
      }
    )
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
