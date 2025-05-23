package code.api.builder.CommonServicesApi

import code.api.berlin.group.ConstantsBG
import code.api.berlin.group.v1_3.{JvalueCaseClass, OBP_BERLIN_GROUP_1_3}
import code.api.builder.AccountInformationServiceAISApi.APIMethods_AccountInformationServiceAISApi
import code.api.builder.PaymentInitiationServicePISApi.APIMethods_PaymentInitiationServicePISApi
import code.api.builder.SigningBasketsApi.APIMethods_SigningBasketsApi
import code.api.util.APIUtil._
import com.openbankproject.commons.util.ApiVersion
import net.liftweb.http.rest.RestHelper
import net.liftweb.json._

import scala.collection.immutable.Nil
import scala.collection.mutable.ArrayBuffer

//TODO maybe we can remove this common services, it just show other apis in this tag. no new ones.
object APIMethods_CommonServicesApi extends RestHelper {
    val apiVersion = ConstantsBG.berlinGroupVersion1
    val resourceDocs = ArrayBuffer[ResourceDoc]()
    val apiRelations = ArrayBuffer[ApiRelation]()
    val codeContext = CodeContext(resourceDocs, apiRelations)
    protected implicit def JvalueToSuper(what: JValue): JvalueCaseClass = JvalueCaseClass(what)

  
  val endpoints = APIMethods_SigningBasketsApi.deleteSigningBasket ::
    APIMethods_SigningBasketsApi.getSigningBasketAuthorisation ::
    APIMethods_SigningBasketsApi.getSigningBasketScaStatus ::
    APIMethods_SigningBasketsApi.startSigningBasketAuthorisation ::
    APIMethods_SigningBasketsApi.getSigningBasketStatus ::
    APIMethods_SigningBasketsApi.updateSigningBasketPsuData ::
    APIMethods_PaymentInitiationServicePISApi.getPaymentCancellationScaStatus ::
    APIMethods_PaymentInitiationServicePISApi.getPaymentInitiationAuthorisation ::
    APIMethods_PaymentInitiationServicePISApi.getPaymentInitiationScaStatus ::
    APIMethods_PaymentInitiationServicePISApi.startPaymentAuthorisationUpdatePsuAuthentication ::
    APIMethods_PaymentInitiationServicePISApi.startPaymentAuthorisationTransactionAuthorisation ::
    APIMethods_PaymentInitiationServicePISApi.startPaymentAuthorisationSelectPsuAuthenticationMethod ::
    APIMethods_PaymentInitiationServicePISApi.startPaymentInitiationCancellationAuthorisationTransactionAuthorisation ::
    APIMethods_PaymentInitiationServicePISApi.startPaymentInitiationCancellationAuthorisationUpdatePsuAuthentication ::
    APIMethods_PaymentInitiationServicePISApi.startPaymentInitiationCancellationAuthorisationSelectPsuAuthenticationMethod ::
    APIMethods_PaymentInitiationServicePISApi.updatePaymentCancellationPsuDataUpdatePsuAuthentication ::
    APIMethods_PaymentInitiationServicePISApi.updatePaymentCancellationPsuDataTransactionAuthorisation ::
    APIMethods_PaymentInitiationServicePISApi.updatePaymentCancellationPsuDataSelectPsuAuthenticationMethod ::
    APIMethods_PaymentInitiationServicePISApi.updatePaymentCancellationPsuDataAuthorisationConfirmation ::
    APIMethods_PaymentInitiationServicePISApi.updatePaymentPsuDataTransactionAuthorisation ::
    APIMethods_PaymentInitiationServicePISApi.updatePaymentPsuDataUpdatePsuAuthentication ::
    APIMethods_PaymentInitiationServicePISApi.updatePaymentPsuDataSelectPsuAuthenticationMethod ::
    APIMethods_PaymentInitiationServicePISApi.updatePaymentPsuDataAuthorisationConfirmation ::
    APIMethods_AccountInformationServiceAISApi.startConsentAuthorisationTransactionAuthorisation ::
    APIMethods_AccountInformationServiceAISApi.startConsentAuthorisationUpdatePsuAuthentication ::
    APIMethods_AccountInformationServiceAISApi.startConsentAuthorisationSelectPsuAuthenticationMethod ::
    APIMethods_AccountInformationServiceAISApi.updateConsentsPsuDataTransactionAuthorisation ::
    APIMethods_AccountInformationServiceAISApi.updateConsentsPsuDataUpdatePsuAuthentication ::
    APIMethods_AccountInformationServiceAISApi.updateConsentsPsuDataUpdateAuthorisationConfirmation ::
    APIMethods_AccountInformationServiceAISApi.updateConsentsPsuDataUpdateSelectPsuAuthenticationMethod ::
    APIMethods_AccountInformationServiceAISApi.getConsentScaStatus :: Nil


  resourceDocs += APIMethods_SigningBasketsApi.resourceDocs.filter(_.partialFunction == APIMethods_SigningBasketsApi.deleteSigningBasket).head
  resourceDocs += APIMethods_SigningBasketsApi.resourceDocs.filter(_.partialFunction == APIMethods_SigningBasketsApi.getSigningBasketAuthorisation).head
  resourceDocs += APIMethods_SigningBasketsApi.resourceDocs.filter(_.partialFunction == APIMethods_SigningBasketsApi.getSigningBasketScaStatus).head
  resourceDocs += APIMethods_SigningBasketsApi.resourceDocs.filter(_.partialFunction == APIMethods_SigningBasketsApi.startSigningBasketAuthorisation).head
  resourceDocs += APIMethods_SigningBasketsApi.resourceDocs.filter(_.partialFunction == APIMethods_SigningBasketsApi.getSigningBasketStatus).head
  resourceDocs += APIMethods_SigningBasketsApi.resourceDocs.filter(_.partialFunction == APIMethods_SigningBasketsApi.updateSigningBasketPsuData).head
  
  resourceDocs += APIMethods_PaymentInitiationServicePISApi.resourceDocs.filter(_.partialFunction == APIMethods_PaymentInitiationServicePISApi.getPaymentCancellationScaStatus).head
  resourceDocs += APIMethods_PaymentInitiationServicePISApi.resourceDocs.filter(_.partialFunction == APIMethods_PaymentInitiationServicePISApi.getPaymentInitiationAuthorisation).head
  resourceDocs += APIMethods_PaymentInitiationServicePISApi.resourceDocs.filter(_.partialFunction == APIMethods_PaymentInitiationServicePISApi.getPaymentInitiationScaStatus).head
  resourceDocs += APIMethods_PaymentInitiationServicePISApi.resourceDocs.filter(_.partialFunction == APIMethods_PaymentInitiationServicePISApi.startPaymentAuthorisationUpdatePsuAuthentication).head
  resourceDocs += APIMethods_PaymentInitiationServicePISApi.resourceDocs.filter(_.partialFunction == APIMethods_PaymentInitiationServicePISApi.startPaymentAuthorisationSelectPsuAuthenticationMethod).head
  resourceDocs += APIMethods_PaymentInitiationServicePISApi.resourceDocs.filter(_.partialFunction == APIMethods_PaymentInitiationServicePISApi.startPaymentAuthorisationTransactionAuthorisation).head
  resourceDocs += APIMethods_PaymentInitiationServicePISApi.resourceDocs.filter(_.partialFunction == APIMethods_PaymentInitiationServicePISApi.startPaymentInitiationCancellationAuthorisationTransactionAuthorisation).head
  resourceDocs += APIMethods_PaymentInitiationServicePISApi.resourceDocs.filter(_.partialFunction == APIMethods_PaymentInitiationServicePISApi.startPaymentInitiationCancellationAuthorisationUpdatePsuAuthentication).head
  resourceDocs += APIMethods_PaymentInitiationServicePISApi.resourceDocs.filter(_.partialFunction == APIMethods_PaymentInitiationServicePISApi.startPaymentInitiationCancellationAuthorisationSelectPsuAuthenticationMethod).head
  resourceDocs += APIMethods_PaymentInitiationServicePISApi.resourceDocs.filter(_.partialFunction == APIMethods_PaymentInitiationServicePISApi.updatePaymentCancellationPsuDataUpdatePsuAuthentication).head
  resourceDocs += APIMethods_PaymentInitiationServicePISApi.resourceDocs.filter(_.partialFunction == APIMethods_PaymentInitiationServicePISApi.updatePaymentCancellationPsuDataTransactionAuthorisation).head
  resourceDocs += APIMethods_PaymentInitiationServicePISApi.resourceDocs.filter(_.partialFunction == APIMethods_PaymentInitiationServicePISApi.updatePaymentCancellationPsuDataAuthorisationConfirmation).head
  resourceDocs += APIMethods_PaymentInitiationServicePISApi.resourceDocs.filter(_.partialFunction == APIMethods_PaymentInitiationServicePISApi.updatePaymentCancellationPsuDataSelectPsuAuthenticationMethod).head
  resourceDocs += APIMethods_PaymentInitiationServicePISApi.resourceDocs.filter(_.partialFunction == APIMethods_PaymentInitiationServicePISApi.updatePaymentPsuDataTransactionAuthorisation).head
  resourceDocs += APIMethods_PaymentInitiationServicePISApi.resourceDocs.filter(_.partialFunction == APIMethods_PaymentInitiationServicePISApi.updatePaymentPsuDataAuthorisationConfirmation).head
  resourceDocs += APIMethods_PaymentInitiationServicePISApi.resourceDocs.filter(_.partialFunction == APIMethods_PaymentInitiationServicePISApi.updatePaymentPsuDataSelectPsuAuthenticationMethod).head
  resourceDocs += APIMethods_PaymentInitiationServicePISApi.resourceDocs.filter(_.partialFunction == APIMethods_PaymentInitiationServicePISApi.updatePaymentPsuDataAuthorisationConfirmation).head
  
  resourceDocs += APIMethods_AccountInformationServiceAISApi.resourceDocs.filter(_.partialFunction == APIMethods_AccountInformationServiceAISApi.startConsentAuthorisationTransactionAuthorisation).head
  resourceDocs += APIMethods_AccountInformationServiceAISApi.resourceDocs.filter(_.partialFunction == APIMethods_AccountInformationServiceAISApi.startConsentAuthorisationUpdatePsuAuthentication).head
  resourceDocs += APIMethods_AccountInformationServiceAISApi.resourceDocs.filter(_.partialFunction == APIMethods_AccountInformationServiceAISApi.startConsentAuthorisationSelectPsuAuthenticationMethod).head
  resourceDocs += APIMethods_AccountInformationServiceAISApi.resourceDocs.filter(_.partialFunction == APIMethods_AccountInformationServiceAISApi.updateConsentsPsuDataTransactionAuthorisation).head
  resourceDocs += APIMethods_AccountInformationServiceAISApi.resourceDocs.filter(_.partialFunction == APIMethods_AccountInformationServiceAISApi.updateConsentsPsuDataUpdatePsuAuthentication).head
  resourceDocs += APIMethods_AccountInformationServiceAISApi.resourceDocs.filter(_.partialFunction == APIMethods_AccountInformationServiceAISApi.updateConsentsPsuDataUpdateAuthorisationConfirmation).head
  resourceDocs += APIMethods_AccountInformationServiceAISApi.resourceDocs.filter(_.partialFunction == APIMethods_AccountInformationServiceAISApi.updateConsentsPsuDataUpdateSelectPsuAuthenticationMethod).head
  resourceDocs += APIMethods_AccountInformationServiceAISApi.resourceDocs.filter(_.partialFunction == APIMethods_AccountInformationServiceAISApi.getConsentScaStatus).head
  
}



