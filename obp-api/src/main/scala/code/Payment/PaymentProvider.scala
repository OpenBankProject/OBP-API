package code.payments

import code.api.berlin.group.v1_3.model.TransactionStatus
import net.liftweb.http.S
import code.api.util.OBPQueryParam
import com.openbankproject.commons.model.enums.TransactionRequestTypes
import net.liftweb.common.{Box, Empty, Failure, Full}
import net.liftweb.mapper._

trait PaymentProvider {
  protected val redirectUriValue: String = "confirm-bg-payment-request"
  def approvePaymentRequestProcess(paymentId: String, debtorIban: String, purposeType: String): Unit
  def cancelPaymentRequestProcess(paymentId: String): Unit
  def getPaymentById(paymentId: String): Box[MappedPayment]
  def getPaymentByEndToEndIdentification(endToEndIdentification: String): Box[MappedPayment]
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
  def updatePayment(
                     paymentId: String,
                     status: Option[TransactionStatus] = None,
                     paymentType: Option[TransactionRequestTypes] = None,
                     debtorAccountIban: Option[String],
                     purposeType: Option[String] = None
                   ): Box[MappedPayment]
}

object MappedPaymentProvider extends PaymentProvider {

  override def getPaymentById(paymentId: String): Box[MappedPayment] =
    MappedPayment.find(By(MappedPayment.mPaymentId, paymentId))

  override def getPaymentByEndToEndIdentification(endToEndIdentification: String): Box[MappedPayment] =
    MappedPayment.find(By(MappedPayment.mEndToEndIdentification, endToEndIdentification))

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

  override def updatePayment(
                              paymentId: String,
                              status: Option[TransactionStatus] = None,
                              paymentType: Option[TransactionRequestTypes] = None,
                              debtorAccountIban: Option[String] = None,
                              purposeType: Option[String] = None
                            ): Box[MappedPayment] = {
    getPaymentById(paymentId) match {
      case Full(payment) =>
        try {
          status.foreach(s => payment.mStatus(s.code))
          debtorAccountIban.foreach(iban => payment.mDebtorAccountIban(iban))
          paymentType.foreach(t => payment.mType(t.toString()))
          purposeType.foreach(p => payment.mPurposeType(p.toString()))
          Full(payment.saveMe())
        } catch {
          case e: Exception => Failure(e.getMessage)
        }
      case Empty => Empty
      case f: Failure => f
    }
  }

  def approvePaymentRequestProcess(paymentId: String, debtorIban: String, purposeType: String): Unit = {
    // Ищем платеж в базе
    MappedPaymentProvider.getPaymentById(paymentId) match {
      case Full(payment) =>
        // Обновляем IBAN платежа и статус
        MappedPaymentProvider.updatePayment(paymentId, Some(TransactionStatus.ACCP), debtorAccountIban = Some(debtorIban), purposeType = Some(purposeType)) match {
          case Full(updatedPayment) =>
            // Перенаправляем пользователя на страницу с подтверждением
            S.redirectTo(s"$redirectUriValue?PAYMENT_ID=${paymentId}")
          case _ =>
            S.error("Failed to update payment status")
        }
      case _ =>
        S.error("Payment not found")
    }
  }

  def cancelPaymentRequestProcess(paymentId: String): Unit = {
    // Ищем платеж в базе
    MappedPaymentProvider.getPaymentById(paymentId) match {
      case Full(payment) =>
        // Обновляем IBAN платежа и статус
        MappedPaymentProvider.updatePayment(paymentId, Some(TransactionStatus.CANC)) match {
          case Full(updatedPayment) =>
            // Перенаправляем пользователя на страницу с подтверждением
            S.redirectTo(s"$redirectUriValue?PAYMENT_ID=${paymentId}")
          case _ =>
            S.error("Failed to update payment status")
        }
      case _ =>
        S.error("Payment not found")
    }
  }

  object PurposeType extends Enumeration {
    type PurposeType = Value

    // Перечисление значений
    val RefundOfErroneousPayment = Value("RefundOfErroneousPayment")
    val LoanOrFinancialHelp = Value("LoanOrFinancialHelp")
    val LoanOrFinancialHelpReimbursement = Value("LoanOrFinancialHelpReimbursement")
    val TransferInPersonalAccount = Value("TransferInPersonalAccount")
    val PersonalTransferFamilyExpenses = Value("PersonalTransferFamilyExpenses")
    val PaymentsToBudget = Value("PaymentsToBudget")

    // Маппинг на описание
    def description(purpose: PurposeType): String = purpose match {
      case RefundOfErroneousPayment => "Refund of erroneous payment (Возврат неверно зачисленного платежа)"
      case LoanOrFinancialHelp => "Loan / Financial help (Ссуда / Финансовая помощь)"
      case LoanOrFinancialHelpReimbursement => "Loan / financial help reimbursement (Возврат займа / финансовой помощи)"
      case TransferInPersonalAccount => "Transfer in one's own account (Перевод на свой счет в другом банке)"
      case PersonalTransferFamilyExpenses => "Personal transfer – family expenses (Семейные расходы)"
      case PaymentsToBudget => "Payments to the budget (Платежи в бюджет)"
    }
  }
}