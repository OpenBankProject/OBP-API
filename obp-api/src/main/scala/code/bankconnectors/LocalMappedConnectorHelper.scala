package code.bankconnectors

import code.api.Constant._
import code.api.util.APIUtil._
import code.api.util.ErrorMessages._
import code.api.util._
import code.bankconnectors.LocalMappedConnector._
import code.transactionrequests._
import code.util.Helper
import code.util.Helper._
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.model._
import com.openbankproject.commons.model.enums.TransactionRequestStatus
import com.openbankproject.commons.model.enums.TransactionRequestTypes
import com.openbankproject.commons.model.enums.PaymentServiceTypes
import net.liftweb.common._
import net.liftweb.json.Serialization.write
import net.liftweb.json.{NoTypeHints, Serialization}
import scala.concurrent._
import scala.language.postfixOps



//Try to keep LocalMappedConnector smaller, so put comment methods in new file.
object LocalMappedConnectorHelper extends MdcLoggable {
  
  def createTransactionRequestBGInternal(
    initiator: User,
    paymentServiceType: PaymentServiceTypes,
    transactionRequestType: TransactionRequestTypes,
    transactionRequestBody: BerlinGroupTransactionRequestCommonBodyJson,
    callContext: Option[CallContext]
  ) = {
    for {
      transDetailsSerialized <- NewStyle.function.tryons(s"$UnknownError Can not serialize in request Json ", 400, callContext) {
        write(transactionRequestBody)(Serialization.formats(NoTypeHints))
      }

      //for Berlin Group, the account routing address is the IBAN.
      fromAccountIban = transactionRequestBody.debtorAccount.iban
      toAccountIban = transactionRequestBody.creditorAccount.iban

      (fromAccount, callContext) <- NewStyle.function.getBankAccountByIban(fromAccountIban, callContext)
      (ibanChecker, callContext) <- NewStyle.function.validateAndCheckIbanNumber(toAccountIban, callContext)
      _ <- Helper.booleanToFuture(invalidIban, cc = callContext) {
        ibanChecker.isValid == true
      }
      (toAccount, callContext) <- NewStyle.function.getToBankAccountByIban(toAccountIban, callContext)

      viewId = ViewId(SYSTEM_INITIATE_PAYMENTS_BERLIN_GROUP_VIEW_ID)
      fromBankIdAccountId = BankIdAccountId(fromAccount.bankId, fromAccount.accountId)
      view <- NewStyle.function.checkAccountAccessAndGetView(viewId, fromBankIdAccountId, Full(initiator), callContext)
      _ <- Helper.booleanToFuture(InsufficientAuthorisationToCreateTransactionRequest, cc = callContext) {
        view.canAddTransactionRequestToAnyAccount
      }

      (paymentLimit, callContext) <- Connector.connector.vend.getPaymentLimit(
        fromAccount.bankId.value,
        fromAccount.accountId.value,
        viewId.value,
        transactionRequestType.toString,
        transactionRequestBody.instructedAmount.currency,
        initiator.userId,
        initiator.name,
        callContext
      ) map { i =>
        (unboxFullOrFail(i._1, callContext, s"$InvalidConnectorResponseForGetPaymentLimit ", 400), i._2)
      }

      paymentLimitAmount <- NewStyle.function.tryons(s"$InvalidConnectorResponseForGetPaymentLimit. payment limit amount ${paymentLimit.amount} not convertible to number", 400, callContext) {
        BigDecimal(paymentLimit.amount)
      }

      //We already checked the value in API level.
      transactionAmount = BigDecimal(transactionRequestBody.instructedAmount.amount)

      _ <- Helper.booleanToFuture(s"$InvalidJsonValue the payment amount is over the payment limit($paymentLimit)", 400, callContext) {
        transactionAmount <= paymentLimitAmount
      }

      // Prevent default value for transaction request type (at least).
      _ <- Helper.booleanToFuture(s"From Account Currency is ${fromAccount.currency}, but Requested instructedAmount.currency is: ${transactionRequestBody.instructedAmount.currency}", cc = callContext) {
        transactionRequestBody.instructedAmount.currency == fromAccount.currency
      }

      // Get the threshold for a challenge. i.e. over what value do we require an out of Band security challenge to be sent?
      (challengeThreshold, callContext) <- Connector.connector.vend.getChallengeThreshold(
        fromAccount.bankId.value,
        fromAccount.accountId.value,
        viewId.value,
        transactionRequestType.toString,
        transactionRequestBody.instructedAmount.currency,
        initiator.userId, initiator.name,
        callContext
      ) map { i =>
        (unboxFullOrFail(i._1, callContext, s"$InvalidConnectorResponseForGetChallengeThreshold ", 400), i._2)
      }
      challengeThresholdAmount <- NewStyle.function.tryons(s"$InvalidConnectorResponseForGetChallengeThreshold. challengeThreshold amount ${challengeThreshold.amount} not convertible to number", 400, callContext) {
        BigDecimal(challengeThreshold.amount)
      }
      status <- getStatus(
        challengeThresholdAmount,
        transactionAmount,
        TransactionRequestType(transactionRequestType.toString)
      )
      (chargeLevel, callContext) <- Connector.connector.vend.getChargeLevelC2(
        BankId(fromAccount.bankId.value),
        AccountId(fromAccount.accountId.value),
        viewId,
        initiator.userId,
        initiator.name,
        transactionRequestType.toString,
        transactionRequestBody.instructedAmount.currency,
        transactionRequestBody.instructedAmount.amount,
        toAccount.accountRoutings,
        Nil,
        callContext
      ) map { i =>
        (unboxFullOrFail(i._1, callContext, s"$InvalidConnectorResponseForGetChargeLevel ", 400), i._2)
      }

      chargeLevelAmount <- NewStyle.function.tryons(s"$InvalidNumber chargeLevel.amount: ${chargeLevel.amount} can not be transferred to decimal !", 400, callContext) {
        BigDecimal(chargeLevel.amount)
      }

      chargeValue <- getChargeValue(chargeLevelAmount, transactionAmount)
      charge = TransactionRequestCharge("Total charges for completed transaction", AmountOfMoney(transactionRequestBody.instructedAmount.currency, chargeValue))

      // Always create a new Transaction Request
      transactionRequest <- Future {
        val transactionRequest = TransactionRequests.transactionRequestProvider.vend.createTransactionRequestImpl210(
          TransactionRequestId(generateUUID()),
          TransactionRequestType(transactionRequestType.toString),
          fromAccount,
          toAccount,
          TransactionRequestCommonBodyJSONCommons(
            transactionRequestBody.instructedAmount,
            ""
          ),
          transDetailsSerialized,
          status.toString,
          charge,
          "", // chargePolicy is not used in BG so far.
          Some(paymentServiceType.toString),
          Some(transactionRequestBody)
        )
        transactionRequest
      } map {
        unboxFullOrFail(_, callContext, s"$InvalidConnectorResponseForCreateTransactionRequestImpl210")
      }

      // If no challenge necessary, create Transaction immediately and put in data store and object to return
      (transactionRequest, callContext) <- status match {
        case TransactionRequestStatus.COMPLETED =>
          for {
            (createdTransactionId, callContext) <- NewStyle.function.makePaymentv210(
              fromAccount,
              toAccount,
              transactionRequest.id,
              TransactionRequestCommonBodyJSONCommons(
                transactionRequestBody.instructedAmount,
                "" //BG no description so far
              ),
              transactionAmount,
              "", //BG no description so far
              TransactionRequestType(transactionRequestType.toString),
              "", // chargePolicy is not used in BG so far.,
              callContext
            )
            //set challenge to null, otherwise it have the default value "challenge": {"id": "","allowed_attempts": 0,"challenge_type": ""}
            transactionRequest <- Future(transactionRequest.copy(challenge = null))

            //save transaction_id into database
            _ <- Future {
              saveTransactionRequestTransaction(transactionRequest.id, createdTransactionId)
            }
            //update transaction_id field for variable 'transactionRequest'
            transactionRequest <- Future(transactionRequest.copy(transaction_ids = createdTransactionId.value))

          } yield {
            logger.debug(s"createTransactionRequestv210.createdTransactionId return: $transactionRequest")
            (transactionRequest, callContext)
          }
        case _ => Future(transactionRequest, callContext)
      }
    } yield {
      logger.debug(transactionRequest)
      (Full(TransactionRequestBGV1(transactionRequest.id, transactionRequest.status)), callContext)
    }
  }

  
}
