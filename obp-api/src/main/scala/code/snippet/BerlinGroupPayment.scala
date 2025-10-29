package code.snippet

import code.accountholders.AccountHolders
import code.api.util.ErrorMessages
import code.model.dataAccess.{AuthUser, BankAccountRouting}
import code.bankaccountbalance.BankAccountBalanceX
import net.liftweb.common._
import net.liftweb.http.{S, SHtml}
import net.liftweb.mapper.By
import code.payments.{MappedPayment, MappedPaymentProvider}
import com.openbankproject.commons.model.BankIdAccountId

import scala.xml.NodeSeq
import scala.concurrent.Await
import scala.concurrent.duration._

class ConfirmPaymentRequest {

  val currentUser = AuthUser.currentUser
  val userAccounts: Set[BankIdAccountId] =
    AccountHolders.accountHolders.vend.getAccountsHeldByUser(
      AuthUser.currentUser.flatMap(_.user.foreign).openOrThrowException(ErrorMessages.UserNotLoggedIn), Some(null)
    ).toSet
  var radioButtons : NodeSeq = NodeSeq.Empty

  val userIbans: Set[String] = userAccounts.flatMap { acc =>
    BankAccountRouting.find(
      By(BankAccountRouting.BankId, acc.bankId.value),
      By(BankAccountRouting.AccountId, acc.accountId.value),
      By(BankAccountRouting.AccountRoutingScheme, "IBAN")
    ).map(_.AccountRoutingAddress.get)
  }

  var payment: Box[MappedPayment] = Empty
  var alreadyApproved: Boolean = false

  def render: NodeSeq = {
    val paymentId = S.param("PAYMENT_ID") openOr ""
    payment = MappedPaymentProvider.getPaymentById(paymentId)

    payment.foreach { p =>
      if (p.status == "ACCP" || p.status == "APPROVED") alreadyApproved = true
    }

    val debtorIban = payment.map(_.mDebtorAccountIban.get).openOr("")

    if (S.post_? && !alreadyApproved) {
      val ibanFromForm = S.param("ibanChoice").openOr("").trim
      if (debtorIban != null && debtorIban.trim.nonEmpty) {
        MappedPaymentProvider.approvePaymentRequestProcess(paymentId, debtorIban)
      } else if (ibanFromForm.nonEmpty) {
        MappedPaymentProvider.approvePaymentRequestProcess(paymentId, ibanFromForm)
      } else {
        S.error("Please select a Debtor IBAN before confirming.")
      }
    }

    // Получаем балансы для всех счетов юзера
    val provider = BankAccountBalanceX.bankAccountBalanceProvider.vend
    val balancesMap: Map[String, (BigDecimal, String)] = userAccounts.flatMap { acc =>
      val balanceList = Await.result(provider.getBankAccountBalances(acc.accountId), 5.seconds).openOr(Nil)
      balanceList.map(b => acc.accountId.value -> (BigDecimal(b.BalanceAmount.get), b.BalanceType.get))
    }.toMap


    // Формируем HTML для Debtor IBAN
    val debtorIbanHtml: NodeSeq = if (debtorIban == null || debtorIban.trim.isEmpty) {
      // Debtor IBAN не указан — показываем список для выбора с балансом
      <div>
        <p><strong>Select Debtor IBAN:</strong></p>
        {
        val ibans = userIbans.toList
        val ibanBalanceMap: Map[String, String] = ibans.map { iban =>
          val balanceOpt = balancesMap.collectFirst {
            case (accId, (amount, _)) if BankAccountRouting.find(
              By(BankAccountRouting.AccountId, accId),
              By(BankAccountRouting.AccountRoutingAddress, iban)
            ).isDefined =>
              f"amount: ${amount.toDouble / 100}%.2f"
          }
          iban -> balanceOpt.getOrElse("No balance")
        }.toMap


        // Соединяем радиокнопки и скрытое поле в один NodeSeq
        radioButtons = ibans.map { iban =>
          <label>
            <input type="radio" name="ibanChoice" value={iban} id={iban}/>
            <span class="toggle-label" style="color:black;font-weight: 500;margin-top: 5px;">{iban} ({ibanBalanceMap(iban)})</span>
          </label>
        }.foldLeft(NodeSeq.Empty)(_ ++ _)
        }
      </div>
    } else {
      // Debtor IBAN указан — проверяем валидность
      if (!userIbans.contains(debtorIban)) {
        <div class="alert alert-danger" style="margin-top: 5px;">
          Invalid Debtor IBAN: {debtorIban}
        </div>
      } else {
        // IBAN валиден — показываем IBAN + баланс и валюту
        val balanceInfo: String = if (alreadyApproved) {
          "" // или "Approved" или просто не показывать
        } else {
          balancesMap.collectFirst {
            case (accId, (amount, amountType)) if BankAccountRouting.find(
              By(BankAccountRouting.AccountId, accId),
              By(BankAccountRouting.AccountRoutingAddress, debtorIban)
            ).isDefined =>
              f"(amount: ${amount.toDouble / 100}%.2f)"
          }.getOrElse("No balance")
        }

        <div class="data-row">
          <strong>Debtor IBAN:</strong> <span class="value">{debtorIban} {balanceInfo}</span>
        </div>
      }
    }

    // Вычисление комиссии и общего количества
    val instructedAmount = payment.map(_.mInstructedAmountAmount.get).openOr("0").toDouble
    val commission = (instructedAmount * 0.03).formatted("%.2f")
    val totalAmount = (instructedAmount + commission.toDouble).formatted("%.2f")

    if (!alreadyApproved) {
      <div class="payment-details">
        <h2 class="transaction-title">Transaction Confirmation</h2>
        <div class="data-row">
          <strong>Creditor MSISDN:</strong> <span class="value">{payment.map(_.mCreditorAccountMsisdn.get).openOr("")}</span>
        </div>
        <div class="data-row">
          <strong>Amount:</strong> <span class="value">{payment.map(_.mInstructedAmountAmount.get).openOr("")} {payment.map(_.mInstructedAmountCurrency.get).openOr("")}</span>
        </div>
        <div class="data-row">
          <strong>Status:</strong> <span class="value">{payment.map(_.status).openOr("")}</span>
        </div>
        <div class="data-row">
          <strong>Type:</strong> <span class="value">{payment.map(_.transactionType).openOr("")}</span>
        </div>
        <div class="data-row">
          <strong>Commission:</strong> <span class="value">0 {payment.map(_.mInstructedAmountCurrency.get).openOr("")}</span>
        </div>
        <div class="data-row">
          <strong>Remittance Info:</strong> <span class="value">{payment.map(_.mRemittanceInformationUnstructured.get).openOr("")}</span>
        </div>
        <div class="data-row">
          <strong>Total Amount:</strong> <span class="value">{totalAmount} {payment.map(_.mInstructedAmountCurrency.get).openOr("")}</span>
        </div>

        {debtorIbanHtml}

        {
        // Показываем кнопки только если Debtor IBAN валиден или выбран
        if (debtorIban == null || userIbans.contains(debtorIban) || debtorIban.trim.isEmpty) {
          NodeSeq.Empty ++
            <form method="post" >
              <div class="toggle-container">
                {radioButtons}
              </div>
              <div class="button-container" >
                <div class="row" >
                  <a id="confirm-bg-payment-request-deny-submit-button" class="btn btn-warning" href="/">Deny</a>
                  <input id="confirm-bg-payment-request-confirm-submit-button" class="btn btn-success" type="submit" value="Confirm" tabindex="0"/>
                </div>
              </div>
            </form>
        } else NodeSeq.Empty
        }

      </div>
    } else {
      <div class="payment-details">
        <h2 class="transaction-title">Transaction Confirmation</h2>
        <div class="data-row">
          <strong>Creditor MSISDN:</strong> <span class="value">{payment.map(_.mCreditorAccountMsisdn.get).openOr("")}</span>
        </div>
        <div class="data-row">
          <strong>Amount:</strong> <span class="value">{payment.map(_.mInstructedAmountAmount.get).openOr("")} {payment.map(_.mInstructedAmountCurrency.get).openOr("")}</span>
        </div>
        <div class="data-row">
          <strong>Status:</strong> <span class="value">{payment.map(_.status).openOr("")}</span>
        </div>
        <div class="data-row">
          <strong>Type:</strong> <span class="value">{payment.map(_.transactionType).openOr("")}</span>
        </div>
        <div class="data-row">
          <strong>Commission:</strong> <span class="value">0 {payment.map(_.mInstructedAmountCurrency.get).openOr("")}</span>
        </div>
        <div class="data-row">
          <strong>Remittance Info:</strong> <span class="value">{payment.map(_.mRemittanceInformationUnstructured.get).openOr("")}</span>
        </div>
        <div class="data-row">
          <strong>Total Amount:</strong> <span class="value">{totalAmount} {payment.map(_.mInstructedAmountCurrency.get).openOr("")}</span>
        </div>

        {debtorIbanHtml}

        <div class="alert alert-info" style="margin-top: 15px;">
          Payment with this ID has already been approved.
        </div>
      </div>
    }
  }
}
