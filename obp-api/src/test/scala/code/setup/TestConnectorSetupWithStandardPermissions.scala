package code.setup

import bootstrap.liftweb.ToSchemify
import code.accountholders.AccountHolders
import code.api.Constant._
import code.api.util.APIUtil.isValidCustomViewName
import code.api.util.ErrorMessages._
import code.model._
import code.model.dataAccess._
import code.views.MapperViews.getExistingCustomView
import code.views.system.{ViewDefinition, ViewPermission}
import code.views.{MapperViews, Views}
import com.openbankproject.commons.model._
import net.liftweb.common.{Failure, Full, ParamFailure}
import net.liftweb.mapper.MetaMapper
import net.liftweb.util.Helpers._


trait TestConnectorSetupWithStandardPermissions extends TestConnectorSetup {

  final val SYSTEM_CUSTOM_VIEW_PERMISSION_TEST = List(
    CAN_SEE_TRANSACTION_THIS_BANK_ACCOUNT,
    CAN_SEE_TRANSACTION_OTHER_BANK_ACCOUNT,
    CAN_SEE_TRANSACTION_METADATA,
    CAN_SEE_TRANSACTION_DESCRIPTION,
    CAN_SEE_TRANSACTION_AMOUNT,
    CAN_SEE_TRANSACTION_TYPE,
    CAN_SEE_TRANSACTION_CURRENCY,
    CAN_SEE_TRANSACTION_START_DATE,
    CAN_SEE_TRANSACTION_FINISH_DATE,
    CAN_SEE_TRANSACTION_BALANCE,
    CAN_SEE_COMMENTS,
    CAN_SEE_OWNER_COMMENT,
    CAN_SEE_TAGS,
    CAN_SEE_IMAGES,
    CAN_SEE_BANK_ACCOUNT_OWNERS,
    CAN_SEE_BANK_ACCOUNT_TYPE,
    CAN_SEE_BANK_ACCOUNT_BALANCE,
    CAN_SEE_BANK_ACCOUNT_CURRENCY,
    CAN_SEE_BANK_ACCOUNT_LABEL,
    CAN_SEE_BANK_ACCOUNT_NATIONAL_IDENTIFIER,
    CAN_SEE_BANK_ACCOUNT_SWIFT_BIC,
    CAN_SEE_BANK_ACCOUNT_IBAN,
    CAN_SEE_BANK_ACCOUNT_NUMBER,
    CAN_SEE_BANK_ACCOUNT_BANK_NAME,
    CAN_SEE_BANK_ACCOUNT_BANK_PERMALINK,
    CAN_SEE_OTHER_ACCOUNT_NATIONAL_IDENTIFIER,
    CAN_SEE_OTHER_ACCOUNT_SWIFT_BIC,
    CAN_SEE_OTHER_ACCOUNT_IBAN,
    CAN_SEE_OTHER_ACCOUNT_BANK_NAME,
    CAN_SEE_OTHER_ACCOUNT_NUMBER,
    CAN_SEE_OTHER_ACCOUNT_METADATA,
    CAN_SEE_OTHER_ACCOUNT_KIND,
    CAN_SEE_MORE_INFO,
    CAN_SEE_URL,
    CAN_SEE_IMAGE_URL,
    CAN_SEE_OPEN_CORPORATES_URL,
    CAN_SEE_CORPORATE_LOCATION,
    CAN_SEE_PHYSICAL_LOCATION,
    CAN_SEE_PUBLIC_ALIAS,
    CAN_SEE_PRIVATE_ALIAS,
    CAN_ADD_MORE_INFO,
    CAN_ADD_URL,
    CAN_ADD_IMAGE_URL,
    CAN_ADD_OPEN_CORPORATES_URL,
    CAN_ADD_CORPORATE_LOCATION,
    CAN_ADD_PHYSICAL_LOCATION,
    CAN_ADD_PUBLIC_ALIAS,
    CAN_ADD_PRIVATE_ALIAS,
    CAN_DELETE_CORPORATE_LOCATION,
    CAN_DELETE_PHYSICAL_LOCATION,
    CAN_EDIT_OWNER_COMMENT,
    CAN_ADD_COMMENT,
    CAN_DELETE_COMMENT,
    CAN_ADD_TAG,
    CAN_DELETE_TAG,
    CAN_ADD_IMAGE,
    CAN_DELETE_IMAGE,
    CAN_ADD_WHERE_TAG,
    CAN_SEE_WHERE_TAG,
    CAN_DELETE_WHERE_TAG,
    CAN_SEE_BANK_ROUTING_SCHEME,
    CAN_SEE_BANK_ROUTING_ADDRESS,
    CAN_SEE_BANK_ACCOUNT_ROUTING_SCHEME,
    CAN_SEE_BANK_ACCOUNT_ROUTING_ADDRESS,
    CAN_SEE_OTHER_BANK_ROUTING_SCHEME,
    CAN_SEE_OTHER_BANK_ROUTING_ADDRESS,
    CAN_SEE_OTHER_ACCOUNT_ROUTING_SCHEME,
    CAN_SEE_OTHER_ACCOUNT_ROUTING_ADDRESS,
    CAN_SEE_BANK_ACCOUNT_CREDIT_LIMIT,
    CAN_SEE_TRANSACTION_STATUS
  )

  
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
          val view = tryo {
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
              saveMe
          }
          view.map(ViewPermission.resetViewPermissions(
            _,
            SYSTEM_CUSTOM_VIEW_PERMISSION_TEST
          ))

          view
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
