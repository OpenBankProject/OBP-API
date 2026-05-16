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
import code.authtypevalidation.JsonAuthTypeValidation
import code.endpointMapping.EndpointMappingCommons
import code.entitlement.Entitlement
import code.model.BankX

import scala.collection.JavaConverters._
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

    lazy val getMapperDatabaseInfo: HttpRoutes[IO] = HttpRoutes.of[IO] {
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

    lazy val getLogoutLink: HttpRoutes[IO] = HttpRoutes.of[IO] {
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

    lazy val getBanks: HttpRoutes[IO] = HttpRoutes.of[IO] {
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

    lazy val getBank: HttpRoutes[IO] = HttpRoutes.of[IO] {
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

    lazy val ibanChecker: HttpRoutes[IO] = HttpRoutes.of[IO] {
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

    lazy val callsLimit: HttpRoutes[IO] = HttpRoutes.of[IO] {
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

    lazy val createBank: HttpRoutes[IO] = HttpRoutes.of[IO] {
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

    lazy val root: HttpRoutes[IO] = HttpRoutes.of[IO] {
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

    lazy val getAtms: HttpRoutes[IO] = HttpRoutes.of[IO] {
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

    lazy val getAtm: HttpRoutes[IO] = HttpRoutes.of[IO] {
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

    lazy val getProducts: HttpRoutes[IO] = HttpRoutes.of[IO] {
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

    lazy val getProduct: HttpRoutes[IO] = HttpRoutes.of[IO] {
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

    lazy val createAtm: HttpRoutes[IO] = HttpRoutes.of[IO] {
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

    lazy val createProduct: HttpRoutes[IO] = HttpRoutes.of[IO] {
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

    lazy val createProductAttribute: HttpRoutes[IO] = HttpRoutes.of[IO] {
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

    lazy val updateProductAttribute: HttpRoutes[IO] = HttpRoutes.of[IO] {
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

    lazy val getEntitlements: HttpRoutes[IO] = HttpRoutes.of[IO] {
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

    lazy val getUserByUserId: HttpRoutes[IO] = HttpRoutes.of[IO] {
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

    lazy val getUserByUsername: HttpRoutes[IO] = HttpRoutes.of[IO] {
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

    lazy val getUsersByEmail: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "users" / "email" / email / "terminator" =>
        EndpointHelpers.withUser(req) { (_, _) =>
          for {
            users <- Users.users.vend.getUsersByEmail(email)
          } yield JSONFactory400.createUsersJson(users)
        }
    }

    staticResourceDocs += ResourceDoc(
      null, implementedInApiVersion, "getUsersByEmail", "GET",
      "/users/email/USER_EMAIL/terminator",
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

    lazy val getUsers: HttpRoutes[IO] = HttpRoutes.of[IO] {
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

    lazy val getCustomersByAttributes: HttpRoutes[IO] = HttpRoutes.of[IO] {
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

    lazy val createCustomer: HttpRoutes[IO] = HttpRoutes.of[IO] {
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

    lazy val getBankAccountsBalancesForCurrentUser: HttpRoutes[IO] = HttpRoutes.of[IO] {
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

    lazy val getCoreAccountById: HttpRoutes[IO] = HttpRoutes.of[IO] {
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

    lazy val getPrivateAccountByIdFull: HttpRoutes[IO] = HttpRoutes.of[IO] {
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

    lazy val getPrivateAccountsAtOneBank: HttpRoutes[IO] = HttpRoutes.of[IO] {
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

    lazy val createUserCustomerLinks: HttpRoutes[IO] = HttpRoutes.of[IO] {
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

    lazy val getSystemDynamicEntities: HttpRoutes[IO] = HttpRoutes.of[IO] {
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

    lazy val getBankLevelDynamicEntities: HttpRoutes[IO] = HttpRoutes.of[IO] {
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

    lazy val getMyDynamicEntities: HttpRoutes[IO] = HttpRoutes.of[IO] {
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

    lazy val createSystemDynamicEntity: HttpRoutes[IO] = HttpRoutes.of[IO] {
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

    lazy val createBankLevelDynamicEntity: HttpRoutes[IO] = HttpRoutes.of[IO] {
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

    lazy val updateSystemDynamicEntity: HttpRoutes[IO] = HttpRoutes.of[IO] {
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

    lazy val updateBankLevelDynamicEntity: HttpRoutes[IO] = HttpRoutes.of[IO] {
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

    lazy val deleteSystemDynamicEntity: HttpRoutes[IO] = HttpRoutes.of[IO] {
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

    lazy val deleteBankLevelDynamicEntity: HttpRoutes[IO] = HttpRoutes.of[IO] {
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

    lazy val updateMyDynamicEntity: HttpRoutes[IO] = HttpRoutes.of[IO] {
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

    lazy val deleteMyDynamicEntity: HttpRoutes[IO] = HttpRoutes.of[IO] {
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

    lazy val createDynamicEndpoint: HttpRoutes[IO] = HttpRoutes.of[IO] {
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

    lazy val createBankLevelDynamicEndpoint: HttpRoutes[IO] = HttpRoutes.of[IO] {
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

    lazy val updateDynamicEndpointHost: HttpRoutes[IO] = HttpRoutes.of[IO] {
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

    lazy val updateBankLevelDynamicEndpointHost: HttpRoutes[IO] = HttpRoutes.of[IO] {
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

    lazy val getDynamicEndpoint: HttpRoutes[IO] = HttpRoutes.of[IO] {
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

    lazy val getDynamicEndpoints: HttpRoutes[IO] = HttpRoutes.of[IO] {
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

    lazy val getBankLevelDynamicEndpoint: HttpRoutes[IO] = HttpRoutes.of[IO] {
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

    lazy val getBankLevelDynamicEndpoints: HttpRoutes[IO] = HttpRoutes.of[IO] {
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

    lazy val deleteDynamicEndpoint: HttpRoutes[IO] = HttpRoutes.of[IO] {
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

    lazy val deleteBankLevelDynamicEndpoint: HttpRoutes[IO] = HttpRoutes.of[IO] {
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

    lazy val getMyDynamicEndpoints: HttpRoutes[IO] = HttpRoutes.of[IO] {
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

    lazy val deleteMyDynamicEndpoint: HttpRoutes[IO] = HttpRoutes.of[IO] {
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

    lazy val getProductAttribute: HttpRoutes[IO] = HttpRoutes.of[IO] {
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

    lazy val getScopes: HttpRoutes[IO] = HttpRoutes.of[IO] {
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

    lazy val addScope: HttpRoutes[IO] = HttpRoutes.of[IO] {
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

    lazy val getConsents: HttpRoutes[IO] = HttpRoutes.of[IO] {
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

    lazy val updateAccountLabel: HttpRoutes[IO] = HttpRoutes.of[IO] {
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

    lazy val getExplicitCounterpartiesForAccount: HttpRoutes[IO] = HttpRoutes.of[IO] {
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

    lazy val getExplicitCounterpartyById: HttpRoutes[IO] = HttpRoutes.of[IO] {
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

    lazy val createExplicitCounterparty: HttpRoutes[IO] = HttpRoutes.of[IO] {
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

    lazy val getFirehoseAccountsAtOneBank: HttpRoutes[IO] = HttpRoutes.of[IO] {
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

    // ─── createTransactionRequest (POST /banks/.../trans-request-types/TYPE/trans-requests → 201) ─
    //
    // v4 supports a wider set of trans-request types than v2.1.0 — and even for the
    // four that overlap (SANDBOX_TAN, COUNTERPARTY, SEPA, FREE_FORM) the v4 response
    // shape differs: it has a `challenges: List[ChallengeJsonV400]` field that the
    // v2.1.0 shape doesn't. The bridge cascade would otherwise route SEPA / COUNTERPARTY
    // / FREE_FORM / SANDBOX_TAN URLs into the v2.1.0 handler and return the v2.1.0 JSON
    // (no `challenges`), failing every TransactionRequestsTest assertion of the form
    // `body.challenges.size != 0`.
    //
    // All v4 types delegate to the same connector helper —
    // `LocalMappedConnectorInternal.createTransactionRequest` — which depends on
    // `SS.user` (Lift's thread-globals). We wrap the call in `SS.init` so the helper's
    // first synchronous read of `SS.user` captures the cc.user, then the Future chain
    // runs normally on any thread.

    lazy val createTransactionRequest: HttpRoutes[IO] = HttpRoutes.of[IO] {
      // GRANT_VIEW_ID in the ResourceDoc URL → middleware skips view validation.
      // Lift's v4 endpoint does no view-access check upfront; it lets
      // `checkAuthorisationToCreateTransactionRequest` inside the connector decide
      // (returns 400 InsufficientAuthorisationToCreateTransactionRequest if the user
      // has neither the role nor view permission). `withViewCreated` would 403 before
      // the connector ran, contradicting the test expectation.
      //
      // The route matches *any* trans-req-type segment (no guard) so:
      //   - v4-supported types route to the connector below.
      //   - Unknown types (e.g. "invalidTransactionRequestType") still hit this route
      //     and get a 400 from the connector's `transactionRequests_supported_types`
      //     check, matching the v210 catch-all behavior the test depends on. Without
      //     this catch, unknown types fall through to Lift → 404.
      //
      // Use `executeFutureCreated` so the response is 201; extract user/bank/account
      // from cc manually (middleware populates them via the BANK_ID and ACCOUNT_ID
      // template segments).
      case req @ POST -> `prefixPath` / "banks" / bankIdStr / "accounts" / accountIdStr / viewIdStr / "transaction-request-types" / transactionRequestTypeStr / "transaction-requests" =>
        implicit val cc: CallContext = req.callContext
        EndpointHelpers.executeFutureCreated(req) {
          val bodyStr = cc.httpBody.getOrElse("")
          for {
            user    <- Future { cc.user.openOrThrowException(AuthenticatedUserIsRequired) }
            bank    <- Future { cc.bank.getOrElse(throw new RuntimeException(BankNotFound)) }
            account <- Future { cc.bankAccount.getOrElse(throw new RuntimeException(BankAccountNotFound)) }
            json <- NewStyle.function.tryons(
              s"$InvalidJsonFormat Empty or invalid request body.", 400, Some(cc)) {
              net.liftweb.json.parse(bodyStr)
            }
            transactionRequestType = TransactionRequestType(transactionRequestTypeStr)
            view <- Future {
              // System views (owner, accountant, etc.) and custom views (e.g. VRP
              // `_vrp-…` views) are stored separately. Try system first; fall back
              // to the account-scoped custom view. SS.init only needs *some* View
              // instance — the connector reads viewId from the parameter, not the
              // View object — so a soft fallback is fine here.
              Views.views.vend.systemView(ViewId(viewIdStr))
                .or(Views.views.vend.customView(ViewId(viewIdStr), BankIdAccountId(account.bankId, account.accountId)))
                .openOrThrowException(s"$ViewNotFound Current view_id($viewIdStr)")
            }
            // SS.init populates Lift thread-globals (used by `SS.user` inside the
            // connector). The connector's first line `SS.user` resolves synchronously
            // inside this block, capturing the user; subsequent flatMap stages run on
            // other threads but the value is already bound.
            innerResult <- APIUtil.SS.init(Full(user), bank, account, view, Some(cc)) {
              code.bankconnectors.LocalMappedConnectorInternal.createTransactionRequest(
                BankId(bankIdStr), AccountId(accountIdStr), ViewId(viewIdStr),
                transactionRequestType, json)
            }
          } yield innerResult._1
        }
    }

    staticResourceDocs += ResourceDoc(
      null, implementedInApiVersion, "createTransactionRequestAccount", "POST",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/GRANT_VIEW_ID/transaction-request-types/TRANSACTION_REQUEST_TYPE/transaction-requests",
      "Create Transaction Request",
      s"""Create a Transaction Request of the type specified in the URL.
         |
         |${userAuthenticationMessage(true)}""",
      EmptyBody, transactionRequestWithChargeJSON400,
      List($AuthenticatedUserIsRequired, InvalidJsonFormat, InvalidNumber, NotPositiveAmount,
        InvalidTransactionRequestType, InvalidISOCurrencyCode,
        InsufficientAuthorisationToCreateTransactionRequest,
        InvalidAccountIdFormat, InvalidBankIdFormat, TransactionDisabled, UnknownError),
      List(apiTagTransactionRequest, apiTagPSD2PIS, apiTagPsd2), None,
      http4sPartialFunction = Some(createTransactionRequest))

    // ─── per-type transaction-request alias ResourceDocs ───────────────────────
    // These 9 Lift `lazy val`s (createTransactionRequestAccountOtp/Sepa/Counterparty
    // /Refund/FreeForm/Simple/AgentCashWithDrawal/Card and the previously-registered
    // createTransactionRequestAccount) all share the same body — call
    // `LocalMappedConnectorInternal.createTransactionRequest`. The already-migrated
    // `createTransactionRequest` http4s route uses a wildcard segment, so adding a
    // ResourceDoc per type (with the literal type segment — recognised in
    // `literalAllCapsSegments`) is enough; no new `lazy val` needed.
    private def initBatch9AliasResourceDocs(): Unit = {
      staticResourceDocs += ResourceDoc(
        null, implementedInApiVersion, "createTransactionRequestAccountOtp", "POST",
        "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/transaction-request-types/ACCOUNT_OTP/transaction-requests",
        "Create Transaction Request (ACCOUNT_OTP)",
        s"""Create Transaction Request (ACCOUNT_OTP).
           |
           |${userAuthenticationMessage(true)}""",
        transactionRequestBodyJsonV200, transactionRequestWithChargeJSON400,
        List($AuthenticatedUserIsRequired, InvalidJsonFormat, InvalidNumber, NotPositiveAmount,
          InvalidTransactionRequestType, InvalidISOCurrencyCode,
          InsufficientAuthorisationToCreateTransactionRequest,
          InvalidAccountIdFormat, InvalidBankIdFormat, TransactionDisabled, UnknownError),
        List(apiTagTransactionRequest, apiTagPSD2PIS, apiTagPsd2), None,
        http4sPartialFunction = Some(createTransactionRequest))

      staticResourceDocs += ResourceDoc(
        null, implementedInApiVersion, "createTransactionRequestSepa", "POST",
        "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/transaction-request-types/SEPA/transaction-requests",
        "Create Transaction Request (SEPA)",
        s"""Create Transaction Request (SEPA).
           |
           |${userAuthenticationMessage(true)}""",
        transactionRequestBodySEPAJsonV400, transactionRequestWithChargeJSON400,
        List($AuthenticatedUserIsRequired, InvalidJsonFormat, InvalidNumber, NotPositiveAmount,
          InvalidTransactionRequestType, InvalidISOCurrencyCode,
          InsufficientAuthorisationToCreateTransactionRequest,
          InvalidAccountIdFormat, InvalidBankIdFormat, TransactionDisabled, UnknownError),
        List(apiTagTransactionRequest, apiTagPSD2PIS, apiTagPsd2), None,
        http4sPartialFunction = Some(createTransactionRequest))

      staticResourceDocs += ResourceDoc(
        null, implementedInApiVersion, "createTransactionRequestCounterparty", "POST",
        "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/transaction-request-types/COUNTERPARTY/transaction-requests",
        "Create Transaction Request (COUNTERPARTY)",
        s"""Create Transaction Request (COUNTERPARTY).
           |
           |${userAuthenticationMessage(true)}""",
        transactionRequestBodyCounterpartyJSON, transactionRequestWithChargeJSON400,
        List($AuthenticatedUserIsRequired, InvalidJsonFormat, InvalidNumber, NotPositiveAmount,
          InvalidTransactionRequestType, InvalidISOCurrencyCode,
          InsufficientAuthorisationToCreateTransactionRequest,
          InvalidAccountIdFormat, InvalidBankIdFormat, TransactionDisabled, UnknownError),
        List(apiTagTransactionRequest, apiTagPSD2PIS, apiTagPsd2), None,
        http4sPartialFunction = Some(createTransactionRequest))

      staticResourceDocs += ResourceDoc(
        null, implementedInApiVersion, "createTransactionRequestRefund", "POST",
        "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/transaction-request-types/REFUND/transaction-requests",
        "Create Transaction Request (REFUND)",
        s"""Create Transaction Request (REFUND).
           |
           |${userAuthenticationMessage(true)}""",
        transactionRequestBodyRefundJsonV400, transactionRequestWithChargeJSON400,
        List($AuthenticatedUserIsRequired, InvalidJsonFormat, InvalidNumber, NotPositiveAmount,
          InvalidTransactionRequestType, InvalidISOCurrencyCode,
          InsufficientAuthorisationToCreateTransactionRequest,
          InvalidAccountIdFormat, InvalidBankIdFormat, TransactionDisabled, UnknownError),
        List(apiTagTransactionRequest, apiTagPSD2PIS, apiTagPsd2), None,
        http4sPartialFunction = Some(createTransactionRequest))

      staticResourceDocs += ResourceDoc(
        null, implementedInApiVersion, "createTransactionRequestFreeForm", "POST",
        "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/transaction-request-types/FREE_FORM/transaction-requests",
        "Create Transaction Request (FREE_FORM)",
        s"""Create Transaction Request (FREE_FORM).
           |
           |${userAuthenticationMessage(true)}""",
        transactionRequestBodyFreeFormJSON, transactionRequestWithChargeJSON400,
        List($AuthenticatedUserIsRequired, InvalidJsonFormat, InvalidNumber, NotPositiveAmount,
          InvalidTransactionRequestType, InvalidISOCurrencyCode,
          InsufficientAuthorisationToCreateTransactionRequest,
          InvalidAccountIdFormat, InvalidBankIdFormat, TransactionDisabled, UnknownError),
        List(apiTagTransactionRequest, apiTagPSD2PIS),
        Some(List(canCreateAnyTransactionRequest)),
        http4sPartialFunction = Some(createTransactionRequest))

      staticResourceDocs += ResourceDoc(
        null, implementedInApiVersion, "createTransactionRequestSimple", "POST",
        "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/transaction-request-types/SIMPLE/transaction-requests",
        "Create Transaction Request (SIMPLE)",
        s"""Create Transaction Request (SIMPLE).
           |
           |${userAuthenticationMessage(true)}""",
        transactionRequestBodySimpleJsonV400, transactionRequestWithChargeJSON400,
        List($AuthenticatedUserIsRequired, InvalidJsonFormat, InvalidNumber, NotPositiveAmount,
          InvalidTransactionRequestType, InvalidISOCurrencyCode,
          InsufficientAuthorisationToCreateTransactionRequest,
          InvalidAccountIdFormat, InvalidBankIdFormat, TransactionDisabled, UnknownError),
        List(apiTagTransactionRequest, apiTagPSD2PIS, apiTagPsd2), None,
        http4sPartialFunction = Some(createTransactionRequest))

      staticResourceDocs += ResourceDoc(
        null, implementedInApiVersion, "createTransactionRequestAgentCashWithDrawal", "POST",
        "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/transaction-request-types/AGENT_CASH_WITHDRAWAL/transaction-requests",
        "Create Transaction Request (AGENT_CASH_WITHDRAWAL)",
        s"""Create Transaction Request (AGENT_CASH_WITHDRAWAL).
           |
           |${userAuthenticationMessage(true)}""",
        transactionRequestBodyAgentJsonV400, transactionRequestWithChargeJSON400,
        List($AuthenticatedUserIsRequired, InvalidJsonFormat, InvalidNumber, NotPositiveAmount,
          InvalidTransactionRequestType, InvalidISOCurrencyCode,
          InsufficientAuthorisationToCreateTransactionRequest,
          InvalidAccountIdFormat, InvalidBankIdFormat, TransactionDisabled, UnknownError),
        List(apiTagTransactionRequest, apiTagPSD2PIS, apiTagPsd2), None,
        http4sPartialFunction = Some(createTransactionRequest))
    }
    initBatch9AliasResourceDocs()

    // createTransactionRequestCard uses a different URL pattern (no bank/account/view)
    // and the Lift body calls the connector with empty BankId/AccountId. Add as its own
    // route + ResourceDoc. The connector reads the user via SS.user, so prime SS with
    // the user only; bank/account/view are connector-resolved from card details.
    lazy val createTransactionRequestCard: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "transaction-request-types" / "CARD" / "transaction-requests" =>
        implicit val cc: CallContext = req.callContext
        EndpointHelpers.executeFutureCreated(req) {
          val bodyStr = cc.httpBody.getOrElse("")
          for {
            user <- Future { cc.user.openOrThrowException(AuthenticatedUserIsRequired) }
            json <- NewStyle.function.tryons(
              s"$InvalidJsonFormat Empty or invalid request body.", 400, Some(cc)) {
              net.liftweb.json.parse(bodyStr)
            }
            transactionRequestType = TransactionRequestType("CARD")
            innerResult <- APIUtil.SS.init(Full(user),
              null.asInstanceOf[Bank], null.asInstanceOf[BankAccount],
              null.asInstanceOf[View], Some(cc)) {
              code.bankconnectors.LocalMappedConnectorInternal.createTransactionRequest(
                BankId(""), AccountId(""), ViewId(Constant.SYSTEM_OWNER_VIEW_ID),
                transactionRequestType, json)
            }
          } yield innerResult._1
        }
    }

    staticResourceDocs += ResourceDoc(
      null, implementedInApiVersion, "createTransactionRequestCard", "POST",
      "/transaction-request-types/CARD/transaction-requests",
      "Create Transaction Request (CARD)",
      s"""Create Transaction Request (CARD).
         |
         |${userAuthenticationMessage(true)}""",
      transactionRequestBodyCardJsonV400, transactionRequestWithChargeJSON400,
      List($AuthenticatedUserIsRequired, InvalidJsonFormat, InvalidNumber, NotPositiveAmount,
        InvalidTransactionRequestType, InvalidISOCurrencyCode,
        InsufficientAuthorisationToCreateTransactionRequest,
        InvalidAccountIdFormat, InvalidBankIdFormat, TransactionDisabled, UnknownError),
      List(apiTagTransactionRequest, apiTagPSD2PIS, apiTagPsd2), None,
      http4sPartialFunction = Some(createTransactionRequestCard))

    // ─── answerTransactionRequestChallenge (POST .../trans-requests/{id}/challenge → 202) ─
    //
    // v4 needs its own handling for this endpoint because the v2.1.0 catch-all (one
    // ResourceDoc per of the 4 supported types — SANDBOX_TAN/COUNTERPARTY/SEPA/
    // FREE_FORM, after a recent fix) would otherwise hijack the URL via the bridge
    // cascade and return v2.1.0's 400 because it doesn't recognize
    // `ChallengeAnswerJson400`. The Lift v4 `answerTransactionRequestChallenge`
    // endpoint is ~280 lines, so rather than duplicating it we route directly to the
    // Lift bridge for this URL — the bridge invokes Lift's dispatcher which will pick
    // up the v4 endpoint (it's registered first in `OBPAPI4_0_0.routes` via
    // `endpointsOf4_0_0`).
    //
    // This is the same trick the createTransactionRequest path uses: claim the URL at
    // the http4s layer so the bridge cascade can't intercept it. The difference is we
    // delegate the body to Lift unchanged.

    lazy val answerTransactionRequestChallenge: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "banks" / _ / "accounts" / _ / _ / "transaction-request-types" / _ / "transaction-requests" / _ / "challenge" =>
        code.api.util.http4s.Http4sLiftWebBridge.dispatch(req)
    }

    staticResourceDocs += ResourceDoc(
      null, implementedInApiVersion, "answerTransactionRequestChallenge", "POST",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/GRANT_VIEW_ID/transaction-request-types/TRANSACTION_REQUEST_TYPE/transaction-requests/TRANSACTION_REQUEST_ID/challenge",
      "Answer Transaction Request Challenge",
      s"""In Sandbox mode, any string that can be converted to a positive integer will be accepted as an answer.
         |
         |${userAuthenticationMessage(true)}""",
      challengeAnswerJson400, transactionRequestWithChargeJSON400,
      List($AuthenticatedUserIsRequired, InvalidJsonFormat, InvalidBankIdFormat,
        InvalidAccountIdFormat, InvalidTransactionRequestChallengeId,
        AllowedAttemptsUsedUp, TransactionRequestStatusNotInitiatedOrPendingOrForwarded,
        TransactionRequestTypeHasChanged, UnknownError),
      List(apiTagTransactionRequest, apiTagPSD2PIS, apiTagPsd2), None,
      http4sPartialFunction = Some(answerTransactionRequestChallenge))

    // ═══════════════════════════════════════════════════════════════════════════
    // Batch 1 — simple GETs (mostly mechanical)
    // ═══════════════════════════════════════════════════════════════════════════

    lazy val getCallContext: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "development" / "call_context" =>
        EndpointHelpers.withUser(req) { (_, cc) =>
          Future(cc)
        }
    }

    lazy val verifyRequestSignResponse: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "development" / "echo" / "jws-verified-request-jws-signed-response" =>
        EndpointHelpers.withUser(req) { (_, cc) =>
          Future(cc)
        }
    }

    lazy val getCurrentUserId: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "users" / "current" / "user_id" =>
        EndpointHelpers.withUser(req) { (user, _) =>
          Future(JSONFactory400.createUserIdInfoJson(user))
        }
    }

    lazy val getScannedApiVersions: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "api" / "versions" =>
        EndpointHelpers.executeAndRespond(req) { _ =>
          Future {
            val versions: List[ScannedApiVersion] =
              ApiVersion.allScannedApiVersion.asScala.toList.filter { v =>
                v.urlPrefix.trim.nonEmpty && APIUtil.versionIsAllowed(v)
              }
            com.openbankproject.commons.model.ListResult("scanned_api_versions", versions)
          }
        }
    }

    lazy val getMySpaces: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "my" / "spaces" =>
        EndpointHelpers.withUser(req) { (user, cc) =>
          for {
            entitlements <- NewStyle.function.getEntitlementsByUserId(user.userId, Some(cc))
          } yield MySpaces(
            entitlements
              .filter(_.roleName == canReadDynamicResourceDocsAtOneBank.toString())
              .map(_.bankId)
          )
        }
    }

    lazy val getBankAttributes: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / _ / "attributes" =>
        EndpointHelpers.withBank(req) { (bank, cc) =>
          for {
            (attributes, _) <- NewStyle.function.getBankAttributesByBank(bank.bankId, Some(cc))
          } yield JSONFactory400.createBankAttributesJson(attributes)
        }
    }

    lazy val getBankAttribute: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / _ / "attributes" / bankAttributeId =>
        EndpointHelpers.withBank(req) { (_, cc) =>
          for {
            (attribute, _) <- NewStyle.function.getBankAttributeById(bankAttributeId, Some(cc))
          } yield JSONFactory400.createBankAttributeJson(attribute)
        }
    }

    lazy val getSystemLevelEndpointTags: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "management" / "endpoints" / operationId / "tags" =>
        EndpointHelpers.withUser(req) { (_, cc) =>
          for {
            (endpointTags, _) <- NewStyle.function.getSystemLevelEndpointTags(operationId, Some(cc))
          } yield endpointTags.map(e =>
            SystemLevelEndpointTagResponseJson400(
              e.endpointTagId.getOrElse(""), e.operationId, e.tagName))
        }
    }

    lazy val getBankLevelEndpointTags: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "management" / "banks" / bankIdStr / "endpoints" / operationId / "tags" =>
        EndpointHelpers.withUserAndBank(req) { (_, _, cc) =>
          for {
            (endpointTags, _) <- NewStyle.function.getBankLevelEndpointTags(bankIdStr, operationId, Some(cc))
          } yield endpointTags.map(e =>
            BankLevelEndpointTagResponseJson400(
              e.bankId.getOrElse(""), e.endpointTagId.getOrElse(""), e.operationId, e.tagName))
        }
    }

    private def getEndpointMappingsMethodHttp4s(bankId: Option[String], cc: CallContext): Future[com.openbankproject.commons.model.ListResult[List[JValue]]] =
      for {
        (endpointMappings, _) <- NewStyle.function.getEndpointMappings(bankId, Some(cc))
      } yield {
        val listCommons: List[EndpointMappingCommons] = endpointMappings
        com.openbankproject.commons.model.ListResult("endpoint-mappings", listCommons.map(_.toJson))
      }

    private def getEndpointMappingMethodHttp4s(bankId: Option[String], endpointMappingId: String, cc: CallContext): Future[JValue] =
      for {
        (endpointMapping, _) <- NewStyle.function.getEndpointMappingById(bankId, endpointMappingId, Some(cc))
      } yield {
        val commonsData: EndpointMappingCommons = endpointMapping
        commonsData.toJson
      }

    lazy val getEndpointMapping: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "management" / "endpoint-mappings" / endpointMappingId =>
        EndpointHelpers.withUser(req) { (_, cc) =>
          getEndpointMappingMethodHttp4s(None, endpointMappingId, cc)
        }
    }

    lazy val getBankLevelEndpointMapping: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "management" / "banks" / _ / "endpoint-mappings" / endpointMappingId =>
        EndpointHelpers.withUserAndBank(req) { (_, bank, cc) =>
          getEndpointMappingMethodHttp4s(Some(bank.bankId.value), endpointMappingId, cc)
        }
    }

    lazy val getAllEndpointMappings: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "management" / "endpoint-mappings" =>
        EndpointHelpers.withUser(req) { (_, cc) =>
          getEndpointMappingsMethodHttp4s(None, cc)
        }
    }

    lazy val getAllBankLevelEndpointMappings: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "management" / "banks" / _ / "endpoint-mappings" =>
        EndpointHelpers.withUserAndBank(req) { (_, bank, cc) =>
          getEndpointMappingsMethodHttp4s(Some(bank.bankId.value), cc)
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Batch 8 — Counterparty management endpoints
    // ═══════════════════════════════════════════════════════════════════════════

    lazy val getCounterpartiesForAnyAccount: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "management" / "banks" / _ / "accounts" / _ / viewIdStr / "counterparties" =>
        EndpointHelpers.withBankAccount(req) { (_, account, cc) =>
          for {
            (counterparties, _) <- NewStyle.function.getCounterparties(
              account.bankId, account.accountId, ViewId(viewIdStr), Some(cc))
            _ <- code.util.Helper.booleanToFuture(
              CreateOrUpdateCounterpartyMetadataError, failCode = 400, cc = Some(cc)) {
              counterparties.forall { c =>
                code.metadata.counterparties.Counterparties.counterparties.vend.getOrCreateMetadata(
                  account.bankId, account.accountId, c.counterpartyId, c.name) match {
                  case Full(_) => true
                  case _       => false
                }
              }
            }
          } yield JSONFactory400.createCounterpartiesJson400(counterparties)
        }
    }

    lazy val getCounterpartyByIdForAnyAccount: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "management" / "banks" / _ / "accounts" / _ / _ / "counterparties" / counterpartyIdStr =>
        EndpointHelpers.withBankAccount(req) { (_, account, cc) =>
          for {
            (counterparty, _) <- NewStyle.function.getCounterpartyByCounterpartyId(
              CounterpartyId(counterpartyIdStr), Some(cc))
            counterpartyMetadata <- NewStyle.function.getMetadata(
              account.bankId, account.accountId, counterpartyIdStr, Some(cc))
          } yield JSONFactory400.createCounterpartyWithMetadataJson400(counterparty, counterpartyMetadata)
        }
    }

    lazy val getCounterpartyByNameForAnyAccount: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "management" / "banks" / _ / "accounts" / _ / viewIdStr / "counterparty-names" / counterpartyName =>
        EndpointHelpers.withBankAccount(req) { (_, account, cc) =>
          for {
            (counterpartyList, _) <- code.bankconnectors.Connector.connector.vend
              .checkCounterpartyExists(counterpartyName, account.bankId.value,
                account.accountId.value, viewIdStr, Some(cc))
            counterparty <- NewStyle.function.tryons(
              CounterpartyNotFound.replace(
                "The BANK_ID / ACCOUNT_ID specified does not exist on this server.",
                s"COUNTERPARTY_NAME($counterpartyName) for the BANK_ID(${account.bankId.value}) and ACCOUNT_ID(${account.accountId.value}) and VIEW_ID($viewIdStr)"),
              400, Some(cc)) { counterpartyList.head }
            (counterpartyMetadata, _) <- NewStyle.function.getOrCreateMetadata(
              account.bankId, account.accountId, counterparty.counterpartyId, counterparty.name, Some(cc))
          } yield JSONFactory400.createCounterpartyWithMetadataJson400(counterparty, counterpartyMetadata)
        }
    }

    private def initBatch8ResourceDocs(): Unit = {
      staticResourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(getCounterpartiesForAnyAccount), "GET",
        "/management/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/counterparties",
        "Get Counterparties for any account (Explicit)",
        s"""Get Counterparties for any account.""".stripMargin,
        EmptyBody, counterpartiesJson400,
        List($AuthenticatedUserIsRequired, $BankNotFound, $BankAccountNotFound,
          ViewNotFound, CreateOrUpdateCounterpartyMetadataError, UnknownError),
        List(apiTagCounterparty, apiTagAccount),
        Some(List(canGetCounterpartiesAtAnyBank, canGetCounterparties)),
        http4sPartialFunction = Some(getCounterpartiesForAnyAccount))

      staticResourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(getCounterpartyByIdForAnyAccount), "GET",
        "/management/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/counterparties/COUNTERPARTY_ID_PARAM",
        "Get Counterparty by Id for any account (Explicit)",
        s"""Get Counterparty by COUNTERPARTY_ID.""".stripMargin,
        EmptyBody, counterpartyWithMetadataJson400,
        List($AuthenticatedUserIsRequired, InvalidAccountIdFormat, InvalidBankIdFormat,
          $BankNotFound, $BankAccountNotFound, InvalidJsonFormat, ViewNotFound, UnknownError),
        List(apiTagCounterparty, apiTagAccount),
        Some(List(canGetCounterpartyAtAnyBank, canGetCounterparty)),
        http4sPartialFunction = Some(getCounterpartyByIdForAnyAccount))

      staticResourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(getCounterpartyByNameForAnyAccount), "GET",
        "/management/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/counterparty-names/COUNTERPARTY_NAME",
        "Get Counterparty by name for any account (Explicit)",
        s"""Get Counterparty by COUNTERPARTY_NAME.""".stripMargin,
        EmptyBody, counterpartyWithMetadataJson400,
        List($AuthenticatedUserIsRequired, InvalidAccountIdFormat, InvalidBankIdFormat,
          $BankNotFound, $BankAccountNotFound, InvalidJsonFormat, ViewNotFound,
          CounterpartyNotFound, UnknownError),
        List(apiTagCounterparty, apiTagAccount),
        Some(List(canGetCounterpartyAtAnyBank, canGetCounterparty)),
        http4sPartialFunction = Some(getCounterpartyByNameForAnyAccount))
    }
    initBatch8ResourceDocs()

    // ═══════════════════════════════════════════════════════════════════════════
    // Batch 9 — Remaining v4 migrations
    // ═══════════════════════════════════════════════════════════════════════════

    // ─── DELETE family ────────────────────────────────────────────────────────
    // Most v4 DELETEs return 200 with body (Lift `HttpCode.\`200\``); use the
    // non-Delete helpers.

    lazy val deleteExplicitCounterparty: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ DELETE -> `prefixPath` / "banks" / bankIdStr / "accounts" / accountIdStr / viewIdStr / "counterparties" / counterpartyIdStr =>
        EndpointHelpers.withView(req) { (_, account, view, cc) =>
          for {
            _ <- code.util.Helper.booleanToFuture(InvalidAccountIdFormat, cc = Some(cc)) { isValidID(accountIdStr) }
            _ <- code.util.Helper.booleanToFuture(InvalidBankIdFormat, cc = Some(cc)) { isValidID(bankIdStr) }
            _ <- code.util.Helper.booleanToFuture(
              s"$NoViewPermission can_delete_counterparty. Please use a view with that permission or add the permission to this view.",
              failCode = 403, cc = Some(cc)) {
              view.allowed_actions.exists(_ == code.api.Constant.CAN_DELETE_COUNTERPARTY)
            }
            (counterparty, _) <- NewStyle.function.deleteCounterpartyByCounterpartyId(
              CounterpartyId(counterpartyIdStr), Some(cc))
            _ <- NewStyle.function.deleteMetadata(
              account.bankId, account.accountId, counterpartyIdStr, Some(cc))
          } yield counterparty
        }
    }

    lazy val deleteCounterpartyForAnyAccount: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ DELETE -> `prefixPath` / "management" / "banks" / bankIdStr / "accounts" / accountIdStr / _ / "counterparties" / counterpartyIdStr =>
        EndpointHelpers.withBankAccount(req) { (_, account, cc) =>
          for {
            _ <- code.util.Helper.booleanToFuture(InvalidAccountIdFormat, cc = Some(cc)) { isValidID(accountIdStr) }
            _ <- code.util.Helper.booleanToFuture(InvalidBankIdFormat, cc = Some(cc)) { isValidID(bankIdStr) }
            (counterparty, _) <- NewStyle.function.deleteCounterpartyByCounterpartyId(
              CounterpartyId(counterpartyIdStr), Some(cc))
            _ <- NewStyle.function.deleteMetadata(
              account.bankId, account.accountId, counterpartyIdStr, Some(cc))
          } yield counterparty
        }
    }

    lazy val deleteTagForViewOnAccount: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ DELETE -> `prefixPath` / "banks" / _ / "accounts" / _ / viewIdStr / "metadata" / "tags" / tagId =>
        EndpointHelpers.withView(req) { (_, _, view, cc) =>
          for {
            _ <- code.util.Helper.booleanToFuture(
              s"$NoViewPermission can_delete_tag. Current ViewId($viewIdStr)",
              cc = Some(cc)) {
              view.allowed_actions.exists(_ == code.api.Constant.CAN_DELETE_TAG)
            }
            account = cc.bankAccount.getOrElse(throw new RuntimeException(BankAccountNotFound))
            deleted <- Future(
              code.metadata.tags.Tags.tags.vend.deleteTagOnAccount(account.bankId, account.accountId)(tagId))
          } yield deleted
        }
    }

    lazy val getTagsForViewOnAccount: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / _ / "accounts" / _ / viewIdStr / "metadata" / "tags" =>
        EndpointHelpers.withView(req) { (_, account, view, cc) =>
          for {
            _ <- code.util.Helper.booleanToFuture(
              s"$NoViewPermission can_see_tags. Current ViewId($viewIdStr)",
              cc = Some(cc)) {
              view.allowed_actions.exists(_ == code.api.Constant.CAN_SEE_TAGS)
            }
            tags <- Future(
              code.metadata.tags.Tags.tags.vend.getTagsOnAccount(account.bankId, account.accountId)(ViewId(viewIdStr)))
          } yield JSONFactory400.createAccountTagsJSON(tags)
        }
    }

    lazy val addTagForViewOnAccount: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "banks" / _ / "accounts" / _ / viewIdStr / "metadata" / "tags" =>
        implicit val cc: CallContext = req.callContext
        EndpointHelpers.executeFutureCreated(req) {
          val bodyStr = cc.httpBody.getOrElse("")
          for {
            user <- Future { cc.user.openOrThrowException(AuthenticatedUserIsRequired) }
            account <- Future { cc.bankAccount.getOrElse(throw new RuntimeException(BankAccountNotFound)) }
            view <- Future { cc.view.getOrElse(throw new RuntimeException(s"$ViewNotFound Current ViewId($viewIdStr)")) }
            _ <- code.util.Helper.booleanToFuture(
              s"$NoViewPermission can_add_tag. Current ViewId($viewIdStr)",
              cc = Some(cc)) {
              view.allowed_actions.exists(_ == code.api.Constant.CAN_ADD_TAG)
            }
            tagJson <- NewStyle.function.tryons(
              s"$InvalidJsonFormat The Json body should be the ${classOf[code.api.v1_2_1.PostTransactionTagJSON].getSimpleName} ",
              400, Some(cc)) {
              net.liftweb.json.parse(bodyStr).extract[code.api.v1_2_1.PostTransactionTagJSON]
            }
            postedTag <- Future(
              code.metadata.tags.Tags.tags.vend.addTagOnAccount(account.bankId, account.accountId)(
                user.userPrimaryKey, ViewId(viewIdStr), tagJson.value, new java.util.Date())
            ) map { box => unboxFullOrFail(box, Some(cc), "OBP-50000: Unknown Error.", 400) }
          } yield JSONFactory400.createAccountTagJSON(postedTag)
        }
    }

    // ─── simpler GETs ────────────────────────────────────────────────────────

    lazy val getDoubleEntryTransaction: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / bankIdStr / "accounts" / accountIdStr / _ / "transactions" / transactionIdStr / "double-entry-transaction" =>
        EndpointHelpers.withView(req) { (_, _, _, cc) =>
          for {
            (_, _) <- NewStyle.function.getTransaction(
              BankId(bankIdStr), AccountId(accountIdStr), TransactionId(transactionIdStr), Some(cc))
            (doubleEntryTransaction, _) <- NewStyle.function.getDoubleEntryBookTransaction(
              BankId(bankIdStr), AccountId(accountIdStr), TransactionId(transactionIdStr), Some(cc))
          } yield JSONFactory400.createDoubleEntryTransactionJson(doubleEntryTransaction)
        }
    }

    lazy val getBalancingTransaction: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "transactions" / transactionIdStr / "balancing-transaction" =>
        EndpointHelpers.withUser(req) { (user, cc) =>
          for {
            (doubleEntryTransaction, _) <- NewStyle.function.getBalancingTransaction(
              TransactionId(transactionIdStr), Some(cc))
            _ <- ViewNewStyle.checkBalancingTransactionAccountAccessAndReturnView(
              doubleEntryTransaction, Full(user), Some(cc))
          } yield JSONFactory400.createDoubleEntryTransactionJson(doubleEntryTransaction)
        }
    }

    lazy val getBankAccountBalancesForCurrentUser: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / _ / "accounts" / accountIdStr / "balances" =>
        EndpointHelpers.withUserAndBank(req) { (user, bank, cc) =>
          for {
            (allowedAccounts, _) <- code.api.util.newstyle.BalanceNewStyle.getAccountAccessAtBank(
              user, bank.bankId, Some(cc))
            msg = s"$CannotFindAccountAccess AccountId(${accountIdStr})"
            bankIdAccountId <- NewStyle.function.tryons(msg, 400, Some(cc)) {
              allowedAccounts.find(_.accountId == AccountId(accountIdStr)).get
            }
            (accountBalances, _) <- code.api.util.newstyle.BalanceNewStyle.getBankAccountBalances(
              bankIdAccountId, Some(cc))
          } yield JSONFactory400.createAccountBalancesJson(accountBalances)
        }
    }

    lazy val getAccountByAccountRouting: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "management" / "accounts" / "account-routing-query" =>
        EndpointHelpers.withUserAndBody[BankAccountRoutingJson, Any](req) { (user, postJson, cc) =>
          for {
            (account, _) <- NewStyle.function.getBankAccountByRouting(
              postJson.bank_id.map(BankId(_)),
              postJson.account_routing.scheme,
              postJson.account_routing.address,
              Some(cc))
            view <- ViewNewStyle.checkOwnerViewAccessAndReturnOwnerView(
              user, BankIdAccountId(account.bankId, account.accountId), Some(cc))
            moderatedAccount <- NewStyle.function.moderatedBankAccountCore(
              account, view, Full(user), Some(cc))
            (accountAttributes, _) <- NewStyle.function.getAccountAttributesByAccount(
              account.bankId, account.accountId, Some(cc))
          } yield {
            val availableViews = Views.views.vend.privateViewsUserCanAccessForAccount(
              user, BankIdAccountId(account.bankId, account.accountId))
            val viewsAvailable = availableViews
              .map(code.api.v1_2_1.JSONFactory.createViewJSON).sortBy(_.short_name)
            val tags = code.metadata.tags.Tags.tags.vend
              .getTagsOnAccount(account.bankId, account.accountId)(view.viewId)
            createBankAccountJSON(moderatedAccount, viewsAvailable, accountAttributes, tags)
          }
        }
    }

    lazy val getAccountsByAccountRoutingRegex: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "management" / "accounts" / "account-routing-regex-query" =>
        EndpointHelpers.withUserAndBody[BankAccountRoutingJson, Any](req) { (user, postJson, cc) =>
          for {
            (accountRoutings, _) <- NewStyle.function.getAccountRoutingsByScheme(
              postJson.bank_id.map(BankId(_)),
              postJson.account_routing.scheme,
              Some(cc))
            accountRoutingAddressRegex = postJson.account_routing.address.r
            filteredAccountRoutings = accountRoutings.filter(accountRouting =>
              accountRoutingAddressRegex.findFirstIn(accountRouting.accountRouting.address).isDefined)
            accountsJson <- Future.sequence(
              filteredAccountRoutings.map(accountRouting =>
                for {
                  (account, _) <- NewStyle.function.getBankAccount(
                    accountRouting.bankId, accountRouting.accountId, Some(cc))
                  view <- ViewNewStyle.checkOwnerViewAccessAndReturnOwnerView(
                    user, BankIdAccountId(account.bankId, account.accountId), Some(cc))
                  moderatedAccount <- NewStyle.function.moderatedBankAccountCore(
                    account, view, Full(user), Some(cc))
                  (accountAttributes, _) <- NewStyle.function.getAccountAttributesByAccount(
                    account.bankId, account.accountId, Some(cc))
                  availableViews = Views.views.vend.privateViewsUserCanAccessForAccount(
                    user, BankIdAccountId(account.bankId, account.accountId))
                  viewsAvailable = availableViews
                    .map(code.api.v1_2_1.JSONFactory.createViewJSON).sortBy(_.short_name)
                  tags = code.metadata.tags.Tags.tags.vend
                    .getTagsOnAccount(account.bankId, account.accountId)(view.viewId)
                } yield createBankAccountJSON(
                  moderatedAccount, viewsAvailable, accountAttributes, tags)))
          } yield ModeratedAccountsJSON400(accountsJson)
        }
    }

    // ─── lockUser / resetPasswordUrl ─────────────────────────────────────────

    lazy val lockUser: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "users" / username / "locks" =>
        EndpointHelpers.withUser(req) { (_, cc) =>
          for {
            userLocks <- Future {
              code.userlocks.UserLocksProvider.lockUser(
                Constant.localIdentityProvider, username)
            } map { box =>
              unboxFullOrFail(box, Some(cc),
                s"$UserNotFoundByProviderAndUsername($username)", 404)
            }
          } yield JSONFactory400.createUserLockStatusJson(userLocks)
        }
    }

    lazy val resetPasswordUrl: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "management" / "user" / "reset-password-url" =>
        EndpointHelpers.withUserAndBodyCreated[PostResetPasswordUrlJsonV400, ResetPasswordUrlJsonV400](req) {
          (_, postedData, cc) =>
            for {
              _ <- code.util.Helper.booleanToFuture(
                failMsg = NotAllowedEndpoint, cc = Some(cc)) {
                APIUtil.getPropsAsBoolValue("ResetPasswordUrlEnabled", false)
              }
              resetLink = AuthUser.passwordResetUrl(
                postedData.username, postedData.email, postedData.user_id)
            } yield ResetPasswordUrlJsonV400(resetLink)
        }
    }

    // ─── settlement-accounts ────────────────────────────────────────────────

    lazy val getSettlementAccounts: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / bankIdStr / "settlement-accounts" =>
        EndpointHelpers.withUserAndBank(req) { (user, bank, cc) =>
          for {
            _ <- NewStyle.function.hasEntitlement(
              bankIdStr, user.userId, canGetSettlementAccountAtOneBank, Some(cc))
            (accounts, _) <- NewStyle.function.getBankSettlementAccounts(bank.bankId, Some(cc))
            settlementAccounts <- Future.sequence(accounts.map { account =>
              NewStyle.function.getAccountAttributesByAccount(
                bank.bankId, account.accountId, Some(cc)
              ).map { case (accountAttributes, _) =>
                JSONFactory400.getSettlementAccountJson(account, accountAttributes)
              }
            })
          } yield SettlementAccountsJson(settlementAccounts)
        }
    }

    private def initBatch9ResourceDocs(): Unit = {
      staticResourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(deleteExplicitCounterparty), "DELETE",
        "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/counterparties/COUNTERPARTY_ID_PARAM",
        "Delete Counterparty (Explicit)",
        s"""Delete Counterparty (Explicit).
           |
           |${userAuthenticationMessage(true)}""",
        EmptyBody, EmptyBody,
        List($AuthenticatedUserIsRequired, $BankAccountNotFound, $BankNotFound,
          InvalidAccountIdFormat, InvalidBankIdFormat, NoViewPermission, UnknownError),
        List(apiTagCounterparty, apiTagAccount), None,
        http4sPartialFunction = Some(deleteExplicitCounterparty))

      staticResourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(deleteCounterpartyForAnyAccount), "DELETE",
        "/management/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/counterparties/COUNTERPARTY_ID",
        "Delete Counterparty for any account (Explicit)",
        s"""Delete Counterparty for any account.
           |
           |${userAuthenticationMessage(true)}""",
        EmptyBody, EmptyBody,
        List($AuthenticatedUserIsRequired, $BankAccountNotFound, $BankNotFound,
          InvalidAccountIdFormat, InvalidBankIdFormat, UserHasMissingRoles, UnknownError),
        List(apiTagCounterparty, apiTagAccount),
        Some(List(canDeleteCounterparty, canDeleteCounterpartyAtAnyBank)),
        http4sPartialFunction = Some(deleteCounterpartyForAnyAccount))

      staticResourceDocs += ResourceDoc(
        null, implementedInApiVersion, "deleteTagForViewOnAccount", "DELETE",
        "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/metadata/tags/TAG_ID",
        "Delete a tag on account",
        s"""Delete a tag on account.
           |
           |${userAuthenticationMessage(true)}""",
        EmptyBody, EmptyBody,
        List(NoViewPermission, ViewNotFound, $AuthenticatedUserIsRequired,
          $BankNotFound, $BankAccountNotFound, $UserNoPermissionAccessView, UnknownError),
        List(apiTagAccountMetadata, apiTagAccount), None,
        http4sPartialFunction = Some(deleteTagForViewOnAccount))

      staticResourceDocs += ResourceDoc(
        null, implementedInApiVersion, "getTagsForViewOnAccount", "GET",
        "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/metadata/tags",
        "Get tags on account",
        s"""Get tags on account.
           |
           |${userAuthenticationMessage(true)}""",
        EmptyBody, accountTagsJSON,
        List($AuthenticatedUserIsRequired, $BankNotFound, $BankAccountNotFound,
          NoViewPermission, $UserNoPermissionAccessView, UnknownError),
        List(apiTagAccountMetadata, apiTagAccount), None,
        http4sPartialFunction = Some(getTagsForViewOnAccount))

      staticResourceDocs += ResourceDoc(
        null, implementedInApiVersion, "addTagForViewOnAccount", "POST",
        "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/metadata/tags",
        "Create a tag on account",
        s"""Create a tag on account.
           |
           |${userAuthenticationMessage(true)}""",
        code.api.v1_2_1.PostTransactionTagJSON("tag-value-example"),
        accountTagJSON,
        List($AuthenticatedUserIsRequired, $BankNotFound, $BankAccountNotFound,
          InvalidJsonFormat, NoViewPermission, $UserNoPermissionAccessView, UnknownError),
        List(apiTagAccountMetadata, apiTagAccount), None,
        http4sPartialFunction = Some(addTagForViewOnAccount))

      staticResourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(getDoubleEntryTransaction), "GET",
        "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/transactions/TRANSACTION_ID/double-entry-transaction",
        "Get Double Entry Transaction",
        s"""Get Double Entry Transaction.
           |
           |${userAuthenticationMessage(true)}""",
        EmptyBody, doubleEntryTransactionJson,
        List($AuthenticatedUserIsRequired, $BankNotFound, $BankAccountNotFound,
          $UserNoPermissionAccessView, InvalidJsonFormat, UnknownError),
        List(apiTagTransaction),
        Some(List(canGetDoubleEntryTransactionAtAnyBank, canGetDoubleEntryTransactionAtOneBank)),
        http4sPartialFunction = Some(getDoubleEntryTransaction))

      staticResourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(getBalancingTransaction), "GET",
        "/transactions/TRANSACTION_ID/balancing-transaction",
        "Get Balancing Transaction",
        s"""Get Balancing Transaction.
           |
           |${userAuthenticationMessage(true)}""",
        EmptyBody, doubleEntryTransactionJson,
        List($AuthenticatedUserIsRequired, InvalidJsonFormat, UnknownError),
        List(apiTagTransaction), Some(List()),
        http4sPartialFunction = Some(getBalancingTransaction))

      staticResourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(getBankAccountBalancesForCurrentUser), "GET",
        "/banks/BANK_ID/accounts/ACCOUNT_ID/balances",
        "Get Account Balances",
        """Get the Balances for one Account of the current User at one bank.""",
        EmptyBody, accountBalanceV400,
        List($AuthenticatedUserIsRequired, $BankNotFound, CannotFindAccountAccess, UnknownError),
        apiTagAccount :: apiTagPSD2AIS :: apiTagPsd2 :: Nil, None,
        http4sPartialFunction = Some(getBankAccountBalancesForCurrentUser))

      staticResourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(getAccountByAccountRouting), "POST",
        "/management/accounts/account-routing-query",
        "Get Account by Account Routing",
        """Get Account by Account Routing.""",
        bankAccountRoutingJson, moderatedAccountJSON400,
        List($AuthenticatedUserIsRequired, $BankNotFound, $BankAccountNotFound,
          $UserNoPermissionAccessView, UnknownError),
        List(apiTagAccount), None,
        http4sPartialFunction = Some(getAccountByAccountRouting))

      staticResourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(getAccountsByAccountRoutingRegex), "POST",
        "/management/accounts/account-routing-regex-query",
        "Get Accounts by Account Routing Regex",
        """Get Accounts by Account Routing Regex.""",
        bankAccountRoutingJson, moderatedAccountsJSON400,
        List($AuthenticatedUserIsRequired, $BankNotFound, $BankAccountNotFound,
          $UserNoPermissionAccessView, UnknownError),
        List(apiTagAccount), None,
        http4sPartialFunction = Some(getAccountsByAccountRoutingRegex))

      staticResourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(lockUser), "POST",
        "/users/USERNAME/locks",
        "Lock the user",
        s"""Lock a User.
           |
           |${userAuthenticationMessage(true)}""",
        EmptyBody, userLockStatusJson,
        List($AuthenticatedUserIsRequired, UserNotFoundByProviderAndUsername,
          UserHasMissingRoles, UnknownError),
        List(apiTagUser),
        Some(List(canLockUser)),
        http4sPartialFunction = Some(lockUser))

      staticResourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(resetPasswordUrl), "POST",
        "/management/user/reset-password-url",
        "Create password reset url",
        s"""Create password reset url.""",
        PostResetPasswordUrlJsonV400("jobloggs", "jo@gmail.com", "74a8ebcc-10e4-4036-bef3-9835922246bf"),
        ResetPasswordUrlJsonV400("https://apisandbox.openbankproject.com/user_mgt/reset_password/QOL1CPNJPCZ4BRMPX3Z01DPOX1HMGU3L"),
        List($AuthenticatedUserIsRequired, UserHasMissingRoles, InvalidJsonFormat, UnknownError),
        List(apiTagUser),
        Some(List(canCreateResetPasswordUrl)),
        http4sPartialFunction = Some(resetPasswordUrl))

      staticResourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(getSettlementAccounts), "GET",
        "/banks/BANK_ID/settlement-accounts",
        "Get Settlement accounts at Bank",
        """Get Settlement accounts at Bank.""",
        EmptyBody, settlementAccountsJson,
        List($AuthenticatedUserIsRequired, UserHasMissingRoles, $BankNotFound, UnknownError),
        List(apiTagBank, apiTagPsd2),
        Some(List(canGetSettlementAccountAtOneBank)),
        http4sPartialFunction = Some(getSettlementAccounts))
    }
    initBatch9ResourceDocs()

    // ═══════════════════════════════════════════════════════════════════════════
    // Batch 10 — Attribute endpoints (Bank/Customer/Transaction/TransactionRequest/ProductFee)
    // ═══════════════════════════════════════════════════════════════════════════

    // ─── Bank Attribute ─────────────────────────────────────────────────────

    lazy val createBankAttribute: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "banks" / bankIdStr / "attribute" =>
        EndpointHelpers.withUserAndBankAndBodyCreated[BankAttributeJsonV400, Any](req) { (_, _, postedData, cc) =>
          for {
            attrType <- NewStyle.function.tryons(
              s"$InvalidJsonFormat The `Type` field can only accept the following field: " +
                s"${com.openbankproject.commons.model.enums.BankAttributeType.DOUBLE}(12.1234), ${com.openbankproject.commons.model.enums.BankAttributeType.STRING}(TAX_NUMBER), ${com.openbankproject.commons.model.enums.BankAttributeType.INTEGER}(123) and ${com.openbankproject.commons.model.enums.BankAttributeType.DATE_WITH_DAY}(2012-04-23)",
              400, Some(cc)) {
              com.openbankproject.commons.model.enums.BankAttributeType.withName(postedData.`type`)
            }
            (bankAttribute, _) <- NewStyle.function.createOrUpdateBankAttribute(
              BankId(bankIdStr), None, postedData.name, attrType, postedData.value,
              postedData.is_active, Some(cc))
          } yield createBankAttributeJson(bankAttribute)
        }
    }

    lazy val updateBankAttribute: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ PUT -> `prefixPath` / "banks" / bankIdStr / "attributes" / bankAttributeId =>
        EndpointHelpers.withUserAndBankAndBody[BankAttributeJsonV400, Any](req) { (user, _, postedData, cc) =>
          for {
            _ <- NewStyle.function.hasEntitlement(
              bankIdStr, user.userId, canUpdateBankAttribute, Some(cc))
            attrType <- NewStyle.function.tryons(
              s"$InvalidJsonFormat The `Type` field can only accept the following field: " +
                s"${com.openbankproject.commons.model.enums.BankAttributeType.DOUBLE}(12.1234), ${com.openbankproject.commons.model.enums.BankAttributeType.STRING}(TAX_NUMBER), ${com.openbankproject.commons.model.enums.BankAttributeType.INTEGER}(123) and ${com.openbankproject.commons.model.enums.BankAttributeType.DATE_WITH_DAY}(2012-04-23)",
              400, Some(cc)) {
              com.openbankproject.commons.model.enums.BankAttributeType.withName(postedData.`type`)
            }
            (_, _) <- NewStyle.function.getBankAttributeById(bankAttributeId, Some(cc))
            (bankAttribute, _) <- NewStyle.function.createOrUpdateBankAttribute(
              BankId(bankIdStr), Some(bankAttributeId), postedData.name, attrType,
              postedData.value, postedData.is_active, Some(cc))
          } yield createBankAttributeJson(bankAttribute)
        }
    }

    // ─── Customer Attribute ──────────────────────────────────────────────────

    private def checkCustomerBank(customer: com.openbankproject.commons.model.Customer,
                                   bankId: String, customerId: String, cc: CallContext): Future[Box[Unit]] =
      code.util.Helper.booleanToFuture(
        InvalidCustomerBankId
          .replaceAll("Bank Id.", s"Bank Id ($bankId).")
          .replaceAll("The Customer", s"The Customer($customerId)"),
        cc = Some(cc)) { customer.bankId == bankId }

    lazy val createCustomerAttribute: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "banks" / bankIdStr / "customers" / customerIdStr / "attribute" =>
        EndpointHelpers.withUserAndBodyCreated[CustomerAttributeJsonV400, Any](req) { (_, postedData, cc) =>
          for {
            attrType <- NewStyle.function.tryons(
              s"$InvalidJsonFormat The `Type` field can only accept the following field: " +
                s"${com.openbankproject.commons.model.enums.CustomerAttributeType.DOUBLE}(12.1234), ${com.openbankproject.commons.model.enums.CustomerAttributeType.STRING}(TAX_NUMBER), ${com.openbankproject.commons.model.enums.CustomerAttributeType.INTEGER}(123) and ${com.openbankproject.commons.model.enums.CustomerAttributeType.DATE_WITH_DAY}(2012-04-23)",
              400, Some(cc)) {
              com.openbankproject.commons.model.enums.CustomerAttributeType.withName(postedData.`type`)
            }
            (customer, _) <- NewStyle.function.getCustomerByCustomerId(customerIdStr, Some(cc))
            _ <- checkCustomerBank(customer, bankIdStr, customerIdStr, cc)
            (attr, _) <- NewStyle.function.createOrUpdateCustomerAttribute(
              BankId(bankIdStr), CustomerId(customerIdStr), None, postedData.name,
              attrType, postedData.value, Some(cc))
          } yield JSONFactory400.createCustomerAttributeJson(attr)
        }
    }

    lazy val updateCustomerAttribute: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ PUT -> `prefixPath` / "banks" / bankIdStr / "customers" / customerIdStr / "attributes" / customerAttributeId =>
        EndpointHelpers.withUserAndBody[CustomerAttributeJsonV400, Any](req) { (_, postedData, cc) =>
          for {
            attrType <- NewStyle.function.tryons(
              s"$InvalidJsonFormat The `Type` field can only accept the following field: " +
                s"${com.openbankproject.commons.model.enums.CustomerAttributeType.DOUBLE}(12.1234), ${com.openbankproject.commons.model.enums.CustomerAttributeType.STRING}(TAX_NUMBER), ${com.openbankproject.commons.model.enums.CustomerAttributeType.INTEGER}(123) and ${com.openbankproject.commons.model.enums.CustomerAttributeType.DATE_WITH_DAY}(2012-04-23)",
              400, Some(cc)) {
              com.openbankproject.commons.model.enums.CustomerAttributeType.withName(postedData.`type`)
            }
            (customer, _) <- NewStyle.function.getCustomerByCustomerId(customerIdStr, Some(cc))
            _ <- checkCustomerBank(customer, bankIdStr, customerIdStr, cc)
            (_, _) <- NewStyle.function.getCustomerAttributeById(customerAttributeId, Some(cc))
            (attr, _) <- NewStyle.function.createOrUpdateCustomerAttribute(
              BankId(bankIdStr), CustomerId(customerIdStr), Some(customerAttributeId),
              postedData.name, attrType, postedData.value, Some(cc))
          } yield JSONFactory400.createCustomerAttributeJson(attr)
        }
    }

    // ─── Transaction Attribute ───────────────────────────────────────────────

    lazy val createTransactionAttribute: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "banks" / bankIdStr / "accounts" / accountIdStr / "transactions" / transactionIdStr / "attribute" =>
        EndpointHelpers.withUserAndBankAndBodyCreated[TransactionAttributeJsonV400, Any](req) { (_, bank, postedData, cc) =>
          for {
            (_, _) <- NewStyle.function.getTransaction(
              bank.bankId, AccountId(accountIdStr), TransactionId(transactionIdStr), Some(cc))
            attrType <- NewStyle.function.tryons(
              s"$InvalidJsonFormat The `Type` field can only accept the following field: " +
                s"${com.openbankproject.commons.model.enums.TransactionAttributeType.DOUBLE}(12.1234), ${com.openbankproject.commons.model.enums.TransactionAttributeType.STRING}(TAX_NUMBER), ${com.openbankproject.commons.model.enums.TransactionAttributeType.INTEGER} (123)and ${com.openbankproject.commons.model.enums.TransactionAttributeType.DATE_WITH_DAY}(2012-04-23)",
              400, Some(cc)) {
              com.openbankproject.commons.model.enums.TransactionAttributeType.withName(postedData.`type`)
            }
            (attr, _) <- NewStyle.function.createOrUpdateTransactionAttribute(
              bank.bankId, TransactionId(transactionIdStr), None, postedData.name,
              attrType, postedData.value, Some(cc))
          } yield JSONFactory400.createTransactionAttributeJson(attr)
        }
    }

    lazy val updateTransactionAttribute: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ PUT -> `prefixPath` / "banks" / bankIdStr / "accounts" / accountIdStr / "transactions" / transactionIdStr / "attributes" / transactionAttributeId =>
        EndpointHelpers.withUserAndBankAndBody[TransactionAttributeJsonV400, Any](req) { (_, bank, postedData, cc) =>
          for {
            (_, _) <- NewStyle.function.getTransaction(
              bank.bankId, AccountId(accountIdStr), TransactionId(transactionIdStr), Some(cc))
            attrType <- NewStyle.function.tryons(
              s"$InvalidJsonFormat The `Type` field can only accept the following field: " +
                s"${com.openbankproject.commons.model.enums.TransactionAttributeType.DOUBLE}(12.1234), ${com.openbankproject.commons.model.enums.TransactionAttributeType.STRING}(TAX_NUMBER), ${com.openbankproject.commons.model.enums.TransactionAttributeType.INTEGER} (123)and ${com.openbankproject.commons.model.enums.TransactionAttributeType.DATE_WITH_DAY}(2012-04-23)",
              400, Some(cc)) {
              com.openbankproject.commons.model.enums.TransactionAttributeType.withName(postedData.`type`)
            }
            (_, _) <- NewStyle.function.getTransactionAttributeById(transactionAttributeId, Some(cc))
            (attr, _) <- NewStyle.function.createOrUpdateTransactionAttribute(
              bank.bankId, TransactionId(transactionIdStr), Some(transactionAttributeId),
              postedData.name, attrType, postedData.value, Some(cc))
          } yield JSONFactory400.createTransactionAttributeJson(attr)
        }
    }

    // ─── Transaction Request Attribute ───────────────────────────────────────

    lazy val createTransactionRequestAttribute: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "banks" / bankIdStr / "accounts" / _ / "transaction-requests" / transactionRequestIdStr / "attribute" =>
        EndpointHelpers.withUserAndBankAndBodyCreated[TransactionRequestAttributeJsonV400, Any](req) { (_, bank, postedData, cc) =>
          for {
            (_, _) <- NewStyle.function.getTransactionRequestImpl(
              TransactionRequestId(transactionRequestIdStr), Some(cc))
            attrType <- NewStyle.function.tryons(
              s"$InvalidJsonFormat The `Type` field can only accept the following field: " +
                s"${com.openbankproject.commons.model.enums.TransactionRequestAttributeType.DOUBLE}(12.1234), ${com.openbankproject.commons.model.enums.TransactionRequestAttributeType.STRING}(TAX_NUMBER), ${com.openbankproject.commons.model.enums.TransactionRequestAttributeType.INTEGER}(123) and ${com.openbankproject.commons.model.enums.TransactionRequestAttributeType.DATE_WITH_DAY}(2012-04-23)",
              400, Some(cc)) {
              com.openbankproject.commons.model.enums.TransactionRequestAttributeType.withName(postedData.attribute_type)
            }
            (attr, _) <- NewStyle.function.createOrUpdateTransactionRequestAttribute(
              bank.bankId, TransactionRequestId(transactionRequestIdStr), None,
              postedData.name, attrType, postedData.value, Some(cc))
          } yield JSONFactory400.createTransactionRequestAttributeJson(attr)
        }
    }

    lazy val updateTransactionRequestAttribute: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ PUT -> `prefixPath` / "banks" / bankIdStr / "accounts" / _ / "transaction-requests" / transactionRequestIdStr / "attributes" / transactionRequestAttributeId =>
        EndpointHelpers.withUserAndBankAndBody[TransactionRequestAttributeJsonV400, Any](req) { (_, bank, postedData, cc) =>
          for {
            (_, _) <- NewStyle.function.getTransactionRequestImpl(
              TransactionRequestId(transactionRequestIdStr), Some(cc))
            attrType <- NewStyle.function.tryons(
              s"$InvalidJsonFormat The `Type` field can only accept the following field: " +
                s"${com.openbankproject.commons.model.enums.TransactionRequestAttributeType.DOUBLE}(12.1234), ${com.openbankproject.commons.model.enums.TransactionRequestAttributeType.STRING}(TAX_NUMBER), ${com.openbankproject.commons.model.enums.TransactionRequestAttributeType.INTEGER}(123) and ${com.openbankproject.commons.model.enums.TransactionRequestAttributeType.DATE_WITH_DAY}(2012-04-23)",
              400, Some(cc)) {
              com.openbankproject.commons.model.enums.TransactionRequestAttributeType.withName(postedData.attribute_type)
            }
            (_, _) <- NewStyle.function.getTransactionRequestAttributeById(
              transactionRequestAttributeId, Some(cc))
            (attr, _) <- NewStyle.function.createOrUpdateTransactionRequestAttribute(
              bank.bankId, TransactionRequestId(transactionRequestIdStr),
              Some(transactionRequestAttributeId), postedData.name, attrType,
              postedData.value, Some(cc))
          } yield JSONFactory400.createTransactionRequestAttributeJson(attr)
        }
    }

    // ─── Product Fee ─────────────────────────────────────────────────────────

    lazy val createProductFee: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "banks" / bankIdStr / "products" / productCode / "fee" =>
        EndpointHelpers.withUserAndBodyCreated[ProductFeeJsonV400, Any](req) { (_, postedData, cc) =>
          for {
            (_, _) <- NewStyle.function.getProduct(BankId(bankIdStr), ProductCode(productCode), Some(cc))
            (productFee, _) <- NewStyle.function.createOrUpdateProductFee(
              BankId(bankIdStr), ProductCode(productCode), None,
              postedData.name, postedData.is_active, postedData.more_info,
              postedData.value.currency, postedData.value.amount,
              postedData.value.frequency, postedData.value.`type`, Some(cc))
          } yield createProductFeeJson(productFee)
        }
    }

    lazy val updateProductFee: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ PUT -> `prefixPath` / "banks" / bankIdStr / "products" / productCode / "fees" / productFeeId =>
        EndpointHelpers.withUserAndBodyCreated[ProductFeeJsonV400, Any](req) { (_, postedData, cc) =>
          for {
            (_, _) <- NewStyle.function.getProduct(BankId(bankIdStr), ProductCode(productCode), Some(cc))
            (_, _) <- NewStyle.function.getProductFeeById(productFeeId, Some(cc))
            (productFee, _) <- NewStyle.function.createOrUpdateProductFee(
              BankId(bankIdStr), ProductCode(productCode), Some(productFeeId),
              postedData.name, postedData.is_active, postedData.more_info,
              postedData.value.currency, postedData.value.amount,
              postedData.value.frequency, postedData.value.`type`, Some(cc))
          } yield createProductFeeJson(productFee)
        }
    }

    // ─── My Personal User Attribute ──────────────────────────────────────────

    lazy val createMyPersonalUserAttribute: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "my" / "user" / "attributes" =>
        EndpointHelpers.withUserAndBodyCreated[UserAttributeJsonV400, Any](req) { (user, postedData, cc) =>
          for {
            attrType <- NewStyle.function.tryons(
              s"$InvalidJsonFormat The `Type` field can only accept the following field: " +
                s"${com.openbankproject.commons.model.enums.UserAttributeType.DOUBLE}(12.1234), ${com.openbankproject.commons.model.enums.UserAttributeType.STRING}(TAX_NUMBER), ${com.openbankproject.commons.model.enums.UserAttributeType.INTEGER} (123)and ${com.openbankproject.commons.model.enums.UserAttributeType.DATE_WITH_DAY}(2012-04-23)",
              400, Some(cc)) {
              com.openbankproject.commons.model.enums.UserAttributeType.withName(postedData.`type`)
            }
            (userAttribute, _) <- NewStyle.function.createOrUpdateUserAttribute(
              user.userId, None, postedData.name, attrType, postedData.value,
              true, Some(cc))
          } yield JSONFactory400.createUserAttributeJson(userAttribute)
        }
    }

    lazy val updateMyPersonalUserAttribute: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ PUT -> `prefixPath` / "my" / "user" / "attributes" / userAttributeId =>
        EndpointHelpers.withUserAndBody[UserAttributeJsonV400, Any](req) { (user, postedData, cc) =>
          for {
            (attributes, _) <- NewStyle.function.getPersonalUserAttributes(user.userId, Some(cc))
            _ <- NewStyle.function.tryons(UserAttributeNotFound, 400, Some(cc)) {
              attributes.exists(_.userAttributeId == userAttributeId)
            }
            attrType <- NewStyle.function.tryons(
              s"$InvalidJsonFormat The `Type` field can only accept the following field: " +
                s"${com.openbankproject.commons.model.enums.UserAttributeType.DOUBLE}(12.1234), ${com.openbankproject.commons.model.enums.UserAttributeType.STRING}(TAX_NUMBER), ${com.openbankproject.commons.model.enums.UserAttributeType.INTEGER} (123)and ${com.openbankproject.commons.model.enums.UserAttributeType.DATE_WITH_DAY}(2012-04-23)",
              400, Some(cc)) {
              com.openbankproject.commons.model.enums.UserAttributeType.withName(postedData.`type`)
            }
            (userAttribute, _) <- NewStyle.function.createOrUpdateUserAttribute(
              user.userId, Some(userAttributeId), postedData.name, attrType,
              postedData.value, true, Some(cc))
          } yield JSONFactory400.createUserAttributeJson(userAttribute)
        }
    }

    private def initBatch10ResourceDocs(): Unit = {
      staticResourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(createBankAttribute), "POST",
        "/banks/BANK_ID/attribute",
        "Create Bank Attribute",
        s"""Create Bank Attribute.
           |
           |${userAuthenticationMessage(true)}""",
        bankAttributeJsonV400, bankAttributeResponseJsonV400,
        List(InvalidJsonFormat, UnknownError),
        List(apiTagBank, apiTagBankAttribute, apiTagAttribute),
        Some(List(canCreateBankAttribute)),
        http4sPartialFunction = Some(createBankAttribute))

      staticResourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(updateBankAttribute), "PUT",
        "/banks/BANK_ID/attributes/BANK_ATTRIBUTE_ID",
        "Update Bank Attribute",
        s"""Update Bank Attribute.
           |
           |${userAuthenticationMessage(true)}""",
        bankAttributeJsonV400, bankAttributeDefinitionJsonV400,
        List(UserHasMissingRoles, UnknownError),
        List(apiTagBank, apiTagBankAttribute, apiTagAttribute),
        Some(List(canUpdateBankAttribute)),
        http4sPartialFunction = Some(updateBankAttribute))

      staticResourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(createCustomerAttribute), "POST",
        "/banks/BANK_ID/customers/CUSTOMER_ID/attribute",
        "Create Customer Attribute",
        s"""Create Customer Attribute.
           |
           |${userAuthenticationMessage(true)}""",
        customerAttributeJsonV400, customerAttributeResponseJson,
        List($AuthenticatedUserIsRequired, $BankNotFound, InvalidJsonFormat, UnknownError),
        List(apiTagCustomer, apiTagCustomerAttribute, apiTagAttribute),
        Some(List(canCreateCustomerAttributeAtOneBank, canCreateCustomerAttributeAtAnyBank)),
        http4sPartialFunction = Some(createCustomerAttribute))

      staticResourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(updateCustomerAttribute), "PUT",
        "/banks/BANK_ID/customers/CUSTOMER_ID/attributes/CUSTOMER_ATTRIBUTE_ID",
        "Update Customer Attribute",
        s"""Update Customer Attribute.
           |
           |${userAuthenticationMessage(true)}""",
        customerAttributeJsonV400, customerAttributeResponseJson,
        List($AuthenticatedUserIsRequired, $BankNotFound, InvalidJsonFormat, UnknownError),
        List(apiTagCustomer, apiTagCustomerAttribute, apiTagAttribute),
        Some(List(canUpdateCustomerAttributeAtOneBank, canUpdateCustomerAttributeAtAnyBank)),
        http4sPartialFunction = Some(updateCustomerAttribute))

      staticResourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(createTransactionAttribute), "POST",
        "/banks/BANK_ID/accounts/ACCOUNT_ID/transactions/TRANSACTION_ID/attribute",
        "Create Transaction Attribute",
        s"""Create Transaction Attribute.
           |
           |${userAuthenticationMessage(true)}""",
        transactionAttributeJsonV400, transactionAttributeResponseJson,
        List($AuthenticatedUserIsRequired, $BankNotFound, $BankAccountNotFound,
          InvalidJsonFormat, UnknownError),
        List(apiTagTransaction, apiTagTransactionAttribute, apiTagAttribute),
        Some(List(canCreateTransactionAttributeAtOneBank)),
        http4sPartialFunction = Some(createTransactionAttribute))

      staticResourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(updateTransactionAttribute), "PUT",
        "/banks/BANK_ID/accounts/ACCOUNT_ID/transactions/TRANSACTION_ID/attributes/ACCOUNT_ATTRIBUTE_ID",
        "Update Transaction Attribute",
        s"""Update Transaction Attribute.
           |
           |${userAuthenticationMessage(true)}""",
        transactionAttributeJsonV400, transactionAttributeResponseJson,
        List($AuthenticatedUserIsRequired, $BankNotFound, $BankAccountNotFound,
          InvalidJsonFormat, UnknownError),
        List(apiTagTransaction, apiTagTransactionAttribute, apiTagAttribute),
        Some(List(canUpdateTransactionAttributeAtOneBank)),
        http4sPartialFunction = Some(updateTransactionAttribute))

      staticResourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(createTransactionRequestAttribute), "POST",
        "/banks/BANK_ID/accounts/ACCOUNT_ID/transaction-requests/TRANSACTION_REQUEST_ID/attribute",
        "Create Transaction Request Attribute",
        s"""Create Transaction Request Attribute.
           |
           |${userAuthenticationMessage(true)}""",
        transactionRequestAttributeJsonV400, transactionRequestAttributeResponseJson,
        List($AuthenticatedUserIsRequired, $BankNotFound, $BankAccountNotFound,
          InvalidJsonFormat, UnknownError),
        List(apiTagTransactionRequest, apiTagTransactionRequestAttribute, apiTagAttribute),
        Some(List(canCreateTransactionRequestAttributeAtOneBank)),
        http4sPartialFunction = Some(createTransactionRequestAttribute))

      staticResourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(updateTransactionRequestAttribute), "PUT",
        "/banks/BANK_ID/accounts/ACCOUNT_ID/transaction-requests/TRANSACTION_REQUEST_ID/attributes/ATTRIBUTE_ID",
        "Update Transaction Request Attribute",
        s"""Update Transaction Request Attribute.
           |
           |${userAuthenticationMessage(true)}""",
        transactionRequestAttributeJsonV400, transactionRequestAttributeResponseJson,
        List($AuthenticatedUserIsRequired, $BankNotFound, $BankAccountNotFound,
          InvalidJsonFormat, UnknownError),
        List(apiTagTransactionRequest, apiTagTransactionRequestAttribute, apiTagAttribute),
        Some(List(canUpdateTransactionRequestAttributeAtOneBank)),
        http4sPartialFunction = Some(updateTransactionRequestAttribute))

      staticResourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(createProductFee), "POST",
        "/banks/BANK_ID/products/PRODUCT_CODE/fee",
        "Create Product Fee",
        s"""Create Product Fee.
           |
           |${userAuthenticationMessage(true)}""",
        productFeeJsonV400.copy(product_fee_id = None), productFeeResponseJsonV400,
        List($AuthenticatedUserIsRequired, $BankNotFound, InvalidJsonFormat, UnknownError),
        List(apiTagProduct),
        Some(List(canCreateProductFee)),
        http4sPartialFunction = Some(createProductFee))

      staticResourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(updateProductFee), "PUT",
        "/banks/BANK_ID/products/PRODUCT_CODE/fees/PRODUCT_FEE_ID",
        "Update Product Fee",
        s"""Update Product Fee.
           |
           |${userAuthenticationMessage(true)}""",
        productFeeJsonV400.copy(product_fee_id = None), productFeeResponseJsonV400,
        List($AuthenticatedUserIsRequired, $BankNotFound, UserHasMissingRoles, UnknownError),
        List(apiTagProduct),
        Some(List(canUpdateProductFee)),
        http4sPartialFunction = Some(updateProductFee))

      staticResourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(createMyPersonalUserAttribute), "POST",
        "/my/user/attributes",
        "Create My Personal User Attribute",
        s"""Create My Personal User Attribute.
           |
           |${userAuthenticationMessage(true)}""",
        userAttributeJsonV400, userAttributeResponseJson,
        List($AuthenticatedUserIsRequired, InvalidJsonFormat, UnknownError),
        List(apiTagUser), Some(List()),
        http4sPartialFunction = Some(createMyPersonalUserAttribute))

      staticResourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(updateMyPersonalUserAttribute), "PUT",
        "/my/user/attributes/USER_ATTRIBUTE_ID",
        "Update My Personal User Attribute",
        s"""Update My Personal User Attribute.
           |
           |${userAuthenticationMessage(true)}""",
        userAttributeJsonV400, userAttributeResponseJson,
        List($AuthenticatedUserIsRequired, InvalidJsonFormat, UnknownError),
        List(apiTagUser), Some(List()),
        http4sPartialFunction = Some(updateMyPersonalUserAttribute))
    }
    initBatch10ResourceDocs()

    // ═══════════════════════════════════════════════════════════════════════════
    // Batch 11 — Account access, user invitations, consents, api collections
    // ═══════════════════════════════════════════════════════════════════════════

    lazy val getUserInvitationAnonymous: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "banks" / bankIdStr / "user-invitations" =>
        EndpointHelpers.executeFutureWithBodyCreated[PostUserInvitationAnonymousJsonV400, Any](req) { (postedData, cc) =>
          for {
            (invitation, _) <- NewStyle.function.getUserInvitation(
              BankId(bankIdStr), postedData.secret_key, Some(cc))
            _ <- code.util.Helper.booleanToFuture(CannotFindUserInvitation, 404, Some(cc)) {
              invitation.status == "CREATED"
            }
            _ <- code.util.Helper.booleanToFuture(CannotFindUserInvitation, 404, Some(cc)) {
              val validUntil = java.util.Calendar.getInstance
              validUntil.setTime(invitation.createdAt.get)
              validUntil.add(java.util.Calendar.HOUR, 24)
              validUntil.getTime.after(new java.util.Date())
            }
          } yield JSONFactory400.createUserInvitationJson(invitation)
        }
    }

    lazy val grantUserAccessToView: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "banks" / bankIdStr / "accounts" / accountIdStr / "account-access" / "grant" =>
        EndpointHelpers.withUserAndBodyCreated[PostAccountAccessJsonV400, Any](req) { (loggedInUser, postJson, cc) =>
          val bankId = BankId(bankIdStr)
          val accountId = AccountId(accountIdStr)
          for {
            _ <- code.util.Helper.booleanToFuture(
              UserLacksPermissionCanGrantAccessToViewForTargetAccount +
                s"Current ViewId(${postJson.view.view_id}) and current UserId(${loggedInUser.userId})",
              cc = Some(cc)) {
              APIUtil.canGrantAccessToView(bankId, accountId, ViewId(postJson.view.view_id), loggedInUser, Some(cc))
            }
            (targetUser, _) <- NewStyle.function.findByUserId(postJson.user_id, Some(cc))
            view <- JSONFactory400.getView(bankId, accountId, postJson.view, Some(cc))
            addedView <- JSONFactory400.grantAccountAccessToUser(bankId, accountId, targetUser, view, Some(cc))
          } yield code.api.v3_0_0.JSONFactory300.createViewJSON(addedView)
        }
    }

    lazy val revokeUserAccessToView: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "banks" / bankIdStr / "accounts" / accountIdStr / "account-access" / "revoke" =>
        EndpointHelpers.withUserAndBodyCreated[PostAccountAccessJsonV400, Any](req) { (loggedInUser, postJson, cc) =>
          val bankId = BankId(bankIdStr)
          val accountId = AccountId(accountIdStr)
          val viewId = ViewId(postJson.view.view_id)
          for {
            _ <- code.util.Helper.booleanToFuture(
              UserLacksPermissionCanGrantAccessToViewForTargetAccount +
                s"Current ViewId($viewId) and current UserId(${loggedInUser.userId})",
              cc = Some(cc)) {
              APIUtil.canRevokeAccessToView(bankId, accountId, viewId, loggedInUser, Some(cc))
            }
            (targetUser, _) <- NewStyle.function.findByUserId(postJson.user_id, Some(cc))
            view <- if (postJson.view.is_system)
                      ViewNewStyle.systemView(viewId, Some(cc))
                    else
                      ViewNewStyle.customView(viewId, BankIdAccountId(bankId, accountId), Some(cc))
            revoked <- if (postJson.view.is_system)
                         ViewNewStyle.revokeAccessToSystemView(bankId, accountId, view, targetUser, Some(cc))
                       else
                         ViewNewStyle.revokeAccessToCustomView(view, targetUser, Some(cc))
          } yield RevokedJsonV400(revoked)
        }
    }

    lazy val revokeGrantUserAccessToViews: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ PUT -> `prefixPath` / "banks" / bankIdStr / "accounts" / accountIdStr / "account-access" =>
        EndpointHelpers.withUserAndBodyCreated[PostRevokeGrantAccountAccessJsonV400, Any](req) { (loggedInUser, postJson, cc) =>
          val bankId = BankId(bankIdStr)
          val accountId = AccountId(accountIdStr)
          for {
            _ <- code.util.Helper.booleanToFuture(
              UserLacksPermissionCanGrantAccessToViewForTargetAccount +
                s"Current ViewIds(${postJson.views.mkString}) and current UserId(${loggedInUser.userId})",
              cc = Some(cc)) {
              APIUtil.canRevokeAccessToAllViews(bankId, accountId, loggedInUser, Some(cc))
            }
            _ <- Future(
              Views.views.vend.revokeAccountAccessByUser(bankId, accountId, loggedInUser, Some(cc))
            ) map { box => unboxFullOrFail(box, Some(cc), "Cannot revoke") }
            grantViews = postJson.views.map(viewIdStr =>
              BankIdAccountIdViewId(bankId, accountId, ViewId(viewIdStr)))
            _ <- Future(
              Views.views.vend.grantAccessToMultipleViews(grantViews, loggedInUser, Some(cc))
            ) map { box =>
              unboxFullOrFail(box, Some(cc),
                s"Cannot grant the views: ${postJson.views.mkString(",")}")
            }
          } yield RevokedJsonV400(true)
        }
    }

    lazy val createMyApiCollection: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "my" / "api-collections" =>
        EndpointHelpers.withUserAndBodyCreated[PostApiCollectionJson400, Any](req) { (user, postJson, cc) =>
          for {
            apiCollection <- Future {
              code.apicollection.MappedApiCollectionsProvider
                .getApiCollectionByUserIdAndCollectionName(user.userId, postJson.api_collection_name)
            }
            _ <- code.util.Helper.booleanToFuture(
              s"$ApiCollectionAlreadyExists Current api_collection_name(${postJson.api_collection_name}) is already existing for the log in user.",
              cc = Some(cc)) {
              apiCollection.isEmpty
            }
            (created, _) <- NewStyle.function.createApiCollection(
              user.userId, postJson.api_collection_name, postJson.is_sharable,
              postJson.description.getOrElse(""), Some(cc))
          } yield JSONFactory400.createApiCollectionJsonV400(created)
        }
    }

    lazy val createMyApiCollectionEndpoint: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "my" / "api-collections" / apiCollectionName / "api-collection-endpoints" =>
        EndpointHelpers.withUserAndBodyCreated[PostApiCollectionEndpointJson400, Any](req) { (user, postJson, cc) =>
          for {
            _ <- code.util.Helper.booleanToFuture(
              s"$InvalidOperationId Current OPERATION_ID(${postJson.operation_id})",
              cc = Some(cc)) {
              getAllResourceDocs.find(_.operationId == postJson.operation_id.trim).isDefined
            }
            (apiCollection, _) <- NewStyle.function.getApiCollectionByUserIdAndCollectionName(
              user.userId, apiCollectionName, Some(cc))
            existing <- Future {
              code.apicollectionendpoint.MappedApiCollectionEndpointsProvider
                .getApiCollectionEndpointByApiCollectionIdAndOperationId(
                  apiCollection.apiCollectionId, postJson.operation_id)
            }
            _ <- code.util.Helper.booleanToFuture(
              s"$ApiCollectionEndpointAlreadyExists Current OPERATION_ID(${postJson.operation_id}) is already in API_COLLECTION_NAME($apiCollectionName) ",
              cc = Some(cc)) {
              existing.isEmpty
            }
            (apiCollectionEndpoint, _) <- NewStyle.function.createApiCollectionEndpoint(
              apiCollection.apiCollectionId, postJson.operation_id, Some(cc))
          } yield JSONFactory400.createApiCollectionEndpointJsonV400(apiCollectionEndpoint)
        }
    }

    lazy val createMyApiCollectionEndpointById: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "my" / "api-collection-ids" / apiCollectionIdStr / "api-collection-endpoints" =>
        EndpointHelpers.withUserAndBodyCreated[PostApiCollectionEndpointJson400, Any](req) { (_, postJson, cc) =>
          for {
            _ <- code.util.Helper.booleanToFuture(
              s"$InvalidOperationId Current OPERATION_ID(${postJson.operation_id})",
              cc = Some(cc)) {
              getAllResourceDocs.find(_.operationId == postJson.operation_id.trim).isDefined
            }
            (apiCollection, _) <- NewStyle.function.getApiCollectionById(apiCollectionIdStr, Some(cc))
            existing <- Future {
              code.apicollectionendpoint.MappedApiCollectionEndpointsProvider
                .getApiCollectionEndpointByApiCollectionIdAndOperationId(
                  apiCollection.apiCollectionId, postJson.operation_id)
            }
            _ <- code.util.Helper.booleanToFuture(
              s"$ApiCollectionEndpointAlreadyExists Current OPERATION_ID(${postJson.operation_id}) is already in API_COLLECTION_ID($apiCollectionIdStr) ",
              cc = Some(cc)) {
              existing.isEmpty
            }
            (apiCollectionEndpoint, _) <- NewStyle.function.createApiCollectionEndpoint(
              apiCollection.apiCollectionId, postJson.operation_id, Some(cc))
          } yield JSONFactory400.createApiCollectionEndpointJsonV400(apiCollectionEndpoint)
        }
    }

    lazy val updateConsentStatus: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ PUT -> `prefixPath` / "banks" / _ / "consents" / consentId =>
        EndpointHelpers.withUserAndBankAndBody[PutConsentStatusJsonV400, Any](req) { (_, _, consentJson, cc) =>
          for {
            consent <- Future(
              code.consent.Consents.consentProvider.vend.getConsentByConsentId(consentId)
            ) map { box => connectorEmptyResponse(box, Some(cc)) }
            status = code.consent.ConsentStatus.withName(consentJson.status)
            updated <- APIUtil.getPropsAsBoolValue("consents.sca.enabled", true) match {
              case true =>
                Future.successful(consent)
              case false =>
                Future(
                  code.consent.Consents.consentProvider.vend.updateConsentStatus(consentId, status)
                ) map { box => connectorEmptyResponse(box, Some(cc)) }
            }
          } yield code.api.v3_1_0.ConsentJsonV310(updated.consentId, updated.jsonWebToken, updated.status)
        }
    }

    lazy val addConsentUser: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ PUT -> `prefixPath` / "banks" / _ / "consents" / consentId / "user-update-request" =>
        EndpointHelpers.withUserAndBankAndBody[PutConsentUserJsonV400, Any](req) { (_, _, putJson, cc) =>
          for {
            user <- code.users.Users.users.vend.getUserByUserIdFuture(putJson.user_id) map { box =>
              unboxFullOrFail(box, Some(cc),
                s"$UserNotFoundByUserId Current UserId(${putJson.user_id})")
            }
            consent <- Future(
              code.consent.Consents.consentProvider.vend.getConsentByConsentId(consentId)
            ) map { box => connectorEmptyResponse(box, Some(cc)) }
            _ <- code.util.Helper.booleanToFuture(ConsentUserAlreadyAdded, cc = Some(cc)) {
              Option(consent.userId).forall(_.isBlank)
            }
            updated <- Future(
              code.consent.Consents.consentProvider.vend.updateConsentUser(consentId, user)
            ) map { box => connectorEmptyResponse(box, Some(cc)) }
          } yield code.api.v3_1_0.ConsentJsonV310(updated.consentId, updated.jsonWebToken, updated.status)
        }
    }

    private def initBatch11ResourceDocs(): Unit = {
      staticResourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(getUserInvitationAnonymous), "POST",
        "/banks/BANK_ID/user-invitations",
        "Get User Invitation (Anonymous)",
        s"""Get User Invitation.""",
        PostUserInvitationAnonymousJsonV400(1L), userInvitationJsonV400,
        List($BankNotFound, InvalidJsonFormat, UnknownError),
        List(apiTagUserInvitation), None,
        http4sPartialFunction = Some(getUserInvitationAnonymous))

      staticResourceDocs += ResourceDoc(
        null, implementedInApiVersion, "grantUserAccessToView", "POST",
        "/banks/BANK_ID/accounts/ACCOUNT_ID/account-access/grant",
        "Grant User access to View",
        s"""Grant User access to View.
           |
           |${userAuthenticationMessage(true)} and the user needs to be account holder.""",
        postAccountAccessJsonV400, viewJsonV300,
        List($AuthenticatedUserIsRequired,
          UserLacksPermissionCanGrantAccessToViewForTargetAccount,
          InvalidJsonFormat, UserNotFoundById, SystemViewNotFound, ViewNotFound,
          CannotGrantAccountAccess, UnknownError),
        List(apiTagAccountAccess, apiTagView, apiTagAccount, apiTagUser, apiTagOwnerRequired), None,
        http4sPartialFunction = Some(grantUserAccessToView))

      staticResourceDocs += ResourceDoc(
        null, implementedInApiVersion, "revokeUserAccessToView", "POST",
        "/banks/BANK_ID/accounts/ACCOUNT_ID/account-access/revoke",
        "Revoke User access to View",
        s"""Revoke User access to View.
           |
           |${userAuthenticationMessage(true)} and the user needs to be account holder.""",
        postAccountAccessJsonV400, revokedJsonV400,
        List($AuthenticatedUserIsRequired,
          UserLacksPermissionCanRevokeAccessToViewForTargetAccount,
          InvalidJsonFormat, UserNotFoundById, SystemViewNotFound, ViewNotFound,
          CannotRevokeAccountAccess, CannotFindAccountAccess, UnknownError),
        List(apiTagAccountAccess, apiTagView, apiTagAccount, apiTagUser, apiTagOwnerRequired), None,
        http4sPartialFunction = Some(revokeUserAccessToView))

      staticResourceDocs += ResourceDoc(
        null, implementedInApiVersion, "revokeGrantUserAccessToViews", "PUT",
        "/banks/BANK_ID/accounts/ACCOUNT_ID/account-access",
        "Revoke/Grant User access to View",
        s"""Revoke/Grant User access to View.
           |
           |${userAuthenticationMessage(true)} and the user needs to be an account holder or has owner view access.""",
        postRevokeGrantAccountAccessJsonV400, revokedJsonV400,
        List($AuthenticatedUserIsRequired,
          UserLacksPermissionCanGrantAccessToViewForTargetAccount,
          InvalidJsonFormat, UserNotFoundById, SystemViewNotFound, ViewNotFound,
          CannotRevokeAccountAccess, CannotFindAccountAccess, UnknownError),
        List(apiTagAccountAccess, apiTagView, apiTagAccount, apiTagUser, apiTagOwnerRequired), None,
        http4sPartialFunction = Some(revokeGrantUserAccessToViews))

      staticResourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(createMyApiCollection), "POST",
        "/my/api-collections",
        "Create My Api Collection",
        s"""Create My Api Collection.
           |
           |${userAuthenticationMessage(true)}""",
        postApiCollectionJson400, apiCollectionJson400,
        List($AuthenticatedUserIsRequired, InvalidJsonFormat, UserNotFoundByUserId, UnknownError),
        List(apiTagApiCollection), None,
        http4sPartialFunction = Some(createMyApiCollection))

      staticResourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(createMyApiCollectionEndpoint), "POST",
        "/my/api-collections/API_COLLECTION_NAME/api-collection-endpoints",
        "Create My Api Collection Endpoint",
        s"""Create My Api Collection Endpoint.
           |
           |${userAuthenticationMessage(true)}""",
        postApiCollectionEndpointJson400, apiCollectionEndpointJson400,
        List($AuthenticatedUserIsRequired, InvalidJsonFormat, UnknownError),
        List(apiTagApiCollection), None,
        http4sPartialFunction = Some(createMyApiCollectionEndpoint))

      staticResourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(createMyApiCollectionEndpointById), "POST",
        "/my/api-collection-ids/API_COLLECTION_ID/api-collection-endpoints",
        "Create My Api Collection Endpoint By Id",
        s"""Create My Api Collection Endpoint By Id.
           |
           |${userAuthenticationMessage(true)}""",
        postApiCollectionEndpointJson400, apiCollectionEndpointJson400,
        List($AuthenticatedUserIsRequired, InvalidJsonFormat, UnknownError),
        List(apiTagApiCollection), None,
        http4sPartialFunction = Some(createMyApiCollectionEndpointById))

      staticResourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(updateConsentStatus), "PUT",
        "/banks/BANK_ID/consents/CONSENT_ID",
        "Update Consent Status",
        s"""Update Consent Status.
           |
           |${userAuthenticationMessage(true)}""",
        PutConsentStatusJsonV400(status = "AUTHORISED"),
        code.api.v3_1_0.ConsentChallengeJsonV310(
          "9d429899-24f5-42c8-8565-943ffa6a7945", "...", "AUTHORISED"),
        List($AuthenticatedUserIsRequired, $BankNotFound, InvalidJsonFormat,
          InvalidConnectorResponse, UnknownError),
        apiTagConsent :: apiTagPSD2AIS :: Nil, None,
        http4sPartialFunction = Some(updateConsentStatus))

      staticResourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(addConsentUser), "PUT",
        "/banks/BANK_ID/consents/CONSENT_ID/user-update-request",
        "Add User to a Consent",
        s"""Add User to a Consent.
           |
           |${userAuthenticationMessage(true)}""",
        PutConsentUserJsonV400("uuid-user"),
        code.api.v3_1_0.ConsentJsonV310("9d429899-24f5-42c8-8565-943ffa6a7945", "...", "AUTHORISED"),
        List($AuthenticatedUserIsRequired, $BankNotFound, UserNotFoundByUserId,
          ConsentUserAlreadyAdded, InvalidJsonFormat, ConsentNotFound, UnknownError),
        apiTagConsent :: apiTagPSD2AIS :: Nil, None,
        http4sPartialFunction = Some(addConsentUser))
    }
    initBatch11ResourceDocs()

    // ═══════════════════════════════════════════════════════════════════════════
    // Batch 12 — direct debits, standing orders, webhooks, settlement account
    // ═══════════════════════════════════════════════════════════════════════════

    private def directDebitImpl(bankIdStr: String, accountIdStr: String,
                                 postJson: PostDirectDebitJsonV400, cc: CallContext): Future[DirectDebitJsonV400] = {
      for {
        (_, _) <- NewStyle.function.getCustomerByCustomerId(postJson.customer_id, Some(cc))
        _ <- code.users.Users.users.vend.getUserByUserIdFuture(postJson.user_id) map { box =>
          unboxFullOrFail(box, Some(cc), s"$UserNotFoundByUserId Current UserId(${postJson.user_id})")
        }
        (_, _) <- NewStyle.function.getCounterpartyByCounterpartyId(
          CounterpartyId(postJson.counterparty_id), Some(cc))
        (directDebit, _) <- NewStyle.function.createDirectDebit(
          bankIdStr, accountIdStr, postJson.customer_id, postJson.user_id,
          postJson.counterparty_id,
          postJson.date_signed.getOrElse(new java.util.Date()),
          postJson.date_starts, postJson.date_expires, Some(cc))
      } yield JSONFactory400.createDirectDebitJSON(directDebit)
    }

    lazy val createDirectDebit: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "banks" / bankIdStr / "accounts" / accountIdStr / viewIdStr / "direct-debit" =>
        EndpointHelpers.withViewCreated[DirectDebitJsonV400](req) { (_, _, view, cc) =>
          implicit val ccx: CallContext = cc
          val bodyStr = cc.httpBody.getOrElse("")
          for {
            _ <- code.util.Helper.booleanToFuture(
              s"$NoViewPermission can_create_direct_debit. Current ViewId($viewIdStr)",
              cc = Some(cc)) {
              view.allowed_actions.exists(_ == code.api.Constant.CAN_CREATE_DIRECT_DEBIT)
            }
            postJson <- NewStyle.function.tryons(
              s"$InvalidJsonFormat The Json body should be the ${classOf[PostDirectDebitJsonV400].getSimpleName} ",
              400, Some(cc)) {
              net.liftweb.json.parse(bodyStr).extract[PostDirectDebitJsonV400]
            }
            result <- directDebitImpl(bankIdStr, accountIdStr, postJson, cc)
          } yield result
        }
    }

    lazy val createDirectDebitManagement: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "management" / "banks" / bankIdStr / "accounts" / accountIdStr / "direct-debit" =>
        EndpointHelpers.withUserAndBankAndBodyCreated[PostDirectDebitJsonV400, Any](req) { (_, _, postJson, cc) =>
          directDebitImpl(bankIdStr, accountIdStr, postJson, cc)
        }
    }

    private def standingOrderImpl(bankIdStr: String, accountIdStr: String,
                                   postJson: PostStandingOrderJsonV400, cc: CallContext): Future[StandingOrderJsonV400] = {
      for {
        amountValue <- NewStyle.function.tryons(
          s"$InvalidNumber Current input is  ${postJson.amount.amount} ", 400, Some(cc)) {
          BigDecimal(postJson.amount.amount)
        }
        _ <- code.util.Helper.booleanToFuture(
          s"${InvalidISOCurrencyCode} Current input is: '${postJson.amount.currency}'",
          cc = Some(cc)) {
          APIUtil.isValidCurrencyISOCode(postJson.amount.currency)
        }
        (_, _) <- NewStyle.function.getCustomerByCustomerId(postJson.customer_id, Some(cc))
        _ <- code.users.Users.users.vend.getUserByUserIdFuture(postJson.user_id) map { box =>
          unboxFullOrFail(box, Some(cc), s"$UserNotFoundByUserId Current UserId(${postJson.user_id})")
        }
        (_, _) <- NewStyle.function.getCounterpartyByCounterpartyId(
          CounterpartyId(postJson.counterparty_id), Some(cc))
        (order, _) <- NewStyle.function.createStandingOrder(
          bankIdStr, accountIdStr, postJson.customer_id, postJson.user_id,
          postJson.counterparty_id, amountValue, postJson.amount.currency,
          postJson.when.frequency, postJson.when.detail,
          postJson.date_signed.getOrElse(new java.util.Date()),
          postJson.date_starts, postJson.date_expires, Some(cc))
      } yield JSONFactory400.createStandingOrderJSON(order)
    }

    lazy val createStandingOrder: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "banks" / bankIdStr / "accounts" / accountIdStr / viewIdStr / "standing-order" =>
        EndpointHelpers.withViewCreated[StandingOrderJsonV400](req) { (_, _, view, cc) =>
          val bodyStr = cc.httpBody.getOrElse("")
          for {
            _ <- code.util.Helper.booleanToFuture(
              s"$NoViewPermission can_create_standing_order. Current ViewId($viewIdStr)",
              cc = Some(cc)) {
              view.allowed_actions.exists(_ == code.api.Constant.CAN_CREATE_STANDING_ORDER)
            }
            postJson <- NewStyle.function.tryons(
              s"$InvalidJsonFormat The Json body should be the ${classOf[PostStandingOrderJsonV400].getSimpleName} ",
              400, Some(cc)) {
              net.liftweb.json.parse(bodyStr).extract[PostStandingOrderJsonV400]
            }
            result <- standingOrderImpl(bankIdStr, accountIdStr, postJson, cc)
          } yield result
        }
    }

    lazy val createStandingOrderManagement: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "management" / "banks" / bankIdStr / "accounts" / accountIdStr / "standing-order" =>
        EndpointHelpers.withUserAndBankAndBodyCreated[PostStandingOrderJsonV400, Any](req) { (_, _, postJson, cc) =>
          standingOrderImpl(bankIdStr, accountIdStr, postJson, cc)
        }
    }

    lazy val createSystemAccountNotificationWebhook: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "web-hooks" / "account" / "notifications" / "on-create-transaction" =>
        EndpointHelpers.withUserAndBodyCreated[AccountNotificationWebhookPostJson, Any](req) { (user, postJson, cc) =>
          for {
            _ <- code.util.Helper.booleanToFuture(
              s"$InvalidHttpMethod Only Support `POST` currently. Current value is (${postJson.http_method})",
              cc = Some(cc)) { postJson.http_method.equals("POST") }
            _ <- code.util.Helper.booleanToFuture(
              s"$InvalidHttpProtocol Only Support `HTTP/1.1` currently. Current value is (${postJson.http_protocol})",
              cc = Some(cc)) { postJson.http_protocol.equals("HTTP/1.1") }
            onCreateTransaction = code.api.util.ApiTrigger.onCreateTransaction.toString()
            wh <- code.webhook.SystemAccountNotificationWebhookTrait
              .systemAccountNotificationWebhook.vend
              .createSystemAccountNotificationWebhookFuture(
                userId = user.userId, triggerName = onCreateTransaction,
                url = postJson.url, httpMethod = postJson.http_method,
                httpProtocol = postJson.http_protocol) map {
                unboxFullOrFail(_, Some(cc), CreateWebhookError)
              }
          } yield createSystemLevelAccountWebhookJsonV400(wh)
        }
    }

    lazy val createBankAccountNotificationWebhook: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "banks" / bankIdStr / "web-hooks" / "account" / "notifications" / "on-create-transaction" =>
        EndpointHelpers.withUserAndBankAndBodyCreated[AccountNotificationWebhookPostJson, Any](req) { (user, bank, postJson, cc) =>
          for {
            _ <- code.util.Helper.booleanToFuture(
              s"$InvalidHttpMethod Only Support `POST` currently. Current value is (${postJson.http_method})",
              cc = Some(cc)) { postJson.http_method.equals("POST") }
            _ <- code.util.Helper.booleanToFuture(
              s"$InvalidHttpProtocol Only Support `HTTP/1.1` currently. Current value is (${postJson.http_protocol})",
              cc = Some(cc)) { postJson.http_protocol.equals("HTTP/1.1") }
            onCreateTransaction = code.api.util.ApiTrigger.onCreateTransaction.toString()
            wh <- code.webhook.BankAccountNotificationWebhookTrait
              .bankAccountNotificationWebhook.vend
              .createBankAccountNotificationWebhookFuture(
                bankId = bank.bankId.value, userId = user.userId,
                triggerName = onCreateTransaction, url = postJson.url,
                httpMethod = postJson.http_method,
                httpProtocol = postJson.http_protocol) map {
                unboxFullOrFail(_, Some(cc), CreateWebhookError)
              }
          } yield createBankLevelAccountWebhookJsonV400(wh)
        }
    }

    lazy val getFastFirehoseAccountsAtOneBank: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "management" / "banks" / bankIdStr / "fast-firehose" / "accounts" =>
        EndpointHelpers.withUserAndBank(req) { (_, _, cc) =>
          for {
            _ <- code.util.Helper.booleanToFuture(
              AccountFirehoseNotAllowedOnThisInstance, cc = Some(cc)) {
              allowAccountFirehose
            }
            allowedParams = List("limit", "offset", "sort_direction")
            httpParams <- NewStyle.function.extractHttpParamsFromUrl(req.uri.renderString)
            (obpQueryParams, _) <- NewStyle.function.createObpParams(
              httpParams, allowedParams, Some(cc))
            (firehoseAccounts, _) <- NewStyle.function.getBankAccountsWithAttributes(
              BankId(bankIdStr), obpQueryParams, Some(cc))
          } yield JSONFactory400.createFirehoseBankAccountJSON(firehoseAccounts)
        }
    }

    private def initBatch12ResourceDocs(): Unit = {
      staticResourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(createDirectDebit), "POST",
        "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/direct-debit",
        "Create Direct Debit",
        s"""Create direct debit for an account.""",
        postDirectDebitJsonV400, directDebitJsonV400,
        List($AuthenticatedUserIsRequired, $BankNotFound, $BankAccountNotFound,
          NoViewPermission, InvalidJsonFormat, CustomerNotFoundByCustomerId,
          UserNotFoundByUserId, CounterpartyNotFoundByCounterpartyId, UnknownError),
        List(apiTagDirectDebit, apiTagAccount), None,
        http4sPartialFunction = Some(createDirectDebit))

      staticResourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(createDirectDebitManagement), "POST",
        "/management/banks/BANK_ID/accounts/ACCOUNT_ID/direct-debit",
        "Create Direct Debit (management)",
        s"""Create direct debit for an account.""",
        postDirectDebitJsonV400, directDebitJsonV400,
        List($AuthenticatedUserIsRequired, $BankNotFound, $BankAccountNotFound,
          NoViewPermission, InvalidJsonFormat, CustomerNotFoundByCustomerId,
          UserNotFoundByUserId, CounterpartyNotFoundByCounterpartyId, UnknownError),
        List(apiTagDirectDebit, apiTagAccount),
        Some(List(canCreateDirectDebitAtOneBank)),
        http4sPartialFunction = Some(createDirectDebitManagement))

      staticResourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(createStandingOrder), "POST",
        "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/standing-order",
        "Create Standing Order",
        s"""Create standing order for an account.""",
        postStandingOrderJsonV400, standingOrderJsonV400,
        List($AuthenticatedUserIsRequired, $BankNotFound, $BankAccountNotFound,
          NoViewPermission, InvalidJsonFormat, InvalidNumber, InvalidISOCurrencyCode,
          CustomerNotFoundByCustomerId, UserNotFoundByUserId, UnknownError),
        List(apiTagStandingOrder, apiTagAccount), None,
        http4sPartialFunction = Some(createStandingOrder))

      staticResourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(createStandingOrderManagement), "POST",
        "/management/banks/BANK_ID/accounts/ACCOUNT_ID/standing-order",
        "Create Standing Order (management)",
        s"""Create standing order for an account.""",
        postStandingOrderJsonV400, standingOrderJsonV400,
        List($AuthenticatedUserIsRequired, $BankNotFound, $BankAccountNotFound,
          NoViewPermission, InvalidJsonFormat, InvalidNumber, InvalidISOCurrencyCode,
          CustomerNotFoundByCustomerId, UserNotFoundByUserId, UnknownError),
        List(apiTagStandingOrder, apiTagAccount),
        Some(List(canCreateStandingOrderAtOneBank)),
        http4sPartialFunction = Some(createStandingOrderManagement))

      staticResourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(createSystemAccountNotificationWebhook), "POST",
        "/web-hooks/account/notifications/on-create-transaction",
        "Create System Level Account Notification Webhook",
        s"""Create System Level Account Notification Webhook.""",
        accountNotificationWebhookPostJson, systemAccountNotificationWebhookJson,
        List(UnknownError),
        apiTagWebhook :: apiTagBank :: Nil,
        Some(List(canCreateSystemAccountNotificationWebhook)),
        http4sPartialFunction = Some(createSystemAccountNotificationWebhook))

      staticResourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(createBankAccountNotificationWebhook), "POST",
        "/banks/BANK_ID/web-hooks/account/notifications/on-create-transaction",
        "Create bank level Account Notification Webhook",
        s"""Create bank level Account Notification Webhook.""",
        accountNotificationWebhookPostJson, bankAccountNotificationWebhookJson,
        List(AuthenticatedUserIsRequired, $BankNotFound, UnknownError),
        apiTagWebhook :: apiTagBank :: Nil,
        Some(List(canCreateAccountNotificationWebhookAtOneBank)),
        http4sPartialFunction = Some(createBankAccountNotificationWebhook))

      staticResourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(getFastFirehoseAccountsAtOneBank), "GET",
        "/management/banks/BANK_ID/fast-firehose/accounts",
        "Get Fast Firehose Accounts at Bank",
        s"""Get Fast Firehose Accounts at Bank.""",
        EmptyBody, fastFirehoseAccountsJsonV400,
        List($BankNotFound),
        List(apiTagAccount, apiTagAccountFirehose, apiTagFirehoseData),
        Some(List(canUseAccountFirehoseAtAnyBank, code.api.util.ApiRole.canUseAccountFirehose)),
        http4sPartialFunction = Some(getFastFirehoseAccountsAtOneBank))
    }
    initBatch12ResourceDocs()

    // ═══════════════════════════════════════════════════════════════════════════
    // Batch 7 — createOrUpdate Attribute Definitions
    // ═══════════════════════════════════════════════════════════════════════════

    private def createOrUpdateAttributeDefinitionImpl(
      bankIdStr: String,
      expectedCategory: com.openbankproject.commons.model.enums.AttributeCategory.Value,
      postedData: AttributeDefinitionJsonV400,
      cc: CallContext): Future[code.api.v4_0_0.AttributeDefinitionResponseJsonV400] = {
      for {
        attributeType <- NewStyle.function.tryons(
          s"$InvalidJsonFormat The `Type` field can only accept the following field: " +
            s"${com.openbankproject.commons.model.enums.AttributeType.DOUBLE}(12.1234), ${com.openbankproject.commons.model.enums.AttributeType.STRING}(TAX_NUMBER), ${com.openbankproject.commons.model.enums.AttributeType.INTEGER} (123)and ${com.openbankproject.commons.model.enums.AttributeType.DATE_WITH_DAY}(2012-04-23)",
          400, Some(cc)) {
          com.openbankproject.commons.model.enums.AttributeType.withName(postedData.`type`)
        }
        category <- NewStyle.function.tryons(
          s"$InvalidJsonFormat The `Category` field can only accept the following field: $expectedCategory",
          400, Some(cc)) {
          val c = com.openbankproject.commons.model.enums.AttributeCategory.withName(postedData.category)
          if (c != expectedCategory) throw new IllegalArgumentException(s"Expected category $expectedCategory")
          c
        }
        (attributeDefinition, _) <- code.api.util.newstyle.AttributeDefinition.createOrUpdateAttributeDefinition(
          BankId(bankIdStr), postedData.name, category, attributeType,
          postedData.description, postedData.alias, postedData.can_be_seen_on_views,
          postedData.is_active, Some(cc))
      } yield JSONFactory400.createAttributeDefinitionJson(attributeDefinition)
    }

    lazy val createOrUpdateCustomerAttributeAttributeDefinition: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ PUT -> `prefixPath` / "banks" / bankIdStr / "attribute-definitions" / "customer" =>
        EndpointHelpers.withUserAndBankAndBodyCreated[AttributeDefinitionJsonV400, Any](req) { (_, _, postedData, cc) =>
          createOrUpdateAttributeDefinitionImpl(bankIdStr,
            com.openbankproject.commons.model.enums.AttributeCategory.Customer, postedData, cc)
        }
    }

    lazy val createOrUpdateAccountAttributeDefinition: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ PUT -> `prefixPath` / "banks" / bankIdStr / "attribute-definitions" / "account" =>
        EndpointHelpers.withUserAndBankAndBodyCreated[AttributeDefinitionJsonV400, Any](req) { (_, _, postedData, cc) =>
          createOrUpdateAttributeDefinitionImpl(bankIdStr,
            com.openbankproject.commons.model.enums.AttributeCategory.Account, postedData, cc)
        }
    }

    lazy val createOrUpdateProductAttributeDefinition: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ PUT -> `prefixPath` / "banks" / bankIdStr / "attribute-definitions" / "product" =>
        EndpointHelpers.withUserAndBankAndBodyCreated[AttributeDefinitionJsonV400, Any](req) { (_, _, postedData, cc) =>
          createOrUpdateAttributeDefinitionImpl(bankIdStr,
            com.openbankproject.commons.model.enums.AttributeCategory.Product, postedData, cc)
        }
    }

    lazy val createOrUpdateTransactionAttributeDefinition: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ PUT -> `prefixPath` / "banks" / bankIdStr / "attribute-definitions" / "transaction" =>
        EndpointHelpers.withUserAndBankAndBodyCreated[AttributeDefinitionJsonV400, Any](req) { (_, _, postedData, cc) =>
          createOrUpdateAttributeDefinitionImpl(bankIdStr,
            com.openbankproject.commons.model.enums.AttributeCategory.Transaction, postedData, cc)
        }
    }

    lazy val createOrUpdateCardAttributeDefinition: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ PUT -> `prefixPath` / "banks" / bankIdStr / "attribute-definitions" / "card" =>
        EndpointHelpers.withUserAndBankAndBodyCreated[AttributeDefinitionJsonV400, Any](req) { (_, _, postedData, cc) =>
          createOrUpdateAttributeDefinitionImpl(bankIdStr,
            com.openbankproject.commons.model.enums.AttributeCategory.Card, postedData, cc)
        }
    }

    lazy val createOrUpdateBankAttributeDefinition: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ PUT -> `prefixPath` / "banks" / bankIdStr / "attribute-definitions" / "bank" =>
        EndpointHelpers.withUserAndBankAndBodyCreated[AttributeDefinitionJsonV400, Any](req) { (user, _, postedData, cc) =>
          createOrUpdateAttributeDefinitionImpl(bankIdStr,
            com.openbankproject.commons.model.enums.AttributeCategory.Bank, postedData, cc)
        }
    }

    private def initBatch7ResourceDocs(): Unit = {
      staticResourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(createOrUpdateCustomerAttributeAttributeDefinition), "PUT",
        "/banks/BANK_ID/attribute-definitions/customer",
        "Create or Update Customer Attribute Definition",
        s"""Create or Update Customer Attribute Definition.""".stripMargin,
        customerAttributeDefinitionJsonV400, customerAttributeDefinitionResponseJsonV400,
        List($AuthenticatedUserIsRequired, $BankNotFound, InvalidJsonFormat, UnknownError),
        List(apiTagCustomer, apiTagCustomerAttribute, apiTagAttribute),
        Some(List(canCreateCustomerAttributeDefinitionAtOneBank)),
        http4sPartialFunction = Some(createOrUpdateCustomerAttributeAttributeDefinition))

      staticResourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(createOrUpdateAccountAttributeDefinition), "PUT",
        "/banks/BANK_ID/attribute-definitions/account",
        "Create or Update Account Attribute Definition",
        s"""Create or Update Account Attribute Definition.""".stripMargin,
        accountAttributeDefinitionJsonV400, accountAttributeDefinitionResponseJsonV400,
        List($AuthenticatedUserIsRequired, $BankNotFound, InvalidJsonFormat, UnknownError),
        List(apiTagAccount, apiTagAccountAttribute, apiTagAttribute),
        Some(List(canCreateAccountAttributeDefinitionAtOneBank)),
        http4sPartialFunction = Some(createOrUpdateAccountAttributeDefinition))

      staticResourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(createOrUpdateProductAttributeDefinition), "PUT",
        "/banks/BANK_ID/attribute-definitions/product",
        "Create or Update Product Attribute Definition",
        s"""Create or Update Product Attribute Definition.""".stripMargin,
        productAttributeDefinitionJsonV400, productAttributeDefinitionResponseJsonV400,
        List($AuthenticatedUserIsRequired, $BankNotFound, InvalidJsonFormat, UnknownError),
        List(apiTagProduct, apiTagProductAttribute, apiTagAttribute),
        Some(List(canCreateProductAttributeDefinitionAtOneBank)),
        http4sPartialFunction = Some(createOrUpdateProductAttributeDefinition))

      staticResourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(createOrUpdateTransactionAttributeDefinition), "PUT",
        "/banks/BANK_ID/attribute-definitions/transaction",
        "Create or Update Transaction Attribute Definition",
        s"""Create or Update Transaction Attribute Definition.""".stripMargin,
        transactionAttributeDefinitionJsonV400, transactionAttributeDefinitionResponseJsonV400,
        List($AuthenticatedUserIsRequired, $BankNotFound, InvalidJsonFormat, UnknownError),
        List(apiTagTransaction, apiTagTransactionAttribute, apiTagAttribute),
        Some(List(canCreateTransactionAttributeDefinitionAtOneBank)),
        http4sPartialFunction = Some(createOrUpdateTransactionAttributeDefinition))

      staticResourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(createOrUpdateCardAttributeDefinition), "PUT",
        "/banks/BANK_ID/attribute-definitions/card",
        "Create or Update Card Attribute Definition",
        s"""Create or Update Card Attribute Definition.""".stripMargin,
        cardAttributeDefinitionJsonV400, cardAttributeDefinitionResponseJsonV400,
        List($AuthenticatedUserIsRequired, $BankNotFound, InvalidJsonFormat, UnknownError),
        List(apiTagCard, apiTagCardAttribute, apiTagAttribute),
        Some(List(canCreateCardAttributeDefinitionAtOneBank)),
        http4sPartialFunction = Some(createOrUpdateCardAttributeDefinition))

      staticResourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(createOrUpdateBankAttributeDefinition), "PUT",
        "/banks/BANK_ID/attribute-definitions/bank",
        "Create or Update Bank Attribute Definition",
        s"""Create or Update Bank Attribute Definition.""".stripMargin,
        bankAttributeDefinitionJsonV400, bankAttributeDefinitionResponseJsonV400,
        List($AuthenticatedUserIsRequired, $BankNotFound, InvalidJsonFormat, UnknownError),
        List(apiTagBank, apiTagBankAttribute, apiTagAttribute),
        Some(List(canCreateBankAttributeDefinitionAtOneBank)),
        http4sPartialFunction = Some(createOrUpdateBankAttributeDefinition))
    }
    initBatch7ResourceDocs()

    // ═══════════════════════════════════════════════════════════════════════════
    // Batch 6 — ATM updates (PUT) and other simple PUTs
    // ═══════════════════════════════════════════════════════════════════════════

    lazy val updateAtmSupportedCurrencies: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ PUT -> `prefixPath` / "banks" / _ / "atms" / atmIdStr / "supported-currencies" =>
        EndpointHelpers.withUserAndBankAndBodyCreated[SupportedCurrenciesJson, Any](req) { (_, bank, postedData, cc) =>
          for {
            (_, _) <- NewStyle.function.getAtm(bank.bankId, AtmId(atmIdStr), Some(cc))
            (atm, _) <- NewStyle.function.updateAtmSupportedCurrencies(
              bank.bankId, AtmId(atmIdStr), postedData.supported_currencies, Some(cc))
          } yield AtmSupportedCurrenciesJson(atm.atmId.value, atm.supportedCurrencies.getOrElse(Nil))
        }
    }

    lazy val updateAtmSupportedLanguages: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ PUT -> `prefixPath` / "banks" / _ / "atms" / atmIdStr / "supported-languages" =>
        EndpointHelpers.withUserAndBankAndBodyCreated[SupportedLanguagesJson, Any](req) { (_, bank, postedData, cc) =>
          for {
            (_, _) <- NewStyle.function.getAtm(bank.bankId, AtmId(atmIdStr), Some(cc))
            (atm, _) <- NewStyle.function.updateAtmSupportedLanguages(
              bank.bankId, AtmId(atmIdStr), postedData.supported_languages, Some(cc))
          } yield AtmSupportedLanguagesJson(atm.atmId.value, atm.supportedLanguages.getOrElse(Nil))
        }
    }

    lazy val updateAtmAccessibilityFeatures: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ PUT -> `prefixPath` / "banks" / _ / "atms" / atmIdStr / "accessibility-features" =>
        EndpointHelpers.withUserAndBankAndBodyCreated[AccessibilityFeaturesJson, Any](req) { (_, bank, postedData, cc) =>
          for {
            (_, _) <- NewStyle.function.getAtm(bank.bankId, AtmId(atmIdStr), Some(cc))
            (atm, _) <- NewStyle.function.updateAtmAccessibilityFeatures(
              bank.bankId, AtmId(atmIdStr), postedData.accessibility_features, Some(cc))
          } yield AtmAccessibilityFeaturesJson(atm.atmId.value, atm.accessibilityFeatures.getOrElse(Nil))
        }
    }

    lazy val updateAtmServices: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ PUT -> `prefixPath` / "banks" / _ / "atms" / atmIdStr / "services" =>
        EndpointHelpers.withUserAndBankAndBodyCreated[AtmServicesJsonV400, Any](req) { (_, bank, postedData, cc) =>
          for {
            (_, _) <- NewStyle.function.getAtm(bank.bankId, AtmId(atmIdStr), Some(cc))
            (atm, _) <- NewStyle.function.updateAtmServices(
              bank.bankId, AtmId(atmIdStr), postedData.services, Some(cc))
          } yield AtmServicesResponseJsonV400(atm.atmId.value, atm.services.getOrElse(Nil))
        }
    }

    lazy val updateAtmNotes: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ PUT -> `prefixPath` / "banks" / _ / "atms" / atmIdStr / "notes" =>
        EndpointHelpers.withUserAndBankAndBodyCreated[AtmNotesJsonV400, Any](req) { (_, bank, postedData, cc) =>
          for {
            (_, _) <- NewStyle.function.getAtm(bank.bankId, AtmId(atmIdStr), Some(cc))
            (atm, _) <- NewStyle.function.updateAtmNotes(
              bank.bankId, AtmId(atmIdStr), postedData.notes, Some(cc))
          } yield AtmServicesResponseJsonV400(atm.atmId.value, atm.notes.getOrElse(Nil))
        }
    }

    lazy val updateAtmLocationCategories: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ PUT -> `prefixPath` / "banks" / _ / "atms" / atmIdStr / "location-categories" =>
        EndpointHelpers.withUserAndBankAndBodyCreated[AtmLocationCategoriesJsonV400, Any](req) { (_, bank, postedData, cc) =>
          for {
            (_, _) <- NewStyle.function.getAtm(bank.bankId, AtmId(atmIdStr), Some(cc))
            (atm, _) <- NewStyle.function.updateAtmLocationCategories(
              bank.bankId, AtmId(atmIdStr), postedData.location_categories, Some(cc))
          } yield AtmLocationCategoriesResponseJsonV400(atm.atmId.value, atm.locationCategories.getOrElse(Nil))
        }
    }

    lazy val updateAtm: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ PUT -> `prefixPath` / "banks" / _ / "atms" / atmIdStr =>
        EndpointHelpers.withUserAndBankAndBodyCreated[AtmJsonV400, Any](req) { (_, bank, atmJsonV400, cc) =>
          for {
            (_, _) <- NewStyle.function.getAtm(bank.bankId, AtmId(atmIdStr), Some(cc))
            _ <- code.util.Helper.booleanToFuture(
              s"$InvalidJsonValue BANK_ID has to be the same in the URL and Body",
              failCode = 400, cc = Some(cc)) { atmJsonV400.bank_id == bank.bankId.value }
            atm <- NewStyle.function.tryons(
              CouldNotTransformJsonToInternalModel + " Atm", 400, Some(cc)) {
              JSONFactory400.transformToAtmFromV400(atmJsonV400.copy(id = Some(atmIdStr)))
            }
            (created, _) <- NewStyle.function.createOrUpdateAtm(atm, Some(cc))
          } yield JSONFactory400.createAtmJsonV400(created)
        }
    }

    private def initBatch6ResourceDocs(): Unit = {
      staticResourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(updateAtmSupportedCurrencies), "PUT",
        "/banks/BANK_ID/atms/ATM_ID/supported-currencies",
        "Update ATM Supported Currencies",
        s"""Update ATM Supported Currencies.""".stripMargin,
        supportedCurrenciesJson, atmSupportedCurrenciesJson,
        List($AuthenticatedUserIsRequired, InvalidJsonFormat, UnknownError),
        List(apiTagATM), None,
        http4sPartialFunction = Some(updateAtmSupportedCurrencies))

      staticResourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(updateAtmSupportedLanguages), "PUT",
        "/banks/BANK_ID/atms/ATM_ID/supported-languages",
        "Update ATM Supported Languages",
        s"""Update ATM Supported Languages.""".stripMargin,
        supportedLanguagesJson, atmSupportedLanguagesJson,
        List($AuthenticatedUserIsRequired, InvalidJsonFormat, UnknownError),
        List(apiTagATM), None,
        http4sPartialFunction = Some(updateAtmSupportedLanguages))

      staticResourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(updateAtmAccessibilityFeatures), "PUT",
        "/banks/BANK_ID/atms/ATM_ID/accessibility-features",
        "Update ATM Accessibility Features",
        s"""Update ATM Accessibility Features.""".stripMargin,
        accessibilityFeaturesJson, atmAccessibilityFeaturesJson,
        List($AuthenticatedUserIsRequired, InvalidJsonFormat, UnknownError),
        List(apiTagATM), None,
        http4sPartialFunction = Some(updateAtmAccessibilityFeatures))

      staticResourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(updateAtmServices), "PUT",
        "/banks/BANK_ID/atms/ATM_ID/services",
        "Update ATM Services",
        s"""Update ATM Services.""".stripMargin,
        atmServicesJson, atmServicesResponseJson,
        List($AuthenticatedUserIsRequired, InvalidJsonFormat, UnknownError),
        List(apiTagATM), None,
        http4sPartialFunction = Some(updateAtmServices))

      staticResourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(updateAtmNotes), "PUT",
        "/banks/BANK_ID/atms/ATM_ID/notes",
        "Update ATM Notes",
        s"""Update ATM Notes.""".stripMargin,
        atmNotesJson, atmNotesResponseJson,
        List($AuthenticatedUserIsRequired, InvalidJsonFormat, UnknownError),
        List(apiTagATM), None,
        http4sPartialFunction = Some(updateAtmNotes))

      staticResourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(updateAtmLocationCategories), "PUT",
        "/banks/BANK_ID/atms/ATM_ID/location-categories",
        "Update ATM Location Categories",
        s"""Update ATM Location Categories.""".stripMargin,
        atmLocationCategoriesJsonV400, atmLocationCategoriesResponseJsonV400,
        List($AuthenticatedUserIsRequired, InvalidJsonFormat, UnknownError),
        List(apiTagATM), None,
        http4sPartialFunction = Some(updateAtmLocationCategories))

      staticResourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(updateAtm), "PUT",
        "/banks/BANK_ID/atms/ATM_ID",
        "Update ATM",
        s"""Update ATM.""".stripMargin,
        atmJsonV400, atmJsonV400,
        List($AuthenticatedUserIsRequired, InvalidJsonFormat, UnknownError),
        List(apiTagATM),
        Some(List(canUpdateAtm, canCreateAtmAtAnyBank)),
        http4sPartialFunction = Some(updateAtm))
    }
    initBatch6ResourceDocs()

    // ═══════════════════════════════════════════════════════════════════════════
    // Batch 5 — More simple endpoints
    // ═══════════════════════════════════════════════════════════════════════════

    lazy val getProductFee: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / _ / "products" / _ / "fees" / productFeeId =>
        EndpointHelpers.executeAndRespond(req) { cc =>
          for {
            (productFee, _) <- NewStyle.function.getProductFeeById(productFeeId, Some(cc))
          } yield JSONFactory400.createProductFeeJson(productFee)
        }
    }

    lazy val getProductFees: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / bankIdStr / "products" / productCodeStr / "fees" =>
        EndpointHelpers.executeAndRespond(req) { cc =>
          for {
            (productFees, _) <- NewStyle.function.getProductFeesFromProvider(
              BankId(bankIdStr), ProductCode(productCodeStr), Some(cc))
          } yield JSONFactory400.createProductFeesJson(productFees)
        }
    }

    lazy val getTransactionAttributes: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / _ / "accounts" / _ / "transactions" / transactionIdStr / "attributes" =>
        EndpointHelpers.withBankAccount(req) { (_, account, cc) =>
          for {
            (_, _) <- NewStyle.function.getTransaction(
              account.bankId, account.accountId, TransactionId(transactionIdStr), Some(cc))
            (attrs, _) <- NewStyle.function.getTransactionAttributes(
              account.bankId, TransactionId(transactionIdStr), Some(cc))
          } yield JSONFactory400.createTransactionAttributesJson(attrs)
        }
    }

    lazy val getTransactionAttributeById: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / _ / "accounts" / _ / "transactions" / transactionIdStr / "attributes" / transactionAttributeId =>
        EndpointHelpers.withBankAccount(req) { (_, account, cc) =>
          for {
            (_, _) <- NewStyle.function.getTransaction(
              account.bankId, account.accountId, TransactionId(transactionIdStr), Some(cc))
            (attr, _) <- NewStyle.function.getTransactionAttributeById(transactionAttributeId, Some(cc))
          } yield JSONFactory400.createTransactionAttributeJson(attr)
        }
    }

    lazy val getTransactionRequestAttributes: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / _ / "accounts" / _ / "transaction-requests" / transactionRequestIdStr / "attributes" =>
        EndpointHelpers.withBankAccount(req) { (_, account, cc) =>
          for {
            (_, _) <- NewStyle.function.getTransactionRequestImpl(
              TransactionRequestId(transactionRequestIdStr), Some(cc))
            (attrs, _) <- NewStyle.function.getTransactionRequestAttributes(
              account.bankId, TransactionRequestId(transactionRequestIdStr), Some(cc))
          } yield JSONFactory400.createTransactionRequestAttributesJson(attrs)
        }
    }

    lazy val getTransactionRequestAttributeById: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / _ / "accounts" / _ / "transaction-requests" / transactionRequestIdStr / "attributes" / transactionRequestAttributeId =>
        EndpointHelpers.withBankAccount(req) { (_, _, cc) =>
          for {
            (_, _) <- NewStyle.function.getTransactionRequestImpl(
              TransactionRequestId(transactionRequestIdStr), Some(cc))
            (attr, _) <- NewStyle.function.getTransactionRequestAttributeById(
              transactionRequestAttributeId, Some(cc))
          } yield JSONFactory400.createTransactionRequestAttributeJson(attr)
        }
    }

    lazy val getTransactionRequestAttributeDefinition: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / _ / "attribute-definitions" / "transaction-request" =>
        EndpointHelpers.withBank(req) { (_, cc) =>
          getAttributeDefinitionImpl(
            com.openbankproject.commons.model.enums.AttributeCategory.TransactionRequest, cc)
        }
    }

    lazy val getTransactionRequest: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / _ / "accounts" / _ / _ / "transaction-requests" / transactionRequestIdStr =>
        EndpointHelpers.withView(req) { (user, account, view, cc) =>
          for {
            (transactionRequest, _) <- NewStyle.function.getTransactionRequestImpl(
              TransactionRequestId(transactionRequestIdStr), Some(cc))
          } yield code.api.v2_1_0.JSONFactory210.createTransactionRequestWithChargeJSON(transactionRequest)
        }
    }

    lazy val getMyCorrelatedEntities: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "my" / "correlated-entities" =>
        EndpointHelpers.withUser(req) { (user, cc) =>
          for {
            (userCustomerLinks, _) <- code.api.util.newstyle.UserCustomerLinkNewStyle
              .getUserCustomerLinksByUserId(user.userId, Some(cc))
            correlatedInfo <- Future.sequence(userCustomerLinks.map { link =>
              for {
                (customer, _) <- NewStyle.function.getCustomerByCustomerId(link.customerId, Some(cc))
                (ucls, _) <- code.api.util.newstyle.UserCustomerLinkNewStyle
                  .getUserCustomerLinks(link.customerId, Some(cc))
                (users, _) <- NewStyle.function.getUsersByUserIds(ucls.map(_.userId), Some(cc))
                (attributes, _) <- NewStyle.function.getUserAttributesByUsers(ucls.map(_.userId), Some(cc))
              } yield (customer, users, attributes)
            })
          } yield CorrelatedEntities(
            correlatedInfo.map { case (c, us, attrs) =>
              JSONFactory400.createCustomerAdUsersWithAttributesJson(c, us, attrs)
            })
        }
    }

    lazy val getCorrelatedUsersInfoByCustomerId: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / _ / "customers" / customerIdStr / "correlated-users" =>
        EndpointHelpers.withUserAndBank(req) { (_, _, cc) =>
          for {
            (customer, _) <- NewStyle.function.getCustomerByCustomerId(customerIdStr, Some(cc))
            (links, _) <- code.api.util.newstyle.UserCustomerLinkNewStyle
              .getUserCustomerLinks(customerIdStr, Some(cc))
            (users, _) <- NewStyle.function.getUsersByUserIds(links.map(_.userId), Some(cc))
            (attributes, _) <- NewStyle.function.getUserAttributesByUsers(links.map(_.userId), Some(cc))
          } yield JSONFactory400.createCustomerAdUsersWithAttributesJson(customer, users, attributes)
        }
    }

    lazy val getAccountsMinimalByCustomerId: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "customers" / customerIdStr / "accounts-minimal" =>
        EndpointHelpers.withUser(req) { (user, cc) =>
          for {
            (customer, _) <- NewStyle.function.getCustomerByCustomerId(customerIdStr, Some(cc))
            _ <- NewStyle.function.hasAtLeastOneEntitlement(
              customer.bankId, user.userId,
              canGetAccountsMinimalForCustomerAtOneBank :: canGetAccountsMinimalForCustomerAtAnyBank :: Nil, Some(cc))
            (links, _) <- code.api.util.newstyle.UserCustomerLinkNewStyle
              .getUserCustomerLinks(customerIdStr, Some(cc))
            (users, _) <- NewStyle.function.getUsersByUserIds(links.map(_.userId), Some(cc))
          } yield {
            val accountAccess = for (u <- users)
              yield Views.views.vend.privateViewsUserCanAccess(u)._2
            JSONFactory400.createAccountsMinimalJson400(accountAccess.flatten)
          }
        }
    }

    lazy val getCustomersByCustomerPhoneNumber: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "banks" / _ / "search" / "customers" / "mobile-phone-number" =>
        EndpointHelpers.withUserAndBankAndBody[PostCustomerPhoneNumberJsonV400, Any](req) { (_, bank, postedData, cc) =>
          for {
            (customers, _) <- NewStyle.function.getCustomersByCustomerPhoneNumber(
              bank.bankId, postedData.mobile_phone_number, Some(cc))
          } yield code.api.v3_0_0.JSONFactory300.createCustomersJson(customers)
        }
    }

    lazy val getCustomersAtAnyBank: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "customers" =>
        EndpointHelpers.withUser(req) { (_, cc) =>
          val httpParams = req.headers.headers.toList.map(h =>
            net.liftweb.http.provider.HTTPParam(h.name.toString, h.value))
          for {
            (requestParams, _) <- NewStyle.function.extractQueryParams(
              req.uri.renderString, List("limit", "offset", "sort_direction"), Some(cc))
            (customers, _) <- NewStyle.function.getCustomersAtAllBanks(Some(cc), requestParams)
          } yield code.api.v3_0_0.JSONFactory300.createCustomersJson(customers.sortBy(_.bankId))
        }
    }

    lazy val getCustomersMinimalAtAnyBank: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "customers-minimal" =>
        EndpointHelpers.withUser(req) { (_, cc) =>
          for {
            (requestParams, _) <- NewStyle.function.extractQueryParams(
              req.uri.renderString, List("limit", "offset", "sort_direction"), Some(cc))
            (customers, _) <- NewStyle.function.getCustomersAtAllBanks(Some(cc), requestParams)
          } yield JSONFactory400.createCustomersMinimalJson(customers.sortBy(_.bankId))
        }
    }

    lazy val getUserInvitation: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / _ / "user-invitations" / secretLink =>
        EndpointHelpers.withUserAndBank(req) { (_, bank, cc) =>
          for {
            (invitation, _) <- NewStyle.function.getUserInvitation(bank.bankId, secretLink.toLong, Some(cc))
          } yield JSONFactory400.createUserInvitationJson(invitation)
        }
    }

    lazy val getUserInvitations: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / _ / "user-invitations" =>
        EndpointHelpers.withUserAndBank(req) { (_, bank, cc) =>
          for {
            (invitations, _) <- NewStyle.function.getUserInvitations(bank.bankId, Some(cc))
          } yield JSONFactory400.createUserInvitationJson(invitations)
        }
    }

    private def initBatch5ResourceDocs(): Unit = {
      staticResourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(getProductFee), "GET",
        "/banks/BANK_ID/products/PRODUCT_CODE/fees/PRODUCT_FEE_ID",
        "Get Product Fee",
        s"""Get Product Fee""".stripMargin,
        EmptyBody, productFeeResponseJsonV400,
        List($BankNotFound, UnknownError),
        List(apiTagProduct), None,
        http4sPartialFunction = Some(getProductFee))

      staticResourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(getProductFees), "GET",
        "/banks/BANK_ID/products/PRODUCT_CODE/fees",
        "Get Product Fees",
        s"""Get Product Fees""".stripMargin,
        EmptyBody, productFeesResponseJsonV400,
        List($BankNotFound, UnknownError),
        List(apiTagProduct), None,
        http4sPartialFunction = Some(getProductFees))

      staticResourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(getTransactionAttributes), "GET",
        "/banks/BANK_ID/accounts/ACCOUNT_ID/transactions/TRANSACTION_ID/attributes",
        "Get Transaction Attributes",
        s"""Get Transaction Attributes
           |
           |${userAuthenticationMessage(true)}""".stripMargin,
        EmptyBody, transactionAttributesResponseJson,
        List($AuthenticatedUserIsRequired, $BankNotFound, $BankAccountNotFound, InvalidJsonFormat, UnknownError),
        List(apiTagTransaction, apiTagTransactionAttribute, apiTagAttribute),
        Some(List(canGetTransactionAttributesAtOneBank)),
        http4sPartialFunction = Some(getTransactionAttributes))

      staticResourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(getTransactionAttributeById), "GET",
        "/banks/BANK_ID/accounts/ACCOUNT_ID/transactions/TRANSACTION_ID/attributes/ATTRIBUTE_ID",
        "Get Transaction Attribute By Id",
        s"""Get Transaction Attribute By Id
           |
           |${userAuthenticationMessage(true)}""".stripMargin,
        EmptyBody, transactionAttributeResponseJson,
        List($AuthenticatedUserIsRequired, $BankNotFound, $BankAccountNotFound, InvalidJsonFormat, UnknownError),
        List(apiTagTransaction, apiTagTransactionAttribute, apiTagAttribute),
        Some(List(canGetTransactionAttributeAtOneBank)),
        http4sPartialFunction = Some(getTransactionAttributeById))

      staticResourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(getTransactionRequestAttributes), "GET",
        "/banks/BANK_ID/accounts/ACCOUNT_ID/transaction-requests/TRANSACTION_REQUEST_ID/attributes",
        "Get Transaction Request Attributes",
        s"""Get Transaction Request Attributes
           |
           |${userAuthenticationMessage(true)}""".stripMargin,
        EmptyBody, transactionRequestAttributesResponseJson,
        List($AuthenticatedUserIsRequired, $BankNotFound, $BankAccountNotFound, InvalidJsonFormat, UnknownError),
        List(apiTagTransactionRequest, apiTagTransactionRequestAttribute, apiTagAttribute),
        Some(List(canGetTransactionRequestAttributesAtOneBank)),
        http4sPartialFunction = Some(getTransactionRequestAttributes))

      staticResourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(getTransactionRequestAttributeById), "GET",
        "/banks/BANK_ID/accounts/ACCOUNT_ID/transaction-requests/TRANSACTION_REQUEST_ID/attributes/ATTRIBUTE_ID",
        "Get Transaction Request Attribute By Id",
        s"""Get Transaction Request Attribute By Id
           |
           |${userAuthenticationMessage(true)}""".stripMargin,
        EmptyBody, transactionRequestAttributeResponseJson,
        List($AuthenticatedUserIsRequired, $BankNotFound, $BankAccountNotFound, InvalidJsonFormat, UnknownError),
        List(apiTagTransactionRequest, apiTagTransactionRequestAttribute, apiTagAttribute),
        Some(List(canGetTransactionRequestAttributeAtOneBank)),
        http4sPartialFunction = Some(getTransactionRequestAttributeById))

      staticResourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(getTransactionRequestAttributeDefinition), "GET",
        "/banks/BANK_ID/attribute-definitions/transaction-request",
        "Get Transaction Request Attribute Definition",
        s"""Get Transaction Request Attribute Definition
           |
           |${userAuthenticationMessage(true)}""".stripMargin,
        EmptyBody, transactionRequestAttributeDefinitionsResponseJsonV400,
        List($AuthenticatedUserIsRequired, $BankNotFound, UnknownError),
        List(apiTagTransactionRequest, apiTagTransactionRequestAttribute, apiTagAttribute),
        Some(List(canGetTransactionRequestAttributeDefinitionAtOneBank)),
        http4sPartialFunction = Some(getTransactionRequestAttributeDefinition))

      staticResourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(getTransactionRequest), "GET",
        "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/transaction-requests/TRANSACTION_REQUEST_ID",
        "Get Transaction Request.",
        s"""Returns the transaction request specified by TRANSACTION_REQUEST_ID at BANK_ID.
           |
           |${userAuthenticationMessage(true)}""".stripMargin,
        EmptyBody, transactionRequestWithChargeJSON400,
        List($AuthenticatedUserIsRequired, $BankNotFound, $BankAccountNotFound, $UserNoPermissionAccessView, UnknownError),
        List(apiTagTransactionRequest), None,
        http4sPartialFunction = Some(getTransactionRequest))

      staticResourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(getMyCorrelatedEntities), "GET",
        "/my/correlated-entities",
        "Get Correlated Entities for the current User",
        s"""Correlated Entities are users and customers linked to the currently authenticated user via User-Customer-Links
           |
           |${userAuthenticationMessage(true)}""".stripMargin,
        EmptyBody, correlatedUsersResponseJson,
        List($AuthenticatedUserIsRequired, $BankNotFound, UnknownError),
        List(apiTagCustomer), None,
        http4sPartialFunction = Some(getMyCorrelatedEntities))

      staticResourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(getCorrelatedUsersInfoByCustomerId), "GET",
        "/banks/BANK_ID/customers/CUSTOMER_ID/correlated-users",
        "Get Correlated User Info by Customer",
        s"""Get Correlated User Info by CUSTOMER_ID
           |
           |${userAuthenticationMessage(true)}""".stripMargin,
        EmptyBody, customerAndUsersWithAttributesResponseJson,
        List($AuthenticatedUserIsRequired, $BankNotFound, UnknownError),
        List(apiTagCustomer),
        Some(List(canGetCorrelatedUsersInfoAtAnyBank, canGetCorrelatedUsersInfo)),
        http4sPartialFunction = Some(getCorrelatedUsersInfoByCustomerId))

      staticResourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(getAccountsMinimalByCustomerId), "GET",
        "/customers/CUSTOMER_ID/accounts-minimal",
        "Get Accounts Minimal for a Customer",
        s"""Get Accounts Minimal that are owned by a Customer.
           |
           |${userAuthenticationMessage(true)}""".stripMargin,
        EmptyBody, accountsMinimalJson400,
        List($AuthenticatedUserIsRequired, CustomerNotFoundByCustomerId, UnknownError),
        List(apiTagCustomer),
        Some(List(canGetAccountsMinimalForCustomerAtOneBank, canGetAccountsMinimalForCustomerAtAnyBank)),
        http4sPartialFunction = Some(getAccountsMinimalByCustomerId))

      staticResourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(getCustomersByCustomerPhoneNumber), "POST",
        "/banks/BANK_ID/search/customers/mobile-phone-number",
        "Get Customers by MOBILE_PHONE_NUMBER",
        s"""Gets the Customers specified by MOBILE_PHONE_NUMBER.""".stripMargin,
        postCustomerPhoneNumberJsonV400, customerJsonV310,
        List($AuthenticatedUserIsRequired, $BankNotFound, InvalidJsonFormat, UnknownError),
        List(apiTagCustomer),
        Some(List(canGetCustomersAtOneBank)),
        http4sPartialFunction = Some(getCustomersByCustomerPhoneNumber))

      staticResourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(getCustomersAtAnyBank), "GET",
        "/customers",
        "Get Customers at Any Bank",
        s"""Get Customers at Any Bank.""".stripMargin,
        EmptyBody, customersJsonV300,
        List(AuthenticatedUserIsRequired, UserCustomerLinksNotFoundForUser, UnknownError),
        List(apiTagCustomer, apiTagUser),
        Some(List(canGetCustomersAtAllBanks)),
        http4sPartialFunction = Some(getCustomersAtAnyBank))

      staticResourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(getCustomersMinimalAtAnyBank), "GET",
        "/customers-minimal",
        "Get Customers Minimal at Any Bank",
        s"""Get Customers Minimal at Any Bank.""".stripMargin,
        EmptyBody, customersMinimalJsonV300,
        List(AuthenticatedUserIsRequired, UserCustomerLinksNotFoundForUser, UnknownError),
        List(apiTagCustomer, apiTagUser),
        Some(List(canGetCustomersMinimalAtAllBanks)),
        http4sPartialFunction = Some(getCustomersMinimalAtAnyBank))

      staticResourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(getUserInvitation), "GET",
        "/banks/BANK_ID/user-invitations/SECRET_LINK",
        "Get User Invitation",
        s"""Get User Invitation
           |
           |${userAuthenticationMessage(true)}""".stripMargin,
        EmptyBody, userInvitationJsonV400,
        List($AuthenticatedUserIsRequired, $BankNotFound, InvalidJsonFormat, UnknownError),
        List(apiTagUserInvitation),
        Some(List(canGetUserInvitation)),
        http4sPartialFunction = Some(getUserInvitation))

      staticResourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(getUserInvitations), "GET",
        "/banks/BANK_ID/user-invitations",
        "Get User Invitations",
        s"""Get User Invitations
           |
           |${userAuthenticationMessage(true)}""".stripMargin,
        EmptyBody, userInvitationJsonV400,
        List($AuthenticatedUserIsRequired, $BankNotFound, InvalidJsonFormat, UnknownError),
        List(apiTagUserInvitation),
        Some(List(canGetUserInvitation)),
        http4sPartialFunction = Some(getUserInvitations))
    }
    initBatch5ResourceDocs()

    // ═══════════════════════════════════════════════════════════════════════════
    // Batch 4 — Consents, ApiCollections, and other simple endpoints
    // ═══════════════════════════════════════════════════════════════════════════

    lazy val getConsentInfosByBank: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / _ / "my" / "consent-infos" =>
        EndpointHelpers.withUserAndBank(req) { (user, bank, _) =>
          for {
            consents <- Future {
              code.consent.Consents.consentProvider.vend.getConsentsByUser(user.userId)
                .sortBy(i => (i.creationDateTime, i.apiStandard)).reverse
            }
          } yield {
            val consentsOfBank = code.api.util.Consent.filterByBankId(consents, bank.bankId)
            JSONFactory400.createConsentInfosJsonV400(consentsOfBank)
          }
        }
    }

    lazy val getConsentInfos: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "my" / "consent-infos" =>
        EndpointHelpers.withUser(req) { (user, _) =>
          for {
            consents <- Future {
              code.consent.Consents.consentProvider.vend.getConsentsByUser(user.userId)
                .sortBy(i => (i.creationDateTime, i.apiStandard)).reverse
            }
          } yield JSONFactory400.createConsentInfosJsonV400(consents)
        }
    }

    lazy val getMyApiCollectionByName: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "my" / "api-collections" / "name" / apiCollectionName =>
        EndpointHelpers.withUser(req) { (user, cc) =>
          for {
            (ac, _) <- NewStyle.function.getApiCollectionByUserIdAndCollectionName(
              user.userId, apiCollectionName, Some(cc))
          } yield JSONFactory400.createApiCollectionJsonV400(ac)
        }
    }

    lazy val getMyApiCollectionById: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "my" / "api-collections" / apiCollectionId =>
        EndpointHelpers.withUser(req) { (_, cc) =>
          for {
            (ac, _) <- NewStyle.function.getApiCollectionById(apiCollectionId, Some(cc))
          } yield JSONFactory400.createApiCollectionJsonV400(ac)
        }
    }

    lazy val getSharableApiCollectionById: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "api-collections" / "sharable" / apiCollectionId =>
        EndpointHelpers.executeAndRespond(req) { cc =>
          for {
            (ac, _) <- NewStyle.function.getApiCollectionById(apiCollectionId, Some(cc))
            _ <- code.util.Helper.booleanToFuture(
              s"$ApiCollectionEndpointNotFound Current api_collection_id($apiCollectionId) is not sharable.",
              cc = Some(cc)) { ac.isSharable }
          } yield JSONFactory400.createApiCollectionJsonV400(ac)
        }
    }

    lazy val getApiCollectionsForUser: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "users" / userIdStr / "api-collections" =>
        EndpointHelpers.withUser(req) { (_, cc) =>
          for {
            (_, _) <- NewStyle.function.findByUserId(userIdStr, Some(cc))
            (acs, _) <- NewStyle.function.getApiCollectionsByUserId(userIdStr, Some(cc))
          } yield JSONFactory400.createApiCollectionsJsonV400(acs)
        }
    }

    lazy val getFeaturedApiCollections: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "api-collections" / "featured" =>
        EndpointHelpers.executeAndRespond(req) { cc =>
          for {
            (acs, _) <- NewStyle.function.getFeaturedApiCollections(Some(cc))
          } yield JSONFactory400.createApiCollectionsJsonV400(acs)
        }
    }

    lazy val getMyApiCollections: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "my" / "api-collections" =>
        EndpointHelpers.withUser(req) { (user, cc) =>
          val params = req.uri.query.params
          val limitParam = params.get("limit").flatMap(s => scala.util.Try(s.toInt).toOption).getOrElse(50)
          val offsetParam = params.get("offset").flatMap(s => scala.util.Try(s.toInt).toOption).getOrElse(0)
          for {
            (acs, _) <- NewStyle.function.getApiCollectionsByUserId(user.userId, Some(cc))
          } yield JSONFactory400.createApiCollectionsJsonV400(acs.drop(offsetParam).take(limitParam))
        }
    }

    lazy val getMyApiCollectionEndpoint: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "my" / "api-collections" / apiCollectionName / "api-collection-endpoints" / operationId =>
        EndpointHelpers.withUser(req) { (user, cc) =>
          for {
            (ac, _) <- NewStyle.function.getApiCollectionByUserIdAndCollectionName(
              user.userId, apiCollectionName, Some(cc))
            (ace, _) <- NewStyle.function.getApiCollectionEndpointByApiCollectionIdAndOperationId(
              ac.apiCollectionId, operationId, Some(cc))
          } yield JSONFactory400.createApiCollectionEndpointJsonV400(ace)
        }
    }

    lazy val getApiCollectionEndpoints: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "api-collections" / apiCollectionId / "api-collection-endpoints" =>
        EndpointHelpers.withUser(req) { (_, cc) =>
          for {
            (aces, _) <- NewStyle.function.getApiCollectionEndpoints(apiCollectionId, Some(cc))
          } yield JSONFactory400.createApiCollectionEndpointsJsonV400(aces)
        }
    }

    lazy val getMyApiCollectionEndpoints: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "my" / "api-collections" / apiCollectionName / "api-collection-endpoints" =>
        EndpointHelpers.withUser(req) { (user, cc) =>
          for {
            (ac, _) <- NewStyle.function.getApiCollectionByUserIdAndCollectionName(
              user.userId, apiCollectionName, Some(cc))
            (aces, _) <- NewStyle.function.getApiCollectionEndpoints(ac.apiCollectionId, Some(cc))
          } yield JSONFactory400.createApiCollectionEndpointsJsonV400(aces)
        }
    }

    lazy val getMyApiCollectionEndpointsById: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "my" / "api-collection-ids" / apiCollectionId / "api-collection-endpoints" =>
        EndpointHelpers.withUser(req) { (_, cc) =>
          for {
            (ac, _) <- NewStyle.function.getApiCollectionById(apiCollectionId, Some(cc))
            (aces, _) <- NewStyle.function.getApiCollectionEndpoints(ac.apiCollectionId, Some(cc))
          } yield JSONFactory400.createApiCollectionEndpointsJsonV400(aces)
        }
    }

    lazy val deleteMyApiCollection: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ DELETE -> `prefixPath` / "my" / "api-collections" / apiCollectionId =>
        EndpointHelpers.withUserDelete(req) { (_, cc) =>
          for {
            (_, _) <- NewStyle.function.getApiCollectionById(apiCollectionId, Some(cc))
            (deleted, _) <- NewStyle.function.deleteApiCollectionById(apiCollectionId, Some(cc))
          } yield deleted
        }
    }

    lazy val deleteMyApiCollectionEndpoint: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ DELETE -> `prefixPath` / "my" / "api-collections" / apiCollectionName / "api-collection-endpoints" / operationId =>
        EndpointHelpers.withUserDelete(req) { (user, cc) =>
          for {
            (ac, _) <- NewStyle.function.getApiCollectionByUserIdAndCollectionName(
              user.userId, apiCollectionName, Some(cc))
            (ace, _) <- NewStyle.function.getApiCollectionEndpointByApiCollectionIdAndOperationId(
              ac.apiCollectionId, operationId, Some(cc))
            (deleted, _) <- NewStyle.function.deleteApiCollectionEndpointById(
              ace.apiCollectionEndpointId, Some(cc))
          } yield deleted
        }
    }

    lazy val deleteMyApiCollectionEndpointByOperationId: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ DELETE -> `prefixPath` / "my" / "api-collection-ids" / apiCollectionId / "api-collection-endpoints" / operationId =>
        EndpointHelpers.withUserDelete(req) { (_, cc) =>
          for {
            (ac, _) <- NewStyle.function.getApiCollectionById(apiCollectionId, Some(cc))
            (ace, _) <- NewStyle.function.getApiCollectionEndpointByApiCollectionIdAndOperationId(
              ac.apiCollectionId, operationId, Some(cc))
            (deleted, _) <- NewStyle.function.deleteApiCollectionEndpointById(
              ace.apiCollectionEndpointId, Some(cc))
          } yield deleted
        }
    }

    lazy val deleteMyApiCollectionEndpointById: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ DELETE -> `prefixPath` / "my" / "api-collection-ids" / _ / "api-collection-endpoint-ids" / apiCollectionEndpointId =>
        EndpointHelpers.withUserDelete(req) { (_, cc) =>
          for {
            (deleted, _) <- NewStyle.function.deleteApiCollectionEndpointById(apiCollectionEndpointId, Some(cc))
          } yield deleted
        }
    }

    private def initBatch4ResourceDocs(): Unit = {
      staticResourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(getConsentInfosByBank), "GET",
        "/banks/BANK_ID/my/consent-infos",
        "Get My Consents Info At Bank",
        s"""This endpoint gets the Consents that the current User created at bank.""".stripMargin,
        EmptyBody, consentInfosJsonV400,
        List($AuthenticatedUserIsRequired, $BankNotFound, UnknownError),
        List(apiTagConsent, apiTagPSD2AIS, apiTagPsd2), None,
        http4sPartialFunction = Some(getConsentInfosByBank))

      staticResourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(getConsentInfos), "GET",
        "/my/consent-infos",
        "Get My Consents Info",
        s"""This endpoint gets the Consents that the current User created.""".stripMargin,
        EmptyBody, consentInfosJsonV400,
        List($AuthenticatedUserIsRequired, $BankNotFound, UnknownError),
        List(apiTagConsent, apiTagPSD2AIS, apiTagPsd2), None,
        http4sPartialFunction = Some(getConsentInfos))

      staticResourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(getMyApiCollectionByName), "GET",
        "/my/api-collections/name/API_COLLECTION_NAME",
        "Get My Api Collection By Name",
        s"""Get Api Collection By API_COLLECTION_NAME.""".stripMargin,
        EmptyBody, apiCollectionJson400,
        List($AuthenticatedUserIsRequired, UserNotFoundByUserId, UnknownError),
        List(apiTagApiCollection), None,
        http4sPartialFunction = Some(getMyApiCollectionByName))

      staticResourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(getMyApiCollectionById), "GET",
        "/my/api-collections/API_COLLECTION_ID",
        "Get My Api Collection By Id",
        s"""Get Api Collection By API_COLLECTION_ID.""".stripMargin,
        EmptyBody, apiCollectionJson400,
        List($AuthenticatedUserIsRequired, UserNotFoundByUserId, UnknownError),
        List(apiTagApiCollection), None,
        http4sPartialFunction = Some(getMyApiCollectionById))

      staticResourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(getSharableApiCollectionById), "GET",
        "/api-collections/sharable/API_COLLECTION_ID",
        "Get Sharable Api Collection By Id",
        s"""Get Sharable Api Collection By Id.""".stripMargin,
        EmptyBody, apiCollectionJson400,
        List(UnknownError),
        List(apiTagApiCollection), None,
        http4sPartialFunction = Some(getSharableApiCollectionById))

      staticResourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(getApiCollectionsForUser), "GET",
        "/users/USER_ID/api-collections",
        "Get Api Collections for User",
        s"""Get Api Collections for User.""".stripMargin,
        EmptyBody, apiCollectionsJson400,
        List(UserNotFoundByUserId, UnknownError),
        List(apiTagApiCollection),
        Some(canGetApiCollectionsForUser :: Nil),
        http4sPartialFunction = Some(getApiCollectionsForUser))

      staticResourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(getFeaturedApiCollections), "GET",
        "/api-collections/featured",
        "Get Featured Api Collections",
        s"""Get Featured Api Collections.""".stripMargin,
        EmptyBody, apiCollectionsJson400,
        List(UnknownError),
        List(apiTagApiCollection), None,
        http4sPartialFunction = Some(getFeaturedApiCollections))

      staticResourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(getMyApiCollections), "GET",
        "/my/api-collections",
        "Get My Api Collections",
        s"""Get all the apiCollections for logged in user.""".stripMargin,
        EmptyBody, apiCollectionsJson400,
        List($AuthenticatedUserIsRequired, UnknownError),
        List(apiTagApiCollection), None,
        http4sPartialFunction = Some(getMyApiCollections))

      staticResourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(getMyApiCollectionEndpoint), "GET",
        "/my/api-collections/API_COLLECTION_NAME/api-collection-endpoints/OPERATION_ID",
        "Get My Api Collection Endpoint",
        s"""Get Api Collection Endpoint By OPERATION_ID.""".stripMargin,
        EmptyBody, apiCollectionEndpointJson400,
        List($AuthenticatedUserIsRequired, UserNotFoundByUserId, UnknownError),
        List(apiTagApiCollection), None,
        http4sPartialFunction = Some(getMyApiCollectionEndpoint))

      staticResourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(getApiCollectionEndpoints), "GET",
        "/api-collections/API_COLLECTION_ID/api-collection-endpoints",
        "Get Api Collection Endpoints",
        s"""Get Api Collection Endpoints By API_COLLECTION_ID.""".stripMargin,
        EmptyBody, apiCollectionEndpointsJson400,
        List($AuthenticatedUserIsRequired, UnknownError),
        List(apiTagApiCollection), None,
        http4sPartialFunction = Some(getApiCollectionEndpoints))

      staticResourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(getMyApiCollectionEndpoints), "GET",
        "/my/api-collections/API_COLLECTION_NAME/api-collection-endpoints",
        "Get My Api Collection Endpoints",
        s"""Get Api Collection Endpoints By API_COLLECTION_NAME.""".stripMargin,
        EmptyBody, apiCollectionEndpointsJson400,
        List($AuthenticatedUserIsRequired, UserNotFoundByUserId, UnknownError),
        List(apiTagApiCollection), None,
        http4sPartialFunction = Some(getMyApiCollectionEndpoints))

      staticResourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(getMyApiCollectionEndpointsById), "GET",
        "/my/api-collection-ids/API_COLLECTION_ID/api-collection-endpoints",
        "Get My Api Collection Endpoints By Id",
        s"""Get Api Collection Endpoints By API_COLLECTION_ID.""".stripMargin,
        EmptyBody, apiCollectionEndpointsJson400,
        List($AuthenticatedUserIsRequired, UserNotFoundByUserId, UnknownError),
        List(apiTagApiCollection), None,
        http4sPartialFunction = Some(getMyApiCollectionEndpointsById))

      staticResourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(deleteMyApiCollection), "DELETE",
        "/my/api-collections/API_COLLECTION_ID",
        "Delete My Api Collection",
        s"""Delete Api Collection By API_COLLECTION_ID.""".stripMargin,
        EmptyBody, Full(true),
        List($AuthenticatedUserIsRequired, UserNotFoundByUserId, UnknownError),
        List(apiTagApiCollection), None,
        http4sPartialFunction = Some(deleteMyApiCollection))

      staticResourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(deleteMyApiCollectionEndpoint), "DELETE",
        "/my/api-collections/API_COLLECTION_NAME/api-collection-endpoints/OPERATION_ID",
        "Delete My Api Collection Endpoint",
        s"""Delete Api Collection Endpoint By OPERATION_ID.""".stripMargin,
        EmptyBody, Full(true),
        List($AuthenticatedUserIsRequired, UserNotFoundByUserId, UnknownError),
        List(apiTagApiCollection), None,
        http4sPartialFunction = Some(deleteMyApiCollectionEndpoint))

      staticResourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(deleteMyApiCollectionEndpointByOperationId), "DELETE",
        "/my/api-collection-ids/API_COLLECTION_ID/api-collection-endpoints/OPERATION_ID",
        "Delete My Api Collection Endpoint By Id",
        s"""Delete Api Collection Endpoint By OPERATION_ID.""".stripMargin,
        EmptyBody, Full(true),
        List($AuthenticatedUserIsRequired, UserNotFoundByUserId, UnknownError),
        List(apiTagApiCollection), None,
        http4sPartialFunction = Some(deleteMyApiCollectionEndpointByOperationId))

      staticResourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(deleteMyApiCollectionEndpointById), "DELETE",
        "/my/api-collection-ids/API_COLLECTION_ID/api-collection-endpoint-ids/API_COLLECTION_ENDPOINT_ID",
        "Delete My Api Collection Endpoint By Id",
        s"""Delete Api Collection Endpoint By Id.""".stripMargin,
        EmptyBody, Full(true),
        List($AuthenticatedUserIsRequired, UserNotFoundByUserId, UnknownError),
        List(apiTagApiCollection), None,
        http4sPartialFunction = Some(deleteMyApiCollectionEndpointById))
    }
    initBatch4ResourceDocs()

    // ═══════════════════════════════════════════════════════════════════════════
    // Batch 3 — simple DELETEs (mostly return 200 with body, some return 204)
    // ═══════════════════════════════════════════════════════════════════════════

    private def deleteAttributeDefinitionImpl(
      attributeDefinitionId: String,
      category: com.openbankproject.commons.model.enums.AttributeCategory.Value,
      cc: CallContext): Future[Box[Boolean]] = {
      for {
        (deleted, _) <- code.api.util.newstyle.AttributeDefinition.deleteAttributeDefinition(
          attributeDefinitionId, category, Some(cc))
      } yield Full(deleted)
    }

    lazy val deleteTransactionAttributeDefinition: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ DELETE -> `prefixPath` / "banks" / _ / "attribute-definitions" / attributeDefinitionId / "transaction" =>
        EndpointHelpers.withBank(req) { (_, cc) =>
          deleteAttributeDefinitionImpl(
            attributeDefinitionId,
            com.openbankproject.commons.model.enums.AttributeCategory.Transaction, cc)
        }
    }

    lazy val deleteCustomerAttributeDefinition: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ DELETE -> `prefixPath` / "banks" / _ / "attribute-definitions" / attributeDefinitionId / "customer" =>
        EndpointHelpers.withBank(req) { (_, cc) =>
          deleteAttributeDefinitionImpl(
            attributeDefinitionId,
            com.openbankproject.commons.model.enums.AttributeCategory.Customer, cc)
        }
    }

    lazy val deleteAccountAttributeDefinition: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ DELETE -> `prefixPath` / "banks" / _ / "attribute-definitions" / attributeDefinitionId / "account" =>
        EndpointHelpers.withBank(req) { (_, cc) =>
          deleteAttributeDefinitionImpl(
            attributeDefinitionId,
            com.openbankproject.commons.model.enums.AttributeCategory.Account, cc)
        }
    }

    lazy val deleteProductAttributeDefinition: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ DELETE -> `prefixPath` / "banks" / _ / "attribute-definitions" / attributeDefinitionId / "product" =>
        EndpointHelpers.withBank(req) { (_, cc) =>
          deleteAttributeDefinitionImpl(
            attributeDefinitionId,
            com.openbankproject.commons.model.enums.AttributeCategory.Product, cc)
        }
    }

    lazy val deleteCardAttributeDefinition: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ DELETE -> `prefixPath` / "banks" / _ / "attribute-definitions" / attributeDefinitionId / "card" =>
        EndpointHelpers.withBank(req) { (_, cc) =>
          deleteAttributeDefinitionImpl(
            attributeDefinitionId,
            com.openbankproject.commons.model.enums.AttributeCategory.Card, cc)
        }
    }

    lazy val deleteTransactionRequestAttributeDefinition: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ DELETE -> `prefixPath` / "banks" / _ / "attribute-definitions" / attributeDefinitionId / "transaction-request" =>
        EndpointHelpers.withBank(req) { (_, cc) =>
          deleteAttributeDefinitionImpl(
            attributeDefinitionId,
            com.openbankproject.commons.model.enums.AttributeCategory.TransactionRequest, cc)
        }
    }

    lazy val deleteUser: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ DELETE -> `prefixPath` / "users" / userId =>
        EndpointHelpers.withUser(req) { (_, cc) =>
          for {
            (user, _) <- NewStyle.function.findByUserId(userId, Some(cc))
            (userDeleted, _) <- NewStyle.function.deleteUser(user.userPrimaryKey, Some(cc))
          } yield Full(userDeleted)
        }
    }

    lazy val deleteUserCustomerLink: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ DELETE -> `prefixPath` / "banks" / _ / "user_customer_links" / userCustomerLinkId =>
        EndpointHelpers.withUserAndBank(req) { (_, _, cc) =>
          for {
            (deleted, _) <- code.api.util.newstyle.UserCustomerLinkNewStyle
              .deleteUserCustomerLink(userCustomerLinkId, Some(cc))
          } yield Full(deleted)
        }
    }

    lazy val deleteTransactionCascade: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ DELETE -> `prefixPath` / "management" / "cascading" / "banks" / bankIdStr / "accounts" / accountIdStr / "transactions" / transactionIdStr =>
        EndpointHelpers.withUser(req) { (_, cc) =>
          for {
            (_, _) <- NewStyle.function.getTransaction(
              BankId(bankIdStr), AccountId(accountIdStr), TransactionId(transactionIdStr), Some(cc))
            _ <- Future(deletion.DeleteTransactionCascade.atomicDelete(
              BankId(bankIdStr), AccountId(accountIdStr), TransactionId(transactionIdStr)))
          } yield Full(true)
        }
    }

    lazy val deleteAccountCascade: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ DELETE -> `prefixPath` / "management" / "cascading" / "banks" / _ / "accounts" / _ =>
        EndpointHelpers.withBankAccount(req) { (_, account, cc) =>
          for {
            result <- Future(deletion.DeleteAccountCascade.atomicDelete(account.bankId, account.accountId))
          } yield Full(result.getOrElse(false))
        }
    }

    lazy val deleteBankCascade: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ DELETE -> `prefixPath` / "management" / "cascading" / "banks" / _ =>
        EndpointHelpers.withUserAndBank(req) { (_, bank, _) =>
          for {
            _ <- Future(deletion.DeleteBankCascade.atomicDelete(bank.bankId))
          } yield Full(true)
        }
    }

    lazy val deleteProductCascade: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ DELETE -> `prefixPath` / "management" / "cascading" / "banks" / _ / "products" / productCodeStr =>
        EndpointHelpers.withUserAndBank(req) { (_, bank, cc) =>
          for {
            (_, _) <- NewStyle.function.getProduct(bank.bankId, ProductCode(productCodeStr), Some(cc))
            _ <- Future(deletion.DeleteProductCascade.atomicDelete(bank.bankId, ProductCode(productCodeStr)))
          } yield Full(true)
        }
    }

    lazy val deleteCustomerCascade: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ DELETE -> `prefixPath` / "management" / "cascading" / "banks" / _ / "customers" / customerIdStr =>
        EndpointHelpers.withUserAndBank(req) { (_, _, cc) =>
          for {
            (_, _) <- NewStyle.function.getCustomerByCustomerId(customerIdStr, Some(cc))
            _ <- Future(deletion.DeleteCustomerCascade.atomicDelete(CustomerId(customerIdStr)))
          } yield Full(true)
        }
    }

    lazy val deleteSystemLevelEndpointTag: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ DELETE -> `prefixPath` / "management" / "endpoints" / _ / "tags" / endpointTagId =>
        EndpointHelpers.withUserDelete(req) { (_, cc) =>
          for {
            (_, _) <- NewStyle.function.getEndpointTag(endpointTagId, Some(cc))
            (deleted, _) <- NewStyle.function.deleteEndpointTag(endpointTagId, Some(cc))
          } yield deleted
        }
    }

    lazy val deleteBankLevelEndpointTag: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ DELETE -> `prefixPath` / "management" / "banks" / _ / "endpoints" / _ / "tags" / endpointTagId =>
        EndpointHelpers.withUserAndBankDelete(req) { (_, _, cc) =>
          for {
            (_, _) <- NewStyle.function.getEndpointTag(endpointTagId, Some(cc))
            (deleted, _) <- NewStyle.function.deleteEndpointTag(endpointTagId, Some(cc))
          } yield deleted
        }
    }

    lazy val deleteAuthenticationTypeValidation: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ DELETE -> `prefixPath` / "management" / "authentication-type-validations" / operationId =>
        EndpointHelpers.withUser(req) { (_, cc) =>
          for {
            (isExists, _) <- NewStyle.function.isAuthenticationTypeValidationExists(operationId, Some(cc))
            _ <- code.util.Helper.booleanToFuture(AuthenticationTypeValidationNotFound, cc = Some(cc)) { isExists }
            (deleteResult, _) <- NewStyle.function.deleteAuthenticationTypeValidation(operationId, Some(cc))
          } yield deleteResult
        }
    }

    lazy val deleteJsonSchemaValidation: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ DELETE -> `prefixPath` / "management" / "json-schema-validations" / operationId =>
        EndpointHelpers.withUser(req) { (_, cc) =>
          for {
            (isExists, _) <- NewStyle.function.isJsonSchemaValidationExists(operationId, Some(cc))
            _ <- code.util.Helper.booleanToFuture(JsonSchemaValidationNotFound, cc = Some(cc)) { isExists }
            (deleteResult, _) <- NewStyle.function.deleteJsonSchemaValidation(operationId, Some(cc))
          } yield deleteResult
        }
    }

    lazy val deleteCustomerAttribute: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ DELETE -> `prefixPath` / "banks" / _ / "customers" / "attributes" / customerAttributeId =>
        EndpointHelpers.withUserAndBankDelete(req) { (_, _, cc) =>
          for {
            (deleted, _) <- NewStyle.function.deleteCustomerAttribute(customerAttributeId, Some(cc))
          } yield deleted
        }
    }

    lazy val deleteBankAttribute: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ DELETE -> `prefixPath` / "banks" / _ / "attributes" / bankAttributeId =>
        EndpointHelpers.withUserAndBankDelete(req) { (_, _, cc) =>
          for {
            (_, _) <- NewStyle.function.deleteBankAttribute(bankAttributeId, Some(cc))
          } yield ()
        }
    }

    lazy val deleteAtm: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ DELETE -> `prefixPath` / "banks" / _ / "atms" / atmIdStr =>
        EndpointHelpers.withUserAndBankDelete(req) { (_, bank, cc) =>
          for {
            (atm, _) <- NewStyle.function.getAtm(bank.bankId, AtmId(atmIdStr), Some(cc))
            (deleted, _) <- NewStyle.function.deleteAtm(atm, Some(cc))
          } yield deleted
        }
    }

    lazy val deleteProductFee: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ DELETE -> `prefixPath` / "banks" / _ / "products" / _ / "fees" / productFeeId =>
        EndpointHelpers.withUserAndBankDelete(req) { (_, _, cc) =>
          for {
            (_, _) <- NewStyle.function.getProductFeeById(productFeeId, Some(cc))
            (productFee, _) <- NewStyle.function.deleteProductFee(productFeeId, Some(cc))
          } yield productFee
        }
    }

    lazy val deleteEndpointMapping: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ DELETE -> `prefixPath` / "management" / "endpoint-mappings" / endpointMappingId =>
        EndpointHelpers.withUser(req) { (_, cc) =>
          for {
            (deleted, _) <- NewStyle.function.deleteEndpointMapping(None, endpointMappingId, Some(cc))
          } yield deleted
        }
    }

    lazy val deleteBankLevelEndpointMapping: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ DELETE -> `prefixPath` / "management" / "banks" / _ / "endpoint-mappings" / endpointMappingId =>
        EndpointHelpers.withUserAndBank(req) { (_, bank, cc) =>
          for {
            (deleted, _) <- NewStyle.function.deleteEndpointMapping(Some(bank.bankId.value), endpointMappingId, Some(cc))
          } yield deleted
        }
    }

    private def initBatch3ResourceDocs(): Unit = {
      staticResourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(deleteTransactionAttributeDefinition), "DELETE",
        "/banks/BANK_ID/attribute-definitions/ATTRIBUTE_DEFINITION_ID/transaction",
        "Delete Transaction Attribute Definition",
        s"""Delete Transaction Attribute Definition by ATTRIBUTE_DEFINITION_ID
           |
           |${userAuthenticationMessage(true)}""".stripMargin,
        EmptyBody, EmptyBody,
        List($AuthenticatedUserIsRequired, $BankNotFound, UnknownError),
        List(apiTagTransaction, apiTagTransactionAttribute, apiTagAttribute),
        Some(List(canDeleteTransactionAttributeDefinitionAtOneBank)),
        http4sPartialFunction = Some(deleteTransactionAttributeDefinition))

      staticResourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(deleteCustomerAttributeDefinition), "DELETE",
        "/banks/BANK_ID/attribute-definitions/ATTRIBUTE_DEFINITION_ID/customer",
        "Delete Customer Attribute Definition",
        s"""Delete Customer Attribute Definition by ATTRIBUTE_DEFINITION_ID
           |
           |${userAuthenticationMessage(true)}""".stripMargin,
        EmptyBody, EmptyBody,
        List($AuthenticatedUserIsRequired, $BankNotFound, UnknownError),
        List(apiTagCustomer, apiTagCustomerAttribute, apiTagAttribute),
        Some(List(canDeleteCustomerAttributeDefinitionAtOneBank)),
        http4sPartialFunction = Some(deleteCustomerAttributeDefinition))

      staticResourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(deleteAccountAttributeDefinition), "DELETE",
        "/banks/BANK_ID/attribute-definitions/ATTRIBUTE_DEFINITION_ID/account",
        "Delete Account Attribute Definition",
        s"""Delete Account Attribute Definition by ATTRIBUTE_DEFINITION_ID
           |
           |${userAuthenticationMessage(true)}""".stripMargin,
        EmptyBody, EmptyBody,
        List($AuthenticatedUserIsRequired, $BankNotFound, UnknownError),
        List(apiTagAccount, apiTagAccountAttribute, apiTagAttribute),
        Some(List(canDeleteAccountAttributeDefinitionAtOneBank)),
        http4sPartialFunction = Some(deleteAccountAttributeDefinition))

      staticResourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(deleteProductAttributeDefinition), "DELETE",
        "/banks/BANK_ID/attribute-definitions/ATTRIBUTE_DEFINITION_ID/product",
        "Delete Product Attribute Definition",
        s"""Delete Product Attribute Definition by ATTRIBUTE_DEFINITION_ID
           |
           |${userAuthenticationMessage(true)}""".stripMargin,
        EmptyBody, EmptyBody,
        List($AuthenticatedUserIsRequired, $BankNotFound, UnknownError),
        List(apiTagProduct, apiTagProductAttribute, apiTagAttribute),
        Some(List(canDeleteProductAttributeDefinitionAtOneBank)),
        http4sPartialFunction = Some(deleteProductAttributeDefinition))

      staticResourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(deleteCardAttributeDefinition), "DELETE",
        "/banks/BANK_ID/attribute-definitions/ATTRIBUTE_DEFINITION_ID/card",
        "Delete Card Attribute Definition",
        s"""Delete Card Attribute Definition by ATTRIBUTE_DEFINITION_ID
           |
           |${userAuthenticationMessage(true)}""".stripMargin,
        EmptyBody, EmptyBody,
        List($AuthenticatedUserIsRequired, $BankNotFound, UnknownError),
        List(apiTagCard, apiTagCardAttribute, apiTagAttribute),
        Some(List(canDeleteCardAttributeDefinitionAtOneBank)),
        http4sPartialFunction = Some(deleteCardAttributeDefinition))

      staticResourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(deleteTransactionRequestAttributeDefinition), "DELETE",
        "/banks/BANK_ID/attribute-definitions/ATTRIBUTE_DEFINITION_ID/transaction-request",
        "Delete Transaction Request Attribute Definition",
        s"""Delete Transaction Request Attribute Definition by ATTRIBUTE_DEFINITION_ID
           |
           |${userAuthenticationMessage(true)}""".stripMargin,
        EmptyBody, Full(true),
        List($AuthenticatedUserIsRequired, $BankNotFound, UnknownError),
        List(apiTagTransactionRequest, apiTagTransactionRequestAttribute, apiTagAttribute),
        Some(List(canDeleteTransactionRequestAttributeDefinitionAtOneBank)),
        http4sPartialFunction = Some(deleteTransactionRequestAttributeDefinition))

      staticResourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(deleteUser), "DELETE",
        "/users/USER_ID",
        "Delete a User",
        s"""Delete a User.
           |
           |${userAuthenticationMessage(true)}""".stripMargin,
        EmptyBody, EmptyBody,
        List($AuthenticatedUserIsRequired, UserHasMissingRoles, UnknownError),
        List(apiTagUser),
        Some(List(canDeleteUser)),
        http4sPartialFunction = Some(deleteUser))

      staticResourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(deleteUserCustomerLink), "DELETE",
        "/banks/BANK_ID/user_customer_links/USER_CUSTOMER_LINK_ID",
        "Delete User Customer Link",
        s"""Delete User Customer Link by USER_CUSTOMER_LINK_ID
           |
           |${userAuthenticationMessage(true)}""".stripMargin,
        EmptyBody, EmptyBody,
        List($AuthenticatedUserIsRequired, $BankNotFound, UserHasMissingRoles, UnknownError),
        List(apiTagCustomer),
        Some(List(canDeleteUserCustomerLink)),
        http4sPartialFunction = Some(deleteUserCustomerLink))

      staticResourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(deleteTransactionCascade), "DELETE",
        "/management/cascading/banks/BANK_ID/accounts/ACCOUNT_ID/transactions/TRANSACTION_ID",
        "Delete Transaction Cascade",
        s"""Delete a Transaction Cascade specified by TRANSACTION_ID.
           |
           |${userAuthenticationMessage(true)}""".stripMargin,
        EmptyBody, EmptyBody,
        List($AuthenticatedUserIsRequired, $BankNotFound, $BankAccountNotFound, UserHasMissingRoles, UnknownError),
        List(apiTagTransaction),
        Some(List(canDeleteTransactionCascade)),
        http4sPartialFunction = Some(deleteTransactionCascade))

      staticResourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(deleteAccountCascade), "DELETE",
        "/management/cascading/banks/BANK_ID/accounts/ACCOUNT_ID",
        "Delete Account Cascade",
        s"""Delete an Account Cascade specified by ACCOUNT_ID.
           |
           |${userAuthenticationMessage(true)}""".stripMargin,
        EmptyBody, EmptyBody,
        List($AuthenticatedUserIsRequired, $BankNotFound, $BankAccountNotFound, UserHasMissingRoles, UnknownError),
        List(apiTagAccount),
        Some(List(canDeleteAccountCascade)),
        http4sPartialFunction = Some(deleteAccountCascade))

      staticResourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(deleteBankCascade), "DELETE",
        "/management/cascading/banks/BANK_ID",
        "Delete Bank Cascade",
        s"""Delete a Bank Cascade specified by BANK_ID.
           |
           |${userAuthenticationMessage(true)}""".stripMargin,
        EmptyBody, EmptyBody,
        List($AuthenticatedUserIsRequired, $BankNotFound, UserHasMissingRoles, UnknownError),
        List(apiTagBank),
        Some(List(canDeleteBankCascade)),
        http4sPartialFunction = Some(deleteBankCascade))

      staticResourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(deleteProductCascade), "DELETE",
        "/management/cascading/banks/BANK_ID/products/PRODUCT_CODE",
        "Delete Product Cascade",
        s"""Delete a Product Cascade specified by PRODUCT_CODE.
           |
           |${userAuthenticationMessage(true)}""".stripMargin,
        EmptyBody, EmptyBody,
        List($AuthenticatedUserIsRequired, $BankNotFound, $BankAccountNotFound, UserHasMissingRoles, UnknownError),
        List(apiTagProduct),
        Some(List(canDeleteProductCascade)),
        http4sPartialFunction = Some(deleteProductCascade))

      staticResourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(deleteCustomerCascade), "DELETE",
        "/management/cascading/banks/BANK_ID/customers/CUSTOMER_ID",
        "Delete Customer Cascade",
        s"""Delete a Customer Cascade specified by CUSTOMER_ID.
           |
           |${userAuthenticationMessage(true)}""".stripMargin,
        EmptyBody, EmptyBody,
        List($AuthenticatedUserIsRequired, $BankNotFound, CustomerNotFoundByCustomerId, UserHasMissingRoles, UnknownError),
        List(apiTagCustomer),
        Some(List(canDeleteCustomerCascade)),
        http4sPartialFunction = Some(deleteCustomerCascade))

      staticResourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(deleteSystemLevelEndpointTag), "DELETE",
        "/management/endpoints/OPERATION_ID/tags/ENDPOINT_TAG_ID",
        "Delete System Level Endpoint Tag",
        s"""Delete System Level Endpoint Tag.""",
        EmptyBody, Full(true),
        List($AuthenticatedUserIsRequired, UserHasMissingRoles, UnknownError),
        List(apiTagApi),
        Some(List(canDeleteSystemLevelEndpointTag)),
        http4sPartialFunction = Some(deleteSystemLevelEndpointTag))

      staticResourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(deleteBankLevelEndpointTag), "DELETE",
        "/management/banks/BANK_ID/endpoints/OPERATION_ID/tags/ENDPOINT_TAG_ID",
        "Delete Bank Level Endpoint Tag",
        s"""Delete Bank Level Endpoint Tag.""",
        EmptyBody, Full(true),
        List($AuthenticatedUserIsRequired, $BankNotFound, UserHasMissingRoles, UnknownError),
        List(apiTagApi),
        Some(List(canDeleteBankLevelEndpointTag)),
        http4sPartialFunction = Some(deleteBankLevelEndpointTag))

      staticResourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(deleteAuthenticationTypeValidation), "DELETE",
        "/management/authentication-type-validations/OPERATION_ID",
        "Delete an Authentication Type Validation",
        s"""Delete an Authentication Type Validation by operation_id.""",
        EmptyBody, BooleanBody(true),
        List($AuthenticatedUserIsRequired, UserHasMissingRoles, InvalidJsonFormat, UnknownError),
        List(apiTagAuthenticationTypeValidation),
        Some(List(canDeleteAuthenticationValidation)),
        http4sPartialFunction = Some(deleteAuthenticationTypeValidation))

      staticResourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(deleteJsonSchemaValidation), "DELETE",
        "/management/json-schema-validations/OPERATION_ID",
        "Delete a JSON Schema Validation",
        s"""Delete a JSON Schema Validation by operation_id.""",
        EmptyBody, BooleanBody(true),
        List($AuthenticatedUserIsRequired, UserHasMissingRoles, InvalidJsonFormat, UnknownError),
        List(apiTagJsonSchemaValidation),
        Some(List(canDeleteJsonSchemaValidation)),
        http4sPartialFunction = Some(deleteJsonSchemaValidation))

      staticResourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(deleteCustomerAttribute), "DELETE",
        "/banks/BANK_ID/customers/attributes/CUSTOMER_ATTRIBUTE_ID",
        "Delete Customer Attribute",
        s"""Delete Customer Attribute.
           |
           |${userAuthenticationMessage(true)}""".stripMargin,
        EmptyBody, EmptyBody,
        List(UserHasMissingRoles, UnknownError),
        List(apiTagCustomer, apiTagCustomerAttribute, apiTagAttribute),
        Some(List(canDeleteCustomerAttributeAtOneBank, canDeleteCustomerAttributeAtAnyBank)),
        http4sPartialFunction = Some(deleteCustomerAttribute))

      staticResourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(deleteBankAttribute), "DELETE",
        "/banks/BANK_ID/attributes/BANK_ATTRIBUTE_ID",
        "Delete Bank Attribute",
        s"""Delete Bank Attribute.
           |
           |${userAuthenticationMessage(true)}""".stripMargin,
        EmptyBody, EmptyBody,
        List(UserHasMissingRoles, BankNotFound, UnknownError),
        List(apiTagBank, apiTagBankAttribute, apiTagAttribute),
        Some(List(canDeleteBankAttribute)),
        http4sPartialFunction = Some(deleteBankAttribute))

      staticResourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(deleteAtm), "DELETE",
        "/banks/BANK_ID/atms/ATM_ID",
        "Delete ATM",
        s"""Delete ATM.""",
        EmptyBody, EmptyBody,
        List(UserHasMissingRoles, UnknownError),
        List(apiTagATM),
        Some(List(canDeleteAtm, canDeleteAtmAtAnyBank)),
        http4sPartialFunction = Some(deleteAtm))

      staticResourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(deleteProductFee), "DELETE",
        "/banks/BANK_ID/products/PRODUCT_CODE/fees/PRODUCT_FEE_ID",
        "Delete Product Fee",
        s"""Delete Product Fee.""",
        EmptyBody, EmptyBody,
        List(UserHasMissingRoles, UnknownError),
        List(apiTagProduct),
        Some(List(canDeleteProductFee)),
        http4sPartialFunction = Some(deleteProductFee))

      staticResourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(deleteEndpointMapping), "DELETE",
        "/management/endpoint-mappings/ENDPOINT_MAPPING_ID",
        "Delete Endpoint Mapping",
        s"""Delete a Endpoint Mapping.""",
        EmptyBody, BooleanBody(true),
        List($AuthenticatedUserIsRequired, UserHasMissingRoles, InvalidJsonFormat, UnknownError),
        List(apiTagEndpointMapping),
        Some(List(canDeleteEndpointMapping)),
        http4sPartialFunction = Some(deleteEndpointMapping))

      staticResourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(deleteBankLevelEndpointMapping), "DELETE",
        "/management/banks/BANK_ID/endpoint-mappings/ENDPOINT_MAPPING_ID",
        "Delete Bank Level Endpoint Mapping",
        s"""Delete a Bank Level Endpoint Mapping.""",
        EmptyBody, BooleanBody(true),
        List($BankNotFound, $AuthenticatedUserIsRequired, UserHasMissingRoles, InvalidJsonFormat, UnknownError),
        List(apiTagEndpointMapping),
        Some(List(canDeleteBankLevelEndpointMapping, canDeleteEndpointMapping)),
        http4sPartialFunction = Some(deleteBankLevelEndpointMapping))
    }
    initBatch3ResourceDocs()

    // ═══════════════════════════════════════════════════════════════════════════
    // Batch 2 — more simple GETs
    // ═══════════════════════════════════════════════════════════════════════════

    lazy val getEntitlementsForBank: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / bankIdStr / "entitlements" =>
        EndpointHelpers.withUser(req) { (_, cc) =>
          for {
            entitlements <- NewStyle.function.getEntitlementsByBankId(bankIdStr, Some(cc))
          } yield JSONFactory400.createEntitlementJSONs(entitlements)
        }
    }

    lazy val getMyPersonalUserAttributes: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "my" / "user" / "attributes" =>
        EndpointHelpers.withUser(req) { (user, cc) =>
          for {
            (attributes, _) <- NewStyle.function.getPersonalUserAttributes(user.userId, Some(cc))
          } yield JSONFactory400.createUserAttributesJson(attributes)
        }
    }

    lazy val getUserWithAttributes: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "users" / userId / "attributes" =>
        EndpointHelpers.withUser(req) { (_, cc) =>
          for {
            (user, _) <- NewStyle.function.getUserByUserId(userId, Some(cc))
            (attributes, _) <- NewStyle.function.getUserAttributes(user.userId, Some(cc))
          } yield JSONFactory400.createUserWithAttributesJson(user, attributes)
        }
    }

    lazy val getCustomerAttributes: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / bankIdStr / "customers" / customerId / "attributes" =>
        EndpointHelpers.withUser(req) { (_, cc) =>
          for {
            (customer, _) <- NewStyle.function.getCustomerByCustomerId(customerId, Some(cc))
            _ <- code.util.Helper.booleanToFuture(
              InvalidCustomerBankId.replaceAll("Bank Id.", s"Bank Id ($bankIdStr).")
                .replaceAll("The Customer", s"The Customer($customerId)"),
              cc = Some(cc)) { customer.bankId == bankIdStr }
            (accountAttribute, _) <- NewStyle.function.getCustomerAttributes(
              BankId(bankIdStr), CustomerId(customerId), Some(cc))
          } yield JSONFactory400.createCustomerAttributesJson(accountAttribute)
        }
    }

    lazy val getCustomerAttributeById: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / bankIdStr / "customers" / customerId / "attributes" / customerAttributeId =>
        EndpointHelpers.withUser(req) { (_, cc) =>
          for {
            (customer, _) <- NewStyle.function.getCustomerByCustomerId(customerId, Some(cc))
            _ <- code.util.Helper.booleanToFuture(
              InvalidCustomerBankId.replaceAll("Bank Id.", s"Bank Id ($bankIdStr).")
                .replaceAll("The Customer", s"The Customer($customerId)"),
              cc = Some(cc)) { customer.bankId == bankIdStr }
            (accountAttribute, _) <- NewStyle.function.getCustomerAttributeById(customerAttributeId, Some(cc))
          } yield JSONFactory400.createCustomerAttributeJson(accountAttribute)
        }
    }

    private def getAttributeDefinitionImpl(
      category: com.openbankproject.commons.model.enums.AttributeCategory.Value,
      cc: CallContext): Future[JValue] = {
      for {
        (defs, _) <- code.api.util.newstyle.AttributeDefinition.getAttributeDefinition(category, Some(cc))
      } yield net.liftweb.json.Extraction.decompose(JSONFactory400.createAttributeDefinitionsJson(defs))
    }

    lazy val getProductAttributeDefinition: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / _ / "attribute-definitions" / "product" =>
        EndpointHelpers.withBank(req) { (_, cc) =>
          getAttributeDefinitionImpl(
            com.openbankproject.commons.model.enums.AttributeCategory.Product, cc)
        }
    }

    lazy val getCustomerAttributeDefinition: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / _ / "attribute-definitions" / "customer" =>
        EndpointHelpers.withBank(req) { (_, cc) =>
          getAttributeDefinitionImpl(
            com.openbankproject.commons.model.enums.AttributeCategory.Customer, cc)
        }
    }

    lazy val getAccountAttributeDefinition: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / _ / "attribute-definitions" / "account" =>
        EndpointHelpers.withBank(req) { (_, cc) =>
          getAttributeDefinitionImpl(
            com.openbankproject.commons.model.enums.AttributeCategory.Account, cc)
        }
    }

    lazy val getTransactionAttributeDefinition: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / _ / "attribute-definitions" / "transaction" =>
        EndpointHelpers.withBank(req) { (_, cc) =>
          getAttributeDefinitionImpl(
            com.openbankproject.commons.model.enums.AttributeCategory.Transaction, cc)
        }
    }

    lazy val getCardAttributeDefinition: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / _ / "attribute-definitions" / "card" =>
        EndpointHelpers.withBank(req) { (_, cc) =>
          getAttributeDefinitionImpl(
            com.openbankproject.commons.model.enums.AttributeCategory.Card, cc)
        }
    }

    lazy val getJsonSchemaValidation: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "management" / "json-schema-validations" / operationId =>
        EndpointHelpers.withUser(req) { (_, cc) =>
          for {
            (validation, _) <- NewStyle.function.getJsonSchemaValidationByOperationId(operationId, Some(cc))
          } yield validation
        }
    }

    lazy val getAllJsonSchemaValidations: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "management" / "json-schema-validations" =>
        EndpointHelpers.withUser(req) { (_, cc) =>
          for {
            (validations, _) <- NewStyle.function.getJsonSchemaValidations(Some(cc))
          } yield com.openbankproject.commons.model.ListResult("json_schema_validations", validations)
        }
      case req @ GET -> `prefixPath` / "endpoints" / "json-schema-validations" =>
        EndpointHelpers.executeAndRespond(req) { cc =>
          for {
            (validations, _) <- NewStyle.function.getJsonSchemaValidations(Some(cc))
          } yield com.openbankproject.commons.model.ListResult("json_schema_validations", validations)
        }
    }

    lazy val getAuthenticationTypeValidation: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "management" / "authentication-type-validations" / operationId =>
        EndpointHelpers.withUser(req) { (_, cc) =>
          for {
            (atv, _) <- NewStyle.function.getAuthenticationTypeValidationByOperationId(operationId, Some(cc))
          } yield atv
        }
    }

    lazy val getAllAuthenticationTypeValidations: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "management" / "authentication-type-validations" =>
        EndpointHelpers.withUser(req) { (_, cc) =>
          for {
            (atvs, _) <- NewStyle.function.getAuthenticationTypeValidations(Some(cc))
          } yield com.openbankproject.commons.model.ListResult("authentication_types_validations", atvs)
        }
      case req @ GET -> `prefixPath` / "endpoints" / "authentication-type-validations" =>
        EndpointHelpers.executeAndRespond(req) { cc =>
          for {
            (atvs, _) <- NewStyle.function.getAuthenticationTypeValidations(Some(cc))
          } yield com.openbankproject.commons.model.ListResult("authentication_types_validations", atvs)
        }
    }

    lazy val getConnectorMethod: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "management" / "connector-methods" / connectorMethodId =>
        EndpointHelpers.withUser(req) { (_, cc) =>
          for {
            (cm, _) <- NewStyle.function.getJsonConnectorMethodById(connectorMethodId, Some(cc))
          } yield cm
        }
    }

    lazy val getAllConnectorMethods: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "management" / "connector-methods" =>
        EndpointHelpers.withUser(req) { (_, cc) =>
          for {
            (methods, _) <- NewStyle.function.getJsonConnectorMethods(Some(cc))
          } yield com.openbankproject.commons.model.ListResult("connector_methods", methods)
        }
    }

    lazy val getUserCustomerLinksByUserId: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / _ / "user_customer_links" / "users" / userId =>
        EndpointHelpers.withUserAndBank(req) { (_, _, cc) =>
          for {
            (links, _) <- code.api.util.newstyle.UserCustomerLinkNewStyle
              .getUserCustomerLinksByUserId(userId, Some(cc))
          } yield code.api.v2_0_0.JSONFactory200.createUserCustomerLinkJSONs(links)
        }
    }

    lazy val getUserCustomerLinksByCustomerId: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / _ / "user_customer_links" / "customers" / customerId =>
        EndpointHelpers.withUserAndBank(req) { (_, _, cc) =>
          for {
            (links, _) <- code.api.util.newstyle.UserCustomerLinkNewStyle
              .getUserCustomerLinks(customerId, Some(cc))
          } yield code.api.v2_0_0.JSONFactory200.createUserCustomerLinkJSONs(links)
        }
    }

    lazy val getCustomerMessages: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / _ / "customers" / customerId / "messages" =>
        EndpointHelpers.withUserAndBank(req) { (_, bank, cc) =>
          for {
            (customer, _) <- NewStyle.function.getCustomerByCustomerId(customerId, Some(cc))
            (messages, _) <- NewStyle.function.getCustomerMessages(customer, bank.bankId, Some(cc))
          } yield JSONFactory400.createCustomerMessagesJson(messages)
        }
    }

    lazy val createCustomerMessage: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "banks" / _ / "customers" / customerId / "messages" =>
        EndpointHelpers.withUserAndBankAndBodyCreated[CreateMessageJsonV400, code.api.v1_2_1.SuccessMessage](req) {
          (_, bank, postedData, cc) =>
            for {
              (customer, _) <- NewStyle.function.getCustomerByCustomerId(customerId, Some(cc))
              (_, _) <- NewStyle.function.createCustomerMessage(
                customer, bank.bankId, postedData.transport, postedData.message,
                postedData.from_department, postedData.from_person, Some(cc))
            } yield successMessage
        }
    }

    private def initBatch2ResourceDocs(): Unit = {
      staticResourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(getEntitlementsForBank), "GET",
        "/banks/BANK_ID/entitlements",
        "Get Entitlements for One Bank",
        s"""${userAuthenticationMessage(true)}""".stripMargin,
        EmptyBody, entitlementsJsonV400,
        List($AuthenticatedUserIsRequired, BankNotFound, UserHasMissingRoles, UnknownError),
        List(apiTagRole, apiTagEntitlement, apiTagUser, apiTagBank),
        Some(List(canGetEntitlementsForOneBank, canGetEntitlementsForAnyBank)),
        http4sPartialFunction = Some(getEntitlementsForBank))

      staticResourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(getMyPersonalUserAttributes), "GET",
        "/my/user/attributes",
        "Get my personal User Attributes",
        s"""Get my personal User Attributes
           |
           |${userAuthenticationMessage(true)}""".stripMargin,
        EmptyBody, userAttributesResponseJson,
        List($AuthenticatedUserIsRequired, UnknownError),
        List(apiTagUser), None,
        http4sPartialFunction = Some(getMyPersonalUserAttributes))

      staticResourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(getUserWithAttributes), "GET",
        "/users/USER_ID/attributes",
        "Get User with Attributes by USER_ID",
        s"""Get User Attributes for the user defined via USER_ID.
           |
           |${userAuthenticationMessage(true)}""".stripMargin,
        EmptyBody, userWithAttributesResponseJson,
        List($AuthenticatedUserIsRequired, UnknownError),
        List(apiTagUser),
        Some(canGetUsersWithAttributes :: Nil),
        http4sPartialFunction = Some(getUserWithAttributes))

      staticResourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(getCustomerAttributes), "GET",
        "/banks/BANK_ID/customers/CUSTOMER_ID/attributes",
        "Get Customer Attributes",
        s"""Get Customer Attributes
           |
           |${userAuthenticationMessage(true)}""".stripMargin,
        EmptyBody, customerAttributesResponseJson,
        List($AuthenticatedUserIsRequired, $BankNotFound, InvalidJsonFormat, UnknownError),
        List(apiTagCustomer, apiTagCustomerAttribute, apiTagAttribute),
        Some(List(canGetCustomerAttributesAtOneBank, canGetCustomerAttributesAtAnyBank)),
        http4sPartialFunction = Some(getCustomerAttributes))

      staticResourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(getCustomerAttributeById), "GET",
        "/banks/BANK_ID/customers/CUSTOMER_ID/attributes/ATTRIBUTE_ID",
        "Get Customer Attribute By Id",
        s"""Get Customer Attribute By Id
           |
           |${userAuthenticationMessage(true)}""".stripMargin,
        EmptyBody, customerAttributeResponseJson,
        List($AuthenticatedUserIsRequired, $BankNotFound, InvalidJsonFormat, UnknownError),
        List(apiTagCustomer, apiTagCustomerAttribute, apiTagAttribute),
        Some(List(canGetCustomerAttributeAtOneBank, canGetCustomerAttributeAtAnyBank)),
        http4sPartialFunction = Some(getCustomerAttributeById))

      staticResourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(getProductAttributeDefinition), "GET",
        "/banks/BANK_ID/attribute-definitions/product",
        "Get Product Attribute Definition",
        s"""Get Product Attribute Definition
           |
           |${userAuthenticationMessage(true)}""".stripMargin,
        EmptyBody, productAttributeDefinitionsResponseJsonV400,
        List($AuthenticatedUserIsRequired, $BankNotFound, UnknownError),
        List(apiTagProduct, apiTagProductAttribute, apiTagAttribute),
        Some(List(canGetProductAttributeDefinitionAtOneBank)),
        http4sPartialFunction = Some(getProductAttributeDefinition))

      staticResourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(getCustomerAttributeDefinition), "GET",
        "/banks/BANK_ID/attribute-definitions/customer",
        "Get Customer Attribute Definition",
        s"""Get Customer Attribute Definition
           |
           |${userAuthenticationMessage(true)}""".stripMargin,
        EmptyBody, customerAttributeDefinitionsResponseJsonV400,
        List($AuthenticatedUserIsRequired, $BankNotFound, UnknownError),
        List(apiTagCustomer, apiTagCustomerAttribute, apiTagAttribute),
        Some(List(canGetCustomerAttributeDefinitionAtOneBank)),
        http4sPartialFunction = Some(getCustomerAttributeDefinition))

      staticResourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(getAccountAttributeDefinition), "GET",
        "/banks/BANK_ID/attribute-definitions/account",
        "Get Account Attribute Definition",
        s"""Get Account Attribute Definition
           |
           |${userAuthenticationMessage(true)}""".stripMargin,
        EmptyBody, accountAttributeDefinitionsResponseJsonV400,
        List($AuthenticatedUserIsRequired, $BankNotFound, UnknownError),
        List(apiTagAccount, apiTagAccountAttribute, apiTagAttribute),
        Some(List(canGetAccountAttributeDefinitionAtOneBank)),
        http4sPartialFunction = Some(getAccountAttributeDefinition))

      staticResourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(getTransactionAttributeDefinition), "GET",
        "/banks/BANK_ID/attribute-definitions/transaction",
        "Get Transaction Attribute Definition",
        s"""Get Transaction Attribute Definition
           |
           |${userAuthenticationMessage(true)}""".stripMargin,
        EmptyBody, transactionAttributeDefinitionsResponseJsonV400,
        List($AuthenticatedUserIsRequired, $BankNotFound, UnknownError),
        List(apiTagTransaction, apiTagTransactionAttribute, apiTagAttribute),
        Some(List(canGetTransactionAttributeDefinitionAtOneBank)),
        http4sPartialFunction = Some(getTransactionAttributeDefinition))

      staticResourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(getCardAttributeDefinition), "GET",
        "/banks/BANK_ID/attribute-definitions/card",
        "Get Card Attribute Definition",
        s"""Get Card Attribute Definition
           |
           |${userAuthenticationMessage(true)}""".stripMargin,
        EmptyBody, cardAttributeDefinitionsResponseJsonV400,
        List($AuthenticatedUserIsRequired, $BankNotFound, UnknownError),
        List(apiTagCard, apiTagCardAttribute, apiTagAttribute),
        Some(List(canGetCardAttributeDefinitionAtOneBank)),
        http4sPartialFunction = Some(getCardAttributeDefinition))

      staticResourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(getJsonSchemaValidation), "GET",
        "/management/json-schema-validations/OPERATION_ID",
        "Get a JSON Schema Validation",
        s"""Get a JSON Schema Validation by operation_id.""",
        EmptyBody, responseJsonSchema,
        List(InvalidJsonFormat, UnknownError),
        List(apiTagJsonSchemaValidation),
        Some(List(canGetJsonSchemaValidation)),
        http4sPartialFunction = Some(getJsonSchemaValidation))

      staticResourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(getAllJsonSchemaValidations), "GET",
        "/management/json-schema-validations",
        "Get all JSON Schema Validations",
        s"""Get all JSON Schema Validations.""",
        EmptyBody,
        com.openbankproject.commons.model.ListResult("json_schema_validations", responseJsonSchema :: Nil),
        List($AuthenticatedUserIsRequired, UserHasMissingRoles, InvalidJsonFormat, UnknownError),
        List(apiTagJsonSchemaValidation),
        Some(List(canGetJsonSchemaValidation)),
        http4sPartialFunction = Some(getAllJsonSchemaValidations))

      staticResourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(getAuthenticationTypeValidation), "GET",
        "/management/authentication-type-validations/OPERATION_ID",
        "Get an Authentication Type Validation",
        s"""Get an Authentication Type Validation by operation_id.""",
        EmptyBody, JsonAuthTypeValidation("OBPv4.0.0-updateXxx", List.empty),
        List(InvalidJsonFormat, UnknownError),
        List(apiTagAuthenticationTypeValidation),
        Some(List(canGetAuthenticationTypeValidation)),
        http4sPartialFunction = Some(getAuthenticationTypeValidation))

      staticResourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(getAllAuthenticationTypeValidations), "GET",
        "/management/authentication-type-validations",
        "Get all Authentication Type Validations",
        s"""Get all Authentication Type Validations.""",
        EmptyBody,
        com.openbankproject.commons.model.ListResult(
          "authentication_types_validations",
          List(JsonAuthTypeValidation("OBPv4.0.0-updateXxx", List.empty))),
        List($AuthenticatedUserIsRequired, UserHasMissingRoles, InvalidJsonFormat, UnknownError),
        List(apiTagAuthenticationTypeValidation),
        Some(List(canGetAuthenticationTypeValidation)),
        http4sPartialFunction = Some(getAllAuthenticationTypeValidations))

      staticResourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(getConnectorMethod), "GET",
        "/management/connector-methods/CONNECTOR_METHOD_ID",
        "Get Connector Method by Id",
        s"""Get an internal connector by CONNECTOR_METHOD_ID.""",
        EmptyBody, jsonScalaConnectorMethod,
        List($AuthenticatedUserIsRequired, UserHasMissingRoles, UnknownError),
        List(apiTagConnectorMethod),
        Some(List(canGetConnectorMethod)),
        http4sPartialFunction = Some(getConnectorMethod))

      staticResourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(getAllConnectorMethods), "GET",
        "/management/connector-methods",
        "Get all Connector Methods",
        s"""Get all Connector Methods.""",
        EmptyBody,
        com.openbankproject.commons.model.ListResult("connectors_methods", jsonScalaConnectorMethod :: Nil),
        List($AuthenticatedUserIsRequired, UserHasMissingRoles, UnknownError),
        List(apiTagConnectorMethod),
        Some(List(canGetAllConnectorMethods)),
        http4sPartialFunction = Some(getAllConnectorMethods))

      staticResourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(getUserCustomerLinksByUserId), "GET",
        "/banks/BANK_ID/user_customer_links/users/USER_ID",
        "Get User Customer Links by User",
        s"""Get User Customer Links by USER_ID
           |
           |${userAuthenticationMessage(true)}""".stripMargin,
        EmptyBody, userCustomerLinksJson,
        List($AuthenticatedUserIsRequired, $BankNotFound, UnknownError),
        List(apiTagCustomer),
        Some(List(canGetUserCustomerLink)),
        http4sPartialFunction = Some(getUserCustomerLinksByUserId))

      staticResourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(getUserCustomerLinksByCustomerId), "GET",
        "/banks/BANK_ID/user_customer_links/customers/CUSTOMER_ID",
        "Get User Customer Links by Customer",
        s"""Get User Customer Links by CUSTOMER_ID
           |
           |${userAuthenticationMessage(true)}""".stripMargin,
        EmptyBody, userCustomerLinksJson,
        List($AuthenticatedUserIsRequired, $BankNotFound, UnknownError),
        List(apiTagCustomer),
        Some(List(canGetUserCustomerLink)),
        http4sPartialFunction = Some(getUserCustomerLinksByCustomerId))

      staticResourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(getCustomerMessages), "GET",
        "/banks/BANK_ID/customers/CUSTOMER_ID/messages",
        "Get Messages for Customer",
        s"""Get messages for the logged in customer
           |
           |${userAuthenticationMessage(true)}""".stripMargin,
        EmptyBody, customerMessagesJson,
        List($AuthenticatedUserIsRequired, $BankNotFound, UnknownError),
        List(apiTagMessage, apiTagCustomer),
        Some(List(canGetCustomerMessages)),
        http4sPartialFunction = Some(getCustomerMessages))

      staticResourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(createCustomerMessage), "POST",
        "/banks/BANK_ID/customers/CUSTOMER_ID/messages",
        "Create Customer Message",
        s"""Create a message for the customer specified by CUSTOMER_ID
           |${userAuthenticationMessage(true)}""".stripMargin,
        createMessageJsonV400, successMessage,
        List($AuthenticatedUserIsRequired, $BankNotFound),
        List(apiTagMessage, apiTagCustomer, apiTagPerson),
        Some(List(canCreateCustomerMessage)),
        http4sPartialFunction = Some(createCustomerMessage))
    }
    initBatch2ResourceDocs()

    private def initBatch1ResourceDocs(): Unit = {
      staticResourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(getCallContext), "GET",
        "/development/call_context",
        "Get the Call Context of a current call",
        s"""Get the Call Context of the current call.""",
        EmptyBody, EmptyBody,
        List($AuthenticatedUserIsRequired, UnknownError),
        List(apiTagApi),
        Some(List(canGetCallContext)),
        http4sPartialFunction = Some(getCallContext))

      staticResourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(verifyRequestSignResponse), "GET",
        "/development/echo/jws-verified-request-jws-signed-response",
        "Verify Request and Sign Response of a current call",
        s"""Verify Request and Sign Response of a current call.""",
        EmptyBody, EmptyBody,
        List($AuthenticatedUserIsRequired, UnknownError),
        List(apiTagApi),
        Some(Nil),
        http4sPartialFunction = Some(verifyRequestSignResponse))

      staticResourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(getCurrentUserId), "GET",
        "/users/current/user_id",
        "Get User Id (Current)",
        s"""Get the USER_ID of the logged in user
           |
           |${userAuthenticationMessage(true)}""".stripMargin,
        EmptyBody, userIdJsonV400,
        List(AuthenticatedUserIsRequired, UnknownError),
        List(apiTagUser), None,
        http4sPartialFunction = Some(getCurrentUserId))

      staticResourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(getScannedApiVersions), "GET",
        "/api/versions",
        "Get scanned API Versions",
        s"""Get all the scanned API Versions.""",
        EmptyBody,
        com.openbankproject.commons.model.ListResult("scanned_api_versions",
          List(net.liftweb.json.Extraction.decompose(ApiVersion.v3_1_0))),
        List(UnknownError),
        List(apiTagDocumentation, apiTagApi),
        Some(Nil),
        http4sPartialFunction = Some(getScannedApiVersions))

      staticResourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(getMySpaces), "GET",
        "/my/spaces",
        "Get My Spaces",
        s"""Get My Spaces.""",
        EmptyBody, code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON.mySpaces,
        List($AuthenticatedUserIsRequired, UnknownError),
        List(apiTagUser), None,
        http4sPartialFunction = Some(getMySpaces))

      staticResourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(getBankAttributes), "GET",
        "/banks/BANK_ID/attributes",
        "Get Bank Attributes",
        s"""Get Bank Attributes
           |
           |${userAuthenticationMessage(true)}""".stripMargin,
        EmptyBody, bankAttributesResponseJsonV400,
        List($AuthenticatedUserIsRequired, $BankNotFound, InvalidJsonFormat, UnknownError),
        List(apiTagBank, apiTagBankAttribute, apiTagAttribute),
        Some(List(canGetBankAttribute)),
        http4sPartialFunction = Some(getBankAttributes))

      staticResourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(getBankAttribute), "GET",
        "/banks/BANK_ID/attributes/BANK_ATTRIBUTE_ID",
        "Get Bank Attribute By BANK_ATTRIBUTE_ID",
        s"""Get Bank Attribute By BANK_ATTRIBUTE_ID
           |
           |${userAuthenticationMessage(true)}""".stripMargin,
        EmptyBody, bankAttributeResponseJsonV400,
        List($AuthenticatedUserIsRequired, $BankNotFound, InvalidJsonFormat, UnknownError),
        List(apiTagBank, apiTagBankAttribute, apiTagAttribute),
        Some(List(canGetBankAttribute)),
        http4sPartialFunction = Some(getBankAttribute))

      staticResourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(getSystemLevelEndpointTags), "GET",
        "/management/endpoints/OPERATION_ID/tags",
        "Get System Level Endpoint Tags",
        s"""Get System Level Endpoint Tags.""",
        EmptyBody, bankLevelEndpointTagResponseJson400 :: Nil,
        List($AuthenticatedUserIsRequired, UserHasMissingRoles, UnknownError),
        List(apiTagApi),
        Some(List(canGetSystemLevelEndpointTag)),
        http4sPartialFunction = Some(getSystemLevelEndpointTags))

      staticResourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(getBankLevelEndpointTags), "GET",
        "/management/banks/BANK_ID/endpoints/OPERATION_ID/tags",
        "Get Bank Level Endpoint Tags",
        s"""Get Bank Level Endpoint Tags.""",
        EmptyBody, bankLevelEndpointTagResponseJson400 :: Nil,
        List($AuthenticatedUserIsRequired, $BankNotFound, UserHasMissingRoles, UnknownError),
        List(apiTagApi),
        Some(List(canGetBankLevelEndpointTag)),
        http4sPartialFunction = Some(getBankLevelEndpointTags))

      staticResourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(getEndpointMapping), "GET",
        "/management/endpoint-mappings/ENDPOINT_MAPPING_ID",
        "Get Endpoint Mapping by Id",
        s"""Get an Endpoint Mapping by ENDPOINT_MAPPING_ID.""",
        EmptyBody, endpointMappingResponseBodyExample,
        List($AuthenticatedUserIsRequired, UserHasMissingRoles, UnknownError),
        List(apiTagEndpointMapping),
        Some(List(canGetEndpointMapping)),
        http4sPartialFunction = Some(getEndpointMapping))

      staticResourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(getBankLevelEndpointMapping), "GET",
        "/management/banks/BANK_ID/endpoint-mappings/ENDPOINT_MAPPING_ID",
        "Get Bank Level Endpoint Mapping",
        s"""Get an Bank Level Endpoint Mapping by ENDPOINT_MAPPING_ID.""",
        EmptyBody, endpointMappingResponseBodyExample,
        List($BankNotFound, $AuthenticatedUserIsRequired, UserHasMissingRoles, UnknownError),
        List(apiTagEndpointMapping),
        Some(List(canGetBankLevelEndpointMapping, canGetEndpointMapping)),
        http4sPartialFunction = Some(getBankLevelEndpointMapping))

      staticResourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(getAllEndpointMappings), "GET",
        "/management/endpoint-mappings",
        "Get all Endpoint Mappings",
        s"""Get all Endpoint Mappings.""",
        EmptyBody,
        com.openbankproject.commons.model.ListResult("endpoint-mappings", endpointMappingResponseBodyExample :: Nil),
        List($AuthenticatedUserIsRequired, UserHasMissingRoles, UnknownError),
        List(apiTagEndpointMapping),
        Some(List(canGetAllEndpointMappings)),
        http4sPartialFunction = Some(getAllEndpointMappings))

      staticResourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(getAllBankLevelEndpointMappings), "GET",
        "/management/banks/BANK_ID/endpoint-mappings",
        "Get all Bank Level Endpoint Mappings",
        s"""Get all Bank Level Endpoint Mappings.""",
        EmptyBody,
        com.openbankproject.commons.model.ListResult("endpoint-mappings", endpointMappingResponseBodyExample :: Nil),
        List($BankNotFound, $AuthenticatedUserIsRequired, UserHasMissingRoles, UnknownError),
        List(apiTagEndpointMapping),
        Some(List(canGetAllBankLevelEndpointMappings, canGetAllEndpointMappings)),
        http4sPartialFunction = Some(getAllBankLevelEndpointMappings))
    }
    initBatch1ResourceDocs()

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
        .orElse(createTransactionRequest.run(req))
        .orElse(answerTransactionRequestChallenge.run(req))
        // Batch 1 — simple GETs
        .orElse(getCallContext.run(req))
        .orElse(verifyRequestSignResponse.run(req))
        .orElse(getCurrentUserId.run(req))
        .orElse(getScannedApiVersions.run(req))
        .orElse(getMySpaces.run(req))
        .orElse(getBankAttributes.run(req))
        .orElse(getBankAttribute.run(req))
        .orElse(getSystemLevelEndpointTags.run(req))
        .orElse(getBankLevelEndpointTags.run(req))
        .orElse(getEndpointMapping.run(req))
        .orElse(getBankLevelEndpointMapping.run(req))
        .orElse(getAllEndpointMappings.run(req))
        .orElse(getAllBankLevelEndpointMappings.run(req))
        // Batch 2 — more GETs
        .orElse(getEntitlementsForBank.run(req))
        .orElse(getMyPersonalUserAttributes.run(req))
        .orElse(getUserWithAttributes.run(req))
        .orElse(getCustomerAttributes.run(req))
        .orElse(getCustomerAttributeById.run(req))
        .orElse(getProductAttributeDefinition.run(req))
        .orElse(getCustomerAttributeDefinition.run(req))
        .orElse(getAccountAttributeDefinition.run(req))
        .orElse(getTransactionAttributeDefinition.run(req))
        .orElse(getCardAttributeDefinition.run(req))
        .orElse(getJsonSchemaValidation.run(req))
        .orElse(getAllJsonSchemaValidations.run(req))
        .orElse(getAuthenticationTypeValidation.run(req))
        .orElse(getAllAuthenticationTypeValidations.run(req))
        .orElse(getConnectorMethod.run(req))
        .orElse(getAllConnectorMethods.run(req))
        .orElse(getUserCustomerLinksByUserId.run(req))
        .orElse(getUserCustomerLinksByCustomerId.run(req))
        .orElse(getCustomerMessages.run(req))
        .orElse(createCustomerMessage.run(req))
        // Batch 3 — DELETEs
        .orElse(deleteTransactionAttributeDefinition.run(req))
        .orElse(deleteCustomerAttributeDefinition.run(req))
        .orElse(deleteAccountAttributeDefinition.run(req))
        .orElse(deleteProductAttributeDefinition.run(req))
        .orElse(deleteCardAttributeDefinition.run(req))
        .orElse(deleteTransactionRequestAttributeDefinition.run(req))
        .orElse(deleteUser.run(req))
        .orElse(deleteUserCustomerLink.run(req))
        .orElse(deleteTransactionCascade.run(req))
        .orElse(deleteAccountCascade.run(req))
        .orElse(deleteBankCascade.run(req))
        .orElse(deleteProductCascade.run(req))
        .orElse(deleteCustomerCascade.run(req))
        .orElse(deleteSystemLevelEndpointTag.run(req))
        .orElse(deleteBankLevelEndpointTag.run(req))
        .orElse(deleteAuthenticationTypeValidation.run(req))
        .orElse(deleteJsonSchemaValidation.run(req))
        .orElse(deleteCustomerAttribute.run(req))
        .orElse(deleteBankAttribute.run(req))
        .orElse(deleteAtm.run(req))
        .orElse(deleteProductFee.run(req))
        .orElse(deleteEndpointMapping.run(req))
        .orElse(deleteBankLevelEndpointMapping.run(req))
        // Batch 4 — Consents, ApiCollections
        .orElse(getConsentInfosByBank.run(req))
        .orElse(getConsentInfos.run(req))
        .orElse(getMyApiCollectionByName.run(req))
        .orElse(getMyApiCollectionById.run(req))
        .orElse(getSharableApiCollectionById.run(req))
        .orElse(getApiCollectionsForUser.run(req))
        .orElse(getFeaturedApiCollections.run(req))
        .orElse(getMyApiCollections.run(req))
        .orElse(getMyApiCollectionEndpoint.run(req))
        .orElse(getApiCollectionEndpoints.run(req))
        .orElse(getMyApiCollectionEndpoints.run(req))
        .orElse(getMyApiCollectionEndpointsById.run(req))
        .orElse(deleteMyApiCollection.run(req))
        .orElse(deleteMyApiCollectionEndpoint.run(req))
        .orElse(deleteMyApiCollectionEndpointByOperationId.run(req))
        .orElse(deleteMyApiCollectionEndpointById.run(req))
        // Batch 5 — more GETs
        .orElse(getProductFee.run(req))
        .orElse(getProductFees.run(req))
        .orElse(getTransactionAttributes.run(req))
        .orElse(getTransactionAttributeById.run(req))
        .orElse(getTransactionRequestAttributes.run(req))
        .orElse(getTransactionRequestAttributeById.run(req))
        .orElse(getTransactionRequestAttributeDefinition.run(req))
        .orElse(getTransactionRequest.run(req))
        .orElse(getMyCorrelatedEntities.run(req))
        .orElse(getCorrelatedUsersInfoByCustomerId.run(req))
        .orElse(getAccountsMinimalByCustomerId.run(req))
        .orElse(getCustomersByCustomerPhoneNumber.run(req))
        .orElse(getCustomersAtAnyBank.run(req))
        .orElse(getCustomersMinimalAtAnyBank.run(req))
        .orElse(getUserInvitation.run(req))
        .orElse(getUserInvitations.run(req))
        // Batch 6 — ATM updates
        .orElse(updateAtmSupportedCurrencies.run(req))
        .orElse(updateAtmSupportedLanguages.run(req))
        .orElse(updateAtmAccessibilityFeatures.run(req))
        .orElse(updateAtmServices.run(req))
        .orElse(updateAtmNotes.run(req))
        .orElse(updateAtmLocationCategories.run(req))
        .orElse(updateAtm.run(req))
        // Batch 7 — Attribute Definitions PUT
        .orElse(createOrUpdateCustomerAttributeAttributeDefinition.run(req))
        .orElse(createOrUpdateAccountAttributeDefinition.run(req))
        .orElse(createOrUpdateProductAttributeDefinition.run(req))
        .orElse(createOrUpdateTransactionAttributeDefinition.run(req))
        .orElse(createOrUpdateCardAttributeDefinition.run(req))
        .orElse(createOrUpdateBankAttributeDefinition.run(req))
        // Batch 8 — Counterparty management
        .orElse(getCounterpartiesForAnyAccount.run(req))
        .orElse(getCounterpartyByIdForAnyAccount.run(req))
        .orElse(getCounterpartyByNameForAnyAccount.run(req))
        // Batch 9 — Remaining v4 migrations
        .orElse(createTransactionRequestCard.run(req))
        .orElse(deleteExplicitCounterparty.run(req))
        .orElse(deleteCounterpartyForAnyAccount.run(req))
        .orElse(deleteTagForViewOnAccount.run(req))
        .orElse(getTagsForViewOnAccount.run(req))
        .orElse(addTagForViewOnAccount.run(req))
        .orElse(getDoubleEntryTransaction.run(req))
        .orElse(getBalancingTransaction.run(req))
        .orElse(getBankAccountBalancesForCurrentUser.run(req))
        .orElse(getAccountByAccountRouting.run(req))
        .orElse(getAccountsByAccountRoutingRegex.run(req))
        .orElse(lockUser.run(req))
        .orElse(resetPasswordUrl.run(req))
        .orElse(getSettlementAccounts.run(req))
        // Batch 10 — Attribute create/update
        .orElse(createBankAttribute.run(req))
        .orElse(updateBankAttribute.run(req))
        .orElse(createCustomerAttribute.run(req))
        .orElse(updateCustomerAttribute.run(req))
        .orElse(createTransactionAttribute.run(req))
        .orElse(updateTransactionAttribute.run(req))
        .orElse(createTransactionRequestAttribute.run(req))
        .orElse(updateTransactionRequestAttribute.run(req))
        .orElse(createProductFee.run(req))
        .orElse(updateProductFee.run(req))
        .orElse(createMyPersonalUserAttribute.run(req))
        .orElse(updateMyPersonalUserAttribute.run(req))
        // Batch 11 — account access, user invitations, consents, api collections
        .orElse(getUserInvitationAnonymous.run(req))
        .orElse(grantUserAccessToView.run(req))
        .orElse(revokeUserAccessToView.run(req))
        .orElse(revokeGrantUserAccessToViews.run(req))
        .orElse(createMyApiCollection.run(req))
        .orElse(createMyApiCollectionEndpoint.run(req))
        .orElse(createMyApiCollectionEndpointById.run(req))
        .orElse(updateConsentStatus.run(req))
        .orElse(addConsentUser.run(req))
        // Batch 12 — direct debits, standing orders, webhooks, fast firehose
        .orElse(createDirectDebit.run(req))
        .orElse(createDirectDebitManagement.run(req))
        .orElse(createStandingOrder.run(req))
        .orElse(createStandingOrderManagement.run(req))
        .orElse(createSystemAccountNotificationWebhook.run(req))
        .orElse(createBankAccountNotificationWebhook.run(req))
        .orElse(getFastFirehoseAccountsAtOneBank.run(req))
    }

    lazy val allRoutesWithMiddleware: HttpRoutes[IO] = ResourceDocMiddleware.apply(resourceDocs)(allOwnRoutes)

    // ─── path-rewriting bridge: /obp/v4.0.0/… → /obp/v3.1.0/… ──────────────

    lazy val v400ToV310Bridge: HttpRoutes[IO] = Kleisli[HttpF, Request[IO], Response[IO]] { req =>
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
