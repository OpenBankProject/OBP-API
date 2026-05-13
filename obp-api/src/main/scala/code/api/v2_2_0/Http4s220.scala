package code.api.v2_2_0

import cats.data.{Kleisli, OptionT}
import cats.effect._
import code.api.Constant._
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON._
import code.api.util.APIUtil.{EmptyBody, ResourceDoc, _}
import code.api.util.ApiRole._
import code.api.util.ApiTag._
import code.api.util.ErrorMessages._
import code.api.util.http4s.Http4sRequestAttributes.{EndpointHelpers, RequestOps}
import code.api.util.http4s.ResourceDocMiddleware
import code.api.util.newstyle.ViewNewStyle
import code.api.util.{APIUtil, CallContext, CustomJsonFormats, NewStyle}
import code.api.v1_2_1.{CreateViewJsonV121, JSONFactory => JSONFactory121, UpdateViewJsonV121}
import code.api.v2_1_0.{ConsumerPostJSON, JSONFactory210, PostCounterpartyJSON}

import code.bankconnectors.Connector
import code.consumer.Consumers
import code.metadata.counterparties.{Counterparties, MappedCounterparty}
import code.metrics.ConnectorMetricsProvider
import code.model.{BankX, Consumer}
import code.model.dataAccess.BankAccountCreation
import code.views.Views
import code.views.system.ViewPermission
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.model._
import com.openbankproject.commons.util.{ApiVersion, ApiVersionStatus, ScannedApiVersion}
import net.liftweb.common.Full
import net.liftweb.json.JsonAST.prettyRender
import net.liftweb.json.{Extraction, Formats, Serialization}
import net.liftweb.util.StringHelpers
import org.http4s._
import org.http4s.dsl.io._

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Future

object Http4s220 {
  val implementedInApiVersion: ScannedApiVersion = ApiVersion.v2_2_0
  val versionStatus: String                       = ApiVersionStatus.STABLE.toString
  val resourceDocs: ArrayBuffer[ResourceDoc]       = ArrayBuffer[ResourceDoc]()

  implicit val formats: Formats = CustomJsonFormats.formats

  type HttpF[A] = OptionT[IO, A]

  object Implementations2_2_0 {
    val prefixPath = Root / ApiPathZero.toString / implementedInApiVersion.toString

    // ─── root ─────────────────────────────────────────────────────────────────

    val root: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` =>
        EndpointHelpers.executeAndRespond(req) { _ =>
          Future.successful(JSONFactory121.getApiInfoJSON(ApiVersion.v2_2_0, versionStatus))
        }
      case req @ GET -> `prefixPath` / "root" =>
        EndpointHelpers.executeAndRespond(req) { _ =>
          Future.successful(JSONFactory121.getApiInfoJSON(ApiVersion.v2_2_0, versionStatus))
        }
    }

    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(root), "GET", "/root",
      "Get API Info (root)",
      """Returns information about:
        |
        |* API version
        |* Hosted by information
        |* Git Commit""",
      EmptyBody, apiInfoJSON,
      List(UnknownError, MandatoryPropertyIsNotSet), apiTagApi :: Nil, None,
      http4sPartialFunction = Some(root))

    // ─── getViewsForBankAccount ───────────────────────────────────────────────

    val getViewsForBankAccount: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / _ / "accounts" / _ / "views" =>
        EndpointHelpers.withBankAccount(req) { (user, account, cc) =>
          for {
            permission <- NewStyle.function.permission(account.bankId, account.accountId, user, Some(cc))
            anyCanSee = permission.views
              .map(_.allowed_actions.exists(_ == CAN_SEE_AVAILABLE_VIEWS_FOR_BANK_ACCOUNT))
              .contains(true)
            _ <- code.util.Helper.booleanToFuture(
              s"${ViewDoesNotPermitAccess} You need the `${CAN_SEE_AVAILABLE_VIEWS_FOR_BANK_ACCOUNT}` permission on any your views",
              cc = Some(cc)) { anyCanSee }
            views <- Future(Views.views.vend.availableViewsForAccount(BankIdAccountId(account.bankId, account.accountId)))
          } yield JSONFactory220.createViewsJSON(views)
        }
    }

    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(getViewsForBankAccount), "GET",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/views",
      "Get Views for Account",
      s"""Returns the list of the views created for account ACCOUNT_ID at BANK_ID.
         |
         |${userAuthenticationMessage(true)} and the user needs to have access to the owner view.""",
      EmptyBody, viewsJSONV220,
      List(AuthenticatedUserIsRequired, BankAccountNotFound, UnknownError),
      List(apiTagView, apiTagAccount), None,
      http4sPartialFunction = Some(getViewsForBankAccount))

    // ─── createViewForBankAccount ─────────────────────────────────────────────

    val createViewForBankAccount: HttpRoutes[IO] = HttpRoutes.of[IO] {
      // VIEW_ACCOUNT_ID (non-standard name) bypasses middleware account-existence check so the
      // handler can return 400 (not 404) for a missing account, matching Lift behaviour.
      case req @ POST -> `prefixPath` / "banks" / _ / "accounts" / accountIdStr / "views" =>
        implicit val cc: CallContext = req.callContext
        val io = for {
          user    <- IO.fromOption(cc.user.toOption)(new RuntimeException(AuthenticatedUserIsRequired))
          bank    <- IO.fromOption(cc.bank)(new RuntimeException(BankNotFound))
          rawBox  <- IO.fromFuture(IO(Connector.connector.vend.checkBankAccountExists(bank.bankId, AccountId(accountIdStr), Some(cc)).map(_._1)))
          account <- IO(unboxFullOrFail(rawBox, Some(cc), BankAccountNotFound))
          body    <- IO.pure(cc.httpBody.getOrElse(""))
          result  <- code.api.util.http4s.RequestScopeConnection.fromFuture(
            createViewImpl(user, account, body, cc))
        } yield result
        io.attempt.flatMap {
          case Right(result) =>
            Created(prettyRender(Extraction.decompose(result)))
          case Left(err) =>
            code.api.util.http4s.ErrorResponseConverter.toHttp4sResponse(err, cc)
        }
    }

    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(createViewForBankAccount), "POST",
      "/banks/BANK_ID/accounts/VIEW_ACCOUNT_ID/views",
      "Create View",
      s"""Create a view on bank account.
         |
         |${userAuthenticationMessage(true)} and the user needs to have access to the owner view.""",
      createViewJsonV121, viewJSONV220,
      List(AuthenticatedUserIsRequired, InvalidJsonFormat, BankAccountNotFound, UnknownError),
      List(apiTagAccount, apiTagView, apiTagOldStyle), None,
      http4sPartialFunction = Some(createViewForBankAccount))

    private def createViewImpl(user: User, account: BankAccount, body: String, cc: CallContext): Future[ViewJSONV220] = {
      for {
        createBodyJson <- NewStyle.function.tryons(InvalidJsonFormat, 400, Some(cc)) {
          net.liftweb.json.parse(body).extract[CreateViewJsonV121]
        }
        _ <- code.util.Helper.booleanToFuture(
          s"$InvalidCustomViewFormat Current view_name (${createBodyJson.name})", cc = Some(cc)) {
          isValidCustomViewName(createBodyJson.name)
        }
        permission <- NewStyle.function.permission(account.bankId, account.accountId, user, Some(cc))
        anyCanCreate = permission.views.map(_.allowed_actions.exists(_ == CAN_CREATE_CUSTOM_VIEW)).contains(true)
        _ <- code.util.Helper.booleanToFuture(
          s"${CreateCustomViewError} You need the `${CAN_CREATE_CUSTOM_VIEW}` permission on any your views",
          cc = Some(cc)) { anyCanCreate }
        createViewJson = CreateViewJson(
          createBodyJson.name,
          createBodyJson.description,
          metadata_view = "",
          createBodyJson.is_public,
          createBodyJson.which_alias_to_use,
          createBodyJson.hide_metadata_if_alias_used,
          createBodyJson.allowed_actions
        )
        (view, _) <- ViewNewStyle.createCustomView(BankIdAccountId(account.bankId, account.accountId), createViewJson, Some(cc))
      } yield JSONFactory220.createViewJSON(view)
    }

    // ─── updateViewForBankAccount ─────────────────────────────────────────────

    val updateViewForBankAccount: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ PUT -> `prefixPath` / "banks" / _ / "accounts" / _ / "views" / viewIdStr =>
        implicit val cc: CallContext = req.callContext
        val io = for {
          user    <- IO.fromOption(cc.user.toOption)(new RuntimeException(AuthenticatedUserIsRequired))
          account <- IO.fromOption(cc.bankAccount)(new RuntimeException(AccountNotFound))
          body    <- IO.pure(cc.httpBody.getOrElse(""))
          result  <- code.api.util.http4s.RequestScopeConnection.fromFuture(
            updateViewImpl(user, account, ViewId(viewIdStr), body, cc))
        } yield result
        io.attempt.flatMap {
          case Right(result) =>
            Ok(prettyRender(Extraction.decompose(result)))
          case Left(err) =>
            code.api.util.http4s.ErrorResponseConverter.toHttp4sResponse(err, cc)
        }
    }

    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(updateViewForBankAccount), "PUT",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/views/UPD_VIEW_ID",
      "Update View",
      s"""Update an existing view on a bank account.
         |
         |${userAuthenticationMessage(true)} and the user needs to have access to the owner view.""",
      updateViewJsonV121, viewJSONV220,
      List(InvalidJsonFormat, AuthenticatedUserIsRequired, BankAccountNotFound, UnknownError),
      List(apiTagAccount, apiTagView, apiTagOldStyle), None,
      http4sPartialFunction = Some(updateViewForBankAccount))

    private def updateViewImpl(user: User, account: BankAccount, viewId: ViewId, body: String, cc: CallContext): Future[ViewJSONV220] = {
      for {
        updateBodyJson <- NewStyle.function.tryons(InvalidJsonFormat, 400, Some(cc)) {
          net.liftweb.json.parse(body).extract[UpdateViewJsonV121]
        }
        _ <- code.util.Helper.booleanToFuture(
          s"$InvalidCustomViewFormat Current view_name (${viewId.value})", cc = Some(cc)) {
          viewId.value.startsWith("_")
        }
        view <- ViewNewStyle.checkViewAccessAndReturnView(viewId, BankIdAccountId(account.bankId, account.accountId), Some(user), Some(cc))
        _ <- code.util.Helper.booleanToFuture(SystemViewsCanNotBeModified, cc = Some(cc)) { !view.isSystem }
        permission <- NewStyle.function.permission(account.bankId, account.accountId, user, Some(cc))
        anyCanUpdate = permission.views.map(_.allowed_actions.exists(_ == CAN_UPDATE_CUSTOM_VIEW)).contains(true)
        _ <- code.util.Helper.booleanToFuture(
          s"${CreateCustomViewError} You need the `${CAN_UPDATE_CUSTOM_VIEW}` permission on any your views",
          cc = Some(cc)) { anyCanUpdate }
        updateViewJson = UpdateViewJSON(
          description                = updateBodyJson.description,
          metadata_view              = view.metadataView,
          is_public                  = updateBodyJson.is_public,
          which_alias_to_use         = updateBodyJson.which_alias_to_use,
          hide_metadata_if_alias_used = updateBodyJson.hide_metadata_if_alias_used,
          allowed_actions            = updateBodyJson.allowed_actions
        )
        (updatedView, _) <- ViewNewStyle.updateCustomView(BankIdAccountId(account.bankId, account.accountId), viewId, updateViewJson, Some(cc))
      } yield JSONFactory220.createViewJSON(updatedView)
    }

    // ─── getCurrentFxRate ─────────────────────────────────────────────────────

    private val getCurrentFxRateIsPublic = APIUtil.getPropsAsBoolValue("apiOptions.getCurrentFxRateIsPublic", false)

    val getCurrentFxRate: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / _ / "fx" / fromCurrencyCode / toCurrencyCode =>
        EndpointHelpers.withBank(req) { (bank, cc) =>
          val fromUpper = fromCurrencyCode.toUpperCase
          val toUpper   = toCurrencyCode.toUpperCase
          for {
            _ <- if (!getCurrentFxRateIsPublic)
                   code.util.Helper.booleanToFuture(AuthenticatedUserIsRequired, failCode = 401, cc = Some(cc)) { cc.user.isDefined }
                 else Future.unit
            _ <- code.util.Helper.booleanToFuture(ConsumerHasMissingRoles + canReadFx, cc = Some(cc)) {
              checkScope(bank.bankId.value, getConsumerPrimaryKey(Some(cc)), canReadFx)
            }
            _ <- NewStyle.function.isValidCurrencyISOCode(fromUpper, Some(cc))
            _ <- NewStyle.function.isValidCurrencyISOCode(toUpper, Some(cc))
            fxRate <- NewStyle.function.getExchangeRate(bank.bankId, fromUpper, toUpper, Some(cc))
          } yield JSONFactory220.createFXRateJSON(fxRate)
        }
    }

    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(getCurrentFxRate), "GET",
      "/banks/BANK_ID/fx/FROM_CURRENCY_CODE/TO_CURRENCY_CODE",
      "Get Current FxRate",
      """Get the latest FX rate specified by BANK_ID, FROM_CURRENCY_CODE and TO_CURRENCY_CODE.""",
      EmptyBody, fXRateJSON,
      List(InvalidISOCurrencyCode, AuthenticatedUserIsRequired, FXCurrencyCodeCombinationsNotSupported, UnknownError),
      List(apiTagFx), None,
      http4sPartialFunction = Some(getCurrentFxRate))

    // ─── getExplicitCounterpartiesForAccount ──────────────────────────────────

    val getExplicitCounterpartiesForAccount: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / _ / "accounts" / _ / _ / "counterparties" =>
        EndpointHelpers.withView(req) { (user, account, view, cc) =>
          for {
            _ <- code.util.Helper.booleanToFuture(
              s"${NoViewPermission} You need the `${CAN_GET_COUNTERPARTY}` permission on the View(${view.viewId.value})",
              cc = Some(cc)) {
              ViewPermission.findViewPermissions(view).exists(_.permission.get == CAN_GET_COUNTERPARTY)
            }
            (counterparties, _) <- NewStyle.function.getCounterparties(account.bankId, account.accountId, view.viewId, Some(cc))
            _ <- code.util.Helper.booleanToFuture(CreateOrUpdateCounterpartyMetadataError, 400, cc = Some(cc)) {
              counterparties.forall { cp =>
                Counterparties.counterparties.vend
                  .getOrCreateMetadata(account.bankId, account.accountId, cp.counterpartyId, cp.name)
                  .isDefined
              }
            }
          } yield JSONFactory220.createCounterpartiesJSON(counterparties)
        }
    }

    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(getExplicitCounterpartiesForAccount), "GET",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/counterparties",
      "Get Counterparties (Explicit)",
      s"""Gets the explicit Counterparties on an Account / View.
         |
         |${userAuthenticationMessage(true)}""",
      EmptyBody, counterpartiesJsonV220,
      List(AuthenticatedUserIsRequired, BankAccountNotFound, ViewNotFound, NoViewPermission,
        UserNoPermissionAccessView, UnknownError),
      List(apiTagCounterparty, apiTagPSD2PIS, apiTagAccount, apiTagPsd2), None,
      http4sPartialFunction = Some(getExplicitCounterpartiesForAccount))

    // ─── getExplicitCounterpartyById ──────────────────────────────────────────

    val getExplicitCounterpartyById: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / _ / "accounts" / _ / _ / "counterparties" / _ =>
        EndpointHelpers.withCounterparty(req) { (_, account, view, counterparty, cc) =>
          for {
            _ <- code.util.Helper.booleanToFuture(
              s"${NoViewPermission} You need the `${CAN_GET_COUNTERPARTY}` permission on the View(${view.viewId.value})",
              cc = Some(cc)) {
              ViewPermission.findViewPermissions(view).exists(_.permission.get == CAN_GET_COUNTERPARTY)
            }
            counterpartyMetadata <- NewStyle.function.getMetadata(
              account.bankId, account.accountId, counterparty.counterpartyId, Some(cc))
          } yield JSONFactory220.createCounterpartyWithMetadataJSON(counterparty, counterpartyMetadata)
        }
    }

    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(getExplicitCounterpartyById), "GET",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/counterparties/COUNTERPARTY_ID",
      "Get Counterparty by Counterparty Id (Explicit)",
      s"""Information returned about the Counterparty specified by COUNTERPARTY_ID.
         |
         |${userAuthenticationMessage(true)}""",
      EmptyBody, counterpartyWithMetadataJson,
      List(AuthenticatedUserIsRequired, BankNotFound, UnknownError),
      List(apiTagCounterparty, apiTagPSD2PIS, apiTagCounterpartyMetaData, apiTagPsd2), None,
      http4sPartialFunction = Some(getExplicitCounterpartyById))

    // ─── getMessageDocs ───────────────────────────────────────────────────────

    val getMessageDocs: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "message-docs" / connector =>
        EndpointHelpers.executeAndRespond(req) { cc =>
          Future {
            val connectorObject = unboxFullOrFail(
              net.liftweb.util.Helpers.tryo { Connector.getConnectorInstance(connector) },
              Some(cc),
              s"$InvalidConnector Current Input is $connector. It should be eg: rest_vMar2019..."
            )
            JSONFactory220.createMessageDocsJson(connectorObject.messageDocs.toList)
          }
        }
    }

    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(getMessageDocs), "GET",
      "/message-docs/CONNECTOR",
      "Get Message Docs",
      """These message docs provide example messages sent by OBP to the message queue for processing by the Adapter.
        |`CONNECTOR`: rest_vMar2019, stored_procedure_vDec2019 ...""",
      EmptyBody, messageDocsJson,
      List(InvalidConnector, UnknownError),
      List(apiTagMessageDoc, apiTagDocumentation, apiTagApi), None,
      http4sPartialFunction = Some(getMessageDocs))

    // ─── createBank ───────────────────────────────────────────────────────────

    val createBank: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "banks" =>
        EndpointHelpers.withUserAndBodyCreated[BankJSONV220, BankJSONV220](req) { (user, bank, cc) =>
          val checkShort = APIUtil.checkShortString(bank.id)
          for {
            _ <- code.util.Helper.booleanToFuture(
              s"$InvalidJsonFormat Min length of BANK_ID should be 5 characters.", cc = Some(cc)) {
              bank.id.length > 5
            }
            _ <- code.util.Helper.booleanToFuture(s"$checkShort.", cc = Some(cc)) { checkShort.isEmpty }
            _ <- code.util.Helper.booleanToFuture(
              s"$InvalidJsonFormat BANK_ID can not contain `::::` characters", cc = Some(cc)) {
              !`checkIfContains::::` (bank.id)
            }
            consumer <- Future { unboxFullOrFail(cc.consumer, Some(cc), InvalidConsumerCredentials) }
            _ <- Future {
              unboxFullOrFail(
                NewStyle.function.hasEntitlementAndScope("", user.userId, consumer.id.get.toString, canCreateBank, Some(cc)),
                Some(cc), UserHasMissingRoles + canCreateBank)
            }
            (success, _) <- NewStyle.function.createOrUpdateBank(
              bank.id, bank.full_name, bank.short_name, bank.logo_url, bank.website_url,
              bank.swift_bic, bank.national_identifier,
              bank.bank_routing.scheme, bank.bank_routing.address, Some(cc)
            )
            entitlements <- Future {
              unboxFullOrFail(
                code.entitlement.Entitlement.entitlement.vend.getEntitlementsByUserId(user.userId),
                Some(cc), UnknownError)
            }
            _ <- Future {
              val bankEntitlements = entitlements.filter(_.bankId == bank.id)
              if (!bankEntitlements.exists(_.roleName == canCreateEntitlementAtOneBank.toString()))
                code.entitlement.Entitlement.entitlement.vend.addEntitlement(bank.id, user.userId, canCreateEntitlementAtOneBank.toString())
              if (!bankEntitlements.exists(_.roleName == canReadDynamicResourceDocsAtOneBank.toString()))
                code.entitlement.Entitlement.entitlement.vend.addEntitlement(bank.id, user.userId, canReadDynamicResourceDocsAtOneBank.toString())
            }
          } yield JSONFactory220.createBankJSON(success)
        }
    }

    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(createBank), "POST",
      "/banks",
      "Create Bank",
      s"""Create a new bank (Authenticated access).
         |${userAuthenticationMessage(true)}""",
      bankJSONV220, bankJSONV220,
      List(InvalidJsonFormat, AuthenticatedUserIsRequired, InsufficientAuthorisationToCreateBank, UnknownError),
      List(apiTagBank, apiTagOldStyle),
      Some(List(canCreateBank)),
      http4sPartialFunction = Some(createBank))

    // ─── createBranch ─────────────────────────────────────────────────────────

    val createBranch: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "banks" / _ / "branches" =>
        EndpointHelpers.withUserAndBankAndBodyCreated[BranchJsonV220, BranchJsonV220](req) { (user, bank, body, cc) =>
          for {
            _ <- code.util.Helper.booleanToFuture(
              s"$InvalidJsonValue BANK_ID has to be the same in the URL and Body", failCode = 400, cc = Some(cc)) {
              body.bank_id == bank.bankId.value
            }
            _ <- Future {
              NewStyle.function.hasAllEntitlements(bank.bankId.value, user.userId, canCreateBranch :: Nil, canCreateBranchAtAnyBank :: Nil, Some(cc))
            } map { unboxFullOrFail(_, Some(cc), s"$InsufficientAuthorisationToCreateBranch") }
            branch <- NewStyle.function.tryons(CouldNotTransformJsonToInternalModel + " Branch", 400, Some(cc)) {
              JSONFactory220.transformV220ToBranch(body).head
            }
            (success, _) <- NewStyle.function.createOrUpdateBranch(branch, Some(cc))
          } yield JSONFactory220.createBranchJson(success)
        }
    }

    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(createBranch), "POST",
      "/banks/BANK_ID/branches",
      "Create Branch",
      s"""Create Branch for the Bank.
         |
         |${userAuthenticationMessage(true)}""",
      branchJsonV220, branchJsonV220,
      List(AuthenticatedUserIsRequired, BankNotFound, InsufficientAuthorisationToCreateBranch, UnknownError),
      List(apiTagBranch, apiTagOpenData),
      Some(List(canCreateBranch, canCreateBranchAtAnyBank)),
      http4sPartialFunction = Some(createBranch))

    // ─── createAtm ────────────────────────────────────────────────────────────

    val createAtm: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "banks" / _ / "atms" =>
        EndpointHelpers.withUserAndBankAndBodyCreated[AtmJsonV220, AtmJsonV220](req) { (user, bank, body, cc) =>
          for {
            _ <- NewStyle.function.hasAtLeastOneEntitlement(createAtmEntitlementsRequiredText)(
              bank.bankId.value, user.userId, createAtmEntitlements, Some(cc))
            _ <- code.util.Helper.booleanToFuture(
              s"$InvalidJsonValue BANK_ID has to be the same in the URL and Body", 400, cc = Some(cc)) {
              body.bank_id == bank.bankId.value
            }
            atm <- NewStyle.function.tryons(CouldNotTransformJsonToInternalModel + " Atm", 400, Some(cc)) {
              JSONFactory220.transformToAtmFromV220(body).head
            }
            (createdAtm, _) <- NewStyle.function.createOrUpdateAtm(atm, Some(cc))
          } yield JSONFactory220.createAtmJson(createdAtm)
        }
    }

    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(createAtm), "POST",
      "/banks/BANK_ID/atms",
      "Create ATM",
      s"""Create ATM for the Bank.
          |
          |${userAuthenticationMessage(true)}""",
      atmJsonV220, atmJsonV220,
      List(AuthenticatedUserIsRequired, BankNotFound, UserHasMissingRoles, UnknownError),
      List(apiTagATM),
      Some(List(canCreateAtm, canCreateAtmAtAnyBank)),
      http4sPartialFunction = Some(createAtm))

    // ─── createProduct ────────────────────────────────────────────────────────

    val createProduct: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ PUT -> `prefixPath` / "banks" / _ / "products" =>
        EndpointHelpers.withUserAndBankAndBodyCreated[ProductJsonV220, ProductJsonV220](req) { (user, bank, body, cc) =>
          for {
            _ <- NewStyle.function.hasAtLeastOneEntitlement(createProductEntitlementsRequiredText)(
              bank.bankId.value, user.userId, createProductEntitlements, Some(cc))
            (success, _) <- NewStyle.function.createOrUpdateProduct(
              bankId           = bank.bankId.value,
              code             = body.code,
              parentProductCode = None,
              name             = body.name,
              category         = body.category,
              family           = body.family,
              superFamily      = body.super_family,
              moreInfoUrl      = body.more_info_url,
              termsAndConditionsUrl = null,
              details          = body.details,
              description      = body.description,
              metaLicenceId    = body.meta.license.id,
              metaLicenceName  = body.meta.license.name,
              callContext      = Some(cc)
            )
          } yield JSONFactory220.createProductJson(success)
        }
    }

    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(createProduct), "PUT",
      "/banks/BANK_ID/products",
      "Create Product",
      s"""Create or Update Product for the Bank.
          |
          |${userAuthenticationMessage(true)}""",
      productJsonV220, productJsonV220,
      List(AuthenticatedUserIsRequired, BankNotFound, UserHasMissingRoles, UnknownError),
      List(apiTagProduct),
      Some(List(canCreateProduct, canCreateProductAtAnyBank)),
      http4sPartialFunction = Some(createProduct))

    // ─── createFx ─────────────────────────────────────────────────────────────

    val createFxEntitlementsRequiredForSpecificBank = canCreateFxRate :: Nil
    val createFxEntitlementsRequiredForAnyBank      = canCreateFxRateAtAnyBank :: Nil

    val createFx: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ PUT -> `prefixPath` / "banks" / _ / "fx" =>
        EndpointHelpers.withUserAndBankAndBodyCreated[FXRateJsonV220, FXRateJsonV220](req) { (user, bank, body, cc) =>
          for {
            _ <- Future {
              NewStyle.function.hasAllEntitlements(
                bank.bankId.value, user.userId,
                createFxEntitlementsRequiredForSpecificBank,
                createFxEntitlementsRequiredForAnyBank,
                Some(cc))
            } map { unboxFullOrFail(_, Some(cc), UserHasMissingRoles + (createFxEntitlementsRequiredForSpecificBank ::: createFxEntitlementsRequiredForAnyBank).mkString(" or ")) }
            _ <- NewStyle.function.isValidCurrencyISOCode(body.from_currency_code, Some(cc))
            _ <- NewStyle.function.isValidCurrencyISOCode(body.to_currency_code, Some(cc))
            (fxRate, _) <- NewStyle.function.createOrUpdateFXRate(
              bankId                = body.bank_id,
              fromCurrencyCode      = body.from_currency_code,
              toCurrencyCode        = body.to_currency_code,
              conversionValue       = body.conversion_value,
              inverseConversionValue = body.inverse_conversion_value,
              effectiveDate         = body.effective_date,
              callContext           = Some(cc)
            )
          } yield JSONFactory220.createFXRateJSON(fxRate)
        }
    }

    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(createFx), "PUT",
      "/banks/BANK_ID/fx",
      "Create Fx",
      s"""Create or Update Fx for the Bank.
          |
          |${userAuthenticationMessage(true)}""",
      fxJsonV220, fxJsonV220,
      List(AuthenticatedUserIsRequired, BankNotFound, UserHasMissingRoles, UnknownError),
      List(apiTagFx),
      Some(List(canCreateFxRate, canCreateFxRateAtAnyBank)),
      http4sPartialFunction = Some(createFx))

    // ─── createAccount ────────────────────────────────────────────────────────

    val createAccount: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ PUT -> `prefixPath` / "banks" / _ / "accounts" / accountIdStr =>
        EndpointHelpers.withUserAndBankAndBody[CreateAccountJSONV220, CreateAccountJSONV220](req) { (user, bank, body, cc) =>
          for {
            _ <- code.util.Helper.booleanToFuture(InvalidAccountIdFormat, cc = Some(cc)) {
              isValidID(accountIdStr)
            }
            _ <- code.util.Helper.booleanToFuture(InvalidBankIdFormat, cc = Some(cc)) { isValidID(bank.bankId.value) }
            accountId = AccountId(accountIdStr)
            loggedInUserId = user.userId
            userIdAccountOwner = if (body.user_id.nonEmpty) body.user_id else loggedInUserId
            (postedOrLoggedInUser, _) <- NewStyle.function.findByUserId(userIdAccountOwner, Some(cc))
            _ <- if (userIdAccountOwner == loggedInUserId) Future.successful(Full(()))
                 else code.util.Helper.booleanToFuture(
                   s"$UserHasMissingRoles $canCreateAccount", failCode = 403, cc = Some(cc)) {
                   APIUtil.hasEntitlement(bank.bankId.value, loggedInUserId, canCreateAccount)
                 }
            _ <- code.util.Helper.booleanToFuture(InitialBalanceMustBeZero, cc = Some(cc)) {
              BigDecimal(body.balance.amount) == 0
            }
            _ <- code.util.Helper.booleanToFuture(InvalidISOCurrencyCode, cc = Some(cc)) {
              isValidCurrencyISOCode(body.balance.currency)
            }
            (bankAccount, _) <- NewStyle.function.createBankAccount(
              bank.bankId, accountId, body.`type`, body.label,
              body.balance.currency, BigDecimal(body.balance.amount),
              postedOrLoggedInUser.name, body.branch_id,
              List(AccountRouting(body.account_routing.scheme, body.account_routing.address)),
              Some(cc)
            )
            _ <- BankAccountCreation.setAccountHolderAndRefreshUserAccountAccess(bank.bankId, accountId, postedOrLoggedInUser, Some(cc))
          } yield JSONFactory220.createAccountJSON(userIdAccountOwner, bankAccount)
        }
    }

    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(createAccount), "PUT",
      "/banks/BANK_ID/accounts/NEW_ACCOUNT_ID",
      "Create Account",
      """Create Account at bank specified by BANK_ID with Id specified by ACCOUNT_ID.
        |
        |The User can create an Account for themself or an Account for another User if they have CanCreateAccount role.
        |
        |Note: The Amount must be zero.""",
      createAccountJSONV220, createAccountJSONV220,
      List(InvalidJsonFormat, BankNotFound, AuthenticatedUserIsRequired, InvalidUserId,
        InvalidAccountIdFormat, InvalidBankIdFormat, UserNotFoundById, UserHasMissingRoles,
        InvalidAccountBalanceAmount, InvalidAccountInitialBalance, InitialBalanceMustBeZero,
        InvalidAccountBalanceCurrency, AccountIdAlreadyExists, UnknownError),
      List(apiTagAccount, apiTagOnboarding),
      None,
      http4sPartialFunction = Some(createAccount))

    // ─── config ───────────────────────────────────────────────────────────────

    val config: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "config" =>
        EndpointHelpers.withUser(req) { (user, cc) =>
          for {
            _ <- NewStyle.function.hasEntitlement("", user.userId, canGetConfig, Some(cc))
          } yield JSONFactory220.getConfigInfoJSON()
        }
    }

    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(config), "GET",
      "/config",
      "Get API Configuration",
      """Returns information about API Config, Akka ports, Elastic search ports, Cached functions.""",
      EmptyBody, configurationJSON,
      List(AuthenticatedUserIsRequired, UserHasMissingRoles, UnknownError),
      apiTagApi :: Nil,
      Some(List(canGetConfig)),
      http4sPartialFunction = Some(config))

    // ─── getConnectorMetrics ──────────────────────────────────────────────────

    val getConnectorMetrics: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "management" / "connector" / "metrics" =>
        EndpointHelpers.withUser(req) { (user, cc) =>
          for {
            _ <- NewStyle.function.hasEntitlement("", user.userId, canGetConnectorMetrics, Some(cc))
            httpParams <- NewStyle.function.extractHttpParamsFromUrl(req.uri.renderString)
            (obpQueryParams, _) <- createQueriesByHttpParamsFuture(httpParams, Some(cc))
            metrics <- Future(ConnectorMetricsProvider.metrics.vend.getAllConnectorMetrics(obpQueryParams))
          } yield JSONFactory220.createConnectorMetricsJson(metrics)
        }
    }

    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(getConnectorMetrics), "GET",
      "/management/connector/metrics",
      "Get Connector Metrics",
      s"""Get all connector metrics. Requires CanGetConnectorMetrics role.""",
      EmptyBody, connectorMetricsJson,
      List(InvalidDateFormat, UnknownError),
      List(apiTagMetric, apiTagApi),
      Some(List(canGetConnectorMetrics)),
      http4sPartialFunction = Some(getConnectorMetrics))

    // ─── createConsumer ───────────────────────────────────────────────────────

    val createConsumer: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "management" / "consumers" =>
        EndpointHelpers.withUserAndBodyCreated[ConsumerPostJSON, ConsumerJson](req) { (user, body, cc) =>
          for {
            _ <- Future {
              unboxFullOrFail(
                NewStyle.function.ownEntitlement("", user.userId, canCreateConsumer, Some(cc)),
                Some(cc), UserHasMissingRoles + canCreateConsumer)
            }
            consumer <- Future {
              Consumers.consumers.vend.createConsumer(
                Some(generateUUID()), Some(generateUUID()),
                Some(body.enabled),
                Some(body.app_name), None,
                Some(body.description),
                Some(body.developer_email),
                Some(body.redirect_url),
                Some(user.userId),
                Some(body.clientCertificate),
                None, None
              ).getOrElse(throw new RuntimeException(UnknownError))
            }
          } yield JSONFactory220.createConsumerJSON(consumer)
        }
    }

    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(createConsumer), "POST",
      "/management/consumers",
      "Post a Consumer",
      s"""Create a Consumer (Authenticated access).
         |
         |${userAuthenticationMessage(true)}""",
      ConsumerPostJSON("Test", "Test", "Description", "some@email.com", "redirecturl", "createdby", true, new java.util.Date(),
        "-----BEGIN CERTIFICATE-----\nclient_certificate_content\n-----END CERTIFICATE-----"),
      ConsumerPostJSON("Some app name", "App type", "Description", "some.email@example.com", "Some redirect url",
        "Created by UUID", true, new java.util.Date(),
        "-----BEGIN CERTIFICATE-----\nclient_certificate_content\n-----END CERTIFICATE-----"),
      List(AuthenticatedUserIsRequired, UserHasMissingRoles, InvalidJsonFormat, UnknownError),
      List(apiTagConsumer, apiTagOldStyle),
      Some(List(canCreateConsumer)),
      http4sPartialFunction = Some(createConsumer))

    // ─── createCounterparty ───────────────────────────────────────────────────

    val createCounterparty: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "banks" / _ / "accounts" / _ / _ / "counterparties" =>
        implicit val cc: CallContext = req.callContext
        val io = for {
          user    <- IO.fromOption(cc.user.toOption)(new RuntimeException(AuthenticatedUserIsRequired))
          account <- IO.fromOption(cc.bankAccount)(new RuntimeException(AccountNotFound))
          view    <- IO.fromOption(cc.view)(new RuntimeException(ViewNotFound))
          body    <- IO.pure(cc.httpBody.getOrElse(""))
          result  <- code.api.util.http4s.RequestScopeConnection.fromFuture(
            createCounterpartyImpl(user, account, view, body, cc))
        } yield result
        io.attempt.flatMap {
          case Right(result) =>
            Created(prettyRender(Extraction.decompose(result)))
          case Left(err) =>
            code.api.util.http4s.ErrorResponseConverter.toHttp4sResponse(err, cc)
        }
    }

    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(createCounterparty), "POST",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/counterparties",
      "Create Counterparty (Explicit)",
      s"""Create Counterparty (Explicit) for an Account.
         |
         |${userAuthenticationMessage(true)}""",
      postCounterpartyJSON, counterpartyWithMetadataJson,
      List(AuthenticatedUserIsRequired, InvalidAccountIdFormat, InvalidBankIdFormat, BankNotFound,
        AccountNotFound, InvalidJsonFormat, ViewNotFound, CounterpartyAlreadyExists, UnknownError),
      List(apiTagCounterparty, apiTagAccount), None,
      http4sPartialFunction = Some(createCounterparty))

    private def createCounterpartyImpl(
      user: User, account: BankAccount, view: View, body: String, cc: CallContext
    ): Future[CounterpartyWithMetadataJson] = {
      for {
        _ <- code.util.Helper.booleanToFuture(InvalidAccountIdFormat, cc = Some(cc)) { isValidID(account.accountId.value) }
        _ <- code.util.Helper.booleanToFuture(InvalidBankIdFormat, cc = Some(cc)) { isValidID(account.bankId.value) }
        postJson <- NewStyle.function.tryons(
          s"$InvalidJsonFormat The Json body should be the $PostCounterpartyJSON", 400, Some(cc)) {
          net.liftweb.json.parse(body).extract[PostCounterpartyJSON]
        }
        _ <- code.util.Helper.booleanToFuture(
          s"${NoViewPermission} You need the `${CAN_ADD_COUNTERPARTY}` permission on the View(${view.viewId.value})",
          cc = Some(cc)) {
          ViewPermission.findViewPermissions(view).exists(_.permission.get == CAN_ADD_COUNTERPARTY)
        }
        (existingCp, _) <- Connector.connector.vend.checkCounterpartyExists(
          postJson.name, account.bankId.value, account.accountId.value, view.viewId.value, Some(cc))
        _ <- code.util.Helper.booleanToFuture(
          CounterpartyAlreadyExists.replace("value for BANK_ID or ACCOUNT_ID or VIEW_ID or NAME.",
            s"COUNTERPARTY_NAME(${postJson.name}) for the BANK_ID(${account.bankId.value}) and ACCOUNT_ID(${account.accountId.value}) and VIEW_ID(${view.viewId.value})"),
          cc = Some(cc)) { existingCp.isEmpty }
        _ <- code.util.Helper.booleanToFuture(
          s"$InvalidValueLength. The maximum length of `description` field is ${code.metadata.counterparties.MappedCounterparty.mDescription.maxLen}",
          cc = Some(cc)) { postJson.description.length <= 36 }
        (_, _) <- if (postJson.other_bank_routing_scheme.equalsIgnoreCase("OBP") && postJson.other_account_routing_scheme.equalsIgnoreCase("OBP"))
                    for {
                      (_, c) <- NewStyle.function.getBank(BankId(postJson.other_bank_routing_address), Some(cc))
                      r      <- NewStyle.function.checkBankAccountExists(BankId(postJson.other_bank_routing_address), AccountId(postJson.other_account_routing_address), c)
                    } yield r
                  else if (postJson.other_bank_routing_scheme.equalsIgnoreCase("OBP") && postJson.other_account_secondary_routing_scheme.equalsIgnoreCase("OBP"))
                    for {
                      (_, c) <- NewStyle.function.getBank(BankId(postJson.other_bank_routing_address), Some(cc))
                      r      <- NewStyle.function.checkBankAccountExists(BankId(postJson.other_bank_routing_address), AccountId(postJson.other_account_secondary_routing_address), c)
                    } yield r
                  else if (postJson.other_bank_routing_scheme.equalsIgnoreCase("ACCOUNT_NUMBER") || postJson.other_bank_routing_scheme.equalsIgnoreCase("ACCOUNT_NO"))
                    NewStyle.function.getBankAccountByNumber(
                      if (postJson.other_bank_routing_address.isEmpty) None else Some(BankId(postJson.other_bank_routing_address)),
                      postJson.other_bank_routing_address, Some(cc))
                  else Future.successful((Full(()), Some(cc)))
        otherAccountRoutingScheme = if (postJson.other_account_routing_scheme.equalsIgnoreCase("AccountNo"))
                                      "ACCOUNT_NUMBER"
                                    else StringHelpers.snakify(postJson.other_account_routing_scheme).toUpperCase
        (counterparty, _) <- NewStyle.function.createCounterparty(
          name                              = postJson.name,
          description                       = postJson.description,
          currency                          = "",
          createdByUserId                   = user.userId,
          thisBankId                        = account.bankId.value,
          thisAccountId                     = account.accountId.value,
          thisViewId                        = view.viewId.value,
          otherAccountRoutingScheme         = otherAccountRoutingScheme,
          otherAccountRoutingAddress        = postJson.other_account_routing_address,
          otherAccountSecondaryRoutingScheme = postJson.other_account_secondary_routing_scheme,
          otherAccountSecondaryRoutingAddress = postJson.other_account_secondary_routing_address,
          otherBankRoutingScheme            = postJson.other_bank_routing_scheme,
          otherBankRoutingAddress           = postJson.other_bank_routing_address,
          otherBranchRoutingScheme          = postJson.other_branch_routing_scheme,
          otherBranchRoutingAddress         = postJson.other_branch_routing_address,
          isBeneficiary                     = postJson.is_beneficiary,
          bespoke                           = postJson.bespoke.map(b => CounterpartyBespoke(b.key, b.value)),
          callContext                       = Some(cc)
        )
        (counterpartyMetadata, _) <- NewStyle.function.getOrCreateMetadata(
          account.bankId, account.accountId, counterparty.counterpartyId, postJson.name, Some(cc))
      } yield JSONFactory220.createCounterpartyWithMetadataJSON(counterparty, counterpartyMetadata)
    }

    // ─── allRoutes ────────────────────────────────────────────────────────────

    private val allOwnRoutes: HttpRoutes[IO] = Kleisli[HttpF, Request[IO], Response[IO]] { req =>
      root.run(req)
        .orElse(getViewsForBankAccount.run(req))
        .orElse(createViewForBankAccount.run(req))
        .orElse(updateViewForBankAccount.run(req))
        .orElse(getCurrentFxRate.run(req))
        .orElse(getExplicitCounterpartiesForAccount.run(req))
        .orElse(getExplicitCounterpartyById.run(req))
        .orElse(getMessageDocs.run(req))
        .orElse(createBank.run(req))
        .orElse(createBranch.run(req))
        .orElse(createAtm.run(req))
        .orElse(createProduct.run(req))
        .orElse(createFx.run(req))
        .orElse(createAccount.run(req))
        .orElse(config.run(req))
        .orElse(getConnectorMetrics.run(req))
        .orElse(createConsumer.run(req))
        .orElse(createCounterparty.run(req))
    }

    val allRoutesWithMiddleware: HttpRoutes[IO] = ResourceDocMiddleware.apply(resourceDocs)(allOwnRoutes)

    // ─── path-rewriting bridge: /obp/v2.2.0/… → /obp/v2.1.0/… ──────────────

    val v220ToV210Bridge: HttpRoutes[IO] = Kleisli[HttpF, Request[IO], Response[IO]] { req =>
      val rawPath = req.uri.path.renderString
      if (rawPath.startsWith("/obp/v2.2.0/")) {
        val rewritten    = rawPath.replaceFirst("/obp/v2\\.2\\.0/", "/obp/v2.1.0/")
        val newUri       = req.uri.withPath(Uri.Path.unsafeFromString(rewritten))
        val rewrittenReq = req.withUri(newUri)
        code.api.v2_1_0.Http4s210.wrappedRoutesV210Services.run(rewrittenReq)
      } else {
        OptionT.none[IO, Response[IO]]
      }
    }
  }

  val wrappedRoutesV220Services: HttpRoutes[IO] =
    Kleisli[HttpF, Request[IO], Response[IO]] { req =>
      Implementations2_2_0.allRoutesWithMiddleware.run(req)
        .orElse(Implementations2_2_0.v220ToV210Bridge.run(req))
    }
}
