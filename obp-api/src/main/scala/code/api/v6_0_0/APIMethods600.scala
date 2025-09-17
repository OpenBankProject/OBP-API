package code.api.v6_0_0

import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON._
import code.api.util.APIUtil._
import code.api.util.ApiTag._
import code.api.util.ErrorMessages.{$UserNotLoggedIn, InvalidJsonFormat, UnknownError, _}
import code.api.util.FutureUtil.EndpointContext
import code.api.util.NewStyle
import code.api.util.NewStyle.HttpCode
import code.bankconnectors.LocalMappedConnectorInternal
import code.bankconnectors.LocalMappedConnectorInternal._
import code.entitlement.Entitlement
import code.views.Views
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model._
import com.openbankproject.commons.util.{ApiVersion, ScannedApiVersion}
import net.liftweb.common.Full
import net.liftweb.http.rest.RestHelper
import com.openbankproject.commons.ExecutionContext.Implicits.global

import scala.collection.immutable.{List, Nil}
import scala.collection.mutable.ArrayBuffer


trait APIMethods600 {
  self: RestHelper =>

  val Implementations6_0_0 = new Implementations600()

  class Implementations600 {

    val implementedInApiVersion: ScannedApiVersion = ApiVersion.v6_0_0

    private val staticResourceDocs = ArrayBuffer[ResourceDoc]()
    def resourceDocs = staticResourceDocs 

    val apiRelations = ArrayBuffer[ApiRelation]()
    val codeContext = CodeContext(staticResourceDocs, apiRelations)


    staticResourceDocs += ResourceDoc(
      getCurrentUser,
      implementedInApiVersion,
      nameOf(getCurrentUser), // TODO can we get this string from the val two lines above?
      "GET",
      "/users/current",
      "Get User (Current)",
      s"""Get the logged in user
         |
         |${userAuthenticationMessage(true)}
      """.stripMargin,
      EmptyBody,
      userJsonV300,
      List(UserNotLoggedIn, UnknownError),
      List(apiTagUser))

    lazy val getCurrentUser: OBPEndpoint = {
      case "users" :: "current" :: Nil JsonGet _ => {
        cc => {
          implicit val ec = EndpointContext(Some(cc))
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            entitlements <- NewStyle.function.getEntitlementsByUserId(u.userId, callContext)
          } yield {
            val permissions: Option[Permission] = Views.views.vend.getPermissionForUser(u).toOption
            val currentUser = UserV600(u, entitlements, permissions)
            val onBehalfOfUser = if(cc.onBehalfOfUser.isDefined) {
              val entitlements = Entitlement.entitlement.vend.getEntitlementsByUserId(cc.onBehalfOfUser.get.userId).headOption.toList.flatten
              val permissions: Option[Permission] = Views.views.vend.getPermissionForUser(cc.onBehalfOfUser.get).toOption
              Some(UserV600(cc.onBehalfOfUser.get, entitlements, permissions))
            } else {
              None
            }
            (JSONFactory600.createUserInfoJSON(currentUser, onBehalfOfUser), HttpCode.`200`(callContext))
          }
        }
      }
    }

    staticResourceDocs += ResourceDoc(
      createTransactionRequestCardano,
      implementedInApiVersion,
      nameOf(createTransactionRequestCardano),
      "POST",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/owner/transaction-request-types/CARDANO/transaction-requests",
      "Create Transaction Request (CARDANO)",
      s"""
         |
         |For sandbox mode, it will use the Cardano Preprod Network.
         |The accountId can be the wallet_id for now, as it uses cardano-wallet in the backend.
         |
         |${transactionRequestGeneralText}
         |
       """.stripMargin,
      transactionRequestBodyCardanoJsonV600,
      transactionRequestWithChargeJSON400,
      List(
        $UserNotLoggedIn,
        $BankNotFound,
        $BankAccountNotFound,
        InsufficientAuthorisationToCreateTransactionRequest,
        InvalidTransactionRequestType,
        InvalidJsonFormat,
        NotPositiveAmount,
        InvalidTransactionRequestCurrency,
        TransactionDisabled,
        UnknownError
      ),
      List(apiTagTransactionRequest, apiTagPSD2PIS, apiTagPsd2)
    )
    
    lazy val createTransactionRequestCardano: OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "transaction-request-types" ::
        "CARDANO" :: "transaction-requests" :: Nil JsonPost json -> _ =>
        cc => implicit val ec = EndpointContext(Some(cc))
          val transactionRequestType = TransactionRequestType("CARDANO")
          LocalMappedConnectorInternal.createTransactionRequest(bankId, accountId, viewId , transactionRequestType, json)
    }
    
  }
}



object APIMethods600 extends RestHelper with APIMethods600 {
  lazy val newStyleEndpoints: List[(String, String)] = Implementations6_0_0.resourceDocs.map {
    rd => (rd.partialFunctionName, rd.implementedInApiVersion.toString())
  }.toList
}

