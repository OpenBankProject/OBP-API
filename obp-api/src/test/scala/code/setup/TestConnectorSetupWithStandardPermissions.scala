package code.setup

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
import net.liftweb.util.Helpers._
import code.api.util.DoobieUtil
import doobie.implicits._


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
            ViewDefinition.insert(ViewDefinition(
              isSystem_ = false,
              isFirehose_ = false,
              name_ = viewName,
              metadataView_ = SYSTEM_OWNER_VIEW_ID,
              description_ = description,
              view_id = viewId,
              isPublic_ = false,
              bank_id = bankId.value,
              account_id = accountId.value,
              usePrivateAliasIfOneExists_ = false,
              usePublicAliasIfOneExists_ = false,
              hideOtherAccountMetadataIfAlias_ = false))
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

    // Every table is listed explicitly: no entity is a Lift Mapper any more, so there is no model
    // loop to clear them. The auth tables are deliberately absent - DefaultUsers manages those.
    // AtmTableResetIsolationTest fails if this is forgotten.
    DoobieUtil.runUpdate(sql"DELETE FROM mappedatm".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM mappednarrative".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM mappedcomment".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM mappedtag".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM mappedwheretag".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM mappedtransactionimage".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM producttag".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM connector_trace".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM consent_item".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM jsonschemavalidation".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM mappedtransactiontype".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM etag".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM authenticationtypevalidation".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM userlocks".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM connectormethod".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM apicollectionendpoint".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM featuredapicollection".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM consentauthcontext".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM mappeduserauthcontext".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM userinitaction".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM accountidmapping".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM transactionidmapping".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM mappedcustomeridmapping".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM mappedbankaccountdata".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM apicollection".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM mappedbadloginattempt".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM bankaccountrouting".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM mappedfxrate".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM transactionrequestreasons".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM apiproductattribute".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM mappedcardattribute".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM atmattribute".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM bankattribute".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM counterpartyattribute".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM regulatedentityattribute".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM mappedproductattribute".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM mappedcustomerattribute".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM mappedaccountattribute".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM mappedtransactionattribute".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM transactionrequestattribute".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM mappedtaxresidence".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM customerlink".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM counterpartylimit".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM customeraccountlink".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM mappedusercustomerlink".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM mappedcrmevent".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM mappeduserrefreshes".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM payeelookup".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM metricsarchiverun".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM open_corridor_fee_accrual".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM utilitypaymentcallback".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM webuiprops".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM groupofroles".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM organisation".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM attributedefinition".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM jobscheduler".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM bankaccountbalance".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM endpointtag".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM apiproduct".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM amqp_bank_broker".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM productfee".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM message_outbox".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM openidconnecttoken".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM useragreement".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM userinvitation".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM methodrouting".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM AccountAccessRequest".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM BulkPayment".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM BulkBatchReference".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM mappedkycstatus".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM mappedkycmedia".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM mappedkyccheck".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM mappedkycdocument".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM mappedsocialmedia".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM reaction".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM chatmessage".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM participant".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM chatroom".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM mappedproductcollection".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM mappedproductcollectionitem".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM directdebit".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM standingorder".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM mappedaccountwebhook".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM bankaccountnotificationwebhook".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM systemaccountnotificationwebhook".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM mappedscope".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM mappedaccountapplication".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM mappedcustomeraddress".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM mappedentitlementrequest".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM mappedcustomerdependant".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM mappedcounterpartybespoke".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM expectedchallengeanswer".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM userattribute".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM regulatedentity".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM routingscheme".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM banksupportedroutingscheme".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM abacrule".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM endpointmapping".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM dynamicentityindex".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM mappedmeetinginvitee".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM mappedmeeting".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM mappedcustomermessage".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM mappedtransactionrequesttypecharge".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM pinreset".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM mappedphysicalcard".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM doubleentrybooktransaction".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM dynamicendpoint".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM mappedconnectormetric".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM mappedentitlement".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM ratelimiting".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM mappedproduct".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM mappedbranch".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM mapperaccountholders".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM dynamicmessagedoc".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM dynamicresourcedoc".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM dynamicdataaccess".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM dynamicentity".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM dynamicdata".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM viewpermission".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM accountaccess".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM mandate".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM mandateprovision".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM signatorypanel".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM signingbasket".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM signingbasketpayment".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM signingbasketconsent".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM consentrequest".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM mappedcounterparty".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM mappedcounterpartymetadata".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM mappedcounterpartywheretag".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM mappedbank".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM mappedtransaction".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM mappedtransactionrequest".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM mappedcustomer".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM metric".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM metricarchive".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM mappedconsent".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM mappedbankaccount".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM viewdefinition".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM mappeduserauthcontextupdate".update.run)

  }
}
