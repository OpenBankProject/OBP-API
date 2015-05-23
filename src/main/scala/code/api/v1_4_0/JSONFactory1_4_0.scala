package code.api.v1_4_0

import java.util.Date

import code.branches.Branches
import code.branches.Branches.{Branch, Meta, License, Location}
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
  def createAddressJson(address : Branches.Address) : AddressJson = {
    AddressJson(address.line1, address.line2, address.line3, address.city, address.state, address.postCode, address.countryCode)
  }

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

}
