package code.setup

import java.util.{Calendar, Date}

import code.accountholders.AccountHolders
import code.api.Constant.{SYSTEM_ACCOUNTANT_VIEW_ID, SYSTEM_AUDITOR_VIEW_ID, SYSTEM_OWNER_VIEW_ID}
import code.api.util.{APIUtil, OBPLimit, OBPOffset}
import code.bankconnectors.{Connector, LocalMappedConnector}
import code.model._
import code.transactionrequests.MappedTransactionRequest
import code.views.Views
import com.openbankproject.commons.model._

trait TestConnectorSetup {

  //TODO: implement these right here using Connector.connector.vend and get rid of specific connector setup files
  protected def createBank(id : String) : Bank
  @deprecated("Please use `createAccountAndOwnerView` instead, we need owner view for each account! ","2018-02-23")
  protected def createAccount(bankId: BankId, accountId : AccountId, currency : String) : BankAccount
  protected def createTransaction(account : BankAccount, startDate : Date, finishDate : Date)
  protected def createTransactionRequest(account: BankAccount): List[MappedTransactionRequest]
  protected def updateAccountCurrency(bankId: BankId, accountId : AccountId, currency : String) : BankAccount

  protected def createCounterparty(bankId: String, accountId: String, counterpartyObpRoutingAddress: String, isBeneficiary: Boolean, createdByUserId:String): CounterpartyTrait
  
  /**
    * This method, will do 4 things:
    * 1 create account
    * 2 create the `owner-view`
    * 3 grant the `owner-view` access to the User. 
    * 4 create the accountHolder for this account. 
    * @param accountOwner it is just a random user here, the user will have the access to the `owner view`
    * @param bankId one bankId
    * @param accountId one accountId
    * @param currency the currency to create account.
    *                 
    */
  final protected def createAccountRelevantResource(accountOwner: Option[User], bankId: BankId, accountId : AccountId, currency : String) : BankAccount = {
    val account = createAccount(bankId, accountId, currency) 
    val ownerView = createOwnerView(bankId, accountId)
    accountOwner.foreach(AccountHolders.accountHolders.vend.getOrCreateAccountHolder(_, BankIdAccountId(account.bankId, account.accountId)))
    accountOwner.foreach(Views.views.vend.grantAccessToCustomView(ViewIdBankIdAccountId(ViewId(ownerView.viewId.value), BankId(ownerView.bankId.value), AccountId(ownerView.accountId.value)), _))
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
    * This method will create lots of account relevant resources: accounts, views, accountAccesses, accountHolders
    * 1st: this will create some accounts,
    * 2rd: for each account, it will create 3 custom views: ownerView, PublicView and RandomView.
    *      and plus systemViews: owner, auditor, accountant.(created in boot, please check `create_system_views_at_boot` props.)
    *      So there will be 6 views for each account
    * 3rd: for each account create the account holder
    * 4th: for each account create the account access. 
    * 
    */
  final protected def createAccountRelevantResources(user: User, banks : Traversable[Bank]) : Traversable[BankAccount] = {
    val testAccountCurrency = "EUR"
    val accounts = banks.flatMap(bank => {
      for { i <- 0 until 2 } yield {
        createAccountRelevantResource(Some(user), bank.bankId, AccountId("testAccount"+i), testAccountCurrency)
      }
    })

    val systemOwnerView = getOrCreateSystemView(SYSTEM_OWNER_VIEW_ID)
    val systemAuditorView = getOrCreateSystemView(SYSTEM_AUDITOR_VIEW_ID)
    val systemAccountantView = getOrCreateSystemView(SYSTEM_ACCOUNTANT_VIEW_ID)
    
    accounts.foreach(account => {
      Views.views.vend.grantAccessToSystemView(account.bankId, account.accountId, systemOwnerView, user)
      Views.views.vend.grantAccessToSystemView(account.bankId, account.accountId, systemAuditorView, user)
      Views.views.vend.grantAccessToSystemView(account.bankId, account.accountId, systemAccountantView, user)
      
      val customPublicView = createPublicView(account.bankId, account.accountId) 
      Views.views.vend.grantAccessToCustomView(customPublicView.uid, user)
      
      val customRandomView = createCustomRandomView(account.bankId, account.accountId) 
      Views.views.vend.grantAccessToCustomView(customRandomView.uid, user)
    })

    accounts
  }

  final protected def createTransactions(accounts : Traversable[BankAccount]): List[Transaction] = {
    val NUM_TRANSACTIONS = 10

    val transactions = accounts.map(account => {

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
      LocalMappedConnector.getTransactionsLegacy(account.bankId, account.accountId, None, OBPOffset(0)::OBPLimit(NUM_TRANSACTIONS)::Nil)
    })
    transactions.flatten.map(_._1).flatten.toList
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

  protected def getOrCreateSystemView(name: String): View

  protected def createOwnerView(bankId: BankId, accountId: AccountId) : View
  protected def createPublicView(bankId: BankId, accountId: AccountId) : View
  protected def createCustomRandomView(bankId: BankId, accountId: AccountId) : View

  protected def setAccountHolder(user: User, bankId : BankId, accountId : AccountId)

  protected def wipeTestData()
}
