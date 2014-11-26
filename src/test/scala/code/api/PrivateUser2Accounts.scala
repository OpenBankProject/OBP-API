package code.api

import code.api.test.ServerSetup
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

    val banks =  HostedBank.findAll

    def generateAccounts() = banks.flatMap(bank => {
      for { i <- 0 until 2 } yield {
        val acc = Account.createRecord.
          accountBalance(0).
          holder(randomString(10)).
          accountNumber(randomString(10)).
          kind(randomString(10)).
          accountName(randomString(10)).
          permalink(randomString(10)).
          bankID(new ObjectId(bank.id.get.toString)).
          accountLabel(randomString(10)).
          accountCurrency(randomString(10)).
          save
        acc
      }
    })

    //fake bank accounts
    val privateAccountsForUser1 = generateAccounts()
    val privateAccountsForUser2 = generateAccounts()
    val publicAccounts = generateAccounts()

    def addViews(accs : List[Account], ownerUser : APIUser, addPublicView : Boolean) = {
      accs.foreach(account => {
        val owner = ownerView(account.bankId, account.accountId)
        ViewPrivileges.create.
          view(owner).
          user(ownerUser).
          save

        if(addPublicView) {
          publicView(account.bankId, account.accountId)
        }
      })
    }
    addViews(privateAccountsForUser1, obpuser1, false)
    addViews(privateAccountsForUser2, obpuser2, false)
    addViews(publicAccounts, obpuser2, true)
  }

}
