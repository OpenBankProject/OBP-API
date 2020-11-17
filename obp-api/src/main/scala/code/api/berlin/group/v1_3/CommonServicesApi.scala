package code.api.builder.CommonServicesApi

import code.api.berlin.group.v1_3.{JvalueCaseClass, OBP_BERLIN_GROUP_1_3}
import code.api.builder.AccountInformationServiceAISApi.APIMethods_AccountInformationServiceAISApi
import code.api.builder.PaymentInitiationServicePISApi.APIMethods_PaymentInitiationServicePISApi
import code.api.builder.SigningBasketsApi.APIMethods_SigningBasketsApi
import code.api.util.APIUtil._
import net.liftweb.http.rest.RestHelper
import net.liftweb.json._

import scala.collection.immutable.Nil
import scala.collection.mutable.ArrayBuffer

//TODO maybe we can remove this common services, it just show other apis in this tag. no new ones.
object APIMethods_CommonServicesApi extends RestHelper {
    val apiVersion =  OBP_BERLIN_GROUP_1_3.apiVersion
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
    APIMethods_PaymentInitiationServicePISApi.startPaymentAuthorisation ::
    APIMethods_PaymentInitiationServicePISApi.startPaymentInitiationCancellationAuthorisation ::
    APIMethods_PaymentInitiationServicePISApi.updatePaymentCancellationPsuData ::
    APIMethods_PaymentInitiationServicePISApi.updatePaymentPsuData ::
    APIMethods_AccountInformationServiceAISApi.startConsentAuthorisation ::
    APIMethods_AccountInformationServiceAISApi.updateConsentsPsuData ::
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
  resourceDocs += APIMethods_PaymentInitiationServicePISApi.resourceDocs.filter(_.partialFunction == APIMethods_PaymentInitiationServicePISApi.startPaymentAuthorisation).head
  resourceDocs += APIMethods_PaymentInitiationServicePISApi.resourceDocs.filter(_.partialFunction == APIMethods_PaymentInitiationServicePISApi.startPaymentInitiationCancellationAuthorisation).head
  resourceDocs += APIMethods_PaymentInitiationServicePISApi.resourceDocs.filter(_.partialFunction == APIMethods_PaymentInitiationServicePISApi.updatePaymentCancellationPsuData).head
  resourceDocs += APIMethods_PaymentInitiationServicePISApi.resourceDocs.filter(_.partialFunction == APIMethods_PaymentInitiationServicePISApi.updatePaymentPsuData).head
  
  resourceDocs += APIMethods_AccountInformationServiceAISApi.resourceDocs.filter(_.partialFunction == APIMethods_AccountInformationServiceAISApi.startConsentAuthorisation ).head
  resourceDocs += APIMethods_AccountInformationServiceAISApi.resourceDocs.filter(_.partialFunction == APIMethods_AccountInformationServiceAISApi.updateConsentsPsuData ).head
  resourceDocs += APIMethods_AccountInformationServiceAISApi.resourceDocs.filter(_.partialFunction == APIMethods_AccountInformationServiceAISApi.getConsentScaStatus).head
  
}



