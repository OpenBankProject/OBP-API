package code.api.v2_2_0

import org.json4s._
import cats.data.{Kleisli, OptionT}
import cats.effect._
import code.api.Constant._
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON._
import code.api.util.APIUtil.{EmptyBody, ResourceDoc, _}
import code.api.util.ApiRole._
import code.api.util.ApiTag._
import code.api.util.ErrorMessages._
import code.api.util.Glossary
import code.api.util.http4s.Http4sRequestAttributes.{EndpointHelpers, RequestOps}
import java.util.Date
import code.api.util.http4s.ResourceDocMiddleware
import code.api.util.http4s.IdempotencyMiddleware
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
import com.openbankproject.commons.util.JsonAliases.prettyRender
import org.json4s.{Extraction, Formats}
import org.json4s.native.Serialization
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
      implementedInApiVersion, nameOf(root), "GET", "/root",
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
      implementedInApiVersion, nameOf(getViewsForBankAccount), "GET",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/views",
      "Get Views for Account",
      s"""#Views
      |
      |
      |Views in Open Bank Project provide a mechanism for fine grained access control and delegation to Accounts and Transactions. Account holders use the 'owner' view by default. 
      |Delegated access is made through other views for example 'accountants', 'share-holders' or 'tagging-application'. Views can be created via the API and each view has a list of entitlements.
      |
      |Views on accounts and transactions filter the underlying data to redact certain fields for certain users. For instance the balance on an account may be hidden from the public. The way to know what is possible on a view is determined in the following JSON.
      |
      |**Data:** When a view moderates a set of data, some fields my contain the value `null` rather than the original value. This indicates either that the user is not allowed to see the original data or the field is empty.
      |
      |There is currently one exception to this rule; the 'holder' field in the JSON contains always a value which is either an alias or the real name - indicated by the 'is_alias' field.
      |
      |**Action:** When a user performs an action like trying to post a comment (with POST API call), if he is not allowed, the body response will contain an error message.
      |
      |**Metadata:**
      |Transaction metadata (like images, tags, comments, etc.) will appears *ONLY* on the view where they have been created e.g. comments posted to the public view only appear on the public view.
      |
      |The other account metadata fields (like image_URL, more_info, etc.) are unique through all the views. Example, if a user edits the 'more_info' field in the 'team' view, then the view 'authorities' will show the new value (if it is allowed to do it).
      |
      |# All
      |*Optional*
      |
      |Returns the list of the views created for account ACCOUNT_ID at BANK_ID.
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
      implementedInApiVersion, nameOf(createViewForBankAccount), "POST",
      "/banks/BANK_ID/accounts/VIEW_ACCOUNT_ID/views",
      "Create View",
      s"""#Create a view on bank account
      |
      | ${userAuthenticationMessage(true)} and the user needs to have access to the owner view.
      | The 'alias' field in the JSON can take one of three values:
      |
      | * _public_: to use the public alias if there is one specified for the other account.
      | * _private_: to use the private alias if there is one specified for the other account.
      |
      | * _''(empty string)_: to use no alias; the view shows the real name of the other account.
      |
      | The 'hide_metadata_if_alias_used' field in the JSON can take boolean values. If it is set to `true` and there is an alias on the other account then the other accounts' metadata (like more_info, url, image_url, open_corporates_url, etc.) will be hidden. Otherwise the metadata will be shown.
      |
      | The 'allowed_actions' field is a list containing the name of the actions allowed on this view, all the actions contained will be set to `true` on the view creation, the rest will be set to `false`.
      |
      | You should use a leading _ (underscore) for the view name because other view names may become reserved by OBP internally
      | """,
      createViewJsonV121, viewJSONV220,
      List(AuthenticatedUserIsRequired, InvalidJsonFormat, BankAccountNotFound, UnknownError),
      List(apiTagAccount, apiTagView, apiTagOldStyle), None,
      http4sPartialFunction = Some(createViewForBankAccount))

    private def createViewImpl(user: User, account: BankAccount, body: String, cc: CallContext): Future[ViewJSONV220] = {
      for {
        createBodyJson <- NewStyle.function.tryons(InvalidJsonFormat, 400, Some(cc)) {
          com.openbankproject.commons.util.JsonAliases.parse(body).extract[CreateViewJsonV121]
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
      implementedInApiVersion, nameOf(updateViewForBankAccount), "PUT",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/views/UPD_VIEW_ID",
      "Update View",
      s"""Update an existing view on a bank account
      |
      |${userAuthenticationMessage(true)} and the user needs to have access to the owner view.
      |
      |The json sent is the same as during view creation (above), with one difference: the 'name' field
      |of a view is not editable (it is only set when a view is created)""",
      updateViewJsonV121, viewJSONV220,
      List(InvalidJsonFormat, AuthenticatedUserIsRequired, BankAccountNotFound, UnknownError),
      List(apiTagAccount, apiTagView, apiTagOldStyle), None,
      http4sPartialFunction = Some(updateViewForBankAccount))

    private def updateViewImpl(user: User, account: BankAccount, viewId: ViewId, body: String, cc: CallContext): Future[ViewJSONV220] = {
      for {
        updateBodyJson <- NewStyle.function.tryons(InvalidJsonFormat, 400, Some(cc)) {
          com.openbankproject.commons.util.JsonAliases.parse(body).extract[UpdateViewJsonV121]
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

    // TODO: Add a v7.0.0 of Get Current FxRate with a richer, provenance-aware response.
    // The current (v2.2.0) response returns only conversion_value / inverse_conversion_value /
    // effective_date, which cannot express how the rate was sourced or how much to trust it.
    // Proposed improvements for the v7.0.0 response body:
    //   - status / quality: "indicative" | "executable" | "fallback" — so consumers know whether
    //     the rate is tradeable or for reference only (especially important when the endpoint is
    //     public via apiOptions.getCurrentFxRateIsPublic).
    //   - source / provenance: which tier produced the rate — "connector" | "fallback_file" |
    //     "hardcoded_map" — so a stale fallback is never mistaken for the bank's live rate.
    //   - bid / ask / mid + spread (instead of a single conversion_value), so indicative mid can
    //     be distinguished from executable quotes, and spread can be withheld when public.
    //   - retrieved_at (when OBP fetched it) and explicit age/staleness, alongside effective_date.
    //   - precision/scale of the currency pair (ISO 4217 minor units) for correct rounding.
    //   - optional `amount` query param to return a converted amount with documented rounding.
    //   - first-class crypto asset support (e.g. lovelace, ETH) — already hinted at in the
    //     InvalidISOCurrencyCode error message.
    //   - a disclaimer field ("indicative only") + cache TTL hint when served publicly.
    //   - consider a batch variant accepting multiple currency pairs in one request.
    // Keep v2.2.0 in place for backward compatibility; v7.0.0 should be additive.
    resourceDocs += ResourceDoc(
      implementedInApiVersion, nameOf(getCurrentFxRate), "GET",
      "/banks/BANK_ID/fx/FROM_CURRENCY_CODE/TO_CURRENCY_CODE",
      "Get Current FxRate",
      """Get the latest FX rate specified by BANK_ID, FROM_CURRENCY_CODE and TO_CURRENCY_CODE
        |
        |OBP may try different sources of FX rate information depending on the Connector in operation.
        |
        |For example we want to convert EUR => USD:
        |
        |OBP will:
        |1st try - Connector (database, core banking system or external FX service)
        |2nd try part 1 - fallbackexchangerates/eur.json
        |2nd try part 2 - fallbackexchangerates/usd.json (the inverse rate is used)
        |3rd try - Hardcoded map of FX rates.
        |
        |![FX Flow](https://user-images.githubusercontent.com/485218/60005085-1eded600-966e-11e9-96fb-798b102d9ad0.png)
        |
        |**Public Access:** This endpoint can be made publicly accessible (no authentication required) by setting the property `apiOptions.getCurrentFxRateIsPublic=true` in the props file.
        |
      """.stripMargin,
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
      implementedInApiVersion, nameOf(getExplicitCounterpartiesForAccount), "GET",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/counterparties",
      "Get Counterparties (Explicit)",
      s"""This endpoints gets the explicit Counterparties on an Account / View.
      |
      |For a general introduction to Counterparties in OBP, see ${Glossary.getGlossaryItemLink("Counterparties")}
      |
      |${userAuthenticationMessage(true)}
      |""".stripMargin,
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
      implementedInApiVersion, nameOf(getExplicitCounterpartyById), "GET",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/counterparties/COUNTERPARTY_ID",
      "Get Counterparty by Counterparty Id (Explicit)",
      s"""Information returned about the Counterparty specified by COUNTERPARTY_ID:
      |
      |${userAuthenticationMessage(true)}
      |""".stripMargin,
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
      implementedInApiVersion, nameOf(getMessageDocs), "GET",
      "/message-docs/CONNECTOR",
      "Get Message Docs",
      """These message docs provide example messages sent by OBP to the (RabbitMq) message queue for processing by the Core Banking / Payment system Adapter - together with an example expected response and possible error codes.
        | Integrators can use these messages to build Adapters that provide core banking services to OBP.
        |
        | Note: API Explorer provides a Message Docs page where these messages are displayed.
        | 
        | `CONNECTOR`: rest_vMar2019, stored_procedure_vDec2019 ...
      """.stripMargin,
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
            // Creator grants target the HUMAN (see v6.0.0 createBank): under a Consent the
            // authenticated user is a per-consent shadow, and roles granted to it are stranded.
            humanUserId = cc.accountableUserId
            entitlements <- Future {
              unboxFullOrFail(
                code.entitlement.Entitlement.entitlement.vend.getEntitlementsByUserId(humanUserId),
                Some(cc), UnknownError)
            }
            _ <- Future {
              val bankEntitlements = entitlements.filter(_.bankId == bank.id)
              if (!bankEntitlements.exists(_.roleName == canCreateEntitlementAtOneBank.toString()))
                code.entitlement.Entitlement.entitlement.vend.addEntitlement(bank.id, humanUserId, canCreateEntitlementAtOneBank.toString(), grantedByUserId = Some(user.userId))
              if (!bankEntitlements.exists(_.roleName == canReadDynamicResourceDocsAtOneBank.toString()))
                code.entitlement.Entitlement.entitlement.vend.addEntitlement(bank.id, humanUserId, canReadDynamicResourceDocsAtOneBank.toString(), grantedByUserId = Some(user.userId))
            }
          } yield JSONFactory220.createBankJSON(success)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion, nameOf(createBank), "POST",
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
      implementedInApiVersion, nameOf(createBranch), "POST",
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
      implementedInApiVersion, nameOf(createAtm), "POST",
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
      implementedInApiVersion, nameOf(createProduct), "PUT",
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
      implementedInApiVersion, nameOf(createFx), "PUT",
      "/banks/BANK_ID/fx",
      "Create Fx",
      s"""Create or Update Fx for the Bank.
       |
       |Example:
       |
       |“from_currency_code”:“EUR”,
       |“to_currency_code”:“USD”,
       |“conversion_value”: 1.136305,
       |“inverse_conversion_value”: 1 / 1.136305 = 0.8800454103431737,
       |
       | Thus 1 Euro = 1.136305 US Dollar
       | and
       | 1 US Dollar = 0.8800 Euro
       |
       |
      |${userAuthenticationMessage(true) }
       |
       |""",
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
      implementedInApiVersion, nameOf(createAccount), "PUT",
      "/banks/BANK_ID/accounts/NEW_ACCOUNT_ID",
      "Create Account",
      """Create Account at bank specified by BANK_ID with Id specified by ACCOUNT_ID.
      |
      |
      |The User can create an Account for themself or an Account for another User if they have CanCreateAccount role.
      |
      |If USER_ID is not specified the account will be owned by the logged in User.
      |
      |The type field should be a product_code from Product.
      |
      |Note: The Amount must be zero.""".stripMargin,
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
      implementedInApiVersion, nameOf(config), "GET",
      "/config",
      "Get API Configuration",
      """Returns information about:
      |
      |* API Config
      |* Akka ports
      |* Elastic search ports
      |* Cached function """,
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
      implementedInApiVersion, nameOf(getConnectorMetrics), "GET",
      "/management/connector/metrics",
      "Get Connector Metrics",
      s"""Get the all metrics
        |
        |require CanGetConnectorMetrics role
        |
        |Filters Part 1.*filtering* (no wilde cards etc.) parameters to GET /management/connector/metrics
        |
        |Should be able to filter on the following metrics fields
        |
        |eg: /management/connector/metrics?from_date=$DateWithMsExampleString&to_date=$DateWithMsExampleString&limit=50&offset=2
        |
        |1 from_date (defaults to one week before current date): eg:from_date=$DateWithMsExampleString
        |
        |2 to_date (defaults to current date) eg:to_date=$DateWithMsExampleString
        |
        |3 limit (for pagination: defaults to 1000)  eg:limit=2000
        |
        |4 offset (for pagination: zero index, defaults to 0) eg: offset=10
        |
        |eg: /management/connector/metrics?from_date=$DateWithMsExampleString&to_date=$DateWithMsExampleString&limit=100&offset=300
        |
        |Other filters:
        |
        |5 connector_name  (if null ignore)
        |
        |6 function_name (if null ignore)
        |
        |7 correlation_id (if null ignore)
        |
      """.stripMargin,
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
      implementedInApiVersion, nameOf(createConsumer), "POST",
      "/management/consumers",
      "Post a Consumer",
      s"""Create a Consumer (Authenticated access).
       |
      |""",
      ConsumerPostJSON(
        "Test",
        "Test",
        "Description",
        "some@email.com",
        "redirecturl",
        "createdby",
        true,
        new Date(),
        """-----BEGIN CERTIFICATE-----
          |client_certificate_content
          |-----END CERTIFICATE-----""".stripMargin
      ),
      ConsumerPostJSON(
        "Some app name",
        "App type",
        "Description",
        "some.email@example.com",
        "Some redirect url",
        "Created by UUID",
        true,
        new Date(),
        """-----BEGIN CERTIFICATE-----
          |client_certificate_content
          |-----END CERTIFICATE-----""".stripMargin
      ),
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
      implementedInApiVersion, nameOf(createCounterparty), "POST",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/counterparties",
      "Create Counterparty (Explicit)",
      s"""Create Counterparty (Explicit) for an Account.
      |
      |In OBP, there are two types of Counterparty.
      |
      |* Explicit Counterparties (those here) which we create explicitly and are used in COUNTERPARTY Transaction Requests
      |
      |* Implicit Counterparties (AKA Other Accounts) which are generated automatically from the other sides of Transactions.
      |
       |Explicit Counterparties are created for the account / view
      |They are how the user of the view (e.g. account owner) refers to the other side of the transaction
      |
      |name : the human readable name (e.g. Piano teacher, Miss Nipa)
      |
      |description : the human readable name (e.g. Piano teacher, Miss Nipa)
      |
      |bank_routing_scheme : eg: bankId or bankCode or any other strings
      |
      |bank_routing_address : eg: `gh.29.uk`, must be valid sandbox bankIds
      |
      |account_routing_scheme : eg: AccountId or AccountNumber or any other strings
      |
      |account_routing_address : eg: `1d65db7c-a7b2-4839-af41-95`, must be valid accountIds
      |
      |other_account_secondary_routing_scheme : eg: IBan or any other strings
      |
      |other_account_secondary_routing_address : if it is an IBAN, it should be unique for each counterparty. 
      |
      |other_branch_routing_scheme : eg: branchId or any other strings or you can leave it empty, not useful in sandbox mode.
      |
      |other_branch_routing_address : eg: `branch-id-123` or you can leave it empty, not useful in sandbox mode.
      |
      |is_beneficiary : must be set to `true` in order to send payments to this counterparty
      |
      |bespoke: It supports a list of key-value, you can add it to the counterparty.
      |
      |bespoke.key : any info-key you want to add to this counterparty
      | 
      |bespoke.value : any info-value you want to add to this counterparty
      |
      |The view specified by VIEW_ID must have the canAddCounterparty permission
      |
      |A minimal example for TransactionRequestType == COUNTERPARTY
      | {
      |  "name": "Tesobe1",
      |  "description": "Good Company",
      |  "other_bank_routing_scheme": "OBP",
      |  "other_bank_routing_address": "gh.29.uk",
      |  "other_account_routing_scheme": "OBP",
      |  "other_account_routing_address": "8ca8a7e4-6d02-48e3-a029-0b2bf89de9f0",
      |  "is_beneficiary": true,
      |  "other_account_secondary_routing_scheme": "",
      |  "other_account_secondary_routing_address": "",
      |  "other_branch_routing_scheme": "",
      |  "other_branch_routing_address": "",
      |  "bespoke": []
      |}
      |
      | 
      |A minimal example for TransactionRequestType == SEPA
      | 
      | {
      |  "name": "Tesobe2",
      |  "description": "Good Company",
      |  "other_bank_routing_scheme": "OBP",
      |  "other_bank_routing_address": "gh.29.uk",
      |  "other_account_routing_scheme": "OBP",
      |  "other_account_routing_address": "8ca8a7e4-6d02-48e3-a029-0b2bf89de9f0",
      |  "other_account_secondary_routing_scheme": "IBAN",
      |  "other_account_secondary_routing_address": "DE89 3704 0044 0532 0130 00",
      |  "is_beneficiary": true,
      |  "other_branch_routing_scheme": "",
      |  "other_branch_routing_address": "",
      |  "bespoke": []
      |}
      |
      |${userAuthenticationMessage(true)}
      |
      |""".stripMargin,
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
          com.openbankproject.commons.util.JsonAliases.parse(body).extract[PostCounterpartyJSON]
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

    val allRoutesWithMiddleware: HttpRoutes[IO] = ResourceDocMiddleware.apply(resourceDocs)(IdempotencyMiddleware(allOwnRoutes))

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
