package code.api

import code.api.test.ServerSetup
import code.bankconnectors.Connector
import code.model.{AccountId, User}
import code.model.dataAccess.{ViewPrivileges, APIUser, Account, HostedBank}
import net.liftweb.util.Helpers._
import org.bson.types.ObjectId

trait PrivateUser2Accounts {

  self: ServerSetup with DefaultUsers =>

  /**
   * Adds some private accounts for obpuser2 to the DB so that not all accounts in the DB are public
   * (which is at the time of writing, the default created in ServerSetup)
   *
   * Also adds some public accounts to which user1 does not have owner access
   *
   * Also adds some private accounts for user1 that are not public
   */
  def accountTestsSpecificDBSetup() {

    val banks =  Connector.connector.vend.getBanks

    def generateAccounts(owner: User) = banks.flatMap(bank => {
      for { i <- 0 until 2 } yield {
        createAccountAndOwnerView(Some(owner), bank.bankId, AccountId(randomString(10)), randomString(10))
      }
    })

    //fake bank accounts

    //private accounts for obpuser1 (visible to obpuser1)
    generateAccounts(obpuser1)
    //private accounts for obpuser2 (not visible to obpuser1)
    generateAccounts(obpuser2)

    //public accounts owned by obpuser2 (visible to obpuser1)
    //create accounts
    val accounts = generateAccounts(obpuser2)
    //add public views
    accounts.foreach(acc => publicView(acc.bankId, acc.accountId))
  }

}
