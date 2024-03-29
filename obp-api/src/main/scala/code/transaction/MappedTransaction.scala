package code.transaction


import code.accountholders.AccountHolders
import code.api.util.{APIUtil, ApiTrigger}
import code.bankconnectors.LocalMappedConnector
import code.bankconnectors.LocalMappedConnector.getBankAccountCommon
import code.model._
import code.usercustomerlinks.UserCustomerLink
import code.util.Helper.MdcLoggable
import code.util._
import code.webhook.WebhookAction
import code.webhook.WebhookActor.{AccountNotificationWebhookRequest, RelatedEntity, WebhookRequest}
import com.openbankproject.commons.model._
import net.liftweb.common.Box.tryo
import net.liftweb.common._
import net.liftweb.mapper._

class MappedTransaction extends LongKeyedMapper[MappedTransaction] with IdPK with CreatedUpdated with TransactionUUID with MdcLoggable {

  def getSingleton = MappedTransaction

  object bank extends MappedString(this, 255)
  object account extends AccountIdString(this)
  object transactionId extends MappedString(this, 255) {
    override def defaultValue = APIUtil.generateUUID()
  }
  
  //TODO: review the need for this
  // (why do we need transactionUUID and transactionId - which is a UUID?)
  // This a history problem, previous we do not used transactionId as a UUID. But late we changed it to a UUID.
  // The UUID is used in V1.1, a long time ago version. So just leave it here now.
  object transactionUUID extends MappedUUID(this)
  object transactionType extends MappedString(this, 100)

  //amount/new balance use the smallest unit of currency! e.g. cents, yen, pence, øre, etc.
  object amount extends MappedLong(this)
  object newAccountBalance extends MappedLong(this)

  object currency extends MappedString(this, 10) // This should probably be 3 only characters long

  object tStartDate extends MappedDateTime(this)
  object tFinishDate extends MappedDateTime(this)

  object description extends MappedString(this, 2000)
  object chargePolicy extends MappedString(this, 32)
  
  object counterpartyAccountHolder extends MappedString(this, 255)
  object counterpartyAccountKind extends MappedString(this, 40)
  object counterpartyBankName extends MappedString(this, 100)
  object counterpartyNationalId extends MappedString(this, 40)
  
  @deprecated("use CPOtherAccountRoutingAddress instead. ","06/12/2017")
  object counterpartyAccountNumber extends MappedAccountNumber(this)
  
  @deprecated("use CPOtherAccountSecondaryRoutingAddress instead. ","06/12/2017")
  //this should eventually be calculated using counterpartyNationalId
  object counterpartyIban extends MappedString(this, 100)

  //The following are the fields from CounterpartyTrait, previous just save BankAccount to simulate the counterparty.
  //Now we save the real Counterparty data 
  //CP means CounterParty
  object CPCounterPartyId extends UUIDString(this)
  object CPOtherAccountProvider extends MappedString(this, 36)
  object CPOtherAccountRoutingScheme extends MappedString(this, 255)
  object CPOtherAccountRoutingAddress extends MappedString(this, 255)
  object CPOtherAccountSecondaryRoutingScheme extends MappedString(this, 255)
  object CPOtherAccountSecondaryRoutingAddress extends MappedString(this, 255)
  object CPOtherBankRoutingScheme extends MappedString(this, 255)
  object CPOtherBankRoutingAddress extends MappedString(this, 255)
  
  //This is a holder for storing data from a previous model version that wasn't set correctly
  //e.g. some previous models had counterpartyAccountNumber set to a string that was clearly
  //not a valid account number, though the string may have actually contained the account number
  //somewhere within it (e.g. "BLS 3020201 BLAH BLAH S/C 2014-05-22")
  //
  // We save information like this so that we can try to manually process it later.
  //
  // Keep in mind that changing the counterparty account number will require an update
  // to the corresponding counterparty metadata object!
  @deprecated
  object extraInfo extends DefaultStringField(this)


  override def theTransactionId = TransactionId(transactionId.get)
  override def theAccountId = AccountId(account.get)
  override def theBankId = BankId(bank.get)

  def getCounterpartyIban() = {
    val i = counterpartyIban.get
    if(i.isEmpty) None else Some(i)
  }
  
  //This method have the side affect, it will createOrget the counterparty-metaData and ger transaction- metadata in database
  //It is a expensive method, cause the perfermance issue somehow. 
  def toTransaction(account: BankAccount): Option[Transaction] = {
    val tBankId = theBankId
    val tAccId = theAccountId

    if (tBankId != account.bankId || tAccId != account.accountId) {
      logger.warn("Attempted to convert MappedTransaction to Transaction using unrelated existing BankAccount object")
      None
    } else {
      val transactionDescription = {
        val d = description.get
        if (d.isEmpty) None else Some(d)
      }

      val transactionCurrency = currency.get
      val transactionAmount = Helper.smallestCurrencyUnitToBigDecimal(amount.get, transactionCurrency)
      val newBalance = Helper.smallestCurrencyUnitToBigDecimal(newAccountBalance.get, transactionCurrency)

      val counterpartyName = counterpartyAccountHolder.get
      val otherAccountRoutingScheme = CPOtherAccountRoutingScheme.get
      val otherAccountRoutingAddress = CPOtherAccountRoutingAddress.get
      
      //TODO This method should be as general as possible, need move to general object, not here.
      //This method is expensive, it has the side affact, will getOrCreateMetadata 
      def createCounterparty(counterpartyId : String) = {
        new Counterparty(
          counterpartyId = counterpartyId,
          kind = counterpartyAccountKind.get,
          nationalIdentifier = counterpartyNationalId.get,
          counterpartyName = counterpartyAccountHolder.get,
          thisBankId = theBankId,
          thisAccountId = theAccountId,
          otherAccountProvider = counterpartyAccountHolder.get,
          otherBankRoutingAddress = Some(CPOtherBankRoutingAddress.get), 
          otherBankRoutingScheme = CPOtherBankRoutingScheme.get,
          otherAccountRoutingScheme = otherAccountRoutingScheme,
          otherAccountRoutingAddress = Some(otherAccountRoutingAddress),
          isBeneficiary = true
        )
      }

      //It is clear, we create the counterpartyId first, and assign it to metadata.counterpartyId and counterparty.counterpartyId manually
      val counterpartyId = APIUtil.createImplicitCounterpartyId(
        theBankId.value, 
        theAccountId.value, 
        counterpartyName,
        otherAccountRoutingScheme, 
        otherAccountRoutingAddress
      )
      val otherAccount = createCounterparty(counterpartyId)

      Some(new Transaction(
                            transactionUUID.get,
                            theTransactionId,
                            account,
                            otherAccount,
                            transactionType.get,
                            transactionAmount,
                            transactionCurrency,
                            transactionDescription,
                            tStartDate.get,
                            tFinishDate.get,
                            newBalance))
    }
  }
  
  def toTransactionCore(account: BankAccount): Option[TransactionCore] = {
    val tBankId = theBankId
    val tAccId = theAccountId
    
    if (tBankId != account.bankId || tAccId != account.accountId) {
      logger.warn("Attempted to convert MappedTransaction to Transaction using unrelated existing BankAccount object")
      None
    } else {
      val transactionDescription = {
        val d = description.get
        if (d.isEmpty) None else Some(d)
      }
      
      val transactionCurrency = currency.get
      val transactionAmount = Helper.smallestCurrencyUnitToBigDecimal(amount.get, transactionCurrency)
      val newBalance = Helper.smallestCurrencyUnitToBigDecimal(newAccountBalance.get, transactionCurrency)
      
      val counterpartyName = counterpartyAccountHolder.get
      val otherAccountRoutingScheme = CPOtherAccountRoutingScheme.get
      val otherAccountRoutingAddress = CPOtherAccountRoutingAddress.get
      
      //TODO This method should be as general as possible, need move to general object, not here.
      //This method is expensive, it has the side affact, will getOrCreateMetadata 
      def createCounterpartyCore(counterpartyId : String) = {
        new CounterpartyCore(
          counterpartyId = counterpartyId,
          kind = counterpartyAccountKind.get,
          counterpartyName = counterpartyName,
          thisBankId = theBankId,
          thisAccountId = theAccountId,
          otherAccountProvider = counterpartyAccountHolder.get,
          otherBankRoutingAddress = Some(CPOtherBankRoutingAddress.get),
          otherBankRoutingScheme = CPOtherBankRoutingScheme.get,
          otherAccountRoutingScheme = otherAccountRoutingScheme,
          otherAccountRoutingAddress = Some(otherAccountRoutingAddress),
          isBeneficiary = true
        )
      }
      
      //It is clear, we create the counterpartyId first, and assign it to metadata.counterpartyId and counterparty.counterpartyId manually
      val counterpartyId = APIUtil.createImplicitCounterpartyId(theBankId.value, theAccountId.value, counterpartyName, otherAccountRoutingScheme, otherAccountRoutingAddress)
      val otherAccount = createCounterpartyCore(counterpartyId)
      
      Some(TransactionCore(
        theTransactionId,
        account,
        otherAccount,
        transactionType.get,
        transactionAmount,
        transactionCurrency,
        transactionDescription,
        tStartDate.get,
        tFinishDate.get,
        newBalance))
    }
  }

  def toTransaction : Option[Transaction] = {
    APIUtil.getPropsValue("connector") match {
      case Full("akka_vDec2018") =>
        for {
          acc <- getBankAccountCommon(theBankId, theAccountId, None).map(_._1)
          transaction <- toTransaction(acc)
        } yield transaction
      case _ =>
        for {
          acc <- LocalMappedConnector.getBankAccountOld(theBankId, theAccountId)
          transaction <- toTransaction(acc)
        } yield transaction
    }
    
  }

}

object MappedTransaction extends MappedTransaction with LongKeyedMetaMapper[MappedTransaction] {
  override def dbIndexes = UniqueIndex(transactionId, bank, account) :: Index(bank, account) :: super.dbIndexes
  override def afterSave = List(
    t =>
      tryo {
        def getAmount(value: Long): String = {
          Helper.smallestCurrencyUnitToBigDecimal(value, t.currency.get).toString() + " " + t.currency.get
        }
        def sendMessage(apiTrigger: ApiTrigger): Unit = {
          if(apiTrigger.equals(ApiTrigger.onCreateTransaction)){

            val userIdCustomerIdPairs: List[(String, String)] = for{
              holder <- AccountHolders.accountHolders.vend.getAccountHolders(t.theBankId, t.theAccountId).toList
              userCustomerLink <- UserCustomerLink.userCustomerLink.vend.getUserCustomerLinksByUserId(holder.userId)
            } yield{
              (holder.userId, userCustomerLink.customerId)
            }

            val userIdCustomerIdsPairs: Map[String, List[String]] = userIdCustomerIdPairs.groupBy(_._1).map( a => (a._1,a._2.map(_._2)))
            val eventId = APIUtil.generateUUID()
            logger.debug("Before firing WebhookActor.AccountNotificationWebhookRequest.eventId: " + eventId)
            WebhookAction.accountNotificationWebhookRequest(
              AccountNotificationWebhookRequest(
                apiTrigger,
                eventId,
                t.theBankId.value,
                t.theAccountId.value,
                t.theTransactionId.value,
                userIdCustomerIdsPairs.map(pair => RelatedEntity(pair._1, pair._2)).toList
              )
            )
          } else{
            val eventId = APIUtil.generateUUID()
            logger.debug("Before firing WebhookActor.WebhookRequest.eventId: " + eventId)
            WebhookAction.webhookRequest(
              WebhookRequest(
                apiTrigger,
                eventId,
                t.theBankId.value,
                t.theAccountId.value,
                getAmount(t.amount.get),
                getAmount(t.newAccountBalance.get)
              )
            )
          }
        }

        t.amount.get match {
          case amount if amount > 0 =>
            sendMessage(ApiTrigger.onBalanceChange)
            sendMessage(ApiTrigger.onCreditTransaction)
            sendMessage(ApiTrigger.onCreateTransaction)
          case amount if amount < 0 =>
            sendMessage(ApiTrigger.onBalanceChange)
            sendMessage(ApiTrigger.onDebitTransaction)
            sendMessage(ApiTrigger.onCreateTransaction)
          case _  => 
            // Do not send anything
        }
        
    }
  )
}
