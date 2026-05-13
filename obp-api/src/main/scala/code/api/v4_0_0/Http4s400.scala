package code.api.v4_0_0

import cats.data.{Kleisli, OptionT}
import cats.effect._
import code.api.Constant._
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON._
import code.api.util.APIUtil.{EmptyBody, ResourceDoc, _}
import code.api.util.ApiRole._
import code.api.util.ApiTag._
import code.api.util.ErrorMessages._
import code.api.util.ExampleValue._
import code.api.util.Glossary
import code.api.Constant
import code.api.dynamic.endpoint.helper.DynamicEndpointHelper
import code.api.dynamic.entity.helper.DynamicEntityInfo
import code.api.util.{ApiRole => ApiRoleObj}
import code.api.util.newstyle.ViewNewStyle
import code.users.Users
import code.views.Views
import code.api.v1_4_0.JSONFactory1_4_0
import code.DynamicEndpoint.DynamicEndpointSwagger
import code.api.util.http4s.Http4sRequestAttributes.{EndpointHelpers, RequestOps}
import code.api.util.http4s.ResourceDocMiddleware
import code.api.util.{APIUtil, CallContext, CustomJsonFormats, NewStyle}
import code.api.v4_0_0.JSONFactory400._
import code.DynamicData.DynamicData
import code.api.util.migration.Migration
import code.dynamicEntity.DynamicEntityCommons
import code.bankconnectors.Connector
import code.entitlement.Entitlement
import code.model.BankX
import code.model._   // implicit BankAccountExtended → moderatedBankAccount
import code.model.dataAccess.AuthUser
import code.ratelimiting.RateLimitingDI
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.model._
import com.openbankproject.commons.model.enums.DynamicEntityOperation.GET_ALL
import com.openbankproject.commons.model.enums.ProductAttributeType
import com.openbankproject.commons.util.{ApiVersion, ApiVersionStatus, ScannedApiVersion}
import net.liftweb.common.{Box, Failure, Full}
import net.liftweb.json.Formats
import net.liftweb.json.JsonAST.{JArray, JObject, JValue}
import net.liftweb.json.JsonDSL._
import net.liftweb.json.{compactRender, parse}
import org.apache.commons.lang3.StringUtils
import org.http4s._
import org.http4s.dsl.io._

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Future

object Http4s400 {
  val implementedInApiVersion: ScannedApiVersion = ApiVersion.v4_0_0
  val versionStatus: String                      = ApiVersionStatus.STABLE.toString
  // v4.0.0 splits doc registration into a static buffer plus a few entries that are dynamic
  // at construction time (createDynamicEntityDoc et al). The public `resourceDocs` accessor
  // (used by the middleware) is the union. For now only `staticResourceDocs` is populated;
  // dynamic doc entries are added by the management endpoints when they're migrated.
  val staticResourceDocs: ArrayBuffer[ResourceDoc] = ArrayBuffer[ResourceDoc]()
  val resourceDocs: ArrayBuffer[ResourceDoc]       = staticResourceDocs

  implicit val formats: Formats = CustomJsonFormats.formats

  type HttpF[A] = OptionT[IO, A]

  object Implementations4_0_0 {
    val prefixPath: Path = Root / ApiPathZero.toString / implementedInApiVersion.toString

    // ─── getMapperDatabaseInfo ────────────────────────────────────────────────

    val getMapperDatabaseInfo: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "database" / "info" =>
        EndpointHelpers.withUser(req) { (user, cc) =>
          for {
            _ <- NewStyle.function.hasEntitlement("", user.userId, canGetDatabaseInfo, Some(cc))
          } yield Migration.DbFunction.mapperDatabaseInfo
        }
    }

    staticResourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(getMapperDatabaseInfo), "GET",
      "/database/info",
      "Get Mapper Database Info",
      s"""Get basic information about the Mapper Database.
         |
         |${userAuthenticationMessage(true)}""",
      EmptyBody, adapterInfoJsonV300,
      List(AuthenticatedUserIsRequired, UnknownError),
      List(apiTagApi), Some(List(canGetDatabaseInfo)),
      http4sPartialFunction = Some(getMapperDatabaseInfo))

    // ─── getLogoutLink ────────────────────────────────────────────────────────

    val getLogoutLink: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "users" / "current" / "logout-link" =>
        EndpointHelpers.withUser(req) { (_, _) =>
          Future {
            val link = code.api.Constant.HostName + AuthUser.logoutPath.foldLeft("")(_ + "/" + _)
            LogoutLinkJson(link)
          }
        }
    }

    staticResourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(getLogoutLink), "GET",
      "/users/current/logout-link",
      "Get Logout Link",
      s"""Get the Logout Link
         |
         |${userAuthenticationMessage(true)}""",
      EmptyBody, logoutLinkV400,
      List(AuthenticatedUserIsRequired, UnknownError),
      List(apiTagUser), None,
      http4sPartialFunction = Some(getLogoutLink))

    // ─── getBanks ─────────────────────────────────────────────────────────────
    // v4.0.0 overrides v3.x getBanks — v4 uses createBanksJson which adds attributes.

    val getBanks: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" =>
        EndpointHelpers.executeAndRespond(req) { cc =>
          for {
            (banks, _) <- NewStyle.function.getBanks(Some(cc))
          } yield JSONFactory400.createBanksJson(banks)
        }
    }

    staticResourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(getBanks), "GET",
      "/banks",
      "Get Banks",
      """Get banks on this API instance
        |Returns a list of banks supported on this server.""".stripMargin,
      EmptyBody, banksJSON400,
      List(UnknownError),
      apiTagBank :: apiTagPSD2AIS :: apiTagPsd2 :: Nil, None,
      http4sPartialFunction = Some(getBanks))

    // ─── getBank ──────────────────────────────────────────────────────────────
    // v4.0.0 overrides v3.x getBank — v4 includes bank attributes.

    val getBank: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / _ =>
        EndpointHelpers.withBank(req) { (bank, cc) =>
          for {
            (attributes, _) <- NewStyle.function.getBankAttributesByBank(bank.bankId, Some(cc))
          } yield JSONFactory400.createBankJSON400(bank, attributes)
        }
    }

    staticResourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(getBank), "GET",
      "/banks/BANK_ID",
      "Get Bank",
      """Get the bank specified by BANK_ID.""".stripMargin,
      EmptyBody, bankJson400,
      List(UnknownError, BankNotFound),
      apiTagBank :: apiTagPSD2AIS :: apiTagPsd2 :: Nil, None,
      http4sPartialFunction = Some(getBank))

    // ─── ibanChecker (POST → 200) ─────────────────────────────────────────────

    val ibanChecker: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "account" / "check" / "scheme" / "iban" =>
        EndpointHelpers.executeFutureWithBody[IbanAddress, Any](req) { (ibanJson, cc) =>
          for {
            (ibanCheckerResult, _) <- NewStyle.function.validateAndCheckIbanNumber(ibanJson.address, Some(cc))
          } yield JSONFactory400.createIbanCheckerJson(ibanCheckerResult)
        }
    }

    staticResourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(ibanChecker), "POST",
      "/account/check/scheme/iban",
      "Validate and check IBAN",
      """Validate and check IBAN for errors""",
      ibanCheckerPostJsonV400, ibanCheckerJsonV400,
      List(UnknownError),
      apiTagAccount :: Nil, None,
      http4sPartialFunction = Some(ibanChecker))

    // ─── callsLimit (PUT → 200) ───────────────────────────────────────────────
    // v4.0.0 overrides v3.1.0 — v4 takes additional api_version / api_name / bank_id fields
    // in the request body for finer-grained rate limiting.

    val callsLimit: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ PUT -> `prefixPath` / "management" / "consumers" / consumerIdStr / "consumer" / "call-limits" =>
        EndpointHelpers.withUserAndBody[CallLimitPostJsonV400, Any](req) { (user, postJson, cc) =>
          for {
            _ <- NewStyle.function.handleEntitlementsAndScopes("", user.userId, List(canUpdateRateLimits), Some(cc))
            _ <- NewStyle.function.getConsumerByConsumerId(consumerIdStr, Some(cc))
            rateLimiting <- RateLimitingDI.rateLimiting.vend.createOrUpdateConsumerCallLimits(
              consumerIdStr,
              postJson.from_date, postJson.to_date,
              postJson.api_version, postJson.api_name, postJson.bank_id,
              Some(postJson.per_second_call_limit),
              Some(postJson.per_minute_call_limit),
              Some(postJson.per_hour_call_limit),
              Some(postJson.per_day_call_limit),
              Some(postJson.per_week_call_limit),
              Some(postJson.per_month_call_limit)) map {
              unboxFullOrFail(_, Some(cc), UpdateConsumerError)
            }
          } yield createCallsLimitJson(rateLimiting)
        }
    }

    staticResourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(callsLimit), "PUT",
      "/management/consumers/CONSUMER_ID/consumer/call-limits",
      "Set Rate Limits / Call Limits per Consumer",
      s"""Set the API rate limits / call limits for a Consumer.
         |
         |${userAuthenticationMessage(true)}""",
      callLimitPostJsonV400, callLimitPostJsonV400,
      List(AuthenticatedUserIsRequired, InvalidJsonFormat, InvalidConsumerId,
        ConsumerNotFoundByConsumerId, UserHasMissingRoles, UpdateConsumerError, UnknownError),
      List(apiTagConsumer, apiTagRateLimits),
      Some(List(canUpdateRateLimits)),
      http4sPartialFunction = Some(callsLimit))

    // ─── createBank (POST → 201) ──────────────────────────────────────────────
    // v4 overrides v2.2.0's createBank — v4 grants CanCreateEntitlementAtOneBank +
    // CanReadDynamicResourceDocsAtOneBank to the creator after the bank is created.
    // Must live in Http4s400's own routes so the bridge cascade can't hijack POST /banks
    // down to Http4s220 (which has its own v2.2.0 createBank — different behavior).

    val createBank: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "banks" =>
        EndpointHelpers.executeFutureCreated(req) {
          implicit val cc: CallContext = req.callContext
          for {
            bank <- NewStyle.function.tryons(
              s"$InvalidJsonFormat The Json body should be the $BankJson400 ", 400, Some(cc)) {
              net.liftweb.json.parse(cc.httpBody.getOrElse("")).extract[BankJson400]
            }
            _ <- code.util.Helper.booleanToFuture(
              failMsg = InvalidConsumerCredentials, cc = Some(cc)) {
              cc.consumer.isDefined
            }
            shortStringCheck = APIUtil.checkShortString(bank.id)
            _ <- code.util.Helper.booleanToFuture(
              failMsg = s"$shortStringCheck.", cc = Some(cc)) {
              shortStringCheck == code.util.Helper.SILENCE_IS_GOLDEN
            }
            _ <- code.util.Helper.booleanToFuture(
              failMsg = s"$InvalidJsonFormat Min length of BANK_ID should be greater than 3 characters.",
              cc = Some(cc)) { bank.id.length > 3 }
            _ <- code.util.Helper.booleanToFuture(
              failMsg = s"$InvalidJsonFormat BANK_ID can not contain space characters",
              cc = Some(cc)) { !bank.id.contains(" ") }
            _ <- code.util.Helper.booleanToFuture(
              failMsg = s"$InvalidJsonFormat BANK_ID can not contain `::::` characters",
              cc = Some(cc)) { !APIUtil.`checkIfContains::::`(bank.id) }
            (success, _) <- NewStyle.function.createOrUpdateBank(
              bank.id, bank.full_name, bank.short_name, bank.logo, bank.website,
              bank.bank_routings.find(_.scheme == "BIC").map(_.address).getOrElse(""),
              "",
              bank.bank_routings.filterNot(_.scheme == "BIC").headOption.map(_.scheme).getOrElse(""),
              bank.bank_routings.filterNot(_.scheme == "BIC").headOption.map(_.address).getOrElse(""),
              Some(cc))
            entitlements <- NewStyle.function.getEntitlementsByUserId(cc.userId, Some(cc))
            entitlementsByBank = entitlements.filter(_.bankId == bank.id)
            _ <- entitlementsByBank.exists(_.roleName == CanCreateEntitlementAtOneBank.toString()) match {
              case true  => Future.successful(())
              case false => Future(Entitlement.entitlement.vend.addEntitlement(
                bank.id, cc.userId, CanCreateEntitlementAtOneBank.toString()))
            }
            _ <- entitlementsByBank.exists(_.roleName == CanReadDynamicResourceDocsAtOneBank.toString()) match {
              case true  => Future.successful(())
              case false => Future(Entitlement.entitlement.vend.addEntitlement(
                bank.id, cc.userId, CanReadDynamicResourceDocsAtOneBank.toString()))
            }
          } yield JSONFactory400.createBankJSON400(success)
        }
    }

    staticResourceDocs += ResourceDoc(
      null, implementedInApiVersion, "createBank", "POST",
      "/banks",
      "Create Bank",
      s"""Create a new bank (Authenticated access).
         |
         |The user creating this will be automatically assigned the Role CanCreateEntitlementAtOneBank.""",
      postBankJson400, bankJson400,
      List(InvalidJsonFormat, AuthenticatedUserIsRequired,
        InsufficientAuthorisationToCreateBank, UnknownError),
      List(apiTagBank),
      Some(List(canCreateBank)),
      http4sPartialFunction = Some(createBank))

    // ─── root (GET) — v4 override of v3.1.0's /root ──────────────────────────

    val root: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` =>
        EndpointHelpers.executeAndRespond(req) { _ =>
          Future.successful(JSONFactory400.getApiInfoJSON(
            ApiVersion.v4_0_0, versionStatus))
        }
      case req @ GET -> `prefixPath` / "root" =>
        EndpointHelpers.executeAndRespond(req) { _ =>
          Future.successful(JSONFactory400.getApiInfoJSON(
            ApiVersion.v4_0_0, versionStatus))
        }
    }

    staticResourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(root), "GET", "/root",
      "Get API Info (root)",
      """Returns information about:
        |
        |* API version
        |* Hosted by information
        |* Git Commit""",
      EmptyBody, apiInfoJson400,
      List(UnknownError, MandatoryPropertyIsNotSet), apiTagApi :: Nil, None,
      http4sPartialFunction = Some(root))

    // ─── getAtms (GET) — v4 override; conditional auth via getAtmsIsPublic ───

    val getAtms: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / _ / "atms" =>
        EndpointHelpers.withBank(req) { (bank, cc) =>
          val limit = req.uri.query.params.get("limit").map(Full(_)).getOrElse(net.liftweb.common.Empty)
          val offset = req.uri.query.params.get("offset").map(Full(_)).getOrElse(net.liftweb.common.Empty)
          for {
            _ <- code.util.Helper.booleanToFuture(
              failMsg = s"$InvalidNumber limit:${limit.getOrElse("")}", cc = Some(cc)) {
              limit match {
                case Full(i) => i.forall(_.isDigit)
                case _       => true
              }
            }
            _ <- code.util.Helper.booleanToFuture(failMsg = maximumLimitExceeded, cc = Some(cc)) {
              limit match {
                case Full(i) if i.toInt > 10000 => false
                case _                          => true
              }
            }
            (atms, _) <- NewStyle.function.getAtmsByBankId(bank.bankId, offset, limit, Some(cc))
          } yield JSONFactory400.createAtmsJsonV400(atms)
        }
    }

    staticResourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(getAtms), "GET",
      "/banks/BANK_ID/atms",
      "Get Bank ATMS",
      s"""Returns information about ATMs for a single bank specified by BANK_ID.
         |
         |${userAuthenticationMessage(!getAtmsIsPublic)}""".stripMargin,
      EmptyBody, atmsJsonV400,
      List(AuthenticatedUserIsRequired, BankNotFound, InvalidJsonFormat, UnknownError),
      List(apiTagATM), None,
      http4sPartialFunction = Some(getAtms))

    // ─── getAtm (GET) — v4 override; conditional auth ────────────────────────

    val getAtm: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / _ / "atms" / atmIdStr =>
        EndpointHelpers.withBank(req) { (bank, cc) =>
          for {
            (atm, _) <- NewStyle.function.getAtm(bank.bankId, AtmId(atmIdStr), Some(cc))
          } yield JSONFactory400.createAtmJsonV400(atm)
        }
    }

    staticResourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(getAtm), "GET",
      "/banks/BANK_ID/atms/ATM_ID",
      "Get Bank ATM",
      s"""Returns information about ATM for a single bank specified by BANK_ID and ATM_ID.
         |
         |${userAuthenticationMessage(!getAtmsIsPublic)}""".stripMargin,
      EmptyBody, atmJsonV400,
      List(AuthenticatedUserIsRequired, InvalidJsonFormat, UnknownError),
      List(apiTagATM), None,
      http4sPartialFunction = Some(getAtm))

    // ─── getProducts (GET) — v4 override; conditional auth ───────────────────

    val getProducts: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / bankIdStr / "products" =>
        EndpointHelpers.executeAndRespond(req) { cc =>
          val params = req.uri.query.multiParams.toList.flatMap {
            case (k, vs) => vs.map(v => com.openbankproject.commons.dto.GetProductsParam(k, List(v)))
          }
          for {
            (_, _) <- NewStyle.function.getBank(BankId(bankIdStr), Some(cc))
            (products, _) <- NewStyle.function.getProducts(BankId(bankIdStr), params, Some(cc))
          } yield JSONFactory400.createProductsJson(products)
        }
    }

    staticResourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(getProducts), "GET",
      "/banks/BANK_ID/products",
      "Get Products",
      s"""Returns information about the financial products offered by a bank specified by BANK_ID.
         |
         |${userAuthenticationMessage(!getProductsIsPublic)}""".stripMargin,
      EmptyBody, productsJsonV400,
      List(AuthenticatedUserIsRequired, BankNotFound, ProductNotFoundByProductCode, UnknownError),
      List(apiTagProduct), None,
      http4sPartialFunction = Some(getProducts))

    // ─── getProduct (GET) — v4 override; loads attributes + fees ─────────────

    val getProduct: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / bankIdStr / "products" / productCodeStr =>
        EndpointHelpers.executeAndRespond(req) { cc =>
          for {
            (product, _) <- NewStyle.function.getProduct(BankId(bankIdStr), ProductCode(productCodeStr), Some(cc))
            (productAttributes, _) <- NewStyle.function.getProductAttributesByBankAndCode(
              BankId(bankIdStr), ProductCode(productCodeStr), Some(cc))
            (productFees, _) <- NewStyle.function.getProductFeesFromProvider(
              BankId(bankIdStr), ProductCode(productCodeStr), Some(cc))
          } yield JSONFactory400.createProductJson(product, productAttributes, productFees)
        }
    }

    staticResourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(getProduct), "GET",
      "/banks/BANK_ID/products/PRODUCT_CODE",
      "Get Bank Product",
      s"""Returns information about a financial Product offered by the bank specified by BANK_ID and PRODUCT_CODE.
         |
         |${userAuthenticationMessage(!getProductsIsPublic)}""".stripMargin,
      EmptyBody, productJsonV400,
      List(AuthenticatedUserIsRequired, BankNotFound, ProductNotFoundByProductCode, UnknownError),
      List(apiTagProduct), None,
      http4sPartialFunction = Some(getProduct))

    // ─── createAtm (POST → 201) — v4 override ─────────────────────────────────

    val createAtm: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "banks" / _ / "atms" =>
        EndpointHelpers.executeFutureCreated(req) {
          implicit val cc: CallContext = req.callContext
          val bank = cc.bank.getOrElse(throw new RuntimeException(BankNotFound))
          val rawBody = cc.httpBody.getOrElse("")
          for {
            atmJsonV400 <- NewStyle.function.tryons(
              s"$InvalidJsonFormat The Json body should be the ${classOf[AtmJsonV400]}",
              400, Some(cc)) {
              val atm = net.liftweb.json.parse(rawBody).extract[AtmJsonV400]
              atm.id.get
              atm
            }
            _ <- code.util.Helper.booleanToFuture(
              s"$InvalidJsonValue BANK_ID has to be the same in the URL and Body",
              failCode = 400, cc = Some(cc)) {
              atmJsonV400.bank_id == bank.bankId.value
            }
            atm <- NewStyle.function.tryons(
              CouldNotTransformJsonToInternalModel + " Atm", 400, Some(cc)) {
              JSONFactory400.transformToAtmFromV400(atmJsonV400)
            }
            (created, _) <- NewStyle.function.createOrUpdateAtm(atm, Some(cc))
          } yield JSONFactory400.createAtmJsonV400(created)
        }
    }

    staticResourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(createAtm), "POST",
      "/banks/BANK_ID/atms",
      "Create ATM",
      s"""Create ATM.""",
      atmJsonV400, atmJsonV400,
      List(AuthenticatedUserIsRequired, InvalidJsonFormat, UnknownError),
      List(apiTagATM),
      Some(List(canCreateAtm, canCreateAtmAtAnyBank)),
      http4sPartialFunction = Some(createAtm))

    // ─── createProduct (PUT → 201) — v4 override ──────────────────────────────

    val createProduct: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ PUT -> `prefixPath` / "banks" / bankIdStr / "products" / productCodeStr =>
        EndpointHelpers.executeFutureCreated(req) {
          implicit val cc: CallContext = req.callContext
          val user = cc.user.openOrThrowException(AuthenticatedUserIsRequired)
          val rawBody = cc.httpBody.getOrElse("")
          for {
            _ <- NewStyle.function.hasAtLeastOneEntitlement(failMsg = createProductEntitlementsRequiredText)(
              bankIdStr, user.userId, createProductEntitlements, Some(cc))
            product <- NewStyle.function.tryons(
              s"$InvalidJsonFormat The Json body should be the $PutProductJsonV400 ",
              400, Some(cc)) {
              net.liftweb.json.parse(rawBody).extract[PutProductJsonV400]
            }
            (parentProduct, _) <- product.parent_product_code.trim.nonEmpty match {
              case false => Future((net.liftweb.common.Empty, Some(cc)))
              case true =>
                NewStyle.function.getProduct(
                  BankId(bankIdStr), ProductCode(product.parent_product_code), Some(cc))
                  .map { case (p, ccc) => (Full(p), ccc) }
            }
            (success, _) <- NewStyle.function.createOrUpdateProduct(
              bankId = bankIdStr,
              code = productCodeStr,
              parentProductCode = parentProduct.map(_.code.value).toOption,
              name = product.name,
              category = null, family = null, superFamily = null,
              moreInfoUrl = product.more_info_url,
              termsAndConditionsUrl = product.terms_and_conditions_url,
              details = null,
              description = product.description,
              metaLicenceId = product.meta.license.id,
              metaLicenceName = product.meta.license.name,
              Some(cc))
          } yield JSONFactory400.createProductJson(success)
        }
    }

    staticResourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(createProduct), "PUT",
      "/banks/BANK_ID/products/PRODUCT_CODE",
      "Create Product",
      s"""Create or Update Product for the Bank.
         |
         |${userAuthenticationMessage(true)}""",
      putProductJsonV400, productJsonV400.copy(attributes = None, fees = None),
      List(AuthenticatedUserIsRequired, BankNotFound, UserHasMissingRoles, UnknownError),
      List(apiTagProduct),
      Some(List(canCreateProduct, canCreateProductAtAnyBank)),
      http4sPartialFunction = Some(createProduct))

    // ─── createProductAttribute (POST → 201) — v4 override ────────────────────

    val createProductAttribute: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "banks" / bankIdStr / "products" / productCodeStr / "attribute" =>
        EndpointHelpers.withUserAndBodyCreated[ProductAttributeJsonV400, Any](req) { (user, postedData, cc) =>
          for {
            _ <- NewStyle.function.hasEntitlement(bankIdStr, user.userId, canCreateProductAttribute, Some(cc))
            (_, _) <- NewStyle.function.getBank(BankId(bankIdStr), Some(cc))
            productAttributeType <- NewStyle.function.tryons(
              s"$InvalidJsonFormat The `Type` field can only accept the following field: " +
                s"${ProductAttributeType.DOUBLE}(12.1234), ${ProductAttributeType.STRING}(TAX_NUMBER), ${ProductAttributeType.INTEGER}(123) and ${ProductAttributeType.DATE_WITH_DAY}(2012-04-23)",
              400, Some(cc)) { ProductAttributeType.withName(postedData.`type`) }
            (_, _) <- NewStyle.function.getProduct(BankId(bankIdStr), ProductCode(productCodeStr), Some(cc))
            (productAttribute, _) <- NewStyle.function.createOrUpdateProductAttribute(
              BankId(bankIdStr), ProductCode(productCodeStr), None,
              postedData.name, productAttributeType, postedData.value, postedData.is_active, Some(cc))
          } yield createProductAttributeJson(productAttribute)
        }
    }

    staticResourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(createProductAttribute), "POST",
      "/banks/BANK_ID/products/PRODUCT_CODE/attribute",
      "Create Product Attribute",
      s"""Create a Product Attribute.
         |
         |${userAuthenticationMessage(true)}""",
      productAttributeJsonV400, productAttributeResponseJsonV400,
      List(InvalidJsonFormat, UnknownError),
      List(apiTagProduct, apiTagProductAttribute, apiTagAttribute),
      Some(List(canCreateProductAttribute)),
      http4sPartialFunction = Some(createProductAttribute))

    // ─── updateProductAttribute (PUT → 200) — v4 override ─────────────────────

    val updateProductAttribute: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ PUT -> `prefixPath` / "banks" / bankIdStr / "products" / productCodeStr / "attributes" / productAttributeIdStr =>
        EndpointHelpers.withUserAndBody[ProductAttributeJsonV400, Any](req) { (user, postedData, cc) =>
          for {
            _ <- NewStyle.function.hasEntitlement(bankIdStr, user.userId, canUpdateProductAttribute, Some(cc))
            (_, _) <- NewStyle.function.getBank(BankId(bankIdStr), Some(cc))
            productAttributeType <- NewStyle.function.tryons(
              s"$InvalidJsonFormat The `Type` field can only accept the following field: " +
                s"${ProductAttributeType.DOUBLE}(12.1234), ${ProductAttributeType.STRING}(TAX_NUMBER), ${ProductAttributeType.INTEGER}(123) and ${ProductAttributeType.DATE_WITH_DAY}(2012-04-23)",
              400, Some(cc)) { ProductAttributeType.withName(postedData.`type`) }
            (_, _) <- NewStyle.function.getProductAttributeById(productAttributeIdStr, Some(cc))
            (productAttribute, _) <- NewStyle.function.createOrUpdateProductAttribute(
              BankId(bankIdStr), ProductCode(productCodeStr), Some(productAttributeIdStr),
              postedData.name, productAttributeType, postedData.value, postedData.is_active, Some(cc))
          } yield createProductAttributeJson(productAttribute)
        }
    }

    staticResourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(updateProductAttribute), "PUT",
      "/banks/BANK_ID/products/PRODUCT_CODE/attributes/PRODUCT_ATTRIBUTE_ID",
      "Update Product Attribute",
      s"""Update one Product Attribute by its id.
         |
         |${userAuthenticationMessage(true)}""",
      productAttributeJsonV400, productAttributeResponseJsonV400,
      List(UserHasMissingRoles, UnknownError),
      List(apiTagProduct, apiTagProductAttribute, apiTagAttribute),
      Some(List(canUpdateProductAttribute)),
      http4sPartialFunction = Some(updateProductAttribute))

    // ─── getEntitlements (GET /users/USER_ID/entitlements) — v4 override ────

    val getEntitlements: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "users" / userIdStr / "entitlements" =>
        EndpointHelpers.withUser(req) { (_, cc) =>
          for {
            entitlements <- NewStyle.function.getEntitlementsByUserId(userIdStr, Some(cc))
          } yield {
            if (APIUtil.isSuperAdmin(userIdStr)) {
              code.api.v2_0_0.JSONFactory200.withVirtualEntitlements(
                entitlements, code.api.v2_0_0.JSONFactory200.superAdminVirtualRoles)
            } else if (APIUtil.isOidcOperator(userIdStr)) {
              code.api.v2_0_0.JSONFactory200.withVirtualEntitlements(
                entitlements, code.api.v2_0_0.JSONFactory200.oidcOperatorVirtualRoles)
            } else {
              code.api.v2_0_0.JSONFactory200.createEntitlementJSONs(entitlements)
            }
          }
        }
    }

    staticResourceDocs += ResourceDoc(
      null, implementedInApiVersion, "getEntitlements", "GET",
      "/users/USER_ID/entitlements",
      "Get Entitlements for User",
      "",
      EmptyBody, entitlementsJsonV400,
      List(AuthenticatedUserIsRequired, UserHasMissingRoles, UnknownError),
      List(apiTagRole, apiTagEntitlement, apiTagUser),
      Some(List(canGetEntitlementsForAnyUserAtAnyBank)),
      http4sPartialFunction = Some(getEntitlements))

    // ─── getUserByUserId (GET /users/user_id/USER_ID) — v4 override ─────────

    val getUserByUserId: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "users" / "user_id" / userIdStr =>
        EndpointHelpers.withUser(req) { (_, cc) =>
          for {
            user <- Users.users.vend.getUserByUserIdFuture(userIdStr) map { x =>
              unboxFullOrFail(x, Some(cc), s"$UserNotFoundByUserId Current UserId($userIdStr)")
            }
            entitlements <- NewStyle.function.getEntitlementsByUserId(user.userId, Some(cc))
            acceptMarketingInfo <- NewStyle.function.getAgreementByUserId(user.userId, "accept_marketing_info", Some(cc))
            termsAndConditions <- NewStyle.function.getAgreementByUserId(user.userId, "terms_and_conditions", Some(cc))
            privacyConditions <- NewStyle.function.getAgreementByUserId(user.userId, "privacy_conditions", Some(cc))
            isLocked = code.loginattempts.LoginAttempt.userIsLocked(user.provider, user.name)
          } yield {
            val agreements = acceptMarketingInfo.toList ::: termsAndConditions.toList ::: privacyConditions.toList
            JSONFactory400.createUserInfoJSON(user, entitlements, Some(agreements), isLocked)
          }
        }
    }

    staticResourceDocs += ResourceDoc(
      null, implementedInApiVersion, "getUserByUserId", "GET",
      "/users/user_id/USER_ID",
      "Get User by USER_ID",
      s"""Get user by USER_ID
         |
         |${userAuthenticationMessage(true)}
         |
         |CanGetAnyUser entitlement is required,""",
      EmptyBody, userJsonV400,
      List(AuthenticatedUserIsRequired, UserHasMissingRoles, UserNotFoundByUserId, UnknownError),
      List(apiTagUser),
      Some(List(canGetAnyUser)),
      http4sPartialFunction = Some(getUserByUserId))

    // ─── getUserByUsername (GET /users/username/USERNAME) — v4 override ─────

    val getUserByUsername: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "users" / "username" / username =>
        EndpointHelpers.withUser(req) { (_, cc) =>
          for {
            user <- Users.users.vend.getUserByProviderAndUsernameFuture(
              Constant.localIdentityProvider, username) map { x =>
              unboxFullOrFail(x, Some(cc), UserNotFoundByProviderAndUsername, 404)
            }
            entitlements <- NewStyle.function.getEntitlementsByUserId(user.userId, Some(cc))
            isLocked = code.loginattempts.LoginAttempt.userIsLocked(user.provider, user.name)
          } yield JSONFactory400.createUserInfoJSON(user, entitlements, None, isLocked)
        }
    }

    staticResourceDocs += ResourceDoc(
      null, implementedInApiVersion, "getUserByUsername", "GET",
      "/users/username/USERNAME",
      "Get User by USERNAME",
      s"""Get user by USERNAME
         |
         |${userAuthenticationMessage(true)}
         |
         |CanGetAnyUser entitlement is required,""",
      EmptyBody, userJsonV400,
      List(AuthenticatedUserIsRequired, UserHasMissingRoles,
        UserNotFoundByProviderAndUsername, UnknownError),
      List(apiTagUser),
      Some(List(canGetAnyUser)),
      http4sPartialFunction = Some(getUserByUsername))

    // ─── getUsersByEmail (GET /users/email/EMAIL/terminator) — v4 override ──

    val getUsersByEmail: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "users" / "email" / email / "terminator" =>
        EndpointHelpers.withUser(req) { (_, _) =>
          for {
            users <- Users.users.vend.getUsersByEmail(email)
          } yield JSONFactory400.createUsersJson(users)
        }
    }

    staticResourceDocs += ResourceDoc(
      null, implementedInApiVersion, "getUsersByEmail", "GET",
      "/users/email/EMAIL/terminator",
      "Get Users by Email Address",
      s"""Get users by email address
         |
         |${userAuthenticationMessage(true)}
         |CanGetAnyUser entitlement is required,""",
      EmptyBody, usersJsonV400,
      List(AuthenticatedUserIsRequired, UserHasMissingRoles, UserNotFoundByEmail, UnknownError),
      List(apiTagUser),
      Some(List(canGetAnyUser)),
      http4sPartialFunction = Some(getUsersByEmail))

    // ─── getUsers (GET /users) — v4 override ─────────────────────────────────

    val getUsers: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "users" =>
        EndpointHelpers.withUser(req) { (_, cc) =>
          val httpParams = req.headers.headers.toList.map(h =>
            net.liftweb.http.provider.HTTPParam(h.name.toString, h.value)) :::
            req.uri.query.multiParams.toList.flatMap { case (k, vs) =>
              vs.map(v => net.liftweb.http.provider.HTTPParam(k, v))
            }
          for {
            (obpQueryParams, _) <- createQueriesByHttpParamsFuture(httpParams, Some(cc))
            users <- Users.users.vend.getUsers(obpQueryParams)
          } yield JSONFactory400.createUsersJson(users)
        }
    }

    staticResourceDocs += ResourceDoc(
      null, implementedInApiVersion, "getUsers", "GET",
      "/users",
      "Get all Users",
      s"""Get all users
         |
         |${userAuthenticationMessage(true)}
         |
         |CanGetAnyUser entitlement is required,""",
      EmptyBody, usersJsonV400,
      List(AuthenticatedUserIsRequired, UserHasMissingRoles, UnknownError),
      List(apiTagUser),
      Some(List(canGetAnyUser)),
      http4sPartialFunction = Some(getUsers))

    // ─── getCustomersByAttributes (GET /banks/BANK_ID/customers) — v4 override

    val getCustomersByAttributes: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / _ / "customers" =>
        EndpointHelpers.withUserAndBank(req) { (_, bank, cc) =>
          val params = req.uri.query.multiParams.map { case (k, vs) => k -> vs.toList }
          for {
            (customerIds, _) <- NewStyle.function.getCustomerIdsByAttributeNameValues(
              bank.bankId, params, Some(cc))
            list <- Future.sequence(customerIds.map { customerId =>
              val customerFuture = NewStyle.function.getCustomerByCustomerId(customerId.value, Some(cc))
              customerFuture.flatMap { case (customer, ccc) =>
                NewStyle.function.getCustomerAttributes(bank.bankId, customerId, ccc)
                  .map { case (attributes, _) =>
                    code.api.v3_1_0.JSONFactory310.createCustomerWithAttributesJson(customer, attributes)
                  }
              }
            })
          } yield ListResult("customers", list)
        }
    }

    staticResourceDocs += ResourceDoc(
      null, implementedInApiVersion, "getCustomersByAttributes", "GET",
      "/banks/BANK_ID/customers",
      "Get Customers by ATTRIBUTES",
      "Gets the Customers specified by attributes",
      EmptyBody,
      ListResult("customers", List(customerWithAttributesJsonV310)),
      List(AuthenticatedUserIsRequired, BankNotFound, UserCustomerLinksNotFoundForUser, UnknownError),
      List(apiTagCustomer),
      Some(List(canGetCustomersAtOneBank)),
      http4sPartialFunction = Some(getCustomersByAttributes))

    // ─── createCustomer (POST /banks/BANK_ID/customers → 201) — v4 override ──

    val createCustomer: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "banks" / _ / "customers" =>
        EndpointHelpers.withUserAndBankAndBodyCreated[code.api.v3_1_0.PostCustomerJsonV310, Any](req) { (_, bank, postedData, cc) =>
          for {
            _ <- code.util.Helper.booleanToFuture(
              failMsg = InvalidJsonContent + s" The field dependants(${postedData.dependants}) not equal the length(${postedData.dob_of_dependants.length}) of dob_of_dependants array",
              failCode = 400, cc = Some(cc)) {
              postedData.dependants == postedData.dob_of_dependants.length
            }
            (customer, _) <- NewStyle.function.createCustomer(
              bank.bankId,
              postedData.legal_name, postedData.mobile_phone_number, postedData.email,
              CustomerFaceImage(postedData.face_image.date, postedData.face_image.url),
              postedData.date_of_birth, postedData.relationship_status,
              postedData.dependants, postedData.dob_of_dependants,
              postedData.highest_education_attained, postedData.employment_status,
              postedData.kyc_status, postedData.last_ok_date,
              Option(CreditRating(postedData.credit_rating.rating, postedData.credit_rating.source)),
              Option(CreditLimit(postedData.credit_limit.currency, postedData.credit_limit.amount)),
              postedData.title, postedData.branch_id, postedData.name_suffix,
              Some(cc))
          } yield code.api.v3_1_0.JSONFactory310.createCustomerJson(customer)
        }
    }

    staticResourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(createCustomer), "POST",
      "/banks/BANK_ID/customers",
      "Create Customer",
      s"""The Customer resource stores the customer number (set by backend), legal name, email, phone number, date of birth, etc.
         |
         |${userAuthenticationMessage(true)}""",
      code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON.postCustomerJsonV310,
      code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON.customerJsonV310,
      List(AuthenticatedUserIsRequired, BankNotFound, InvalidJsonFormat,
        CustomerNumberAlreadyExists, UserNotFoundById, CustomerAlreadyExistsForUser,
        CreateCustomerError, UnknownError),
      List(apiTagCustomer, apiTagPerson),
      Some(List(canCreateCustomer, canCreateCustomerAtAnyBank)),
      http4sPartialFunction = Some(createCustomer))

    // ─── getBankAccountsBalancesForCurrentUser (GET /banks/BANK_ID/balances) — v4

    val getBankAccountsBalancesForCurrentUser: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / _ / "balances" =>
        EndpointHelpers.withUserAndBank(req) { (user, bank, cc) =>
          for {
            (allowedAccounts, _) <- code.api.util.newstyle.BalanceNewStyle.getAccountAccessAtBank(user, bank.bankId, Some(cc))
            (accountsBalances, _) <- code.api.util.newstyle.BalanceNewStyle.getBankAccountsBalances(allowedAccounts, Some(cc))
          } yield createBalancesJson(accountsBalances)
        }
    }

    staticResourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(getBankAccountsBalancesForCurrentUser), "GET",
      "/banks/BANK_ID/balances",
      "Get Accounts Balances",
      "Get the Balances for the Accounts of the current User at one bank.",
      EmptyBody, accountBalancesV400Json,
      List(AuthenticatedUserIsRequired, BankNotFound, UnknownError),
      apiTagAccount :: apiTagPSD2AIS :: apiTagPsd2 :: Nil, None,
      http4sPartialFunction = Some(getBankAccountsBalancesForCurrentUser))

    // ─── getCoreAccountById (GET /my/banks/BANK_ID/accounts/ACCOUNT_ID/account)

    val getCoreAccountById: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "my" / "banks" / bankIdStr / "accounts" / accountIdStr / "account" =>
        EndpointHelpers.withUser(req) { (user, cc) =>
          for {
            (_, _) <- NewStyle.function.getBank(BankId(bankIdStr), Some(cc))
            (account, _) <- NewStyle.function.checkBankAccountExists(BankId(bankIdStr), AccountId(accountIdStr), Some(cc))
            view <- ViewNewStyle.checkOwnerViewAccessAndReturnOwnerView(user,
              BankIdAccountId(account.bankId, account.accountId), Some(cc))
            moderatedAccount <- NewStyle.function.moderatedBankAccountCore(account, view, Full(user), Some(cc))
          } yield {
            val availableViews: List[View] =
              Views.views.vend.privateViewsUserCanAccessForAccount(user,
                BankIdAccountId(account.bankId, account.accountId))
            createNewCoreBankAccountJson(moderatedAccount, availableViews)
          }
        }
    }

    staticResourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(getCoreAccountById), "GET",
      "/my/banks/BANK_ID/accounts/ACCOUNT_ID/account",
      "Get Account by Id (Core)",
      s"""Information returned about the account specified by ACCOUNT_ID.
         |
         |${userAuthenticationMessage(true)}""",
      EmptyBody, moderatedCoreAccountJsonV400,
      List(AuthenticatedUserIsRequired, BankAccountNotFound, UnknownError),
      apiTagAccount :: apiTagPSD2AIS :: apiTagPsd2 :: Nil, None,
      http4sPartialFunction = Some(getCoreAccountById))

    // ─── getPrivateAccountByIdFull (GET /banks/BANK_ID/.../VIEW_ID/account) ──

    val getPrivateAccountByIdFull: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / bankIdStr / "accounts" / _ / _ / "account" =>
        EndpointHelpers.withView(req) { (user, account, view, cc) =>
          for {
            moderatedAccount <- NewStyle.function.moderatedBankAccountCore(account, view, Full(user), Some(cc))
            (accountAttributes, _) <- NewStyle.function.getAccountAttributesByAccount(
              account.bankId, account.accountId, Some(cc))
          } yield {
            val availableViews = Views.views.vend.privateViewsUserCanAccessForAccount(
              user, BankIdAccountId(account.bankId, account.accountId))
            val viewsAvailable = availableViews.map(code.api.v1_2_1.JSONFactory.createViewJSON).sortBy(_.short_name)
            val tags = code.metadata.tags.Tags.tags.vend.getTagsOnAccount(
              account.bankId, account.accountId)(view.viewId)
            createBankAccountJSON(moderatedAccount, viewsAvailable, accountAttributes, tags)
          }
        }
    }

    staticResourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(getPrivateAccountByIdFull), "GET",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/account",
      "Get Account by Id (Full)",
      """Information returned about an account specified by ACCOUNT_ID moderated by the view (VIEW_ID).""",
      EmptyBody, moderatedAccountJSON400,
      List(AuthenticatedUserIsRequired, BankNotFound, BankAccountNotFound,
        UserNoPermissionAccessView, UnknownError),
      apiTagAccount :: Nil, None,
      http4sPartialFunction = Some(getPrivateAccountByIdFull))

    // ─── getPrivateAccountsAtOneBank (GET /banks/BANK_ID/accounts) — v4 override

    val getPrivateAccountsAtOneBank: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / _ / "accounts" =>
        EndpointHelpers.withUserAndBank(req) { (user, bank, cc) =>
          val params: Map[String, String] = req.uri.query.params
            .filterNot(_._1 == code.api.Constant.PARAM_TIMESTAMP)
            .filterNot(_._1 == code.api.Constant.PARAM_LOCALE)
          val viewsAndAccess: (List[View], List[code.views.system.AccountAccess]) =
            Views.views.vend.privateViewsUserCanAccessAtBank(user, bank.bankId)
          val privateViewsUserCanAccessAtOneBank: List[View] = viewsAndAccess._1
          val privateAccountAccess: List[code.views.system.AccountAccess] = viewsAndAccess._2
          for {
            privateAccountAccess2 <-
              if (params.isEmpty || privateAccountAccess.isEmpty)
                Future.successful(privateAccountAccess)
              else
                code.accountattribute.AccountAttributeX.accountAttributeProvider.vend
                  .getAccountIdsByParams(bank.bankId, params.map { case (k, v) => k -> List(v) })
                  .map { boxedAccountIds =>
                    val accountIds = boxedAccountIds.getOrElse(Nil)
                    privateAccountAccess.filter(aa => accountIds.contains(aa.account_id.get))
                  }
            (availablePrivateAccounts, _) <- code.model.BankExtended(bank).privateAccountsFuture(
              privateAccountAccess2, Some(cc))
          } yield code.api.v2_0_0.OBPAPI2_0_0.Implementations2_0_0.processAccounts(
            privateViewsUserCanAccessAtOneBank, availablePrivateAccounts)
        }
    }

    staticResourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(getPrivateAccountsAtOneBank), "GET",
      "/banks/BANK_ID/accounts",
      "Get Accounts at Bank",
      s"""Returns the list of accounts at BANK_ID that the user has access to.""",
      EmptyBody, basicAccountsJSON,
      List(AuthenticatedUserIsRequired, BankNotFound, UnknownError),
      List(apiTagAccount, apiTagPrivateData, apiTagPublicData), None,
      http4sPartialFunction = Some(getPrivateAccountsAtOneBank))

    // ─── createUserCustomerLinks (POST → 201) — v4 override ─────────────────

    val createUserCustomerLinks: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "banks" / bankIdStr / "user_customer_links" =>
        EndpointHelpers.withUserAndBankAndBodyCreated[code.api.v2_0_0.CreateUserCustomerLinkJson, Any](req) { (_, bank, postedData, cc) =>
          for {
            _ <- NewStyle.function.tryons(InvalidBankIdFormat, 400, Some(cc)) {
              assert(isValidID(bank.bankId.value))
            }
            _ <- Users.users.vend.getUserByUserIdFuture(postedData.user_id) map { x =>
              unboxFullOrFail(x, Some(cc), UserNotFoundByUserId, 404)
            }
            _ <- code.util.Helper.booleanToFuture(
              "Field customer_id is not defined in the posted json!",
              failCode = 400, cc = Some(cc)) {
              postedData.customer_id.nonEmpty
            }
            (customer, _) <- NewStyle.function.getCustomerByCustomerId(postedData.customer_id, Some(cc))
            _ <- code.util.Helper.booleanToFuture(
              s"Bank of the customer specified by the CUSTOMER_ID(${customer.bankId}) has to matches BANK_ID(${bank.bankId.value}) in URL",
              failCode = 400, cc = Some(cc)) {
              customer.bankId == bank.bankId.value
            }
            _ <- code.util.Helper.booleanToFuture(CustomerAlreadyExistsForUser, failCode = 400, cc = Some(cc)) {
              code.usercustomerlinks.UserCustomerLink.userCustomerLink.vend
                .getUserCustomerLink(postedData.user_id, postedData.customer_id).isEmpty
            }
            userCustomerLink <- Future {
              code.usercustomerlinks.UserCustomerLink.userCustomerLink.vend.createUserCustomerLink(
                postedData.user_id, postedData.customer_id, new java.util.Date(), true)
            } map { x => unboxFullOrFail(x, Some(cc), CreateUserCustomerLinksError, 400) }
          } yield code.api.v2_0_0.JSONFactory200.createUserCustomerLinkJSON(userCustomerLink)
        }
    }

    staticResourceDocs += ResourceDoc(
      null, implementedInApiVersion, "createUserCustomerLinks", "POST",
      "/banks/BANK_ID/user_customer_links",
      "Create User Customer Link",
      s"""Link a User to a Customer
         |
         |${userAuthenticationMessage(true)}""",
      createUserCustomerLinkJson, userCustomerLinkJson,
      List(AuthenticatedUserIsRequired, InvalidBankIdFormat, BankNotFound, InvalidJsonFormat,
        CustomerNotFoundByCustomerId, UserHasMissingRoles, CustomerAlreadyExistsForUser,
        CreateUserCustomerLinksError, UnknownError),
      List(apiTagCustomer, apiTagUser),
      Some(List(canCreateUserCustomerLinkAtAnyBank, canCreateUserCustomerLink)),
      http4sPartialFunction = Some(createUserCustomerLinks))

    // ─── getSystemDynamicEntities ─────────────────────────────────────────────

    val getSystemDynamicEntities: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "management" / "system-dynamic-entities" =>
        EndpointHelpers.withUser(req) { (user, cc) =>
          for {
            _ <- NewStyle.function.hasEntitlement("", user.userId, canGetSystemLevelDynamicEntities, Some(cc))
            dynamicEntities <- Future(NewStyle.function.getDynamicEntities(None, false))
          } yield {
            val listCommons: List[DynamicEntityCommons] = dynamicEntities
            ListResult("dynamic_entities", listCommons.map(_.jValue))
          }
        }
    }

    staticResourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(getSystemDynamicEntities), "GET",
      "/management/system-dynamic-entities",
      "Get System Dynamic Entities",
      s"""Get all System Dynamic Entities.
         |
         |For more information see ${Glossary.getGlossaryItemLink("Dynamic-Entities")}""",
      EmptyBody,
      ListResult("dynamic_entities", List(dynamicEntityResponseBodyExample)),
      List(AuthenticatedUserIsRequired, UserHasMissingRoles, UnknownError),
      List(apiTagManageDynamicEntity, apiTagApi),
      Some(List(canGetSystemLevelDynamicEntities)),
      http4sPartialFunction = Some(getSystemDynamicEntities))

    // ─── getBankLevelDynamicEntities ──────────────────────────────────────────

    val getBankLevelDynamicEntities: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "management" / "banks" / _ / "dynamic-entities" =>
        EndpointHelpers.withUserAndBank(req) { (user, bank, cc) =>
          for {
            _ <- NewStyle.function.hasAtLeastOneEntitlement(bank.bankId.value, user.userId,
              List(canGetBankLevelDynamicEntities, canGetAnyBankLevelDynamicEntities), Some(cc))
            dynamicEntities <- Future(NewStyle.function.getDynamicEntities(Some(bank.bankId.value), false))
          } yield {
            val listCommons: List[DynamicEntityCommons] = dynamicEntities
            ListResult("dynamic_entities", listCommons.map(_.jValue))
          }
        }
    }

    staticResourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(getBankLevelDynamicEntities), "GET",
      "/management/banks/BANK_ID/dynamic-entities",
      "Get Bank Level Dynamic Entities",
      s"""Get all the bank level Dynamic Entities for one bank.
         |
         |For more information see ${Glossary.getGlossaryItemLink("Dynamic-Entities")}""",
      EmptyBody,
      ListResult("dynamic_entities", List(dynamicEntityResponseBodyExample)),
      List(BankNotFound, AuthenticatedUserIsRequired, UserHasMissingRoles, UnknownError),
      List(apiTagManageDynamicEntity, apiTagApi),
      Some(List(canGetBankLevelDynamicEntities, canGetAnyBankLevelDynamicEntities)),
      http4sPartialFunction = Some(getBankLevelDynamicEntities))

    // ─── getMyDynamicEntities ─────────────────────────────────────────────────

    val getMyDynamicEntities: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "my" / "dynamic-entities" =>
        EndpointHelpers.withUser(req) { (user, cc) =>
          for {
            dynamicEntities <- Future(NewStyle.function.getDynamicEntitiesByUserId(user.userId))
          } yield {
            val listCommons: List[DynamicEntityCommons] = dynamicEntities
            ListResult("dynamic_entities", listCommons.map(_.jValue))
          }
        }
    }

    staticResourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(getMyDynamicEntities), "GET",
      "/my/dynamic-entities",
      "Get My Dynamic Entities",
      s"""Get all the Dynamic Entities created by the current user.
         |
         |For more information see ${Glossary.getGlossaryItemLink("Dynamic-Entities")}""",
      EmptyBody,
      ListResult("dynamic_entities", List(dynamicEntityResponseBodyExample)),
      List(AuthenticatedUserIsRequired, UnknownError),
      List(apiTagManageDynamicEntity, apiTagApi), None,
      http4sPartialFunction = Some(getMyDynamicEntities))

    // ─── dynamic-entity shared helpers (ported from APIMethods400) ──────────

    /**
     * Convert IllegalArgumentException from validation (e.g. DynamicEntityCommons.apply
     * shape checks) into a JSON-encoded APIFailureNewStyle exception. ErrorResponseConverter
     * picks this up and emits an HTTP response with the exact failMsg verbatim.
     *
     * Why not `NewStyle.function.tryons`: tryons builds a Lift Failure chain and produces
     * messages like ". Details: <orig>" or " <- . Details: <orig>", which doesn't match
     * the original error string the v4.0.0 tests assert on.
     */
    private def tryOrApiFail[T](cc: CallContext, failCode: Int = 400)(f: => T): Future[T] = Future {
      try f catch {
        case e: IllegalArgumentException =>
          val apiFailure = code.api.APIFailureNewStyle(e.getMessage, failCode, Some(cc.toLight))
          throw new Exception(net.liftweb.json.JsonAST.compactRender(
            net.liftweb.json.Extraction.decompose(apiFailure)))
      }
    }

    private def unboxResult[T: Manifest](box: Box[T], entityName: String): T = {
      if (box.isInstanceOf[Failure]) {
        val failure = box.asInstanceOf[Failure]
        val msg = failure.msg.replace(
          DynamicData.DynamicDataId.dbColumnName,
          StringUtils.uncapitalize(entityName) + "Id")
        val changedMsgFailure = failure.copy(msg = s"${code.api.util.ErrorMessages.InternalServerError} $msg")
        APIUtil.fullBoxOrException[T](changedMsgFailure)
      }
      box.openOrThrowException("impossible error")
    }

    private def createDynamicEntityImpl(cc: CallContext, dynamicEntity: DynamicEntityCommons): Future[JValue] =
      for {
        Full(result) <- NewStyle.function.createOrUpdateDynamicEntity(dynamicEntity, Some(cc))
        crudRoles = List(
          DynamicEntityInfo.canCreateRole(result.entityName, dynamicEntity.bankId),
          DynamicEntityInfo.canUpdateRole(result.entityName, dynamicEntity.bankId),
          DynamicEntityInfo.canGetRole(result.entityName, dynamicEntity.bankId),
          DynamicEntityInfo.canDeleteRole(result.entityName, dynamicEntity.bankId)
        )
      } yield {
        crudRoles.foreach(role =>
          Entitlement.entitlement.vend.addEntitlement(
            dynamicEntity.bankId.getOrElse(""), cc.userId, role.toString()))
        val commonsData: DynamicEntityCommons = result
        commonsData.jValue
      }

    private def updateDynamicEntityImpl(bankId: Option[String], dynamicEntityId: String, json: JValue, cc: CallContext): Future[JValue] =
      for {
        (entity, _) <- NewStyle.function.getDynamicEntityById(bankId, dynamicEntityId, Some(cc))
        (box, _) <- NewStyle.function.invokeDynamicConnector(
          GET_ALL, entity.entityName, None, None, entity.bankId, None, None, false, Some(cc))
        resultList: JArray = unboxResult(box.asInstanceOf[Box[JArray]], entity.entityName)
        _ <- code.util.Helper.booleanToFuture(DynamicEntityOperationNotAllowed, cc = Some(cc)) {
          resultList.arr.isEmpty
        }
        dynamicEntity <- tryOrApiFail(cc) {
          DynamicEntityCommons(json.asInstanceOf[JObject], Some(dynamicEntityId), cc.userId, bankId)
        }
        Full(result) <- NewStyle.function.createOrUpdateDynamicEntity(dynamicEntity, Some(cc))
      } yield {
        val commonsData: DynamicEntityCommons = result
        commonsData.jValue
      }

    private def deleteDynamicEntityImpl(bankId: Option[String], dynamicEntityId: String, cc: CallContext): Future[Box[Boolean]] =
      for {
        (entity, _) <- NewStyle.function.getDynamicEntityById(bankId, dynamicEntityId, Some(cc))
        (box, _) <- NewStyle.function.invokeDynamicConnector(
          GET_ALL, entity.entityName, None, None, entity.bankId, None, None, false, Some(cc))
        resultList: JArray = unboxResult(box.asInstanceOf[Box[JArray]], entity.entityName)
        _ <- code.util.Helper.booleanToFuture(DynamicEntityOperationNotAllowed, cc = Some(cc)) {
          resultList.arr.isEmpty
        }
        deleted: Box[Boolean] <- NewStyle.function.deleteDynamicEntity(bankId, dynamicEntityId)
      } yield deleted

    // ─── createSystemDynamicEntity ────────────────────────────────────────────

    val createSystemDynamicEntity: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "management" / "system-dynamic-entities" =>
        EndpointHelpers.executeFutureCreated(req) {
          implicit val cc: CallContext = req.callContext
          val rawBody = cc.httpBody.getOrElse("")
          for {
            jsonObj <- NewStyle.function.tryons(InvalidJsonFormat, 400, Some(cc)) {
              net.liftweb.json.parse(rawBody).asInstanceOf[JObject]
            }
            dynamicEntity <- tryOrApiFail(cc) {
              DynamicEntityCommons(jsonObj, None, cc.userId, None)
            }
            result <- createDynamicEntityImpl(cc, dynamicEntity)
          } yield result
        }
    }

    staticResourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(createSystemDynamicEntity), "POST",
      "/management/system-dynamic-entities",
      "Create System Level Dynamic Entity",
      s"""Create a system level Dynamic Entity.
         |
         |For more information see ${Glossary.getGlossaryItemLink("Dynamic-Entities")}
         |
         |${userAuthenticationMessage(true)}""",
      dynamicEntityRequestBodyExample.copy(bankId = None),
      dynamicEntityResponseBodyExample,
      List(AuthenticatedUserIsRequired, UserHasMissingRoles, InvalidJsonFormat, UnknownError),
      List(apiTagManageDynamicEntity, apiTagApi),
      Some(List(canCreateSystemLevelDynamicEntity)),
      http4sPartialFunction = Some(createSystemDynamicEntity))

    // ─── createBankLevelDynamicEntity ─────────────────────────────────────────

    val createBankLevelDynamicEntity: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "management" / "banks" / _ / "dynamic-entities" =>
        EndpointHelpers.executeFutureCreated(req) {
          implicit val cc: CallContext = req.callContext
          val bank = cc.bank.getOrElse(throw new RuntimeException(BankNotFound))
          val rawBody = cc.httpBody.getOrElse("")
          for {
            jsonObj <- NewStyle.function.tryons(InvalidJsonFormat, 400, Some(cc)) {
              net.liftweb.json.parse(rawBody).asInstanceOf[JObject]
            }
            dynamicEntity <- tryOrApiFail(cc) {
              DynamicEntityCommons(jsonObj, None, cc.userId, Some(bank.bankId.value))
            }
            result <- createDynamicEntityImpl(cc, dynamicEntity)
          } yield result
        }
    }

    staticResourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(createBankLevelDynamicEntity), "POST",
      "/management/banks/BANK_ID/dynamic-entities",
      "Create Bank Level Dynamic Entity",
      s"""Create a Bank Level DynamicEntity.
         |
         |For more information see ${Glossary.getGlossaryItemLink("Dynamic-Entities")}
         |
         |${userAuthenticationMessage(true)}""",
      dynamicEntityRequestBodyExample.copy(bankId = None),
      dynamicEntityResponseBodyExample,
      List(BankNotFound, AuthenticatedUserIsRequired, UserHasMissingRoles, InvalidJsonFormat, UnknownError),
      List(apiTagManageDynamicEntity, apiTagApi),
      Some(List(canCreateBankLevelDynamicEntity, canCreateAnyBankLevelDynamicEntity)),
      http4sPartialFunction = Some(createBankLevelDynamicEntity))

    // ─── updateSystemDynamicEntity ────────────────────────────────────────────

    val updateSystemDynamicEntity: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ PUT -> `prefixPath` / "management" / "system-dynamic-entities" / dynamicEntityId =>
        EndpointHelpers.executeAndRespond(req) { cc =>
          val rawBody = cc.httpBody.getOrElse("")
          for {
            json <- NewStyle.function.tryons(InvalidJsonFormat, 400, Some(cc)) {
              net.liftweb.json.parse(rawBody)
            }
            result <- updateDynamicEntityImpl(None, dynamicEntityId, json, cc)
          } yield result
        }
    }

    staticResourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(updateSystemDynamicEntity), "PUT",
      "/management/system-dynamic-entities/DYNAMIC_ENTITY_ID",
      "Update System Level Dynamic Entity",
      s"""Update a system level DynamicEntity.
         |
         |${userAuthenticationMessage(true)}""",
      dynamicEntityRequestBodyExample.copy(bankId = None),
      dynamicEntityResponseBodyExample,
      List(AuthenticatedUserIsRequired, UserHasMissingRoles, InvalidJsonFormat, UnknownError),
      List(apiTagManageDynamicEntity, apiTagApi),
      Some(List(canUpdateSystemDynamicEntity)),
      http4sPartialFunction = Some(updateSystemDynamicEntity))

    // ─── updateBankLevelDynamicEntity ─────────────────────────────────────────

    val updateBankLevelDynamicEntity: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ PUT -> `prefixPath` / "management" / "banks" / bankIdStr / "dynamic-entities" / dynamicEntityId =>
        EndpointHelpers.withUserAndBank(req) { (_, bank, cc) =>
          val rawBody = cc.httpBody.getOrElse("")
          for {
            json <- NewStyle.function.tryons(InvalidJsonFormat, 400, Some(cc)) {
              net.liftweb.json.parse(rawBody)
            }
            result <- updateDynamicEntityImpl(Some(bank.bankId.value), dynamicEntityId, json, cc)
          } yield result
        }
    }

    staticResourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(updateBankLevelDynamicEntity), "PUT",
      "/management/banks/BANK_ID/dynamic-entities/DYNAMIC_ENTITY_ID",
      "Update Bank Level Dynamic Entity",
      s"""Update a Bank Level DynamicEntity.
         |
         |${userAuthenticationMessage(true)}""",
      dynamicEntityRequestBodyExample.copy(bankId = None),
      dynamicEntityResponseBodyExample,
      List(BankNotFound, AuthenticatedUserIsRequired, UserHasMissingRoles, InvalidJsonFormat, UnknownError),
      List(apiTagManageDynamicEntity, apiTagApi),
      Some(List(canUpdateBankLevelDynamicEntity)),
      http4sPartialFunction = Some(updateBankLevelDynamicEntity))

    // ─── deleteSystemDynamicEntity (200) ─────────────────────────────────────

    val deleteSystemDynamicEntity: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ DELETE -> `prefixPath` / "management" / "system-dynamic-entities" / dynamicEntityId =>
        EndpointHelpers.withUser(req) { (_, cc) =>
          deleteDynamicEntityImpl(None, dynamicEntityId, cc).map(Full(_))
        }
    }

    staticResourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(deleteSystemDynamicEntity), "DELETE",
      "/management/system-dynamic-entities/DYNAMIC_ENTITY_ID",
      "Delete System Level Dynamic Entity",
      s"""Delete a system-level DynamicEntity specified by DYNAMIC_ENTITY_ID.
         |
         |${userAuthenticationMessage(true)}""",
      EmptyBody, EmptyBody,
      List(AuthenticatedUserIsRequired, UserHasMissingRoles, UnknownError),
      List(apiTagManageDynamicEntity, apiTagApi),
      Some(List(canDeleteSystemLevelDynamicEntity)),
      http4sPartialFunction = Some(deleteSystemDynamicEntity))

    // ─── deleteBankLevelDynamicEntity (200) ──────────────────────────────────

    val deleteBankLevelDynamicEntity: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ DELETE -> `prefixPath` / "management" / "banks" / _ / "dynamic-entities" / dynamicEntityId =>
        EndpointHelpers.withUserAndBank(req) { (_, bank, cc) =>
          deleteDynamicEntityImpl(Some(bank.bankId.value), dynamicEntityId, cc).map(Full(_))
        }
    }

    staticResourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(deleteBankLevelDynamicEntity), "DELETE",
      "/management/banks/BANK_ID/dynamic-entities/DYNAMIC_ENTITY_ID",
      "Delete Bank Level Dynamic Entity",
      s"""Delete a bank-level DynamicEntity specified by DYNAMIC_ENTITY_ID.
         |
         |${userAuthenticationMessage(true)}""",
      EmptyBody, EmptyBody,
      List(BankNotFound, AuthenticatedUserIsRequired, UserHasMissingRoles, UnknownError),
      List(apiTagManageDynamicEntity, apiTagApi),
      Some(List(canDeleteBankLevelDynamicEntity)),
      http4sPartialFunction = Some(deleteBankLevelDynamicEntity))

    // ─── updateMyDynamicEntity ────────────────────────────────────────────────

    val updateMyDynamicEntity: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ PUT -> `prefixPath` / "my" / "dynamic-entities" / dynamicEntityId =>
        EndpointHelpers.withUser(req) { (user, cc) =>
          val rawBody = cc.httpBody.getOrElse("")
          for {
            dynamicEntities <- Future(NewStyle.function.getDynamicEntitiesByUserId(user.userId))
            entityOption = dynamicEntities.find(_.dynamicEntityId.contains(dynamicEntityId))
            myEntity <- NewStyle.function.tryons(InvalidMyDynamicEntityUser, 400, Some(cc)) {
              entityOption.get
            }
            (box, _) <- NewStyle.function.invokeDynamicConnector(
              GET_ALL, myEntity.entityName, None, myEntity.dynamicEntityId,
              myEntity.bankId, None, Some(myEntity.userId), false, Some(cc))
            resultList: JArray = unboxResult(box.asInstanceOf[Box[JArray]], myEntity.entityName)
            _ <- code.util.Helper.booleanToFuture(DynamicEntityOperationNotAllowed, cc = Some(cc)) {
              resultList.arr.isEmpty
            }
            jsonObj <- NewStyle.function.tryons(InvalidJsonFormat, 400, Some(cc)) {
              net.liftweb.json.parse(rawBody).asInstanceOf[JObject]
            }
            dynamicEntity <- tryOrApiFail(cc) {
              DynamicEntityCommons(jsonObj, Some(dynamicEntityId), user.userId, myEntity.bankId)
            }
            Full(result) <- NewStyle.function.createOrUpdateDynamicEntity(dynamicEntity, Some(cc))
          } yield {
            val commonsData: DynamicEntityCommons = result
            commonsData.jValue
          }
        }
    }

    staticResourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(updateMyDynamicEntity), "PUT",
      "/my/dynamic-entities/DYNAMIC_ENTITY_ID",
      "Update My Dynamic Entity",
      s"""Update my DynamicEntity specified by DYNAMIC_ENTITY_ID.
         |
         |${userAuthenticationMessage(true)}""",
      dynamicEntityRequestBodyExample.copy(bankId = None),
      dynamicEntityResponseBodyExample,
      List(AuthenticatedUserIsRequired, InvalidMyDynamicEntityUser, InvalidJsonFormat, UnknownError),
      List(apiTagManageDynamicEntity, apiTagApi), None,
      http4sPartialFunction = Some(updateMyDynamicEntity))

    // ─── deleteMyDynamicEntity (200) ─────────────────────────────────────────

    val deleteMyDynamicEntity: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ DELETE -> `prefixPath` / "my" / "dynamic-entities" / dynamicEntityId =>
        EndpointHelpers.withUser(req) { (user, cc) =>
          for {
            dynamicEntities <- Future(NewStyle.function.getDynamicEntitiesByUserId(user.userId))
            entityOption = dynamicEntities.find(_.dynamicEntityId.contains(dynamicEntityId))
            myEntity <- NewStyle.function.tryons(InvalidMyDynamicEntityUser, 400, Some(cc)) {
              entityOption.get
            }
            (box, _) <- NewStyle.function.invokeDynamicConnector(
              GET_ALL, myEntity.entityName, None, myEntity.dynamicEntityId,
              myEntity.bankId, None, Some(myEntity.userId), false, Some(cc))
            resultList: JArray = unboxResult(box.asInstanceOf[Box[JArray]], myEntity.entityName)
            _ <- code.util.Helper.booleanToFuture(DynamicEntityOperationNotAllowed, cc = Some(cc)) {
              resultList.arr.isEmpty
            }
            deleted: Box[Boolean] <- NewStyle.function.deleteDynamicEntity(myEntity.bankId, dynamicEntityId)
          } yield deleted
        }
    }

    staticResourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(deleteMyDynamicEntity), "DELETE",
      "/my/dynamic-entities/DYNAMIC_ENTITY_ID",
      "Delete My Dynamic Entity",
      s"""Delete my DynamicEntity specified by DYNAMIC_ENTITY_ID.
         |
         |${userAuthenticationMessage(true)}""",
      EmptyBody, EmptyBody,
      List(AuthenticatedUserIsRequired, InvalidMyDynamicEntityUser, UnknownError),
      List(apiTagManageDynamicEntity, apiTagApi), None,
      http4sPartialFunction = Some(deleteMyDynamicEntity))

    // ─── dynamic-endpoint shared helpers (ported from APIMethods400) ────────

    private def createDynamicEndpointImpl(bankId: Option[String], json: JValue, cc: CallContext): Future[JObject] =
      for {
        tup <- NewStyle.function.tryons(
          InvalidJsonFormat + "The request json is not valid OpenAPIV3.0.x or Swagger 2.0.x Please check it in Swagger Editor or similar tools ",
          400, Some(cc)) {
          val jsonTweakedPath = DynamicEndpointHelper.addedBankToPath(json, bankId)
          val swaggerContent = compactRender(jsonTweakedPath)
          (DynamicEndpointSwagger(swaggerContent), DynamicEndpointHelper.parseSwaggerContent(swaggerContent))
        }
        (postedJson, openAPI) = tup
        duplicatedUrl = DynamicEndpointHelper.findExistingDynamicEndpoints(openAPI).map(kv => s"${kv._1}:${kv._2}")
        errorMsg = s"""$DynamicEndpointExists Duplicated ${if (duplicatedUrl.size > 1) "endpoints" else "endpoint"}: ${duplicatedUrl.mkString("; ")}"""
        _ <- code.util.Helper.booleanToFuture(errorMsg, cc = Some(cc)) { duplicatedUrl.isEmpty }
        dynamicEndpointInfo <- NewStyle.function.tryons(
          InvalidJsonFormat + "Can not convert to OBP Internal Resource Docs", 400, Some(cc)) {
          DynamicEndpointHelper.buildDynamicEndpointInfo(openAPI, "current_request_json_body", bankId)
        }
        roles <- NewStyle.function.tryons(
          InvalidJsonFormat + "Can not generate OBP roles", 400, Some(cc)) {
          DynamicEndpointHelper.getRoles(dynamicEndpointInfo)
        }
        _ <- NewStyle.function.tryons(
          InvalidJsonFormat + "Can not generate OBP external Resource Docs", 400, Some(cc)) {
          JSONFactory1_4_0.createResourceDocsJson(dynamicEndpointInfo.resourceDocs.toList, false, None)
        }
        (dynamicEndpoint, _) <- NewStyle.function.createDynamicEndpoint(
          bankId, cc.userId, postedJson.swaggerString, Some(cc))
        _ <- NewStyle.function.tryons(
          InvalidJsonFormat + s"Can not grant these roles ${roles.toString} ", 400, Some(cc)) {
          roles.map(role => Entitlement.entitlement.vend.addEntitlement(
            bankId.getOrElse(""), cc.userId, role.toString()))
        }
      } yield {
        val swaggerJson = parse(dynamicEndpoint.swaggerString)
        ("bank_id", dynamicEndpoint.bankId) ~ ("user_id", cc.userId) ~
          ("dynamic_endpoint_id", dynamicEndpoint.dynamicEndpointId) ~ ("swagger_string", swaggerJson)
      }

    private def updateDynamicEndpointHostImpl(bankId: Option[String], dynamicEndpointId: String, json: JValue, cc: CallContext): Future[code.api.v4_0_0.DynamicEndpointHostJson400] =
      for {
        (_, _) <- NewStyle.function.getDynamicEndpoint(bankId, dynamicEndpointId, Some(cc))
        postedData <- NewStyle.function.tryons(
          s"$InvalidJsonFormat The Json body should be the $DynamicEndpointHostJson400",
          400, Some(cc)) {
          json.extract[code.api.v4_0_0.DynamicEndpointHostJson400]
        }
        (_, _) <- NewStyle.function.updateDynamicEndpointHost(bankId, dynamicEndpointId, postedData.host, Some(cc))
      } yield postedData

    private def getDynamicEndpointsImpl(bankId: Option[String], cc: CallContext): Future[JValue] =
      for {
        (dynamicEndpoints, _) <- NewStyle.function.getDynamicEndpoints(bankId, Some(cc))
      } yield {
        val resultList = dynamicEndpoints.map[JObject, List[JObject]] { dynamicEndpoint =>
          val swaggerJson = parse(dynamicEndpoint.swaggerString)
          ("user_id", cc.userId) ~ ("dynamic_endpoint_id", dynamicEndpoint.dynamicEndpointId) ~
            ("swagger_string", swaggerJson)
        }
        net.liftweb.json.Extraction.decompose(ListResult("dynamic_endpoints", resultList))
      }

    private def getDynamicEndpointImpl(bankId: Option[String], dynamicEndpointId: String, cc: CallContext): Future[JObject] =
      for {
        (dynamicEndpoint, _) <- NewStyle.function.getDynamicEndpoint(bankId, dynamicEndpointId, Some(cc))
      } yield {
        val swaggerJson = parse(dynamicEndpoint.swaggerString)
        ("user_id", cc.userId) ~ ("dynamic_endpoint_id", dynamicEndpoint.dynamicEndpointId) ~
          ("swagger_string", swaggerJson)
      }

    // ─── createDynamicEndpoint (POST → 201) ──────────────────────────────────

    val createDynamicEndpoint: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "management" / "dynamic-endpoints" =>
        EndpointHelpers.executeFutureCreated(req) {
          implicit val cc: CallContext = req.callContext
          val rawBody = cc.httpBody.getOrElse("")
          for {
            json <- NewStyle.function.tryons(InvalidJsonFormat, 400, Some(cc)) {
              parse(rawBody)
            }
            result <- createDynamicEndpointImpl(None, json, cc)
          } yield result
        }
    }

    staticResourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(createDynamicEndpoint), "POST",
      "/management/dynamic-endpoints",
      "Create Dynamic Endpoint",
      s"""Create dynamic endpoints with one json format swagger content.
         |
         |${userAuthenticationMessage(true)}""",
      dynamicEndpointRequestBodyExample, dynamicEndpointResponseBodyExample,
      List(AuthenticatedUserIsRequired, UserHasMissingRoles, DynamicEndpointExists,
        InvalidJsonFormat, UnknownError),
      List(apiTagManageDynamicEndpoint, apiTagApi),
      Some(List(canCreateDynamicEndpoint)),
      http4sPartialFunction = Some(createDynamicEndpoint))

    // ─── createBankLevelDynamicEndpoint (POST → 201) ─────────────────────────

    val createBankLevelDynamicEndpoint: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "management" / "banks" / _ / "dynamic-endpoints" =>
        EndpointHelpers.executeFutureCreated(req) {
          implicit val cc: CallContext = req.callContext
          val bank = cc.bank.getOrElse(throw new RuntimeException(BankNotFound))
          val rawBody = cc.httpBody.getOrElse("")
          for {
            json <- NewStyle.function.tryons(InvalidJsonFormat, 400, Some(cc)) {
              parse(rawBody)
            }
            result <- createDynamicEndpointImpl(Some(bank.bankId.value), json, cc)
          } yield result
        }
    }

    staticResourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(createBankLevelDynamicEndpoint), "POST",
      "/management/banks/BANK_ID/dynamic-endpoints",
      "Create Bank Level Dynamic Endpoint",
      s"""Create dynamic endpoints with one json format swagger content.
         |
         |${userAuthenticationMessage(true)}""",
      dynamicEndpointRequestBodyExample, dynamicEndpointResponseBodyExample,
      List(BankNotFound, AuthenticatedUserIsRequired, UserHasMissingRoles, DynamicEndpointExists,
        InvalidJsonFormat, UnknownError),
      List(apiTagManageDynamicEndpoint, apiTagApi),
      Some(List(canCreateBankLevelDynamicEndpoint, canCreateDynamicEndpoint)),
      http4sPartialFunction = Some(createBankLevelDynamicEndpoint))

    // ─── updateDynamicEndpointHost (PUT → 201) ───────────────────────────────

    val updateDynamicEndpointHost: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ PUT -> `prefixPath` / "management" / "dynamic-endpoints" / dynamicEndpointId / "host" =>
        EndpointHelpers.executeFutureCreated(req) {
          implicit val cc: CallContext = req.callContext
          val rawBody = cc.httpBody.getOrElse("")
          for {
            json <- NewStyle.function.tryons(InvalidJsonFormat, 400, Some(cc)) { parse(rawBody) }
            result <- updateDynamicEndpointHostImpl(None, dynamicEndpointId, json, cc)
          } yield result
        }
    }

    staticResourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(updateDynamicEndpointHost), "PUT",
      "/management/dynamic-endpoints/DYNAMIC_ENDPOINT_ID/host",
      " Update Dynamic Endpoint Host",
      s"""Update dynamic endpoint Host.
         |The value can be obp_mock, dynamic_entity, or some service url.""",
      dynamicEndpointHostJson400, dynamicEndpointHostJson400,
      List(AuthenticatedUserIsRequired, UserHasMissingRoles,
        DynamicEntityNotFoundByDynamicEntityId, InvalidJsonFormat, UnknownError),
      List(apiTagManageDynamicEndpoint, apiTagApi),
      Some(List(canUpdateDynamicEndpoint)),
      http4sPartialFunction = Some(updateDynamicEndpointHost))

    // ─── updateBankLevelDynamicEndpointHost (PUT → 201) ──────────────────────

    val updateBankLevelDynamicEndpointHost: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ PUT -> `prefixPath` / "management" / "banks" / _ / "dynamic-endpoints" / dynamicEndpointId / "host" =>
        EndpointHelpers.executeFutureCreated(req) {
          implicit val cc: CallContext = req.callContext
          val bank = cc.bank.getOrElse(throw new RuntimeException(BankNotFound))
          val rawBody = cc.httpBody.getOrElse("")
          for {
            json <- NewStyle.function.tryons(InvalidJsonFormat, 400, Some(cc)) { parse(rawBody) }
            result <- updateDynamicEndpointHostImpl(Some(bank.bankId.value), dynamicEndpointId, json, cc)
          } yield result
        }
    }

    staticResourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(updateBankLevelDynamicEndpointHost), "PUT",
      "/management/banks/BANK_ID/dynamic-endpoints/DYNAMIC_ENDPOINT_ID/host",
      " Update Bank Level Dynamic Endpoint Host",
      s"""Update Bank Level dynamic endpoint Host.""",
      dynamicEndpointHostJson400, dynamicEndpointHostJson400,
      List(BankNotFound, AuthenticatedUserIsRequired, UserHasMissingRoles,
        DynamicEntityNotFoundByDynamicEntityId, InvalidJsonFormat, UnknownError),
      List(apiTagManageDynamicEndpoint, apiTagApi),
      Some(List(canUpdateBankLevelDynamicEndpoint, canUpdateDynamicEndpoint)),
      http4sPartialFunction = Some(updateBankLevelDynamicEndpointHost))

    // ─── getDynamicEndpoint (GET → 200) ──────────────────────────────────────

    val getDynamicEndpoint: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "management" / "dynamic-endpoints" / dynamicEndpointId =>
        EndpointHelpers.executeAndRespond(req) { cc =>
          getDynamicEndpointImpl(None, dynamicEndpointId, cc)
        }
    }

    staticResourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(getDynamicEndpoint), "GET",
      "/management/dynamic-endpoints/DYNAMIC_ENDPOINT_ID",
      "Get Dynamic Endpoint",
      s"""Get a Dynamic Endpoint by DYNAMIC_ENDPOINT_ID.""",
      EmptyBody, dynamicEndpointResponseBodyExample,
      List(AuthenticatedUserIsRequired, UserHasMissingRoles,
        DynamicEndpointNotFoundByDynamicEndpointId, InvalidJsonFormat, UnknownError),
      List(apiTagManageDynamicEndpoint, apiTagApi),
      Some(List(canGetDynamicEndpoint)),
      http4sPartialFunction = Some(getDynamicEndpoint))

    // ─── getDynamicEndpoints (GET → 200) ─────────────────────────────────────

    val getDynamicEndpoints: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "management" / "dynamic-endpoints" =>
        EndpointHelpers.executeAndRespond(req) { cc =>
          getDynamicEndpointsImpl(None, cc)
        }
    }

    staticResourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(getDynamicEndpoints), "GET",
      "/management/dynamic-endpoints",
      " Get Dynamic Endpoints",
      s"""Get Dynamic Endpoints.""",
      EmptyBody, ListResult("dynamic_endpoints", List(dynamicEndpointResponseBodyExample)),
      List(AuthenticatedUserIsRequired, UserHasMissingRoles, InvalidJsonFormat, UnknownError),
      List(apiTagManageDynamicEndpoint, apiTagApi),
      Some(List(canGetDynamicEndpoints)),
      http4sPartialFunction = Some(getDynamicEndpoints))

    // ─── getBankLevelDynamicEndpoint (GET → 200) ─────────────────────────────

    val getBankLevelDynamicEndpoint: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "management" / "banks" / _ / "dynamic-endpoints" / dynamicEndpointId =>
        EndpointHelpers.withUserAndBank(req) { (_, bank, cc) =>
          getDynamicEndpointImpl(Some(bank.bankId.value), dynamicEndpointId, cc)
        }
    }

    staticResourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(getBankLevelDynamicEndpoint), "GET",
      "/management/banks/BANK_ID/dynamic-endpoints/DYNAMIC_ENDPOINT_ID",
      " Get Bank Level Dynamic Endpoint",
      s"""Get a Bank Level Dynamic Endpoint.""",
      EmptyBody, dynamicEndpointResponseBodyExample,
      List(BankNotFound, AuthenticatedUserIsRequired, UserHasMissingRoles,
        DynamicEndpointNotFoundByDynamicEndpointId, InvalidJsonFormat, UnknownError),
      List(apiTagManageDynamicEndpoint, apiTagApi),
      Some(List(canGetBankLevelDynamicEndpoint, canGetDynamicEndpoint)),
      http4sPartialFunction = Some(getBankLevelDynamicEndpoint))

    // ─── getBankLevelDynamicEndpoints (GET → 200) ────────────────────────────

    val getBankLevelDynamicEndpoints: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "management" / "banks" / _ / "dynamic-endpoints" =>
        EndpointHelpers.withUserAndBank(req) { (_, bank, cc) =>
          getDynamicEndpointsImpl(Some(bank.bankId.value), cc)
        }
    }

    staticResourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(getBankLevelDynamicEndpoints), "GET",
      "/management/banks/BANK_ID/dynamic-endpoints",
      "Get Bank Level Dynamic Endpoints",
      s"""Get Bank Level Dynamic Endpoints.""",
      EmptyBody, ListResult("dynamic_endpoints", List(dynamicEndpointResponseBodyExample)),
      List(BankNotFound, AuthenticatedUserIsRequired, UserHasMissingRoles,
        InvalidJsonFormat, UnknownError),
      List(apiTagManageDynamicEndpoint, apiTagApi),
      Some(List(canGetBankLevelDynamicEndpoints, canGetDynamicEndpoints)),
      http4sPartialFunction = Some(getBankLevelDynamicEndpoints))

    // ─── deleteDynamicEndpoint (DELETE → 204) ────────────────────────────────

    val deleteDynamicEndpoint: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ DELETE -> `prefixPath` / "management" / "dynamic-endpoints" / dynamicEndpointId =>
        EndpointHelpers.withUserDelete(req) { (_, cc) =>
          NewStyle.function.deleteDynamicEndpoint(None, dynamicEndpointId, Some(cc))
        }
    }

    staticResourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(deleteDynamicEndpoint), "DELETE",
      "/management/dynamic-endpoints/DYNAMIC_ENDPOINT_ID",
      " Delete Dynamic Endpoint",
      s"""Delete a DynamicEndpoint specified by DYNAMIC_ENDPOINT_ID.""",
      EmptyBody, EmptyBody,
      List(AuthenticatedUserIsRequired, DynamicEndpointNotFoundByDynamicEndpointId, UnknownError),
      List(apiTagManageDynamicEndpoint, apiTagApi),
      Some(List(canDeleteDynamicEndpoint)),
      http4sPartialFunction = Some(deleteDynamicEndpoint))

    // ─── deleteBankLevelDynamicEndpoint (DELETE → 204) ───────────────────────

    val deleteBankLevelDynamicEndpoint: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ DELETE -> `prefixPath` / "management" / "banks" / _ / "dynamic-endpoints" / dynamicEndpointId =>
        EndpointHelpers.withUserAndBankDelete(req) { (_, bank, cc) =>
          NewStyle.function.deleteDynamicEndpoint(Some(bank.bankId.value), dynamicEndpointId, Some(cc))
        }
    }

    staticResourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(deleteBankLevelDynamicEndpoint), "DELETE",
      "/management/banks/BANK_ID/dynamic-endpoints/DYNAMIC_ENDPOINT_ID",
      " Delete Bank Level Dynamic Endpoint",
      s"""Delete a Bank Level DynamicEndpoint specified by DYNAMIC_ENDPOINT_ID.""",
      EmptyBody, EmptyBody,
      List(BankNotFound, AuthenticatedUserIsRequired,
        DynamicEndpointNotFoundByDynamicEndpointId, UnknownError),
      List(apiTagManageDynamicEndpoint, apiTagApi),
      Some(List(canDeleteBankLevelDynamicEndpoint, canDeleteDynamicEndpoint)),
      http4sPartialFunction = Some(deleteBankLevelDynamicEndpoint))

    // ─── getMyDynamicEndpoints (GET → 200) ───────────────────────────────────

    val getMyDynamicEndpoints: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "my" / "dynamic-endpoints" =>
        EndpointHelpers.withUser(req) { (user, cc) =>
          for {
            (dynamicEndpoints, _) <- NewStyle.function.getDynamicEndpointsByUserId(user.userId, Some(cc))
          } yield {
            val resultList = dynamicEndpoints.map[JObject, List[JObject]] { dynamicEndpoint =>
              val swaggerJson = parse(dynamicEndpoint.swaggerString)
              ("user_id", user.userId) ~ ("dynamic_endpoint_id", dynamicEndpoint.dynamicEndpointId) ~
                ("swagger_string", swaggerJson)
            }
            ListResult("dynamic_endpoints", resultList)
          }
        }
    }

    staticResourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(getMyDynamicEndpoints), "GET",
      "/my/dynamic-endpoints",
      "Get My Dynamic Endpoints",
      s"""Get My Dynamic Endpoints.""",
      EmptyBody, ListResult("dynamic_endpoints", List(dynamicEndpointResponseBodyExample)),
      List(AuthenticatedUserIsRequired, InvalidJsonFormat, UnknownError),
      List(apiTagManageDynamicEndpoint, apiTagApi), None,
      http4sPartialFunction = Some(getMyDynamicEndpoints))

    // ─── deleteMyDynamicEndpoint (DELETE → 204) ──────────────────────────────

    val deleteMyDynamicEndpoint: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ DELETE -> `prefixPath` / "my" / "dynamic-endpoints" / dynamicEndpointId =>
        EndpointHelpers.withUserDelete(req) { (user, cc) =>
          for {
            (dynamicEndpoint, _) <- NewStyle.function.getDynamicEndpoint(None, dynamicEndpointId, Some(cc))
            _ <- code.util.Helper.booleanToFuture(InvalidMyDynamicEndpointUser, cc = Some(cc)) {
              dynamicEndpoint.userId.equals(user.userId)
            }
            deleted <- NewStyle.function.deleteDynamicEndpoint(None, dynamicEndpointId, Some(cc))
          } yield deleted
        }
    }

    staticResourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(deleteMyDynamicEndpoint), "DELETE",
      "/my/dynamic-endpoints/DYNAMIC_ENDPOINT_ID",
      "Delete My Dynamic Endpoint",
      s"""Delete a DynamicEndpoint specified by DYNAMIC_ENDPOINT_ID.""",
      EmptyBody, EmptyBody,
      List(AuthenticatedUserIsRequired, DynamicEndpointNotFoundByDynamicEndpointId, UnknownError),
      List(apiTagManageDynamicEndpoint, apiTagApi), None,
      http4sPartialFunction = Some(deleteMyDynamicEndpoint))

    // ─── getProductAttribute (v4 override of Http4s310 — Lift declared role mismatch fixed) ─

    val getProductAttribute: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / bankIdStr / "products" / _ / "attributes" / productAttributeIdStr =>
        EndpointHelpers.withUser(req) { (user, cc) =>
          for {
            _ <- NewStyle.function.hasEntitlement(bankIdStr, user.userId, canGetProductAttribute, Some(cc))
            (_, _) <- NewStyle.function.getBank(BankId(bankIdStr), Some(cc))
            (productAttribute, _) <- NewStyle.function.getProductAttributeById(productAttributeIdStr, Some(cc))
          } yield createProductAttributeJson(productAttribute)
        }
    }

    staticResourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(getProductAttribute), "GET",
      "/banks/BANK_ID/products/PRODUCT_CODE/attributes/PRODUCT_ATTRIBUTE_ID",
      "Get Product Attribute",
      s"""Get one Product Attribute by its id.
         |
         |${userAuthenticationMessage(true)}""",
      EmptyBody, productAttributeResponseJsonV400,
      List(UserHasMissingRoles, UnknownError),
      List(apiTagProduct, apiTagProductAttribute, apiTagAttribute),
      Some(List(canGetProductAttribute)),
      http4sPartialFunction = Some(getProductAttribute))

    // ─── getScopes (GET /consumers/CONSUMER_ID/scopes) — v4 override of Http4s300 ─

    val getScopes: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "consumers" / uuidOfConsumer / "scopes" =>
        EndpointHelpers.withUser(req) { (user, cc) =>
          for {
            callingConsumer <- Future { cc.consumer } map { x =>
              unboxFullOrFail(x, Some(cc), InvalidConsumerCredentials)
            }
            _ <- Future {
              NewStyle.function.hasEntitlementAndScope(
                "", user.userId, callingConsumer.id.get.toString,
                canGetEntitlementsForAnyUserAtAnyBank, Some(cc))
            } flatMap { unboxFullAndWrapIntoFuture(_) }
            targetConsumer <- NewStyle.function.getConsumerByConsumerId(uuidOfConsumer, Some(cc))
            scopes <- Future {
              code.scope.Scope.scope.vend.getScopesByConsumerId(targetConsumer.id.get.toString)
            } map { unboxFull(_) }
          } yield code.api.v3_0_0.JSONFactory300.createScopeJSONs(scopes)
        }
    }

    staticResourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(getScopes), "GET",
      "/consumers/CONSUMER_ID/scopes",
      "Get Scopes for Consumer",
      s"""Get all the scopes for an consumer specified by CONSUMER_ID
         |
         |${userAuthenticationMessage(true)}""",
      EmptyBody, scopeJsons,
      List(AuthenticatedUserIsRequired, EntitlementNotFound, ConsumerNotFoundByConsumerId, UnknownError),
      List(apiTagScope, apiTagConsumer), None,
      http4sPartialFunction = Some(getScopes))

    // ─── addScope (POST /consumers/CONSUMER_ID/scopes → 201) — v4 override ────

    val addScope: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "consumers" / consumerId / "scopes" =>
        EndpointHelpers.withUserAndBodyCreated[code.api.v3_0_0.CreateScopeJson, Any](req) { (user, postedData, cc) =>
          for {
            consumer <- NewStyle.function.getConsumerByConsumerId(consumerId, Some(cc))
            role <- Future { net.liftweb.util.Helpers.tryo { code.api.util.ApiRole.valueOf(postedData.role_name) } } map { x =>
              unboxFullOrFail(x, Some(cc),
                IncorrectRoleName + postedData.role_name + ". Possible roles are " + code.api.util.ApiRole.availableRoles.sorted.mkString(", "))
            }
            _ <- code.util.Helper.booleanToFuture(
              failMsg = if (role.requiresBankId) EntitlementIsBankRole else EntitlementIsSystemRole,
              cc = Some(cc)) {
              role.requiresBankId == postedData.bank_id.nonEmpty
            }
            allowedEntitlements = canCreateScopeAtOneBank :: canCreateScopeAtAnyBank :: Nil
            _ <- NewStyle.function.hasAtLeastOneEntitlement(
              failMsg = s"$UserHasMissingRoles ${allowedEntitlements.mkString(", ")}!"
            )(postedData.bank_id, user.userId, allowedEntitlements, Some(cc))
            _ <- code.util.Helper.booleanToFuture(failMsg = BankNotFound, cc = Some(cc)) {
              postedData.bank_id.isEmpty || BankX(BankId(postedData.bank_id), Some(cc)).map(_._1).isDefined
            }
            _ <- code.util.Helper.booleanToFuture(failMsg = EntitlementAlreadyExists, cc = Some(cc)) {
              !APIUtil.hasScope(postedData.bank_id, consumerId, role)
            }
            addedEntitlement <- Future {
              code.scope.Scope.scope.vend.addScope(
                postedData.bank_id, consumer.id.get.toString, postedData.role_name)
            } map { unboxFull(_) }
          } yield code.api.v3_0_0.JSONFactory300.createScopeJson(addedEntitlement)
        }
    }

    staticResourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(addScope), "POST",
      "/consumers/CONSUMER_ID/scopes",
      "Create Scope for a Consumer",
      """Create Scope. Grant Role to Consumer.
        |
        |Scopes are used to grant System or Bank level roles to the Consumer (App).""",
      createScopeJson, scopeJson,
      List(AuthenticatedUserIsRequired, ConsumerNotFoundById, InvalidJsonFormat,
        IncorrectRoleName, EntitlementIsBankRole, EntitlementIsSystemRole, EntitlementAlreadyExists, UnknownError),
      List(apiTagScope, apiTagConsumer),
      Some(List(canCreateScopeAtAnyBank, canCreateScopeAtOneBank)),
      http4sPartialFunction = Some(addScope))

    // ─── getConsents (GET /banks/BANK_ID/my/consents) — v4 override of Http4s310 ─

    val getConsents: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / _ / "my" / "consents" =>
        EndpointHelpers.withUserAndBank(req) { (user, bank, _) =>
          val params = req.uri.query.params
          val limit = params.get("limit").flatMap(s => scala.util.Try(s.toInt).toOption).getOrElse(50)
          val offset = params.get("offset").flatMap(s => scala.util.Try(s.toInt).toOption).getOrElse(0)
          for {
            rows <- Future {
              code.consent.DoobieConsentQueries.getConsentsByUserAndBank(
                userId = user.userId, bankId = bank.bankId.value, status = None,
                limit = limit, offset = offset,
                sortField = "created_date", sortDirection = "desc")
            }
          } yield {
            val consents = rows.map(r => ConsentJsonV400(
              r.consentId, r.jwt.getOrElse(""), r.status,
              r.apiStandard.getOrElse(""), r.apiVersion.getOrElse("")))
            ConsentsJsonV400(consents)
          }
        }
    }

    staticResourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(getConsents), "GET",
      "/banks/BANK_ID/my/consents",
      "Get Consents",
      s"""This endpoint gets the Consents that the current User created.
         |
         |${userAuthenticationMessage(true)}
         |
         |1 limit (for pagination: defaults to 50)  eg:limit=200
         |
         |2 offset (for pagination: zero index, defaults to 0) eg: offset=10""",
      EmptyBody, consentsJsonV400,
      List($AuthenticatedUserIsRequired, $BankNotFound, UnknownError),
      List(apiTagConsent, apiTagPSD2AIS, apiTagPsd2), None,
      http4sPartialFunction = Some(getConsents))

    // ─── updateAccountLabel (POST /banks/BANK_ID/accounts/ACCOUNT_ID → 200) — v4 override of Http4s121 ─

    val updateAccountLabel: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "banks" / bankIdStr / "accounts" / accountIdStr =>
        EndpointHelpers.withUserAndBody[UpdateAccountJsonV400, Any](req) { (user, postedData, cc) =>
          for {
            (account, _) <- NewStyle.function.checkBankAccountExists(BankId(bankIdStr), AccountId(accountIdStr), Some(cc))
            anyViewContainsCanUpdateBankAccountLabelPermission = Views.views.vend
              .permission(BankIdAccountId(account.bankId, account.accountId), user)
              .map(_.views.map(_.allowed_actions.exists(_ == CAN_UPDATE_BANK_ACCOUNT_LABEL)))
              .getOrElse(Nil)
              .find(_ == true)
              .getOrElse(false)
            _ <- code.util.Helper.booleanToFuture(
              s"${ViewDoesNotPermitAccess} You need the `${CAN_UPDATE_BANK_ACCOUNT_LABEL}` permission on any your views",
              cc = Some(cc)) {
              anyViewContainsCanUpdateBankAccountLabelPermission
            }
            _ <- Connector.connector.vend.updateAccountLabel(
              BankId(bankIdStr), AccountId(accountIdStr), postedData.label, Some(cc)
            ) map { i =>
              unboxFullOrFail(i._1, i._2,
                s"$UpdateBankAccountLabelError Current BankId is $bankIdStr and Current AccountId is $accountIdStr", 404)
            }
          } yield successMessage
        }
    }

    staticResourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(updateAccountLabel), "POST",
      "/banks/BANK_ID/accounts/ACCOUNT_ID",
      "Update Account Label",
      s"""Update the label for the account. The label is how the account is known to the account owner e.g. 'My savings account'.
         |
         |${userAuthenticationMessage(true)}""",
      updateAccountJsonV400, successMessage,
      List(InvalidJsonFormat, $AuthenticatedUserIsRequired, $BankAccountNotFound,
        "user does not have access to owner view on account", UnknownError),
      List(apiTagAccount), None,
      http4sPartialFunction = Some(updateAccountLabel))

    // ─── getExplicitCounterpartiesForAccount (GET .../counterparties) — v4 override ─

    val getExplicitCounterpartiesForAccount: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / _ / "accounts" / _ / _ / "counterparties" =>
        EndpointHelpers.withView(req) { (user, account, view, cc) =>
          for {
            _ <- code.util.Helper.booleanToFuture(
              failMsg = s"${NoViewPermission}can_get_counterparty", failCode = 403, cc = Some(cc)) {
              view.allowed_actions.exists(_ == CAN_GET_COUNTERPARTY)
            }
            (counterparties, _) <- NewStyle.function.getCounterparties(
              account.bankId, account.accountId, view.viewId, Some(cc))
            _ <- code.util.Helper.booleanToFuture(CreateOrUpdateCounterpartyMetadataError, 400, cc = Some(cc)) {
              counterparties.forall { cp =>
                code.metadata.counterparties.Counterparties.counterparties.vend
                  .getOrCreateMetadata(account.bankId, account.accountId, cp.counterpartyId, cp.name)
                  .isDefined
              }
            }
          } yield JSONFactory400.createCounterpartiesJson400(counterparties)
        }
    }

    staticResourceDocs += ResourceDoc(
      null, implementedInApiVersion, "getExplicitCounterpartiesForAccount", "GET",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/counterparties",
      "Get Counterparties (Explicit)",
      s"""Get the Counterparties that have been explicitly created on the specified Account / View.
         |
         |${userAuthenticationMessage(true)}""",
      EmptyBody, counterpartiesJson400,
      List($AuthenticatedUserIsRequired, $BankNotFound, $BankAccountNotFound,
        $UserNoPermissionAccessView, ViewNotFound, UnknownError),
      List(apiTagCounterparty, apiTagPSD2PIS, apiTagPsd2, apiTagAccount), None,
      http4sPartialFunction = Some(getExplicitCounterpartiesForAccount))

    // ─── getExplicitCounterpartyById (GET .../counterparties/COUNTERPARTY_ID) — v4 override ─

    val getExplicitCounterpartyById: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / _ / "accounts" / _ / _ / "counterparties" / counterpartyIdStr =>
        EndpointHelpers.withView(req) { (_, account, view, cc) =>
          for {
            _ <- code.util.Helper.booleanToFuture(
              failMsg = s"${NoViewPermission}can_get_counterparty", failCode = 403, cc = Some(cc)) {
              view.allowed_actions.exists(_ == CAN_GET_COUNTERPARTY)
            }
            (counterparty, _) <- NewStyle.function.getCounterpartyByCounterpartyId(
              CounterpartyId(counterpartyIdStr), Some(cc))
            counterpartyMetadata <- NewStyle.function.getMetadata(
              account.bankId, account.accountId, counterparty.counterpartyId, Some(cc))
          } yield JSONFactory400.createCounterpartyWithMetadataJson400(counterparty, counterpartyMetadata)
        }
    }

    staticResourceDocs += ResourceDoc(
      null, implementedInApiVersion, "getExplicitCounterpartyById", "GET",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/counterparties/EXPLICIT_COUNTERPARTY_ID",
      "Get Counterparty by Id (Explicit)",
      s"""This endpoint returns a single Counterparty on an Account View specified by its COUNTERPARTY_ID.
         |
         |${userAuthenticationMessage(true)}""",
      EmptyBody, counterpartyWithMetadataJson400,
      List($AuthenticatedUserIsRequired, $BankNotFound, $BankAccountNotFound,
        $UserNoPermissionAccessView, UnknownError),
      List(apiTagCounterparty, apiTagPSD2PIS, apiTagPsd2, apiTagCounterpartyMetaData), None,
      http4sPartialFunction = Some(getExplicitCounterpartyById))

    // ─── createExplicitCounterparty (POST .../counterparties → 201) — v4 override ─

    val createExplicitCounterparty: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "banks" / bankIdStr / "accounts" / accountIdStr / _ / "counterparties" =>
        EndpointHelpers.withViewCreated(req) { (user, account, view, cc) =>
          val bodyStr = cc.httpBody.getOrElse("")
          for {
            _ <- code.util.Helper.booleanToFuture(InvalidAccountIdFormat, cc = Some(cc)) { isValidID(account.accountId.value) }
            _ <- code.util.Helper.booleanToFuture(InvalidBankIdFormat, cc = Some(cc)) { isValidID(account.bankId.value) }
            postJson <- NewStyle.function.tryons(
              s"$InvalidJsonFormat The Json body should be the PostCounterpartyJson400", 400, Some(cc)) {
              net.liftweb.json.parse(bodyStr).extract[PostCounterpartyJson400]
            }
            _ <- code.util.Helper.booleanToFuture(
              failMsg = s"$NoViewPermission can_add_counterparty. Please use a view with that permission or add the permission to this view.",
              failCode = 403, cc = Some(cc)) {
              view.allowed_actions.exists(_ == CAN_ADD_COUNTERPARTY)
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
            _ <- code.util.Helper.booleanToFuture(
              s"$InvalidISOCurrencyCode Current input is: '${postJson.currency}'",
              cc = Some(cc)) { APIUtil.isValidCurrencyISOCode(postJson.currency) }
            (_, _) <-
              if (postJson.other_bank_routing_scheme.equalsIgnoreCase("OBP")
                && postJson.other_account_routing_scheme.equalsIgnoreCase("OBP"))
                for {
                  (_, c) <- NewStyle.function.getBank(BankId(postJson.other_bank_routing_address), Some(cc))
                  r      <- NewStyle.function.checkBankAccountExists(BankId(postJson.other_bank_routing_address), AccountId(postJson.other_account_routing_address), c)
                } yield r
              else if (postJson.other_bank_routing_scheme.equalsIgnoreCase("OBP")
                && postJson.other_account_secondary_routing_scheme.equalsIgnoreCase("OBP"))
                for {
                  (_, c) <- NewStyle.function.getBank(BankId(postJson.other_bank_routing_address), Some(cc))
                  r      <- NewStyle.function.checkBankAccountExists(BankId(postJson.other_bank_routing_address), AccountId(postJson.other_account_secondary_routing_address), c)
                } yield r
              else if (postJson.other_bank_routing_scheme.equalsIgnoreCase("ACCOUNT_NUMBER")
                || postJson.other_bank_routing_scheme.equalsIgnoreCase("ACCOUNT_NO"))
                NewStyle.function.getBankAccountByNumber(
                  if (postJson.other_bank_routing_address.isEmpty) None else Some(BankId(postJson.other_bank_routing_address)),
                  postJson.other_bank_routing_address, Some(cc))
              else Future.successful((Full(()), Some(cc)))
            otherAccountRoutingSchemeOBPFormat =
              if (postJson.other_account_routing_scheme.equalsIgnoreCase("AccountNo")) "ACCOUNT_NUMBER"
              else org.apache.commons.lang3.StringUtils.upperCase(
                net.liftweb.util.StringHelpers.snakify(postJson.other_account_routing_scheme))
            (counterparty, _) <- NewStyle.function.createCounterparty(
              name                              = postJson.name,
              description                       = postJson.description,
              currency                          = postJson.currency,
              createdByUserId                   = user.userId,
              thisBankId                        = account.bankId.value,
              thisAccountId                     = account.accountId.value,
              thisViewId                        = view.viewId.value,
              otherAccountRoutingScheme         = otherAccountRoutingSchemeOBPFormat,
              otherAccountRoutingAddress        = postJson.other_account_routing_address,
              otherAccountSecondaryRoutingScheme = net.liftweb.util.StringHelpers.snakify(postJson.other_account_secondary_routing_scheme).toUpperCase,
              otherAccountSecondaryRoutingAddress = postJson.other_account_secondary_routing_address,
              otherBankRoutingScheme            = net.liftweb.util.StringHelpers.snakify(postJson.other_bank_routing_scheme).toUpperCase,
              otherBankRoutingAddress           = postJson.other_bank_routing_address,
              otherBranchRoutingScheme          = net.liftweb.util.StringHelpers.snakify(postJson.other_branch_routing_scheme).toUpperCase,
              otherBranchRoutingAddress         = postJson.other_branch_routing_address,
              isBeneficiary                     = postJson.is_beneficiary,
              bespoke                           = postJson.bespoke.map(b => CounterpartyBespoke(b.key, b.value)),
              callContext                       = Some(cc)
            )
            (counterpartyMetadata, _) <- NewStyle.function.getOrCreateMetadata(
              account.bankId, account.accountId, counterparty.counterpartyId, postJson.name, Some(cc))
          } yield JSONFactory400.createCounterpartyWithMetadataJson400(counterparty, counterpartyMetadata)
        }
    }

    staticResourceDocs += ResourceDoc(
      null, implementedInApiVersion, "createCounterparty", "POST",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/counterparties",
      "Create Counterparty (Explicit)",
      s"""Create Counterparty (Explicit) for an Account.
         |
         |${userAuthenticationMessage(true)}""",
      postCounterpartyJson400, counterpartyWithMetadataJson400,
      List($AuthenticatedUserIsRequired, InvalidAccountIdFormat, InvalidBankIdFormat,
        InvalidJsonFormat, NoViewPermission, CounterpartyAlreadyExists,
        InvalidValueLength, InvalidISOCurrencyCode, UnknownError),
      List(apiTagCounterparty, apiTagPSD2PIS, apiTagPsd2, apiTagAccount), None,
      http4sPartialFunction = Some(createExplicitCounterparty))

    // ─── getFirehoseAccountsAtOneBank ─────────────────────────────────────────
    // v4 override of Http4s300: same business logic, but the response is built by
    // JSONFactory400.createFirehoseCoreBankAccountJSON which returns
    // ModeratedFirehoseAccountsJsonV400 (with `accounts`/`product_code` etc.) instead
    // of v3.0.0's ModeratedCoreAccountsJsonV300 shape that FirehoseTest can't parse.

    val getFirehoseAccountsAtOneBank: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / bankIdStr / "firehose" / "accounts" / "views" / viewIdStr =>
        EndpointHelpers.withUser(req) { (user, cc) =>
          val roles = ApiRoleObj.canUseAccountFirehose :: canUseAccountFirehoseAtAnyBank :: Nil
          val roleMsg = UserHasMissingRoles + roles.mkString(" or ")
          for {
            _ <- code.util.Helper.booleanToFuture(AccountFirehoseNotAllowedOnThisInstance, cc = Some(cc)) {
              allowAccountFirehose
            }
            _ <- code.util.Helper.booleanToFuture(roleMsg, failCode = 403, cc = Some(cc)) {
              APIUtil.hasAtLeastOneEntitlement(bankIdStr, user.userId, roles)
            }
            (bank, _) <- NewStyle.function.getBank(BankId(bankIdStr), Some(cc))
            view <- ViewNewStyle.checkViewAccessAndReturnView(
              ViewId(viewIdStr), BankIdAccountId(bank.bankId, AccountId("")), Some(user), Some(cc))
            availableBankIdAccountIdList <- Future {
              Views.views.vend.getAllFirehoseAccounts(bank.bankId).map(a => BankIdAccountId(a.bankId, a.accountId))
            }
            params = req.uri.query.multiParams.filterNot { case (k, _) => k == PARAM_TIMESTAMP || k == PARAM_LOCALE }
            filteredList <- if (params.isEmpty) {
              Future.successful(availableBankIdAccountIdList)
            } else {
              code.accountattribute.AccountAttributeX.accountAttributeProvider.vend
                .getAccountIdsByParams(bank.bankId, params.map { case (k, vs) => k -> vs.toList })
                .map { boxedAccountIds =>
                  val accountIds = boxedAccountIds.getOrElse(Nil)
                  availableBankIdAccountIdList.filter(ba => accountIds.contains(ba.accountId.value))
                }
            }
            moderatedAccounts: List[ModeratedBankAccount] = for {
              bankIdAccountId <- filteredList
              (bankAccount, callContext) <- Connector.connector.vend
                .getBankAccountLegacy(bankIdAccountId.bankId, bankIdAccountId.accountId, Some(cc)) ?~!
                s"$BankAccountNotFound Current Bank_Id(${bankIdAccountId.bankId}), Account_Id(${bankIdAccountId.accountId})"
              moderatedAccount <- bankAccount.moderatedBankAccount(view, bankIdAccountId, Full(user), Some(cc))
            } yield moderatedAccount
            (accountAttributes: Option[List[AccountAttribute]], _) <- if (moderatedAccounts.nonEmpty && params.nonEmpty) {
              val futures = filteredList.map { bankIdAccount =>
                NewStyle.function.getAccountAttributesByAccount(bankIdAccount.bankId, bankIdAccount.accountId, Some(cc))
              }
              Future.reduceLeft(futures)((r, t) => r.copy(_1 = r._1 ::: t._1))
                .map(it => (Some(it._1), it._2))
            } else {
              Future.successful((None, Some(cc)))
            }
          } yield JSONFactory400.createFirehoseCoreBankAccountJSON(moderatedAccounts, accountAttributes)
        }
    }

    staticResourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(getFirehoseAccountsAtOneBank), "GET",
      "/banks/FIREHOSE_BANK_ID/firehose/accounts/views/FIREHOSE_VIEW_ID",
      "Get Firehose Accounts at Bank",
      s"""Get all Accounts at a Bank that have a Firehose View.
         |
         |${userAuthenticationMessage(true)}""",
      EmptyBody, moderatedFirehoseAccountsJsonV400,
      List(AuthenticatedUserIsRequired, AccountFirehoseNotAllowedOnThisInstance, UnknownError),
      List(apiTagAccountFirehose, apiTagAccount, apiTagFirehoseData, apiTagAccount), None,
      http4sPartialFunction = Some(getFirehoseAccountsAtOneBank))

    // ─── allRoutes ────────────────────────────────────────────────────────────

    private val allOwnRoutes: HttpRoutes[IO] = Kleisli[HttpF, Request[IO], Response[IO]] { req =>
      root.run(req)
        .orElse(getMapperDatabaseInfo.run(req))
        .orElse(getLogoutLink.run(req))
        .orElse(getBanks.run(req))
        .orElse(getBank.run(req))
        .orElse(ibanChecker.run(req))
        .orElse(callsLimit.run(req))
        .orElse(createBank.run(req))
        .orElse(getAtms.run(req))
        .orElse(getAtm.run(req))
        .orElse(getProducts.run(req))
        .orElse(getProduct.run(req))
        .orElse(createAtm.run(req))
        .orElse(createProduct.run(req))
        .orElse(createProductAttribute.run(req))
        .orElse(updateProductAttribute.run(req))
        .orElse(getEntitlements.run(req))
        .orElse(getUserByUserId.run(req))
        .orElse(getUserByUsername.run(req))
        .orElse(getUsersByEmail.run(req))
        .orElse(getUsers.run(req))
        .orElse(getCustomersByAttributes.run(req))
        .orElse(createCustomer.run(req))
        .orElse(getBankAccountsBalancesForCurrentUser.run(req))
        .orElse(getCoreAccountById.run(req))
        .orElse(getPrivateAccountByIdFull.run(req))
        .orElse(getPrivateAccountsAtOneBank.run(req))
        .orElse(createUserCustomerLinks.run(req))
        .orElse(getSystemDynamicEntities.run(req))
        .orElse(getBankLevelDynamicEntities.run(req))
        .orElse(getMyDynamicEntities.run(req))
        .orElse(createSystemDynamicEntity.run(req))
        .orElse(createBankLevelDynamicEntity.run(req))
        .orElse(updateSystemDynamicEntity.run(req))
        .orElse(updateBankLevelDynamicEntity.run(req))
        .orElse(deleteSystemDynamicEntity.run(req))
        .orElse(deleteBankLevelDynamicEntity.run(req))
        .orElse(updateMyDynamicEntity.run(req))
        .orElse(deleteMyDynamicEntity.run(req))
        .orElse(createDynamicEndpoint.run(req))
        .orElse(createBankLevelDynamicEndpoint.run(req))
        .orElse(updateDynamicEndpointHost.run(req))
        .orElse(updateBankLevelDynamicEndpointHost.run(req))
        .orElse(getDynamicEndpoint.run(req))
        .orElse(getDynamicEndpoints.run(req))
        .orElse(getBankLevelDynamicEndpoint.run(req))
        .orElse(getBankLevelDynamicEndpoints.run(req))
        .orElse(deleteDynamicEndpoint.run(req))
        .orElse(deleteBankLevelDynamicEndpoint.run(req))
        .orElse(getMyDynamicEndpoints.run(req))
        .orElse(deleteMyDynamicEndpoint.run(req))
        .orElse(getProductAttribute.run(req))
        .orElse(getScopes.run(req))
        .orElse(addScope.run(req))
        .orElse(getConsents.run(req))
        .orElse(updateAccountLabel.run(req))
        .orElse(getExplicitCounterpartiesForAccount.run(req))
        .orElse(getExplicitCounterpartyById.run(req))
        .orElse(createExplicitCounterparty.run(req))
        .orElse(getFirehoseAccountsAtOneBank.run(req))
    }

    val allRoutesWithMiddleware: HttpRoutes[IO] = ResourceDocMiddleware.apply(resourceDocs)(allOwnRoutes)

    // ─── path-rewriting bridge: /obp/v4.0.0/… → /obp/v3.1.0/… ──────────────

    val v400ToV310Bridge: HttpRoutes[IO] = Kleisli[HttpF, Request[IO], Response[IO]] { req =>
      val rawPath = req.uri.path.renderString
      if (rawPath.startsWith("/obp/v4.0.0/")) {
        val rewritten    = rawPath.replaceFirst("/obp/v4\\.0\\.0/", "/obp/v3.1.0/")
        val newUri       = req.uri.withPath(Uri.Path.unsafeFromString(rewritten))
        val rewrittenReq = req.withUri(newUri)
        code.api.v3_1_0.Http4s310.wrappedRoutesV310Services.run(rewrittenReq)
      } else {
        OptionT.none[IO, Response[IO]]
      }
    }
  }

  val wrappedRoutesV400Services: HttpRoutes[IO] =
    Kleisli[HttpF, Request[IO], Response[IO]] { req =>
      Implementations4_0_0.allRoutesWithMiddleware.run(req)
        .orElse(Implementations4_0_0.v400ToV310Bridge.run(req))
    }
}
