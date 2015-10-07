package code.api.v1_4_0

import java.util.Date

import code.api.util.APIUtil.ResourceDoc
import code.common.{Meta, License, Location, Address}
import code.atms.Atms.Atm
import code.branches.Branches.{Branch}
import code.crm.CrmEvent.{CrmEvent, CrmEventId}
import code.products.Products.{Product}


import code.customer.{CustomerMessage, Customer}
import code.model._
import code.products.Products.ProductCode
import code.transactionrequests.TransactionRequests._
import net.liftweb.json.JsonAST.{JValue, JObject}
import org.pegdown.PegDownProcessor

object JSONFactory1_4_0 {

  case class CustomerJson(customer_number : String,
                              legal_name : String,
                              mobile_phone_number : String,
                              email : String,
                              face_image : CustomerFaceImageJson)

  case class CustomerFaceImageJson(url : String, date : Date)

  case class CustomerMessagesJson(messages : List[CustomerMessageJson])
  case class CustomerMessageJson(id : String, date : Date, message : String, from_department : String, from_person : String)

  case class AddCustomerMessageJson(message : String, from_department : String, from_person : String)

  case class LicenseJson(id : String, name : String)

  case class MetaJson(license : LicenseJson)

  case class LocationJson(latitude : Double, longitude : Double)

  case class DriveUpJson(hours : String)
  case class LobbyJson(hours : String)



  case class BranchJson(id : String,
                        name : String,
                        address : AddressJson,
                        location : LocationJson,
                        lobby : LobbyJson,
                        drive_up: DriveUpJson,
                        meta : MetaJson)

  case class BranchesJson (branches : List[BranchJson])


  case class AtmJson(id : String,
                        name : String,
                        address : AddressJson,
                        location : LocationJson,
                        meta : MetaJson)

  case class AtmsJson (atms : List[AtmJson])


  case class AddressJson(line_1 : String, line_2 : String, line_3 : String, city : String, state : String, postcode : String, country : String)





  def createCustomerJson(cInfo : Customer) : CustomerJson = {

    CustomerJson(customer_number = cInfo.number,
      legal_name = cInfo.legalName, mobile_phone_number = cInfo.mobileNumber,
      email = cInfo.email, face_image = CustomerFaceImageJson(url = cInfo.faceImage.url, date = cInfo.faceImage.date))

  }

  def createCustomerMessageJson(cMessage : CustomerMessage) : CustomerMessageJson = {
    CustomerMessageJson(id = cMessage.messageId, date = cMessage.date,
      message = cMessage.message, from_department = cMessage.fromDepartment,
      from_person = cMessage.fromPerson)
  }

  def createCustomerMessagesJson(messages : List[CustomerMessage]) : CustomerMessagesJson = {
    CustomerMessagesJson(messages.map(createCustomerMessageJson))
  }

  // Accept a license object and return its json representation
  def createLicenseJson(license : License) : LicenseJson = {
    LicenseJson(license.id, license.name)
  }

  def createLocationJson(location : Location) : LocationJson = {
    LocationJson(location.latitude, location.longitude)
  }


  def createDriveUpJson(hours : String) : DriveUpJson = {
    DriveUpJson(hours)
  }

  def createLobbyJson(hours : String) : LobbyJson = {
    LobbyJson(hours)
  }

  def createMetaJson(meta: Meta) : MetaJson = {
    MetaJson(createLicenseJson(meta.license))
  }


  // Accept an address object and return its json representation
  def createAddressJson(address : Address) : AddressJson = {
    AddressJson(address.line1, address.line2, address.line3, address.city, address.state, address.postCode, address.countryCode)
  }

  // Branches

  def createBranchJson(branch: Branch) : BranchJson = {
    BranchJson(branch.branchId.value,
                branch.name,
                createAddressJson(branch.address),
                createLocationJson(branch.location),
                createLobbyJson(branch.lobby.hours),
                createDriveUpJson(branch.driveUp.hours),
                createMetaJson(branch.meta))
  }

  def createBranchesJson(branchesList: List[Branch]) : BranchesJson = {
    BranchesJson(branchesList.map(createBranchJson))
  }

  // Atms

  def createAtmJson(atm: Atm) : AtmJson = {
    AtmJson(atm.atmId.value,
      atm.name,
      createAddressJson(atm.address),
      createLocationJson(atm.location),
      createMetaJson(atm.meta))
  }

  def createAtmsJson(AtmsList: List[Atm]) : AtmsJson = {
    AtmsJson(AtmsList.map(createAtmJson))
  }

  // Products


  case class ProductJson(code : String,
                        name : String,
                        category: String,
                        family : String,
                        super_family : String,
                        more_info_url: String,
                        meta : MetaJson)

  case class ProductsJson (products : List[ProductJson])



  def createProductJson(product: Product) : ProductJson = {
    ProductJson(product.code.value,
      product.name,
      product.category,
      product.family,
      product.superFamily,
      product.moreInfoUrl,
      createMetaJson(product.meta))
  }

  def createProductsJson(productsList: List[Product]) : ProductsJson = {
    ProductsJson(productsList.map(createProductJson))
  }


  // Crm Events
  case class CrmEventJson(
    id: String,
    bank_id: String,
    customer_name : String,
    customer_number : String,
    category : String,
    detail : String,
    channel : String,
    scheduled_date : Date,
    actual_date: Date,
    result: String)

  case class CrmEventsJson (crm_events : List[CrmEventJson])

  def createCrmEventJson(crmEvent: CrmEvent) : CrmEventJson = {
    CrmEventJson(
      id = crmEvent.crmEventId.value,
      bank_id = crmEvent.bankId.value,
      customer_name = crmEvent.customerName,
      customer_number = crmEvent.customerNumber,
      category = crmEvent.category,
      detail = crmEvent.detail,
      channel = crmEvent.channel,
      scheduled_date = crmEvent.scheduledDate,
      actual_date = crmEvent.actualDate,
      result = crmEvent.result)
  }

  def createCrmEventsJson(crmEventList: List[CrmEvent]) : CrmEventsJson = {
    CrmEventsJson(crmEventList.map(createCrmEventJson))
  }


  // Used to describe where an API call is implemented
  case class ImplementedByJson (
    version : String, // Short hand for the version e.g. "1_4_0" means Implementations1_4_0
    function : String // The val / partial function that implements the call e.g. "getBranches"
  )


  // Used to describe the OBP API calls for documentation and API discovery purposes
  case class ResourceDocJson(operation_id: String,
                         request_verb: String,
                         request_url: String,
                         summary: String,
                         description: String,
                         example_request_body: JValue,
                         success_response_body: JValue,
                         implemented_by: ImplementedByJson)



  // Creates the json resource_docs
  case class ResourceDocsJson (resource_docs : List[ResourceDocJson])

  def createResourceDocJson(resourceDoc: ResourceDoc) : ResourceDocJson = {

    // There are multiple flavours of markdown. For instance, original markdown emphasises underscores (surrounds _ with (<em>))
    // But we don't want to have to escape underscores (\_) in our documentation
    // Thus we use a flavour of markdown that ignores underscores in words. (Github markdown does this too)
    // PegDown seems to be feature rich and ignores underscores in words by default.

    // We return html rather than markdown to the consumer so they don't have to bother with these questions.

    val pegDownProcessor : PegDownProcessor = new PegDownProcessor

    ResourceDocJson(
      operation_id = s"${resourceDoc.apiVersion.toString}-${resourceDoc.apiFunction.toString}",
      request_verb = resourceDoc.requestVerb,
      request_url = resourceDoc.requestUrl,
      summary = resourceDoc.summary,
      // Strip the margin character (|) and line breaks and convert from markdown to html
      description = pegDownProcessor.markdownToHtml(resourceDoc.description.stripMargin).replaceAll("\n", ""),
      example_request_body = resourceDoc.exampleRequestBody,
      success_response_body = resourceDoc.successResponseBody,
      implemented_by = ImplementedByJson(resourceDoc.apiVersion, resourceDoc.apiFunction)
      )
  }

  def createResourceDocsJson(resourceDocList: List[ResourceDoc]) : ResourceDocsJson = {
    ResourceDocsJson(resourceDocList.map(createResourceDocJson))
  }


  //transaction requests
  def getTransactionRequestBodyFromJson(body: TransactionRequestBodyJSON) : TransactionRequestBody = {
    val toAcc = TransactionRequestAccount (
      bank_id = body.to.bank_id,
      account_id = body.to.account_id
    )
    val amount = AmountOfMoney (
      currency = body.value.currency,
      amount = body.value.amount
    )

    TransactionRequestBody (
      to = toAcc,
      value = amount,
      description = body.description
    )
  }

  def getTransactionRequestFromJson(json : TransactionRequestJSON) : TransactionRequest = {
    val fromAcc = TransactionRequestAccount (
      json.from.bank_id,
      json.from.account_id
    )
    val challenge = TransactionRequestChallenge (
      id = json.challenge.id,
      allowed_attempts = json.challenge.allowed_attempts,
      challenge_type = json.challenge.challenge_type
    )

    TransactionRequest (
      transactionRequestId = TransactionRequestId(json.id),
      `type`= json.`type`,
      from = fromAcc,
      body = getTransactionRequestBodyFromJson(json.body),
      transaction_ids = json.transaction_ids,
      status = json.status,
      start_date = json.start_date,
      end_date = json.end_date,
      challenge = challenge
    )
  }

  case class AmountOfMoneyJSON (
                                currency : String,
                                amount : String
                              )
  case class TransactionRequestAccountJSON (
                             bank_id: String,
                             account_id : String
                            )

  case class TransactionRequestBodyJSON(to: TransactionRequestAccountJSON,
                              value : AmountOfMoneyJSON,
                              description : String,
                              challenge_type : String
                             )

  case class TransactionRequestJSON(id: String,
                          `type`: String,
                          from: TransactionRequestAccountJSON,
                          body: TransactionRequestBodyJSON,
                          transaction_ids: String,
                          status: String,
                          start_date: Date,
                          end_date: Date,
                          challenge: ChallengeJSON
                          )

  case class ChallengeJSON (
                           id: String,
                           allowed_attempts : Int,
                           challenge_type: String
                          )

  case class ChallengeAnswerJSON (
                             id: String,
                             answer : String
                           )

  /*case class ChallengeErrorJSON (
                          code : Int,
                          message: String
                        )
  */
}
