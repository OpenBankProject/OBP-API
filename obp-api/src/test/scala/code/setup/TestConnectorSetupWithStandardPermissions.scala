package code.setup

import bootstrap.liftweb.ToSchemify
import code.accountholders.AccountHolders
import code.api.util.ErrorMessages._
import code.model._
import code.model.dataAccess._
import code.views.Views
import com.openbankproject.commons.model._
import net.liftweb.mapper.MetaMapper
import net.liftweb.mongodb._
import net.liftweb.util.DefaultConnectionIdentifier
import net.liftweb.util.Helpers._

/**
 * Handles setting up views and permissions and account holders using ViewImpls, ViewPrivileges,
 * and MappedAccountHolder
 */
trait TestConnectorSetupWithStandardPermissions extends TestConnectorSetup {

  override protected def setAccountHolder(user: User, bankId : BankId, accountId : AccountId) = {
    AccountHolders.accountHolders.vend.getOrCreateAccountHolder(user, BankIdAccountId(bankId, accountId))
  }

  protected def getOrCreateSystemView(name: String) : View = {
    Views.views.vend.getOrCreateSystemView(name).openOrThrowException(attemptedToOpenAnEmptyBox)
  }
  protected def createOwnerView(bankId: BankId, accountId: AccountId ) : View = {
    Views.views.vend.getOrCreateOwnerView(bankId, accountId, randomString(3)).openOrThrowException(attemptedToOpenAnEmptyBox)
  }

  protected def createPublicView(bankId: BankId, accountId: AccountId) : View = {
    Views.views.vend.getOrCreateCustomPublicView(bankId, accountId, randomString(3)).openOrThrowException(attemptedToOpenAnEmptyBox)
  }

  protected def createCustomRandomView(bankId: BankId, accountId: AccountId) : View = {
    Views.views.vend.createCustomRandomView(bankId, accountId).openOrThrowException(attemptedToOpenAnEmptyBox)
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
    ToSchemify.modelsRemotedata.filterNot(exclusion).foreach(_.bulkDelete_!!())
  }
}
