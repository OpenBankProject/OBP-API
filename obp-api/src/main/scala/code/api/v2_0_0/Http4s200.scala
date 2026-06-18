package code.api.v2_0_0

import org.json4s._
import cats.data.{Kleisli, OptionT}
import cats.effect._
import code.TransactionTypes.TransactionType
import code.api.APIFailureNewStyle
import code.api.Constant._
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON._
import code.api.util.APIUtil.{EmptyBody, ResourceDoc, _}
import code.api.util.ApiRole._
import code.api.util.ApiTag._
import code.api.util.ErrorMessages._
import code.api.util.http4s.Http4sRequestAttributes.{EndpointHelpers, RequestOps}
import code.api.util.http4s.ResourceDocMiddleware
import code.api.util.newstyle.ViewNewStyle
import code.api.util.{APIUtil, ApiRole, CustomJsonFormats, NewStyle}
import code.api.v1_2_1.{JSONFactory => JSONFactory121, SuccessMessage}
import code.api.v1_4_0.JSONFactory1_4_0
import code.api.v2_0_0.JSONFactory200
import code.api.v2_0_0.JSONFactory200._
import code.customer.CustomerX
import code.entitlement.Entitlement
import code.model.dataAccess.{AuthUser, BankAccountCreation}
import code.model.{BankAccountX, BankExtended, UserX, _}
import code.search.{elasticsearchMetrics, elasticsearchWarehouse}
import code.socialmedia.SocialMediaHandle
import code.usercustomerlinks.UserCustomerLink
import code.users.Users
import code.views.Views
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.model.{AccountId, AmountOfMoneyJsonV121, BankId, BankIdAccountId, CustomerFaceImage}
import com.openbankproject.commons.model.{AmountOfMoneyJsonV121 => AmountOfMoneyJSON121}
import com.openbankproject.commons.util.{ApiVersion, ApiVersionStatus, ScannedApiVersion}
import net.liftweb.common._
import org.json4s.JsonAST.JValue
import org.json4s.{Extraction, Formats}
import net.liftweb.mapper.By
import org.http4s._
import org.http4s.dsl.io._

import java.util.Date
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Future

object Http4s200 {
  val implementedInApiVersion: ScannedApiVersion = ApiVersion.v2_0_0
  val versionStatus: String                       = ApiVersionStatus.DEPRECATED.toString
  val resourceDocs: ArrayBuffer[ResourceDoc]       = ArrayBuffer[ResourceDoc]()

  implicit val formats: Formats = CustomJsonFormats.formats

  type HttpF[A] = OptionT[IO, A]

  // Carried over verbatim from APIMethods200.scala — referenced inside
  // restored `s"""..."""` doc interpolations for createUserCustomerLinks.
  private val createUserCustomerLinksEntitlementsRequiredForSpecificBank = canCreateUserCustomerLink :: Nil
  private val createUserCustomerLinksEntitlementsRequiredForAnyBank = canCreateUserCustomerLinkAtAnyBank :: Nil
  private val createUserCustomerLinksrequiredEntitlementsText =
    createUserCustomerLinksEntitlementsRequiredForSpecificBank.mkString(" and ") +
      " OR " +
      createUserCustomerLinksEntitlementsRequiredForAnyBank.mkString(" and ") +
      " entitlements are required."

  object Implementations2_0_0 {
    val prefixPath = Root / ApiPathZero.toString / implementedInApiVersion.toString

    // ─── root ─────────────────────────────────────────────────────────────────

    val root: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` =>
        EndpointHelpers.executeAndRespond(req) { _ =>
          Future.successful(JSONFactory121.getApiInfoJSON(ApiVersion.v2_0_0, versionStatus))
        }
      case req @ GET -> `prefixPath` / "root" =>
        EndpointHelpers.executeAndRespond(req) { _ =>
          Future.successful(JSONFactory121.getApiInfoJSON(ApiVersion.v2_0_0, versionStatus))
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

    // ─── getPrivateAccountsAllBanks ───────────────────────────────────────────

    val getPrivateAccountsAllBanks: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "accounts" =>
        EndpointHelpers.withUser(req) { (user, cc) =>
          Future {
            val (privateViewsUserCanAccess, privateAccountAccess) = Views.views.vend.privateViewsUserCanAccess(user)
            val privateAccounts = BankAccountX.privateAccounts(privateAccountAccess)
            privateBankAccountsListToJson(privateAccounts, privateViewsUserCanAccess)
          }
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion, nameOf(getPrivateAccountsAllBanks), "GET", "/accounts",
      "Get all Accounts at all Banks",
      s"""Get all accounts at all banks the User has access to.
         |Returns the list of accounts at that the user has access to at all banks.
         |For each account the API returns the account ID and the available views.
         |
         |${userAuthenticationMessage(true)}""".stripMargin,
      EmptyBody, basicAccountsJSON,
      List(AuthenticatedUserIsRequired, UnknownError),
      List(apiTagAccount, apiTagPrivateData, apiTagPublicData, apiTagOldStyle), None,
      http4sPartialFunction = Some(getPrivateAccountsAllBanks))

    // ─── corePrivateAccountsAllBanks ──────────────────────────────────────────

    val corePrivateAccountsAllBanks: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "my" / "accounts" =>
        EndpointHelpers.withUser(req) { (user, cc) =>
          Future {
            val (privateViewsUserCanAccess, privateAccountAccess) = Views.views.vend.privateViewsUserCanAccess(user)
            val privateAccounts = BankAccountX.privateAccounts(privateAccountAccess)
            privateAccounts.map { account =>
              createCoreAccountJSON(account, org.json4s.JObject(Nil))
            }
          }
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion, nameOf(corePrivateAccountsAllBanks), "GET", "/my/accounts",
      "Get Accounts at all Banks (Private)",
      s"""Get private accounts at all banks (Authenticated access)
         |Returns the list of accounts containing private views for the user at all banks.
         |For each account the API returns the ID and the available views.
         |
         |${userAuthenticationMessage(true)}""".stripMargin,
      EmptyBody, coreAccountsJSON,
      List(UnknownError),
      List(apiTagAccount, apiTagPrivateData, apiTagPsd2, apiTagOldStyle), None,
      http4sPartialFunction = Some(corePrivateAccountsAllBanks))

    // ─── publicAccountsAllBanks ───────────────────────────────────────────────

    val publicAccountsAllBanks: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "accounts" / "public" =>
        EndpointHelpers.executeAndRespond(req) { _ =>
          Future {
            val (publicViews, publicAccountAccess) = Views.views.vend.publicViews
            val accounts = BankAccountX.publicAccounts(publicAccountAccess)
            val accJson: List[BasicAccountJSON] = accounts.map { account =>
              val viewsAvailable = publicViews
                .filter(v => v.bankId == account.bankId && v.accountId == account.accountId && v.isPublic)
                .map(createBasicViewJSON)
                .distinct
              createBasicAccountJSON(account, viewsAvailable)
            }
            BasicAccountsJSON(accJson)
          }
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion, nameOf(publicAccountsAllBanks), "GET", "/accounts/public",
      "Get Public Accounts at all Banks",
      s"""Get public accounts at all banks (Anonymous access).
         |Returns accounts that contain at least one public view (a view where is_public is true)
         |For each account the API returns the ID and the available views.
         |
         |${userAuthenticationMessage(false)}""".stripMargin,
      EmptyBody, basicAccountsJSON,
      List(AuthenticatedUserIsRequired, CannotGetAccounts, UnknownError),
      List(apiTagAccountPublic, apiTagAccount, apiTagPublicData), None,
      http4sPartialFunction = Some(publicAccountsAllBanks))

    def processAccounts(privateViews: List[com.openbankproject.commons.model.View],
                        accounts: List[com.openbankproject.commons.model.BankAccount]
                       ): org.json4s.JsonAST.JValue = {
      val accJson = accounts.map { account =>
        val viewsAvailable = privateViews
          .filter(v => v.bankId == account.bankId && v.accountId == account.accountId && v.isPrivate)
          .map(createBasicViewJSON)
          .distinct
        createBasicAccountJSON(account, viewsAvailable)
      }
      org.json4s.Extraction.decompose(accJson)
    }

    // ─── getPrivateAccountsAtOneBank ──────────────────────────────────────────

    val getPrivateAccountsAtOneBank: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / _ / "accounts" =>
        EndpointHelpers.withUserAndBank(req) { (user, bank, cc) =>
          for {
            (privateViewsUserCanAccessAtOneBank, privateAccountAccess) <- Future {
              Views.views.vend.privateViewsUserCanAccessAtBank(user, bank.bankId)
            }
            (availablePrivateAccounts, _) <- BankExtended(bank).privateAccountsFuture(privateAccountAccess, Some(cc))
          } yield {
            // Lift returns a raw List[BasicAccountJSON] here (JArray), not BasicAccountsJSON
            // (JObject). v3_1_0/AccountTest extracts `List[BasicAccountJSON]` directly, so we
            // must NOT wrap; the sibling /banks/BANK_ID/accounts/private endpoint does wrap.
            availablePrivateAccounts.map { account =>
              val viewsAvailable = privateViewsUserCanAccessAtOneBank
                .filter(v => v.bankId == account.bankId && v.accountId == account.accountId && v.isPrivate)
                .map(createBasicViewJSON)
                .distinct
              createBasicAccountJSON(account, viewsAvailable)
            }
          }
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion, nameOf(getPrivateAccountsAtOneBank), "GET", "/banks/BANK_ID/accounts",
      "Get Accounts at Bank",
      s"""
         |Returns the list of accounts at BANK_ID that the user has access to.
         |For each account the API returns the account ID and the views available to the user..
         |Each account must have at least one private View.
         |
         |${userAuthenticationMessage(true)}
      """.stripMargin,
      EmptyBody, basicAccountsJSON,
      List(BankNotFound, UnknownError),
      List(apiTagAccount, apiTagPrivateData, apiTagPublicData), None,
      http4sPartialFunction = Some(getPrivateAccountsAtOneBank))

    // ─── corePrivateAccountsAtOneBank ─────────────────────────────────────────

    val corePrivateAccountsAtOneBank: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "my" / "banks" / _ / "accounts" =>
        EndpointHelpers.withUserAndBank(req) { (user, bank, cc) =>
          for {
            (privateViewsUserCanAccessAtOneBank, privateAccountAccess) <- Future {
              Views.views.vend.privateViewsUserCanAccessAtBank(user, bank.bankId)
            }
            (privateAccountsForOneBank, _) <- BankExtended(bank).privateAccountsFuture(privateAccountAccess, Some(cc))
          } yield privateAccountsForOneBank.map(account =>
            createCoreAccountJSON(account, org.json4s.JObject(Nil)))
        }
      case req @ GET -> `prefixPath` / "my" / "banks" / _ / "accounts" / "private" =>
        EndpointHelpers.withUserAndBank(req) { (user, bank, cc) =>
          for {
            (privateViewsUserCanAccessAtOneBank, privateAccountAccess) <- Future {
              Views.views.vend.privateViewsUserCanAccessAtBank(user, bank.bankId)
            }
            (privateAccountsForOneBank, _) <- BankExtended(bank).privateAccountsFuture(privateAccountAccess, Some(cc))
          } yield privateAccountsForOneBank.map(account =>
            createCoreAccountJSON(account, org.json4s.JObject(Nil)))
        }
      case req @ GET -> `prefixPath` / "bank" / "accounts" =>
        EndpointHelpers.withUser(req) { (user, cc) =>
          for {
            (bank, _) <- NewStyle.function.getBank(BankId(APIUtil.defaultBankId), Some(cc))
            (privateViewsUserCanAccessAtOneBank, privateAccountAccess) <- Future {
              Views.views.vend.privateViewsUserCanAccessAtBank(user, bank.bankId)
            }
            (availablePrivateAccounts, _) <- BankExtended(bank).privateAccountsFuture(privateAccountAccess, Some(cc))
          } yield availablePrivateAccounts.map(account =>
            createCoreAccountJSON(account, org.json4s.JObject(Nil)))
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion, nameOf(corePrivateAccountsAtOneBank), "GET", "/my/banks/BANK_ID/accounts",
      "Get Accounts at Bank (Private)",
      s"""Get private accounts at one bank (Authenticated access).
      |Returns the list of accounts containing private views for the user at BANK_ID.
      |For each account the API returns the ID and label. To also see the list of Views, see privateAccountsAtOneBank
      |
      |
      |This call MAY have an alias /bank/accounts but ONLY if defaultBank is set in Props
      |
      |${userAuthenticationMessage(true)}
      |
      |""".stripMargin,
      EmptyBody, coreAccountsJSON,
      List(AuthenticatedUserIsRequired, UnknownError),
      List(apiTagAccount, apiTagPrivateData, apiTagPsd2), None,
      http4sPartialFunction = Some(corePrivateAccountsAtOneBank))

    // ─── privateAccountsAtOneBank ─────────────────────────────────────────────

    val privateAccountsAtOneBank: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / _ / "accounts" / "private" =>
        EndpointHelpers.withUserAndBank(req) { (user, bank, cc) =>
          for {
            (privateViewsUserCanAccessAtOneBank, privateAccountAccess) <- Future {
              Views.views.vend.privateViewsUserCanAccessAtBank(user, bank.bankId)
            }
            (availablePrivateAccounts, _) <- BankExtended(bank).privateAccountsFuture(privateAccountAccess, Some(cc))
          } yield privateBankAccountsListToJson(availablePrivateAccounts, privateViewsUserCanAccessAtOneBank)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion, nameOf(privateAccountsAtOneBank), "GET", "/banks/BANK_ID/accounts/private",
      "Get private accounts at one bank",
      s"""Returns the list of private accounts at BANK_ID that the user has access to.
      |For each account the API returns the ID and the available views.
      |
      |If you want to see more information on the Views, use the Account Detail call.
      |If you want less information about the account, use the /my accounts call
      |
      |
      |${userAuthenticationMessage(true)}
      |
      |""".stripMargin,
      EmptyBody, basicAccountsJSON,
      List(AuthenticatedUserIsRequired, BankNotFound, UnknownError),
      List(apiTagAccount, apiTagPsd2), None,
      http4sPartialFunction = Some(privateAccountsAtOneBank))

    // ─── publicAccountsAtOneBank ──────────────────────────────────────────────

    val publicAccountsAtOneBank: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / _ / "accounts" / "public" =>
        EndpointHelpers.withBank(req) { (bank, cc) =>
          Future {
            val (publicViewsForBank, publicAccountAccess) = Views.views.vend.publicViewsForBank(bank.bankId)
            val accounts = bank.publicAccounts(publicAccountAccess)
            val accJson = accounts.map { account =>
              val viewsAvailable = publicViewsForBank
                .filter(v => v.bankId == account.bankId && v.accountId == account.accountId && v.isPublic)
                .map(createBasicViewJSON)
                .distinct
              createBasicAccountJSON(account, viewsAvailable)
            }
            BasicAccountsJSON(accJson)
          }
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion, nameOf(publicAccountsAtOneBank), "GET", "/banks/BANK_ID/accounts/public",
      "Get Public Accounts at Bank",
      s"""Returns a list of the public accounts (Anonymous access) at BANK_ID.
         |For each account the API returns the ID and the available views.
         |
         |${userAuthenticationMessage(false)}""".stripMargin,
      EmptyBody, basicAccountsJSON,
      List(UnknownError),
      List(apiTagAccountPublic, apiTagAccount, apiTagPublicData), None,
      http4sPartialFunction = Some(publicAccountsAtOneBank))

    // ─── getKycDocuments ──────────────────────────────────────────────────────

    val getKycDocuments: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "customers" / customerId / "kyc_documents" =>
        EndpointHelpers.withUser(req) { (user, cc) =>
          for {
            _ <- code.util.Helper.booleanToFuture(UserHasMissingRoles + canGetAnyKycDocuments, failCode = 403, cc = Some(cc)) {
              APIUtil.hasEntitlement("", user.userId, canGetAnyKycDocuments)
            }
            (_, cc2)           <- NewStyle.function.getCustomerByCustomerId(customerId, Some(cc))
            (kycDocuments, _)  <- NewStyle.function.getKycDocuments(customerId, cc2)
          } yield createKycDocumentsJSON(kycDocuments)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion, nameOf(getKycDocuments), "GET", "/customers/CUSTOMER_ID/kyc_documents",
      "Get Customer KYC Documents",
      s"""Get KYC (know your customer) documents for a customer specified by CUSTOMER_ID
         |Get a list of documents that affirm the identity of the customer
         |Passport, driving licence etc.
         |${userAuthenticationMessage(false)}""".stripMargin,
      EmptyBody, kycDocumentsJSON,
      List(AuthenticatedUserIsRequired, CustomerNotFoundByCustomerId, UnknownError),
      List(apiTagKyc, apiTagCustomer),
      Some(List(canGetAnyKycDocuments)),
      http4sPartialFunction = Some(getKycDocuments))

    // ─── getKycMedia ──────────────────────────────────────────────────────────

    val getKycMedia: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "customers" / customerId / "kyc_media" =>
        EndpointHelpers.withUser(req) { (user, cc) =>
          for {
            _ <- code.util.Helper.booleanToFuture(UserHasMissingRoles + canGetAnyKycMedia, failCode = 403, cc = Some(cc)) {
              APIUtil.hasEntitlement("", user.userId, canGetAnyKycMedia)
            }
            (_, cc2)        <- NewStyle.function.getCustomerByCustomerId(customerId, Some(cc))
            (kycMedias, _)  <- NewStyle.function.getKycMedias(customerId, cc2)
          } yield createKycMediasJSON(kycMedias)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion, nameOf(getKycMedia), "GET", "/customers/CUSTOMER_ID/kyc_media",
      "Get KYC Media for a customer",
      s"""Get KYC media (scans, pictures, videos) that affirms the identity of the customer.
         |
         |${userAuthenticationMessage(true)}""".stripMargin,
      EmptyBody, kycMediasJSON,
      List(AuthenticatedUserIsRequired, CustomerNotFoundByCustomerId, UnknownError),
      List(apiTagKyc, apiTagCustomer),
      Some(List(canGetAnyKycMedia)),
      http4sPartialFunction = Some(getKycMedia))

    // ─── getKycChecks ─────────────────────────────────────────────────────────

    val getKycChecks: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "customers" / customerId / "kyc_checks" =>
        EndpointHelpers.withUser(req) { (user, cc) =>
          for {
            _ <- code.util.Helper.booleanToFuture(UserHasMissingRoles + canGetAnyKycChecks, failCode = 403, cc = Some(cc)) {
              APIUtil.hasEntitlement("", user.userId, canGetAnyKycChecks)
            }
            (_, cc2)        <- NewStyle.function.getCustomerByCustomerId(customerId, Some(cc))
            (kycChecks, _)  <- NewStyle.function.getKycChecks(customerId, cc2)
          } yield createKycChecksJSON(kycChecks)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion, nameOf(getKycChecks), "GET", "/customers/CUSTOMER_ID/kyc_checks",
      "Get Customer KYC Checks",
      s"""Get KYC checks for the Customer specified by CUSTOMER_ID.
         |
         |${userAuthenticationMessage(true)}""".stripMargin,
      EmptyBody, kycChecksJSON,
      List(AuthenticatedUserIsRequired, CustomerNotFoundByCustomerId, UnknownError),
      List(apiTagKyc, apiTagCustomer),
      Some(List(canGetAnyKycChecks)),
      http4sPartialFunction = Some(getKycChecks))

    // ─── getKycStatuses ───────────────────────────────────────────────────────

    val getKycStatuses: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "customers" / customerId / "kyc_statuses" =>
        EndpointHelpers.withUser(req) { (user, cc) =>
          for {
            _ <- code.util.Helper.booleanToFuture(UserHasMissingRoles + canGetAnyKycStatuses, failCode = 403, cc = Some(cc)) {
              APIUtil.hasEntitlement("", user.userId, canGetAnyKycStatuses)
            }
            (_, cc2)          <- NewStyle.function.getCustomerByCustomerId(customerId, Some(cc))
            (kycStatuses, _)  <- NewStyle.function.getKycStatuses(customerId, cc2)
          } yield createKycStatusesJSON(kycStatuses)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion, nameOf(getKycStatuses), "GET", "/customers/CUSTOMER_ID/kyc_statuses",
      "Get Customer KYC statuses",
      s"""Get the KYC statuses for a customer specified by CUSTOMER_ID over time.
         |
         |${userAuthenticationMessage(true)}""".stripMargin,
      EmptyBody, kycStatusesJSON,
      List(AuthenticatedUserIsRequired, CustomerNotFoundByCustomerId, UnknownError),
      List(apiTagKyc, apiTagCustomer),
      Some(List(canGetAnyKycStatuses)),
      http4sPartialFunction = Some(getKycStatuses))

    // ─── getSocialMediaHandles ────────────────────────────────────────────────

    val getSocialMediaHandles: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / _ / "customers" / customerId / "social_media_handles" =>
        EndpointHelpers.withUserAndBank(req) { (user, bank, cc) =>
          for {
            _ <- code.util.Helper.booleanToFuture(UserHasMissingRoles + canGetSocialMediaHandles, failCode = 403, cc = Some(cc)) {
              APIUtil.hasEntitlement(bank.bankId.value, user.userId, canGetSocialMediaHandles)
            }
            (customer, _) <- NewStyle.function.getCustomerByCustomerId(customerId, Some(cc))
          } yield {
            val socialMedias = SocialMediaHandle.socialMediaHandleProvider.vend.getSocialMedias(customer.number)
            createSocialMediasJSON(socialMedias)
          }
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion, nameOf(getSocialMediaHandles), "GET",
      "/banks/BANK_ID/customers/CUSTOMER_ID/social_media_handles",
      "Get Customer Social Media Handles",
      s"""Get social media handles for a customer specified by CUSTOMER_ID.
         |
         |${userAuthenticationMessage(true)}""".stripMargin,
      EmptyBody, socialMediasJSON,
      List(AuthenticatedUserIsRequired, UserHasMissingRoles, CustomerNotFoundByCustomerId, UnknownError),
      List(apiTagCustomer),
      Some(List(canGetSocialMediaHandles)),
      http4sPartialFunction = Some(getSocialMediaHandles))

    // ─── addKycDocument ───────────────────────────────────────────────────────

    val addKycDocument: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ PUT -> `prefixPath` / "banks" / _ / "customers" / customerId / "kyc_documents" / documentId =>
        EndpointHelpers.withUserAndBankAndBodyCreated[PostKycDocumentJSON, KycDocumentJSON](req) { (user, bank, body, cc) =>
          for {
            _ <- code.util.Helper.booleanToFuture(UserHasMissingRoles + canAddKycDocument, failCode = 403, cc = Some(cc)) {
              APIUtil.hasEntitlement(bank.bankId.value, user.userId, canAddKycDocument)
            }
            (_, cc2)               <- NewStyle.function.getCustomerByCustomerId(customerId, Some(cc))
            (kycDocumentCreated, _) <- NewStyle.function.createOrUpdateKycDocument(
              bank.bankId.value, customerId, documentId,
              body.customer_number, body.`type`, body.number,
              body.issue_date, body.issue_place, body.expiry_date, cc2)
          } yield createKycDocumentJSON(kycDocumentCreated)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion, nameOf(addKycDocument), "PUT",
      "/banks/BANK_ID/customers/CUSTOMER_ID/kyc_documents/KYC_DOCUMENT_ID",
      "Add KYC Document",
      "Add a KYC document for the customer specified by CUSTOMER_ID. KYC Documents contain the document type (e.g. passport), place of issue, expiry etc. ",
      postKycDocumentJSON, kycDocumentJSON,
      List(AuthenticatedUserIsRequired, InvalidJsonFormat, BankNotFound, CustomerNotFoundByCustomerId,"Server error: could not add KycDocument", UnknownError),
      List(apiTagKyc, apiTagCustomer),
      Some(List(canAddKycDocument)),
      http4sPartialFunction = Some(addKycDocument))

    // ─── addKycMedia ──────────────────────────────────────────────────────────

    val addKycMedia: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ PUT -> `prefixPath` / "banks" / _ / "customers" / customerId / "kyc_media" / mediaId =>
        EndpointHelpers.withUserAndBankAndBodyCreated[PostKycMediaJSON, KycMediaJSON](req) { (user, bank, body, cc) =>
          for {
            _ <- code.util.Helper.booleanToFuture(UserHasMissingRoles + canAddKycMedia, failCode = 403, cc = Some(cc)) {
              APIUtil.hasEntitlement(bank.bankId.value, user.userId, canAddKycMedia)
            }
            (_, cc2)           <- NewStyle.function.getCustomerByCustomerId(customerId, Some(cc))
            (kycMediaCreated, _) <- NewStyle.function.createOrUpdateKycMedia(
              bank.bankId.value, customerId, mediaId,
              body.customer_number, body.`type`, body.url, body.date,
              body.relates_to_kyc_document_id, body.relates_to_kyc_check_id, cc2)
          } yield createKycMediaJSON(kycMediaCreated)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion, nameOf(addKycMedia), "PUT",
      "/banks/BANK_ID/customers/CUSTOMER_ID/kyc_media/KYC_MEDIA_ID",
      "Add KYC Media",
      "Add some KYC media for the customer specified by CUSTOMER_ID. KYC Media resources relate to KYC Documents and KYC Checks and contain media urls for scans of passports, utility bills etc",
      postKycMediaJSON, kycMediaJSON,
      List(AuthenticatedUserIsRequired, InvalidJsonFormat, CustomerNotFoundByCustomerId, ServerAddDataError, UnknownError),
      List(apiTagKyc, apiTagCustomer),
      Some(List(canAddKycMedia)),
      http4sPartialFunction = Some(addKycMedia))

    // ─── addKycCheck ──────────────────────────────────────────────────────────

    val addKycCheck: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ PUT -> `prefixPath` / "banks" / _ / "customers" / customerId / "kyc_check" / checkId =>
        EndpointHelpers.withUserAndBankAndBodyCreated[PostKycCheckJSON, KycCheckJSON](req) { (user, bank, body, cc) =>
          for {
            _ <- code.util.Helper.booleanToFuture(UserHasMissingRoles + canAddKycCheck, failCode = 403, cc = Some(cc)) {
              APIUtil.hasEntitlement(bank.bankId.value, user.userId, canAddKycCheck)
            }
            (_, cc2)         <- NewStyle.function.getCustomerByCustomerId(customerId, Some(cc))
            (kycCheck, _)    <- NewStyle.function.createOrUpdateKycCheck(
              bank.bankId.value, customerId, checkId,
              body.customer_number, body.date, body.how,
              body.staff_user_id, body.staff_name, body.satisfied, body.comments, cc2)
          } yield createKycCheckJSON(kycCheck)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion, nameOf(addKycCheck), "PUT",
      "/banks/BANK_ID/customers/CUSTOMER_ID/kyc_check/KYC_CHECK_ID",
      "Add KYC Check",
      "Add a KYC check for the customer specified by CUSTOMER_ID. KYC Checks store details of checks on a customer made by the KYC team, their comments and a satisfied status",
      postKycCheckJSON, kycCheckJSON,
      List(AuthenticatedUserIsRequired, InvalidJsonFormat, BankNotFound, CustomerNotFoundByCustomerId, ServerAddDataError, UnknownError),
      List(apiTagKyc, apiTagCustomer),
      Some(List(canAddKycCheck)),
      http4sPartialFunction = Some(addKycCheck))

    // ─── addKycStatus ─────────────────────────────────────────────────────────

    val addKycStatus: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ PUT -> `prefixPath` / "banks" / _ / "customers" / customerId / "kyc_statuses" =>
        EndpointHelpers.withUserAndBankAndBodyCreated[PostKycStatusJSON, KycStatusJSON](req) { (user, bank, body, cc) =>
          for {
            _ <- code.util.Helper.booleanToFuture(UserHasMissingRoles + canAddKycStatus, failCode = 403, cc = Some(cc)) {
              APIUtil.hasEntitlement(bank.bankId.value, user.userId, canAddKycStatus)
            }
            (_, cc2)        <- NewStyle.function.getCustomerByCustomerId(customerId, Some(cc))
            (kycStatus, _)  <- NewStyle.function.createOrUpdateKycStatus(
              bank.bankId.value, customerId,
              body.customer_number, body.ok, body.date, cc2)
          } yield createKycStatusJSON(kycStatus)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion, nameOf(addKycStatus), "PUT",
      "/banks/BANK_ID/customers/CUSTOMER_ID/kyc_statuses",
      "Add KYC Status",
      "Add a kyc_status for the customer specified by CUSTOMER_ID. KYC Status is a timeline of the KYC status of the customer",
      postKycStatusJSON, kycStatusJSON,
      List(AuthenticatedUserIsRequired, InvalidJsonFormat, InvalidBankIdFormat, UnknownError, BankNotFound, ServerAddDataError, CustomerNotFoundByCustomerId),
      List(apiTagKyc, apiTagCustomer),
      Some(List(canAddKycStatus)),
      http4sPartialFunction = Some(addKycStatus))

    // ─── addSocialMediaHandle ─────────────────────────────────────────────────

    val addSocialMediaHandle: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "banks" / _ / "customers" / customerId / "social_media_handles" =>
        EndpointHelpers.withUserAndBankAndBodyCreated[SocialMediaJSON, SuccessMessage](req) { (user, bank, body, cc) =>
          for {
            _ <- code.util.Helper.booleanToFuture(InvalidBankIdFormat, cc = Some(cc)) {
              isValidID(bank.bankId.value)
            }
            _ <- code.util.Helper.booleanToFuture(UserHasMissingRoles + canAddSocialMediaHandle, failCode = 403, cc = Some(cc)) {
              APIUtil.hasEntitlement(bank.bankId.value, user.userId, canAddSocialMediaHandle)
            }
            (_, _) <- NewStyle.function.getCustomerByCustomerId(customerId, Some(cc))
            _ <- code.util.Helper.booleanToFuture("Server error: could not add", cc = Some(cc)) {
              SocialMediaHandle.socialMediaHandleProvider.vend.addSocialMedias(
                body.customer_number, body.`type`, body.handle,
                body.date_added, body.date_activated)
            }
          } yield SuccessMessage("Success")
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion, nameOf(addSocialMediaHandle), "POST",
      "/banks/BANK_ID/customers/CUSTOMER_ID/social_media_handles",
      "Create Customer Social Media Handle",
      "Create a customer social media handle for the customer specified by CUSTOMER_ID",
      socialMediaJSON, successMessage,
      List(AuthenticatedUserIsRequired, InvalidJsonFormat, InvalidBankIdFormat, UserHasMissingRoles, CustomerNotFoundByCustomerId, UnknownError),
      List(apiTagCustomer),
      Some(List(canAddSocialMediaHandle)),
      http4sPartialFunction = Some(addSocialMediaHandle))

    // ─── getCoreAccountById ───────────────────────────────────────────────────

    val getCoreAccountById: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "my" / "banks" / _ / "accounts" / _ / "account" =>
        EndpointHelpers.withBankAccount(req) { (user, account, cc) =>
          for {
            view              <- ViewNewStyle.checkOwnerViewAccessAndReturnOwnerView(user, BankIdAccountId(account.bankId, account.accountId), Some(cc))
            moderatedAccount  <- Future {
              unboxFullOrFail(
                account.moderatedBankAccount(view, BankIdAccountId(account.bankId, account.accountId), Full(user), Some(cc)),
                Some(cc), UnknownError)
            }
          } yield createCoreBankAccountJSON(moderatedAccount)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion, nameOf(getCoreAccountById), "GET",
      "/my/banks/BANK_ID/accounts/ACCOUNT_ID/account",
      "Get Account by Id (Core)",
      s"""Information returned about the account specified by ACCOUNT_ID:
         |
         |* Number
         |* Owners
         |* Type
         |* Balance
         |* IBAN
         |
         |This call returns the owner view and requires access to that view.
         |
         |${userAuthenticationMessage(true)}""".stripMargin,
      EmptyBody, moderatedCoreAccountJSON,
      List(BankAccountNotFound, UnknownError),
      List(apiTagAccount, apiTagPsd2, apiTagOldStyle), None,
      http4sPartialFunction = Some(getCoreAccountById))

    // ─── getCoreTransactionsForBankAccount ────────────────────────────────────

    val getCoreTransactionsForBankAccount: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "my" / "banks" / _ / "accounts" / _ / "transactions" =>
        EndpointHelpers.withBankAccount(req) { (user, account, cc) =>
          for {
            view               <- ViewNewStyle.checkOwnerViewAccessAndReturnOwnerView(user, BankIdAccountId(account.bankId, account.accountId), Some(cc))
            (bank, _)          <- NewStyle.function.getBank(account.bankId, Some(cc))
            httpParams         <- NewStyle.function.extractHttpParamsFromUrl(cc.url)
            (obpQueryParams, _) <- createQueriesByHttpParamsFuture(httpParams, Some(cc))
            (transactions, _)  <- Future {
              unboxFullOrFail(
                account.getModeratedTransactions(bank, Full(user), view, BankIdAccountId(account.bankId, account.accountId), Some(cc), obpQueryParams),
                Some(cc), UnknownError)
            }
          } yield createCoreTransactionsJSON(transactions)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion, nameOf(getCoreTransactionsForBankAccount), "GET",
      "/my/banks/BANK_ID/accounts/ACCOUNT_ID/transactions",
      "Get Transactions for Account (Core)",
      s"""Returns transactions list (Core info) of the account specified by ACCOUNT_ID.
         |
         |Authentication is required.
         |
         |${urlParametersDocument(true, true)}""",
      EmptyBody, coreTransactionsJSON,
      List(BankAccountNotFound, UnknownError),
      List(apiTagTransaction, apiTagAccount, apiTagPsd2, apiTagOldStyle), None,
      http4sPartialFunction = Some(getCoreTransactionsForBankAccount))

    // ─── accountById ──────────────────────────────────────────────────────────

    val accountById: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / _ / "accounts" / _ / _ / "account" =>
        EndpointHelpers.withView(req) { (user, account, view, cc) =>
          for {
            availableViews <- Future {
              Views.views.vend.privateViewsUserCanAccessForAccount(user, BankIdAccountId(account.bankId, account.accountId))
            }
            moderatedAccount <- Future {
              unboxFullOrFail(
                account.moderatedBankAccount(view, BankIdAccountId(account.bankId, account.accountId), Full(user), Some(cc)),
                Some(cc), UnknownError)
            }
          } yield {
            val viewsAvailable = availableViews.map(JSONFactory121.createViewJSON).sortBy(_.short_name)
            JSONFactory121.createBankAccountJSON(moderatedAccount, viewsAvailable)
          }
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion, nameOf(accountById), "GET",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/account",
      "Get Account by Id (Full)",
      s"""Information returned about an account specified by ACCOUNT_ID as moderated by the view (VIEW_ID):
      |
      |* Number
      |* Owners
      |* Type
      |* Balance
      |* IBAN
      |* Available views (sorted by short_name)
      |
      |More details about the data moderation by the view [here](#1_2_1-getViewsForBankAccount).
      |
      |PSD2 Context: PSD2 requires customers to have access to their account information via third party applications.
      |This call provides balance and other account information via delegated authentication using OAuth.
      |
      |${userAuthenticationMessage(true)} if the 'is_public' field in view (VIEW_ID) is not set to `true`.
      |
      |""".stripMargin,
      EmptyBody, moderatedAccountJSON,
      List(BankNotFound, AccountNotFound, ViewNotFound, UserNoPermissionAccessView, UnknownError),
      List(apiTagAccount, apiTagOldStyle), None,
      http4sPartialFunction = Some(accountById))

    // ─── getPermissionsForBankAccount ─────────────────────────────────────────

    val getPermissionsForBankAccount: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / _ / "accounts" / _ / "permissions" =>
        EndpointHelpers.withBankAccount(req) { (user, account, cc) =>
          val bankIdAccountId = BankIdAccountId(account.bankId, account.accountId)
          for {
            hasPermission <- Future {
              Views.views.vend.permission(bankIdAccountId, user)
                .map(_.views.map(_.allowed_actions.exists(_ == CAN_SEE_VIEWS_WITH_PERMISSIONS_FOR_ALL_USERS)))
                .getOrElse(Nil).find(_ == true).getOrElse(false)
            }
            _ <- code.util.Helper.booleanToFuture(
              s"${ViewDoesNotPermitAccess} You need the `$CAN_SEE_VIEWS_WITH_PERMISSIONS_FOR_ALL_USERS` permission on any your views",
              cc = Some(cc)
            ) { hasPermission }
            permissions <- Future { Views.views.vend.permissions(bankIdAccountId) }
          } yield JSONFactory121.createPermissionsJSON(permissions.sortBy(_.user.emailAddress))
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion, nameOf(getPermissionsForBankAccount), "GET",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/permissions",
      "Get access",
      s"""Returns the list of the permissions at BANK_ID for account ACCOUNT_ID, with each time a pair composed of the user and the views that he has access to.
         |
         |${userAuthenticationMessage(true)}
         |and the user needs to have access to the owner view.""",
      EmptyBody, permissionsJSON,
      List(AuthenticatedUserIsRequired, BankNotFound, AccountNotFound, UnknownError),
      List(apiTagView, apiTagAccount, apiTagUser, apiTagEntitlement), None,
      http4sPartialFunction = Some(getPermissionsForBankAccount))

    // ─── getPermissionForUserForBankAccount ───────────────────────────────────

    val getPermissionForUserForBankAccount: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / _ / "accounts" / _ / "permissions" / provider / providerId =>
        EndpointHelpers.withBankAccount(req) { (user, account, cc) =>
          val bankIdAccountId = BankIdAccountId(account.bankId, account.accountId)
          for {
            hasPermission <- Future {
              Views.views.vend.permission(bankIdAccountId, user)
                .map(_.views.map(_.allowed_actions.exists(_ == CAN_SEE_VIEWS_WITH_PERMISSIONS_FOR_ONE_USER)))
                .getOrElse(Nil).find(_ == true).getOrElse(false)
            }
            _ <- code.util.Helper.booleanToFuture(
              s"${CreateCustomViewError} You need the `$CAN_SEE_VIEWS_WITH_PERMISSIONS_FOR_ONE_USER` permission on any your views",
              cc = Some(cc)
            ) { hasPermission }
            userFromURL <- Future {
              unboxFullOrFail(
                UserX.findByProviderId(provider, providerId),
                Some(cc), UserNotFoundByProviderAndProvideId)
            }
            permission  <- Future {
              unboxFullOrFail(
                Views.views.vend.permission(bankIdAccountId, userFromURL),
                Some(cc), UserNotFoundByProviderAndProvideId)
            }
          } yield JSONFactory121.createViewsJSON(permission.views.sortBy(_.viewId.value))
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion, nameOf(getPermissionForUserForBankAccount), "GET",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/permissions/PROVIDER/PROVIDER_ID",
      "Get Account access for User",
      s"""Returns the list of the views at BANK_ID for account ACCOUNT_ID that a user identified by PROVIDER_ID at their provider PROVIDER has access to.
      |All url parameters must be [%-encoded](http://en.wikipedia.org/wiki/Percent-encoding), which is often especially relevant for USER_ID and PROVIDER.
      |
      |${userAuthenticationMessage(true)}
      |
      |The user needs to have access to the owner view.""",
      EmptyBody, viewsJSONV121,
      List(AuthenticatedUserIsRequired, BankNotFound, AccountNotFound, UnknownError),
      List(apiTagView, apiTagAccount, apiTagUser, apiTagOldStyle), None,
      http4sPartialFunction = Some(getPermissionForUserForBankAccount))

    // ─── createAccount ────────────────────────────────────────────────────────

    val createAccount: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ PUT -> `prefixPath` / "banks" / _ / "accounts" / _ =>
        EndpointHelpers.withUserAndBankAndBody[CreateAccountJSON, CoreAccountJSON](req) { (user, bank, body, cc) =>
          for {
            _ <- code.util.Helper.booleanToFuture(InvalidAccountIdFormat, cc = Some(cc)) {
              isValidID(cc.bankAccount.map(_.accountId.value).getOrElse(
                req.uri.path.segments.lastOption.map(_.encoded).getOrElse("")))
            }
            _ <- code.util.Helper.booleanToFuture(InvalidBankIdFormat, cc = Some(cc)) {
              isValidID(bank.bankId.value)
            }
            loggedInUserId   = user.userId
            userIdAccountOwner = if (body.user_id.nonEmpty) body.user_id else loggedInUserId
            (postedOrLoggedInUser, cc2) <- NewStyle.function.findByUserId(userIdAccountOwner, Some(cc))
            _ <- if (userIdAccountOwner == loggedInUserId) Future.successful(Full(()))
                 else code.util.Helper.booleanToFuture(
                   s"${UserHasMissingRoles} $canCreateAccount or create account for self", failCode = 403, cc = Some(cc)) {
                   APIUtil.hasEntitlement(bank.bankId.value, loggedInUserId, canCreateAccount)
                 }
            initialBalanceAsNumber <- NewStyle.function.tryons(InvalidAccountInitialBalance, 400, cc2) {
              BigDecimal(body.balance.amount)
            }
            _ <- code.util.Helper.booleanToFuture(InitialBalanceMustBeZero, cc = cc2) {
              initialBalanceAsNumber == 0
            }
            _ <- code.util.Helper.booleanToFuture(InvalidISOCurrencyCode, cc = cc2) {
              isValidCurrencyISOCode(body.balance.currency)
            }
            accountId = cc.bankAccount.map(_.accountId).getOrElse(
              AccountId(req.uri.path.segments.lastOption.map(_.encoded).getOrElse("")))
            (bankAccount, cc3) <- NewStyle.function.createBankAccount(
              bank.bankId, accountId, body.`type`, body.label, body.balance.currency,
              initialBalanceAsNumber, postedOrLoggedInUser.name, "", List.empty, cc2)
            _ <- BankAccountCreation.setAccountHolderAndRefreshUserAccountAccess(
              bank.bankId, accountId, postedOrLoggedInUser, cc3)
          } yield createCoreAccountJSON(bankAccount, org.json4s.JObject(Nil))
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
      |ACCOUNT_ID SHOULD be a UUID. ACCOUNT_ID MUST NOT be the ACCOUNT_NUMBER.
      |
      |TYPE SHOULD be the PRODUCT_CODE from Product.
      |
      |Note: The Amount must be zero.""".stripMargin,
      CreateAccountJSON("A user_id","CURRENT", "Label", AmountOfMoneyJSON121("EUR", "0")),
      coreAccountJSON,
      List(AuthenticatedUserIsRequired, InvalidJsonFormat, InvalidUserId, InvalidAccountIdFormat, InvalidBankIdFormat,
        UserNotFoundById, InvalidAccountBalanceAmount, InvalidAccountType, InvalidAccountInitialBalance,
        InvalidAccountBalanceCurrency, UnknownError),
      List(apiTagAccount, apiTagOldStyle),
      None,
      http4sPartialFunction = Some(createAccount))

    // ─── getTransactionTypes ──────────────────────────────────────────────────

    private val getTransactionTypesIsPublic = APIUtil.getPropsAsBoolValue("apiOptions.getTransactionTypesIsPublic", true)

    val getTransactionTypes: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / _ / "transaction-types" =>
        EndpointHelpers.withBank(req) { (bank, cc) =>
          Future {
            val types = TransactionType.TransactionTypeProvider.vend.getTransactionTypesForBank(bank.bankId)
            JSONFactory200.createTransactionTypeJSON(connectorEmptyResponse(types, Some(cc)))
          }
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion, nameOf(getTransactionTypes), "GET",
      "/banks/BANK_ID/transaction-types",
      "Get Transaction Types at Bank",
      // TODO get the documentation of the parameters from the scala doc of the case class we return
      s"""Get Transaction Types for the bank specified by BANK_ID:
         |
         |Lists the possible Transaction Types available at the bank (as opposed to Transaction Request Types which are the possible ways Transactions can be created by this API Server).
         |
         |  * id : Unique transaction type id across the API instance. SHOULD be a UUID. MUST be unique.
         |  * bank_id : The bank that supports this TransactionType
         |  * short_code : A short code (SHOULD have no-spaces) which MUST be unique across the bank. May be stored with Transactions to link here
         |  * summary : A succinct summary
         |  * description : A longer description
         |  * charge : The charge to the customer for each one of these
         |
         |${userAuthenticationMessage(!getTransactionTypesIsPublic)}""".stripMargin,
      EmptyBody, transactionTypesJsonV200,
      List(BankNotFound, UnknownError),
      List(apiTagBank, apiTagPSD2AIS, apiTagPsd2), None,
      http4sPartialFunction = Some(getTransactionTypes))

    // ─── createUser ───────────────────────────────────────────────────────────

    val createUser: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "users" =>
        EndpointHelpers.executeFutureWithBodyCreated[CreateUserJson, JSONFactory200.UserJsonV200](req) { (body, cc) =>
          for {
            _ <- code.util.Helper.booleanToFuture(InvalidStrongPasswordFormat, cc = Some(cc)) {
              fullPasswordValidation(body.password)
            }
            _ <- code.util.Helper.booleanToFuture(DuplicateUsername, failCode = 409, cc = Some(cc)) {
              AuthUser.find(By(AuthUser.username, body.username)).isEmpty
            }
            userCreated <- Future {
              AuthUser.create
                .firstName(body.first_name)
                .lastName(body.last_name)
                .username(body.username)
                .email(body.email)
                .password(body.password)
                .validated(APIUtil.getPropsAsBoolValue("authUser.skipEmailValidation", defaultValue = false))
            }
            _ <- code.util.Helper.booleanToFuture(
              InvalidJsonFormat + userCreated.validate.map(_.msg).mkString(";"), cc = Some(cc)) {
              userCreated.validate.isEmpty
            }
            savedUser <- NewStyle.function.tryons(InvalidJsonFormat, 400, Some(cc)) {
              userCreated.saveMe()
            }
            _ <- code.util.Helper.booleanToFuture(s"$UnknownError Error occurred during user creation.", cc = Some(cc)) {
              userCreated.saved_?
            }
          } yield {
            val skipEmailValidation = APIUtil.getPropsAsBoolValue("authUser.skipEmailValidation", defaultValue = false)
            if (!skipEmailValidation) AuthUser.sendValidationEmail(savedUser)
            AuthUser.grantDefaultEntitlementsToAuthUser(savedUser)
            createUserJSONfromAuthUser(userCreated)
          }
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion, nameOf(createUser), "POST", "/users",
      "Create User",
      s"""Creates OBP user.
      | No authorisation required.
      |
      | Mimics current webform to Register.
      |
      | Requires username(email), password, first_name, last_name, and email.
      |
      | Validation checks performed:
      | - Password must meet strong password requirements (InvalidStrongPasswordFormat error if not)
      | - Username must be unique (409 error if username already exists)
      | - All required fields must be present in valid JSON format
      |
      | Email validation behavior:
      | - Controlled by property 'authUser.skipEmailValidation' (default: false)
      | - When false: User is created with validated=false and a validation email is sent to the user's email address
      | - When true: User is created with validated=true and no validation email is sent
      | - Default entitlements are granted immediately regardless of validation status
      |
      | Note: If email validation is required (skipEmailValidation=false), the user must click the validation link
      | in the email before they can log in, even though entitlements are already granted.
      |
      |""",
      createUserJson, userJsonV200,
      // Intentional drift from Lift's APIMethods200.scala source-of-truth:
      // `AuthenticatedUserIsRequired` removed. Lift's createUser handler is
      // anonymous (no authenticatedAccess / anonymousAccess wrapper — straight
      // into JSON extraction), but Lift's ResourceDoc errors list includes
      // `AuthenticatedUserIsRequired`. Lift's runtime ignored that contradiction
      // because OBPRestHelper doesn't enforce ResourceDoc errors at request
      // dispatch time. The http4s `ResourceDocMiddleware` does — it derives
      // `needsAuthentication = errors.contains($AuthenticatedUserIsRequired) ||
      // roles.nonEmpty`, so leaving the error in makes middleware reject
      // unauthenticated POST /users with 401 instead of letting the handler
      // run and return 201 (or 409 on duplicate). Same pattern as upstream
      // commit 14abed06c for v3.1.0's getObpConnectorLoopback.
      List(InvalidJsonFormat, InvalidStrongPasswordFormat, DuplicateUsername, ExternalUserCheckFailed, "Error occurred during user creation.", UnknownError),
      List(apiTagUser, apiTagOnboarding), None,
      http4sPartialFunction = Some(createUser))

    // ─── createCustomer ───────────────────────────────────────────────────────

    val createCustomer: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "banks" / _ / "customers" =>
        EndpointHelpers.withUserAndBankAndBodyCreated[CreateCustomerJson, JSONFactory1_4_0.CustomerJsonV140](req) { (user, bank, body, cc) =>
          for {
            _ <- code.util.Helper.booleanToFuture(InvalidBankIdFormat, cc = Some(cc)) {
              isValidID(bank.bankId.value)
            }
            _ <- code.util.Helper.booleanToFuture(
              s"${InvalidJsonFormat} customer_number can not contain `::::` characters", cc = Some(cc)) {
              !`checkIfContains::::` (body.customer_number)
            }
            _ <- code.util.Helper.booleanToFuture(
              s"${UserHasMissingRoles}${canCreateCustomer} and ${canCreateUserCustomerLink} entitlements are required for BankId(${bank.bankId.value}).",
              failCode = 403, cc = Some(cc)
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
                bank.bankId, body.customer_number, body.legal_name, body.mobile_phone_number, body.email,
                CustomerFaceImage(body.face_image.date, body.face_image.url),
                body.date_of_birth, body.relationship_status, body.dependants, body.dob_of_dependants,
                body.highest_education_attained, body.employment_status, body.kyc_status, body.last_ok_date,
                None, None, "", "", ""
              ).getOrElse(throw new RuntimeException(CreateConsumerError))
            }
            _ <- code.util.Helper.booleanToFuture(CustomerAlreadyExistsForUser, cc = Some(cc)) {
              UserCustomerLink.userCustomerLink.vend.getUserCustomerLink(userId, customer.customerId).isEmpty
            }
            _ <- Future {
              UserCustomerLink.userCustomerLink.vend
                .createUserCustomerLink(userId, customer.customerId, new Date(), true)
                .getOrElse(throw new RuntimeException(CreateUserCustomerLinksError))
            }
          } yield JSONFactory1_4_0.createCustomerJson(customer)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion, nameOf(createCustomer), "POST",
      "/banks/BANK_ID/customers",
      "Create Customer",
      s"""Add a customer linked to the user specified by user_id
      |The Customer resource stores the customer number, legal name, email, phone number, their date of birth, relationship status, education attained, a url for a profile image, KYC status etc.
      |This call may require additional permissions/role in the future.
      |For now the authenticated user can create at most one linked customer.
      |Dates need to be in the format 2013-01-21T23:08:00Z
      |${userAuthenticationMessage(true)}
      |""",
      createCustomerJson, customerJsonV140,
      List(InvalidBankIdFormat, AuthenticatedUserIsRequired, BankNotFound, CustomerNumberAlreadyExists,
        UserHasMissingRoles, UserNotFoundById, CreateConsumerError, CustomerAlreadyExistsForUser,
        CreateUserCustomerLinksError, UnknownError),
      List(apiTagCustomer, apiTagPerson, apiTagOldStyle),
      Some(List(canCreateCustomer, canCreateUserCustomerLink)),
      http4sPartialFunction = Some(createCustomer))

    // ─── getCurrentUser ───────────────────────────────────────────────────────

    val getCurrentUser: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "users" / "current" =>
        EndpointHelpers.withUser(req) { (user, cc) =>
          Future.successful(createUserJSON(user))
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion, nameOf(getCurrentUser), "GET", "/users/current",
      "Get User (Current)",
      """Get the logged in user
        |
        |Login is required.""".stripMargin,
      EmptyBody, userJsonV200,
      List(AuthenticatedUserIsRequired, UnknownError),
      List(apiTagUser, apiTagOldStyle), None,
      http4sPartialFunction = Some(getCurrentUser))

    // ─── getUser ──────────────────────────────────────────────────────────────

    val getUser: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "users" / userEmail =>
        EndpointHelpers.withUser(req) { (user, cc) =>
          for {
            _ <- code.util.Helper.booleanToFuture(UserHasMissingRoles + canGetAnyUser, failCode = 403, cc = Some(cc)) {
              APIUtil.hasEntitlement("", user.userId, canGetAnyUser)
            }
            users <- Future {
              AuthUser.getResourceUsersByEmail(userEmail)
            }
          } yield JSONFactory200.createUserJSONs(users)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion, nameOf(getUser), "GET", "/users/USER_EMAIL",
      "Get Users by Email Address",
      """Get users by email address
        |
        |Login is required.
        |CanGetAnyUser entitlement is required,
        |
      """.stripMargin,
      EmptyBody, usersJsonV200,
      List(AuthenticatedUserIsRequired, UserHasMissingRoles, UserNotFoundByEmail, UnknownError),
      List(apiTagUser, apiTagOldStyle),
      Some(List(canGetAnyUser)),
      http4sPartialFunction = Some(getUser))

    // ─── createUserCustomerLinks ──────────────────────────────────────────────

    val createUserCustomerLinks: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "banks" / _ / "user_customer_links" =>
        EndpointHelpers.withUserAndBankAndBody[CreateUserCustomerLinkJson, UserCustomerLinkJson](req) { (_, bank, body, cc) =>
          for {
            _ <- code.util.Helper.booleanToFuture(s"$InvalidBankIdFormat", cc = Some(cc)) {
              isValidID(bank.bankId.value)
            }
            _ <- code.util.Helper.booleanToFuture("Field customer_id is not defined in the posted json!", cc = Some(cc)) {
              body.customer_id.nonEmpty
            }
            targetUser <- Users.users.vend.getUserByUserIdFuture(body.user_id) map {
              x => unboxFullOrFail(x, Some(cc), UserNotFoundByUserId, 404)
            }
            (customer, cc2) <- NewStyle.function.getCustomerByCustomerId(body.customer_id, Some(cc))
            _ <- code.util.Helper.booleanToFuture(
              s"Bank of the customer specified by the CUSTOMER_ID(${customer.bankId}) has to matches BANK_ID(${bank.bankId.value}) in URL",
              cc = cc2) {
              customer.bankId == bank.bankId.value
            }
            _ <- code.util.Helper.booleanToFuture(CustomerAlreadyExistsForUser, cc = cc2) {
              UserCustomerLink.userCustomerLink.vend.getUserCustomerLink(body.user_id, body.customer_id).isEmpty
            }
            userCustomerLink <- Future {
              unboxFullOrFail(
                UserCustomerLink.userCustomerLink.vend.createUserCustomerLink(body.user_id, body.customer_id, new Date(), true),
                cc2, CreateUserCustomerLinksError, 400)
            }
            _ <- AuthUser.refreshUser(targetUser, cc2)
          } yield createUserCustomerLinkJSON(userCustomerLink)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion, nameOf(createUserCustomerLinks), "POST",
      "/banks/BANK_ID/user_customer_links",
      "Create User Customer Link",
      s"""Link a User to a Customer
      |
      |${userAuthenticationMessage(true)}
      |
      |$createUserCustomerLinksrequiredEntitlementsText
      |""",
      createUserCustomerLinkJson, userCustomerLinkJson,
      List(AuthenticatedUserIsRequired, InvalidBankIdFormat, BankNotFound, InvalidJsonFormat,
        CustomerNotFoundByCustomerId, UserHasMissingRoles, CustomerAlreadyExistsForUser,
        CreateUserCustomerLinksError, UnknownError),
      List(apiTagCustomer, apiTagUser, apiTagOldStyle),
      Some(List(canCreateUserCustomerLink, canCreateUserCustomerLinkAtAnyBank)),
      http4sPartialFunction = Some(createUserCustomerLinks))

    // ─── addEntitlement ───────────────────────────────────────────────────────

    val addEntitlement: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "users" / userId / "entitlements" =>
        EndpointHelpers.withUserAndBodyCreated[CreateEntitlementJSON, EntitlementJSON](req) { (user, body, cc) =>
          for {
            (_, cc2) <- NewStyle.function.findByUserId(userId, Some(cc))
            role <- Future {
              unboxFullOrFail(
                net.liftweb.util.Helpers.tryo { ApiRole.valueOf(body.role_name) },
                Some(cc), IncorrectRoleName + body.role_name + ". Possible roles are " + ApiRole.availableRoles.sorted.mkString(", "))
            }
            _ <- code.util.Helper.booleanToFuture(
              if (ApiRole.valueOf(body.role_name).requiresBankId) EntitlementIsBankRole else EntitlementIsSystemRole,
              cc = cc2) {
              ApiRole.valueOf(body.role_name).requiresBankId == body.bank_id.nonEmpty
            }
            requiredEntitlements = canCreateEntitlementAtOneBank :: canCreateEntitlementAtAnyBank :: Nil
            requiredEntitlementsTxt = UserNotSuperAdmin + " or" + UserHasMissingRoles + canCreateEntitlementAtOneBank +
              s" BankId(${body.bank_id})." + " or" + UserHasMissingRoles + canCreateEntitlementAtAnyBank
            _ <- if (isSuperAdmin(user.userId)) Future.successful(Full(()))
                 else code.util.Helper.booleanToFuture(requiredEntitlementsTxt, failCode = 403, cc = cc2) {
                   APIUtil.hasAtLeastOneEntitlement(body.bank_id, user.userId, requiredEntitlements)
                 }
            _ <- code.util.Helper.booleanToFuture(BankNotFound, cc = cc2) {
              body.bank_id.isEmpty || BankX(BankId(body.bank_id), cc2).map(_._1).isDefined
            }
            _ <- code.util.Helper.booleanToFuture(EntitlementAlreadyExists, cc = cc2) {
              !hasEntitlement(body.bank_id, userId, role)
            }
            addedEntitlement <- Future {
              unboxFull(Entitlement.entitlement.vend.addEntitlement(body.bank_id, userId, body.role_name))
            }
          } yield JSONFactory200.createEntitlementJSON(addedEntitlement)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion, nameOf(addEntitlement), "POST",
      "/users/USER_ID/entitlements",
      "Add Entitlement for a User",
      """Create Entitlement. Grant Role to User.
      |
      |Entitlements are used to grant System or Bank level roles to Users. (For Account level privileges, see Views)
      |
      |For a System level Role (.e.g CanGetAnyUser), set bank_id to an empty string i.e. "bank_id":""
      |
      |For a Bank level Role (e.g. CanCreateAccount), set bank_id to a valid value e.g. "bank_id":"my-bank-id"
      |
      |Authentication is required and the user needs to be a Super Admin. Super Admins are listed in the Props file.""",
      code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON.createEntitlementJSON, entitlementJSON,
      List(AuthenticatedUserIsRequired, UserNotFoundById, UserNotSuperAdmin, InvalidJsonFormat,
        IncorrectRoleName, EntitlementIsBankRole, EntitlementIsSystemRole, EntitlementAlreadyExists, UnknownError),
      List(apiTagRole, apiTagEntitlement, apiTagUser),
      None, // bank comes from request body, not URL — middleware can't check, handler does it inline
      http4sPartialFunction = Some(addEntitlement))

    // ─── getEntitlements ──────────────────────────────────────────────────────

    val getEntitlements: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "users" / userId / "entitlements" =>
        EndpointHelpers.withUser(req) { (user, cc) =>
          for {
            _ <- code.util.Helper.booleanToFuture(UserHasMissingRoles + canGetEntitlementsForAnyUserAtAnyBank, failCode = 403, cc = Some(cc)) {
              APIUtil.hasEntitlement("", user.userId, canGetEntitlementsForAnyUserAtAnyBank)
            }
            entitlements <- Entitlement.entitlement.vend.getEntitlementsByUserIdFuture(userId) map {
              connectorEmptyResponse(_, Some(cc))
            }
          } yield {
            if (isSuperAdmin(userId)) JSONFactory200.withVirtualEntitlements(entitlements, APIUtil.superAdminVirtualRoles)
            else if (isOidcOperator(userId)) JSONFactory200.withVirtualEntitlements(entitlements, APIUtil.oidcOperatorVirtualRoles)
            else JSONFactory200.createEntitlementJSONs(entitlements)
          }
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion, nameOf(getEntitlements), "GET",
      "/users/USER_ID/entitlements",
      "Get Entitlements for User",
      s"""${userAuthenticationMessage(true)}""",
      EmptyBody, entitlementJSONs,
      List(AuthenticatedUserIsRequired, UserHasMissingRoles, UnknownError),
      List(apiTagRole, apiTagEntitlement, apiTagUser, apiTagOldStyle),
      Some(List(canGetEntitlementsForAnyUserAtAnyBank)),
      http4sPartialFunction = Some(getEntitlements))

    // ─── deleteEntitlement ────────────────────────────────────────────────────

    val deleteEntitlement: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ DELETE -> `prefixPath` / "users" / userId / "entitlement" / entitlementId =>
        EndpointHelpers.withUserDelete(req) { (user, cc) =>
          for {
            _ <- code.util.Helper.booleanToFuture(UserHasMissingRoles + canDeleteEntitlementAtAnyBank, failCode = 403, cc = Some(cc)) {
              APIUtil.hasEntitlement("", user.userId, canDeleteEntitlementAtAnyBank)
            }
            entitlement <- Future {
              unboxFullOrFail(
                Entitlement.entitlement.vend.getEntitlementById(entitlementId),
                Some(cc), EntitlementNotFound, 404)
            }
            _ <- code.util.Helper.booleanToFuture(UserDoesNotHaveEntitlement, cc = Some(cc)) {
              entitlement.userId == userId
            }
            _ <- Future {
              fullBoxOrException(
                Entitlement.entitlement.vend.deleteEntitlement(Some(entitlement))
                  ~> APIFailureNewStyle(EntitlementCannotBeDeleted, 500, Some(cc.toLight)))
            }
          } yield ()
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion, nameOf(deleteEntitlement), "DELETE",
      "/users/USER_ID/entitlement/ENTITLEMENT_ID",
      "Delete Entitlement",
      """Delete Entitlement specified by ENTITLEMENT_ID for an user specified by USER_ID
        |
        |Authentication is required and the user needs to be a Super Admin.
        |Super Admins are listed in the Props file.
        |
        |
      """.stripMargin,
      EmptyBody, EmptyBody,
      List(AuthenticatedUserIsRequired, UserHasMissingRoles, EntitlementNotFound, UnknownError),
      List(apiTagRole, apiTagUser, apiTagEntitlement),
      Some(List(canDeleteEntitlementAtAnyBank)),
      http4sPartialFunction = Some(deleteEntitlement))

    // ─── getAllEntitlements ────────────────────────────────────────────────────

    val getAllEntitlements: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "entitlements" =>
        EndpointHelpers.withUser(req) { (user, cc) =>
          for {
            _ <- code.util.Helper.booleanToFuture(UserHasMissingRoles + canGetEntitlementsForAnyUserAtAnyBank, failCode = 403, cc = Some(cc)) {
              APIUtil.hasEntitlement("", user.userId, canGetEntitlementsForAnyUserAtAnyBank)
            }
            entitlements <- Entitlement.entitlement.vend.getEntitlementsFuture() map {
              connectorEmptyResponse(_, Some(cc))
            }
          } yield JSONFactory200.createEntitlementJSONs(entitlements)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion, nameOf(getAllEntitlements), "GET", "/entitlements",
      "Get all Entitlements",
      """Login is required.""",
      EmptyBody, entitlementJSONs,
      List(AuthenticatedUserIsRequired, UnknownError),
      List(apiTagRole, apiTagEntitlement),
      Some(List(canGetEntitlementsForAnyUserAtAnyBank)),
      http4sPartialFunction = Some(getAllEntitlements))

    // ─── elasticSearchWarehouse ───────────────────────────────────────────────

    val esw = new elasticsearchWarehouse

    val elasticSearchWarehouse: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "search" / "warehouse" / queryString =>
        EndpointHelpers.withUser(req) { (user, cc) =>
          for {
            _ <- code.util.Helper.booleanToFuture(UserHasMissingRoles + canSearchWarehouse, failCode = 403, cc = Some(cc)) {
              APIUtil.hasEntitlement("", user.userId, canSearchWarehouse)
            }
          } yield {
            esw.searchProxy(user.userId, queryString)
          }
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion, nameOf(elasticSearchWarehouse), "GET",
      "/search/warehouse",
      "Search Warehouse Data Via Elasticsearch",
      """
      |Search warehouse data via Elastic Search.
      |
      |Login is required.
      |
      |CanSearchWarehouse entitlement is required to search warehouse data!
      |
      |Send your email, name, project name and user_id to the admins to get access.
      |
      |Elastic (search) is used in the background. See links below for syntax.
      |
      |
      |parameters:
      |
      | esType  - elasticsearch type
      |
      | simple query:
      |
      | q       - plain_text_query
      |
      | df      - default field to search
      |
      | sort    - field to sort on
      |
      | size    - number of hits returned, default 10
      |
      | from    - show hits starting from
      |
      | json query:
      |
      | source  - JSON_query_(URL-escaped)
      |
      |
      |Example usage:
      |
      |GET /search/warehouse/q=findThis
      |
      |or:
      |
      |GET /search/warehouse/source={"query":{"query_string":{"query":"findThis"}}}
      |
      |
      |Note!!
      |
      |The whole JSON query string MUST be URL-encoded:
      |
      |* For {  use %7B
      |* For }  use %7D
      |* For : use %3A
      |* For " use %22
      |
      |etc..
      |
      |
      |
      |Only q, source and esType are passed to Elastic
      |
      |Elastic simple query: https://www.elastic.co/guide/en/elasticsearch/reference/current/search-uri-request.html
      |
      |Elastic JSON query: https://www.elastic.co/guide/en/elasticsearch/reference/current/query-filter-context.html
      |
      |You can specify the esType thus: /search/warehouse/esType=type&q=a
      |
      """,
      EmptyBody, emptyElasticSearch,
      //TODO what is output here?
      List(AuthenticatedUserIsRequired, BankNotFound, UserHasMissingRoles, UnknownError),
      List(apiTagSearchWarehouse, apiTagOldStyle),
      Some(List(canSearchWarehouse)),
      http4sPartialFunction = Some(elasticSearchWarehouse))

    // ─── elasticSearchMetrics ─────────────────────────────────────────────────

    val esm = new elasticsearchMetrics

    val elasticSearchMetrics: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "search" / "metrics" / queryString =>
        EndpointHelpers.withUser(req) { (user, cc) =>
          for {
            _ <- code.util.Helper.booleanToFuture(UserHasMissingRoles + canSearchMetrics, failCode = 403, cc = Some(cc)) {
              APIUtil.hasEntitlement("", user.userId, canSearchMetrics)
            }
          } yield {
            esm.searchProxy(user.userId, queryString)
          }
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion, nameOf(elasticSearchMetrics), "GET",
      "/search/metrics",
      "Search API Metrics via Elasticsearch",
      """
      |Search the API calls made to this API instance via Elastic Search.
      |
      |Login is required.
      |
      |CanSearchMetrics entitlement is required to search metrics data.
      |
      |
      |parameters:
      |
      | esType  - elasticsearch type
      |
      | simple query:
      |
      | q       - plain_text_query
      |
      | df      - default field to search
      |
      | sort    - field to sort on
      |
      | size    - number of hits returned, default 10
      |
      | from    - show hits starting from
      |
      | json query:
      |
      | source  - JSON_query_(URL-escaped)
      |
      |
      |example usage:
      |
      | /search/metrics/q=findThis
      |
      |or:
      |
      | /search/metrics/source={"query":{"query_string":{"query":"findThis"}}}
      |
      |
      |Note!!
      |
      |The whole JSON query string MUST be URL-encoded:
      |
      |* For {  use %7B
      |* For }  use %7D
      |* For : use %3A
      |* For " use %22
      |
      |etc..
      |
      |
      |
      |Only q, source and esType are passed to Elastic
      |
      |Elastic simple query: https://www.elastic.co/guide/en/elasticsearch/reference/current/search-uri-request.html
      |
      |Elastic JSON query: https://www.elastic.co/guide/en/elasticsearch/reference/current/query-filter-context.html
      |
      |
      """,
      EmptyBody, emptyElasticSearch,
      List(AuthenticatedUserIsRequired, UserHasMissingRoles, UnknownError),
      List(apiTagMetric, apiTagApi, apiTagOldStyle),
      Some(List(canSearchMetrics)),
      http4sPartialFunction = Some(elasticSearchMetrics))

    // ─── getCustomers ─────────────────────────────────────────────────────────

    val getCustomers: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "users" / "current" / "customers" =>
        EndpointHelpers.withUser(req) { (user, cc) =>
          Future {
            val customers = CustomerX.customerProvider.vend.getCustomersByUserId(user.userId)
            JSONFactory1_4_0.createCustomersJson(customers)
          }
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion, nameOf(getCustomers), "GET",
      "/users/current/customers",
      "Get all customers for logged in user",
      """Information about the currently authenticated user.
        |
        |Authentication via OAuth is required.""",
      EmptyBody, customersJsonV140,
      List(AuthenticatedUserIsRequired, UserCustomerLinksNotFoundForUser, UnknownError),
      List(apiTagPerson, apiTagCustomer, apiTagOldStyle), None,
      http4sPartialFunction = Some(getCustomers))

    // ─── allRoutes ────────────────────────────────────────────────────────────

    private val allOwnRoutes: HttpRoutes[IO] = Kleisli[HttpF, Request[IO], Response[IO]] { req =>
      root.run(req)
        .orElse(getPrivateAccountsAllBanks.run(req))
        .orElse(corePrivateAccountsAllBanks.run(req))
        .orElse(publicAccountsAllBanks.run(req))
        .orElse(getPrivateAccountsAtOneBank.run(req))
        .orElse(corePrivateAccountsAtOneBank.run(req))
        .orElse(privateAccountsAtOneBank.run(req))
        .orElse(publicAccountsAtOneBank.run(req))
        .orElse(getKycDocuments.run(req))
        .orElse(getKycMedia.run(req))
        .orElse(getKycChecks.run(req))
        .orElse(getKycStatuses.run(req))
        .orElse(getSocialMediaHandles.run(req))
        .orElse(addKycDocument.run(req))
        .orElse(addKycMedia.run(req))
        .orElse(addKycCheck.run(req))
        .orElse(addKycStatus.run(req))
        .orElse(addSocialMediaHandle.run(req))
        .orElse(getCoreAccountById.run(req))
        .orElse(getCoreTransactionsForBankAccount.run(req))
        .orElse(accountById.run(req))
        .orElse(getPermissionsForBankAccount.run(req))
        .orElse(getPermissionForUserForBankAccount.run(req))
        .orElse(createAccount.run(req))
        .orElse(getTransactionTypes.run(req))
        .orElse(createUser.run(req))
        .orElse(createCustomer.run(req))
        .orElse(getCustomers.run(req))
        .orElse(getCurrentUser.run(req))
        .orElse(getUser.run(req))
        .orElse(createUserCustomerLinks.run(req))
        .orElse(addEntitlement.run(req))
        .orElse(getEntitlements.run(req))
        .orElse(deleteEntitlement.run(req))
        .orElse(getAllEntitlements.run(req))
        .orElse(elasticSearchWarehouse.run(req))
        .orElse(elasticSearchMetrics.run(req))
    }

    val allRoutesWithMiddleware: HttpRoutes[IO] = ResourceDocMiddleware.apply(resourceDocs)(allOwnRoutes)

    // ─── path-rewriting bridge: /obp/v2.0.0/… → /obp/v1.4.0/… ──────────────
    // Delegates to Http4s140 so all inherited v1.4.0/v1.3.0/v1.2.1 endpoints are
    // served under the v2.0.0 URL prefix without duplicating any logic.

    val v200ToV140Bridge: HttpRoutes[IO] = Kleisli[HttpF, Request[IO], Response[IO]] { req =>
      val rawPath = req.uri.path.renderString
      if (rawPath.startsWith("/obp/v2.0.0/")) {
        val rewritten    = rawPath.replaceFirst("/obp/v2\\.0\\.0/", "/obp/v1.4.0/")
        val newUri       = req.uri.withPath(Uri.Path.unsafeFromString(rewritten))
        val rewrittenReq = req.withUri(newUri)
        code.api.v1_4_0.Http4s140.wrappedRoutesV140Services.run(rewrittenReq)
      } else {
        OptionT.none[IO, Response[IO]]
      }
    }
  }

  // Own middleware-wrapped routes take priority; inherited v1.4.0/v1.3.0/v1.2.1 paths follow.
  val wrappedRoutesV200Services: HttpRoutes[IO] =
    Kleisli[HttpF, Request[IO], Response[IO]] { req =>
      Implementations2_0_0.allRoutesWithMiddleware.run(req)
        .orElse(Implementations2_0_0.v200ToV140Bridge.run(req))
    }
}
