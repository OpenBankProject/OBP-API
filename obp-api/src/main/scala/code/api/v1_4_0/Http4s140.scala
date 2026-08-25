package code.api.v1_4_0

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
import code.api.util.http4s.IdempotencyMiddleware
import code.api.util.{APIUtil, NewStyle}
import code.api.v1_2_1.{JSONFactory, SuccessMessage}
import code.atms.Atms
import code.bankconnectors.Connector
import code.branches.Branches
import code.customer.{CustomerMessages, CustomerX}
import code.products.Products
import code.usercustomerlinks.UserCustomerLink
import code.views.system.ViewPermission
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.model.CustomerFaceImage
import com.openbankproject.commons.util.{ApiVersion, ApiVersionStatus, ScannedApiVersion}
import org.http4s._
import org.http4s.dsl.io._

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Future

object Http4s140 {
  val implementedInApiVersion: ScannedApiVersion = ApiVersion.v1_4_0
  val versionStatus: String                       = ApiVersionStatus.DEPRECATED.toString
  val resourceDocs: ArrayBuffer[ResourceDoc]       = ArrayBuffer[ResourceDoc]()

  type HttpF[A] = OptionT[IO, A]

  object Implementations1_4_0 {
    val prefixPath = Root / ApiPathZero.toString / implementedInApiVersion.toString

    // ─── root ─────────────────────────────────────────────────────────────────

    val root: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` =>
        EndpointHelpers.executeAndRespond(req) { _ =>
          Future.successful(JSONFactory.getApiInfoJSON(ApiVersion.v1_4_0, versionStatus))
        }
      case req @ GET -> `prefixPath` / "root" =>
        EndpointHelpers.executeAndRespond(req) { _ =>
          Future.successful(JSONFactory.getApiInfoJSON(ApiVersion.v1_4_0, versionStatus))
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(root),
      "GET",
      "/root",
      "Get API Info (root)",
      """Returns information about:
        |
        |* API version
        |* Hosted by information
        |* Git Commit""",
      EmptyBody,
      apiInfoJSON,
      List(UnknownError, MandatoryPropertyIsNotSet),
      apiTagApi :: Nil,
      None,
      http4sPartialFunction = Some(root)
    )

    // ─── getCustomer ──────────────────────────────────────────────────────────

    val getCustomer: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / _ / "customer" =>
        EndpointHelpers.withUserAndBank(req) { (user, bank, cc) =>
          for {
            ucls <- Future { UserCustomerLink.userCustomerLink.vend.getUserCustomerLinksByUserId(user.userId) }
            matchingUcl <- Future {
              ucls.find(x => CustomerX.customerProvider.vend.getBankIdByCustomerId(x.customerId) == bank.bankId.value)
                .getOrElse(throw new RuntimeException(UserCustomerLinksNotFoundForUser))
            }
            (customer, _) <- NewStyle.function.getCustomerByCustomerId(matchingUcl.customerId, Some(cc))
          } yield JSONFactory1_4_0.createCustomerJson(customer)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(getCustomer),
      "GET",
      "/banks/BANK_ID/customer",
      "Get customer for logged in user",
      """Information about the currently authenticated user.
      |
      |Authentication via OAuth is required.""",
      EmptyBody,
      customerJsonV140,
      List(AuthenticatedUserIsRequired, UnknownError),
      List(apiTagCustomer, apiTagOldStyle),
      None,
      http4sPartialFunction = Some(getCustomer)
    )

    // ─── getCustomersMessages ─────────────────────────────────────────────────

    val getCustomersMessages: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / _ / "customer" / "messages" =>
        EndpointHelpers.withUserAndBank(req) { (user, bank, cc) =>
          Future {
            val messages = CustomerMessages.customerMessageProvider.vend.getMessages(user, bank.bankId)
            JSONFactory1_4_0.createCustomerMessagesJson(messages)
          }
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(getCustomersMessages),
      "GET",
      "/banks/BANK_ID/customer/messages",
      "Get Customer Messages for all Customers",
      """Get messages for the logged in customer
      |Messages sent to the currently authenticated user.
      |
      |Authentication via OAuth is required.""",
      EmptyBody,
      customerMessagesJson,
      List(AuthenticatedUserIsRequired, UnknownError),
      List(apiTagMessage, apiTagCustomer),
      None,
      http4sPartialFunction = Some(getCustomersMessages)
    )

    // ─── addCustomerMessage ───────────────────────────────────────────────────

    val addCustomerMessage: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "banks" / _ / "customer" / customerId / "messages" =>
        EndpointHelpers.withUserAndBankAndBodyCreated[JSONFactory1_4_0.AddCustomerMessageJson, SuccessMessage](req) { (_, bank, body, cc) =>
          for {
            (_, cc2)          <- NewStyle.function.getCustomerByCustomerId(customerId, Some(cc))
            (ucl, cc3)        <- NewStyle.function.getUserCustomerLinkByCustomerId(customerId, cc2)
            (targetUser, cc4) <- NewStyle.function.findByUserId(ucl.userId, cc3)
            (_, _)            <- NewStyle.function.createMessage(targetUser, bank.bankId, body.message, body.from_department, body.from_person, cc4)
          } yield SuccessMessage("Success")
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(addCustomerMessage),
      "POST",
      "/banks/BANK_ID/customer/CUSTOMER_ID/messages",
      "Create Customer Message",
      "Create a message for the customer specified by CUSTOMER_ID",
      addCustomerMessageJson,
      successMessage,
      List(AuthenticatedUserIsRequired, UnknownError),
      List(apiTagMessage, apiTagCustomer, apiTagPerson),
      None,
      http4sPartialFunction = Some(addCustomerMessage)
    )

    // ─── getBranches ──────────────────────────────────────────────────────────

    private val getBranchesIsPublic = APIUtil.getPropsAsBoolValue("apiOptions.getBranchesIsPublic", true)

    val getBranches: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / _ / "branches" =>
        EndpointHelpers.withBank(req) { (bank, cc) =>
          for {
            httpParams         <- NewStyle.function.extractHttpParamsFromUrl(cc.url)
            (obpQueryParams, _) <- createQueriesByHttpParamsFuture(httpParams, Some(cc))
            branches <- Future {
              Branches.branchesProvider.vend.getBranches(bank.bankId, obpQueryParams)
                .getOrElse(throw new RuntimeException("No branches available. License may not be set."))
            }
          } yield JSONFactory1_4_0.createBranchesJson(branches)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(getBranches),
      "GET",
      "/banks/BANK_ID/branches",
      "Get Bank Branches",
      s"""Returns information about branches for a single bank specified by BANK_ID including:
        |
        |* Name
        |* Address
        |* Geo Location
        |* License the data under this endpoint is released under
        |
        ${urlParametersDocument(false, false)}
        |
        |You can use the url query parameters *limit* and *offset* for pagination
        |
        |${userAuthenticationMessage(!getBranchesIsPublic)}""".stripMargin,
      EmptyBody,
      branchesJson,
      List(AuthenticatedUserIsRequired, BankNotFound, "No branches available. License may not be set.", UnknownError),
      List(apiTagBranch, apiTagOldStyle),
      None,
      http4sPartialFunction = Some(getBranches)
    )

    // ─── getAtms ──────────────────────────────────────────────────────────────

    private val getAtmsIsPublic = APIUtil.getPropsAsBoolValue("apiOptions.getAtmsIsPublic", true)

    val getAtms: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / _ / "atms" =>
        EndpointHelpers.withBank(req) { (bank, cc) =>
          for {
            httpParams         <- NewStyle.function.extractHttpParamsFromUrl(cc.url)
            (obpQueryParams, _) <- createQueriesByHttpParamsFuture(httpParams, Some(cc))
            atms <- Future {
              Atms.atmsProvider.vend.getAtms(bank.bankId, obpQueryParams)
                .getOrElse(throw new RuntimeException("No ATMs available. License may not be set."))
            }
          } yield JSONFactory1_4_0.createAtmsJson(atms)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(getAtms),
      "GET",
      "/banks/BANK_ID/atms",
      "Get Bank ATMS",
      s"""Returns information about ATMs for a single bank specified by BANK_ID including:
         |
         |* Address
         |* Geo Location
         |* License the data under this endpoint is released under
         |
         |${urlParametersDocument(false, false)}
         |
         |${userAuthenticationMessage(!getAtmsIsPublic)}""".stripMargin,
      EmptyBody,
      atmsJson,
      List(AuthenticatedUserIsRequired, BankNotFound, "No ATMs available. License may not be set.", UnknownError),
      List(apiTagBank, apiTagOldStyle),
      None,
      http4sPartialFunction = Some(getAtms)
    )

    // ─── getProducts ──────────────────────────────────────────────────────────

    private val getProductsIsPublic = APIUtil.getPropsAsBoolValue("apiOptions.getProductsIsPublic", true)

    val getProducts: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / _ / "products" =>
        EndpointHelpers.withBank(req) { (bank, cc) =>
          Future {
            val products = Products.productsProvider.vend.getProducts(bank.bankId)
              .getOrElse(throw new RuntimeException("No products available. License may not be set."))
            JSONFactory1_4_0.createProductsJson(products)
          }
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(getProducts),
      "GET",
      "/banks/BANK_ID/products",
      "Get Bank Products",
      s"""Returns information about the financial products offered by a bank specified by BANK_ID including:
        |
        |* Name
        |* Code
        |* Category
        |* Family
        |* Super Family
        |* More info URL
        |* Description
        |* Terms and Conditions
        |* License the data under this endpoint is released under
        |${userAuthenticationMessage(!getProductsIsPublic)}""".stripMargin,
      EmptyBody,
      productsJson,
      List(AuthenticatedUserIsRequired, BankNotFound, "No products available.", "License may not be set.", UnknownError),
      List(apiTagBank, apiTagOldStyle),
      None,
      http4sPartialFunction = Some(getProducts)
    )

    // ─── getCrmEvents ─────────────────────────────────────────────────────────

    val getCrmEvents: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / _ / "crm-events" =>
        EndpointHelpers.withUserAndBank(req) { (_, bank, cc) =>
          NewStyle.function.getCrmEvents(bank.bankId, Some(cc))
            .map(JSONFactory1_4_0.createCrmEventsJson)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(getCrmEvents),
      "GET",
      "/banks/BANK_ID/crm-events",
      "Get CRM Events",
      "",
      EmptyBody,
      crmEventsJson,
      List(AuthenticatedUserIsRequired, BankNotFound, "No CRM Events available.", UnknownError),
      List(apiTagCustomer),
      None,
      http4sPartialFunction = Some(getCrmEvents)
    )

    // ─── getTransactionRequestTypes ───────────────────────────────────────────

    val getTransactionRequestTypes: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / _ / "accounts" / _ / _ / "transaction-request-types" =>
        EndpointHelpers.withView(req) { (user, fromAccount, view, cc) =>
          for {
            _ <- NewStyle.function.isEnabledTransactionRequests(Some(cc))
            failMsg = InvalidISOCurrencyCode.concat("Please specify a valid value for CURRENCY of your Bank Account. ")
            _ <- NewStyle.function.isValidCurrencyISOCode(fromAccount.currency, failMsg, Some(cc))
            _ <- code.util.Helper.booleanToFuture(
              s"$ViewDoesNotPermitAccess You need the `$CAN_SEE_TRANSACTION_REQUEST_TYPES` permission on the View(${view.viewId.value})",
              cc = Some(cc)
            ) {
              ViewPermission.findViewPermissions(view).exists(_.permission.get == CAN_SEE_TRANSACTION_REQUEST_TYPES)
            }
            (transactionRequestTypes, cc2) <- Future {
              connectorEmptyResponse(
                Connector.connector.vend.getTransactionRequestTypes(user, fromAccount, Some(cc)),
                Some(cc)
              )
            }
            (charges, _) <- NewStyle.function.getTransactionRequestTypeCharges(
              fromAccount.bankId, fromAccount.accountId, view.viewId, transactionRequestTypes, cc2
            )
          } yield JSONFactory1_4_0.createTransactionRequestTypesJSONs(charges)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(getTransactionRequestTypes),
      "GET",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/transaction-request-types",
      "Get Transaction Request Types for Account",
      """Returns the Transaction Request Types that the account specified by ACCOUNT_ID and view specified by VIEW_ID has access to.
        |
        |These are the ways this API Server can create a Transaction via a Transaction Request
        |(as opposed to Transaction Types which include external types too e.g. for Transactions created by core banking etc.)
        |
        | A Transaction Request Type internally determines:
        |
        | * the required Transaction Request 'body' i.e. fields that define the 'what' and 'to' of a Transaction Request,
        | * the type of security challenge that may be be raised before the Transaction Request proceeds, and
        | * the threshold of that challenge.
        |
        | For instance in a 'SANDBOX_TAN' Transaction Request, for amounts over 1000 currency units, the user must supply a positive integer to complete the Transaction Request and create a Transaction.
        |
        | This approach aims to provide only one endpoint for initiating transactions, and one that handles challenges, whilst still allowing flexibility with the payload and internal logic.
      """.stripMargin,
      EmptyBody,
      transactionRequestTypesJsonV140,
      List(
        AuthenticatedUserIsRequired,
        BankNotFound,
        AccountNotFound,
        "Please specify a valid value for CURRENCY of your Bank Account. ",
        "Current user does not have access to the view ",
        TransactionRequestsNotEnabled,
        UnknownError),
      List(apiTagTransactionRequest, apiTagPSD2PIS, apiTagPsd2),
      None,
      http4sPartialFunction = Some(getTransactionRequestTypes)
    )

    // ─── addCustomer ─────────────────────────────────────────────────────────

    val addCustomer: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "banks" / _ / "customer" =>
        EndpointHelpers.withUserAndBankAndBody[code.api.v2_0_0.CreateCustomerJson, JSONFactory1_4_0.CustomerJsonV140](req) { (user, bank, body, cc) =>
          for {
            _ <- code.util.Helper.booleanToFuture(
              s"${UserHasMissingRoles}${canCreateCustomer} and ${canCreateUserCustomerLink} entitlements are required for BankId(${bank.bankId.value}).",
              failCode = 403,
              cc = Some(cc)
            ) {
              APIUtil.hasAllEntitlements(bank.bankId.value, user.userId, canCreateCustomer :: canCreateUserCustomerLink :: Nil)
            }
            _ <- code.util.Helper.booleanToFuture(CustomerNumberAlreadyExists, cc = Some(cc)) {
              CustomerX.customerProvider.vend.checkCustomerNumberAvailable(bank.bankId, body.customer_number)
            }
            userId = if (body.user_id.nonEmpty) body.user_id else user.userId
            (_, _) <- NewStyle.function.findByUserId(userId, Some(cc))
            customer <- Future {
              CustomerX.customerProvider.vend.addCustomer(
                bankId                   = bank.bankId,
                number                   = body.customer_number,
                legalName                = body.legal_name,
                mobileNumber             = body.mobile_phone_number,
                email                    = body.email,
                faceImage                = CustomerFaceImage(body.face_image.date, body.face_image.url),
                dateOfBirth              = body.date_of_birth,
                relationshipStatus       = body.relationship_status,
                dependents               = body.dependants,
                dobOfDependents          = body.dob_of_dependants,
                highestEducationAttained = body.highest_education_attained,
                employmentStatus         = body.employment_status,
                kycStatus                = body.kyc_status,
                lastOkDate               = body.last_ok_date,
                creditRating             = None,
                creditLimit              = None,
                title                    = body.title,
                branchId                 = body.branchId,
                nameSuffix               = body.nameSuffix
              ).getOrElse(throw new RuntimeException("Could not create customer"))
            }
            _ <- Future {
              UserCustomerLink.userCustomerLink.vend
                .createUserCustomerLink(userId, customer.customerId, DateWithMsExampleObject, true)
                .getOrElse(throw new RuntimeException("Could not create user_customer_links"))
            }
          } yield JSONFactory1_4_0.createCustomerJson(customer)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(addCustomer),
      "POST",
      "/banks/BANK_ID/customer",
      "Add a customer.",
      s"""Add a customer linked to the currently authenticated user.
         |The Customer resource stores the customer number, legal name, email, phone number, their date of birth, relationship status, education attained, a url for a profile image, KYC status etc.
         |This call may require additional permissions/role in the future.
         |For now the authenticated user can create at most one linked customer.
         |Dates need to be in the format 2013-01-21T23:08:00Z
         |${userAuthenticationMessage(true)}
         |Note: This call is depreciated in favour of v.2.0.0 createCustomer
         |""",
      code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON.createCustomerJson,
      customerJsonV140,
      List(
        AuthenticatedUserIsRequired,
        BankNotFound,
        InvalidJsonFormat,
        "entitlements required",
        CustomerNumberAlreadyExists,
        "Problem getting user_id",
        UserNotFoundById,
        "Could not create customer",
        "Could not create user_customer_links",
        UnknownError),
      List(apiTagCustomer, apiTagOldStyle),
      Some(List(canCreateCustomer, canCreateUserCustomerLink)),
      http4sPartialFunction = Some(addCustomer)
    )

    // ─── allRoutes ────────────────────────────────────────────────────────────

    private val allOwnRoutes: HttpRoutes[IO] = Kleisli[HttpF, Request[IO], Response[IO]] { req =>
      root.run(req)
        .orElse(getCustomer.run(req))
        .orElse(getCustomersMessages.run(req))
        .orElse(addCustomerMessage.run(req))
        .orElse(getBranches.run(req))
        .orElse(getAtms.run(req))
        .orElse(getProducts.run(req))
        .orElse(getCrmEvents.run(req))
        .orElse(getTransactionRequestTypes.run(req))
        .orElse(addCustomer.run(req))
    }

    val allRoutesWithMiddleware: HttpRoutes[IO] = ResourceDocMiddleware.apply(resourceDocs)(IdempotencyMiddleware(allOwnRoutes))

    // ─── path-rewriting bridge: /obp/v1.4.0/… → /obp/v1.3.0/… ──────────────
    // Delegates to Http4s130 so all inherited v1.3.0 and v1.2.1 endpoints are
    // served under the v1.4.0 URL prefix without duplicating any logic.

    val v140ToV130Bridge: HttpRoutes[IO] = Kleisli[HttpF, Request[IO], Response[IO]] { req =>
      val rawPath = req.uri.path.renderString
      if (rawPath.startsWith("/obp/v1.4.0/")) {
        val rewritten    = rawPath.replaceFirst("/obp/v1\\.4\\.0/", "/obp/v1.3.0/")
        val newUri       = req.uri.withPath(Uri.Path.unsafeFromString(rewritten))
        val rewrittenReq = req.withUri(newUri)
        code.api.v1_3_0.Http4s130.wrappedRoutesV130Services.run(rewrittenReq)
      } else {
        OptionT.none[IO, Response[IO]]
      }
    }
  }

  // Own middleware-wrapped routes take priority; inherited v1.3.0 and v1.2.1 paths follow.
  val wrappedRoutesV140Services: HttpRoutes[IO] =
    Kleisli[HttpF, Request[IO], Response[IO]] { req =>
      Implementations1_4_0.allRoutesWithMiddleware.run(req)
        .orElse(Implementations1_4_0.v140ToV130Bridge.run(req))
    }
}
