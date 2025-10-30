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
  var alreadyCanceled: Boolean = false

  def render: NodeSeq = {
    val paymentId = S.param("PAYMENT_ID") openOr ""
    payment = MappedPaymentProvider.getPaymentById(paymentId)

    alreadyApproved = payment.exists(p =>
      p.status == "ACCP" || p.status == "APPROVED"
    )
    alreadyCanceled = payment.exists(p =>
      p.status == "CANC" || p.status == "CANCELED"
    )

    val debtorIban = payment.map(_.mDebtorAccountIban.get).openOr("")

    // Получаем балансы для всех счетов юзера
    val provider = BankAccountBalanceX.bankAccountBalanceProvider.vend
    val balancesMap: Map[String, (BigDecimal, String)] = userAccounts.flatMap { acc =>
      val balanceList = Await.result(provider.getBankAccountBalances(acc.accountId), 5.seconds).openOr(Nil)
      balanceList.map(b => acc.accountId.value -> (BigDecimal(b.BalanceAmount.get), b.BalanceType.get))
    }.toMap


    // Формируем HTML для Debtor IBAN
    val debtorIbanHtml: NodeSeq =
      if (debtorIban == null || debtorIban.trim.isEmpty) {
        // Debtor IBAN не указан — показываем список для выбора с балансом
        <div>
          <p style="margin-top:10px"><strong>Select Debtor IBAN:</strong></p>
          {
          val ibans = userIbans.toList
          val ibanBalanceMap: Map[String, String] = ibans.map { iban =>
            val balanceOpt = balancesMap.collectFirst {
              case (accId, (amount, _)) if BankAccountRouting.find(
                By(BankAccountRouting.AccountId, accId),
                By(BankAccountRouting.AccountRoutingAddress, iban)
              ).isDefined =>
                f"amount: ${amount.toDouble / 100}%.2f MDL"
            }
            iban -> balanceOpt.getOrElse("amount: 0 MDL")
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
            }.getOrElse("amount: 0 MDL")
          }

          <div class="data-row">
            <strong>Debtor IBAN:</strong> <span class="value">{debtorIban} {balanceInfo}</span>
          </div>
        }
      }
    val debtorHtml: NodeSeq = {
      if (!alreadyApproved && !alreadyCanceled) {
        debtorIbanHtml ++ {
          if (debtorIban == null || userIbans.contains(debtorIban) || debtorIban.trim.isEmpty) {
            <form method="post">
              <div class="toggle-container">
                {radioButtons}
              </div>
              <div class="button-container">
                <div class="row">
                  <input id="confirm-bg-payment-request-deny-submit-button" class="btn btn-warning" name="action" type="submit" value="Deny" tabindex="0"/>
                  <input id="confirm-bg-payment-request-confirm-submit-button" class="btn btn-success" name="action"  type="submit" value="Confirm" tabindex="0"/>
                </div>
              </div>
            </form>
          } else NodeSeq.Empty
        }
      } else {
        debtorIbanHtml
      }
    }
    // Вычисление комиссии и общего количества
    val instructedAmount = payment.map(_.mInstructedAmountAmount.get).openOr("0").toDouble
    val commission = (instructedAmount * 0.03).formatted("%.2f")
    val totalAmount = instructedAmount.formatted("%.2f")

    if (S.post_? && !alreadyApproved && !alreadyCanceled) {
      S.param("action") match {
        case Full("Confirm") =>
          val ibanFromForm = S.param("ibanChoice").openOr("").trim
          val selectedIban = if (debtorIban != null && debtorIban.trim.nonEmpty) debtorIban else ibanFromForm

          if (selectedIban.nonEmpty) {
            val hasEnoughBalance = balancesMap.exists {
              case (accId, (amount, _)) =>
                BankAccountRouting.find(
                  By(BankAccountRouting.AccountId, accId),
                  By(BankAccountRouting.AccountRoutingAddress, selectedIban)
                ).isDefined && amount >= BigDecimal(totalAmount)
            }

            if (hasEnoughBalance) {
              MappedPaymentProvider.approvePaymentRequestProcess(paymentId, selectedIban)
            } else {
              S.error(s"Insufficient funds on account $selectedIban. Required: $totalAmount MDL")
            }
          } else {
            S.error("Please select a Debtor IBAN before confirming.")
          }


        case Full("Deny") =>
          MappedPaymentProvider.cancelPaymentRequestProcess(paymentId)
          S.notice("Payment request has been canceled.")

        case Full("Redirect")  =>
          S.redirectTo("www.google.md");
        case _ =>
          S.error("Unknown action.")
      }
    }


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
      {debtorHtml}
      {
      {
        val statusHtml =
          if (alreadyApproved) {
            <div class="alert alert-info text-center" style="margin-top: 15px;">
              APPROVED
            </div>
          } else if (alreadyCanceled) {
            <div class="alert alert-warning text-center" style="margin-top: 15px;">
              CANCELED
            </div>
          } else NodeSeq.Empty

        val redirectForm =
          if (alreadyCanceled || alreadyApproved) {
            <div class="button-container">
              <div class="row">
                <button id="redirect-button" class="btn btn-success" onclick="window.location.href='https://www.google.md';">
                  Redirect
                </button>
              </div>
            </div>

          } else NodeSeq.Empty

        statusHtml ++ redirectForm
      }

      }
    </div>

  }
}
