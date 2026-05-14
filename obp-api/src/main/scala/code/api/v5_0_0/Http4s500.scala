package code.api.v5_0_0

import cats.data.{Kleisli, OptionT}
import cats.effect._
import code.accountattribute.AccountAttributeX
import code.api.Constant._
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON._
import code.api.util.APIUtil._
import code.api.util.ApiRole
import code.api.util.ApiRole._
import code.api.util.ApiTag._
import code.api.util.ErrorMessages
import code.api.util.ErrorMessages._
import code.api.util.http4s.Http4sRequestAttributes.{EndpointHelpers, RequestOps}
import code.api.util.http4s.ResourceDocMiddleware
import code.api.util.newstyle.ViewNewStyle
import code.api.util.{APIUtil, ConsentJWT, Consent, CustomJsonFormats, JwtUtil, NewStyle, OBPBankId, SecureRandomUtil}
import code.api.v2_1_0.JSONFactory210
import code.api.v3_0_0.JSONFactory300
import code.api.v3_1_0.{JSONFactory310, PostConsentBodyCommonJson, PostConsentViewJsonV310, PostUserAuthContextJson, PostUserAuthContextUpdateJsonV310}
import code.api.v4_0_0.JSONFactory400
import code.api.v4_0_0.JSONFactory400.createCustomersMinimalJson
import code.api.v4_0_0.PostCounterpartyJson400
import code.api.v5_0_0.JSONFactory500.{createPhysicalCardJson, createViewJsonV500, createViewsIdsJsonV500, createViewsJsonV500}
import code.api.v5_1_0.{CreateCustomViewJson, PostCounterpartyLimitV510, PostVRPConsentRequestJsonV510}
import code.bankconnectors.Connector
import code.consent.{ConsentRequests, ConsentStatus, Consents, MappedConsent}
import code.consumer.Consumers
import code.entitlement.Entitlement
import code.metadata.counterparties.MappedCounterparty
import code.metrics.APIMetrics
import code.model.dataAccess.BankAccountCreation
import code.util.Helper
import code.util.Helper.{SILENCE_IS_GOLDEN, booleanToFuture}
import code.views.Views
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.dto.GetProductsParam
import com.openbankproject.commons.model.{
  AccountId, AccountRouting, AccountRoutingJsonV121, Bank, BankAccount, BankAccountRoutings,
  BankId, BankIdAccountId, BankRoutingJson, BranchRoutingJsonV141, CardAction,
  CardCollectionInfo, CardPostedInfo, CardReplacementInfo, CardReplacementReason,
  CounterpartyBespoke, CounterpartyId, CreditLimit, CreditRating, CustomerFaceImage,
  CustomerId, PinResetInfo, PinResetReason, ProductCode, User, UserAuthContextUpdateStatus,
  ViewId
}
import com.openbankproject.commons.model.enums.StrongCustomerAuthentication
import com.openbankproject.commons.util.{ApiVersion, ApiVersionStatus, ScannedApiVersion}
import net.liftweb.common.{Empty, Full}
import net.liftweb.json
import net.liftweb.json.JsonAST.prettyRender
import net.liftweb.json.{Extraction, Formats, compactRender}
import net.liftweb.mapper.By
import net.liftweb.util.{Helpers, Props, StringHelpers}
import org.http4s.{HttpRoutes, MediaType, Method, Request, Response, Status, Uri}
import org.http4s.dsl.io._
import org.typelevel.ci.CIString

import java.util.UUID
import java.util.concurrent.ThreadLocalRandom
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Future
import scala.language.{higherKinds, implicitConversions}
import scala.util.Random

object Http4s500 {

  type HttpF[A] = OptionT[IO, A]

  implicit val formats: Formats = CustomJsonFormats.formats
  implicit def convertAnyToJsonString(any: Any): String = prettyRender(Extraction.decompose(any))

  val implementedInApiVersion: ScannedApiVersion = ApiVersion.v5_0_0
  val versionStatus: String = ApiVersionStatus.STABLE.toString
  val resourceDocs: ArrayBuffer[ResourceDoc] = ArrayBuffer[ResourceDoc]()

  object Implementations5_0_0 {

    val prefixPath = Root / ApiPathZero.toString / implementedInApiVersion.toString

    resourceDocs += ResourceDoc(
      null,
      implementedInApiVersion,
      nameOf(root),
      "GET",
      "/root",
      "Get API Info (root)",
      """Returns information about:
        |
        |* API version
        |* Hosted by information
        |* Hosted at information
        |* Energy source information
        |* Git Commit""",
      EmptyBody,
      apiInfoJson400,
      List(
        UnknownError,
        MandatoryPropertyIsNotSet
      ),
      apiTagApi :: Nil,
      http4sPartialFunction = Some(root)
    )

    val root: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "root" =>
        val responseJson = convertAnyToJsonString(
          JSONFactory400.getApiInfoJSON(OBPAPI5_0_0.version, OBPAPI5_0_0.versionStatus)
        )
        Ok(responseJson)
    }

    resourceDocs += ResourceDoc(
      null,
      implementedInApiVersion,
      nameOf(getBanks),
      "GET",
      "/banks",
      "Get Banks",
      """Get banks on this API instance
        |Returns a list of banks supported on this server:
        |
        |* ID used as parameter in URLs
        |* Short and full name of bank
        |* Logo URL
        |* Website""",
      EmptyBody,
      banksJSON,
      List(
        UnknownError
      ),
      apiTagBank :: Nil,
      http4sPartialFunction = Some(getBanks)
    )

    val getBanks: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" =>
        EndpointHelpers.executeAndRespond(req) { implicit cc =>
          for {
            (banks, callContext) <- NewStyle.function.getBanks(Some(cc))
          } yield JSONFactory400.createBanksJson(banks)
        }
    }

    resourceDocs += ResourceDoc(
      null,
      implementedInApiVersion,
      nameOf(getBank),
      "GET",
      "/banks/BANK_ID",
      "Get Bank",
      """Get the bank specified by BANK_ID
        |Returns information about a single bank specified by BANK_ID including:
        |
        |* Bank code and full name of bank
        |* Logo URL
        |* Website""",
      EmptyBody,
      bankJson500,
      List(
        UnknownError,
        BankNotFound
      ),
      apiTagBank :: apiTagPSD2AIS :: apiTagPsd2 :: Nil,
      http4sPartialFunction = Some(getBank)
    )

    val getBank: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / bankId =>
        EndpointHelpers.withBank(req) { (bank, cc) =>
          for {
            (attributes, callContext) <- NewStyle.function.getBankAttributesByBank(BankId(bankId), Some(cc))
          } yield JSONFactory500.createBankJSON500(bank, attributes)
        }
    }

    private val productsAuthErrorBodies =
      if (getProductsIsPublic) List(BankNotFound, UnknownError)
      else List(AuthenticatedUserIsRequired, BankNotFound, UnknownError)

    resourceDocs += ResourceDoc(
      null,
      implementedInApiVersion,
      nameOf(getProducts),
      "GET",
      "/banks/BANK_ID/products",
      "Get Products",
      s"""Get products offered by the bank specified by BANK_ID.
         |
         |Can filter with attributes name and values.
         |URL params example: /banks/some-bank-id/products?&limit=50&offset=1
         |
         |${code.api.util.APIUtil.userAuthenticationMessage(!getProductsIsPublic)}""".stripMargin,
      EmptyBody,
      productsJsonV400,
      productsAuthErrorBodies,
      List(apiTagProduct),
      http4sPartialFunction = Some(getProducts)
    )

    val getProducts: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / bankId / "products" =>
        EndpointHelpers.executeFuture(req) {
          val cc = req.callContext
          val params = req.uri.query.multiParams.toList.map { case (k, vs) =>
            GetProductsParam(k, vs.toList)
          }
          for {
            (products, callContext) <- NewStyle.function.getProducts(BankId(bankId), params, Some(cc))
          } yield JSONFactory400.createProductsJson(products)
        }
    }

    resourceDocs += ResourceDoc(
      null,
      implementedInApiVersion,
      nameOf(getProduct),
      "GET",
      "/banks/BANK_ID/products/PRODUCT_CODE",
      "Get Bank Product",
      s"""Returns information about a financial Product offered by the bank specified by BANK_ID and PRODUCT_CODE.
         |
         |${code.api.util.APIUtil.userAuthenticationMessage(!getProductsIsPublic)}""".stripMargin,
      EmptyBody,
      productJsonV400,
      productsAuthErrorBodies ::: List(ProductNotFoundByProductCode),
      List(apiTagProduct),
      http4sPartialFunction = Some(getProduct)
    )

    val getProduct: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / bankId / "products" / productCode =>
        EndpointHelpers.executeFuture(req) {
          val cc = req.callContext
          val bankIdObj = BankId(bankId)
          val productCodeObj = ProductCode(productCode)
          for {
            (product, callContext) <- NewStyle.function.getProduct(bankIdObj, productCodeObj, Some(cc))
            (productAttributes, callContext) <- NewStyle.function.getProductAttributesByBankAndCode(bankIdObj, productCodeObj, callContext)
            (productFees, callContext) <- NewStyle.function.getProductFeesFromProvider(bankIdObj, productCodeObj, callContext)
          } yield JSONFactory400.createProductJson(product, productAttributes, productFees)
        }
    }

    resourceDocs += ResourceDoc(
      null,
      implementedInApiVersion,
      nameOf(createSystemView),
      "POST",
      "/system-views",
      "Create System View",
      s"""Create a system view
         |
         |${code.api.util.APIUtil.userAuthenticationMessage(true)} and the user needs to have access to the CanCreateSystemView entitlement.
         |
         |The 'allowed_actions' field is a list containing the names of the actions allowed through this view.
         |All the actions contained in the list will be set to `true` on the view creation, the rest will be set to `false`.
         |
         |System views cannot be public. In case you try to set it you will get the error $SystemViewCannotBePublicError
         |""",
      createSystemViewJsonV500,
      viewJsonV500,
      List(
        AuthenticatedUserIsRequired,
        InvalidJsonFormat,
        SystemViewCannotBePublicError,
        InvalidSystemViewFormat,
        UnknownError
      ),
      apiTagSystemView :: Nil,
      Some(List(canCreateSystemView)),
      http4sPartialFunction = Some(createSystemView)
    )

    val createSystemView: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "system-views" =>
        EndpointHelpers.executeFutureCreated(req) {
          implicit val cc = req.callContext
          val bodyString = cc.httpBody.getOrElse("")
          for {
            createViewJson <- NewStyle.function.tryons(
              s"$InvalidJsonFormat The Json body should be the CreateViewJsonV500",
              400,
              Some(cc)
            ) {
              net.liftweb.json.parse(bodyString).extract[CreateViewJsonV500]
            }
            _ <- code.util.Helper.booleanToFuture(
              SystemViewCannotBePublicError,
              failCode = 400,
              cc = Some(cc)
            )(createViewJson.is_public == false)
            _ <- code.util.Helper.booleanToFuture(
              s"$InvalidSystemViewFormat Current view_name (${createViewJson.name})",
              cc = Some(cc)
            )(code.api.util.APIUtil.isValidSystemViewName(createViewJson.name))
            view <- ViewNewStyle.createSystemView(createViewJson.toCreateViewJson, Some(cc))
          } yield JSONFactory500.createViewJsonV500(view)
        }
    }

    resourceDocs += ResourceDoc(
      null,
      implementedInApiVersion,
      nameOf(getSystemView),
      "GET",
      "/system-views/VIEW_ID",
      "Get System View",
      s"""Get System View
         |
         |${code.api.util.APIUtil.userAuthenticationMessage(true)}
         |""",
      EmptyBody,
      viewJsonV500,
      List(
        AuthenticatedUserIsRequired,
        SystemViewNotFound,
        UnknownError
      ),
      apiTagSystemView :: Nil,
      Some(List(canGetSystemView)),
      http4sPartialFunction = Some(getSystemView)
    )

    val getSystemView: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "system-views" / viewId =>
        EndpointHelpers.executeFuture(req) {
          implicit val cc = req.callContext
          for {
            view <- ViewNewStyle.systemView(ViewId(viewId), Some(cc))
          } yield JSONFactory500.createViewJsonV500(view)
        }
    }

    resourceDocs += ResourceDoc(
      null,
      implementedInApiVersion,
      nameOf(updateSystemView),
      "PUT",
      "/system-views/VIEW_ID",
      "Update System View",
      s"""Update an existing system view
         |
         |${code.api.util.APIUtil.userAuthenticationMessage(true)} and the user needs to have access to the CanUpdateSystemView entitlement.
         |
         |The json sent is the same as during view creation, with one difference: the 'name' field
         |of a view is not editable (it is only set when a view is created)""",
      updateSystemViewJson500,
      viewJsonV500,
      List(
        InvalidJsonFormat,
        AuthenticatedUserIsRequired,
        SystemViewNotFound,
        SystemViewCannotBePublicError,
        UnknownError
      ),
      apiTagSystemView :: Nil,
      Some(List(canUpdateSystemView)),
      http4sPartialFunction = Some(updateSystemView)
    )

    val updateSystemView: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ PUT -> `prefixPath` / "system-views" / viewId =>
        EndpointHelpers.executeFuture(req) {
          implicit val cc = req.callContext
          val bodyString = cc.httpBody.getOrElse("")
          for {
            updateJson <- NewStyle.function.tryons(
              s"$InvalidJsonFormat The Json body should be the UpdateViewJsonV500",
              400,
              Some(cc)
            ) {
              net.liftweb.json.parse(bodyString).extract[UpdateViewJsonV500]
            }
            _ <- code.util.Helper.booleanToFuture(
              SystemViewCannotBePublicError,
              failCode = 400,
              cc = Some(cc)
            )(updateJson.is_public == false)
            _ <- ViewNewStyle.systemView(ViewId(viewId), Some(cc))
            updatedView <- ViewNewStyle.updateSystemView(ViewId(viewId), updateJson.toUpdateViewJson, Some(cc))
          } yield JSONFactory500.createViewJsonV500(updatedView)
        }
    }

    resourceDocs += ResourceDoc(
      null,
      implementedInApiVersion,
      nameOf(deleteSystemView),
      "DELETE",
      "/system-views/VIEW_ID",
      "Delete System View",
      s"""Deletes the system view specified by VIEW_ID
         |
         |${code.api.util.APIUtil.userAuthenticationMessage(true)} and the user needs to have access to the CanDeleteSystemView entitlement.
         |""",
      EmptyBody,
      EmptyBody,
      List(
        AuthenticatedUserIsRequired,
        SystemViewNotFound,
        UnknownError
      ),
      apiTagSystemView :: Nil,
      Some(List(canDeleteSystemView)),
      http4sPartialFunction = Some(deleteSystemView)
    )

    val deleteSystemView: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ DELETE -> `prefixPath` / "system-views" / viewId =>
        EndpointHelpers.executeFuture(req) {
          implicit val cc = req.callContext
          for {
            _ <- ViewNewStyle.systemView(ViewId(viewId), Some(cc))
            result <- ViewNewStyle.deleteSystemView(ViewId(viewId), Some(cc))
          } yield result
        }
    }

    // ─── createBank (POST /banks → 201) — v5 override of v2.2.0/v4 ──────────
    // v5 uses PostBankJson500 (id is Option[String], includes bank_routings).
    // Must live in own routes so the bridge cascade can't hijack down to v4/v2.2.

    val createBank: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "banks" =>
        EndpointHelpers.executeFutureCreated(req) {
          implicit val cc: code.api.util.CallContext = req.callContext
          val failMsg = s"$InvalidJsonFormat The Json body should be the $PostBankJson500 "
          for {
            postJson <- NewStyle.function.tryons(failMsg, 400, Some(cc)) {
              net.liftweb.json.parse(cc.httpBody.getOrElse("")).extract[PostBankJson500]
            }
            checkShortStringValue = APIUtil.checkOptionalShortString(postJson.id.getOrElse(SILENCE_IS_GOLDEN))
            _ <- Helper.booleanToFuture(s"$checkShortStringValue.", cc = Some(cc)) {
              checkShortStringValue == SILENCE_IS_GOLDEN
            }
            _ <- Helper.booleanToFuture(InvalidConsumerCredentials, cc = Some(cc)) {
              cc.consumer.isDefined
            }
            _ <- Helper.booleanToFuture(s"$InvalidJsonFormat Min length of BANK_ID should be greater than 3 characters.", cc = Some(cc)) {
              postJson.id.forall(_.length > 3)
            }
            _ <- Helper.booleanToFuture(s"$InvalidJsonFormat BANK_ID can not contain space characters", cc = Some(cc)) {
              !postJson.id.contains(" ")
            }
            _ <- Helper.booleanToFuture(s"$InvalidJsonFormat BANK_ID can not contain `::::` characters", cc = Some(cc)) {
              !`checkIfContains::::`(postJson.id.getOrElse(""))
            }
            (banks, _) <- NewStyle.function.getBanks(Some(cc))
            _ <- Helper.booleanToFuture(bankIdAlreadyExists, cc = Some(cc)) {
              !banks.exists(b => Some(b.bankId.value) == postJson.id)
            }
            (success, _) <- NewStyle.function.createOrUpdateBank(
              postJson.id.getOrElse(APIUtil.generateUUID()),
              postJson.full_name.getOrElse(""),
              postJson.bank_code,
              postJson.logo.getOrElse(""),
              postJson.website.getOrElse(""),
              postJson.bank_routings.getOrElse(Nil).find(_.scheme == "BIC").map(_.address).getOrElse(""),
              "",
              postJson.bank_routings.getOrElse(Nil).filterNot(_.scheme == "BIC").headOption.map(_.scheme).getOrElse(""),
              postJson.bank_routings.getOrElse(Nil).filterNot(_.scheme == "BIC").headOption.map(_.address).getOrElse(""),
              Some(cc)
            )
            entitlements <- NewStyle.function.getEntitlementsByUserId(cc.userId, Some(cc))
            entitlementsByBank = entitlements.filter(_.bankId == postJson.id.getOrElse(""))
            _ <- entitlementsByBank.exists(_.roleName == CanCreateEntitlementAtOneBank.toString()) match {
              case true  => Future.successful(())
              case false => Future(Entitlement.entitlement.vend.addEntitlement(
                postJson.id.getOrElse(""), cc.userId, CanCreateEntitlementAtOneBank.toString()))
            }
            _ <- entitlementsByBank.exists(_.roleName == CanReadDynamicResourceDocsAtOneBank.toString()) match {
              case true  => Future.successful(())
              case false => Future(Entitlement.entitlement.vend.addEntitlement(
                postJson.id.getOrElse(""), cc.userId, CanReadDynamicResourceDocsAtOneBank.toString()))
            }
          } yield JSONFactory500.createBankJSON500(success)
        }
    }

    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, "createBank", "POST",
      "/banks", "Create Bank",
      s"""Create a new bank (Authenticated access).
         |
         |The user creating this will be automatically assigned the Role CanCreateEntitlementAtOneBank.
         |Thus the User can manage the bank they create and assign Roles to other Users.
         |""",
      postBankJson500, bankJson500,
      List(InvalidJsonFormat, $AuthenticatedUserIsRequired,
        InsufficientAuthorisationToCreateBank, UnknownError),
      List(apiTagBank),
      Some(List(canCreateBank)),
      http4sPartialFunction = Some(createBank)
    )

    // ─── updateBank (PUT /banks → 200) ──────────────────────────────────────

    val updateBank: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ PUT -> `prefixPath` / "banks" =>
        EndpointHelpers.executeFuture(req) {
          implicit val cc: code.api.util.CallContext = req.callContext
          val failMsg = s"$InvalidJsonFormat The Json body should be the $PostBankJson500 "
          for {
            bank <- NewStyle.function.tryons(failMsg, 400, Some(cc)) {
              net.liftweb.json.parse(cc.httpBody.getOrElse("")).extract[PostBankJson500]
            }
            _ <- Helper.booleanToFuture(InvalidConsumerCredentials, cc = Some(cc)) {
              cc.consumer.isDefined
            }
            _ <- Helper.booleanToFuture(s"$InvalidJsonFormat Min length of BANK_ID should be greater than 3 characters.", cc = Some(cc)) {
              bank.id.forall(_.length > 3)
            }
            _ <- Helper.booleanToFuture(s"$InvalidJsonFormat BANK_ID can not contain space characters", cc = Some(cc)) {
              !bank.id.contains(" ")
            }
            bankId <- NewStyle.function.tryons(updateBankError, 400, Some(cc)) {
              bank.id.get
            }
            (_, _) <- NewStyle.function.getBank(BankId(bankId), Some(cc))
            (success, _) <- NewStyle.function.createOrUpdateBank(
              bankId,
              bank.full_name.getOrElse(""),
              bank.bank_code,
              bank.logo.getOrElse(""),
              bank.website.getOrElse(""),
              bank.bank_routings.getOrElse(Nil).find(_.scheme == "BIC").map(_.address).getOrElse(""),
              "",
              bank.bank_routings.getOrElse(Nil).filterNot(_.scheme == "BIC").headOption.map(_.scheme).getOrElse(""),
              bank.bank_routings.getOrElse(Nil).filterNot(_.scheme == "BIC").headOption.map(_.address).getOrElse(""),
              Some(cc)
            )
          } yield JSONFactory500.createBankJSON500(success)
        }
    }

    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, "updateBank", "PUT",
      "/banks", "Update Bank",
      "Update an existing bank (Authenticated access).",
      postBankJson500, bankJson500,
      List(InvalidJsonFormat, $AuthenticatedUserIsRequired, BankNotFound, updateBankError, UnknownError),
      List(apiTagBank),
      Some(List(canCreateBank)),
      http4sPartialFunction = Some(updateBank)
    )

    // ─── createAccount (PUT /banks/BANK_ID/accounts/NEW_ACCOUNT_ID → 201) ───
    // Account doesn't exist yet — use NEW_ACCOUNT_ID so middleware's
    // validateAccount doesn't 404 the create. (See CLAUDE.md gotcha.)

    val createAccount: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ PUT -> `prefixPath` / "banks" / bankIdStr / "accounts" / accountIdStr =>
        EndpointHelpers.executeFutureCreated(req) {
          implicit val cc: code.api.util.CallContext = req.callContext
          val bankId = BankId(bankIdStr)
          val accountId = AccountId(accountIdStr)
          val failMsg = s"$InvalidJsonFormat The Json body should be the ${prettyRender(Extraction.decompose(createAccountRequestJsonV310))} "
          for {
            user <- Future.successful(cc.user.openOrThrowException(AuthenticatedUserIsRequired))
            (account, _) <- Connector.connector.vend.checkBankAccountExists(bankId, accountId, Some(cc))
            _ <- Helper.booleanToFuture(AccountIdAlreadyExists, cc = Some(cc)) { account.isEmpty }
            createAccountJson <- NewStyle.function.tryons(failMsg, 400, Some(cc)) {
              net.liftweb.json.parse(cc.httpBody.getOrElse("")).extract[CreateAccountRequestJsonV500]
            }
            loggedInUserId = user.userId
            userIdAccountOwner = createAccountJson.user_id.getOrElse(loggedInUserId)
            _ <- Helper.booleanToFuture(InvalidAccountIdFormat, cc = Some(cc)) { isValidID(accountId.value) }
            _ <- Helper.booleanToFuture(InvalidBankIdFormat, cc = Some(cc)) { isValidID(accountId.value) }
            (postedOrLoggedInUser, _) <- NewStyle.function.findByUserId(userIdAccountOwner, Some(cc))
            _ <- if (userIdAccountOwner == loggedInUserId) Future.successful(Full(()))
                 else Helper.booleanToFuture(
                   s"${UserHasMissingRoles} $canCreateAccount", failCode = 403, cc = Some(cc)) {
                   APIUtil.hasEntitlement(bankId.value, loggedInUserId, canCreateAccount)
                 }
            initialBalanceAsString = createAccountJson.balance.map(_.amount).getOrElse("0")
            accountType = createAccountJson.product_code
            accountLabel = createAccountJson.label
            initialBalanceAsNumber <- NewStyle.function.tryons(InvalidAccountInitialBalance, 400, Some(cc)) {
              BigDecimal(initialBalanceAsString)
            }
            _ <- Helper.booleanToFuture(InitialBalanceMustBeZero, cc = Some(cc)) { 0 == initialBalanceAsNumber }
            _ <- Helper.booleanToFuture(InvalidISOCurrencyCode, cc = Some(cc)) {
              isValidCurrencyISOCode(createAccountJson.balance.map(_.currency).getOrElse("EUR"))
            }
            currency = createAccountJson.balance.map(_.currency).getOrElse("EUR")
            (_, _) <- NewStyle.function.getBank(bankId, Some(cc))
            _ <- Helper.booleanToFuture(s"$InvalidAccountRoutings Duplication detected in account routings, please specify only one value per routing scheme", 400, cc = Some(cc)) {
              createAccountJson.account_routings.getOrElse(Nil).map(_.scheme).distinct.size == createAccountJson.account_routings.getOrElse(Nil).size
            }
            alreadyExistAccountRoutings <- Future.sequence(createAccountJson.account_routings.getOrElse(Nil).map(accountRouting =>
              NewStyle.function.getAccountRouting(Some(bankId), accountRouting.scheme, accountRouting.address, Some(cc))
                .map(_ => Some(accountRouting)).fallbackTo(Future.successful(None))
            ))
            alreadyExistingAccountRouting = alreadyExistAccountRoutings.collect {
              case Some(r) => s"bankId: $bankId, scheme: ${r.scheme}, address: ${r.address}"
            }
            _ <- Helper.booleanToFuture(s"$AccountRoutingAlreadyExist (${alreadyExistingAccountRouting.mkString("; ")})", cc = Some(cc)) {
              alreadyExistingAccountRouting.isEmpty
            }
            (bankAccount, _) <- NewStyle.function.createBankAccount(
              bankId, accountId, accountType, accountLabel, currency, initialBalanceAsNumber,
              postedOrLoggedInUser.name,
              createAccountJson.branch_id.getOrElse(""),
              createAccountJson.account_routings.getOrElse(Nil).map(r => AccountRouting(r.scheme, r.address)),
              Some(cc)
            )
            (productAttributes, _) <- NewStyle.function.getProductAttributesByBankAndCode(bankId, ProductCode(accountType), Some(cc))
            (accountAttributes, _) <- NewStyle.function.createAccountAttributes(
              bankId, accountId, ProductCode(accountType), productAttributes, None, Some(cc)
            )
            _ <- BankAccountCreation.setAccountHolderAndRefreshUserAccountAccess(bankId, accountId, postedOrLoggedInUser, Some(cc))
          } yield JSONFactory310.createAccountJSON(userIdAccountOwner, bankAccount, accountAttributes)
        }
    }

    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, "createAccount", "PUT",
      "/banks/BANK_ID/accounts/NEW_ACCOUNT_ID", "Create Account (PUT)",
      """Create Account at bank specified by BANK_ID with Id specified by ACCOUNT_ID.
        |
        |The User can create an Account for themself - or - the User specified in the PUT body.
        |If the PUT body USER_ID is specified, the logged in user must have the Role canCreateAccount.""".stripMargin,
      createAccountRequestJsonV500, createAccountResponseJsonV310,
      List(InvalidJsonFormat, BankNotFound, AuthenticatedUserIsRequired, InvalidUserId,
        InvalidAccountIdFormat, InvalidBankIdFormat, UserNotFoundById, UserHasMissingRoles,
        InvalidAccountBalanceAmount, InvalidAccountInitialBalance, InitialBalanceMustBeZero,
        InvalidAccountBalanceCurrency, AccountIdAlreadyExists, UnknownError),
      List(apiTagAccount, apiTagOnboarding),
      Some(List(canCreateAccount)),
      http4sPartialFunction = Some(createAccount)
    )

    // ─── createUserAuthContext (POST /users/USER_ID/auth-context → 201) ─────

    val createUserAuthContext: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "users" / userId / "auth-context" =>
        EndpointHelpers.executeFutureCreated(req) {
          implicit val cc: code.api.util.CallContext = req.callContext
          val failMsg = s"$InvalidJsonFormat The Json body should be the $PostUserAuthContextJson "
          for {
            postedData <- NewStyle.function.tryons(failMsg, 400, Some(cc)) {
              net.liftweb.json.parse(cc.httpBody.getOrElse("")).extract[PostUserAuthContextJson]
            }
            (user, _) <- NewStyle.function.findByUserId(userId, Some(cc))
            (userAuthContext, _) <- NewStyle.function.createUserAuthContext(
              user, postedData.key.trim, postedData.value.trim, Some(cc))
          } yield JSONFactory500.createUserAuthContextJson(userAuthContext)
        }
    }

    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(createUserAuthContext), "POST",
      "/users/USER_ID/auth-context", "Create User Auth Context",
      s"""Create User Auth Context. These key value pairs will be propagated over connector to adapter.
         |
         |${userAuthenticationMessage(true)}""",
      postUserAuthContextJson, userAuthContextJsonV500,
      List(AuthenticatedUserIsRequired, InvalidJsonFormat, CreateUserAuthContextError, UnknownError),
      List(apiTagUser),
      Some(List(canCreateUserAuthContext)),
      http4sPartialFunction = Some(createUserAuthContext)
    )

    // ─── getUserAuthContexts (GET /users/USER_ID/auth-context → 200) ────────

    val getUserAuthContexts: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "users" / userId / "auth-context" =>
        EndpointHelpers.executeFuture(req) {
          implicit val cc: code.api.util.CallContext = req.callContext
          for {
            (_, _) <- NewStyle.function.findByUserId(userId, Some(cc))
            (userAuthContexts, _) <- NewStyle.function.getUserAuthContexts(userId, Some(cc))
          } yield JSONFactory500.createUserAuthContextsJson(userAuthContexts)
        }
    }

    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(getUserAuthContexts), "GET",
      "/users/USER_ID/auth-context", "Get User Auth Contexts",
      s"""Get User Auth Contexts for a User.
         |
         |${userAuthenticationMessage(true)}""",
      EmptyBody, userAuthContextJsonV500,
      List(AuthenticatedUserIsRequired, UserHasMissingRoles, UnknownError),
      List(apiTagUser),
      Some(canGetUserAuthContext :: Nil),
      http4sPartialFunction = Some(getUserAuthContexts)
    )

    // ─── createUserAuthContextUpdateRequest ──────────────────────────────────
    // POST /banks/BANK_ID/users/current/auth-context-updates/SCA_METHOD → 201
    // SCA_METHOD is a literal in {"SMS", "EMAIL"} per ResourceDocMatcher; the
    // handler also validates inline.

    val createUserAuthContextUpdateRequest: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "banks" / bankIdStr / "users" / "current" / "auth-context-updates" / scaMethod =>
        EndpointHelpers.executeFutureCreated(req) {
          implicit val cc: code.api.util.CallContext = req.callContext
          val bankId = BankId(bankIdStr)
          for {
            user <- Future.successful(cc.user.openOrThrowException(AuthenticatedUserIsRequired))
            _ <- Helper.booleanToFuture(ConsumerHasMissingRoles + CanCreateUserAuthContextUpdate, cc = Some(cc)) {
              checkScope(bankId.value, getConsumerPrimaryKey(Some(cc)), ApiRole.canCreateUserAuthContextUpdate)
            }
            _ <- Helper.booleanToFuture(UserAuthContextUpdateRequestAllowedScaMethods, cc = Some(cc)) {
              List(StrongCustomerAuthentication.SMS.toString(), StrongCustomerAuthentication.EMAIL.toString()).contains(scaMethod)
            }
            failMsg = s"$InvalidJsonFormat The Json body should be the $PostUserAuthContextJson "
            postedData <- NewStyle.function.tryons(failMsg, 400, Some(cc)) {
              net.liftweb.json.parse(cc.httpBody.getOrElse("")).extract[PostUserAuthContextJson]
            }
            (userAuthContextUpdate, _) <- NewStyle.function.validateUserAuthContextUpdateRequest(
              bankId.value, user.userId, postedData.key.trim, postedData.value.trim, scaMethod, Some(cc))
          } yield JSONFactory500.createUserAuthContextUpdateJson(userAuthContextUpdate)
        }
    }

    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(createUserAuthContextUpdateRequest), "POST",
      "/banks/BANK_ID/users/current/auth-context-updates/SCA_METHOD",
      "Create User Auth Context Update Request",
      s"""Create User Auth Context Update Request.
         |${userAuthenticationMessage(true)}
         |
         |A One Time Password (OTP) is sent Out of Band (OOB) to the User via the transport defined in SCA_METHOD.""",
      postUserAuthContextJson, userAuthContextUpdateJsonV500,
      List(AuthenticatedUserIsRequired, $BankNotFound, InvalidJsonFormat, CreateUserAuthContextError, UnknownError),
      List(apiTagUser),
      None,
      http4sPartialFunction = Some(createUserAuthContextUpdateRequest)
    )

    // ─── answerUserAuthContextUpdateChallenge ─────────────────────────────
    // POST /banks/BANK_ID/users/current/auth-context-updates/AUTH_CONTEXT_UPDATE_ID/challenge → 200

    val answerUserAuthContextUpdateChallenge: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "banks" / bankIdStr / "users" / "current" / "auth-context-updates" / authContextUpdateId / "challenge" =>
        EndpointHelpers.executeFuture(req) {
          implicit val cc: code.api.util.CallContext = req.callContext
          val bankId = BankId(bankIdStr)
          val failMsg = s"$InvalidJsonFormat The Json body should be the $PostUserAuthContextUpdateJsonV310 "
          for {
            postUserAuthContextUpdateJson <- NewStyle.function.tryons(failMsg, 400, Some(cc)) {
              net.liftweb.json.parse(cc.httpBody.getOrElse("")).extract[PostUserAuthContextUpdateJsonV310]
            }
            (userAuthContextUpdate, _) <- NewStyle.function.checkAnswer(authContextUpdateId, postUserAuthContextUpdateJson.answer, Some(cc))
            (user, _) <- NewStyle.function.getUserByUserId(userAuthContextUpdate.userId, Some(cc))
            _ <- userAuthContextUpdate.status match {
              case status if status == UserAuthContextUpdateStatus.ACCEPTED.toString =>
                NewStyle.function.createUserAuthContext(
                  user, userAuthContextUpdate.key.trim, userAuthContextUpdate.value.trim, Some(cc))
                  .map(x => (Some(x._1), x._2))
              case _ =>
                Future.successful((None, Some(cc)))
            }
            _ <- userAuthContextUpdate.key match {
              case "CUSTOMER_NUMBER" =>
                NewStyle.function.getOCreateUserCustomerLink(bankId, userAuthContextUpdate.value, user.userId, Some(cc))
              case _ =>
                Future.successful((None, Some(cc)))
            }
          } yield JSONFactory500.createUserAuthContextUpdateJson(userAuthContextUpdate)
        }
    }

    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(answerUserAuthContextUpdateChallenge), "POST",
      "/banks/BANK_ID/users/current/auth-context-updates/AUTH_CONTEXT_UPDATE_ID/challenge",
      "Answer User Auth Context Update Challenge",
      "Answer User Auth Context Update Challenge.",
      postUserAuthContextUpdateJsonV310, userAuthContextUpdateJsonV500,
      List(AuthenticatedUserIsRequired, $BankNotFound, InvalidJsonFormat, InvalidConnectorResponse, UnknownError),
      apiTagUser :: Nil,
      None,
      http4sPartialFunction = Some(answerUserAuthContextUpdateChallenge)
    )

    // ─── createConsentRequest (POST /consumer/consent-requests → 201) ───────
    // Application-access endpoint (no user auth) — the resourceDoc has no
    // AuthenticatedUserIsRequired, so middleware skips auth and we call
    // applicationAccess inline.

    val createConsentRequest: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "consumer" / "consent-requests" =>
        EndpointHelpers.executeFutureCreated(req) {
          implicit val cc: code.api.util.CallContext = req.callContext
          val rawBody = cc.httpBody.getOrElse("")
          for {
            (_, callContextOpt) <- APIUtil.applicationAccess(cc)
            _ <- APIUtil.passesPsd2Aisp(callContextOpt)
            failMsg = s"$InvalidJsonFormat The Json body should be the $PostConsentBodyCommonJson "
            consentJson <- NewStyle.function.tryons(failMsg, 400, callContextOpt) {
              net.liftweb.json.parse(rawBody).extract[PostConsentRequestJsonV500]
            }
            maxTimeToLive = APIUtil.getPropsAsIntValue(nameOfProperty = "consents.max_time_to_live", defaultValue = 3600)
            _ <- Helper.booleanToFuture(s"$ConsentMaxTTL ($maxTimeToLive)", cc = callContextOpt) {
              consentJson.time_to_live match {
                case Some(ttl) => ttl <= maxTimeToLive
                case _         => true
              }
            }
            createdConsentRequest <- Future(ConsentRequests.consentRequestProvider.vend.createConsentRequest(
              callContextOpt.flatMap(_.consumer),
              Some(compactRender(net.liftweb.json.parse(rawBody)))
            )).map(i => connectorEmptyResponse(i, callContextOpt))
          } yield JSONFactory500.createConsentRequestResponseJson(createdConsentRequest)
        }
    }

    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(createConsentRequest), "POST",
      "/consumer/consent-requests", "Create Consent Request",
      s"""Create a Consent Request — the first step of the OBP Consent flow.
         |
         |The calling application (TPP) authenticates with Client Credentials and posts the consent details.
         |
         |${applicationAccessMessage(true)}
         |
         |${userAuthenticationMessage(false)}""".stripMargin,
      postConsentRequestJsonV500, consentRequestResponseJson,
      List(InvalidJsonFormat, ConsentMaxTTL, X509CannotGetCertificate, X509GeneralError, InvalidConnectorResponse, UnknownError),
      apiTagConsent :: apiTagPSD2AIS :: apiTagPsd2 :: Nil,
      None,
      http4sPartialFunction = Some(createConsentRequest)
    )

    // ─── getConsentRequest (GET /consumer/consent-requests/CONSENT_REQUEST_ID → 200) ───

    val getConsentRequest: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "consumer" / "consent-requests" / consentRequestId =>
        EndpointHelpers.executeFuture(req) {
          implicit val cc: code.api.util.CallContext = req.callContext
          for {
            (_, callContextOpt) <- APIUtil.applicationAccess(cc)
            _ <- APIUtil.passesPsd2Aisp(callContextOpt)
            consentRequest <- Future(ConsentRequests.consentRequestProvider.vend.getConsentRequestById(consentRequestId))
              .map(i => unboxFullOrFail(i, callContextOpt, ConsentRequestNotFound))
          } yield JSONFactory500.createConsentRequestResponseJson(consentRequest)
        }
    }

    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(getConsentRequest), "GET",
      "/consumer/consent-requests/CONSENT_REQUEST_ID", "Get Consent Request",
      "Return the full payload of a previously-created Consent Request.",
      EmptyBody, consentRequestResponseJson,
      List(InvalidJsonFormat, ConsentMaxTTL, X509CannotGetCertificate, X509GeneralError, InvalidConnectorResponse, UnknownError),
      apiTagConsent :: apiTagPSD2AIS :: apiTagPsd2 :: Nil,
      None,
      http4sPartialFunction = Some(getConsentRequest)
    )

    // ─── getConsentByConsentRequestId ────────────────────────────────────────
    // GET /consumer/consent-requests/CONSENT_REQUEST_ID/consents → 200

    val getConsentByConsentRequestId: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "consumer" / "consent-requests" / consentRequestId / "consents" =>
        EndpointHelpers.executeFuture(req) {
          implicit val cc: code.api.util.CallContext = req.callContext
          for {
            (_, callContextOpt) <- APIUtil.applicationAccess(cc)
            consent <- Future { Consents.consentProvider.vend.getConsentByConsentRequestId(consentRequestId) }
              .map(unboxFullOrFail(_, callContextOpt, ConsentRequestNotFound))
            _ <- Helper.booleanToFuture(failMsg = ConsentNotFound, failCode = 404, cc = Some(cc)) {
              consent.mConsumerId.get == cc.consumer.map(_.consumerId.get).getOrElse("None")
            }
            tuple <- NewStyle.function.tryons(
              failMsg = Oauth2BadJWTException, 400, callContextOpt) {
              val jsonWebTokenAsJValue = JwtUtil.getSignedPayloadAsJson(consent.jsonWebToken)
                .map(json.parse(_).extract[ConsentJWT])
              val viewsFromJwtToken = jsonWebTokenAsJValue.head.views
              val isVrpConsent = (viewsFromJwtToken.length == 1) &&
                viewsFromJwtToken.head.bank_id.nonEmpty &&
                viewsFromJwtToken.head.account_id.nonEmpty &&
                viewsFromJwtToken.head.view_id.startsWith("_vrp-")
              if (isVrpConsent) {
                val bId = BankId(viewsFromJwtToken.head.bank_id)
                val aId = AccountId(viewsFromJwtToken.head.account_id)
                val vId = ViewId(viewsFromJwtToken.head.view_id)
                val helperInfoFromJwtToken = viewsFromJwtToken.head.helper_info
                val viewCanGetCounterparty = Views.views.vend
                  .customView(vId, BankIdAccountId(bId, aId))
                  .map(_.allowed_actions.exists(_ == CAN_GET_COUNTERPARTY))
                val helperInfo = if (viewCanGetCounterparty == Full(true)) helperInfoFromJwtToken else None
                (Option(bId), Option(aId), Option(vId), helperInfo): (Option[BankId], Option[AccountId], Option[ViewId], Option[HelperInfoJson])
              } else {
                (Option.empty[BankId], Option.empty[AccountId], Option.empty[ViewId], Option.empty[HelperInfoJson])
              }
            }
            (bankIdOpt, accountIdOpt, viewIdOpt, helperInfo) = tuple
          } yield ConsentJsonV500(
            consent.consentId,
            consent.jsonWebToken,
            consent.status,
            Some(consent.consentRequestId),
            if (bankIdOpt.isDefined && accountIdOpt.isDefined && viewIdOpt.isDefined)
              Some(ConsentAccountAccessJson(
                bank_id = bankIdOpt.get.value,
                account_id = accountIdOpt.get.value,
                view_id = viewIdOpt.get.value,
                helper_info = helperInfo))
            else None
          )
        }
    }

    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(getConsentByConsentRequestId), "GET",
      "/consumer/consent-requests/CONSENT_REQUEST_ID/consents",
      "Get Consent By Consent Request Id via Consumer",
      s"""This endpoint gets the Consent By consent request id.
         |
         |${userAuthenticationMessage(true)}""",
      EmptyBody, consentJsonV500,
      List($AuthenticatedUserIsRequired, UnknownError),
      List(apiTagConsent, apiTagPSD2AIS, apiTagPsd2),
      None,
      http4sPartialFunction = Some(getConsentByConsentRequestId)
    )

    // ─── createConsentByConsentRequestId ─────────────────────────────────────
    // POST /consumer/consent-requests/CONSENT_REQUEST_ID/{EMAIL|SMS|IMPLICIT}/consents → 201
    // Three ResourceDoc registrations (one per SCA literal) but one HttpRoutes pattern.

    private def sendEmailConsentNotification(
      callContextOpt: Option[code.api.util.CallContext],
      consentRequestJson: PostConsentRequestJsonV500,
      challengeText: String
    ): Future[String] =
      for {
        consentScaEmail <- NewStyle.function.tryons(
          s"$InvalidJsonFormat The Json body must contain the field email", 400, callContextOpt) {
          consentRequestJson.email.head
        }
        (status, _) <- NewStyle.function.sendCustomerNotification(
          StrongCustomerAuthentication.EMAIL, consentScaEmail,
          Some("OBP Consent Challenge"), challengeText, callContextOpt)
      } yield status

    private def sendSmsConsentNotification(
      callContextOpt: Option[code.api.util.CallContext],
      consentRequestJson: PostConsentRequestJsonV500,
      challengeText: String
    ): Future[String] =
      for {
        consentScaPhoneNumber <- NewStyle.function.tryons(
          s"$InvalidJsonFormat The Json body must contain the field phone_number", 400, callContextOpt) {
          consentRequestJson.phone_number.head
        }
        (status, _) <- NewStyle.function.sendCustomerNotification(
          StrongCustomerAuthentication.SMS, consentScaPhoneNumber, None, challengeText, callContextOpt)
      } yield status

    val createConsentByConsentRequestId: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "consumer" / "consent-requests" / consentRequestId / scaMethod / "consents"
        if scaMethod == "EMAIL" || scaMethod == "SMS" || scaMethod == "IMPLICIT" =>
        EndpointHelpers.executeFutureCreated(req) {
          implicit val cc: code.api.util.CallContext = req.callContext
          val callContextOpt = Some(cc)
          for {
            user <- Future.successful(cc.user.openOrThrowException(AuthenticatedUserIsRequired))
            createdConsentRequest <- Future(ConsentRequests.consentRequestProvider.vend.getConsentRequestById(consentRequestId))
              .map(i => unboxFullOrFail(i, callContextOpt, ConsentRequestNotFound))
            _ <- Helper.booleanToFuture(
              s"$ConsentRequestIsInvalid, the current CONSENT_REQUEST_ID($consentRequestId) is already used to create a consent, please provide another one!",
              cc = callContextOpt) {
              Consents.consentProvider.vend.getConsentByConsentRequestId(consentRequestId).isEmpty
            }
            _ <- Helper.booleanToFuture(ConsentAllowedScaMethods, cc = callContextOpt) {
              List(StrongCustomerAuthentication.SMS.toString(),
                   StrongCustomerAuthentication.EMAIL.toString(),
                   StrongCustomerAuthentication.IMPLICIT.toString()).contains(scaMethod)
            }
            isVrpConsent = createdConsentRequest.payload.contains("to_account")
            (consentRequestJson, isVRPConsentRequest) <-
              if (isVrpConsent) {
                val failMsg = s"$InvalidJsonFormat The vrp consent request json body should be the $PostVRPConsentRequestJsonV510 "
                NewStyle.function.tryons(failMsg, 400, callContextOpt) {
                  json.parse(createdConsentRequest.payload).extract[code.api.v5_1_0.PostVRPConsentRequestJsonInternalV510]
                }.map(p => (p.toPostConsentRequestJsonV500, true))
              } else {
                val failMsg = s"$InvalidJsonFormat The consent request Json body should be the $PostConsentRequestJsonV500 "
                NewStyle.function.tryons(failMsg, 400, callContextOpt) {
                  json.parse(createdConsentRequest.payload).extract[PostConsentRequestJsonV500]
                }.map(p => (p, false))
              }
            (bankId, accountId, viewId, counterpartyId) <- if (isVRPConsentRequest) {
              val postConsentRequestJsonV510 = json.parse(createdConsentRequest.payload).extract[code.api.v5_1_0.PostVRPConsentRequestJsonV510]
              val vrpViewId = s"_vrp-${UUID.randomUUID.toString}".dropRight(5)
              val targetPermissions = List(
                CAN_ADD_TRANSACTION_REQUEST_TO_BENEFICIARY,
                CAN_GET_COUNTERPARTY,
                CAN_SEE_TRANSACTION_REQUESTS
              )
              val targetCreateCustomViewJson = CreateCustomViewJson(
                name = vrpViewId, description = vrpViewId, metadata_view = vrpViewId,
                is_public = false, which_alias_to_use = vrpViewId,
                hide_metadata_if_alias_used = true, allowed_permissions = targetPermissions
              )
              val fromBankAccountRoutings = BankAccountRoutings(
                bank = BankRoutingJson(postConsentRequestJsonV510.from_account.bank_routing.scheme, postConsentRequestJsonV510.from_account.bank_routing.address),
                account = BranchRoutingJsonV141(postConsentRequestJsonV510.from_account.account_routing.scheme, postConsentRequestJsonV510.from_account.account_routing.address),
                branch = AccountRoutingJsonV121(postConsentRequestJsonV510.from_account.branch_routing.scheme, postConsentRequestJsonV510.from_account.branch_routing.address)
              )
              val postJson: PostCounterpartyJson400 = PostCounterpartyJson400(
                name = postConsentRequestJsonV510.to_account.counterparty_name,
                description = postConsentRequestJsonV510.to_account.counterparty_name,
                currency = postConsentRequestJsonV510.to_account.limit.currency,
                other_account_routing_scheme = StringHelpers.snakify(postConsentRequestJsonV510.to_account.account_routing.scheme).toUpperCase,
                other_account_routing_address = postConsentRequestJsonV510.to_account.account_routing.address,
                other_account_secondary_routing_scheme = "",
                other_account_secondary_routing_address = "",
                other_bank_routing_scheme = StringHelpers.snakify(postConsentRequestJsonV510.to_account.bank_routing.scheme).toUpperCase,
                other_bank_routing_address = postConsentRequestJsonV510.to_account.bank_routing.address,
                other_branch_routing_scheme = StringHelpers.snakify(postConsentRequestJsonV510.to_account.branch_routing.scheme).toUpperCase,
                other_branch_routing_address = postConsentRequestJsonV510.to_account.branch_routing.address,
                is_beneficiary = true, bespoke = Nil
              )
              val postCounterpartyLimitV510: PostCounterpartyLimitV510 = PostCounterpartyLimitV510(
                currency = postConsentRequestJsonV510.to_account.limit.currency,
                max_single_amount = postConsentRequestJsonV510.to_account.limit.max_single_amount,
                max_monthly_amount = postConsentRequestJsonV510.to_account.limit.max_monthly_amount,
                max_number_of_monthly_transactions = postConsentRequestJsonV510.to_account.limit.max_number_of_monthly_transactions,
                max_yearly_amount = postConsentRequestJsonV510.to_account.limit.max_yearly_amount,
                max_number_of_yearly_transactions = postConsentRequestJsonV510.to_account.limit.max_number_of_yearly_transactions,
                max_total_amount = postConsentRequestJsonV510.to_account.limit.max_total_amount,
                max_number_of_transactions = postConsentRequestJsonV510.to_account.limit.max_number_of_transactions
              )
              val vrpFlow: Future[(BankId, AccountId, ViewId, CounterpartyId)] = for {
                (fromAccount, _) <- NewStyle.function.getBankAccountByRoutings(fromBankAccountRoutings, callContextOpt)
                fromBankIdAccountId: BankIdAccountId = BankIdAccountId(fromAccount.bankId, fromAccount.accountId)
                permission <- NewStyle.function.permission(fromAccount.bankId, fromAccount.accountId, user, callContextOpt)
                permissionsFromSource: Set[String] = permission.views.flatMap(_.allowed_actions).toSet
                userMissingPermissions: Set[String] = targetCreateCustomViewJson.allowed_permissions.toSet diff permissionsFromSource
                _ <- Helper.booleanToFuture(s"${ErrorMessages.UserDoesNotHavePermission} ${userMissingPermissions.toString}", cc = callContextOpt) {
                  userMissingPermissions.isEmpty
                }
                (vrpView, _) <- ViewNewStyle.createCustomView(fromBankIdAccountId, targetCreateCustomViewJson.toCreateViewJson, callContextOpt)
                _ <- ViewNewStyle.grantAccessToCustomView(vrpView, user, callContextOpt)
                _ <- Helper.booleanToFuture(s"$InvalidValueLength. The maximum length of `description` field is ${MappedCounterparty.mDescription.maxLen}", cc = callContextOpt) {
                  postJson.description.length <= 36
                }
                (existingCounterparty, _) <- Connector.connector.vend.checkCounterpartyExists(
                  postJson.name, fromBankIdAccountId.bankId.value, fromBankIdAccountId.accountId.value, vrpView.viewId.value, callContextOpt)
                _ <- Helper.booleanToFuture(
                  CounterpartyAlreadyExists.replace(
                    "value for BANK_ID or ACCOUNT_ID or VIEW_ID or NAME.",
                    s"COUNTERPARTY_NAME(${postJson.name}) for the BANK_ID(${fromBankIdAccountId.bankId.value}) and ACCOUNT_ID(${fromBankIdAccountId.accountId.value}) and VIEW_ID($vrpViewId)"),
                  cc = callContextOpt) {
                  existingCounterparty.isEmpty
                }
                _ <- Helper.booleanToFuture(s"$InvalidISOCurrencyCode Current input is: '${postJson.currency}'", cc = callContextOpt) {
                  isValidCurrencyISOCode(postJson.currency)
                }
                (counterparty, _) <- NewStyle.function.createCounterparty(
                  name = postJson.name, description = postJson.description, currency = postJson.currency,
                  createdByUserId = user.userId,
                  thisBankId = fromBankIdAccountId.bankId.value,
                  thisAccountId = fromBankIdAccountId.accountId.value,
                  thisViewId = vrpViewId,
                  otherAccountRoutingScheme = postJson.other_account_routing_scheme,
                  otherAccountRoutingAddress = postJson.other_account_routing_address,
                  otherAccountSecondaryRoutingScheme = postJson.other_account_secondary_routing_scheme,
                  otherAccountSecondaryRoutingAddress = postJson.other_account_secondary_routing_address,
                  otherBankRoutingScheme = postJson.other_bank_routing_scheme,
                  otherBankRoutingAddress = postJson.other_bank_routing_address,
                  otherBranchRoutingScheme = postJson.other_branch_routing_scheme,
                  otherBranchRoutingAddress = postJson.other_branch_routing_address,
                  isBeneficiary = postJson.is_beneficiary,
                  bespoke = postJson.bespoke.map(b => CounterpartyBespoke(b.key, b.value)),
                  callContextOpt
                )
                (counterpartyLimitBox, _) <- Connector.connector.vend.getCounterpartyLimit(
                  fromBankIdAccountId.bankId.value, fromBankIdAccountId.accountId.value,
                  vrpViewId, counterparty.counterpartyId, callContextOpt)
                _ <- Helper.booleanToFuture(
                  s"$CounterpartyLimitAlreadyExists Current BANK_ID(${fromBankIdAccountId.bankId.value}), " +
                    s"ACCOUNT_ID(${fromBankIdAccountId.accountId.value}), VIEW_ID($vrpViewId),COUNTERPARTY_ID(${counterparty.counterpartyId})",
                  cc = callContextOpt) {
                  counterpartyLimitBox.isEmpty
                }
                _ <- NewStyle.function.createOrUpdateCounterpartyLimit(
                  bankId = counterparty.thisBankId, accountId = counterparty.thisAccountId,
                  viewId = counterparty.thisViewId, counterpartyId = counterparty.counterpartyId,
                  postCounterpartyLimitV510.currency,
                  BigDecimal(postCounterpartyLimitV510.max_single_amount),
                  BigDecimal(postCounterpartyLimitV510.max_monthly_amount),
                  postCounterpartyLimitV510.max_number_of_monthly_transactions,
                  BigDecimal(postCounterpartyLimitV510.max_yearly_amount),
                  postCounterpartyLimitV510.max_number_of_yearly_transactions,
                  BigDecimal(postCounterpartyLimitV510.max_total_amount),
                  postCounterpartyLimitV510.max_number_of_transactions,
                  callContextOpt
                )
              } yield (fromAccount.bankId, fromAccount.accountId, vrpView.viewId, CounterpartyId(counterparty.counterpartyId))
              vrpFlow
            } else {
              Future.successful((BankId(""), AccountId(""), ViewId(""), CounterpartyId(""))): Future[(BankId, AccountId, ViewId, CounterpartyId)]
            }
            maxTimeToLive = APIUtil.getPropsAsIntValue(nameOfProperty = "consents.max_time_to_live", defaultValue = 3600)
            _ <- Helper.booleanToFuture(s"$ConsentMaxTTL ($maxTimeToLive)", cc = callContextOpt) {
              consentRequestJson.time_to_live match {
                case Some(ttl) => ttl <= maxTimeToLive
                case _         => true
              }
            }
            requestedEntitlements = consentRequestJson.entitlements.getOrElse(Nil)
            myEntitlements <- Entitlement.entitlement.vend.getEntitlementsByUserIdFuture(user.userId)
            _ <- Helper.booleanToFuture(RolesForbiddenInConsent, cc = callContextOpt) {
              requestedEntitlements.map(_.role_name).intersect(
                List(canCreateEntitlementAtOneBank.toString(), canCreateEntitlementAtAnyBank.toString())
              ).isEmpty
            }
            _ <- Helper.booleanToFuture(RolesAllowedInConsent, cc = callContextOpt) {
              requestedEntitlements.forall(re =>
                myEntitlements.getOrElse(Nil).exists(e => e.roleName == re.role_name && e.bankId == re.bank_id))
            }
            postConsentViewJsons <- if (isVrpConsent) {
              Future.successful(List(PostConsentViewJsonV310(bankId.value, accountId.value, viewId.value)))
            } else {
              Future.sequence(consentRequestJson.account_access.map(access =>
                NewStyle.function.getBankAccountByRouting(
                  consentRequestJson.bank_id.map(BankId(_)),
                  access.account_routing.scheme, access.account_routing.address, callContextOpt)
                  .map(r => PostConsentViewJsonV310(r._1.bankId.value, r._1.accountId.value, access.view_id))))
            }
            (_, assignedViews) <- Future(Views.views.vend.privateViewsUserCanAccess(user))
            _ <- Helper.booleanToFuture(ViewsAllowedInConsent, cc = callContextOpt) {
              postConsentViewJsons.forall(rv =>
                assignedViews.exists(e =>
                  e.view_id == rv.view_id && e.bank_id == rv.bank_id && e.account_id == rv.account_id))
            }
            calculatedConsumerId = consentRequestJson.consumer_id.orElse(Some(createdConsentRequest.consumerId))
            (consumerIdOpt, applicationText) <- calculatedConsumerId match {
              case Some(id) =>
                NewStyle.function.checkConsumerByConsumerId(id, callContextOpt).map { c =>
                  (Some(c.consumerId.get), c.description)
                }
              case None => Future.successful((None, "Any application"))
            }
            challengeAnswer = Props.mode match {
              case Props.RunModes.Test => Consent.challengeAnswerAtTestEnvironment
              case _                   => SecureRandomUtil.numeric()
            }
            consumer = Consumers.consumers.vend.getConsumerByConsumerId(calculatedConsumerId.getOrElse("None"))
            createdConsent <- Future(Consents.consentProvider.vend.createObpConsent(
              user, challengeAnswer, Some(consentRequestId), consumer))
              .map(i => connectorEmptyResponse(i, callContextOpt))
            postConsentBodyCommonJson = PostConsentBodyCommonJson(
              everything = consentRequestJson.everything,
              bank_id = consentRequestJson.bank_id,
              views = postConsentViewJsons,
              entitlements = consentRequestJson.entitlements.getOrElse(Nil),
              consumer_id = consentRequestJson.consumer_id,
              consent_request_id = Some(consentRequestId),
              valid_from = consentRequestJson.valid_from,
              time_to_live = consentRequestJson.time_to_live
            )
            consentJWT = Consent.createConsentJWT(
              user, postConsentBodyCommonJson, createdConsent.secret, createdConsent.consentId,
              consumerIdOpt, postConsentBodyCommonJson.valid_from,
              postConsentBodyCommonJson.time_to_live.getOrElse(3600),
              Some(HelperInfoJson(List(counterpartyId.value)))
            )
            _ <- Future(Consents.consentProvider.vend.setJsonWebToken(createdConsent.consentId, consentJWT))
              .map(i => connectorEmptyResponse(i, callContextOpt))
            validUntil = Helper.calculateValidTo(postConsentBodyCommonJson.valid_from, postConsentBodyCommonJson.time_to_live.getOrElse(3600))
            _ <- Future(Consents.consentProvider.vend.setValidUntil(createdConsent.consentId, validUntil))
              .map(i => connectorEmptyResponse(i, callContextOpt))
            grantorConsumerId = callContextOpt.flatMap(_.consumer.toOption.map(_.consumerId.get)).getOrElse("Unknown")
            granteeConsumerId = postConsentBodyCommonJson.consumer_id.getOrElse("Unknown")
            shouldSkipConsentScaForConsumerIdPair = APIUtil.skipConsentScaForConsumerIdPairs.contains(
              APIUtil.ConsumerIdPair(grantorConsumerId, granteeConsumerId))
            mappedConsent <- if (shouldSkipConsentScaForConsumerIdPair) {
              Future {
                MappedConsent.find(By(MappedConsent.mConsentId, createdConsent.consentId))
                  .map(_.mStatus(ConsentStatus.ACCEPTED.toString).saveMe()).head
              }
            } else {
              val challengeText = s"Your consent challenge : ${challengeAnswer}, Application: $applicationText"
              scaMethod match {
                case v if v == StrongCustomerAuthentication.EMAIL.toString =>
                  sendEmailConsentNotification(callContextOpt, consentRequestJson, challengeText)
                case v if v == StrongCustomerAuthentication.SMS.toString =>
                  sendSmsConsentNotification(callContextOpt, consentRequestJson, challengeText)
                case v if v == StrongCustomerAuthentication.IMPLICIT.toString =>
                  for {
                    (consentImplicitSCA, _) <- NewStyle.function.getConsentImplicitSCA(user, callContextOpt)
                    _ <- consentImplicitSCA.scaMethod match {
                      case v if v == StrongCustomerAuthentication.EMAIL =>
                        sendEmailConsentNotification(callContextOpt, consentRequestJson.copy(email = Some(consentImplicitSCA.recipient)), challengeText)
                      case v if v == StrongCustomerAuthentication.SMS =>
                        sendSmsConsentNotification(callContextOpt, consentRequestJson.copy(phone_number = Some(consentImplicitSCA.recipient)), challengeText)
                      case _ => Future.successful("Success")
                    }
                  } yield "Success"
                case _ => Future.successful("Success")
              }
              Future(createdConsent)
            }
          } yield ConsentJsonV500(
            mappedConsent.consentId,
            consentJWT,
            mappedConsent.status,
            Some(mappedConsent.consentRequestId),
            if (isVRPConsentRequest)
              Some(ConsentAccountAccessJson(bankId.value, accountId.value, viewId.value, Some(HelperInfoJson(List(counterpartyId.value)))))
            else None
          )
        }
    }

    // Three resourceDoc registrations — one per SCA literal — sharing the same handler.
    private val createConsentByConsentRequestIdCommonErrors = List(
      AuthenticatedUserIsRequired, BankNotFound, InvalidJsonFormat,
      ConsentAllowedScaMethods, RolesAllowedInConsent, ViewsAllowedInConsent,
      ConsumerNotFoundByConsumerId, ConsumerIsDisabled,
      InvalidConnectorResponse, UnknownError
    )

    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(createConsentByConsentRequestId).replace("Id", "IdEmail"), "POST",
      "/consumer/consent-requests/CONSENT_REQUEST_ID/EMAIL/consents",
      "Create Consent By CONSENT_REQUEST_ID (EMAIL)",
      "Answer a Consent Request and create the resulting Consent, with an EMAIL Strong Customer Authentication challenge.",
      EmptyBody, consentJsonV500,
      createConsentByConsentRequestIdCommonErrors,
      apiTagConsent :: apiTagPSD2AIS :: apiTagPsd2 :: apiTagVrp :: Nil,
      None,
      http4sPartialFunction = Some(createConsentByConsentRequestId)
    )

    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(createConsentByConsentRequestId).replace("Id", "IdSms"), "POST",
      "/consumer/consent-requests/CONSENT_REQUEST_ID/SMS/consents",
      "Create Consent By CONSENT_REQUEST_ID (SMS)",
      "Answer a Consent Request and create the resulting Consent, with an SMS Strong Customer Authentication challenge.",
      EmptyBody, consentJsonV500,
      ConsentRequestIsInvalid :: MissingPropsValueAtThisInstance :: SmsServerNotResponding :: createConsentByConsentRequestIdCommonErrors,
      apiTagConsent :: apiTagPSD2AIS :: apiTagPsd2 :: Nil,
      None,
      http4sPartialFunction = Some(createConsentByConsentRequestId)
    )

    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(createConsentByConsentRequestId).replace("Id", "IdImplicit"), "POST",
      "/consumer/consent-requests/CONSENT_REQUEST_ID/IMPLICIT/consents",
      "Create Consent By CONSENT_REQUEST_ID (IMPLICIT)",
      "Answer a Consent Request and create the resulting Consent without an SCA challenge.",
      EmptyBody, consentJsonV500,
      ConsentRequestIsInvalid :: MissingPropsValueAtThisInstance :: SmsServerNotResponding :: createConsentByConsentRequestIdCommonErrors,
      apiTagConsent :: apiTagPSD2AIS :: apiTagPsd2 :: Nil,
      None,
      http4sPartialFunction = Some(createConsentByConsentRequestId)
    )

    // ─── headAtms (HEAD /banks/BANK_ID/atms → 200) ──────────────────────────

    val headAtms: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ Method.HEAD -> `prefixPath` / "banks" / _ / "atms" =>
        EndpointHelpers.executeFuture(req) {
          implicit val cc: code.api.util.CallContext = req.callContext
          for {
            (_, _) <- if (getAtmsIsPublic) APIUtil.anonymousAccess(cc) else APIUtil.applicationAccess(cc)
          } yield ""
        }
    }

    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(headAtms), "HEAD",
      "/banks/BANK_ID/atms", "Head Bank ATMS",
      "Head Bank ATMS.",
      EmptyBody, atmsJsonV400,
      List($BankNotFound, UnknownError),
      List(apiTagATM),
      None,
      http4sPartialFunction = Some(headAtms)
    )

    // ─── createCustomer (POST /banks/BANK_ID/customers → 201) — v5 override ──
    // v5 uses PostCustomerJsonV500 with extra fields (kyc_status default, etc.)

    val createCustomer: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "banks" / bankIdStr / "customers" =>
        EndpointHelpers.executeFutureCreated(req) {
          implicit val cc: code.api.util.CallContext = req.callContext
          val bankId = BankId(bankIdStr)
          for {
            postedData <- NewStyle.function.tryons(
              s"$InvalidJsonFormat The Json body should be the $PostCustomerJsonV500 ", 400, Some(cc)) {
              net.liftweb.json.parse(cc.httpBody.getOrElse("")).extract[PostCustomerJsonV500]
            }
            _ <- Helper.booleanToFuture(
              InvalidJsonContent + s" The field dependants(${postedData.dependants.getOrElse(0)}) not equal the length(${postedData.dob_of_dependants.getOrElse(Nil).length}) of dob_of_dependants array",
              400, Some(cc)) {
              postedData.dependants.getOrElse(0) == postedData.dob_of_dependants.getOrElse(Nil).length
            }
            customerNumber = postedData.customer_number.getOrElse(Random.nextInt(Integer.MAX_VALUE).toString)
            _ <- Helper.booleanToFuture(s"$InvalidJsonFormat customer_number can not contain `::::` characters", cc = Some(cc)) {
              !`checkIfContains::::`(customerNumber)
            }
            (_, _) <- NewStyle.function.checkCustomerNumberAvailable(bankId, customerNumber, Some(cc))
            (customer, _) <- NewStyle.function.createCustomerC2(
              bankId,
              postedData.legal_name, customerNumber, postedData.mobile_phone_number,
              postedData.email.getOrElse(""),
              CustomerFaceImage(
                postedData.face_image.map(_.date).orNull,
                postedData.face_image.map(_.url).getOrElse("")),
              postedData.date_of_birth.orNull,
              postedData.relationship_status.getOrElse(""),
              postedData.dependants.getOrElse(0),
              postedData.dob_of_dependants.getOrElse(Nil),
              postedData.highest_education_attained.getOrElse(""),
              postedData.employment_status.getOrElse(""),
              postedData.kyc_status.getOrElse(false),
              postedData.last_ok_date.orNull,
              postedData.credit_rating.map(i => CreditRating(i.rating, i.source)),
              postedData.credit_limit.map(i => CreditLimit(i.currency, i.amount)),
              postedData.title.getOrElse(""),
              postedData.branch_id.getOrElse(""),
              postedData.name_suffix.getOrElse(""),
              "", "",
              Some(cc)
            )
          } yield JSONFactory310.createCustomerJson(customer)
        }
    }

    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(createCustomer), "POST",
      "/banks/BANK_ID/customers", "Create Customer",
      s"""The Customer resource stores the customer number, legal name, email, phone number, date of birth, etc.
         |
         |If kyc_status is not provided, it defaults to false.
         |
         |${userAuthenticationMessage(true)}""",
      postCustomerJsonV500, customerJsonV310,
      List($AuthenticatedUserIsRequired, $BankNotFound, InvalidJsonFormat,
        CustomerNumberAlreadyExists, UserNotFoundById, CustomerAlreadyExistsForUser,
        CreateConsumerError, UnknownError),
      List(apiTagCustomer, apiTagPerson),
      Some(List(canCreateCustomer, canCreateCustomerAtAnyBank)),
      http4sPartialFunction = Some(createCustomer)
    )

    // ─── getCustomerOverview (POST /banks/.../customers/customer-number-query/overview) ─

    val getCustomerOverview: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "banks" / bankIdStr / "customers" / "customer-number-query" / "overview" =>
        EndpointHelpers.executeFuture(req) {
          implicit val cc: code.api.util.CallContext = req.callContext
          val bankId = BankId(bankIdStr)
          for {
            postedData <- NewStyle.function.tryons(
              s"$InvalidJsonFormat The Json body should be the $PostCustomerOverviewJsonV500 ", 400, Some(cc)) {
              net.liftweb.json.parse(cc.httpBody.getOrElse("")).extract[PostCustomerOverviewJsonV500]
            }
            (customer, _) <- NewStyle.function.getCustomerByCustomerNumber(postedData.customer_number, bankId, Some(cc))
            (customerAttributes, _) <- NewStyle.function.getCustomerAttributes(bankId, CustomerId(customer.customerId), Some(cc))
            accountIds <- AccountAttributeX.accountAttributeProvider.vend
              .getAccountIdsByParams(bankId, List("customer_number" -> List(postedData.customer_number)).toMap)
            (accounts: List[BankAccount], _) <- NewStyle.function.getBankAccounts(
              accountIds.toList.flatten.map(i => BankIdAccountId(bankId, AccountId(i))), Some(cc))
            (accountAttributes, _) <- NewStyle.function.getAccountAttributesForAccounts(bankId, accounts, Some(cc))
          } yield JSONFactory500.createCustomerWithAttributesJson(customer, customerAttributes, accountAttributes)
        }
    }

    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(getCustomerOverview), "POST",
      "/banks/BANK_ID/customers/customer-number-query/overview", "Get Customer Overview",
      s"""Gets the Customer Overview specified by customer_number and bank_code.
         |
         |${userAuthenticationMessage(true)}""",
      postCustomerOverviewJsonV500, customerOverviewJsonV500,
      List(AuthenticatedUserIsRequired, UserCustomerLinksNotFoundForUser, UnknownError),
      List(apiTagCustomer, apiTagKyc),
      Some(List(canGetCustomerOverview)),
      http4sPartialFunction = Some(getCustomerOverview)
    )

    // ─── getCustomerOverviewFlat ────────────────────────────────────────────

    val getCustomerOverviewFlat: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "banks" / bankIdStr / "customers" / "customer-number-query" / "overview-flat" =>
        EndpointHelpers.executeFuture(req) {
          implicit val cc: code.api.util.CallContext = req.callContext
          val bankId = BankId(bankIdStr)
          for {
            postedData <- NewStyle.function.tryons(
              s"$InvalidJsonFormat The Json body should be the $PostCustomerOverviewJsonV500 ", 400, Some(cc)) {
              net.liftweb.json.parse(cc.httpBody.getOrElse("")).extract[PostCustomerOverviewJsonV500]
            }
            (customer, _) <- NewStyle.function.getCustomerByCustomerNumber(postedData.customer_number, bankId, Some(cc))
            (customerAttributes, _) <- NewStyle.function.getCustomerAttributes(bankId, CustomerId(customer.customerId), Some(cc))
            accountIds <- AccountAttributeX.accountAttributeProvider.vend
              .getAccountIdsByParams(bankId, List("customer_number" -> List(postedData.customer_number)).toMap)
            (accounts: List[BankAccount], _) <- NewStyle.function.getBankAccounts(
              accountIds.toList.flatten.map(i => BankIdAccountId(bankId, AccountId(i))), Some(cc))
            (accountAttributes, _) <- NewStyle.function.getAccountAttributesForAccounts(bankId, accounts, Some(cc))
          } yield JSONFactory500.createCustomerOverviewFlatJson(customer, customerAttributes, accountAttributes)
        }
    }

    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(getCustomerOverviewFlat), "POST",
      "/banks/BANK_ID/customers/customer-number-query/overview-flat", "Get Customer Overview Flat",
      s"""Gets the Customer Overview Flat specified by customer_number and bank_code.
         |
         |${userAuthenticationMessage(true)}""",
      postCustomerOverviewJsonV500, customerOverviewFlatJsonV500,
      List(AuthenticatedUserIsRequired, UserCustomerLinksNotFoundForUser, UnknownError),
      List(apiTagCustomer, apiTagKyc),
      Some(List(canGetCustomerOverviewFlat)),
      http4sPartialFunction = Some(getCustomerOverviewFlat)
    )

    // ─── getMyCustomersAtAnyBank (GET /my/customers) ────────────────────────

    val getMyCustomersAtAnyBank: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "my" / "customers" =>
        EndpointHelpers.withUser(req) { (user, cc) =>
          for {
            (customers, _) <- Connector.connector.vend.getCustomersByUserId(user.userId, Some(cc))
              .map(connectorEmptyResponse(_, Some(cc)))
          } yield JSONFactory210.createCustomersJson(customers)
        }
    }

    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(getMyCustomersAtAnyBank), "GET",
      "/my/customers", "Get My Customers",
      "Gets all Customers that are linked to me.\n\nAuthentication via OAuth is required.",
      EmptyBody, customerJsonV210,
      List($AuthenticatedUserIsRequired, UserCustomerLinksNotFoundForUser, UnknownError),
      List(apiTagCustomer, apiTagUser),
      None,
      http4sPartialFunction = Some(getMyCustomersAtAnyBank)
    )

    // ─── getMyCustomersAtBank (GET /banks/BANK_ID/my/customers) ─────────────

    val getMyCustomersAtBank: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / bankIdStr / "my" / "customers" =>
        EndpointHelpers.executeFuture(req) {
          implicit val cc: code.api.util.CallContext = req.callContext
          val bankId = BankId(bankIdStr)
          for {
            user <- Future.successful(cc.user.openOrThrowException(AuthenticatedUserIsRequired))
            (_, _) <- NewStyle.function.getBank(bankId, Some(cc))
            (customers, _) <- Connector.connector.vend.getCustomersByUserId(user.userId, Some(cc))
              .map(connectorEmptyResponse(_, Some(cc)))
          } yield JSONFactory210.createCustomersJson(customers.filter(_.bankId == bankId.value))
        }
    }

    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(getMyCustomersAtBank), "GET",
      "/banks/BANK_ID/my/customers", "Get My Customers at Bank",
      s"""Returns a list of Customers at the Bank that are linked to the currently authenticated User.
         |
         |${userAuthenticationMessage(true)}""",
      EmptyBody, customerJSONs,
      List($AuthenticatedUserIsRequired, $BankNotFound, UserCustomerLinksNotFoundForUser, UnknownError),
      List(apiTagCustomer),
      None,
      http4sPartialFunction = Some(getMyCustomersAtBank)
    )

    // ─── getCustomersAtOneBank (GET /banks/BANK_ID/customers) — override ────

    val getCustomersAtOneBank: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / bankIdStr / "customers" =>
        EndpointHelpers.executeFuture(req) {
          implicit val cc: code.api.util.CallContext = req.callContext
          val bankId = BankId(bankIdStr)
          for {
            (requestParams, _) <- NewStyle.function.extractQueryParams(
              req.uri.renderString, List("limit", "offset", "sort_direction"), Some(cc))
            customers <- NewStyle.function.getCustomers(bankId, Some(cc), requestParams)
          } yield JSONFactory300.createCustomersJson(customers.sortBy(_.bankId))
        }
    }

    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(getCustomersAtOneBank), "GET",
      "/banks/BANK_ID/customers", "Get Customers at Bank",
      s"""Get Customers at Bank.
         |
         |${userAuthenticationMessage(true)}""",
      EmptyBody, customersJsonV300,
      List(AuthenticatedUserIsRequired, UserCustomerLinksNotFoundForUser, UnknownError),
      List(apiTagCustomer, apiTagUser),
      Some(List(canGetCustomersAtOneBank)),
      http4sPartialFunction = Some(getCustomersAtOneBank)
    )

    // ─── getCustomersMinimalAtOneBank (GET /banks/BANK_ID/customers-minimal) ─

    val getCustomersMinimalAtOneBank: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / bankIdStr / "customers-minimal" =>
        EndpointHelpers.executeFuture(req) {
          implicit val cc: code.api.util.CallContext = req.callContext
          val bankId = BankId(bankIdStr)
          for {
            (requestParams, _) <- NewStyle.function.extractQueryParams(
              req.uri.renderString, List("limit", "offset", "sort_direction"), Some(cc))
            customers <- NewStyle.function.getCustomers(bankId, Some(cc), requestParams)
          } yield createCustomersMinimalJson(customers.sortBy(_.bankId))
        }
    }

    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(getCustomersMinimalAtOneBank), "GET",
      "/banks/BANK_ID/customers-minimal", "Get Customers Minimal at Bank",
      "Get Customers Minimal at Bank.",
      EmptyBody, customersMinimalJsonV300,
      List(UserCustomerLinksNotFoundForUser, UnknownError),
      List(apiTagCustomer, apiTagUser),
      Some(List(canGetCustomersMinimalAtOneBank)),
      http4sPartialFunction = Some(getCustomersMinimalAtOneBank)
    )

    // ─── createProduct (PUT /banks/BANK_ID/products/PRODUCT_CODE → 201) ────
    // v5 override of v3.1.0/v4.0.0 — uses PutProductJsonV500 (parent_product_code).

    val createProduct: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ PUT -> `prefixPath` / "banks" / bankIdStr / "products" / productCodeStr =>
        EndpointHelpers.executeFutureCreated(req) {
          implicit val cc: code.api.util.CallContext = req.callContext
          val bankId = BankId(bankIdStr)
          val productCode = ProductCode(productCodeStr)
          for {
            user <- Future.successful(cc.user.openOrThrowException(AuthenticatedUserIsRequired))
            _ <- NewStyle.function.hasAtLeastOneEntitlement(failMsg = createProductEntitlementsRequiredText)(
              bankId.value, user.userId, createProductEntitlements, Some(cc))
            failMsg = s"$InvalidJsonFormat The Json body should be the $PutProductJsonV500 "
            product <- NewStyle.function.tryons(failMsg, 400, Some(cc)) {
              net.liftweb.json.parse(cc.httpBody.getOrElse("")).extract[PutProductJsonV500]
            }
            (parentProduct, _) <- product.parent_product_code.trim.nonEmpty match {
              case false => Future.successful((Empty, Some(cc)))
              case true =>
                NewStyle.function.getProduct(bankId, ProductCode(product.parent_product_code), Some(cc))
                  .map(p => (Full(p._1), p._2))
            }
            (success, _) <- NewStyle.function.createOrUpdateProduct(
              bankId = bankId.value, code = productCode.value,
              parentProductCode = parentProduct.map(_.code.value).toOption,
              name = product.name, category = null, family = null, superFamily = null,
              moreInfoUrl = product.more_info_url.getOrElse(""),
              termsAndConditionsUrl = product.terms_and_conditions_url.getOrElse(""),
              details = null,
              description = product.description.getOrElse(""),
              metaLicenceId = product.meta.map(_.license.id).getOrElse(""),
              metaLicenceName = product.meta.map(_.license.name).getOrElse(""),
              Some(cc)
            )
          } yield JSONFactory400.createProductJson(success)
        }
    }

    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(createProduct), "PUT",
      "/banks/BANK_ID/products/PRODUCT_CODE", "Create Product",
      s"""Create or Update Product for the Bank.
         |
         |${userAuthenticationMessage(true)}""",
      putProductJsonV500, productJsonV400.copy(attributes = None, fees = None),
      List($AuthenticatedUserIsRequired, $BankNotFound, UserHasMissingRoles, UnknownError),
      List(apiTagProduct),
      Some(List(canCreateProduct, canCreateProductAtAnyBank)),
      http4sPartialFunction = Some(createProduct)
    )

    // ─── addCardForBank (POST /management/banks/BANK_ID/cards → 201) ───────

    val addCardForBank: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "management" / "banks" / bankIdStr / "cards" =>
        EndpointHelpers.executeFutureCreated(req) {
          implicit val cc: code.api.util.CallContext = req.callContext
          val bankId = BankId(bankIdStr)
          for {
            user <- Future.successful(cc.user.openOrThrowException(AuthenticatedUserIsRequired))
            (_, _) <- NewStyle.function.getBank(bankId, Some(cc))
            failMsg = s"$InvalidJsonFormat The Json body should be the $CreatePhysicalCardJsonV500 "
            postJson <- NewStyle.function.tryons(failMsg, 400, Some(cc)) {
              net.liftweb.json.parse(cc.httpBody.getOrElse("")).extract[CreatePhysicalCardJsonV500]
            }
            _ <- postJson.allows match {
              case Nil => Future.successful(true)
              case _   => Helper.booleanToFuture(
                AllowedValuesAre + CardAction.availableValues.mkString(", "), cc = Some(cc)) {
                postJson.allows.forall(a => CardAction.availableValues.contains(a))
              }
            }
            cardReplacementReason <- NewStyle.function.tryons(
              AllowedValuesAre + CardReplacementReason.availableValues.mkString(", "), 400, Some(cc)) {
              postJson.replacement match {
                case Some(value) => CardReplacementReason.valueOf(value.reason_requested)
                case None        => CardReplacementReason.valueOf(CardReplacementReason.FIRST.toString)
              }
            }
            _ <- Helper.booleanToFuture(
              s"${maximumLimitExceeded.replace("10000", "10")} Current issue_number is ${postJson.issue_number}",
              cc = Some(cc)) {
              postJson.issue_number.length <= 10
            }
            (_, _) <- NewStyle.function.getBankAccount(bankId, AccountId(postJson.account_id), Some(cc))
            (_, _) <- NewStyle.function.getCustomerByCustomerId(postJson.customer_id, Some(cc))
            replacement = postJson.replacement.map(r => CardReplacementInfo(requestedDate = r.requested_date, cardReplacementReason))
            collected = postJson.collected.map(c => CardCollectionInfo(c))
            posted = postJson.posted.map(p => CardPostedInfo(p))
            cvv = ThreadLocalRandom.current().nextLong(100, 999)
            (card, _) <- NewStyle.function.createPhysicalCard(
              bankCardNumber = postJson.card_number,
              nameOnCard = postJson.name_on_card,
              cardType = postJson.card_type,
              issueNumber = postJson.issue_number,
              serialNumber = postJson.serial_number,
              validFrom = postJson.valid_from_date,
              expires = postJson.expires_date,
              enabled = postJson.enabled,
              cancelled = false, onHotList = false,
              technology = postJson.technology,
              networks = postJson.networks,
              allows = postJson.allows,
              accountId = postJson.account_id,
              bankId = bankId.value,
              replacement = replacement,
              pinResets = postJson.pin_reset.map(e => PinResetInfo(e.requested_date, PinResetReason.valueOf(e.reason_requested.toUpperCase))),
              collected = collected, posted = posted,
              customerId = postJson.customer_id,
              cvv = cvv.toString,
              brand = postJson.brand,
              Some(cc)
            )
          } yield createPhysicalCardJson(card, user).copy(cvv = cvv.toString)
        }
    }

    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(addCardForBank), "POST",
      "/management/banks/BANK_ID/cards", "Create Card",
      s"""Create Card at bank specified by BANK_ID.
         |
         |${userAuthenticationMessage(true)}""",
      createPhysicalCardJsonV500, physicalCardJsonV500,
      List($AuthenticatedUserIsRequired, $BankNotFound, UserHasMissingRoles, AllowedValuesAre, UnknownError),
      List(apiTagCard),
      Some(List(canCreateCardsForBank)),
      http4sPartialFunction = Some(addCardForBank)
    )

    // ─── getViewsForBankAccount (GET /banks/BANK_ID/accounts/ACCOUNT_ID/views) ─

    val getViewsForBankAccount: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / bankIdStr / "accounts" / accountIdStr / "views" =>
        EndpointHelpers.withBankAccount(req) { (user, _, cc) =>
          val bankId = BankId(bankIdStr)
          val accountId = AccountId(accountIdStr)
          for {
            permission <- NewStyle.function.permission(bankId, accountId, user, Some(cc))
            anyViewContainsCanSeeAvailableViewsForBankAccountPermission =
              permission.views.map(_.allowed_actions.exists(_ == CAN_SEE_AVAILABLE_VIEWS_FOR_BANK_ACCOUNT))
                .find(_ == true).getOrElse(false)
            _ <- Helper.booleanToFuture(
              s"${ErrorMessages.ViewDoesNotPermitAccess} You need the `${CAN_SEE_AVAILABLE_VIEWS_FOR_BANK_ACCOUNT}` permission on any your views",
              cc = Some(cc)) {
              anyViewContainsCanSeeAvailableViewsForBankAccountPermission
            }
            views = Views.views.vend.availableViewsForAccount(BankIdAccountId(bankId, accountId))
          } yield createViewsJsonV500(views)
        }
    }

    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(getViewsForBankAccount), "GET",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/views", "Get Views for Account",
      s"""Returns the list of the views created for account ACCOUNT_ID at BANK_ID.
         |
         |${userAuthenticationMessage(true)} and the user needs to have access to the owner view.""",
      EmptyBody, viewsJsonV500,
      List($AuthenticatedUserIsRequired, $BankAccountNotFound, UnknownError),
      List(apiTagView, apiTagAccount),
      None,
      http4sPartialFunction = Some(getViewsForBankAccount)
    )

    // ─── getMetricsAtBank (GET /management/metrics/banks/BANK_ID) ──────────

    val getMetricsAtBank: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "management" / "metrics" / "banks" / bankIdStr =>
        EndpointHelpers.executeFuture(req) {
          implicit val cc: code.api.util.CallContext = req.callContext
          for {
            httpParams <- NewStyle.function.extractHttpParamsFromUrl(req.uri.renderString)
            (obpQueryParams, _) <- createQueriesByHttpParamsFuture(httpParams, Some(cc))
            metrics <- Future(APIMetrics.apiMetrics.vend.getAllMetrics(obpQueryParams ::: List(OBPBankId(bankIdStr))))
          } yield JSONFactory210.createMetricsJson(metrics)
        }
    }

    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(getMetricsAtBank), "GET",
      "/management/metrics/banks/BANK_ID", "Get Metrics at Bank",
      "Get the all metrics at the Bank specified by BANK_ID. Requires CanReadMetrics role.",
      EmptyBody, metricsJson,
      List($AuthenticatedUserIsRequired, UserHasMissingRoles, UnknownError),
      List(apiTagMetric, apiTagApi),
      Some(List(canGetMetricsAtOneBank)),
      http4sPartialFunction = Some(getMetricsAtBank)
    )

    // ─── getSystemViewsIds (GET /system-views-ids) ──────────────────────────

    val getSystemViewsIds: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "system-views-ids" =>
        EndpointHelpers.executeFuture(req) {
          implicit val cc: code.api.util.CallContext = req.callContext
          for {
            views <- ViewNewStyle.systemViews()
          } yield createViewsIdsJsonV500(views)
        }
    }

    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(getSystemViewsIds), "GET",
      "/system-views-ids", "Get Ids of System Views",
      s"""Get Ids of System Views.
         |
         |${userAuthenticationMessage(true)}""",
      EmptyBody, viewIdsJsonV500,
      List($AuthenticatedUserIsRequired, $BankNotFound, UnknownError),
      List(apiTagSystemView),
      Some(List(canGetSystemView)),
      http4sPartialFunction = Some(getSystemViewsIds)
    )

    // ─── customer-account-link endpoints (6) ────────────────────────────────

    val createCustomerAccountLink: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "banks" / bankIdStr / "customer-account-links" =>
        EndpointHelpers.executeFutureCreated(req) {
          implicit val cc: code.api.util.CallContext = req.callContext
          val bankId = BankId(bankIdStr)
          for {
            (_, _) <- NewStyle.function.getBank(bankId, Some(cc))
            postedData <- NewStyle.function.tryons(
              s"$InvalidJsonFormat The Json body should be the $CreateCustomerAccountLinkJson ", 400, Some(cc)) {
              net.liftweb.json.parse(cc.httpBody.getOrElse("")).extract[CreateCustomerAccountLinkJson]
            }
            (customer, _) <- NewStyle.function.getCustomerByCustomerId(postedData.customer_id, Some(cc))
            _ <- booleanToFuture(
              s"Bank of the customer specified by the CUSTOMER_ID(${customer.bankId}) has to matches BANK_ID(${bankId.value}) in URL",
              400, Some(cc)) { customer.bankId == bankId.value }
            (_, _) <- NewStyle.function.getBankAccount(bankId, AccountId(postedData.account_id), Some(cc))
            _ <- booleanToFuture("Field customer_id is not defined in the posted json!", 400, Some(cc)) {
              postedData.customer_id.nonEmpty
            }
            (existingLink, _) <- Connector.connector.vend.getCustomerAccountLink(postedData.customer_id, postedData.account_id, Some(cc))
            _ <- booleanToFuture(AccountAlreadyExistsForCustomer, 400, Some(cc)) { existingLink.isEmpty }
            (link, _) <- NewStyle.function.createCustomerAccountLink(
              postedData.customer_id, postedData.bank_id, postedData.account_id, postedData.relationship_type, Some(cc))
          } yield JSONFactory500.createCustomerAccountLinkJson(link)
        }
    }

    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(createCustomerAccountLink), "POST",
      "/banks/BANK_ID/customer-account-links", "Create Customer Account Link",
      s"""Link a Customer to a Account.
         |
         |${userAuthenticationMessage(true)}""",
      createCustomerAccountLinkJson, customerAccountLinkJson,
      List($AuthenticatedUserIsRequired, $BankNotFound, BankAccountNotFound, InvalidJsonFormat,
        CustomerNotFoundByCustomerId, UserHasMissingRoles, AccountAlreadyExistsForCustomer,
        CreateCustomerAccountLinkError, UnknownError),
      List(apiTagCustomer, apiTagAccount),
      Some(List(canCreateCustomerAccountLink)),
      http4sPartialFunction = Some(createCustomerAccountLink)
    )

    val getCustomerAccountLinksByCustomerId: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / bankIdStr / "customers" / customerId / "customer-account-links" =>
        EndpointHelpers.executeFuture(req) {
          implicit val cc: code.api.util.CallContext = req.callContext
          val bankId = BankId(bankIdStr)
          for {
            (customer, _) <- NewStyle.function.getCustomerByCustomerId(customerId, Some(cc))
            _ <- booleanToFuture(
              s"Bank of the customer specified by the CUSTOMER_ID(${customer.bankId}) has to matches BANK_ID(${bankId.value}) in URL",
              400, Some(cc)) { customer.bankId == bankId.value }
            (links, _) <- NewStyle.function.getCustomerAccountLinksByCustomerId(customerId, Some(cc))
          } yield JSONFactory500.createCustomerAccountLinksJon(links)
        }
    }

    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(getCustomerAccountLinksByCustomerId), "GET",
      "/banks/BANK_ID/customers/CUSTOMER_ID/customer-account-links",
      "Get Customer Account Links by CUSTOMER_ID",
      s"""Get Customer Account Links by CUSTOMER_ID.
         |
         |${userAuthenticationMessage(true)}""",
      EmptyBody, customerAccountLinksJson,
      List($AuthenticatedUserIsRequired, $BankNotFound, CustomerNotFoundByCustomerId,
        UserHasMissingRoles, UnknownError),
      List(apiTagCustomer),
      Some(List(canGetCustomerAccountLinks)),
      http4sPartialFunction = Some(getCustomerAccountLinksByCustomerId)
    )

    val getCustomerAccountLinksByBankIdAccountId: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / bankIdStr / "accounts" / accountIdStr / "customer-account-links" =>
        EndpointHelpers.withBankAccount(req) { (_, _, cc) =>
          for {
            (links, _) <- NewStyle.function.getCustomerAccountLinksByBankIdAccountId(bankIdStr, accountIdStr, Some(cc))
          } yield JSONFactory500.createCustomerAccountLinksJon(links)
        }
    }

    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(getCustomerAccountLinksByBankIdAccountId), "GET",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/customer-account-links",
      "Get Customer Account Links by ACCOUNT_ID",
      s"""Get Customer Account Links by ACCOUNT_ID.
         |
         |${userAuthenticationMessage(true)}""",
      EmptyBody, customerAccountLinksJson,
      List($AuthenticatedUserIsRequired, $BankNotFound, BankAccountNotFound,
        UserHasMissingRoles, UnknownError),
      List(apiTagCustomer),
      Some(List(canGetCustomerAccountLinks)),
      http4sPartialFunction = Some(getCustomerAccountLinksByBankIdAccountId)
    )

    val getCustomerAccountLinkById: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / bankIdStr / "customer-account-links" / customerAccountLinkId =>
        EndpointHelpers.withBank(req) { (_, cc) =>
          for {
            (link, _) <- NewStyle.function.getCustomerAccountLinkById(customerAccountLinkId, Some(cc))
          } yield JSONFactory500.createCustomerAccountLinkJson(link)
        }
    }

    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(getCustomerAccountLinkById), "GET",
      "/banks/BANK_ID/customer-account-links/CUSTOMER_ACCOUNT_LINK_ID",
      "Get Customer Account Link by Id",
      s"""Get Customer Account Link by CUSTOMER_ACCOUNT_LINK_ID.
         |
         |${userAuthenticationMessage(true)}""",
      EmptyBody, customerAccountLinkJson,
      List($AuthenticatedUserIsRequired, $BankNotFound, UserHasMissingRoles, UnknownError),
      List(apiTagCustomer),
      Some(List(canGetCustomerAccountLink)),
      http4sPartialFunction = Some(getCustomerAccountLinkById)
    )

    val updateCustomerAccountLinkById: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ PUT -> `prefixPath` / "banks" / bankIdStr / "customer-account-links" / customerAccountLinkId =>
        EndpointHelpers.executeFuture(req) {
          implicit val cc: code.api.util.CallContext = req.callContext
          val bankId = BankId(bankIdStr)
          for {
            (_, _) <- NewStyle.function.getBank(bankId, Some(cc))
            postedData <- NewStyle.function.tryons(
              s"$InvalidJsonFormat The Json body should be the $UpdateCustomerAccountLinkJson ", 400, Some(cc)) {
              net.liftweb.json.parse(cc.httpBody.getOrElse("")).extract[UpdateCustomerAccountLinkJson]
            }
            (_, _) <- NewStyle.function.getCustomerAccountLinkById(customerAccountLinkId, Some(cc))
            (link, _) <- NewStyle.function.updateCustomerAccountLinkById(customerAccountLinkId, postedData.relationship_type, Some(cc))
          } yield JSONFactory500.createCustomerAccountLinkJson(link)
        }
    }

    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(updateCustomerAccountLinkById), "PUT",
      "/banks/BANK_ID/customer-account-links/CUSTOMER_ACCOUNT_LINK_ID",
      "Update Customer Account Link by Id",
      s"""Update Customer Account Link by CUSTOMER_ACCOUNT_LINK_ID.
         |
         |${userAuthenticationMessage(true)}""",
      updateCustomerAccountLinkJson, customerAccountLinkJson,
      List($AuthenticatedUserIsRequired, $BankNotFound, UserHasMissingRoles, UnknownError),
      List(apiTagCustomer),
      Some(List(canUpdateCustomerAccountLink)),
      http4sPartialFunction = Some(updateCustomerAccountLinkById)
    )

    val deleteCustomerAccountLinkById: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ DELETE -> `prefixPath` / "banks" / bankIdStr / "customer-account-links" / customerAccountLinkId =>
        EndpointHelpers.withUserAndBankDelete(req) { (_, _, cc) =>
          for {
            (_, _) <- NewStyle.function.getCustomerAccountLinkById(customerAccountLinkId, Some(cc))
            (deleted, _) <- NewStyle.function.deleteCustomerAccountLinkById(customerAccountLinkId, Some(cc))
          } yield deleted
        }
    }

    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(deleteCustomerAccountLinkById), "DELETE",
      "/banks/BANK_ID/customer-account-links/CUSTOMER_ACCOUNT_LINK_ID",
      "Delete Customer Account Link",
      s"""Delete Customer Account Link by CUSTOMER_ACCOUNT_LINK_ID.
         |
         |${userAuthenticationMessage(true)}""",
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, $BankNotFound, UserHasMissingRoles, UnknownError),
      List(apiTagCustomer),
      Some(List(canDeleteCustomerAccountLink)),
      http4sPartialFunction = Some(deleteCustomerAccountLinkById)
    )

    // ─── getAdapterInfo (GET /adapter) — v3.1.0 override ───────────────────

    val getAdapterInfo: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "adapter" =>
        EndpointHelpers.executeFuture(req) {
          implicit val cc: code.api.util.CallContext = req.callContext
          for {
            (adapterInfo, _) <- NewStyle.function.getAdapterInfo(Some(cc))
          } yield JSONFactory500.createAdapterInfoJson(
            adapterInfo, cc.startTime.getOrElse(Helpers.now).getTime)
        }
    }

    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(getAdapterInfo), "GET",
      "/adapter", "Get Adapter Info",
      s"""Get basic information about the Adapter.
         |
         |${userAuthenticationMessage(true)}""",
      EmptyBody, adapterInfoJsonV500,
      List($AuthenticatedUserIsRequired, UserHasMissingRoles, UnknownError),
      List(apiTagApi),
      Some(List(canGetAdapterInfo)),
      http4sPartialFunction = Some(getAdapterInfo)
    )

    val allRoutes: HttpRoutes[IO] =
      Kleisli[HttpF, Request[IO], Response[IO]] { req: Request[IO] =>
        root(req)
          .orElse(getBanks(req))
          .orElse(getBank(req))
          .orElse(createBank(req))
          .orElse(updateBank(req))
          .orElse(createAccount(req))
          .orElse(getProducts(req))
          .orElse(getProduct(req))
          .orElse(createProduct(req))
          .orElse(addCardForBank(req))
          .orElse(getViewsForBankAccount(req))
          .orElse(createSystemView(req))
          .orElse(getSystemView(req))
          .orElse(updateSystemView(req))
          .orElse(deleteSystemView(req))
          .orElse(getSystemViewsIds(req))
          .orElse(createUserAuthContext(req))
          .orElse(getUserAuthContexts(req))
          .orElse(createUserAuthContextUpdateRequest(req))
          .orElse(answerUserAuthContextUpdateChallenge(req))
          .orElse(createConsentRequest(req))
          .orElse(getConsentRequest(req))
          .orElse(getConsentByConsentRequestId(req))
          .orElse(createConsentByConsentRequestId(req))
          .orElse(headAtms(req))
          .orElse(createCustomer(req))
          .orElse(getCustomerOverview(req))
          .orElse(getCustomerOverviewFlat(req))
          .orElse(getMyCustomersAtAnyBank(req))
          .orElse(getMyCustomersAtBank(req))
          .orElse(getCustomersAtOneBank(req))
          .orElse(getCustomersMinimalAtOneBank(req))
          .orElse(createCustomerAccountLink(req))
          .orElse(getCustomerAccountLinksByCustomerId(req))
          .orElse(getCustomerAccountLinksByBankIdAccountId(req))
          .orElse(getCustomerAccountLinkById(req))
          .orElse(updateCustomerAccountLinkById(req))
          .orElse(deleteCustomerAccountLinkById(req))
          .orElse(getMetricsAtBank(req))
          .orElse(getAdapterInfo(req))
      }

    val allRoutesWithMiddleware: HttpRoutes[IO] =
      ResourceDocMiddleware.apply(resourceDocs)(allRoutes)

    // ─── path-rewriting bridge: /obp/v5.0.0/… → /obp/v4.0.0/… ─────────────
    // Cascades inherited (v1.2.1–v4.0.0) endpoints through the http4s versions
    // instead of falling all the way through to Http4sLiftWebBridge.
    val v500ToV400Bridge: HttpRoutes[IO] = Kleisli[HttpF, Request[IO], Response[IO]] { req =>
      val rawPath = req.uri.path.renderString
      if (rawPath.startsWith("/obp/v5.0.0/")) {
        val rewritten = rawPath.replaceFirst("/obp/v5\\.0\\.0/", "/obp/v4.0.0/")
        val newUri = req.uri.withPath(Uri.Path.unsafeFromString(rewritten))
        val rewrittenReq = req.withUri(newUri)
        code.api.v4_0_0.Http4s400.wrappedRoutesV400Services.run(rewrittenReq)
      } else {
        OptionT.none[IO, Response[IO]]
      }
    }
  }

  val wrappedRoutesV500Services: HttpRoutes[IO] =
    Kleisli[HttpF, Request[IO], Response[IO]] { req =>
      Implementations5_0_0.allRoutesWithMiddleware.run(req)
        .orElse(Implementations5_0_0.v500ToV400Bridge.run(req))
    }
  
  // Wrap routes with JSON not-found handler for better error responses
  val wrappedRoutesV500ServicesWithJsonNotFound: HttpRoutes[IO] = {
    import code.api.util.APIUtil
    import code.api.util.ErrorMessages
    Kleisli[HttpF, Request[IO], Response[IO]] { req: Request[IO] =>
      wrappedRoutesV500Services(req).orElse {
        OptionT.liftF(IO.pure {
          val contentType = req.headers.get(CIString("Content-Type")).map(_.head.value).getOrElse("")
          Response[IO](status = Status.NotFound)
            .withEntity(APIUtil.errorJsonResponse(s"${ErrorMessages.InvalidUri}Current Url is (${req.uri}), Current Content-Type Header is ($contentType)", 404).toResponse.data)
            .withContentType(org.http4s.headers.`Content-Type`(MediaType.application.json))
        })
      }
    }
  }
  
  // Combined routes with bridge fallback for testing proxy parity
  // This mimics the production server behavior where unimplemented endpoints fall back to Lift
  val wrappedRoutesV500ServicesWithBridge: HttpRoutes[IO] = {
    import code.api.util.http4s.Http4sLiftWebBridge
    Kleisli[HttpF, Request[IO], Response[IO]] { req: Request[IO] =>
      wrappedRoutesV500Services(req)
        .orElse(Http4sLiftWebBridge.routes.run(req))
    }
  }
}
