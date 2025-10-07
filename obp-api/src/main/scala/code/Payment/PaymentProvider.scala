package code.payments

import code.api.berlin.group.v1_3.model.TransactionStatus

import java.util.Date
import code.api.util.OBPQueryParam
import code.consent.ConsentStatus.Value
import code.util.MappedUUID
import com.openbankproject.commons.model.enums.TransactionRequestTypes
import net.liftweb.common.{Box, Empty, Failure, Full}
import net.liftweb.mapper._

trait PaymentProvider {
  def getPaymentById(paymentId: String): Box[MappedPayment]
  def getPayments(queryParams: List[OBPQueryParam] = Nil): List[MappedPayment]
  def createPayment(
                     endToEndIdentification: String,
                     debtorAccountIban: Option[String],
                     instructedAmountCurrency: String,
                     instructedAmountAmount: String,
                     creditorAccountMsisdn: String,
                     purposeCode: String,
                     remittanceInformationUnstructured: String,
                     status: TransactionStatus = TransactionStatus.RCVD,
                     paymentType: TransactionRequestTypes = TransactionRequestTypes.SANDBOX_TAN
                   ): Box[MappedPayment]
  def updatePayment(paymentId: String, status: Option[TransactionStatus] = None, paymentType: Option[TransactionRequestTypes] = None): Box[MappedPayment]
}

object MappedPaymentProvider extends PaymentProvider {

  override def getPaymentById(paymentId: String): Box[MappedPayment] =
    MappedPayment.find(By(MappedPayment.mPaymentId, paymentId))

  override def getPayments(queryParams: List[OBPQueryParam] = Nil): List[MappedPayment] = {
    // Можно добавить фильтры по статусу / типу из queryParams, если нужно
    MappedPayment.findAll()
  }

  override def createPayment(
                              endToEndIdentification: String,
                              debtorAccountIban: Option[String],
                              instructedAmountCurrency: String,
                              instructedAmountAmount: String,
                              creditorAccountMsisdn: String,
                              purposeCode: String,
                              remittanceInformationUnstructured: String,
                              status: TransactionStatus = TransactionStatus.RCVD,
                              paymentType: TransactionRequestTypes = TransactionRequestTypes.INSTANT_CREDIT_TRANSFERS_MD
                            ): Box[MappedPayment] = {
    try {
      Full(
        MappedPayment.create
          .mEndToEndIdentification(endToEndIdentification)
          .mDebtorAccountIban(debtorAccountIban.orNull)
          .mInstructedAmountCurrency(instructedAmountCurrency)
          .mInstructedAmountAmount(instructedAmountAmount)
          .mCreditorAccountMsisdn(creditorAccountMsisdn)
          .mPurposeCode(purposeCode)
          .mRemittanceInformationUnstructured(remittanceInformationUnstructured)
          .mStatus(status.code)
          .mType(paymentType.toString())
          .saveMe()
      )
    } catch {
      case e: Exception => Failure(e.getMessage)
    }
  }

  override def updatePayment(paymentId: String, status: Option[TransactionStatus] = None, paymentType: Option[TransactionRequestTypes] = None): Box[MappedPayment] = {
    getPaymentById(paymentId) match {
      case Full(payment) =>
        try {
          status.foreach(s => payment.mStatus(s.code))
          paymentType.foreach(t => payment.mType(t.toString()))
          Full(payment.saveMe())
        } catch {
          case e: Exception => Failure(e.getMessage)
        }
      case Empty => Empty
      case f: Failure => f
    }
  }
}