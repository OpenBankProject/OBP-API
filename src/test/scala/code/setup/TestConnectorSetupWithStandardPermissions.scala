package code.setup

import bootstrap.liftweb.ToSchemify
import code.accountholder.AccountHolders
import code.api.util.APIUtil
import code.model._
import code.model.dataAccess._
import code.views.Views
import net.liftweb.mapper.MetaMapper
import net.liftweb.mongodb._
import net.liftweb.util.Helpers._
import net.liftweb.util.{DefaultConnectionIdentifier, Props}
import code.api.util.ErrorMessages._

/**
 * Handles setting up views and permissions and account holders using ViewImpls, ViewPrivileges,
 * and MappedAccountHolder
 */
trait TestConnectorSetupWithStandardPermissions extends TestConnectorSetup {

  override protected def setAccountHolder(user: User, bankId : BankId, accountId : AccountId) = {
    AccountHolders.accountHolders.vend.getOrCreateAccountHolder(user, BankIdAccountId(bankId, accountId))
  }

  override protected def grantAccessToAllExistingViews(user : User) = {
    Views.views.vend.grantAccessToAllExistingViews(user)
  }

  override protected def grantAccessToView(user : User, view : View) = {
    Views.views.vend.grantAccessToView(user, view)
  }

  protected def createOwnerView(bankId: BankId, accountId: AccountId ) : View = {
    Views.views.vend.getOrCreateOwnerView(bankId, accountId, randomString(3)).openOrThrowException(attemptedToOpenAnEmptyBox)
  }

  protected def createPublicView(bankId: BankId, accountId: AccountId) : View = {
    Views.views.vend.getOrCreatePublicView(bankId, accountId, randomString(3)).openOrThrowException(attemptedToOpenAnEmptyBox)
  }

  protected def createRandomView(bankId: BankId, accountId: AccountId) : View = {
    Views.views.vend.createRandomView(bankId, accountId).openOrThrowException(attemptedToOpenAnEmptyBox)
  }


  protected def wipeTestData(): Unit = {

    //drop the mongo Database after each test
    MongoDB.getDb(DefaultConnectionIdentifier).foreach(_.dropDatabase())

    //returns true if the model should not be wiped after each test
    def exclusion(m : MetaMapper[_]) = {
      m == Nonce || m == Token || m == Consumer || m == AuthUser || m == ResourceUser
    }

    //empty the relational db tables after each test
    ToSchemify.models.filterNot(exclusion).foreach(_.bulkDelete_!!())
    if (!APIUtil.getPropsAsBoolValue("remotedata.enable", false)) {
    ToSchemify.modelsRemotedata.filterNot(exclusion).foreach(_.bulkDelete_!!())
    } else {
      Views.views.vend.bulkDeleteAllPermissionsAndViews()
      AccountHolders.accountHolders.vend.bulkDeleteAllAccountHolders()
    }
  }
}
