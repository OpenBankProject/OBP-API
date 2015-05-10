package code.api.v1_4_0

import java.util.Date

import code.branches.Branches
import code.branches.Branches.{Branch, Meta, License}
import code.customerinfo.{CustomerMessage, CustomerInfo}

object JSONFactory1_4_0 {

  case class CustomerInfoJson(customer_number : String,
                              legal_name : String,
                              mobile_phone_number : String,
                              email : String,
                              face_image : CustomerFaceImageJson)

  case class CustomerFaceImageJson(url : String, date : Date)

  case class CustomerMessagesJson(messages : List[CustomerMessageJson])
  case class CustomerMessageJson(id : String, date : Date, message : String, from_department : String, from_person : String)

  case class AddCustomerMessageJson(message : String, from_department : String, from_person : String)

  case class LicenseJson(name : String, url : String)

  case class MetaJson(license : LicenseJson)


  case class BranchJson(id : String, name : String, address : AddressJson, meta : MetaJson)
  case class BranchesJson (branches : List[BranchJson])

  case class AddressJson(line_1 : String, line_2 : String, line_3 : String, city : String, state : String, postcode : String, country : String)

  def createCustomerInfoJson(cInfo : CustomerInfo) : CustomerInfoJson = {

    CustomerInfoJson(customer_number = cInfo.number,
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
    LicenseJson(license.name, license.url)
  }

  def createMetaJson(meta: Meta) : MetaJson = {
    MetaJson(createLicenseJson(meta.license))
  }


  // Accept an address object and return its json representation
  def createAddressJson(address : Branches.Address) : AddressJson = {
    AddressJson(address.line1, address.line2, address.line3, address.city, address.state, address.postCode, address.countryCode)
  }

  def createBranchJson(branch: Branch) : BranchJson = {
    BranchJson(branch.branchId.value, branch.name, createAddressJson(branch.address), createMetaJson(branch.meta))
  }

  def createBranchesJson(branchesList: List[Branch]) : BranchesJson = {
    BranchesJson(branchesList.map(createBranchJson))
  }

}
