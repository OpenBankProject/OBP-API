package code.api.berlin.group.v1_3

import org.json4s._
import cats.data.{Kleisli, OptionT}
import cats.effect._
import code.api.APIFailureNewStyle
import code.api.Constant.{SYSTEM_READ_ACCOUNTS_BERLIN_GROUP_VIEW_ID, SYSTEM_READ_BALANCES_BERLIN_GROUP_VIEW_ID, SYSTEM_READ_TRANSACTIONS_BERLIN_GROUP_VIEW_ID}
import code.api.berlin.group.ConstantsBG
import code.api.berlin.group.v1_3.JSONFactory_BERLIN_GROUP_1_3._
import code.api.berlin.group.v1_3.model._
import code.api.berlin.group.v1_3.{BgSpecValidation, JSONFactory_BERLIN_GROUP_1_3, JvalueCaseClass}
import code.api.RequestHeader
import code.api.util.APIUtil
import code.api.util.APIUtil.{EmptyBody, ResourceDoc, UserOrApplication, connectorEmptyResponse, createQueriesByHttpParams, fullBoxOrException, getHttpRequestUrlParam, getSuggestedDefaultScaMethod, mockedDataText, passesPsd2Aisp, unboxFull, unboxFullOrFail}
import code.api.util.CallContext
import code.api.util.ApiTag._
import code.api.util.CustomJsonFormats
import code.api.util.ErrorMessages._
import code.api.util.{ApiTag, Consent, NewStyle}
import code.api.util.newstyle.ViewNewStyle
import code.api.util.http4s.Http4sRequestAttributes.{EndpointHelpers, RequestOps}
import code.consent.{ConsentStatus, Consents}
import code.context.{ConsentAuthContextProvider, UserAuthContextProvider}
import code.model
import code.model._
import code.util.Helper
import code.util.Helper.{MdcLoggable, booleanToFuture}
import code.views.Views
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.model._
import com.openbankproject.commons.model.enums.{ChallengeType, StrongCustomerAuthenticationStatus, SuppliedAnswerType}
import net.liftweb.common.{Empty, Full}
import com.openbankproject.commons.util.json
import org.json4s.Formats
import org.http4s._
import org.http4s.dsl.io._

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Future

object Http4sBGv13AIS extends MdcLoggable {

  type HttpF[A] = OptionT[IO, A]

  implicit val formats: Formats = CustomJsonFormats.formats

  // ResourceDoc example bodies are written as `json.parse(...)` (JValue). Since the json4s
  // migration, JValue itself extends scala.Product, so an implicit JValue => JvalueCaseClass
  // conversion never fires. Each example body is therefore wrapped explicitly in
  // JvalueCaseClass(...) so resource-docs serialization takes its special-case path (no field
  // reflection; the jvalueToCaseclass wrapper key is stripped) instead of reflecting on a raw JObject.

  val implementedInApiVersion = ConstantsBG.berlinGroupVersion1
  val resourceDocs = ArrayBuffer[ResourceDoc]()

  val bgV13Prefix = Root / ConstantsBG.berlinGroupVersion1.urlPrefix / ConstantsBG.berlinGroupVersion1.apiShortVersion

  private def checkAccountAccess(viewId: ViewId, u: User, account: BankAccount, callContext: Option[code.api.util.CallContext]) = {
    Future {
      Helper.booleanToBox(u.hasViewAccess(BankIdAccountId(account.bankId, account.accountId), viewId, callContext))
    } map {
      // No user id in the message. Under consent authentication `u` is the consent's own shadow
      // user -- an internal identifier the TPP has no business learning and cannot act on. What
      // the refusal is actually about is the view and the account, both of which the caller
      // already named. The user id stays in the logs for anyone diagnosing it.
      unboxFullOrFail(_, callContext,
        s"$NoViewReadAccountsBerlinGroup ${viewId.value}. account : ${account.accountId}", 403)
    }
  }

  // ── POST /consents ──────────────────────────────────────────────────────
  lazy val createConsent: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ POST -> `bgV13Prefix` / "consents" =>
      EndpointHelpers.executeFutureCreated(req) {
        val cc = req.callContext
        val callContext = Some(cc)
        // A pure client-credentials token still resolves cc.user to an auto-vivified
        // pseudo-user (idGivenByProvider == the calling consumer's own client key) rather than
        // leaving it Empty -- that pseudo-user is not a PSU, so it must not become the
        // consent's owner (it would permanently block the real PSU's authorise-time
        // ConsentDoesNotMatchUser check). Only carry a genuine PSU session through.
        val createdByUser: Option[User] = cc.user.toOption
          .filterNot(u => cc.consumer.map(_.key.get).contains(u.idGivenByProvider))
        for {
          _ <- passesPsd2Aisp(callContext)
          failMsg = s"$InvalidJsonFormat The Json body should be the $PostConsentJson "
          consentJson <- NewStyle.function.tryons(failMsg, 400, callContext) {
            json.parse(cc.httpBody.getOrElse("")).extract[PostConsentJson]
          }
          _ <- if (consentJson.access.availableAccounts.isDefined) {
            for {
              _ <- booleanToFuture(failMsg = BerlinGroupConsentAccessAvailableAccounts, cc = callContext) {
                consentJson.access.availableAccounts.contains("allAccounts")
              }
              _ <- booleanToFuture(failMsg = BerlinGroupConsentAccessRecurringIndicator, cc = callContext) {
                !consentJson.recurringIndicator
              }
              _ <- booleanToFuture(failMsg = BerlinGroupConsentAccessFrequencyPerDay, cc = callContext) {
                consentJson.frequencyPerDay == 1
              }
            } yield Full(())
          } else {
            booleanToFuture(failMsg = BerlinGroupConsentAccessIsEmpty, cc = callContext) {
              consentJson.access.accounts.isDefined ||
                consentJson.access.balances.isDefined ||
                consentJson.access.transactions.isDefined
            }
          }
          upperLimit = code.api.util.APIUtil.getPropsAsIntValue("berlin_group_frequency_per_day_upper_limit", 4)
          _ <- booleanToFuture(failMsg = FrequencyPerDayError, cc = callContext) {
            consentJson.frequencyPerDay > 0 && consentJson.frequencyPerDay <= upperLimit
          }
          _ <- booleanToFuture(failMsg = FrequencyPerDayMustBeOneError, cc = callContext) {
            consentJson.recurringIndicator ||
              !consentJson.recurringIndicator && consentJson.frequencyPerDay == 1
          }
          failMsg2 = BgSpecValidation.getErrorMessage(consentJson.validUntil)
          validUntil = BgSpecValidation.getDate(consentJson.validUntil)
          _ <- booleanToFuture(failMsg2, 400, callContext) {
            failMsg2.isEmpty
          }
          _ <- NewStyle.function.getBankAccountsByIban(consentJson.access.accounts.getOrElse(Nil).map(_.iban.getOrElse("")), callContext)
          createdConsent <- Future(Consents.consentProvider.vend.createBerlinGroupConsent(
            createdByUser,
            callContext.flatMap(_.consumer),
            recurringIndicator = consentJson.recurringIndicator,
            validUntil = validUntil,
            frequencyPerDay = consentJson.frequencyPerDay,
            combinedServiceIndicator = consentJson.combinedServiceIndicator.getOrElse(false),
            apiStandard = Some(implementedInApiVersion.apiStandard),
            apiVersion = Some(implementedInApiVersion.apiShortVersion)
          )) map {
            i => connectorEmptyResponse(i, callContext)
          }
          consentJWT <- _root_.code.api.util.Consent.createBerlinGroupConsentJWT(
            createdByUser,
            consentJson,
            createdConsent.secret,
            createdConsent.consentId,
            callContext.flatMap(_.consumer).map(_.consumerId.get),
            Some(validUntil),
            callContext
          ) map {
            i => connectorEmptyResponse(i, callContext)
          }
          _ <- Future(Consents.consentProvider.vend.setJsonWebToken(createdConsent.consentId, consentJWT)) map {
            i => connectorEmptyResponse(i, callContext)
          }
        } yield {
          createPostConsentResponseJson(createdConsent)
        }
      }
  }

  // ── DELETE /consents/CONSENTID ──────────────────────────────────────────
  lazy val deleteConsent: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ DELETE -> `bgV13Prefix` / "consents" / consentId =>
      EndpointHelpers.executeDelete(req) { cc =>
        val callContext = Some(cc)
        for {
          _ <- passesPsd2Aisp(callContext)
          consent <- Future(Consents.consentProvider.vend.getConsentByConsentId(consentId)) map {
            unboxFullOrFail(_, callContext, ConsentNotFound, 403)
          }
          _ <- Consent.assertBerlinGroupConsentReadAccess(consent.userId, consent.consumerId, cc)
          _ <- Future(Consents.consentProvider.vend.revokeBerlinGroupConsent(consentId)) map {
            i => connectorEmptyResponse(i, callContext)
          }
        } yield ()
      }
  }

  // ── GET /accounts ───────────────────────────────────────────────────────
  lazy val getAccountList: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `bgV13Prefix` / "accounts" =>
      EndpointHelpers.executeAndRespond(req) { cc =>
        val callContext = Some(cc)
        val u = cc.user.openOrThrowException(AuthenticatedUserIsRequired)
        for {
          withBalanceParam <- NewStyle.function.tryons(s"$InvalidUrlParameters withBalance parameter can only take two values: TRUE or FALSE!", 400, callContext) {
            val withBalance = getHttpRequestUrlParam(cc.url, "withBalance")
            if (withBalance.isEmpty) Some(false) else Some(withBalance.toBoolean)
          }
          _ <- passesPsd2Aisp(callContext)
          (availablePrivateAccounts, callContext) <- NewStyle.function.getAccountListOfBerlinGroup(u, callContext)
          (canReadBalancesAccounts, callContext) <- NewStyle.function.getAccountCanReadBalancesOfBerlinGroup(u, callContext)
          (canReadTransactionsAccounts, callContext) <- NewStyle.function.getAccountCanReadTransactionsOfBerlinGroup(u, callContext)
          (accounts, callContext) <- NewStyle.function.getBankAccounts(availablePrivateAccounts, callContext)
          bankAccountsFiltered = accounts.filter(bankAccount =>
            bankAccount.attributes.toList.flatten.find(attribute =>
              attribute.name.equals("CashAccountTypeCode") &&
                attribute.`type`.equals("STRING") &&
                attribute.value.equalsIgnoreCase("card")
            ).isEmpty)
          (balances, callContext) <- code.api.util.newstyle.BankAccountBalanceNewStyle.getBankAccountsBalances(
            bankAccountsFiltered.map(_.accountId),
            callContext
          )
        } yield {
          JSONFactory_BERLIN_GROUP_1_3.createAccountListJson(
            bankAccountsFiltered,
            canReadBalancesAccounts,
            canReadTransactionsAccounts,
            u,
            withBalanceParam,
            balances
          )
        }
      }
  }

  // ── GET /accounts/ACCOUNT_ID/balances ───────────────────────────────────
  lazy val getBalances: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `bgV13Prefix` / "accounts" / accountId / "balances" =>
      EndpointHelpers.executeAndRespond(req) { cc =>
        val callContext = Some(cc)
        val u = cc.user.openOrThrowException(AuthenticatedUserIsRequired)
        for {
          _ <- passesPsd2Aisp(callContext)
          (account: BankAccount, callContext) <- NewStyle.function.getBankAccountByAccountId(AccountId(accountId), callContext)
          _ <- checkAccountAccess(ViewId(SYSTEM_READ_BALANCES_BERLIN_GROUP_VIEW_ID), u, account, callContext)
          (accountBalances, callContext) <- code.api.util.newstyle.BankAccountBalanceNewStyle.getBankAccountBalances(
            AccountId(accountId),
            callContext
          )
        } yield {
          JSONFactory_BERLIN_GROUP_1_3.createAccountBalanceJSON(account, accountBalances)
        }
      }
  }

  // ── GET /card-accounts ──────────────────────────────────────────────────
  lazy val getCardAccounts: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `bgV13Prefix` / "card-accounts" =>
      EndpointHelpers.executeAndRespond(req) { cc =>
        val callContext = Some(cc)
        val u = cc.user.openOrThrowException(AuthenticatedUserIsRequired)
        for {
          _ <- passesPsd2Aisp(callContext)
          availablePrivateAccounts <- Views.views.vend.getPrivateBankAccountsFuture(u)
          (_, callContext) <- NewStyle.function.getPhysicalCardsForUser(u, callContext)
          (accounts, callContext) <- NewStyle.function.getBankAccounts(availablePrivateAccounts, callContext)
          (canReadBalancesAccounts, callContext) <- NewStyle.function.getAccountCanReadBalancesOfBerlinGroup(u, callContext)
          (canReadTransactionsAccounts, callContext) <- NewStyle.function.getAccountCanReadTransactionsOfBerlinGroup(u, callContext)
          bankAccountsFiltered = accounts.filter(bankAccount =>
            bankAccount.attributes.toList.flatten.find(attribute =>
              attribute.name.equals("CashAccountTypeCode") &&
                attribute.`type`.equals("STRING") &&
                attribute.value.equalsIgnoreCase("card")
            ).isDefined)
        } yield {
          JSONFactory_BERLIN_GROUP_1_3.createCardAccountListJson(
            bankAccountsFiltered,
            canReadBalancesAccounts,
            canReadTransactionsAccounts,
            u
          )
        }
      }
  }

  // ── GET /card-accounts/ACCOUNT_ID/balances ──────────────────────────────
  lazy val getCardAccountBalances: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `bgV13Prefix` / "card-accounts" / accountId / "balances" =>
      EndpointHelpers.executeAndRespond(req) { cc =>
        val callContext = Some(cc)
        val u = cc.user.openOrThrowException(AuthenticatedUserIsRequired)
        for {
          _ <- passesPsd2Aisp(callContext)
          (account: BankAccount, callContext) <- NewStyle.function.getBankAccountByAccountId(AccountId(accountId), callContext)
          _ <- checkAccountAccess(ViewId(SYSTEM_READ_BALANCES_BERLIN_GROUP_VIEW_ID), u, account, callContext)
          (accountBalances, callContext) <- code.api.util.newstyle.BankAccountBalanceNewStyle.getBankAccountBalances(
            AccountId(accountId),
            callContext
          )
        } yield {
          JSONFactory_BERLIN_GROUP_1_3.createCardAccountBalanceJSON(account, accountBalances)
        }
      }
  }

  // ── GET /card-accounts/ACCOUNT_ID/transactions ──────────────────────────
  lazy val getCardAccountTransactionList: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `bgV13Prefix` / "card-accounts" / accountId / "transactions" =>
      EndpointHelpers.executeAndRespond(req) { cc =>
        val callContext = Some(cc)
        val u = cc.user.openOrThrowException(AuthenticatedUserIsRequired)
        for {
          _ <- passesPsd2Aisp(callContext)
          (bankAccount: BankAccount, callContext) <- NewStyle.function.getBankAccountByAccountId(AccountId(accountId), callContext)
          (bank, callContext) <- NewStyle.function.getBank(bankAccount.bankId, callContext)
          viewId = ViewId(SYSTEM_READ_TRANSACTIONS_BERLIN_GROUP_VIEW_ID)
          bankIdAccountId = BankIdAccountId(bankAccount.bankId, bankAccount.accountId)
          view <- ViewNewStyle.checkAccountAccessAndGetView(viewId, bankIdAccountId, Full(u), callContext)
          params <- Future { createQueriesByHttpParams(callContext.get.requestHeaders)} map {
            x => fullBoxOrException(x ~> APIFailureNewStyle(UnknownError, 400, callContext.map(_.toLight)))
          } map { unboxFull(_) }
          (transactions, callContext) <- code.model.toBankAccountExtended(bankAccount).getModeratedTransactionsFuture(bank, Full(u), view, callContext, params) map {
            x => fullBoxOrException(x ~> APIFailureNewStyle(UnknownError, 400, callContext.map(_.toLight)))
          } map { unboxFull(_) }
        } yield {
          JSONFactory_BERLIN_GROUP_1_3.createCardTransactionsJson(bankAccount, transactions)
        }
      }
  }

  // ── GET /consents/CONSENTID/authorisations ──────────────────────────────
  lazy val getConsentAuthorisation: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `bgV13Prefix` / "consents" / consentId / "authorisations" =>
      EndpointHelpers.executeAndRespond(req) { cc =>
        val callContext = Some(cc)
        for {
          _ <- passesPsd2Aisp(callContext)
          // The same ownership test its sibling GET /consents/CONSENTID applies twelve lines below.
          // Without it any PSD2-AISP caller could list the authorisation ids of a consent lodged by
          // somebody else -- the PUT that answers one is guarded, so this leaked identifiers rather
          // than access, but the asymmetry between two neighbouring reads of the same consent was an
          // oversight, not a decision.
          consent <- Future(Consents.consentProvider.vend.getConsentByConsentId(consentId)) map {
            unboxFullOrFail(_, callContext, ConsentNotFound, 403)
          }
          _ <- Consent.assertBerlinGroupConsentReadAccess(consent.userId, consent.consumerId, cc)
          (challenges, callContext) <- NewStyle.function.getChallengesByConsentId(consentId, callContext)
        } yield {
          JSONFactory_BERLIN_GROUP_1_3.AuthorisationJsonV13(challenges.map(_.challengeId))
        }
      }
  }

  // ── GET /consents/CONSENTID ─────────────────────────────────────────────
  lazy val getConsentInformation: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `bgV13Prefix` / "consents" / consentId =>
      EndpointHelpers.executeAndRespond(req) { cc =>
        val callContext = Some(cc)
        for {
          _ <- passesPsd2Aisp(callContext)
          consent <- Future(Consents.consentProvider.vend.getConsentByConsentId(consentId)) map {
            unboxFullOrFail(_, callContext, ConsentNotFound, 403)
          }
          _ <- Consent.assertBerlinGroupConsentReadAccess(consent.userId, consent.consumerId, cc)
        } yield {
          createGetConsentResponseJson(consent)
        }
      }
  }

  // ── GET /consents/CONSENTID/authorisations/AUTHORISATIONID ─────────────
  lazy val getConsentScaStatus: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `bgV13Prefix` / "consents" / consentId / "authorisations" / authorisationId =>
      EndpointHelpers.executeAndRespond(req) { cc =>
        val callContext = Some(cc)
        for {
          _ <- passesPsd2Aisp(callContext)
          // Same ownership test as the three sibling reads of this consent. The consent was already
          // being fetched here purely to prove it exists, so the SCA status of anyone's consent was
          // readable by any AISP that knew the id.
          consent <- Future(Consents.consentProvider.vend.getConsentByConsentId(consentId)) map {
            unboxFullOrFail(_, callContext, ConsentNotFound, 403)
          }
          _ <- Consent.assertBerlinGroupConsentReadAccess(consent.userId, consent.consumerId, cc)
          (challenges, callContext) <- NewStyle.function.getChallengesByConsentId(consentId, callContext)
        } yield {
          val challengeStatus = challenges.filter(_.challengeId == authorisationId)
            .flatMap(_.scaStatus).headOption.map(_.toString).getOrElse("None")
          JSONFactory_BERLIN_GROUP_1_3.ScaStatusJsonV13(challengeStatus)
        }
      }
  }

  // ── GET /consents/CONSENTID/status ──────────────────────────────────────
  lazy val getConsentStatus: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `bgV13Prefix` / "consents" / consentId / "status" =>
      EndpointHelpers.executeAndRespond(req) { cc =>
        val callContext = Some(cc)
        for {
          _ <- passesPsd2Aisp(callContext)
          consent <- Future(Consents.consentProvider.vend.getConsentByConsentId(consentId)) map {
            unboxFullOrFail(_, callContext, ConsentNotFound, 403)
          }
          // Same ownership test as the three sibling reads of this consent. Without it the status of
          // any consent was readable by any AISP holding its id -- which is enough to confirm the
          // consent exists and to watch it move through authorisation.
          _ <- Consent.assertBerlinGroupConsentReadAccess(consent.userId, consent.consumerId, cc)
        } yield {
          JSONFactory_BERLIN_GROUP_1_3.ConsentStatusJsonV13(consent.status)
        }
      }
  }

  // ── GET /accounts/ACCOUNT_ID/transactions/TRANSACTIONID ─────────────────
  lazy val getTransactionDetails: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `bgV13Prefix` / "accounts" / accountId / "transactions" / transactionId =>
      EndpointHelpers.executeAndRespond(req) { cc =>
        val callContext = Some(cc)
        val user = cc.user.openOrThrowException(AuthenticatedUserIsRequired)
        for {
          (account: BankAccount, callContext) <- NewStyle.function.getBankAccountByAccountId(AccountId(accountId), callContext)
          viewId = ViewId(SYSTEM_READ_TRANSACTIONS_BERLIN_GROUP_VIEW_ID)
          bankIdAccountId = BankIdAccountId(account.bankId, account.accountId)
          view <- ViewNewStyle.checkAccountAccessAndGetView(viewId, bankIdAccountId, Full(user), callContext)
          (moderatedTransaction, callContext) <- account.moderatedTransactionFuture(TransactionId(transactionId), view, Some(user), callContext) map {
            unboxFullOrFail(_, callContext, GetTransactionsException)
          }
        } yield {
          JSONFactory_BERLIN_GROUP_1_3.createTransactionJson(account, moderatedTransaction)
        }
      }
  }

  // ── GET /accounts/ACCOUNT_ID/transactions ───────────────────────────────
  lazy val getTransactionList: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `bgV13Prefix` / "accounts" / accountId / "transactions" =>
      EndpointHelpers.executeAndRespond(req) { cc =>
        val callContext = Some(cc)
        val u = cc.user.openOrThrowException(AuthenticatedUserIsRequired)
        for {
          _ <- passesPsd2Aisp(callContext)
          (bankAccount: BankAccount, callContext) <- NewStyle.function.getBankAccountByAccountId(AccountId(accountId), callContext)
          (bank, callContext) <- NewStyle.function.getBank(bankAccount.bankId, callContext)
          viewId = ViewId(SYSTEM_READ_TRANSACTIONS_BERLIN_GROUP_VIEW_ID)
          bankIdAccountId = BankIdAccountId(bankAccount.bankId, bankAccount.accountId)
          view <- ViewNewStyle.checkAccountAccessAndGetView(viewId, bankIdAccountId, Full(u), callContext)
          params <- Future { createQueriesByHttpParams(callContext.get.requestHeaders)} map {
            x => fullBoxOrException(x ~> APIFailureNewStyle(UnknownError, 400, callContext.map(_.toLight)))
          } map { unboxFull(_) }
          bookingStatus = getHttpRequestUrlParam(cc.url, "bookingStatus")
          _ <- booleanToFuture(s"$InvalidUrlParameters bookingStatus parameter must take two one of those values : booked, pending or both!", 400, callContext) {
            bookingStatus match {
              case "booked" | "pending" | "both" => true
              case _ => false
            }
          }
          (transactions, callContext) <- bankAccount.getModeratedTransactionsFuture(bank, Full(u), view, callContext, params) map {
            x => fullBoxOrException(x ~> APIFailureNewStyle(UnknownError, 400, callContext.map(_.toLight)))
          } map { unboxFull(_) }
        } yield {
          JSONFactory_BERLIN_GROUP_1_3.createTransactionsJson(bankAccount, transactions, bookingStatus)
        }
      }
  }

  // ── GET /accounts/ACCOUNT_ID ────────────────────────────────────────────
  lazy val getAccountDetails: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `bgV13Prefix` / "accounts" / accountId =>
      EndpointHelpers.executeAndRespond(req) { cc =>
        val callContext = Some(cc)
        val u = cc.user.openOrThrowException(AuthenticatedUserIsRequired)
        for {
          withBalanceParam <- NewStyle.function.tryons(s"$InvalidUrlParameters withBalance parameter can only take two values: TRUE or FALSE!", 400, callContext) {
            val withBalance = getHttpRequestUrlParam(cc.url, "withBalance")
            if (withBalance.isEmpty) Some(false) else Some(withBalance.toBoolean)
          }
          _ <- passesPsd2Aisp(callContext)
          (account: BankAccount, callContext) <- NewStyle.function.getBankAccountByAccountId(AccountId(accountId), callContext)
          (canReadBalancesAccounts, callContext) <- NewStyle.function.getAccountCanReadBalancesOfBerlinGroup(u, callContext)
          (canReadTransactionsAccounts, callContext) <- NewStyle.function.getAccountCanReadTransactionsOfBerlinGroup(u, callContext)
          _ <- checkAccountAccess(ViewId(SYSTEM_READ_ACCOUNTS_BERLIN_GROUP_VIEW_ID), u, account, callContext)
          (accountBalances, callContext) <- code.api.util.newstyle.BankAccountBalanceNewStyle.getBankAccountBalances(
            AccountId(accountId),
            callContext
          )
        } yield {
          JSONFactory_BERLIN_GROUP_1_3.createAccountDetailsJson(
            account,
            canReadBalancesAccounts,
            canReadTransactionsAccounts,
            withBalanceParam,
            accountBalances,
            u
          )
        }
      }
  }

  // ── GET /card-accounts/ACCOUNT_ID ───────────────────────────────────────
  lazy val readCardAccount: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `bgV13Prefix` / "card-accounts" / accountId =>
      EndpointHelpers.executeAndRespond(req) { cc =>
        val callContext = Some(cc)
        val u = cc.user.openOrThrowException(AuthenticatedUserIsRequired)
        for {
          _ <- passesPsd2Aisp(callContext)
          (account: BankAccount, callContext) <- NewStyle.function.getBankAccountByAccountId(AccountId(accountId), callContext)
          (canReadBalancesAccounts, callContext) <- NewStyle.function.getAccountCanReadBalancesOfBerlinGroup(u, callContext)
          (canReadTransactionsAccounts, callContext) <- NewStyle.function.getAccountCanReadTransactionsOfBerlinGroup(u, callContext)
          _ <- checkAccountAccess(ViewId(SYSTEM_READ_ACCOUNTS_BERLIN_GROUP_VIEW_ID), u, account, callContext)
          withBalanceParam <- NewStyle.function.tryons(s"$InvalidUrlParameters withBalance parameter can only take two values: TRUE or FALSE!", 400, callContext) {
            val withBalance = getHttpRequestUrlParam(cc.url, "withBalance")
            if (withBalance.isEmpty) Some(false) else Some(withBalance.toBoolean)
          }
          (accountBalances, callContext) <- code.api.util.newstyle.BankAccountBalanceNewStyle.getBankAccountBalances(
            AccountId(accountId),
            callContext
          )
        } yield {
          JSONFactory_BERLIN_GROUP_1_3.createCardAccountDetailsJson(
            account,
            canReadBalancesAccounts,
            canReadTransactionsAccounts,
            withBalanceParam,
            accountBalances,
            u
          )
        }
      }
  }

  /**
   * The PSU-ID request header, resolved to the user id it names.
   *
   * Berlin Group makes the header conditional rather than mandatory, so absent is a conforming
   * answer and gives None -- the caller may be identifying the PSU some other way, which
   * Consent.resolveBerlinGroupPsu works out. A value the ASPSP cannot resolve is a different matter
   * and is refused with the code the standard reserves for exactly it: PSU_CREDENTIALS_INVALID, 401,
   * "PSU-ID cannot be found by ASPSP".
   */
  private def resolvePsuIdHeader(cc: CallContext, callContext: Option[CallContext]): Future[Option[String]] =
    Option(APIUtil.getRequestHeader(RequestHeader.`PSU-ID`, cc.requestHeaders)).map(_.trim).filter(_.nonEmpty) match {
      case None => Future.successful(None)
      case Some(psuId) =>
        Future(Consent.findPsuByPsuId(psuId)) map { psu =>
          Some(unboxFullOrFail(psu, callContext, UserNotFoundByProviderAndUsername, 401).userId)
        }
    }

  // ── POST /consents/CONSENTID/authorisations (3 body-guard variants) ─────
  lazy val startConsentAuthorisationAll: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ POST -> `bgV13Prefix` / "consents" / consentId / "authorisations" =>
      EndpointHelpers.executeFutureCreated(req) {
        val cc = req.callContext
        val callContext = Some(cc)
        val parsedJson = scala.util.Try(json.parse(cc.httpBody.getOrElse(""))).getOrElse(json.JNothing)
        if (startsAuthorisation(parsedJson)) {
          for {
            _ <- passesPsd2Aisp(callContext)
            consent <- Future(Consents.consentProvider.vend.getConsentByConsentId(consentId)) map {
              unboxFullOrFail(_, callContext, ConsentNotFound, 403)
            }
            // Starting an authorisation is the entry to claiming the consent: the challenge minted
            // here is what the PUT twin answers before binding a PSU. Without this, any authenticated
            // caller could raise a challenge on any consent id and then answer their own.
            _ <- Consent.checkBerlinGroupConsentAccess(
              consent.userId, consent.consumerId,
              Consent.genuinePsu(cc).map(_.userId), cc.consumer.map(_.consumerId.get),
              Consent.isScaFrontEnd(cc.consumer.map(_.consumerId.get))) match {
              case Some(reason) => booleanToFuture(failMsg = reason, failCode = 403, cc = callContext)(false)
              case None => Future.successful(true)
            }
            headerPsuUserId <- resolvePsuIdHeader(cc, callContext)
            // Whose challenge this is, which is also where the OTP gets sent. Not the session's
            // principal: in Berlin Group the caller is the TPP. See Consent.resolveBerlinGroupPsu.
            psuUserId <- Consent.resolveBerlinGroupPsu(
              consent.userId, Consent.genuinePsu(cc).map(_.userId), headerPsuUserId) match {
              case Right(userId) => Future.successful(userId)
              // A PSU-ID contradicting what the ASPSP already knows is an ownership refusal (403,
              // like the guard above); no PSU identifiable at all is the standard's own
              // PSU_CREDENTIALS_INVALID (401). booleanToFuture(false) always fails, so the mapped
              // value is never reached -- it only lines the two branches up.
              case Left(reason) =>
                val failCode = if (reason == ConsentDoesNotMatchUser) 403 else 401
                booleanToFuture(failMsg = reason, failCode = failCode, cc = callContext)(false).map(_ => "")
            }
            // Refuse here rather than at the PUT, even though binding happens there: the next step
            // sends this person an OTP out of band. A consent naming accounts they do not hold can
            // never legitimately bind to them, so minting the challenge would only deliver a code to
            // someone the TPP nominated for an authorisation that must fail.
            (psuForCheck, callContext) <- NewStyle.function.findByUserId(psuUserId, callContext)
            _ <- Consent.assertBerlinGroupConsentAccountsHeld(psuForCheck, consent, callContext)
            (challenges, callContext) <- NewStyle.function.createChallengesC2(
              List(psuUserId),
              ChallengeType.BERLIN_GROUP_CONSENT_CHALLENGE,
              None,
              getSuggestedDefaultScaMethod(),
              Some(StrongCustomerAuthenticationStatus.received),
              Some(consentId),
              None,
              callContext
            )
            challenge <- NewStyle.function.tryons(InvalidConnectorResponseForCreateChallenge, 400, callContext) {
              challenges.head
            }
          } yield {
            createStartConsentAuthorisationJson(consent, challenge)
          }
        } else if (checkUpdatePsuAuthentication(parsedJson) || checkSelectPsuAuthenticationMethod(parsedJson)) {
          // Mocked for the updatePsuAuthentication and selectPsuAuthenticationMethod variants, which
          // are Embedded-approach steps OBP does not implement. Guarded now: this was the
          // unconditional final else, so any body the server could not recognise was answered with
          // this example -- a fabricated authorisationId, returned 201, that matches no challenge.
          // The TPP only discovers it at the PUT, where the id resolves to nothing.
          Future.successful(com.openbankproject.commons.util.JsonAliases.parse(
            """{
                "scaStatus": "received",
                "psuMessage": "Please use your BankApp for transaction Authorisation.",
                "authorisationId": "123auth456.",
                "_links":
                  {
                    "scaStatus":  {"href":"/v1.3/consents/qwer3456tzui7890/authorisations/123auth456"}
                  }
              }"""))
        } else {
          // None of the recognised shapes. A malformed request has to be reported as one; handing
          // back an id that was never minted only moves the failure somewhere harder to read.
          Helper.booleanToFuture(
            failMsg = s"$InvalidJsonFormat The Json body should be empty, or one of " +
              s"updatePsuAuthentication, selectPsuAuthenticationMethod or transactionAuthorisation.",
            failCode = 400, cc = callContext)(false).map(_ => json.parse("{}"))
        }
      }
  }

  // ── PUT /consents/CONSENTID/authorisations/AUTHORISATIONID (4 variants) ─
  lazy val updateConsentsPsuDataAll: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ PUT -> `bgV13Prefix` / "consents" / consentId / "authorisations" / authorisationId =>
      EndpointHelpers.executeAndRespond(req) { cc =>
        val callContext = Some(cc)
        val parsedJson = scala.util.Try(json.parse(cc.httpBody.getOrElse(""))).getOrElse(json.JNothing)
        if (checkTransactionAuthorisation(parsedJson)) {
          for {
            _ <- passesPsd2Aisp(callContext)
            storedConsent <- Future(Consents.consentProvider.vend.getConsentByConsentId(consentId)) map {
              unboxFullOrFail(_, callContext, ConsentNotFound, 403)
            }
            // updateConsentUser below overwrites mUserId unconditionally, so this is the check that
            // decides who a consent ends up belonging to. See Consent.checkBerlinGroupConsentAccess.
            _ <- Consent.checkBerlinGroupConsentAccess(
              storedConsent.userId, storedConsent.consumerId,
              Consent.genuinePsu(cc).map(_.userId), cc.consumer.map(_.consumerId.get),
              Consent.isScaFrontEnd(cc.consumer.map(_.consumerId.get))) match {
              case Some(reason) => booleanToFuture(failMsg = reason, failCode = 403, cc = callContext)(false)
              case None => Future.successful(true)
            }
            failMsg = s"$InvalidJsonFormat The Json body should be the $TransactionAuthorisation "
            updateJson <- NewStyle.function.tryons(failMsg, 400, callContext) {
              parsedJson.extract[TransactionAuthorisation]
            }
            (startedChallenge, callContext) <- NewStyle.function.getChallenge(authorisationId, callContext)
            // The connector's validateChallengeAnswerC4 matches on challengeId alone and ignores the
            // consentId it is handed, so without this a challenge minted on one consent could be
            // answered on another's path -- and the ownership decision below reads off that
            // challenge, so it has to be this consent's.
            _ <- booleanToFuture(
              failMsg = s"$InvalidChallengeChallengeId Current challengeId($authorisationId) does not belong to CONSENTID($consentId) ",
              failCode = 400, cc = callContext)(startedChallenge.consentId.contains(consentId))
            // Who this consent binds to. The POST twin minted the challenge for a particular PSU and
            // the OTP was delivered to that person, so the challenge is the record of whose
            // authorisation this is -- the session is the TPP's and cannot say.
            (psu, callContext) <- NewStyle.function.findByUserId(startedChallenge.expectedUserId, callContext)
            // The binding point, so the holdings check is repeated here rather than trusted from the
            // POST: the two calls are separate requests and an account can change hands between
            // them. It runs before validateChallengeAnswerC4 and before the status update, because
            // none of what follows is transactional -- a refusal after any of it would leave the
            // consent half-claimed.
            _ <- Consent.assertBerlinGroupConsentAccountsHeld(psu, storedConsent, callContext)
            // Berlin Group Embedded has the TPP relay the PSU's OTP, so the identity the answer is
            // validated against is the challenge's PSU rather than the principal on the token. The
            // caller's own right to be here was settled by checkBerlinGroupConsentAccess above.
            (challenge, _) <- NewStyle.function.validateChallengeAnswerC4(
              ChallengeType.BERLIN_GROUP_CONSENT_CHALLENGE,
              None,
              Some(consentId),
              authorisationId,
              updateJson.scaAuthenticationData,
              SuppliedAnswerType.PLAIN_TEXT_VALUE,
              callContext.map(_.copy(user = Full(psu)))
            )
            // Bind an "availableAccounts": "allAccounts" consent to this PSU's own accounts. That
            // shape names no IBAN, so createBerlinGroupConsentJWT wrote no views for it and the
            // consent would otherwise go valid and serve an empty account list. Any other shape
            // resolved its accounts at creation and passes through untouched.
            //
            // On a finalised answer only: a failed or still-pending SCA must not grant anything.
            // And before the status update, because none of this is transactional -- a consent that
            // reached `valid` and then failed to gain its views would be an authorised consent that
            // serves nothing, with no way to repair it through the API. Refuse first, commit after,
            // same ordering as the UK twin in Http4s510.authoriseUKConsent.
            _ <- if (challenge.scaStatus.contains(StrongCustomerAuthenticationStatus.finalised)) {
              Consent.grantBerlinGroupAvailableAccountsAccess(psu, storedConsent)
                .map(unboxFullOrFail(_, callContext, ConsentAccountAccessCannotBeGranted))
            } else {
              Future.successful(storedConsent)
            }
            consent <- challenge.scaStatus match {
              case Some(status) if status == StrongCustomerAuthenticationStatus.finalised =>
                Future(Consents.consentProvider.vend.updateConsentStatus(consentId, ConsentStatus.valid))
              case Some(status) if status == StrongCustomerAuthenticationStatus.failed =>
                Future(Consents.consentProvider.vend.updateConsentStatus(consentId, ConsentStatus.rejected))
              case _ =>
                Future(Consents.consentProvider.vend.getConsentByConsentId(consentId))
            }
            _ <- NewStyle.function.tryons(ConsentUpdateStatusError, 400, callContext) {
              consent.toList.size == 1
            }
            _ <- Future {
              val authContexts = UserAuthContextProvider.userAuthContextProvider.vend.getUserAuthContextsBox(psu.userId)
                .map(_.map(i => BasicUserAuthContext(i.key, i.value)))
              ConsentAuthContextProvider.consentAuthContextProvider.vend.createOrUpdateConsentAuthContexts(consentId, authContexts.getOrElse(Nil))
            } map {
              unboxFullOrFail(_, callContext, ConsentUserAuthContextCannotBeAdded)
            }
            _ <- Future(Consents.consentProvider.vend.updateConsentUser(consentId, psu)) map {
              unboxFullOrFail(_, callContext, ConsentUserCannotBeAdded)
            }
          } yield {
            createPutConsentResponseJson(consent.toList.head)
          }
        } else if (checkUpdatePsuAuthentication(parsedJson)) {
          Future.successful(com.openbankproject.commons.util.JsonAliases.parse(
            """{
               | "scaStatus": "psuAuthenticated",
               | "_links": {
               |  "authoriseTransaction": {"href": "/psd2/v1/payments/1234-wertiq-983/authorisations/123auth456"}
               | }
               |}""".stripMargin))
        } else if (checkSelectPsuAuthenticationMethod(parsedJson)) {
          Future.successful(com.openbankproject.commons.util.JsonAliases.parse(
            """{
               |  "scaStatus": "scaMethodSelected",
               |  "chosenScaMethod": {
               |    "authenticationType": "SMS_OTP",
               |    "authenticationMethodId": "myAuthenticationID"},
               |  "challengeData": {
               |    "otpMaxLength": 6,
               |    "otpFormat": "integer"},
               |  "_links": {
               |    "authoriseTransaction": {"href": "/psd2/v1/payments/1234-wertiq-983/authorisations/123auth456"}
               |  }
               |}""".stripMargin))
        } else if (checkAuthorisationConfirmation(parsedJson)) {
          // authorisationConfirmation variant. Guarded by the checker that already existed for it:
          // this was the unconditional final else, so a body matching none of the four shapes -- an
          // empty one included -- was answered "scaStatus": "finalised", the terminal success state
          // of strong customer authentication, for an authorisation nothing had happened to.
          Future.successful(com.openbankproject.commons.util.JsonAliases.parse(
            """{
               |  "scaStatus": "finalised",
               |  "_links":{
               |    "status":  {"href":"/v1/payments/sepa-credit-transfers/qwer3456tzui7890/status"}
               |  }
               |}""".stripMargin))
        } else {
          // None of the four Berlin Group shapes. Malformed, and it has to say so: claiming an SCA
          // outcome for a request the server could not read is worse than any of them.
          Helper.booleanToFuture(
            failMsg = s"$InvalidJsonFormat The Json body should be one of updatePsuAuthentication, " +
              s"selectPsuAuthenticationMethod, transactionAuthorisation or authorisationConfirmation.",
            failCode = 400, cc = callContext)(false).map(_ => json.parse("{}"))
        }
      }
  }

  private def initConsentResourceDocs(): Unit = {

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(createConsent),
      "POST",
      "/consents",
      "Create consent",
      s"""${mockedDataText(false)}
This method create a consent resource, defining access rights to dedicated accounts of
a given PSU-ID. These accounts are addressed explicitly in the method as
parameters as a core function.

**Side Effects**
When this Consent Request is a request where the "recurringIndicator" equals "true",
and if it exists already a former consent for recurring access on account information
for the addressed PSU, then the former consent automatically expires as soon as the new
consent request is authorised by the PSU.

Optional Extension:
As an option, an ASPSP might optionally accept a specific access right on the access on all psd2 related services for all available accounts.

As another option an ASPSP might optionally also accept a command, where only access rights are inserted without mentioning the addressed account.
The relation to accounts is then handled afterwards between PSU and ASPSP.
This option is not supported for the Embedded SCA Approach.
As a last option, an ASPSP might in addition accept a command with access rights
  * to see the list of available payment accounts or
  * to see the list of available payment accounts with balances.

frequencyPerDay:
       This field indicates the requested maximum frequency for an access without PSU involvement per day.
       For a one-off access, this attribute is set to "1".
       The frequency needs to be greater equal to one.
       If not otherwise agreed bilaterally between TPP and ASPSP, the frequency is less equal to 4.
recurringIndicator:
       "true", if the consent is for recurring access to the account data.
       "false", if the consent is for one access to the account data.
""",
      PostConsentJson(
        access = ConsentAccessJson(
          accounts = Option(List(ConsentAccessAccountsJson(
            iban = Some(code.api.util.ExampleValue.ibanExample.value),
            bban = None,
            pan = None,
            maskedPan = None,
            msisdn = None,
            currency = None,
          ))),
          balances = None,
          transactions = None,
          availableAccounts = None,
          allPsd2 = None
        ),
        recurringIndicator = true,
        validUntil = "2020-12-31",
        frequencyPerDay = 4,
        combinedServiceIndicator = Some(false)
      ),
      PostConsentResponseJson(
        consentId = "1234-wertiq-983",
        consentStatus = "received",
        _links = ConsentLinksV13(Some(Href("/v1.3/consents/1234-wertiq-983/authorisations")))
      ),
      List(AuthenticatedUserIsRequired, UnknownError),
      ApiTag("Account Information Service (AIS)") :: apiTagBerlinGroupM :: Nil,
      authMode = UserOrApplication,
      http4sPartialFunction = Some(createConsent)
    )

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(deleteConsent),
      "DELETE",
      "/consents/CONSENTID",
      "Delete Consent",
      s"""${mockedDataText(false)}
            The TPP can delete an account information consent object if needed.""",
      EmptyBody,
      EmptyBody,
      List(AuthenticatedUserIsRequired, UnknownError),
      ApiTag("Account Information Service (AIS)") :: apiTagBerlinGroupM :: Nil,
      authMode = UserOrApplication,
      http4sPartialFunction = Some(deleteConsent)
    )

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(getConsentInformation),
      "GET",
      "/consents/CONSENTID",
      "Get Consent Request",
      s"""${mockedDataText(false)}
Returns the content of an account information consent object.
This is returning the data for the TPP especially in cases,
where the consent was directly managed between ASPSP and PSU e.g. in a re-direct SCA Approach.
""",
      EmptyBody,
      JvalueCaseClass(json.parse("""{
                    "access": {
                      "accounts": [
                        {
                          "bban": "BARC12345612345678",
                          "maskedPan": "123456xxxxxx1234",
                          "iban": "FR7612345987650123456789014",
                          "currency": "EUR",
                          "msisdn": "+49 170 1234567",
                          "pan": "5409050000000000"
                        }
                      ]
                    },
                    "recurringIndicator": false,
                    "validUntil": "2020-12-31",
                    "frequencyPerDay": 4,
                    "combinedServiceIndicator": false,
                    "lastActionDate": "2019-06-30",
                    "consentStatus": "received"
                  }""")),
      List(AuthenticatedUserIsRequired, ConsentNotFound, UnknownError),
      ApiTag("Account Information Service (AIS)") :: apiTagBerlinGroupM :: Nil,
      authMode = UserOrApplication,
      http4sPartialFunction = Some(getConsentInformation)
    )

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(getConsentStatus),
      "GET",
      "/consents/CONSENTID/status",
      "Consent status request",
      s"""${mockedDataText(false)}
            Read the status of an account information consent resource.""",
      EmptyBody,
      JvalueCaseClass(json.parse("""{
                    "consentStatus": "received"
                   }""")),
      List(AuthenticatedUserIsRequired, UnknownError),
      ApiTag("Account Information Service (AIS)") :: apiTagBerlinGroupM :: Nil,
      authMode = UserOrApplication,
      http4sPartialFunction = Some(getConsentStatus)
    )

    val generalStartConsentAuthorisationSummary =
      s"""${mockedDataText(false)}
Create an authorisation sub-resource and start the authorisation process of a consent.
The message might in addition transmit authentication and authorisation related data.
his method is iterated n times for a n times SCA authorisation in a corporate context,
each creating an own authorisation sub-endpoint for the corresponding PSU authorising the consent.
The ASPSP might make the usage of this access method unnecessary, since the related authorisation
resource will be automatically created by the ASPSP after the submission of the consent data with the
first POST consents call. The start authorisation process is a process which is needed for creating
a new authorisation or cancellation sub-resource.

This applies in the following scenarios: * The ASPSP has indicated with an 'startAuthorisation' hyperlink
in the preceding Payment Initiation Response that an explicit start of the authorisation process is needed by the TPP.
The 'startAuthorisation' hyperlink can transport more information about data which needs to be uploaded by using
the extended forms.
* 'startAuthorisationWithPsuIdentfication',
* 'startAuthorisationWithPsuAuthentication'
* 'startAuthorisationWithEncryptedPsuAuthentication'
* 'startAuthorisationWithAuthentciationMethodSelection'
* The related payment initiation cannot yet be executed since a multilevel SCA is mandated.
* The ASPSP has indicated with an 'startAuthorisation' hyperlink in the preceding Payment Cancellation
Response that an explicit start of the authorisation process is needed by the TPP.

The 'startAuthorisation' hyperlink can transport more information about data which needs to be uploaded by
using the extended forms as indicated above.
* The related payment cancellation request cannot be applied yet since a multilevel SCA is mandate for executing the cancellation.
* The signing basket needs to be authorised yet.

"""

    val startConsentAuthorisationResponse = JvalueCaseClass(json.parse("""{
                     "scaStatus": "received",
                     "psuMessage": "Please use your BankApp for transaction Authorisation.",
                     "authorisationId": "123auth456.",
                     "_links":
                       {
                         "scaStatus":  {"href":"/v1.3/consents/qwer3456tzui7890/authorisations/123auth456"}
                       }
                   }"""))

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      "startConsentAuthorisationTransactionAuthorisation",
      "POST",
      "/consents/CONSENTID/authorisations",
      "Start the authorisation process for a consent(transactionAuthorisation)",
      generalStartConsentAuthorisationSummary,
      JvalueCaseClass(json.parse("""{"scaAuthenticationData":""}""")),
      startConsentAuthorisationResponse,
      List(AuthenticatedUserIsRequired, UnknownError),
      ApiTag("Account Information Service (AIS)") :: apiTagBerlinGroupM :: Nil,
      // Berlin Group's Embedded SCA step, and the TPP is the caller: the PSU either authenticated
      // at the ASPSP under Redirect or handed its factors to the TPP under Embedded. Which PSU the
      // challenge is for no longer comes from the session -- see Consent.resolveBerlinGroupPsu --
      // so the doc can now say what the call actually is, as its GET siblings already do. Left on
      // the UserOnly default it would 401 a client-credentials caller the day OAuth2 token parsing
      // stops auto-vivifying a user.
      authMode = UserOrApplication,
      http4sPartialFunction = Some(startConsentAuthorisationAll)
    )

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      "startConsentAuthorisationUpdatePsuAuthentication",
      "POST",
      "/consents/CONSENTID/authorisations",
      "Start the authorisation process for a consent(updatePsuAuthentication)",
      generalStartConsentAuthorisationSummary,
      JvalueCaseClass(json.parse("""{"psuData": {"password": "start12"}}""")),
      startConsentAuthorisationResponse,
      List(AuthenticatedUserIsRequired, UnknownError),
      ApiTag("Account Information Service (AIS)") :: apiTagBerlinGroupM :: Nil,
      authMode = UserOrApplication,
      http4sPartialFunction = Some(startConsentAuthorisationAll)
    )

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      "startConsentAuthorisationSelectPsuAuthenticationMethod",
      "POST",
      "/consents/CONSENTID/authorisations",
      "Start the authorisation process for a consent(selectPsuAuthenticationMethod)",
      generalStartConsentAuthorisationSummary,
      JvalueCaseClass(json.parse("""{"authenticationMethodId":""}""")),
      startConsentAuthorisationResponse,
      List(AuthenticatedUserIsRequired, UnknownError),
      ApiTag("Account Information Service (AIS)") :: apiTagBerlinGroupM :: Nil,
      authMode = UserOrApplication,
      http4sPartialFunction = Some(startConsentAuthorisationAll)
    )

    val generalUpdateConsentsPsuDataSummary =
      s"""${mockedDataText(false)}
This method update PSU data on the consents resource if needed. It may authorise a consent within the Embedded
SCA Approach where needed. Independently from the SCA Approach it supports
e.g. the selection of the authentication method and a non-SCA PSU authentication.
This methods updates PSU data on the cancellation authorisation resource if needed.
There are several possible Update PSU Data requests in the context of a consent request if needed,
which depends on the SCA approach: * Redirect SCA Approach: A specific Update PSU Data Request is applicable
for
* the selection of authentication methods, before choosing the actual SCA approach.
* Decoupled SCA Approach: A specific Update PSU Data Request is only applicable for
* adding the PSU Identification, if not provided yet in the Payment Initiation Request or the Account Information Consent Request,
or if no OAuth2 access token is used, or
* the selection of authentication methods.
* Embedded SCA Approach: The Update PSU Data Request might be used
* to add credentials as a first factor authentication data of the PSU and
* to select the authentication method and
* transaction authorisation.
The SCA Approach might depend on the chosen SCA method. For that reason,
the following possible Update PSU Data request can apply to all SCA approaches:
* Select an SCA method in case of several SCA methods are available for the customer. There are the following request types on this access path:
* Update PSU Identification * Update PSU Authentication
* Select PSU Autorization Method WARNING: This method need a reduced header, therefore many optional elements are not present.
Maybe in a later version the access path will change.
* Transaction Authorisation WARNING: This method need a reduced header, therefore many optional elements are not present.
Maybe in a later version the access path will change.

          """

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      "updateConsentsPsuDataTransactionAuthorisation",
      "PUT",
      "/consents/CONSENTID/authorisations/AUTHORISATIONID",
      "Update PSU Data for consents (transactionAuthorisation)",
      generalUpdateConsentsPsuDataSummary,
      JvalueCaseClass(json.parse("""{"scaAuthenticationData":"123"}""")),
      ScaStatusResponse(
        scaStatus = "received",
        _links = Some(LinksAll(scaStatus = Some(HrefType(Some(s"/v1.3/consents/1234-wertiq-983/authorisations")))))
      ),
      List(AuthenticatedUserIsRequired, UnknownError),
      ApiTag("Account Information Service (AIS)") :: apiTagBerlinGroupM :: Nil,
      authMode = UserOrApplication,
      http4sPartialFunction = Some(updateConsentsPsuDataAll)
    )

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      "updateConsentsPsuDataUpdatePsuAuthentication",
      "PUT",
      "/consents/CONSENTID/authorisations/AUTHORISATIONID",
      "Update PSU Data for consents (updatePsuAuthentication)",
      generalUpdateConsentsPsuDataSummary,
      JvalueCaseClass(json.parse("""{"psuData": {"password": "start12"}}""".stripMargin)),
      JvalueCaseClass(json.parse("""{
         |          "scaStatus": "psuAuthenticated",
         |          "_links": {
         |           "authoriseTransaction": {"href": "/psd2/v1/payments/1234-wertiq-983/authorisations/123auth456"}
         |          }
         |        }""".stripMargin)),
      List(AuthenticatedUserIsRequired, UnknownError),
      ApiTag("Account Information Service (AIS)") :: apiTagBerlinGroupM :: Nil,
      authMode = UserOrApplication,
      http4sPartialFunction = Some(updateConsentsPsuDataAll)
    )

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      "updateConsentsPsuDataUpdateSelectPsuAuthenticationMethod",
      "PUT",
      "/consents/CONSENTID/authorisations/AUTHORISATIONID",
      "Update PSU Data for consents (selectPsuAuthenticationMethod)",
      generalUpdateConsentsPsuDataSummary,
      JvalueCaseClass(json.parse("""{
                   |  "authenticationMethodId": "myAuthenticationID"
                   |}""".stripMargin)),
      JvalueCaseClass(json.parse("""{
                   |          "scaStatus": "scaMethodSelected",
                   |          "chosenScaMethod": {
                   |          "authenticationType": "SMS_OTP",
                   |          "authenticationMethodId": "myAuthenticationID"},
                   |          "challengeData": {
                   |          "otpMaxLength": 6,
                   |          "otpFormat": "integer"},
                   |          "_links": {
                   |             "authoriseTransaction": {"href": "/psd2/v1/payments/1234-wertiq-983/authorisations/123auth456"}
                   |          }
                   |        }""".stripMargin)),
      List(AuthenticatedUserIsRequired, UnknownError),
      ApiTag("Account Information Service (AIS)") :: apiTagBerlinGroupM :: Nil,
      authMode = UserOrApplication,
      http4sPartialFunction = Some(updateConsentsPsuDataAll)
    )

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      "updateConsentsPsuDataUpdateAuthorisationConfirmation",
      "PUT",
      "/consents/CONSENTID/authorisations/AUTHORISATIONID",
      "Update PSU Data for consents (authorisationConfirmation)",
      generalUpdateConsentsPsuDataSummary,
      JvalueCaseClass(json.parse("""{"confirmationCode":"confirmationCode"}""")),
      JvalueCaseClass(json.parse("""{
                   |          "scaStatus": "finalised",
                   |          "_links":{
                   |            "status":  {"href":"/v1/payments/sepa-credit-transfers/qwer3456tzui7890/status"}
                   |          }
                   |        }""".stripMargin)),
      List(AuthenticatedUserIsRequired, UnknownError),
      ApiTag("Account Information Service (AIS)") :: apiTagBerlinGroupM :: Nil,
      authMode = UserOrApplication,
      http4sPartialFunction = Some(updateConsentsPsuDataAll)
    )
  }

  private def initAccountResourceDocs(): Unit = {

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(getAccountList),
      "GET",
      "/accounts",
      "Read Account List",
      s"""${mockedDataText(false)}
Read the identifiers of the available payment account together with
booking balance information, depending on the consent granted.

It is assumed that a consent of the PSU to this access is already given and stored on the ASPSP system.
The addressed list of accounts depends then on the PSU ID and the stored consent addressed by consentId,
respectively the OAuth2 access token.

Returns all identifiers of the accounts, to which an account access has been granted to through
the /consents endpoint by the PSU.
In addition, relevant information about the accounts and hyperlinks to corresponding account
information resources are provided if a related consent has been already granted.

Remark: Note that the /consents endpoint optionally offers to grant an access on all available
payment accounts of a PSU.
In this case, this endpoint will deliver the information about all available payment accounts
of the PSU at this ASPSP.
""",
      EmptyBody,
      JvalueCaseClass(json.parse("""{
                   |  "accounts": [
                   |    {
                   |      "resourceId": "3dc3d5b3-7023-4848-9853-f5400a64e80f",
                   |      "iban": "DE2310010010123456789",
                   |      "currency": "EUR",
                   |      "product": "Girokonto",
                   |      "cashAccountType": "CACC",
                   |      "name": "Main Account",
                   |      "_links": {
                   |        "balances": {
                   |          "href": "/v1/accounts/3dc3d5b3-7023-4848-9853-f5400a64e80f/balances"
                   |        }
                   |      }
                   |    }
                   |  ]
                   |}""".stripMargin)),
      List(AuthenticatedUserIsRequired, UnknownError),
      ApiTag("Account Information Service (AIS)") :: apiTagBerlinGroupM :: Nil,
      http4sPartialFunction = Some(getAccountList)
    )

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(getBalances),
      "GET",
      "/accounts/ACCOUNT_ID/balances",
      "Read Balance",
      s"""${mockedDataText(false)}
Reads account data from a given account addressed by "account-id".

**Remark:** This account-id can be a tokenised identification due to data protection reason since the path
information might be logged on intermediary servers within the ASPSP sphere.
This account-id then can be retrieved by the "GET Account List" call.

The account-id is constant at least throughout the lifecycle of a given consent.
""",
      EmptyBody,
      JvalueCaseClass(json.parse("""{
  "account":{
    "iban":"DE91 1000 0000 0123 4567 89"
  },
  "balances":[{
    "balanceAmount":{
      "currency":"EUR",
      "amount":"50.89"
    },
    "balanceType":"AC",
    "lastChangeDateTime":"yyyy-MM-dd'T'HH:mm:ss.SSSZ",
    "lastCommittedTransaction":"String",
    "referenceDate":"2018-03-08"
  }]
}
""")),
      List(AuthenticatedUserIsRequired, UnknownError),
      ApiTag("Account Information Service (AIS)") :: apiTagBerlinGroupM :: Nil,
      http4sPartialFunction = Some(getBalances)
    )

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(getCardAccounts),
      "GET",
      "/card-accounts",
      "Reads a list of card accounts",
      s"""${mockedDataText(false)}
Reads a list of card accounts with additional information, e.g. balance information.
It is assumed that a consent of the PSU to this access is already given and stored on the ASPSP system.
The addressed list of card accounts depends then on the PSU ID and the stored consent addressed by consentId,
respectively the OAuth2 access token.
""",
      EmptyBody,
      JvalueCaseClass(json.parse("""{
  "cardAccounts": [
    {
      "resourceId": "3d9a81b3-a47d-4130-8765-a9c0ff861b99",
      "maskedPan": "525412******3241",
      "currency": "EUR",
      "name": "Main",
      "product": "Basic Credit",
      "status": "enabled",
      "creditLimit": {
        "currency": "EUR",
        "amount": 15000
      },
      "_links": {
        "balances": {
          "href": "/v1/card-accounts/3d9a81b3-a47d-4130-8765-a9c0ff861b99/balances"
        }
      }
    }
  ]
}""")),
      List(AuthenticatedUserIsRequired, UnknownError),
      ApiTag("Account Information Service (AIS)") :: apiTagMockedData :: Nil,
      http4sPartialFunction = Some(getCardAccounts)
    )

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(getCardAccountBalances),
      "GET",
      "/card-accounts/ACCOUNT_ID/balances",
      "Read card account balances",
      s"""${mockedDataText(false)}
Reads balance data from a given card account addressed by
"account-id".

Remark: This account-id can be a tokenised identification due
to data protection reason since the path information might be
logged on intermediary servers within the ASPSP sphere.
This account-id then can be retrieved by the
"GET Card Account List" call
""",
      EmptyBody,
      JvalueCaseClass(json.parse("""{
  "cardAccount":{
    "iban":"DE91 1000 0000 0123 4567 89"
  },
  "balances":[{
    "balanceAmount":{
      "currency":"EUR",
      "amount":"50.89"
    },
    "balanceType":"AC",
    "lastChangeDateTime":"yyyy-MM-dd'T'HH:mm:ss.SSSZ",
    "lastCommittedTransaction":"String",
    "referenceDate":"2018-03-08"
  }]
}""")),
      List(AuthenticatedUserIsRequired, UnknownError),
      ApiTag("Account Information Service (AIS)") :: Nil,
      http4sPartialFunction = Some(getCardAccountBalances)
    )

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(getCardAccountTransactionList),
      "GET",
      "/card-accounts/ACCOUNT_ID/transactions",
      "Read transaction list of a card account",
      s"""${mockedDataText(false)}
Reads account data from a given card account addressed by "account-id".
""",
      EmptyBody,
      JvalueCaseClass(json.parse("""{
                    "cardAccount": {
                      "maskedPan": "525412******3241"
                    },
                    "transactions": {
                      "booked": [],
                      "_links": {
                        "cardAccount": {
                          "href": "/v1.3/card-accounts/3d9a81b3-a47d-4130-8765-a9c0ff861b99"
                        }
                      }
                    }
                  }""")),
      List(AuthenticatedUserIsRequired, UnknownError),
      ApiTag("Account Information Service (AIS)") :: apiTagBerlinGroupM :: Nil,
      http4sPartialFunction = Some(getCardAccountTransactionList)
    )

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(getConsentAuthorisation),
      "GET",
      "/consents/CONSENTID/authorisations",
      "Get Consent Authorisation Sub-Resources Request",
      s"""${mockedDataText(false)}
Return a list of all authorisation subresources IDs which have been created.

This function returns an array of hyperlinks to all generated authorisation sub-resources.
""",
      EmptyBody,
      JvalueCaseClass(json.parse("""{
  "authorisationIds" : "faa3657e-13f0-4feb-a6c3-34bf21a9ae8e"
}""")),
      List(AuthenticatedUserIsRequired, UnknownError),
      ApiTag("Account Information Service (AIS)") :: apiTagBerlinGroupM :: Nil,
      // The TPP reads its own consent's authorisation sub-resources; no PSU is party to the call.
      // The Implementation Guidelines put it plainly (§4.6, Authorisation Endpoints): the ASPSP
      // "will give access to these sub-resources to the TPP by returning corresponding hyperlinks",
      // and "the authorisation status would still result by submitting the command GET
      // .../authorisations/authorisationId". In Berlin Group the PSU never calls the API at all --
      // it authenticates at the ASPSP under Redirect, or hands its factors to the TPP under
      // Embedded. The handler already reflects that, taking no user; only the doc disagreed, and a
      // doc left on the UserOnly default sends the middleware down anonymousAccess, which 401s a
      // request carrying no user. Brings these into line with the consent endpoints in this same
      // file, which have been UserOrApplication all along.
      authMode = UserOrApplication,
      http4sPartialFunction = Some(getConsentAuthorisation)
    )

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(getConsentScaStatus),
      "GET",
      "/consents/CONSENTID/authorisations/AUTHORISATIONID",
      "Read the SCA status of the consent authorisation",
      s"""${mockedDataText(false)}
This method returns the SCA status of a consent initiation's authorisation sub-resource.
""",
      EmptyBody,
      JvalueCaseClass(json.parse("""{
  "scaStatus" : "started"
}""")),
      List(AuthenticatedUserIsRequired, UnknownError),
      ApiTag("Account Information Service (AIS)") :: apiTagBerlinGroupM :: Nil,
      // As above -- reading the SCA status is the TPP polling its own authorisation sub-resource.
      authMode = UserOrApplication,
      http4sPartialFunction = Some(getConsentScaStatus)
    )

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(getTransactionDetails),
      "GET",
      "/accounts/ACCOUNT_ID/transactions/TRANSACTIONID",
      "Read Transaction Details",
      s"""${mockedDataText(false)}
Reads transaction details from a given transaction addressed by "transactionId" on a given account addressed
by "account-id". This call is only available on transactions as reported in a JSON format.

**Remark:** Please note that the PATH might be already given in detail by the corresponding entry of the response
of the "Read Transaction List" call within the _links subfield.

            """,
      EmptyBody,
      JvalueCaseClass(json.parse("""{
  "description": "Example for transaction details",
  "value": {
    "transactionsDetails": {
      "transactionId": "1234567",
      "creditorName": "John Miles",
      "transactionAmount": {
        "currency": "EUR",
        "amount": "-256.67"
      },
      "bookingDate": "2017-10-25",
      "valueDate": "2017-10-26"
    }
  }
}""")),
      List(AuthenticatedUserIsRequired, UnknownError),
      ApiTag("Account Information Service (AIS)") :: Nil,
      http4sPartialFunction = Some(getTransactionDetails)
    )

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(getTransactionList),
      "GET",
      "/accounts/ACCOUNT_ID/transactions",
      "Read transaction list of an account",
      s"""${mockedDataText(false)}
Read transaction reports or transaction lists of a given account addressed by "account-id",
depending on the steering parameter "bookingStatus" together with balances.
For a given account, additional parameters are e.g. the attributes "dateFrom" and "dateTo".
The ASPSP might add balance information, if transaction lists without balances are not supported. """,
      EmptyBody,
      JvalueCaseClass(json.parse("""{
                    "account": {
                      "iban": "DE2310010010123456788"
                    },
                    "transactions": {
                      "booked": [],
                      "_links": {
                        "account": {
                          "href": "/v1.3/accounts/3dc3d5b3-7023-4848-9853-f5400a64e80f"
                        }
                      }
                    }
                  }""")),
      List(AuthenticatedUserIsRequired, UnknownError),
      ApiTag("Account Information Service (AIS)") :: apiTagBerlinGroupM :: Nil,
      http4sPartialFunction = Some(getTransactionList)
    )

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(getAccountDetails),
      "GET",
      "/accounts/ACCOUNT_ID",
      "Read Account Details",
      s"""${mockedDataText(false)}
Reads details about an account, with balances where required.
It is assumed that a consent of the PSU to this access is already given and stored on the ASPSP system.
The addressed details of this account depends then on the stored consent addressed by consentId,
respectively the OAuth2 access token. **NOTE:** The account-id can represent a multicurrency account.
In this case the currency code is set to "XXX". Give detailed information about the addressed account.
Give detailed information about the addressed account together with balance information

            """,
      EmptyBody,
      JvalueCaseClass(json.parse("""{
  "account": {
    "resourceId": "3dc3d5b3-7023-4848-9853-f5400a64e80f",
    "iban": "FR7612345987650123456789014",
    "currency": "EUR",
    "product": "Girokonto",
    "cashAccountType": "CACC",
    "name": "Main Account",
    "_links": {
      "balances": {
        "href": "/v1/accounts/3dc3d5b3-7023-4848-9853-f5400a64e80f/balances"
      }
    }
  }
}""")),
      List(AuthenticatedUserIsRequired, UnknownError),
      ApiTag("Account Information Service (AIS)") :: apiTagBerlinGroupM :: Nil,
      http4sPartialFunction = Some(getAccountDetails)
    )

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(readCardAccount),
      "GET",
      "/card-accounts/ACCOUNT_ID",
      "Reads details about a card account",
      s"""${mockedDataText(false)}
Reads details about a card account.
It is assumed that a consent of the PSU to this access is already given and stored on the ASPSP system.
The addressed details of this account depends then on the stored consent addressed by consentId,
respectively the OAuth2 access token.
""",
      EmptyBody,
      JvalueCaseClass(json.parse("""{
                   |  "cardAccount": {
                   |    "resourceId": "3d9a81b3-a47d-4130-8765-a9c0ff861b99",
                   |    "maskedPan": "525412******3241",
                   |    "currency": "EUR",
                   |    "name": "Main",
                   |    "product": "Basic Credit",
                   |    "status": "enabled"
                   |  }
                   |}""".stripMargin)),
      List(AuthenticatedUserIsRequired, UnknownError),
      ApiTag("Account Information Service (AIS)") :: Nil,
      http4sPartialFunction = Some(readCardAccount)
    )
  }

  initConsentResourceDocs()
  initAccountResourceDocs()

  val routes: HttpRoutes[IO] = Kleisli[HttpF, Request[IO], Response[IO]] { req =>
    createConsent(req)
      .orElse(deleteConsent(req))
      .orElse(getAccountList(req))
      .orElse(getBalances(req))
      .orElse(getCardAccounts(req))
      .orElse(getCardAccountBalances(req))
      .orElse(getCardAccountTransactionList(req))
      .orElse(getConsentAuthorisation(req))
      .orElse(getConsentInformation(req))
      .orElse(getConsentScaStatus(req))
      .orElse(getConsentStatus(req))
      .orElse(getTransactionDetails(req))
      .orElse(getTransactionList(req))
      .orElse(getAccountDetails(req))
      .orElse(readCardAccount(req))
      .orElse(startConsentAuthorisationAll(req))
      .orElse(updateConsentsPsuDataAll(req))
  }
}
