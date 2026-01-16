package code.api.v7_0_0

import cats.data.{Kleisli, OptionT}
import cats.effect._
import code.api.Constant._
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON._
import code.api.ResourceDocs1_4_0.{ResourceDocs140, ResourceDocsAPIMethodsUtil}
import code.api.util.APIUtil.{EmptyBody, _}
import code.api.util.ApiTag._
import code.api.util.ErrorMessages._
import code.api.util.{ApiRole, ApiVersionUtils, CallContext, CustomJsonFormats, NewStyle}
import code.api.util.ApiRole.canReadResourceDoc
import code.api.util.http4s.{Http4sCallContextBuilder, Http4sVaultKeys, ResourceDocMiddleware, ErrorResponseConverter}
import code.api.v1_4_0.JSONFactory1_4_0
import code.api.v4_0_0.JSONFactory400
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.util.{ApiVersion, ApiVersionStatus, ScannedApiVersion}
import net.liftweb.json.JsonAST.prettyRender
import net.liftweb.json.{Extraction, Formats}
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.headers._

import java.util.UUID
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Future
import scala.language.{higherKinds, implicitConversions}

object Http4s700 {

  type HttpF[A] = OptionT[IO, A]

  implicit val formats: Formats = CustomJsonFormats.formats
  implicit def convertAnyToJsonString(any: Any): String = prettyRender(Extraction.decompose(any))

  val implementedInApiVersion: ScannedApiVersion = ApiVersion.v7_0_0
  val versionStatus = ApiVersionStatus.STABLE.toString
  val resourceDocs = ArrayBuffer[ResourceDoc]()

  object Implementations7_0_0 {

    // Common prefix: /obp/v7.0.0
    val prefixPath = Root / ApiPathZero.toString / implementedInApiVersion.toString
    private val jsonContentType: `Content-Type` = `Content-Type`(MediaType.application.json)


    // ResourceDoc with $UserNotLoggedIn in errorResponseBodies indicates auth is required
    // ResourceDocMiddleware will automatically handle authentication based on this metadata
    // No explicit auth code needed in the endpoint handler - just like Lift's wrappedWithAuthCheck
    resourceDocs += ResourceDoc(
      null,
      implementedInApiVersion,
      nameOf(root),
      "GET",
      "/root",
      "Get API Info (root)",
      s"""Returns information about:
        |
        |* API version
        |* Hosted by information
        |* Git Commit
        |${userAuthenticationMessage(false)}""",
      EmptyBody,
      apiInfoJSON, 
      List(
        UnknownError, 
        "no connector set"
      ),
      apiTagApi :: Nil,
      http4sPartialFunction = Some(root)
    )

    // Route: GET /obp/v7.0.0/root
    // Authentication is handled automatically by ResourceDocMiddleware based on $UserNotLoggedIn in ResourceDoc
    // The endpoint code only contains business logic - validated User is available from request attributes
    val root: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "root" =>
        val responseJson = convertAnyToJsonString(
          JSONFactory700.getApiInfoJSON(implementedInApiVersion, s"Hello")
        )
        
        Ok(responseJson).map(_.withContentType(jsonContentType))
    }

    resourceDocs += ResourceDoc(
      null,
      implementedInApiVersion,
      nameOf(getBanks),
      "GET",
      "/banks",
      "Get Banks",
      s"""Get banks on this API instance
        |Returns a list of banks supported on this server:
        |
        |* ID used as parameter in URLs
        |* Short and full name of bank
        |* Logo URL
        |* Website
        |${userAuthenticationMessage(false)}""",
      EmptyBody,
      banksJSON,
      List(UnknownError),
      apiTagBank :: Nil,
      http4sPartialFunction = Some(getBanks)
    )

    // Route: GET /obp/v7.0.0/banks
    val getBanks: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" =>

        val responseJson = convertAnyToJsonString(
          JSONFactory700.getApiInfoJSON(implementedInApiVersion, s"Hello ")
        )
        Ok(responseJson).map(_.withContentType(jsonContentType))
    }

    val getResourceDocsObpV700: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "resource-docs" / requestedApiVersionString / "obp" =>
        import com.openbankproject.commons.ExecutionContext.Implicits.global
        val response = for {
          cc <- Http4sCallContextBuilder.fromRequest(req, implementedInApiVersion.toString)
          result <- IO.fromFuture(IO {
            // Check resource_docs_requires_role property
            val resourceDocsRequireRole = getPropsAsBoolValue("resource_docs_requires_role", false)
            
            for {
              // Authentication based on property
              (boxUser, cc1) <- if (resourceDocsRequireRole) 
                authenticatedAccess(cc)
              else 
                anonymousAccess(cc)
              
              // Role check based on property
              _ <- if (resourceDocsRequireRole) {
                NewStyle.function.hasAtLeastOneEntitlement(
                  failMsg = UserHasMissingRoles + canReadResourceDoc.toString
                )("", boxUser.map(_.userId).getOrElse(""), ApiRole.canReadResourceDoc :: Nil, cc1)
              } else {
                Future.successful(())
              }
              
              httpParams <- NewStyle.function.extractHttpParamsFromUrl(req.uri.renderString)
              tagsParam = httpParams.filter(_.name == "tags").map(_.values).headOption
              functionsParam = httpParams.filter(_.name == "functions").map(_.values).headOption
              localeParam = httpParams.filter(param => param.name == "locale" || param.name == "language").map(_.values).flatten.headOption
              contentParam = httpParams.filter(_.name == "content").map(_.values).flatten.flatMap(ResourceDocsAPIMethodsUtil.stringToContentParam).headOption
              apiCollectionIdParam = httpParams.filter(_.name == "api-collection-id").map(_.values).flatten.headOption
              tags = tagsParam.map(_.map(ResourceDocTag(_)))
              functions = functionsParam.map(_.toList)
              requestedApiVersion <- Future(ApiVersionUtils.valueOf(requestedApiVersionString))
              resourceDocs = ResourceDocs140.ImplementationsResourceDocs.getResourceDocsList(requestedApiVersion).getOrElse(Nil)
              filteredDocs = ResourceDocsAPIMethodsUtil.filterResourceDocs(resourceDocs, tags, functions)
              resourceDocsJson = JSONFactory1_4_0.createResourceDocsJson(filteredDocs, isVersion4OrHigher = true, localeParam)
            } yield convertAnyToJsonString(resourceDocsJson)
          })
        } yield result
        Ok(response).map(_.withContentType(jsonContentType))
    }
    
    // Example endpoint demonstrating full validation chain with ResourceDocMiddleware
    // This endpoint requires: authentication + bank validation + account validation + view validation
    // When using ResourceDocMiddleware, these validations are automatic based on path parameters
    resourceDocs += ResourceDoc(
      null,
      implementedInApiVersion,
      nameOf(getAccountByIdWithMiddleware),
      "GET",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/account",
      "Get Account by Id (http4s with middleware)",
      s"""Get account by id with automatic validation via ResourceDocMiddleware.
        |
        |This endpoint demonstrates the full validation chain:
        |* Authentication (required)
        |* Bank existence validation (BANK_ID in path)
        |* Account existence validation (ACCOUNT_ID in path)
        |* View access validation (VIEW_ID in path)
        |
        |${userAuthenticationMessage(true)}""",
      EmptyBody,
      moderatedAccountJSON,
      List(UserNotLoggedIn, BankNotFound, BankAccountNotFound, ViewNotFound, UserNoPermissionAccessView, UnknownError),
      apiTagAccount :: Nil,
      http4sPartialFunction = Some(getAccountByIdWithMiddleware)
    )
    
    // Route: GET /obp/v7.0.0/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/account
    // When used with ResourceDocMiddleware, validation is automatic
    val getAccountByIdWithMiddleware: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / bankId / "accounts" / accountId / viewId / "account" =>
        import com.openbankproject.commons.ExecutionContext.Implicits.global
        
        // When using middleware, validated objects are available in request attributes
        val userOpt = Http4sVaultKeys.getUser(req)
        val bankOpt = Http4sVaultKeys.getBank(req)
        val accountOpt = Http4sVaultKeys.getBankAccount(req)
        val viewOpt = Http4sVaultKeys.getView(req)
        val ccOpt = Http4sVaultKeys.getCallContext(req)
        
        val response = for {
          // If middleware was used, objects are already validated and available
          // If not using middleware, we need to build CallContext and validate manually
          cc <- ccOpt match {
            case Some(existingCC) => IO.pure(existingCC)
            case None => Http4sCallContextBuilder.fromRequest(req, implementedInApiVersion.toString)
          }
          
          result <- IO.fromFuture(IO {
            for {
              // If middleware was used, these are already validated
              // If not, we need to validate manually
              (boxUser, cc1) <- if (userOpt.isDefined) {
                Future.successful((net.liftweb.common.Full(userOpt.get), Some(cc)))
              } else {
                authenticatedAccess(cc)
              }
              
              (bank, cc2) <- if (bankOpt.isDefined) {
                Future.successful((bankOpt.get, cc1))
              } else {
                NewStyle.function.getBank(com.openbankproject.commons.model.BankId(bankId), cc1)
              }
              
              (account, cc3) <- if (accountOpt.isDefined) {
                Future.successful((accountOpt.get, cc2))
              } else {
                NewStyle.function.getBankAccount(
                  com.openbankproject.commons.model.BankId(bankId), 
                  com.openbankproject.commons.model.AccountId(accountId), 
                  cc2
                )
              }
              
              (view, cc4) <- if (viewOpt.isDefined) {
                Future.successful((viewOpt.get, cc3))
              } else {
                code.api.util.newstyle.ViewNewStyle.checkViewAccessAndReturnView(
                  com.openbankproject.commons.model.ViewId(viewId),
                  com.openbankproject.commons.model.BankIdAccountId(
                    com.openbankproject.commons.model.BankId(bankId),
                    com.openbankproject.commons.model.AccountId(accountId)
                  ),
                  boxUser.toOption,
                  cc3
                ).map(v => (v, cc3))
              }
              
              // Create simple account response (avoiding complex moderated account dependencies)
              accountResponse = Map(
                "bank_id" -> bankId,
                "account_id" -> accountId,
                "view_id" -> viewId,
                "label" -> account.label,
                "bank_name" -> bank.fullName
              )
            } yield convertAnyToJsonString(accountResponse)
          })
        } yield result
        
        Ok(response).map(_.withContentType(jsonContentType))
    }

    // All routes combined (without middleware - for direct use)
    val allRoutes: HttpRoutes[IO] =
      Kleisli[HttpF, Request[IO], Response[IO]] { req: Request[IO] =>
        root(req)
          .orElse(getBanks(req))
          .orElse(getResourceDocsObpV700(req))
          .orElse(getAccountByIdWithMiddleware(req))
      }
    
    // Routes wrapped with ResourceDocMiddleware for automatic validation
    val allRoutesWithMiddleware: HttpRoutes[IO] = 
      ResourceDocMiddleware.apply(resourceDocs)(allRoutes)
  }

  // Routes with ResourceDocMiddleware - provides automatic validation based on ResourceDoc metadata
  // Authentication is automatic based on $UserNotLoggedIn in ResourceDoc errorResponseBodies
  // This matches Lift's wrappedWithAuthCheck behavior
  val wrappedRoutesV700Services: HttpRoutes[IO] = Implementations7_0_0.allRoutesWithMiddleware
}
