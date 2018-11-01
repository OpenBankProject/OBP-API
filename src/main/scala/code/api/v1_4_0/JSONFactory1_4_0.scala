package code.api.v1_4_0

import java.util.Date

import code.api.util.APIUtil.ResourceDoc
import code.api.util.ApiRole
import code.api.v1_2_1.AmountOfMoneyJsonV121
import code.api.v3_0_0.BranchJsonV300
import code.atms.Atms.AtmT
import code.branches.Branches.BranchT
import code.common._
import code.crm.CrmEvent.CrmEvent
import code.customer.{Customer, CustomerMessage}
import code.model._
import code.products.Products.Product
import code.transactionrequests.TransactionRequestTypeCharge
import code.transactionrequests.TransactionRequests.{TransactionRequest, _}
import net.liftweb.common.Full
import net.liftweb.json
import net.liftweb.json.JValue
import net.liftweb.json.JsonAST.JValue
import org.pegdown.PegDownProcessor

import scala.reflect.runtime.currentMirror
import scala.reflect.runtime.universe._

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

  //case class BranchesJsonV300 (branches : List[BranchJsonV300])


  case class AtmJson(id : String,
                     name : String,
                     address : AddressJsonV140,
                     location : LocationJsonV140,
                     meta : MetaJsonV140)

  case class AtmsJson (atms : List[AtmJson])


  // Note this case class has country (not countryCode) and it is missing county
  case class AddressJsonV140(line_1 : String, line_2 : String, line_3 : String, city : String, state : String, postcode : String, country : String)
  
  case class TransactionRequestBodyJson (
    val to: TransactionRequestAccount,
    val value : AmountOfMoney,
    val description : String
  )
  
  case class TransactionRequestJson (
    id: TransactionRequestId,
    `type` : String,
    from: TransactionRequestAccount,
    details: TransactionRequestBodyJson, 
    body: TransactionRequestBodyJson,
    transaction_ids: String,
    status: String,
    start_date: Date,
    end_date: Date,
    challenge: TransactionRequestChallenge,
    charge: TransactionRequestCharge,
    charge_policy: String,
    counterparty_id :CounterpartyId,
    name :String,
    this_bank_id : BankId,
    this_account_id : AccountId,
    this_view_id :ViewId,
    other_account_routing_scheme : String,
    other_account_routing_address : String,
    other_bank_routing_scheme : String,
    other_bank_routing_address : String,
    is_beneficiary :Boolean
  )


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

  def createAtmJson(atm: AtmT) : AtmJson = {
    AtmJson(atm.atmId.value,
      atm.name,
      createAddressJson(atm.address),
      createLocationJson(atm.location),
      createMetaJson(atm.meta))
  }

  def createAtmsJson(AtmsList: List[AtmT]) : AtmsJson = {
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
                         implemented_by: ImplementedByJson,
                         request_verb: String,
                         request_url: String,
                         summary: String,
                         description: String,
                         example_request_body: scala.Product,
                         success_response_body: scala.Product,
                         error_response_bodies: List[String],
                         is_core: Boolean,
                         is_psd2: Boolean,
                         is_obwg: Boolean,
                         tags: List[String],
                         typed_request_body: JValue, //JSON Schema --> https://spacetelescope.github.io/understanding-json-schema/index.html
                         typed_success_response_body: JValue, //JSON Schema --> https://spacetelescope.github.io/understanding-json-schema/index.html
                         roles: Option[List[ApiRole]] = None,
                         is_featured: Boolean,
                         special_instructions: String)



  // Creates the json resource_docs
  case class ResourceDocsJson (resource_docs : List[ResourceDocJson])

  def createResourceDocJson(rd: ResourceDoc) : ResourceDocJson = {

    // There are multiple flavours of markdown. For instance, original markdown emphasises underscores (surrounds _ with (<em>))
    // But we don't want to have to escape underscores (\_) in our documentation
    // Thus we use a flavour of markdown that ignores underscores in words. (Github markdown does this too)
    // PegDown seems to be feature rich and ignores underscores in words by default.

    // We return html rather than markdown to the consumer so they don't have to bother with these questions.
    // Set the timeout: https://github.com/sirthias/pegdown#parsing-timeouts
    val PegDownProcessorTimeout: Long = 1000*20  
    val pegDownProcessor : PegDownProcessor = new PegDownProcessor(PegDownProcessorTimeout)

    ResourceDocJson(
      operation_id = s"v${rd.implementedInApiVersion.toString}-${rd.partialFunctionName.toString}",
      request_verb = rd.requestVerb,
      request_url = rd.requestUrl,
      summary = rd.summary,
      // Strip the margin character (|) and line breaks and convert from markdown to html
      description = pegDownProcessor.markdownToHtml(rd.description.stripMargin), //.replaceAll("\n", ""),
      example_request_body = rd.exampleRequestBody,
      success_response_body = rd.successResponseBody,
      error_response_bodies = rd.errorResponseBodies,
      implemented_by = ImplementedByJson(rd.implementedInApiVersion.noV(), rd.partialFunctionName),
      is_core = rd.catalogs.core,
      is_psd2 = rd.catalogs.psd2,
      is_obwg = rd.catalogs.obwg,
      tags = rd.tags.map(i => i.tag),
      typed_request_body = createTypedBody(rd.exampleRequestBody),
      typed_success_response_body = createTypedBody(rd.successResponseBody),
      roles = rd.roles,
      is_featured = rd.isFeatured,
      special_instructions = pegDownProcessor.markdownToHtml(rd.specialInstructions.getOrElse("").stripMargin)
      )
  }

  def createResourceDocsJson(resourceDocList: List[ResourceDoc]) : ResourceDocsJson = {
    ResourceDocsJson(resourceDocList.map(createResourceDocJson))
  }
  
  //please check issue first: https://github.com/OpenBankProject/OBP-API/issues/877
  //change: 
  // { "first_name": "George"} -->  {"type": "object","properties": {"first_name": {"type": "string" }
  /**
    * 
    * @param entity can be any entity, primitive or any references 
    * @param isArray is a Array or not. If it is Array the output format is different .
    * @return
    *         the OBP type format. 
    */
  def translateEntity(entity: Any, isArray:Boolean): String = {
    
    val r = currentMirror.reflect(entity)
    val mapOfFields = r.symbol.typeSignature.members.toStream
      .collect { case s: TermSymbol if !s.isMethod => r.reflectField(s)}
      .map(r => r.symbol.name.toString.trim -> r.get)
      .toMap
  
    val properties = for {
      (key, value) <- mapOfFields
    } yield {
      value match {
        //Date -- should be the first
        case i: Date                       => "\""  + key + """": {"type": "string","format": "date-time"}"""
        case Some(i: Date)                 => "\""  + key + """": {"type": "string","format": "date-time"}"""
        case List(i: Date, _*)             => "\""  + key + """": {"type": "array","items": {"type": "string","format": "date-time"}}""" 
        case Some(List(i: Date, _*))       => "\""  + key + """": {"type": "array","items": {"type": "string","format": "date-time"}}"""

        //Boolean - 4 kinds
        case i: Boolean                    => "\""  + key + """": {"type":"boolean"}""" 
        case Some(i: Boolean)              => "\""  + key + """": {"type":"boolean"}"""
        case List(i: Boolean, _*)          => "\""  + key + """": {"type": "array","items": {"type": "boolean"}}"""
        case Some(List(i: Boolean, _*))    => "\""  + key + """": {"type": "array","items": {"type": "boolean"}}"""
          
        //String --> Some field calleds `date`, we will treat the filed as a `date` object.
        //String --> But for some are not, eg: `date_of_birth` and `future_date` in V300Custom  
        case i: String if(key.contains("date")&& value.toString.length != "20181230".length)  => "\""  + key + """": {"type": "string","format": "date-time"}"""
        case Some(i: String) if(key.contains("date")&& value.toString.length != "20181230".length)  => "\""  + key + """": {"type": "string","format": "date-time"}"""
        case List(i: String, _*) if(key.contains("date")&& value.toString.length != "20181230".length)  => "\""  + key + """": {"type": "array","items": {"type": "string","format": "date-time"}}"""
        case Some(List(i: String, _*)) if(key.contains("date")&& value.toString.length != "20181230".length)  => "\""  + key + """": {"type": "array","items": {"type": "string","format": "date-time"}}"""
         
        //String-->
        case i: String                     => "\""  + key + """": {"type":"string"}"""
        case Some(i: String)               => "\""  + key + """": {"type":"string"}"""
        case List(i: String, _*)           => "\""  + key + """": {"type": "array","items": {"type": "string"}}""" 
        case Some(List(i: String, _*))     => "\""  + key + """": {"type": "array","items": {"type": "string"}}"""
        //Int 
        case i: Int                        => "\""  + key + """": {"type":"integer"}"""
        case Some(i: Int)                  => "\""  + key + """": {"type":"integer"}"""
        case List(i: Int, _*)              => "\""  + key + """": {"type": "array","items": {"type": "integer"}}"""
        case Some(List(i: Int, _*))        => "\""  + key + """": {"type": "array","items": {"type": "integer"}}"""
        //Long
        case i: Long                       => "\""  + key + """": {"type":"integer"}"""
        case Some(i: Long)                 => "\""  + key + """": {"type":"integer"}"""
        case List(i: Long, _*)             => "\""  + key + """": {"type": "array","items": {"type": "integer"}}"""
        case Some(List(i: Long, _*))       => "\""  + key + """": {"type": "array","items": {"type": "integer"}}"""
        //Float
        case i: Float                      => "\""  + key + """": {"type":"number"}"""
        case Some(i: Float)                => "\""  + key + """": {"type":"number"}"""
        case List(i: Float, _*)            => "\""  + key + """": {"type": "array","items": {"type": "number"}}"""
        case Some(List(i: Float, _*))      => "\""  + key + """": {"type": "array","items": {"type": "number"}}"""
        //Double
        case i: Double                     => "\""  + key + """": {"type":"number"}"""
        case Some(i: Double)               => "\""  + key + """": {"type":"number"}"""
        case List(i: Double, _*)           => "\""  + key + """": {"type": "array","items": {"type": "number"}}"""
        case Some(List(i: Double, _*))     => "\""  + key + """": {"type": "array","items": {"type": "number"}}"""
        
        //List case classes.  
        case List(f)                       => "\""  + key + """":""" +translateEntity(f,true)
        case List(f,_*)                    => "\""  + key + """":""" +translateEntity(f,true)
        case List(Some(f))                 => "\""  + key + """":""" +translateEntity(f,true)
        case List(Some(f),_*)              => "\""  + key + """":""" +translateEntity(f,true)
        case Some(List(f))                 => "\""  + key + """":""" +translateEntity(f,true)
        case Some(List(f,_*))              => "\""  + key + """":""" +translateEntity(f,true)
        //Single object
        case Some(f)                       => "\""  + key + """":""" +translateEntity(f,false)
        case null                          => "\""  + key + """":{"type":"null"}"""
        case f                             => "\""  + key + """":""" +translateEntity(f,false)
        // TODO resolve the warning patterns after a variable pattern cannot match (SLS 8.1.1)
        // case _ => "unknown"
      }
    }
    //Exclude all unrecognised fields and make part of fields definition
    // add comment and filter unknow
    // fields --> "id" : {"type":"integer", "format":"int32"} ,"name" : {"type":"string"} ,"bank": {"$ref":"#/definitions/Bank"} ,"banks": {"type": "array", "items":{"$ref": "#/definitions/Bank"}}  
    val fields: String = properties filter (_.contains("unknown") == false) mkString (",")
    //val definition = "\"" + entity.getClass.getSimpleName + "\":{" + requiredFieldsPart + """"properties": {""" + fields + """}}"""
    val definition = if (isArray)
        """{ "type": "array",  "items" : {"type": "object","properties": {""" + fields + """}}}""" 
      else 
        """{ "type": "object", "properties" : {""" + fields + """}}"""
    definition
  }
  
  def createTypedBody(exampleRequestBody: scala.Product): JValue = {
    val res = translateEntity(exampleRequestBody,false)
    json.parse(res)
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
  
  //We get rid of JValue in transaction request class. We need keep the response body the same as before. (compatability) 
  def transforOldTransactionRequest(transactionRequest: TransactionRequest) =
    Full(TransactionRequestJson(
      id = transactionRequest.id,
      `type` = transactionRequest.`type`,
      from = transactionRequest.from,
      details = TransactionRequestBodyJson(
        transactionRequest.body.to_sandbox_tan.get, 
        transactionRequest.body.value, 
        transactionRequest.body.description
      ),
      body = TransactionRequestBodyJson(
        transactionRequest.body.to_sandbox_tan.get, 
        transactionRequest.body.value, 
        transactionRequest.body.description
      ),
      transaction_ids = transactionRequest.transaction_ids, 
      status = transactionRequest.status,
      start_date = transactionRequest.start_date,
      end_date = transactionRequest.end_date,
      challenge = transactionRequest.challenge,
      charge = transactionRequest.charge,
      charge_policy = transactionRequest.charge_policy,
      counterparty_id = transactionRequest.counterparty_id,
      name = transactionRequest.name,
      this_bank_id  = transactionRequest.this_bank_id,
      this_account_id  = transactionRequest.this_account_id,
      this_view_id  = transactionRequest.this_view_id,
      other_account_routing_scheme  = transactionRequest.other_account_routing_scheme,
      other_account_routing_address  = transactionRequest.other_account_routing_address,
      other_bank_routing_scheme  = transactionRequest.other_bank_routing_scheme,
      other_bank_routing_address  = transactionRequest.other_bank_routing_address,
      is_beneficiary  = transactionRequest.is_beneficiary
    ))





}
