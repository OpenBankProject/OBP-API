package code.setup

import code.bankconnectors.Connector
import code.model.{AccountId, User}
import net.liftweb.util.Helpers._
import code.api.util.ErrorMessages._

trait PrivateUser2AccountsAndSetUpWithTestData {

  self: ServerSetupWithTestData with DefaultUsers =>

  /**
   * Adds some private accounts for authuser2 to the DB so that not all accounts in the DB are public
   * (which is at the time of writing, the default created in ServerSetup)
   *
   * Also adds some public accounts to which user1 does not have owner access
   *
   * Also adds some private accounts for user1 that are not public
   */
  def accountTestsSpecificDBSetup() {

    val banks =  Connector.connector.vend.getBanks(None).map(_._1).openOrThrowException(attemptedToOpenAnEmptyBox)

    def generateAccounts(owner: User) = banks.flatMap(bank => {
      for { i <- 0 until 2 } yield {
        createAccountAndOwnerView(Some(owner), bank.bankId, AccountId(randomString(10)), randomString(10))
      }
    })

    //fake bank accounts

    //private accounts for authuser1 (visible to authuser1)
    generateAccounts(resourceUser1)
    //private accounts for authuser2 (not visible to authuser1)
    generateAccounts(resourceUser2)

    //public accounts owned by authuser2 (visible to authuser1)
    //create accounts
    val accounts = generateAccounts(resourceUser2)
    //add public views
    accounts.foreach(acc => createPublicView(acc.bankId, acc.accountId))
  }

}
