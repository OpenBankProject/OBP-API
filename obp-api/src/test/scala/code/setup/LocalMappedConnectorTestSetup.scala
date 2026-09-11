package code.setup

import code.api.JedisMethod
import code.api.cache.Redis
import code.api.util.APIUtil
import code.api.util.ErrorMessages._
import code.entitlement.Entitlement
import code.metadata.counterparties.Counterparties
import code.model._
import code.model.dataAccess._
import code.transaction.MappedTransaction
import code.transactionrequests.MappedTransactionRequest
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.model._
import com.openbankproject.commons.model.enums.AccountRoutingScheme
import  com.openbankproject.commons.model.enums._
import net.liftweb.common.Box
import net.liftweb.util.Helpers._
import org.iban4j

import java.util.Date
import scala.util.Random
import code.api.util.DoobieUtil
import doobie.implicits._

trait LocalMappedConnectorTestSetup extends TestConnectorSetupWithStandardPermissions with MdcLoggable{
  //TODO: replace all these helpers with connector agnostic methods like createRandomBank
  // that call Connector.createBank etc.
  // (same in LocalRecordConnectorTestSetup)
  // Tests should simply use the currently selected connector
  override protected def createBank(id : String) : Bank = {
    //Note: we do not have the `UniqueIndex` for bank.id(permalink) yet, we but when we have getBankById endpoint,
    //Better set only create one bank for one id.
    MappedBank.findByBankId(BankId(id)).getOrElse(
        MappedBank.insert(
          bankId = id,
          fullBankName = randomString(5),
          shortBankName = randomString(5),
          logoURL = "", websiteURL = "", swiftBIC = "",
          nationalIdentifier = randomString(5),
          bankRoutingScheme = randomString(5),
          bankRoutingAddress = randomString(5),
          createdByUserId = ""))
  }

  override protected def createCounterparty(bankId: String, accountId: String, counterpartyObpRoutingAddress: String, isBeneficiary: Boolean, createdByUserId:String): CounterpartyTrait = {
    Counterparties.counterparties.vend.createCounterparty(
      createdByUserId = createdByUserId,
      thisBankId = bankId,
      thisAccountId = accountId,
      thisViewId = "",
      name = APIUtil.generateUUID(),
      otherAccountRoutingScheme = "OBP",
      otherAccountRoutingAddress = counterpartyObpRoutingAddress,
      otherBankRoutingScheme = "OBP",
      otherBankRoutingAddress = bankId,
      otherBranchRoutingScheme ="OBP",
      otherBranchRoutingAddress ="Berlin",
      isBeneficiary = isBeneficiary,
      otherAccountSecondaryRoutingScheme ="IBAN",
      otherAccountSecondaryRoutingAddress ="DE89 3704 0044 0532 0130 00",
      description = "String",
      currency = "String",
      bespoke = Nil
    ).openOrThrowException(attemptedToOpenAnEmptyBox)
  }

  override protected def createAccount(bankId: BankId, accountId : AccountId, currency : String) : BankAccount = {
    def getOrCreateRouting(scheme: String, address: String): Unit = {
      val existing = code.bankconnectors.DoobieBankAccountRoutingQueries.findByBankAccountScheme(bankId, accountId, scheme)
      if (existing.isEmpty) {
        try {
          code.bankconnectors.DoobieBankAccountRoutingQueries.create(bankId, accountId, scheme, address)
        } catch {
          case _: Throwable =>
        }
      }
      ()
    }

    getOrCreateRouting(AccountRoutingScheme.IBAN.toString, iban4j.Iban.random().toString())
    getOrCreateRouting("AccountId", accountId.value)

    val existingAccount = MappedBankAccount.find(bankId.value, accountId.value)
    existingAccount.openOr {
      try {
        MappedBankAccount.insert(
          bankId = bankId.value,
          accountId = accountId.value,
          accountCurrency = currency.toUpperCase,
          accountBalance = 900000000,
          holder = randomString(4),
          accountLastUpdate = now,
          accountName = randomString(4),
          accountNumber = randomString(4),
          accountLabel = randomString(4),
          branchId = randomString(4))
      } catch {
        // A concurrent creator won the unique index; read its row instead.
        case _: Throwable =>
          MappedBankAccount.find(bankId.value, accountId.value)
            .openOrThrowException(attemptedToOpenAnEmptyBox)
      }
    }
  }

  override protected def updateAccountCurrency(bankId: BankId, accountId : AccountId, currency : String) : BankAccount = {
     MappedBankAccount.setCurrency(bankId.value, accountId.value, currency.toUpperCase)
       .openOrThrowException(attemptedToOpenAnEmptyBox)
  }

  def addEntitlement(bankId: String, userId: String, roleName: String): Box[Entitlement] = {
    // Return a Box so we can handle errors later.
    Entitlement.entitlement.vend.addEntitlement(bankId, userId, roleName)
  }

  override protected def createTransaction(account: BankAccount, startDate: Date, finishDate: Date, isCompleted: Boolean) = {
    //ugly
    val mappedBankAccount = account.asInstanceOf[MappedBankAccount]

    val accountBalanceBefore = mappedBankAccount.accountBalance
    val transactionAmount = Random.nextInt(1000).toLong
    val accountBalanceAfter = accountBalanceBefore + transactionAmount

    MappedBankAccount.setBalance(mappedBankAccount.bank, mappedBankAccount.theAccountId,
      accountBalanceAfter)

    // Determine transaction status based on isCompleted parameter
    val transactionStatus = if (isCompleted) {
      TransactionRequestStatus.COMPLETED.toString
    } else {
      TransactionRequestStatus.INITIATED.toString
    }

    MappedTransaction.insert(
      bank = account.bankId.value,
      account = account.accountId.value,
      transactionType = randomString(5),
      tStartDate = startDate,
      tFinishDate = finishDate,
      currency = account.currency,
      amount = transactionAmount,
      newAccountBalance = accountBalanceAfter,
      description = randomString(5),
      counterpartyAccountHolder = randomString(5),
      counterpartyAccountKind = randomString(5),
      counterpartyAccountNumber = randomString(5),
      counterpartyBankName = randomString(5),
      counterpartyIban = randomString(5),
      counterpartyNationalId = randomString(5),
      cpOtherAccountRoutingScheme = randomString(5),
      cpOtherAccountRoutingAddress = randomString(5),
      cpOtherAccountSecondaryRoutingScheme = randomString(5),
      cpOtherAccountSecondaryRoutingAddress = randomString(5),
      cpOtherBankRoutingScheme = randomString(5),
      cpOtherBankRoutingAddress = randomString(5),
      status = transactionStatus)  // Use determined transaction status
      .toTransaction.orNull
  }

  override protected def createTransactionRequest(account: BankAccount): List[MappedTransactionRequest] = {
  
    def request(amount: String, status: String) = MappedTransactionRequest.insert(
      MappedTransactionRequest.empty.copy(
        transactionRequestId = APIUtil.generateUUID(),
        transactionType = "SANDBOX_TAN",
        fromBankId = account.bankId.value,
        fromAccountId = account.accountId.value,
        toBankId = randomString(5),
        toAccountId = randomString(5),
        bodyValueCurrency = account.currency,
        bodyValueAmount = amount,
        bodyDescription = "This is a description..",
        status = status,
        startDate = now,
        endDate = now))

    val firstRequest = request("10", "COMPLETED")

    val secondRequest = request("1001", "INITIATED")
    
    List(firstRequest, secondRequest)
  }
  
  override protected def wipeTestData() = {
    //returns true if the model should not be wiped after each test
    // Every table is listed explicitly: no entity is a Lift Mapper any more, so there is no model
    // loop to clear them. The auth tables (nonce, token, consumer, resourceuser, authuser) are
    // deliberately absent - DefaultUsers manages those and the suites authenticate against them.
    // AtmTableResetIsolationTest fails if a non-auth table is forgotten.
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

    
    // Delete only THIS shard's namespaced Redis keys. Each parallel shard uses a distinct
    // api_instance_id (OBP_API_INSTANCE_ID) -> distinct getGlobalCacheNamespacePrefix, so a
    // pattern-scoped delete avoids wiping other shards' rate-limit/cache state. A plain FLUSHDB
    // would clear the whole shared DB and cause cross-shard rate-limit/cache flakiness.
    try {
      Redis.deleteKeysByPattern(code.api.Constant.getGlobalCacheNamespacePrefix + "*")
    } catch {
      case e: Throwable =>
        logger.warn("------------| Redis issue during flushing data |------------")
        logger.warn(e)
    }
  }
}
