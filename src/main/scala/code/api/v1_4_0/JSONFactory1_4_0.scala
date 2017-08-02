package code.api.v1_4_0

import java.util.Date

import code.api.util.APIUtil.ResourceDoc
import code.api.v1_2_1.AmountOfMoneyJsonV121
import code.api.v3_0_0.BranchJsonV300
import code.atms.Atms.Atm
import code.branches.Branches.BranchT
import code.common._
import code.crm.CrmEvent.CrmEvent
import code.customer.{Customer, CustomerMessage}
import code.model._
import code.products.Products.Product
import code.transactionrequests.TransactionRequestTypeCharge
import code.transactionrequests.TransactionRequests._
import org.pegdown.PegDownProcessor


object JSONFactory1_4_0 {


  case class PostCustomerJson(
                          customer_number : String,
                          legal_name : String,
                          mobile_phone_number : String,
                          email : String,
                          face_image : CustomerFaceImageJson,
                          date_of_birth: Date,
                          relationship_status: String,
                          dependants: Int,
                          dob_of_dependants: List[Date],
                          highest_education_attained: String,
                          employment_status: String,
                          kyc_status: Boolean,
                          last_ok_date: Date)


  case class CustomerJsonV140(customer_id: String,
                          customer_number : String,
                          legal_name : String,
                          mobile_phone_number : String,
                          email : String,
                          face_image : CustomerFaceImageJson,
                          date_of_birth: Date,
                          relationship_status: String,
                          dependants: Int,
                          dob_of_dependants: List[Date],
                          highest_education_attained: String,
                          employment_status: String,
                          kyc_status: Boolean,
                          last_ok_date: Date)

  case class CustomersJsonV140(customers: List[CustomerJsonV140])

  case class CustomerFaceImageJson(url : String, date : Date)

  case class CustomerMessagesJson(messages : List[CustomerMessageJson])
  case class CustomerMessageJson(id : String, date : Date, message : String, from_department : String, from_person : String)

  case class AddCustomerMessageJson(message : String, from_department : String, from_person : String)

  case class LicenseJsonV140(id : String, name : String)

  case class MetaJsonV140(license : LicenseJsonV140)

  case class LocationJsonV140(latitude : Double, longitude : Double)

  case class DriveUpStringJson(hours : String)
  case class LobbyStringJson(hours : String)



  case class BranchRoutingJsonV141(
    scheme: String,
    address: String
  )

  case class BranchJson(id : String,
                        name : String,
                        address : AddressJsonV140,
                        location : LocationJsonV140,
                        lobby : LobbyStringJson,
                        drive_up: DriveUpStringJson,
                        meta : MetaJsonV140,
                        branch_routing: BranchRoutingJsonV141) // This is bad branch_routing should not have been put in V140

  case class BranchesJson (branches : List[BranchJson])

  case class BranchesJsonV300 (branches : List[BranchJsonV300])


  case class AtmJson(id : String,
                     name : String,
                     address : AddressJsonV140,
                     location : LocationJsonV140,
                     meta : MetaJsonV140)

  case class AtmsJson (atms : List[AtmJson])


  // Note this case class has country (not countryCode) and it is missing county
  case class AddressJsonV140(line_1 : String, line_2 : String, line_3 : String, city : String, state : String, postcode : String, country : String)





  def createCustomerJson(cInfo : Customer) : CustomerJsonV140 = {

    CustomerJsonV140(
      customer_id = cInfo.customerId,
      customer_number = cInfo.number,
      legal_name = cInfo.legalName,
      mobile_phone_number = cInfo.mobileNumber,
      email = cInfo.email,
      face_image = CustomerFaceImageJson(url = cInfo.faceImage.url,
        date = cInfo.faceImage.date),
      date_of_birth = cInfo.dateOfBirth,
      relationship_status = cInfo.relationshipStatus,
      dependants = cInfo.dependents,
      dob_of_dependants = cInfo.dobOfDependents,
      highest_education_attained = cInfo.highestEducationAttained,
      employment_status = cInfo.employmentStatus,
      kyc_status = cInfo.kycStatus,
      last_ok_date = cInfo.lastOkDate
    )



  }

  def createCustomersJson(customers : List[Customer]) : CustomersJsonV140 = {
    CustomersJsonV140(customers.map(createCustomerJson))
  }

  def createCustomerMessageJson(cMessage : CustomerMessage) : CustomerMessageJson = {
    CustomerMessageJson(id = cMessage.messageId,
                        date = cMessage.date,
                        message = cMessage.message,
                        from_department = cMessage.fromDepartment,
                        from_person = cMessage.fromPerson)
  }

  def createCustomerMessagesJson(messages : List[CustomerMessage]) : CustomerMessagesJson = {
    CustomerMessagesJson(messages.map(createCustomerMessageJson))
  }

  // Accept a license object and return its json representation
  def createLicenseJson(license : LicenseT) : LicenseJsonV140 = {
    LicenseJsonV140(license.id, license.name)
  }

  def createLocationJson(location : LocationT) : LocationJsonV140 = {
    LocationJsonV140(location.latitude, location.longitude)
  }


  def createDriveUpStringJson(hours : String) : DriveUpStringJson = {
    DriveUpStringJson(hours)
  }

  def createLobbyStringJson(hours : String) : LobbyStringJson = {
    LobbyStringJson(hours)
  }

  def createMetaJson(meta: MetaT) : MetaJsonV140 = {
    MetaJsonV140(createLicenseJson(meta.license))
  }


  // Accept an address object and return its json representation
  def createAddressJson(address : AddressT) : AddressJsonV140 = {
    AddressJsonV140(address.line1, address.line2, address.line3, address.city, address.state, address.postCode, address.countryCode)
  }

  // Branches

  def createBranchJson(branch: BranchT) : BranchJson = {
    BranchJson(branch.branchId.value,
                branch.name,
                createAddressJson(branch.address),
                createLocationJson(branch.location),
                createLobbyStringJson(branch.lobbyString.map(_.hours).getOrElse("")),
                createDriveUpStringJson(branch.driveUpString.map(_.hours).getOrElse("")),
                createMetaJson(branch.meta),
                BranchRoutingJsonV141(
                  scheme = branch.branchRouting.map(_.scheme).getOrElse(""),
                  address = branch.branchRouting.map(_.scheme).getOrElse("")
                )
    )
  }

//  def createBranchJson(branch: BranchT) : BranchJson = {
//    BranchJson(branch.branchId.value,
//      branch.name,
//      createAddressJson(branch.address),
//      createLocationJson(branch.location),
//      createLobbyStringJson(branch.lobbyString.getOrElse("")),
//      createDriveUpStringJson(branch.driveUpString.getOrElse("")),
//      createMetaJson(branch.meta),
//      BranchRoutingJsonV141(
//        scheme = branch.branchRouting.map(_.scheme).getOrElse(""),
//        address = branch.branchRouting.map(_.address).getOrElse("")
//      )
//    )
//  }




  def createBranchesJson(branchesList: List[BranchT]) : BranchesJson = {
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
                        meta : MetaJsonV140)

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
                         example_request_body: scala.Product,
                         success_response_body: scala.Product,
                         error_response_bodies: List[String],
                         implemented_by: ImplementedByJson,
                         is_core: Boolean,
                         is_psd2: Boolean,
                         is_obwg: Boolean,
                         tags: List[String])



  // Creates the json resource_docs
  case class ResourceDocsJson (resource_docs : List[ResourceDocJson])

  def createResourceDocJson(rd: ResourceDoc) : ResourceDocJson = {

    // There are multiple flavours of markdown. For instance, original markdown emphasises underscores (surrounds _ with (<em>))
    // But we don't want to have to escape underscores (\_) in our documentation
    // Thus we use a flavour of markdown that ignores underscores in words. (Github markdown does this too)
    // PegDown seems to be feature rich and ignores underscores in words by default.

    // We return html rather than markdown to the consumer so they don't have to bother with these questions.

    val pegDownProcessor : PegDownProcessor = new PegDownProcessor

    ResourceDocJson(
      operation_id = s"${rd.apiVersion.toString}-${rd.apiFunction.toString}",
      request_verb = rd.requestVerb,
      request_url = rd.requestUrl,
      summary = rd.summary,
      // Strip the margin character (|) and line breaks and convert from markdown to html
      description = pegDownProcessor.markdownToHtml(rd.description.stripMargin).replaceAll("\n", ""),
      example_request_body = rd.exampleRequestBody,
      success_response_body = rd.successResponseBody,
      error_response_bodies = rd.errorResponseBodies,
      implemented_by = ImplementedByJson(rd.apiVersion, rd.apiFunction),
      is_core = rd.catalogs.core,
      is_psd2 = rd.catalogs.psd2,
      is_obwg = rd.catalogs.obwg,// No longer tracking isCore
      tags = rd.tags.map(i => i.tag)
      )
  }

  def createResourceDocsJson(resourceDocList: List[ResourceDoc]) : ResourceDocsJson = {
    ResourceDocsJson(resourceDocList.map(createResourceDocJson))
  }


  //transaction requests
  def getTransactionRequestBodyFromJson(body: TransactionRequestBodyJsonV140) : TransactionRequestBody = {
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

  def getTransactionRequestFromJson(json : TransactionRequestJsonV140) : TransactionRequest = {
    val fromAcc = TransactionRequestAccount (
      json.from.bank_id,
      json.from.account_id
    )
    val challenge = TransactionRequestChallenge (
      id = json.challenge.id,
      allowed_attempts = json.challenge.allowed_attempts,
      challenge_type = json.challenge.challenge_type
    )

    val charge = TransactionRequestCharge("Total charges for a completed transaction request.", AmountOfMoney(json.body.value.currency, "0.05"))


    TransactionRequest (
      id = TransactionRequestId(json.id),
      `type`= json.`type`,
      from = fromAcc,
      details = null,
      body = getTransactionRequestBodyFromJson(json.body),
      transaction_ids = json.transaction_ids,
      status = json.status,
      start_date = json.start_date,
      end_date = json.end_date,
      challenge = challenge,
      charge = charge,
      charge_policy ="",// Note: charge_policy only used in V210. For V140 just set it empty
      counterparty_id =  CounterpartyId(""),// Note: counterparty only used in V210. For V140 just set it empty
      name = "",
      this_bank_id = BankId(""),
      this_account_id = AccountId(""),
      this_view_id = ViewId(""),
      other_account_routing_scheme = "",
      other_account_routing_address = "",
      other_bank_routing_scheme = "",
      other_bank_routing_address = "",
      is_beneficiary = true
    )
  }

  /**
    * package the transactionRequestTypeCharge
    */
  def createTransactionRequestTypesJSON(transactionRequestTypeCharges: TransactionRequestTypeCharge): TransactionRequestTypeJsonV140 = {
    TransactionRequestTypeJsonV140(transactionRequestTypeCharges.transactionRequestTypeId,
      TransactionRequestChargeJsonV140(transactionRequestTypeCharges.chargeSummary,
        AmountOfMoneyJsonV121(transactionRequestTypeCharges.chargeCurrency, transactionRequestTypeCharges.chargeAmount)))
  }

  /**
    * package the transactionRequestTypeCharges
    */
  def createTransactionRequestTypesJSONs(transactionRequestTypeCharges: List[TransactionRequestTypeCharge]): TransactionRequestTypesJsonV140 = {
    TransactionRequestTypesJsonV140(transactionRequestTypeCharges.map(createTransactionRequestTypesJSON))
  }

  case class TransactionRequestAccountJsonV140 (
                             bank_id: String,
                             account_id : String
                            )

  case class TransactionRequestBodyJsonV140 (
                              to: TransactionRequestAccountJsonV140,
                              value : AmountOfMoneyJsonV121,
                              description : String,
                              challenge_type : String
                             )

  case class TransactionRequestJsonV140(
                          id: String,
                          `type`: String,
                          from: TransactionRequestAccountJsonV140,
                          body: TransactionRequestBodyJsonV140,
                          transaction_ids: String,
                          status: String,
                          start_date: Date,
                          end_date: Date,
                          challenge: ChallengeJsonV140
                          )

  case class ChallengeJsonV140 (
                           id: String,
                           allowed_attempts : Int,
                           challenge_type: String
                          )

  case class ChallengeAnswerJSON (
                             id: String,
                             answer : String
                           )

  case class TransactionRequestChargeJsonV140(
    val summary: String,
    val value : AmountOfMoneyJsonV121
  )

  case class TransactionRequestTypeJsonV140(value: String, charge: TransactionRequestChargeJsonV140)

  case class TransactionRequestTypesJsonV140(transaction_request_types: List[TransactionRequestTypeJsonV140])


  // It seems we can't overload this function i.e. have to give it specific name because
  // else cant use it with a nested case class when the top case class is a different version
  def transformToLocationFromV140(locationJsonV140: LocationJsonV140): Location = {
    Location (
      latitude = locationJsonV140.latitude,
      longitude = locationJsonV140.longitude,
      date = None,
      user = None
    )
  }


  def transformV140ToLicence(licenseJsonV140: LicenseJsonV140): License = {
    License (
      id = licenseJsonV140.id,
      name = licenseJsonV140.name
    )
  }


  def transformToMetaFromV140(metaJsonV140: MetaJsonV140): Meta = {
    Meta (
      license = transformV140ToLicence (
        metaJsonV140.license)
    )
  }


  def transformToAddressFromV140(addressJsonV140: AddressJsonV140): Address = {
    Address(
      line1 = addressJsonV140.line_1,
      line2 = addressJsonV140.line_2,
      line3 = addressJsonV140.line_3,
      city = addressJsonV140.city,
      county = None,
      state = addressJsonV140.state,
      postCode = addressJsonV140.postcode,
      countryCode = addressJsonV140.country // May not be a code
    )
  }





}
