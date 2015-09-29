package code.api.v1_4_0

import java.util.Date

import code.api.util.APIUtil.ResourceDoc
import code.common.{Meta, License, Location, Address}
import code.atms.Atms.Atm
import code.branches.Branches.{Branch}
import code.crm.CrmEvent.{CrmEvent, CrmEventId}
import code.products.Products.{Product}


import code.customer.{CustomerMessage, Customer}
import code.model.{AmountOfMoney, BankAccount, AccountId, BankId}
import code.products.Products.ProductCode
import code.transfers.Transfers._
import net.liftweb.json.JsonAST.{JValue, JObject}

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
  case class ResourceDocJson(id: String,
                         request_verb: String,
                         request_url: String,
                         description: String,
                         overview: String,
                         request_body: JValue,
                         response_body: JValue,
                         implemented_by: ImplementedByJson)



  // Creates the json resource_docs
  case class ResourceDocsJson (resource_docs : List[ResourceDocJson])

  def createResourceDocJson(resourceDoc: ResourceDoc) : ResourceDocJson = {
    ResourceDocJson(
      id = s"${resourceDoc.apiVersion.toString}-${resourceDoc.apiFunction.toString}",
      request_verb = resourceDoc.requestVerb,
      request_url = resourceDoc.requestUrl,
      description = resourceDoc.description,
      overview = resourceDoc.overview.stripMargin.replaceAll("\n", " "),
      request_body = resourceDoc.requestBody,
      response_body = resourceDoc.responseBody,
      implemented_by = ImplementedByJson(resourceDoc.apiVersion, resourceDoc.apiFunction)
      )
  }

  def createResourceDocsJson(resourceDocList: List[ResourceDoc]) : ResourceDocsJson = {
    ResourceDocsJson(resourceDocList.map(createResourceDocJson))
  }


  //payments / transfers
  def getTransferBodyFromJson(body: TransferBodyJSON) : TransferBody = {
    val toAcc = TransferAccount (
      bank_id = body.to.bank_id,
      account_id = body.to.account_id
    )
    val amount = AmountOfMoney (
      currency = body.value.currency,
      amount = body.value.amount
    )

    TransferBody (
      to = toAcc,
      value = amount,
      description = body.description
    )
  }

  def getTransferFromJson(json : TransferJSON) : Transfer = {
    val fromAcc = TransferAccount (
      json.from.bank_id,
      json.from.account_id
    )
    val challenge = TransferChallenge (
      id = json.challenge.id,
      allowed_attempts = json.challenge.allowed_attempts,
      challenge_type = json.challenge.challenge_type
    )

    Transfer (
      transferId = TransferId(json.id),
      `type`= json.`type`,
      from = fromAcc,
      body = getTransferBodyFromJson(json.body),
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
  case class TransferAccountJSON (
                             bank_id: String,
                             account_id : String
                            )

  case class TransferBodyJSON(to: TransferAccountJSON,
                              value : AmountOfMoneyJSON,
                              description : String
                             )

  case class TransferJSON(id: String,
                          `type`: String,
                          from: TransferAccountJSON,
                          body: TransferBodyJSON,
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
}
