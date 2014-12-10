package code.bankconnectors

import code.model._
import net.liftweb.common.Box

object DummyConnector extends Connector {
  override type AccountType = BankAccount

  //gets a particular bank handled by this connector
  override def getBank(bankId: BankId): Box[Bank] = ???

  override def getTransaction(bankId: BankId, accountID: AccountId, transactionId: TransactionId): Box[Transaction] = ???

  override def getOtherBankAccount(bankId: BankId, accountID: AccountId, otherAccountID: String): Box[OtherBankAccount] = ???

  override def getTransactions(bankId: BankId, accountID: AccountId, queryParams: OBPQueryParam*): Box[List[Transaction]] = ???

  //gets the users who are the legal owners/holders of the account
  override def getAccountHolders(bankId: BankId, accountID: AccountId): Set[User] = ???

  override def getOtherBankAccounts(bankId: BankId, accountID: AccountId): List[OtherBankAccount] = ???

  override protected def makePaymentImpl(fromAccount: DummyConnector.AccountType, toAccount: DummyConnector.AccountType, amt: BigDecimal): Box[TransactionId] = ???

  override def getPhysicalCards(user: User): Set[PhysicalCard] = ???

  override def getPhysicalCardsForBank(bankId: BankId, user: User): Set[PhysicalCard] = ???

  override protected def getBankAccountType(bankId: BankId, accountId: AccountId): Box[DummyConnector.AccountType] = ???

  //gets banks handled by this connector
  override def getBanks: List[Bank] = ???
}
