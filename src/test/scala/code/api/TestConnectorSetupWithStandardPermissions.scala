package code.api

import bootstrap.liftweb.ToSchemify
import code.model.dataAccess._
import code.model._
import net.liftweb.mapper.MetaMapper
import net.liftweb.mongodb._
import net.liftweb.util.Helpers._

/**
 * Handles setting up views and permissions and account holders using ViewImpls, ViewPrivileges,
 * and MappedAccountHolder
 */
trait TestConnectorSetupWithStandardPermissions extends TestConnectorSetup {

  override protected def setAccountHolder(user: User, bankId : BankId, accountId : AccountId) = {
    MappedAccountHolder.createMappedAccountHolder(user.apiId.value, bankId.value, accountId.value, "TestConnectorSetupWithStandardPermissions")
  }

  override protected def grantAccessToAllExistingViews(user : User) = {
    ViewImpl.findAll.foreach(v => {
      ViewPrivileges.create.
        view(v).
        user(user.apiId.value).
        save
    })
  }

  override protected def grantAccessToView(user : User, view : View) = {
    val viewImpl = ViewImpl.find(view.uid)
    ViewPrivileges.create.
      view(viewImpl.get). //explodes if no viewImpl exists, but that's okay, the test should fail then
      user(user.apiId.value).
      save
  }

  protected def createOwnerView(bankId: BankId, accountId: AccountId) : View = {
    ViewImpl.createAndSaveOwnerView(bankId, accountId, randomString(3))
  }

  protected def createPublicView(bankId: BankId, accountId: AccountId) : View = {
    ViewImpl.createAndSaveDefaultPublicView(bankId, accountId, randomString(3))
  }

  protected def createRandomView(bankId: BankId, accountId: AccountId) : View = {
    ViewImpl.create.
      name_(randomString(5)).
      description_(randomString(3)).
      permalink_(randomString(3)).
      isPublic_(false).
      bankPermalink(bankId.value).
      accountPermalink(accountId.value).
      usePrivateAliasIfOneExists_(false).
      usePublicAliasIfOneExists_(false).
      hideOtherAccountMetadataIfAlias_(false).
      canSeeTransactionThisBankAccount_(true).
      canSeeTransactionOtherBankAccount_(true).
      canSeeTransactionMetadata_(true).
      canSeeTransactionDescription_(true).
      canSeeTransactionAmount_(true).
      canSeeTransactionType_(true).
      canSeeTransactionCurrency_(true).
      canSeeTransactionStartDate_(true).
      canSeeTransactionFinishDate_(true).
      canSeeTransactionBalance_(true).
      canSeeComments_(true).
      canSeeOwnerComment_(true).
      canSeeTags_(true).
      canSeeImages_(true).
      canSeeBankAccountOwners_(true).
      canSeeBankAccountType_(true).
      canSeeBankAccountBalance_(true).
      canSeeBankAccountCurrency_(true).
      canSeeBankAccountLabel_(true).
      canSeeBankAccountNationalIdentifier_(true).
      canSeeBankAccountSwift_bic_(true).
      canSeeBankAccountIban_(true).
      canSeeBankAccountNumber_(true).
      canSeeBankAccountBankName_(true).
      canSeeBankAccountBankPermalink_(true).
      canSeeOtherAccountNationalIdentifier_(true).
      canSeeOtherAccountSWIFT_BIC_(true).
      canSeeOtherAccountIBAN_ (true).
      canSeeOtherAccountBankName_(true).
      canSeeOtherAccountNumber_(true).
      canSeeOtherAccountMetadata_(true).
      canSeeOtherAccountKind_(true).
      canSeeMoreInfo_(true).
      canSeeUrl_(true).
      canSeeImageUrl_(true).
      canSeeOpenCorporatesUrl_(true).
      canSeeCorporateLocation_(true).
      canSeePhysicalLocation_(true).
      canSeePublicAlias_(true).
      canSeePrivateAlias_(true).
      canAddMoreInfo_(true).
      canAddURL_(true).
      canAddImageURL_(true).
      canAddOpenCorporatesUrl_(true).
      canAddCorporateLocation_(true).
      canAddPhysicalLocation_(true).
      canAddPublicAlias_(true).
      canAddPrivateAlias_(true).
      canCreateCounterparty_(true).
      canDeleteCorporateLocation_(true).
      canDeletePhysicalLocation_(true).
      canEditOwnerComment_(true).
      canAddComment_(true).
      canDeleteComment_(true).
      canAddTag_(true).
      canDeleteTag_(true).
      canAddImage_(true).
      canDeleteImage_(true).
      canAddWhereTag_(true).
      canSeeWhereTag_(true).
      canDeleteWhereTag_(true).
      saveMe
  }

  protected def wipeTestData(): Unit = {

    //drop the mongo Database after each test
    MongoDB.getDb(DefaultMongoIdentifier).foreach(_.dropDatabase())

    //returns true if the model should not be wiped after each test
    def exclusion(m : MetaMapper[_]) = {
      m == Nonce || m == Token || m == Consumer || m == OBPUser || m == APIUser
    }

    //empty the relational db tables after each test
    ToSchemify.models.filterNot(exclusion).foreach(_.bulkDelete_!!())
  }
}
