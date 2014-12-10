package code.api

import java.util.Date

import code.model.{BankAccount, AccountId, BankId, Bank}

/**
 * A placeholder for test setup. It should be implemented in order to create banks, accounts, and transactions
 * for the connector being used by the api.
 */
trait DummyTestSetup extends TestConnectorSetupWithStandardPermissions {

  val TODO = throw new NotImplementedError("Implement methods to set up test data (and rename this trait).")

  protected def createBank(id : String) : Bank = TODO
  protected def createAccount(bankId: BankId, accountId : AccountId, currency : String) : BankAccount = TODO
  protected def createTransaction(account : BankAccount, startDate : Date, finishDate : Date) = TODO

}
