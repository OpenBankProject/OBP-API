package code.setup

import bootstrap.liftweb.ToSchemify
import code.accountholders.AccountHolders
import code.api.Constant.{CUSTOM_PUBLIC_VIEW_ID, SYSTEM_OWNER_VIEW_ID}
import code.api.util.APIUtil.isValidCustomViewName
import code.api.util.ErrorMessages._
import code.model._
import code.model.dataAccess._
import code.views.MapperViews.getExistingCustomView
import code.views.system.ViewDefinition
import code.views.{MapperViews, Views}
import com.openbankproject.commons.model._
import net.liftweb.common.{Failure, Full, ParamFailure}
import net.liftweb.mapper.MetaMapper
import net.liftweb.util.Helpers._

/**
 * Handles setting up views and permissions and account holders using ViewImpls, ViewPrivileges,
 * and MappedAccountHolder
 */
trait TestConnectorSetupWithStandardPermissions extends TestConnectorSetup {

  override protected def setAccountHolder(user: User, bankId : BankId, accountId : AccountId) = {
    AccountHolders.accountHolders.vend.getOrCreateAccountHolder(user, BankIdAccountId(bankId, accountId))
  }

  protected def getOrCreateSystemView(viewId: String) : View = {
    Views.views.vend.getOrCreateSystemView(viewId).openOrThrowException(attemptedToOpenAnEmptyBox)
  }
 
  protected def createPublicView(bankId: BankId, accountId: AccountId) : View = {
    Views.views.vend.getOrCreateCustomPublicView(bankId: BankId, accountId: AccountId, CUSTOM_PUBLIC_VIEW_ID).openOrThrowException(attemptedToOpenAnEmptyBox)
  }

  def createCustomRandomView(bankId: BankId, accountId: AccountId) : View = {
    {
      //we set the length is to 40, try to be difficult for scala tests create the same viewName.
      val viewName = "_" + randomString(40)
      val viewId = MapperViews.createViewIdByName(viewName)
      val description = randomString(40)

      if (!isValidCustomViewName(viewName)) {
        throw new RuntimeException(InvalidCustomViewFormat)
      }
      
      getExistingCustomView(bankId, accountId, viewId) match {
        case net.liftweb.common.Empty => {
          tryo {
            ViewDefinition.create.
              isSystem_(false).
              isFirehose_(false).
              name_(viewName).
              metadataView_(SYSTEM_OWNER_VIEW_ID).
              description_(description).
              view_id(viewId).
              isPublic_(false).
              bank_id(bankId.value).
              account_id(accountId.value).
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
              canSeeOtherAccountIBAN_(true).
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
              canSeeBankRoutingScheme_(true). //added following in V300
              canSeeBankRoutingAddress_(true).
              canSeeBankAccountRoutingScheme_(true).
              canSeeBankAccountRoutingAddress_(true).
              canSeeOtherBankRoutingScheme_(true).
              canSeeOtherBankRoutingAddress_(true).
              canSeeOtherAccountRoutingScheme_(true).
              canSeeOtherAccountRoutingAddress_(true).
              canAddTransactionRequestToOwnAccount_(false). //added following two for payments
              canAddTransactionRequestToAnyAccount_(false).
              canAddTransactionRequestToBeneficiary_(false).
              canSeeBankAccountCreditLimit_(true).
              saveMe
          }
        }
        case Full(v) => Full(v)
        case Failure(msg, t, c) => Failure(msg, t, c)
        case ParamFailure(x, y, z, q) => ParamFailure(x, y, z, q)
      }
    }.openOrThrowException(attemptedToOpenAnEmptyBox)
  }


  protected def wipeTestData(): Unit = {

    //returns true if the model should not be wiped after each test
    def exclusion(m : MetaMapper[_]) = {
      m == Nonce || m == Token || m == Consumer || m == AuthUser || m == ResourceUser
    }

    //empty the relational db tables after each test
    ToSchemify.models.filterNot(exclusion).foreach(_.bulkDelete_!!())
  }
}
