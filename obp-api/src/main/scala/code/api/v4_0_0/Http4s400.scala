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
import code.api.dynamic.endpoint.helper.DynamicEndpointHelper
import code.api.dynamic.entity.helper.DynamicEntityInfo
import code.api.v1_4_0.JSONFactory1_4_0
import code.DynamicEndpoint.DynamicEndpointSwagger
import code.api.util.http4s.Http4sRequestAttributes.{EndpointHelpers, RequestOps}
import code.api.util.http4s.ResourceDocMiddleware
import code.api.util.{APIUtil, CallContext, CustomJsonFormats, NewStyle}
import code.api.v4_0_0.JSONFactory400._
import code.DynamicData.DynamicData
import code.api.util.migration.Migration
import code.dynamicEntity.DynamicEntityCommons
import code.entitlement.Entitlement
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
