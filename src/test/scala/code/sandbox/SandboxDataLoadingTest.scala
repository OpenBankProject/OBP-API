/**
Open Bank Project - API
Copyright (C) 2011-2016, TESOBE Ltd

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

you should have received a copy of the GNU Affero General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.

Email: contact@tesobe.com
TESOBE Ltd
Osloerstrasse 16/17
Berlin 13359, Germany

  This product includes software developed at
  TESOBE (http://www.tesobe.com/)
  by
  Simon Redfern : simon AT tesobe DOT com
  Stefan Bethge : stefan AT tesobe DOT com
  Everett Sochowski : everett AT tesobe DOT com
  Ayoub Benali: ayoub AT tesobe DOT com

  */
package code.sandbox

import java.text.SimpleDateFormat
import java.util.Date

import bootstrap.liftweb.ToSchemify
import code.TestServer
import code.accountholder.AccountHolders
import code.api.v1_2_1.APIMethods121
import code.atms.Atms
import code.atms.Atms.{Atm, AtmId, countOfAtms}
import code.branches.Branches
import code.branches.Branches.{Branch, BranchId, countOfBranches}
import code.crm.CrmEvent
import code.crm.CrmEvent
import code.crm.CrmEvent.{CrmEvent, CrmEventId}
import code.products.Products
import code.products.Products.{Product, ProductCode, countOfProducts}
import code.model.dataAccess._
import code.model._
import code.products.Products.ProductCode
import code.users.Users
import code.views.Views
import dispatch._
import net.liftweb.json.JsonAST.JObject
import net.liftweb.json.JsonAST.JValue
import net.liftweb.mapper.By
import net.liftweb.util.Props
import org.scalatest.{BeforeAndAfterEach, FlatSpec, MustMatchers}
import net.liftweb.json.JsonDSL._
import net.liftweb.json._
import net.liftweb.json.Serialization.write
import code.bankconnectors.Connector
import net.liftweb.common.{Empty, Full, ParamFailure}
import code.api.util.APIUtil._
import code.setup.{APIResponse, SendServerRequests}

/*
This tests:

Posting of json to the sandbox creation API endpoint.
Checking that the various objects were created OK via calling the Mapper.
 */
class SandboxDataLoadingTest extends FlatSpec with SendServerRequests with MustMatchers with BeforeAndAfterEach {

  val SUCCESS: Int = 201
  val FAILED: Int = 400

  implicit val formats = Serialization.formats(NoTypeHints)

  //tests running on the actual sandbox?
  val server = TestServer
  def baseRequest = host(server.host, server.port)

  def sandboxApiPrefix = baseRequest / "obp" / "vsandbox"

  //users must automatically be assigned the "hostname" as a provider (for now at least)
  val defaultProvider = Props.get("hostname").openOrThrowException("no hostname set")

  val theImportToken = Props.get("sandbox_data_import_secret").openOrThrowException("sandbox_data_import_secret not set")

  override def beforeEach() = {
    //drop database tables before
    //MongoDB.getDb(DefaultMongoIdentifier).foreach(_.dropDatabase())
    ToSchemify.models.foreach(_.bulkDelete_!!())
    if (!Props.getBool("remotedata.enable", false)) {
      ToSchemify.modelsRemotedata.foreach(_.bulkDelete_!!())
    } else {
      Views.views.vend.bulkDeleteAllPermissionsAndViews()
      Users.users.vend.bulkDeleteAllResourceUsers()
      AccountHolders.accountHolders.vend.bulkDeleteAllAccountHolders()
    }
  }


  def toJsonArray(xs : List[String]) : String = {
    xs.mkString("[", ",", "]")
  }

  def createImportJson(banks: List[JValue],
                       users: List[JValue],
                       accounts : List[JValue],
                       transactions : List[JValue],
                       branches : List[JValue],
                       atms : List[JValue],
                       products : List[JValue],
                       crm_events : List[JValue]) : String = {

    // Note: These keys must exactly match SandboxDataImport else consumer will get 404 when trying to call sandbox creation url
    val json =
      ("banks" -> banks) ~
      ("users" -> users) ~
      ("accounts" -> accounts) ~
      ("transactions" -> transactions) ~
      ("branches" -> branches) ~
      ("atms" -> atms) ~
      ("products" -> products) ~
      ("crm_events" -> crm_events)
    compact(render(json))
  }

  // posts the json with the correct secret token
  def postImportJson(json : String) : APIResponse = {
    postImportJson(json, Some(theImportToken))
  }

  def postImportJson(json : String, secretToken : Option[String]) : APIResponse = {
    val base = sandboxApiPrefix / "v1.0" / "data-import"

    // If we have a secretToken add that to the base request
    val request = secretToken match {
      case Some(t) => base <<? Map("secret_token" -> t)
      case None => base
    }
    makePostRequest(request, json)
  }

  def verifyBankCreated(bank : SandboxBankImport) = {
    val bankId = BankId(bank.id)
    val foundBankBox = Connector.connector.vend.getBank(bankId)

    foundBankBox.isDefined must equal(true)

    val foundBank = foundBankBox match {
      case Full(b) => b
    }

    foundBank.bankId must equal(bankId)
    foundBank.shortName must equal(bank.short_name)
    foundBank.fullName must equal(bank.full_name)
    foundBank.logoUrl must equal(bank.logo)
    foundBank.websiteUrl must equal(bank.website)
  }

  def verifyBranchCreated(branch : SandboxBranchImport) = {
    //compare branches with data retrieved from connector (i.e. the db)

    // Get ids from input
    val bankId = BankId(branch.bank_id)
    val branchId = BranchId(branch.id)

    // check we have found a branch
    val foundBranchOpt: Option[Branch] = Branches.branchesProvider.vend.getBranch(branchId)
    foundBranchOpt.isDefined must equal(true)

    val foundBranch = foundBranchOpt.get
    foundBranch.name must equal(branch.name)
    foundBranch.address.line1 must equal(branch.address.line_1)
    foundBranch.address.line2 must equal(branch.address.line_2)
    foundBranch.address.line3 must equal(branch.address.line_3)
    foundBranch.address.city must equal(branch.address.city)
    foundBranch.address.county must equal(branch.address.county)
    foundBranch.address.state must equal(branch.address.state)

    foundBranch.location.latitude must equal(branch.location.latitude)
    foundBranch.location.longitude must equal(branch.location.longitude)

    foundBranch.address.postCode must equal(branch.address.post_code)
    foundBranch.address.countryCode must equal(branch.address.country_code)

    foundBranch.meta.license.id must equal(branch.meta.license.id)
    foundBranch.meta.license.name must equal(branch.meta.license.name)

    foundBranch.lobby.hours must equal(branch.lobby.get.hours)     // TODO Check None situation (lobby is None)
    foundBranch.driveUp.hours must equal(branch.driveUp.get.hours) // TODO Check None situation (driveUp is None)
  }

  def verifyAtmCreated(atm : SandboxAtmImport) = {
    // Get ids from input
    val bankId = BankId(atm.bank_id)
    val atmId = AtmId(atm.id)

    // check we have found a branch
    val foundAtmOpt: Option[Atm] = Atms.atmsProvider.vend.getAtm(atmId)
    foundAtmOpt.isDefined must equal(true)

    val foundAtm = foundAtmOpt.get
    foundAtm.name must equal(atm.name)
    foundAtm.address.line1 must equal(atm.address.line_1)
    foundAtm.address.line2 must equal(atm.address.line_2)
    foundAtm.address.line3 must equal(atm.address.line_3)
    foundAtm.address.city must equal(atm.address.city)
    foundAtm.address.county must equal(atm.address.county)
    foundAtm.address.state must equal(atm.address.state)

    foundAtm.location.latitude must equal(atm.location.latitude)
    foundAtm.location.longitude must equal(atm.location.longitude)

    foundAtm.address.postCode must equal(atm.address.post_code)
    foundAtm.address.countryCode must equal(atm.address.country_code)

    foundAtm.meta.license.id must equal(atm.meta.license.id)
    foundAtm.meta.license.name must equal(atm.meta.license.name)
  }


  def verifyProductCreated(product : SandboxProductImport) = {
    // Get ids from input
    val bankId = BankId(product.bank_id)
    val code = ProductCode(product.code)

    // check we have found a product
    val foundProductOpt: Option[Product] = Products.productsProvider.vend.getProduct(bankId, code)
    foundProductOpt.isDefined must equal(true)

    val foundProduct = foundProductOpt.get
    foundProduct.bankId.toString must equal (product.bank_id)
    foundProduct.code.value must equal(product.code)
    foundProduct.name must equal(product.name)
    foundProduct.category must equal(product.category)
    foundProduct.family must equal(product.family)
    foundProduct.superFamily must equal(product.super_family)
    foundProduct.moreInfoUrl must equal(product.more_info_url)
  }



  def verifyCrmEventCreated(crmEvent : SandboxCrmEventImport) = {
    // Get ids from input
    val bankId = BankId(crmEvent.bank_id)
    val crmEventId = CrmEventId(crmEvent.id)

    // check we have found a CrmEvent
    val foundCrmEventOpt: Option[CrmEvent] = CrmEvent.crmEventProvider.vend.getCrmEvent(crmEventId)
    foundCrmEventOpt.isDefined must equal(true)

    val foundCrmEvent = foundCrmEventOpt.get
//    foundCrmEvent.actualDate must equal (crmEvent.actual_date)
    foundCrmEvent.category must equal (crmEvent.category)
    foundCrmEvent.channel must equal (crmEvent.channel)
    foundCrmEvent.detail must equal (crmEvent.detail)
    foundCrmEvent.customerName must equal (crmEvent.customer.name)
    foundCrmEvent.customerNumber must equal (crmEvent.customer.number)
    // TODO check dates

  }

  def verifyUserCreated(user : SandboxUserImport) = {
    val foundUserBox = Users.users.vend.getUserByProviderId(defaultProvider, user.user_name)
    foundUserBox.isDefined must equal(true)

    val foundUser = foundUserBox match {
      case Full(u) => u
    }

    foundUser.provider must equal(defaultProvider)
    foundUser.idGivenByProvider must equal(user.user_name)
    foundUser.emailAddress must equal(user.email)
    foundUser.name must equal(user.user_name)
  }

  def verifyAccountCreated(account : SandboxAccountImport) = {
    val accId = AccountId(account.id)
    val bankId = BankId(account.bank)
    val foundAccountBox = Connector.connector.vend.getBankAccount(bankId, accId)
    foundAccountBox.isDefined must equal(true)

    val foundAccount = foundAccountBox match {
      case Full(a) => a
    }

    foundAccount.bankId must equal(bankId)
    foundAccount.accountId must equal(accId)
    foundAccount.label must equal(account.label)
    foundAccount.number must equal(account.number)
    foundAccount.accountType must equal(account.`type`)
    foundAccount.iban must equal(Some(account.IBAN))
    foundAccount.balance.toString must equal(account.balance.amount)
    foundAccount.currency must equal(account.balance.currency)

    foundAccount.owners.map(_.name) must equal(account.owners.toSet)

    if(account.generate_public_view) {
      foundAccount.publicViews.size must equal(1)
    } else {
      foundAccount.publicViews.size must equal(0)
    }

    val owner = Users.users.vend.getUserByProviderId(defaultProvider, foundAccount.owners.toList.head.name) match {
      case Full(o) => o
    }
    //there should be an owner view
    val views = Views.views.vend.permittedViews(owner, BankAccountUID(foundAccount.bankId, foundAccount.accountId))
    val ownerView = views.find(v => v.viewId.value == "owner")
    ownerView.isDefined must equal(true)

    //and the owners should have access to it
    Views.views.vend.getOwners(ownerView.get).map(_.idGivenByProvider) must equal(account.owners.toSet)
  }

  def verifyTransactionCreated(transaction : SandboxTransactionImport, accountsUsed : List[SandboxAccountImport]) = {
    val bankId = BankId(transaction.this_account.bank)
    val accountId = AccountId(transaction.this_account.id)
    val transactionId = TransactionId(transaction.id)
    val foundTransactionBox = Connector.connector.vend.getTransaction(bankId, accountId, transactionId)

    foundTransactionBox.isDefined must equal(true)

    val foundTransaction = foundTransactionBox match {
      case Full(t) => t
    }

    foundTransaction.id must equal(transactionId)
    foundTransaction.bankId must equal(bankId)
    foundTransaction.accountId must equal(accountId)
    foundTransaction.description must equal(Some(transaction.details.description))
    foundTransaction.balance.toString must equal(transaction.details.new_balance)
    foundTransaction.amount.toString must equal(transaction.details.value)

    def toDate(dateString : String) : Date = {
      DateParser.parse(dateString) match {
        case Full(d) => d
      }
    }

    foundTransaction.startDate.getTime must equal(toDate(transaction.details.posted).getTime)
    foundTransaction.finishDate.getTime must equal(toDate(transaction.details.completed).getTime)

    //a counterparty must exist
    val otherAcc = foundTransaction.otherAccount
    otherAcc.counterPartyId must not be empty
    otherAcc.otherAccountId must equal(accountId)
    otherAcc.otherBankId must equal(bankId)
    val otherAccMeta = otherAcc.metadata
    otherAccMeta.getPublicAlias must not be empty

    //if a counterparty was originally specified in the import data, it must correspond to that
    //counterparty
    if(transaction.counterparty.isDefined) {
      transaction.counterparty.get.name match {
        case Some(name) => otherAcc.label must equal(name)
        case None => otherAcc.label.nonEmpty must equal(true) //it must generate a counterparty label
      }

      transaction.counterparty.get.account_number match {
        case Some(number) => otherAcc.thisAccountId.value must equal(number)
        case None => otherAcc.thisAccountId.value must equal("")
      }
    }

  }

  def addField(json : JValue, fieldName : String, fieldValue : String) = {
    json.transform{
      case JObject(fields) => JObject(JField(fieldName, fieldValue) :: fields)
    }
  }

  def removeField(json : JValue, fieldName : String) = {
    json.removeField {
      case JField(`fieldName`, _) => true
      case _ => false
    }
  }

  def removeField(json : JValue, fieldSpecifier : List[String]) = {
    json.replace(fieldSpecifier, JNothing)
  }

  implicit class JValueWithSingleReplace(jValue : JValue) {
    def replace(fieldName : String, fieldValue : String) =
      jValue.replace(List(fieldName), fieldValue)
  }

  //TODO: remove this method?
  def replaceField(json : JValue, fieldName : String, fieldValue : String) =
    json.replace(List(fieldName), fieldValue)

  //TODO: remove this method?
  def replaceDisplayName(json : JValue, displayName : String) =
    replaceField(json, "display_name", displayName)

  def addIdField(json : JValue, id : String) =
    addField(json, "id", id)

  def removeIdField(json : JValue) =
    removeField(json, "id")

  def addEmailField(json : JValue, email : String) =
    addField(json, "email", email)

  def removeEmailField(json : JValue) =
    removeField(json, "email")

  val bank1 = SandboxBankImport(id = "bank1", short_name = "bank 1", full_name = "Bank 1 Inc.",
    logo = "http://example.com/logo", website = "http://example.com")
  val bank2 = SandboxBankImport(id = "bank2", short_name = "bank 2", full_name = "Bank 2 Inc.",
    logo = "http://example.com/logo2", website = "http://example.com/2")

  val standardBanks = bank1 :: bank2 :: Nil


  val standardAddress1 = SandboxAddressImport(line_1 = "5 Some Street", line_2 = "Rosy Place", line_3 = "Sunny Village",
    city = "Ashbourne", county = "Derbyshire",  state = "", post_code = "WHY RU4", country_code = "UK")

  val standardLocation1 = SandboxLocationImport(52.556198, 13.384099)

  //val license1AtBank1 = SandboxDataLicenseImport (id = "pddl", bank = bank1.id, name = "PDDL", url = "http://opendatacommons.org/licenses/pddl/")
  //val standardLicenses = license1AtBank1 :: Nil


  val standardLicense = SandboxLicenseImport  (id = "pddl", name = "Open Data Commons Public Domain Dedication and License (PDDL)")
  val standardMeta = SandboxMetaImport (license = standardLicense)

  val standardLobby = SandboxLobbyImport(hours = "M-TH 8:30-3:30, F 9-5")
  val standardDriveUp = SandboxDriveUpImport(hours = "M-Th 8:30-5:30, F-8:30-6, Sat 9-12")

  val branch1AtBank1 = SandboxBranchImport(id = "branch1", name = "Genel Müdürlük", bank_id = "bank1", address = standardAddress1
    , location = standardLocation1, meta = standardMeta, lobby = Option(standardLobby), driveUp = Option(standardDriveUp))
  val branch2AtBank1 = SandboxBranchImport(id = "branch2", name = "Manchester", bank_id = "bank1", address = standardAddress1
    , location = standardLocation1, meta = standardMeta, lobby = Option(standardLobby), driveUp = Option(standardDriveUp))

  val standardBranches = branch1AtBank1 :: branch2AtBank1 :: Nil

  val atm1AtBank1 = SandboxAtmImport(id = "atm1", name = "Ashbourne Atm 1", bank_id = "bank1", address = standardAddress1
    , location = standardLocation1, meta = standardMeta)
  val atm2AtBank1 = SandboxAtmImport(id = "atm2", name = "Manchester Atm 1", bank_id = "bank1", address = standardAddress1
    , location = standardLocation1, meta = standardMeta)

  val standardAtms = atm1AtBank1 :: atm2AtBank1 :: Nil


  val product1AtBank1 = SandboxProductImport(
    bank_id = "bank1",
    code = "prd1",
    name = "product 1",
    category = "cat1",
    family = "fam1",
    super_family = "sup fam 1",
    more_info_url = "www.example.com/index1",
    meta = standardMeta
  )

  val product2AtBank1 = SandboxProductImport(
    bank_id = "bank1",
    code = "prd2",
    name = "Product 2",
    category = "cat2",
    family = "fam2",
    super_family = "sup fam 2",
    more_info_url = "www.example.com/index2",
    meta = standardMeta
  )

  val standardProducts = product1AtBank1 :: product2AtBank1 :: Nil


  val user1 = SandboxUserImport(email = "user1@example.com", password = "TESOBE520berlin123!", user_name = "User 1")
  val user2 = SandboxUserImport(email = "user2@example.com", password = "TESOBE520berlin123!", user_name = "User 2")

  val standardUsers = user1 :: user2 :: Nil

  val account1AtBank1 = SandboxAccountImport(id = "account1", bank = "bank1", label = "Account 1 at Bank 1",
    number = "1", `type` = "savings", IBAN = "1234567890", generate_public_view = true, owners = List(user1.user_name),
    balance = SandboxBalanceImport(currency = "EUR", amount = "1000.00"), generate_accountants_view = true, generate_auditors_view = true)

  val account2AtBank1 = SandboxAccountImport(id = "account2", bank = "bank1", label = "Account 2 at Bank 1",
    number = "2", `type` = "current", IBAN = "91234567890", generate_public_view = false, owners = List(user2.user_name),
    balance = SandboxBalanceImport(currency = "EUR", amount = "2000.00"), generate_accountants_view = true, generate_auditors_view = true)

  val account1AtBank2 = SandboxAccountImport(id = "account1", bank = "bank2", label = "Account 1 at Bank 2",
    number = "22", `type` = "savings", IBAN = "21234567890", generate_public_view = false, owners = List(user1.user_name, user2.user_name),
    balance = SandboxBalanceImport(currency = "EUR", amount = "1500.00"), generate_accountants_view = true, generate_auditors_view = true)

  val standardAccounts = account1AtBank1 :: account2AtBank1 :: account1AtBank2 :: Nil

  val counterparty1 = SandboxTransactionCounterparty(name = Some("Acme Inc."), account_number = Some("12345-B"))

  val transactionWithCounterparty = SandboxTransactionImport(id = "transaction-with-counterparty",
    this_account = SandboxAccountIdImport(id = account1AtBank1.id, bank=account1AtBank1.bank),
    counterparty = Some(counterparty1),
    details = SandboxAccountDetailsImport(
      `type` = "SEPA",
      description = "some description",
      posted = "2012-03-07T00:00:00.001Z",
      completed = "2012-04-07T00:00:00.001Z",
      new_balance = "1244.00",
      value = "-135.33"
    ))

  val transactionWithoutCounterparty = SandboxTransactionImport(id = "transaction-without-counterparty",
    this_account = SandboxAccountIdImport(id = account1AtBank1.id, bank=account1AtBank1.bank),
    counterparty = None,
    details = SandboxAccountDetailsImport(
      `type` = "SEPA",
      description = "this is a description",
      posted = "2012-03-07T00:00:00.001Z",
      completed = "2012-04-07T00:00:00.001Z",
      new_balance = "1244.00",
      value = "-135.33"
    ))

  val standardTransactions = transactionWithCounterparty :: transactionWithoutCounterparty :: Nil

  val standardCustomer1 = SandboxCustomerImport("James Brown", "698761728934")


  val format = new java.text.SimpleDateFormat("dd/MM/yyyy")
  val standardDate = format.parse("30/03/2015")


  val dataImportDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")

  val standardDateString = dataImportDateFormat.format(standardDate)



  val standardCrmEvent1 = SandboxCrmEventImport("ASDFHJ47YKJH", bank1.id, standardCustomer1, "Call", "Check mortgage", "Phone", standardDateString)
  val standardCrmEvent2 = SandboxCrmEventImport("KIFJA76876AS", bank1.id, standardCustomer1, "Call", "Check mortgage", "Phone", standardDateString)

  val standardCrmEvents =  standardCrmEvent1 :: standardCrmEvent2 :: Nil




  /**
   *
   *
   * Tests below
   *
   *
   */

  "Data import" must "work in the general case" in {

    //same transaction id as another one used, but for a different bank account, so it must work
    val anotherTransaction = SandboxTransactionImport(id = transactionWithoutCounterparty.id,
      this_account = SandboxAccountIdImport(id = account1AtBank2.id, bank=account1AtBank2.bank),
      counterparty = None,
      details = SandboxAccountDetailsImport(
        `type` = "SEPA",
        description = "this is another description",
        posted = "2012-03-07T00:00:00.001Z",
        completed = "2012-04-07T00:00:00.001Z",
        new_balance = "1224.00",
        value = "-135.38"
      ))

    val blankCounterpartyNameTransaction  = SandboxTransactionImport(id = "blankCounterpartNameTransaction",
      this_account = SandboxAccountIdImport(id = account1AtBank2.id, bank=account1AtBank2.bank),
      counterparty = Some(SandboxTransactionCounterparty(None, Some("123456-AVB"))),
      details = SandboxAccountDetailsImport(
        `type` = "SEPA",
        description = "this is another description",
        posted = "2012-03-07T00:00:00.001Z",
        completed = "2012-04-07T00:00:00.001Z",
        new_balance = "1224.00",
        value = "-135.38"
      ))

    val blankCounterpartyAccountNumberTransaction  = SandboxTransactionImport(id = "blankCounterpartAccountNumberTransaction",
      this_account = SandboxAccountIdImport(id = account1AtBank2.id, bank=account1AtBank2.bank),
      counterparty = Some(SandboxTransactionCounterparty(Some("Piano Repair"), None)),
      details = SandboxAccountDetailsImport(
        `type` = "SEPA",
        description = "this is another description",
        posted = "2012-03-07T00:00:00.001Z",
        completed = "2012-04-07T00:00:00.001Z",
        new_balance = "1224.00",
        value = "-135.38"
      ))

    val banks = standardBanks
    val users = standardUsers
    val accounts = standardAccounts
    val transactions = anotherTransaction :: blankCounterpartyNameTransaction :: blankCounterpartyAccountNumberTransaction :: standardTransactions
    val branches = standardBranches
    val atms = standardAtms
    val products = standardProducts
    val crmEvents = standardCrmEvents



    val importJson = SandboxDataImport(banks, users, accounts, transactions, branches, atms, products, crmEvents)



    val response = postImportJson(write(importJson))

    response.code must equal(SUCCESS)

    banks.foreach(verifyBankCreated)
    users.foreach(verifyUserCreated)
    println("accounts: " + accounts)
    accounts.foreach(verifyAccountCreated)
    transactions.foreach(verifyTransactionCreated(_, accounts))
  }

  it must "not allow data to be imported without a secret token" in {
    val importJson = SandboxDataImport(standardBanks, standardUsers, standardAccounts, standardTransactions, standardBranches, standardAtms, standardProducts, standardCrmEvents)
    val response = postImportJson(write(importJson), None)

    response.code must equal(403)

    //nothing should be created
    Connector.connector.vend.getBanks must equal(Nil)
  }

  it must "not allow data to be imported with an invalid secret token" in {
    val importJson = SandboxDataImport(standardBanks, standardUsers, standardAccounts, standardTransactions, standardBranches, standardAtms, standardProducts, standardCrmEvents)
    val badToken = "12345"
    badToken must not equal(theImportToken)
    val response = postImportJson(write(importJson), Some(badToken))

    response.code must equal(403)

    //nothing should be created
    Connector.connector.vend.getBanks must equal(Nil)
  }

  it must "require banks to have non-empty ids" in {

    //no banks must exist initially
    Connector.connector.vend.getBanks.size must equal(0)

    val bank1Json = Extraction.decompose(bank1)

    val bankWithoutId = removeIdField(bank1Json)

    def getResponse(bankJson : JValue) = {
      val json = createImportJson(List(bankJson), Nil, Nil, Nil, Nil, Nil, Nil, Nil)
      postImportJson(json)
    }

    getResponse(bankWithoutId).code must equal(FAILED)

    //no banks should have been created
    Connector.connector.vend.getBanks.size must equal(0)

    val bankWithEmptyId = addIdField(bankWithoutId, "")
    getResponse(bankWithEmptyId).code must equal(FAILED)

    //no banks should have been created
    Connector.connector.vend.getBanks.size must equal(0)

    //Check that the same json becomes valid when a non-empty id is added
    val validId = "foo"
    val bankWithValidId = addIdField(bankWithoutId, validId)
    val response = getResponse(bankWithValidId)
    response.code must equal(SUCCESS)

    //Check the bank was created
    val banks = Connector.connector.vend.getBanks
    banks.size must equal(1)
    val createdBank  = banks(0)

    createdBank.bankId must equal(BankId(validId))
    createdBank.shortName must equal(bank1.short_name)
    createdBank.fullName must equal(bank1.full_name)
    createdBank.logoUrl must equal(bank1.logo)
    createdBank.websiteUrl must equal(bank1.website)
  }

  it must "not allow multiple banks with the same id" in {
    //no banks must exist initially
    Connector.connector.vend.getBanks.size must equal(0)

    val bank1AsJValue = Extraction.decompose(bank1)

    val baseOtherBank =
      ("short_name" -> {bank1.short_name + "2"}) ~
      ("full_name" -> {bank1.full_name + "2"}) ~
      ("logo" -> {bank1.logo + "2"}) ~
      ("website" -> {bank1.website + "2"})

    //same id as bank1, but different other attributes
    val bankWithSameId = addIdField(baseOtherBank, bank1.id)

    def getResponse(bankJsons : List[JValue]) = {
      val json = createImportJson(bankJsons, Nil, Nil, Nil, Nil, Nil, Nil, Nil)
      postImportJson(json)
    }

    getResponse(List(bank1AsJValue, bankWithSameId)).code must equal(FAILED)

    //now try again but this time with a different id
    val validOtherBank = addIdField(baseOtherBank, {bank1.id + "2"})

    getResponse(List(bank1AsJValue, validOtherBank)).code must equal(SUCCESS)

    //check that two banks were created
    val banks = Connector.connector.vend.getBanks
    banks.size must equal(2)
  }

  it must "fail if a specified bank already exists" in {
    def getResponse(bankJsons : List[JValue]) = {
      val json = createImportJson(bankJsons, Nil, Nil, Nil, Nil, Nil, Nil, Nil)
      postImportJson(json)
    }

    val bank1Json = Extraction.decompose(bank1)

    //add bank1
    getResponse(List(bank1Json)).code must equal(SUCCESS)


    val otherBank = bank2
    //when we try to add bank1 and another valid bank it should now fail
    getResponse(List(bank1Json, Extraction.decompose(bank2))).code must equal(FAILED)

    //and the other bank should not have been created
    Connector.connector.vend.getBank(BankId(otherBank.id)).isDefined must equal(false)
  }

  it must "require users to have valid emails" in {

    def getResponse(userJson : JValue) = {
      val json = createImportJson(Nil, List(userJson), Nil, Nil, Nil, Nil, Nil, Nil)
      postImportJson(json)
    }

    val user1AsJson = Extraction.decompose(user1)

    val userWithoutEmail = removeEmailField(user1AsJson)

    getResponse(userWithoutEmail).code must equal(FAILED)

    val userWithEmptyEmail = addEmailField(userWithoutEmail, "")

    //there should be no user with a blank id before we try to add one
    Users.users.vend.getUserByProviderId(defaultProvider, "") match {
      case ParamFailure(_,x,y,_) => x must equal(Empty) // Returned result in case when akka is used
      case Empty                 => Empty must equal(Empty)
      case _                     => 0 must equal (1) // Should not happen
    }

    getResponse(userWithEmptyEmail).code must equal(FAILED)

    //there must still be no user with a blank email
    Users.users.vend.getUserByProviderId(defaultProvider, "") match {
      case ParamFailure(_,x,y,_) => x must equal(Empty) // Returned result in case when akka is used
      case Empty                 => Empty must equal(Empty)
      case _                     => 0 must equal (1) // Should not happen
    }

    //invalid email must fail
    val invalidEmail = "foooo"
    val userWithInvalidEmail = addEmailField(userWithoutEmail, invalidEmail)

    getResponse(userWithInvalidEmail).code must equal(FAILED)

    //there must still be no user
    Users.users.vend.getUserByProviderId(defaultProvider, user1.user_name) match {
      case ParamFailure(_,x,y,_) => x must equal(Empty) // Returned result in case when akka is used
      case Empty                 => Empty must equal(Empty)
      case _                     => 0 must equal (1) // Should not happen
    }

    val validEmail = "test@example.com"
    val userWithValidEmail = addEmailField(userWithoutEmail, validEmail)

    getResponse(userWithValidEmail).code must equal(SUCCESS)

    //a user should now have been created
    val createdUser = Users.users.vend.getUserByProviderId(defaultProvider, user1.user_name) match {
      case Full(c) => c
      case Empty => null
    }
    createdUser must not equal(null)
    createdUser.provider must equal(defaultProvider)
    createdUser.idGivenByProvider must equal(user1.user_name)
    createdUser.name must equal(user1.user_name)

  }

  it must "not allow multiple users with the same username" in {

    def getResponse(userJsons : List[JValue]) = {
      val json = createImportJson(Nil, userJsons, Nil, Nil, Nil, Nil, Nil, Nil)
      postImportJson(json)
    }

    //emails of the user we will eventually create to show multiple users with different ids are possible
    val secondUserName = "user-two"

    val user1Json = Extraction.decompose(user1)

    val differentUsername = "user-one"
    differentUsername must not equal(user1.user_name)
    val userWithSameUsernameAsUser1 = user1Json

    //neither of the users must exist initially
    Users.users.vend.getUserByProviderId(defaultProvider, user1.user_name) match {
      case ParamFailure(_,x,y,_) => x must equal(Empty) // Returned result in case when akka is used
      case Empty                 => Empty must equal(Empty)
      case _                     => 0 must equal (1) // Should not happen
    }
    Users.users.vend.getUserByProviderId(defaultProvider, secondUserName) match {
      case ParamFailure(_,x,y,_) => x must equal(Empty) // Returned result in case when akka is used
      case Empty                 => Empty must equal(Empty)
      case _                     => 0 must equal (1) // Should not happen
    }

    getResponse(List(user1Json, userWithSameUsernameAsUser1)).code must equal(FAILED)

    //no user with firstUserId should be created
    Users.users.vend.getUserByProviderId(defaultProvider, user1.user_name) match {
      case ParamFailure(_,x,y,_) => x must equal(Empty) // Returned result in case when akka is used
      case Empty                 => Empty must equal(Empty)
      case _                     => 0 must equal (1) // Should not happen
    }

    //when we only alter the id (display name stays the same), it must work
    val userWithUsername2 = userWithSameUsernameAsUser1.replace("user_name", secondUserName)

    getResponse(List(user1Json, userWithUsername2)).code must equal(SUCCESS)

    //and both users should be created
    val firstUser = Users.users.vend.getUserByProviderId(defaultProvider, user1.user_name) match {
      case Full(fu) => fu
      case Empty => null
    }
    val secondUser = Users.users.vend.getUserByProviderId(defaultProvider, secondUserName)
     match {
      case Full(su) => su
      case Empty => null
    }

    firstUser must not equal(null)
    secondUser must not equal(null)

    firstUser.idGivenByProvider must equal(user1.user_name)
    secondUser.idGivenByProvider must equal(secondUserName)

    firstUser.name must equal(user1.user_name)
    secondUser.name must equal(secondUserName)
  }

  it must "fail if a specified user already exists" in {
    def getResponse(userJsons : List[JValue]) = {
      val json = createImportJson(Nil, userJsons, Nil, Nil, Nil, Nil, Nil, Nil)
      postImportJson(json)
    }

    val user1Json = Extraction.decompose(user1)

    //add user1
    getResponse(List(user1Json)).code must equal(SUCCESS)


    val otherUser = user2
    //when we try to add user1 and another valid new user it should now fail
    getResponse(List(user1Json, Extraction.decompose(otherUser))).code must equal(FAILED)

    //and the other user should not have been created
    Users.users.vend.getUserByProviderId(defaultProvider, otherUser.user_name)
  }

  it must "fail if a user's password is missing or empty" in {
    def getResponse(userJsons : List[JValue]) = {
      val json = createImportJson(Nil, userJsons, Nil, Nil, Nil, Nil, Nil, Nil)
      postImportJson(json)
    }

    val goodUser = Extraction.decompose(user1)

    val userWithoutPassword = removeField(goodUser, "password")
    getResponse(List(userWithoutPassword)).code must equal(FAILED)
    //no user should be created
    Users.users.vend.getUserByProviderId(defaultProvider, user1.user_name).isDefined must equal(false)

    val userWithBlankPassword = replaceField(goodUser, "password", "")
    getResponse(List(userWithBlankPassword)).code must equal(FAILED)
    //no user should be created
    Users.users.vend.getUserByProviderId(defaultProvider, user1.user_name).isDefined must equal(false)

    //check that a normal password is okay
    getResponse(List(goodUser)).code must equal(SUCCESS)
    Users.users.vend.getUserByProviderId(defaultProvider, user1.user_name).isDefined must equal(true)
  }

  it must "set user passwords properly" in {
    def getResponse(userJsons : List[JValue]) = {
      val json = createImportJson(Nil, userJsons, Nil, Nil, Nil, Nil, Nil, Nil)
      postImportJson(json)
    }

    getResponse(List(Extraction.decompose(user1))).code must equal(SUCCESS)

    //TODO: we shouldn't reference AuthUser here as it is an implementation, but for now there
    //is no way to check User (the trait) passwords
    val createdAuthUserBox = AuthUser.find(By(AuthUser.username, user1.user_name))
    createdAuthUserBox.isDefined must equal(true)

    val createdAuthUser = createdAuthUserBox match {
      case Full(cu) => cu
    }
    createdAuthUser.password.match_?(user1.password) must equal(true)
  }

  it must "require accounts to have non-empty ids" in {

    def getResponse(accountJsons : List[JValue]) = {
      val banks = standardBanks.map(Extraction.decompose)
      val users = standardUsers.map(Extraction.decompose)
      val json = createImportJson(banks, users, accountJsons, Nil, Nil, Nil, Nil, Nil)
      postImportJson(json)
    }

    val acc1AtBank1Json = Extraction.decompose(account1AtBank1)
    val accountWithoutId = removeIdField(acc1AtBank1Json)

    getResponse(List(accountWithoutId)).code must equal(FAILED)

    val accountWithEmptyId = addIdField(accountWithoutId, "")

    getResponse(List(accountWithEmptyId)).code must equal(FAILED)

    //no account must exist with an empty id
    Connector.connector.vend.getBankAccount(BankId(account1AtBank1.bank), AccountId("")).isDefined must equal(false)

    getResponse(List(acc1AtBank1Json)).code must equal(SUCCESS)

    //an account should now exist
    verifyAccountCreated(account1AtBank1)
  }

  it must "not allow multiple accounts at the same bank with the same id" in {

    def getResponse(accountJsons : List[JValue]) = {
      val banks = standardBanks.map(Extraction.decompose)
      val users = standardUsers.map(Extraction.decompose)
      val json = createImportJson(banks, users, accountJsons, Nil, Nil, Nil, Nil, Nil)
      postImportJson(json)
    }

    val account1AtBank1Json = Extraction.decompose(account1AtBank1)
    val accountWithSameId = replaceField(Extraction.decompose(account2AtBank1), "id", account1AtBank1.id)
    //might be nice to test a case where the only similar attribute between the accounts is the id
    getResponse(List(account1AtBank1Json, accountWithSameId)).code must equal(FAILED)

    //no accounts should have been created
    Connector.connector.vend.getBankAccount(BankId(account1AtBank1.bank), AccountId(account1AtBank1.id)).isDefined must equal(false)

    val accountIdTwo = "2"
    accountIdTwo must not equal(account1AtBank1.id)

    val accountWithDifferentId = replaceField(accountWithSameId, "id", accountIdTwo)

    getResponse(List(account1AtBank1Json, accountWithDifferentId)).code must equal(SUCCESS)

    //two accounts should have been created
    Connector.connector.vend.getBankAccount(BankId(account1AtBank1.bank), AccountId(account1AtBank1.id)).isDefined must equal(true)
    Connector.connector.vend.getBankAccount(BankId(account1AtBank1.bank), AccountId(accountIdTwo)).isDefined must equal(true)

  }

  it must "fail if a specified account already exists" in {
    def getResponse(accountJsons : List[JValue]) = {
      val banks = standardBanks.map(Extraction.decompose)
      val users = standardUsers.map(Extraction.decompose)
      val json = createImportJson(banks, users, accountJsons, Nil, Nil, Nil, Nil, Nil)
      postImportJson(json)
    }
    val account1AtBank1Json = Extraction.decompose(account1AtBank1)

    //add account1AtBank1
    getResponse(List(account1AtBank1Json)).code must equal(SUCCESS)

    val otherAccount = account1AtBank2
    //when we try to add account1AtBank1 and another valid account it should now fail
    getResponse(List(account1AtBank1Json, Extraction.decompose(otherAccount))).code must equal(FAILED)

    //and the other account should not have been created
    Connector.connector.vend.getBankAccount(BankId(otherAccount.bank), AccountId(otherAccount.id)).isDefined must equal(false)
  }

  it must "not allow an account to have a bankId not specified in the imported banks" in {

    val badBankId = "asdf"

    def getResponse(accountJsons : List[JValue]) = {
      standardBanks.exists(b => b.id == badBankId) must equal(false)
      val banks = standardBanks.map(Extraction.decompose)

      val users = standardUsers.map(Extraction.decompose)
      val json = createImportJson(banks, users, accountJsons, Nil, Nil, Nil, Nil, Nil)
      postImportJson(json)
    }

    val badBankAccount = replaceField(Extraction.decompose(account1AtBank1), "bank", badBankId)

    getResponse(List(badBankAccount)).code must equal(FAILED)

    //no account should have been created
    Connector.connector.vend.getBankAccount(BankId(badBankId), AccountId(account1AtBank1.id)).isDefined must equal(false)
  }

  it must "not allow an account to be created without an owner" in {
    def getResponse(accountJsons : List[JValue]) = {
      val banks = standardBanks.map(Extraction.decompose)
      val users = standardUsers.map(Extraction.decompose)

      val json = createImportJson(banks, users, accountJsons, Nil, Nil, Nil, Nil, Nil)
      postImportJson(json)
    }

    val acc1AtBank1Json = Extraction.decompose(account1AtBank1)

    val accountWithNoOwnerField = removeField(acc1AtBank1Json, "owners")

    getResponse(List(accountWithNoOwnerField)).code must equal(FAILED)

    val accountWithNilOwners = Extraction.decompose(account1AtBank1.copy(owners = Nil))

    getResponse(List(accountWithNilOwners)).code must equal(FAILED)
  }

  it must "not allow an account to be created with an owner not specified in data import users" in {

    val users = standardUsers
    val banks = standardBanks

    def getResponse(accountJsons : List[JValue]) = {
      val json = createImportJson(banks.map(Extraction.decompose), users.map(Extraction.decompose), accountJsons, Nil, Nil, Nil, Nil, Nil)
      postImportJson(json)
    }

    val nonExistentOwnerEmail = "asdfasdfasdf@example.com"
    users.exists(u => u.email == nonExistentOwnerEmail) must equal(false)

    val accountWithInvalidOwner = account1AtBank1.copy(owners = List(nonExistentOwnerEmail))

    getResponse(List(Extraction.decompose(accountWithInvalidOwner))).code must equal(FAILED)

    //it should not have been created
    Connector.connector.vend.getBankAccount(BankId(accountWithInvalidOwner.bank), AccountId(accountWithInvalidOwner.id)).isDefined must equal(false)

    //a mix of valid an invalid owners must also not work
    val accountWithSomeValidSomeInvalidOwners = accountWithInvalidOwner.copy(owners = List(accountWithInvalidOwner.owners + user1.user_name))
    getResponse(List(Extraction.decompose(accountWithSomeValidSomeInvalidOwners))).code must equal(FAILED)

    //it should not have been created
    Connector.connector.vend.getBankAccount(BankId(accountWithSomeValidSomeInvalidOwners.bank), AccountId(accountWithSomeValidSomeInvalidOwners.id)).isDefined must equal(false)

  }

  it must "not allow multiple accounts at the same bank with the same account number" in {
    val users = standardUsers
    val banks = standardBanks

    def getResponse(accountJsons : List[JValue]) = {
      val json = createImportJson(banks.map(Extraction.decompose), users.map(Extraction.decompose), accountJsons, Nil, Nil, Nil, Nil, Nil)
      postImportJson(json)
    }

    val acc1 = account1AtBank1
    val acc2 = account2AtBank1

    val acc1Json = Extraction.decompose(acc1)
    val acc2Json = Extraction.decompose(acc2)
    val sameNumberJson = replaceField(acc2Json, "number", acc1.number)

    getResponse(List(acc1Json, sameNumberJson)).code must equal(FAILED)

    //no accounts should have been created
    Connector.connector.vend.getBankAccount(BankId(acc1.bank), AccountId(acc1.id)).isDefined must equal(false)
    Connector.connector.vend.getBankAccount(BankId(acc1.bank), AccountId(acc2.id)).isDefined must equal(false)

    //check it works with the normal different number
    getResponse(List(acc1Json, acc2Json)).code must equal(SUCCESS)

    //and the accounts should be created
    Connector.connector.vend.getBankAccount(BankId(acc1.bank), AccountId(acc1.id)).isDefined must equal(true)
    Connector.connector.vend.getBankAccount(BankId(acc1.bank), AccountId(acc2.id)).isDefined must equal(true)
  }

  it must "require transactions to have non-empty ids" in {

    def getResponse(transactionJsons : List[JValue]) = {
      val json = createImportJson(standardBanks.map(Extraction.decompose),
        standardUsers.map(Extraction.decompose), standardAccounts.map(Extraction.decompose), transactionJsons, Nil, Nil, Nil, Nil)
      postImportJson(json)
    }

    def transactionExists() : Boolean = {
      Connector.connector.vend.getTransaction(BankId(transactionWithoutCounterparty.this_account.bank),
        AccountId(transactionWithoutCounterparty.this_account.id),
        TransactionId(transactionWithoutCounterparty.id)).isDefined
    }

    val transactionJson = Extraction.decompose(transactionWithoutCounterparty)

    val missingIdTransaction = removeIdField(transactionJson)
    getResponse(List(missingIdTransaction)).code must equal(FAILED)
    transactionExists() must equal(false)

    val emptyIdTransaction = replaceField(transactionJson, "id", "")
    getResponse(List(emptyIdTransaction)).code must equal(FAILED)
    transactionExists() must equal(false)

    //the original transaction must work too (just to make sure it's not failing because we have, e.g. a bank id that doesn't exist)
    getResponse(List(transactionJson)).code must equal(SUCCESS)

    //it must exist now
    transactionExists() must equal(true)
  }

  it must "require transactions for a single account do not have the same id" in {

    def getResponse(transactionJsons : List[JValue]) = {
      val json = createImportJson(standardBanks.map(Extraction.decompose),
        standardUsers.map(Extraction.decompose), standardAccounts.map(Extraction.decompose), transactionJsons, Nil, Nil, Nil, Nil)
      postImportJson(json)
    }

    val t1 = transactionWithoutCounterparty
    val t2 = transactionWithCounterparty

    //make sure the two transactions are for the same account have different ids
    t1.this_account.bank must equal(t2.this_account.bank)
    t1.this_account.id must equal(t2.this_account.id)
    t1.id must not equal(t2.id)

    val transactionJson = Extraction.decompose(t1)
    val transaction2Json = Extraction.decompose(t2)

    //now edit the second transaction to give it the same id as the first one
    val sameIdAsOtherTransaction = replaceField(transaction2Json, "id", t1.id)

    getResponse(List(transactionJson, sameIdAsOtherTransaction)).code must equal(FAILED)

    //Neither must exist
    Connector.connector.vend.getTransaction(BankId(t1.this_account.bank),
      AccountId(t1.this_account.id),
      TransactionId(t1.id)).isDefined must equal(false)

    //now make sure it's not failing because we have, e.g. a bank id that doesn't exist by checking the originals worked
    getResponse(List(transactionJson, transaction2Json)).code must equal(SUCCESS)

    //both must exist now
    Connector.connector.vend.getTransaction(BankId(t1.this_account.bank),
      AccountId(t1.this_account.id),
      TransactionId(t1.id)).isDefined must equal(true)

    Connector.connector.vend.getTransaction(BankId(t2.this_account.bank),
      AccountId(t2.this_account.id),
      TransactionId(t2.id)).isDefined must equal(true)
  }

  it must "fail if a specified transaction already exists" in {
    def getResponse(transactionJsons : List[JValue]) = {
      val json = createImportJson(standardBanks.map(Extraction.decompose),
        standardUsers.map(Extraction.decompose), standardAccounts.map(Extraction.decompose), transactionJsons, Nil, Nil, Nil, Nil)
      postImportJson(json)
    }

    val t1Json = Extraction.decompose(transactionWithoutCounterparty)

    //add transaction
    getResponse(List(t1Json)).code must equal(SUCCESS)

    val otherTransaction = transactionWithCounterparty

    //when we try to add t1Json and another valid transaction it should now fail
    getResponse(List(t1Json, Extraction.decompose(otherTransaction))).code must equal(FAILED)

    //and no new transaction must exist
    Connector.connector.vend.getTransaction(BankId(otherTransaction.this_account.bank),
      AccountId(otherTransaction.this_account.id),
      TransactionId(otherTransaction.id)).isDefined must equal(false)
  }

  it must "not create any transactions when one has an invalid this_account" in {
    val banks = standardBanks
    val users = standardUsers
    val accounts = standardAccounts

    def getResponse(transactionJsons : List[JValue]) = {
      val json = createImportJson(banks.map(Extraction.decompose),
        users.map(Extraction.decompose), accounts.map(Extraction.decompose), transactionJsons, Nil, Nil, Nil, Nil)
      postImportJson(json)
    }

    val t = transactionWithCounterparty

    val validTransaction = Extraction.decompose(t)

    //ensure bank is correct
    banks.exists(b => b.id == t.this_account.bank) must equal(true)

    val invalidAccountId = "asdfasdfasdf"
    //ensure account id is invalid
    accounts.exists(a => a.bank == t.this_account.bank && a.id == invalidAccountId) must equal(false)

    //check one where the bank id exists, but the account id doesn't
    val invalidAccTransaction = validTransaction.replace(List("this_account","id"), invalidAccountId)

    getResponse(List(invalidAccTransaction)).code must equal(FAILED)

    //transaction must not exist
    Connector.connector.vend.getTransaction(BankId(t.this_account.bank),
      AccountId(invalidAccountId),
      TransactionId(t.id)).isDefined must equal(false)

    //now check one where the bankId is invalid

    val invalidBankId = "omommomom"
    //ensure bank is invalid
    banks.exists(b => b.id == invalidBankId) must equal(false)

    val invalidBankTransaction = validTransaction.replace(List("this_account", "bank"), invalidBankId)

    getResponse(List(invalidBankTransaction)).code must equal(FAILED)

    //transaction must not exist
    Connector.connector.vend.getTransaction(BankId(invalidBankId),
      AccountId(t.this_account.id),
      TransactionId(t.id)).isDefined must equal(false)

    //now make sure it works when all is well
    getResponse(List(validTransaction)).code must equal(SUCCESS)

    //transaction must exist
    Connector.connector.vend.getTransaction(BankId(t.this_account.bank),
      AccountId(t.this_account.id),
      TransactionId(t.id)).isDefined must equal(true)

  }

  it must "allow counterparty name to be empty" in {
    val (banks, users, accounts) = (standardBanks, standardUsers, standardAccounts)

    def getResponse(transactionJsons : List[JValue]) = {
      val json = createImportJson(banks.map(Extraction.decompose),
        users.map(Extraction.decompose), accounts.map(Extraction.decompose), transactionJsons, Nil, Nil, Nil, Nil)
      postImportJson(json)
    }

    val t = transactionWithCounterparty
    val baseT = Extraction.decompose(t)
    val emptyCounterpartyNameTransaction = baseT.replace(List("counterparty", "name"), "")

    getResponse(List(emptyCounterpartyNameTransaction)).code must equal(SUCCESS)

    //check it was created, name is generated, and account number matches
    val createdTransaction = Connector.connector.vend.getTransaction(BankId(t.this_account.bank),
      AccountId(t.this_account.id),
      TransactionId(t.id))

    createdTransaction.isDefined must equal(true)
    val created = createdTransaction match {
      case Full(c) => c
    }

    created.otherAccount.label.nonEmpty must equal(true)
    created.otherAccount.thisAccountId.value must equal(t.counterparty.get.account_number.get)

  }

  it must "allow counterparty name to be unspecified" in {
    val (banks, users, accounts) = (standardBanks, standardUsers, standardAccounts)

    def getResponse(transactionJsons : List[JValue]) = {
      val json = createImportJson(banks.map(Extraction.decompose),
        users.map(Extraction.decompose), accounts.map(Extraction.decompose), transactionJsons, Nil, Nil, Nil, Nil)
      postImportJson(json)
    }

    val t = transactionWithCounterparty
    val baseT = Extraction.decompose(t)
    val missingCounterpartNameTransaction = removeField(baseT, List("counterparty", "name"))

    getResponse(List(missingCounterpartNameTransaction)).code must equal(SUCCESS)

    //check it was created, name is generated, and account number matches
    val createdTransaction = Connector.connector.vend.getTransaction(BankId(t.this_account.bank),
      AccountId(t.this_account.id),
      TransactionId(t.id))

    createdTransaction.isDefined must equal(true)
    val created = createdTransaction match {
      case Full(c) => c
    }

    created.otherAccount.label.nonEmpty must equal(true)
    created.otherAccount.thisAccountId.value must equal(t.counterparty.get.account_number.get)

  }

  it must "allow counterparty account number to be empty" in {
    val (banks, users, accounts) = (standardBanks, standardUsers, standardAccounts)

    def getResponse(transactionJsons : List[JValue]) = {
      val json = createImportJson(banks.map(Extraction.decompose),
        users.map(Extraction.decompose), accounts.map(Extraction.decompose), transactionJsons, Nil, Nil, Nil, Nil)
      postImportJson(json)
    }

    val t = transactionWithCounterparty
    val baseT = Extraction.decompose(t)
    val emptyCounterpartyAccountNumberTransaction = baseT.replace(List("counterparty", "account_number"), "")

    getResponse(List(emptyCounterpartyAccountNumberTransaction)).code must equal(SUCCESS)

    //check it was created, name matches, and account number is empty
    val createdTransaction = Connector.connector.vend.getTransaction(BankId(t.this_account.bank),
      AccountId(t.this_account.id),
      TransactionId(t.id))

    createdTransaction.isDefined must equal(true)
    val created = createdTransaction match {
      case Full(c) => c
    }

    created.otherAccount.label must equal(t.counterparty.get.name.get)
    created.otherAccount.thisAccountId.value must equal("")
  }

  it must "allow counterparty account number to be unspecified" in {
    val (banks, users, accounts) = (standardBanks, standardUsers, standardAccounts)

    def getResponse(transactionJsons : List[JValue]) = {
      val json = createImportJson(banks.map(Extraction.decompose),
        users.map(Extraction.decompose), accounts.map(Extraction.decompose), transactionJsons, Nil, Nil, Nil, Nil)
      postImportJson(json)
    }

    val t = transactionWithCounterparty
    val baseT = Extraction.decompose(t)
    val missingCounterpartyAccountNumberTransaction = removeField(baseT, List("counterparty", "account_number"))

    getResponse(List(missingCounterpartyAccountNumberTransaction)).code must equal(SUCCESS)

    //check it was created, name matches, and account number is empty
    val createdTransaction = Connector.connector.vend.getTransaction(BankId(t.this_account.bank),
      AccountId(t.this_account.id),
      TransactionId(t.id))

    createdTransaction.isDefined must equal(true)
    val created = createdTransaction match {
      case Full(c) => c
    }

    created.otherAccount.label must equal(t.counterparty.get.name.get)
    created.otherAccount.thisAccountId.value must equal("")
  }

  it must "allow counterparties with the same name to have different account numbers" in {
    val (banks, users, accounts) = (standardBanks, standardUsers, standardAccounts)

    def getResponse(transactionJsons : List[JValue]) = {
      val json = createImportJson(banks.map(Extraction.decompose),
        users.map(Extraction.decompose), accounts.map(Extraction.decompose), transactionJsons, Nil, Nil, Nil, Nil)
      postImportJson(json)
    }

    val t1 = Extraction.decompose(transactionWithCounterparty)
    val t1Id = transactionWithCounterparty.id
    val t2Id = "t2Id"
    t2Id must not equal(t1Id)
    val c1 = transactionWithCounterparty.counterparty.get
    val counterparty2AccountNumber = c1.account_number.get + "2"

    val badT2 = t1.replace("id", t2Id).replace(List("counterparty", "account_number"), counterparty2AccountNumber)

    getResponse(List(t1, badT2)).code must equal(SUCCESS)

    val bankId = BankId(transactionWithCounterparty.this_account.bank)
    val accountId = AccountId(transactionWithCounterparty.this_account.id)

    def checkTransactionsCreated(created : Boolean) = {
      val foundTransaction1Box = Connector.connector.vend.getTransaction(bankId, accountId, TransactionId(t1Id))
      val foundTransaction2Box = Connector.connector.vend.getTransaction(bankId, accountId, TransactionId(t2Id))

      foundTransaction1Box.isDefined must equal(created)
      foundTransaction2Box.isDefined must equal(created)
    }

    checkTransactionsCreated(true)
  }

  it must "have transactions share counterparties if they are the same" in {
    val (banks, users, accounts) = (standardBanks, standardUsers, standardAccounts)

    def getResponse(transactionJsons : List[JValue]) = {
      val json = createImportJson(banks.map(Extraction.decompose),
        users.map(Extraction.decompose), accounts.map(Extraction.decompose), transactionJsons, Nil, Nil, Nil, Nil)
      postImportJson(json)
    }

    val t1 = Extraction.decompose(transactionWithCounterparty)
    val t1Id = transactionWithCounterparty.id
    val t2Id = "t2Id"
    t2Id must not equal(t1Id)
    val t2 = replaceField(t1, "id", t2Id)

    val bankId = BankId(transactionWithCounterparty.this_account.bank)
    val accountId = AccountId(transactionWithCounterparty.this_account.id)

    getResponse(t1 :: t2 :: Nil).code must equal(SUCCESS)

    val foundTransaction1Box = Connector.connector.vend.getTransaction(bankId, accountId, TransactionId(t1Id)) match {
      case Full(tb) => tb
      case Empty => null
    }
    val foundTransaction2Box = Connector.connector.vend.getTransaction(bankId, accountId, TransactionId(t2Id)) match {
      case Full(t2b) => t2b
      case Empty => null
    }

    foundTransaction1Box must not equal(null)
    foundTransaction2Box must not equal(null)

    val counter1 = foundTransaction1Box.otherAccount
    val counter2 = foundTransaction2Box.otherAccount

    counter1.counterPartyId must equal(counter2.counterPartyId)
    counter1.metadata.getPublicAlias must equal(counter2.metadata.getPublicAlias)
  }

  it must "consider counterparties with the same name but different account numbers to be different" in {
    val (banks, users, accounts) = (standardBanks, standardUsers, standardAccounts)

    def getResponse(transactionJsons : List[JValue]) = {
      val json = createImportJson(banks.map(Extraction.decompose),
        users.map(Extraction.decompose), accounts.map(Extraction.decompose), transactionJsons, Nil, Nil, Nil, Nil)
      postImportJson(json)
    }

    val baseT = Extraction.decompose(transactionWithCounterparty)
    val counterAcc1 = "1"
    val counterAcc2 = "2"
    val id1 = "id1"
    val id2 = "id2"
    val t1 = baseT.replace(List("counterparty", "account_number"), counterAcc1).replace(List("id"), id1)
    val t2 = baseT.replace(List("counterparty", "account_number"), counterAcc2).replace(List("id"), id2)

    getResponse(t1 :: t2 :: Nil).code must equal(SUCCESS)

    val bankId = BankId(transactionWithCounterparty.this_account.bank)
    val accountId = AccountId(transactionWithCounterparty.this_account.id)
    val foundTransaction1Box = Connector.connector.vend.getTransaction(bankId, accountId, TransactionId(id1)) match {
      case Full(tb) => tb
      case Empty => null
    }
    val foundTransaction2Box = Connector.connector.vend.getTransaction(bankId, accountId, TransactionId(id2)) match {
      case Full(t2b) => t2b
      case Empty => null
    }

    foundTransaction1Box must not equal(null)
    foundTransaction2Box must not equal(null)

    val counter1 = foundTransaction1Box.otherAccount
    val counter2 = foundTransaction2Box.otherAccount

    counter1.counterPartyId must not equal(counter2.counterPartyId)
    counter1.metadata.getPublicAlias must not equal(counter2.metadata.getPublicAlias)
    counter1.thisAccountId.value must equal(counterAcc1)
    counter2.thisAccountId.value must equal(counterAcc2)
  }

  it must "consider counterparties without names but with the same account numbers to be the same" in {
    val (banks, users, accounts) = (standardBanks, standardUsers, standardAccounts)

    def getResponse(transactionJsons : List[JValue]) = {
      val json = createImportJson(banks.map(Extraction.decompose),
        users.map(Extraction.decompose), accounts.map(Extraction.decompose), transactionJsons, Nil, Nil, Nil, Nil)
      postImportJson(json)
    }

    val baseT = Extraction.decompose(transactionWithCounterparty)
    val t1 = removeField(baseT, List("counterparty", "name"))
    val id1 = transactionWithCounterparty.id
    val id2 = id1 + "--2"
    val t2 = removeField(t1.replace("id", id2), List("counterparty", "name"))

    getResponse(t1 :: t2 :: Nil).code must equal(SUCCESS)

    val bankId = BankId(transactionWithCounterparty.this_account.bank)
    val accountId = AccountId(transactionWithCounterparty.this_account.id)
    val foundTransaction1Box = Connector.connector.vend.getTransaction(bankId, accountId, TransactionId(id1)) match {
      case Full(tb) => tb
      case Empty => null
    }
    val foundTransaction2Box = Connector.connector.vend.getTransaction(bankId, accountId, TransactionId(id2)) match {
      case Full(t2b) => t2b
      case Empty => null
    }

    foundTransaction1Box must not equal(null)
    foundTransaction2Box must not equal(null)

    val counter1 = foundTransaction1Box.otherAccount
    val counter2 = foundTransaction2Box.otherAccount

    counter1.counterPartyId must equal(counter2.counterPartyId)
    counter1.metadata.getPublicAlias must equal(counter2.metadata.getPublicAlias)
    counter1.thisAccountId.value must equal(transactionWithCounterparty.counterparty.get.account_number.get)
    counter2.thisAccountId.value must equal(transactionWithCounterparty.counterparty.get.account_number.get)
  }

  it must "consider counterparties without names but with different account numbers to be different" in {
    val (banks, users, accounts) = (standardBanks, standardUsers, standardAccounts)

    def getResponse(transactionJsons : List[JValue]) = {
      val json = createImportJson(banks.map(Extraction.decompose),
        users.map(Extraction.decompose), accounts.map(Extraction.decompose), transactionJsons, Nil, Nil, Nil, Nil)
      postImportJson(json)
    }

    val id1 = "id1"
    val id2 = "id2"
    val counterpartyAccNumber1 = "1"
    val counterpartyAccNumber2 = "2"

    val baseT = transactionWithoutCounterparty
    val baseTransaction = Extraction.decompose(baseT)

    val t1 = baseTransaction.replace(List("id"), id1).replace(List("counterparty"), ("account_number" -> counterpartyAccNumber1))
    val t2 = baseTransaction.replace(List("id"), id2).replace(List("counterparty"), ("account_number" -> counterpartyAccNumber2))

    getResponse(List(t1, t2)).code must equal(SUCCESS)

    val bankId = BankId(baseT.this_account.bank)
    val accountId = AccountId(baseT.this_account.id)
    val foundTransaction1Box = Connector.connector.vend.getTransaction(bankId, accountId, TransactionId(id1)) match {
      case Full(tb) => tb
    }
    val foundTransaction2Box = Connector.connector.vend.getTransaction(bankId, accountId, TransactionId(id2)) match {
      case Full(t2b) => t2b
      case Empty => null
    }

    foundTransaction1Box must not equal(null)
    foundTransaction2Box must not equal(null)

    val counter1 = foundTransaction1Box.otherAccount
    val counter2 = foundTransaction2Box.otherAccount

    //transactions should have the same counterparty
    counter1.counterPartyId must not equal(counter2.counterPartyId)
    counter1.counterPartyId.isEmpty must equal(false)
    counter2.counterPartyId.isEmpty must equal(false)
    counter1.metadata.getPublicAlias must not equal(counter2.metadata.getPublicAlias)
    counter1.thisAccountId.value must equal(counterpartyAccNumber1)
    counter2.thisAccountId.value must equal(counterpartyAccNumber2)
  }

  it must "always create a new counterparty if none was specified, rather than having all transactions without specified" +
    "counterparties share a single one" in {

    val (banks, users, accounts) = (standardBanks, standardUsers, standardAccounts)

    def getResponse(transactionJsons : List[JValue]) = {
      val json = createImportJson(banks.map(Extraction.decompose),
        users.map(Extraction.decompose), accounts.map(Extraction.decompose), transactionJsons, Nil, Nil, Nil, Nil)
      postImportJson(json)
    }

    val id2 = "id2"
    id2 must not equal(transactionWithoutCounterparty.id)
    val anotherTransactionWithoutCounterparty = replaceField(Extraction.decompose(transactionWithoutCounterparty), "id", id2)

    val id3 = transactionWithCounterparty.id + "id3"
    val transactionWithBlankCounterparty = replaceField(Extraction.decompose(transactionWithoutCounterparty), "id", id3).replace(List("counterparty"), JNothing)

    val response = getResponse(anotherTransactionWithoutCounterparty :: transactionWithBlankCounterparty :: Extraction.decompose(transactionWithoutCounterparty) :: Nil)
    response.code must equal(SUCCESS)

    val accountId = AccountId(transactionWithoutCounterparty.this_account.id)
    val bankId = BankId(transactionWithoutCounterparty.this_account.bank)
    val tId1 = TransactionId(transactionWithoutCounterparty.id)
    val tId2 = TransactionId(id2)
    val tId3 = TransactionId(id3)

    val foundTransaction1Box = Connector.connector.vend.getTransaction(bankId, accountId, tId1) match {
      case Full(tb) => tb
      case Empty => null
    }
    val foundTransaction2Box = Connector.connector.vend.getTransaction(bankId, accountId, tId2) match {
      case Full(t2b) => t2b
      case Empty => null
    }
    val foundTransaction3Box = Connector.connector.vend.getTransaction(bankId, accountId, tId3) match {
      case Full(t3b) => t3b
      case Empty => null
    }

    foundTransaction1Box must not equal(null)
    foundTransaction2Box must not equal(null)
    foundTransaction3Box must not equal(null)

    val counter1 = foundTransaction1Box.otherAccount
    val counter2 = foundTransaction2Box.otherAccount
    val counter3 = foundTransaction3Box.otherAccount

    counter1.counterPartyId must not equal(counter2.counterPartyId)
    counter1.counterPartyId must not equal(counter3.counterPartyId)
    counter2.counterPartyId must not equal(counter3.counterPartyId)
    counter1.metadata.getPublicAlias must not equal(counter2.metadata.getPublicAlias)
    counter1.metadata.getPublicAlias must not equal(counter3.metadata.getPublicAlias)
    counter2.metadata.getPublicAlias must not equal(counter3.metadata.getPublicAlias)
  }

  it must "not create any transactions when one has an invalid or missing value" in {
    val (banks, users, accounts) = (standardBanks, standardUsers, standardAccounts)

    def getResponse(transactionJsons : List[JValue]) = {
      val json = createImportJson(banks.map(Extraction.decompose),
        users.map(Extraction.decompose), accounts.map(Extraction.decompose), transactionJsons, Nil, Nil, Nil, Nil)
      postImportJson(json)
    }

    val t = transactionWithCounterparty

    val validTransaction = Extraction.decompose(t)

    val newTransId = "0239403294322343"
    newTransId must not equal(t.id)

    val baseNewTransaction = replaceField(validTransaction, "id", newTransId)

    val transactionWithoutValue = removeField(baseNewTransaction, List("details", "value"))

    //shouldn't work
    getResponse(List(validTransaction, transactionWithoutValue)).code must equal(FAILED)

    def checkNoTransactionsExist() = checkTransactions(false)
    def checkTransactionsExist() = checkTransactions(true)

    def checkTransactions(exist: Boolean) = {
      Connector.connector.vend.getTransaction(BankId(t.this_account.bank),
        AccountId(t.this_account.id),
        TransactionId(t.id)).isDefined must equal(exist)

      Connector.connector.vend.getTransaction(BankId(t.this_account.bank),
        AccountId(t.this_account.id),
        TransactionId(newTransId)).isDefined must equal(exist)
    }

    //no transactions should be created
    checkNoTransactionsExist()

    //check transaction with bad value
    val transactionWithBadValue = baseNewTransaction.replace(List("details", "value"), "ABCD")

    //shouldn't work
    getResponse(List(validTransaction, transactionWithBadValue)).code must equal(FAILED)
    checkNoTransactionsExist()

    //now make sure it works with a good value
    val transactionWithGoodValue = baseNewTransaction.replace(List("details", "value"), "-34.65")

    getResponse(List(validTransaction, transactionWithGoodValue)).code must equal(SUCCESS)
    checkTransactionsExist()
  }

  it must "not create any transactions when one has an invalid or missing completed date" in {
    val (banks, users, accounts) = (standardBanks, standardUsers, standardAccounts)

    def getResponse(transactionJsons : List[JValue]) = {
      val json = createImportJson(banks.map(Extraction.decompose),
        users.map(Extraction.decompose), accounts.map(Extraction.decompose), transactionJsons, Nil, Nil, Nil, Nil)
      postImportJson(json)
    }

    val t = transactionWithCounterparty

    val validTransaction = Extraction.decompose(t)

    val newTransId = "0239403294322343"
    newTransId must not equal(t.id)

    def checkNoTransactionsExist() = checkTransactions(false)
    def checkTransactionsExist() = checkTransactions(true)

    def checkTransactions(exist: Boolean) = {
      Connector.connector.vend.getTransaction(BankId(t.this_account.bank),
        AccountId(t.this_account.id),
        TransactionId(t.id)).isDefined must equal(exist)

      Connector.connector.vend.getTransaction(BankId(t.this_account.bank),
        AccountId(t.this_account.id),
        TransactionId(newTransId)).isDefined must equal(exist)
    }

    val baseNewTransaction = validTransaction.replace("id", newTransId)

    val transactionWithMissingCompleted = removeField(baseNewTransaction, List("details", "completed"))

    //shouldn't work
    getResponse(List(validTransaction, transactionWithMissingCompleted)).code must equal(FAILED)
    checkNoTransactionsExist()

    //check transaction with bad completed date
    val transactionWithBadCompleted = baseNewTransaction.replace(List("details", "completed"), "ASDF")

    //shouldn't work
    getResponse(List(validTransaction, transactionWithBadCompleted)).code must equal(FAILED)
    checkNoTransactionsExist()

    //now make sure it works with a valid completed date
    val transactionWithGoodcompleted = baseNewTransaction.replace(List("details", "completed"), "2016-11-07T05:25:33.001Z")

    //should work
    getResponse(List(validTransaction, transactionWithGoodcompleted)).code must equal(SUCCESS)
    checkTransactionsExist()
  }

  it must "check that counterparty specified is not generated if it already exists (for the original account in question)" in {
    val (banks, users, accounts) = (standardBanks, standardUsers, standardAccounts)

    def getResponse(transactionJsons : List[JValue]) = {
      val json = createImportJson(banks.map(Extraction.decompose),
        users.map(Extraction.decompose), accounts.map(Extraction.decompose), transactionJsons, Nil, Nil, Nil, Nil)
      postImportJson(json)
    }

    val t = transactionWithCounterparty

    val validTransaction = Extraction.decompose(t)

    val newTransId = "0239403294322343"
    newTransId must not equal(t.id)

    val transactionWithSameCounterparty = replaceField(validTransaction, "id", newTransId)

    getResponse(List(validTransaction, transactionWithSameCounterparty)).code must equal(SUCCESS)

    def getCreatedTransaction(id : String) =
      Connector.connector.vend.getTransaction(BankId(t.this_account.bank),
        AccountId(t.this_account.id),
        TransactionId(id)) match {
        case Full(ct) => ct
      }

    val t1 = getCreatedTransaction(t.id)
    val t2 = getCreatedTransaction(newTransId)

    //check the created transactions have the same counterparty id
    t1.otherAccount.counterPartyId must equal(t2.otherAccount.counterPartyId)
  }

  it must "create branches ok" in {

    // Helper function expects banks and branches
    def getResponse(branchList : List[JValue]) = {
          val json = createImportJson(standardBanks.map(Extraction.decompose), Nil, Nil, Nil, branchList, Nil, Nil, Nil)
          // Posts the Json (token gets added)
          postImportJson(json)
        }

    val bankId1 = BankId(bank1.id)

    val branchesJson = standardBranches.map(Extraction.decompose)

    // Check we are starting from a clean slate (no branches for this bank)
    // Might be better to expect Try[List[Branch]] but then would need to modify the API stack up to the top
    val existingBranches: Option[List[Branch]] = Branches.branchesProvider.vend.getBranches(bankId1)

    // We want the size of the list inside the Option
    val existingBranchesCount = countOfBranches(existingBranches)
    existingBranchesCount must equal (0)

    // Check creation succeeds
    val response = getResponse(branchesJson)
    response.code must equal(SUCCESS)

    // Check count after creation. Again counting the items in list, not the option
    val countBranchesAfter = countOfBranches(Branches.branchesProvider.vend.getBranches(bankId1))
    countBranchesAfter must equal(standardBranches.size) // We expect N branches

    // Check that for each branch we did indeed create something good
    standardBranches.foreach(verifyBranchCreated)
  }




  it must "create ATMs ok" in {

    // Helper function expects banks and branches
    def getResponse(atmList : List[JValue]) = {
      val json = createImportJson(standardBanks.map(Extraction.decompose), Nil, Nil, Nil, Nil, atmList, Nil, Nil)
      println(json)
      postImportJson(json)
    }


    val bankId1 = BankId(bank1.id)

    val atmsJson  = standardAtms.map(Extraction.decompose)

    // Check we are starting from a clean slate (no atms for this bank)
    // Might be better to expect Try[List[Branch]] but then would need to modify the API stack up to the top
    val existingAtms: Option[List[Atm]] = Atms.atmsProvider.vend.getAtms(bankId1)

    // We want the size of the list inside the Option
    val existingAtmsCount = countOfAtms(existingAtms)
    existingAtmsCount must equal (0)

    // Check creation succeeds
    val response = getResponse(atmsJson).code
    response must equal(SUCCESS)

    // Check count after creation. Again counting the items in list, not the option
    val countAtmsAfter = countOfAtms(Atms.atmsProvider.vend.getAtms(bankId1))
    countAtmsAfter must equal(standardBranches.size) // We expect N branches

    // Check that for each branch we did indeed create something good
    standardAtms.foreach(verifyAtmCreated)
  }


  it must "create Products ok" in {

    // Helper function expects banks and branches
    def getResponse(productList : List[JValue]) = {
      val json = createImportJson(standardBanks.map(Extraction.decompose), Nil, Nil, Nil, Nil, Nil, productList, Nil)
      println(json)
      postImportJson(json)
    }


    val bankId1 = BankId(bank1.id)

    val productsJson  = standardProducts.map(Extraction.decompose)

    // Check we are starting from a clean slate (no atms for this bank)
    // Might be better to expect Try[List[Branch]] but then would need to modify the API stack up to the top
    val existingProducts: Option[List[Product]] = Products.productsProvider.vend.getProducts(bankId1)

    // We want the size of the list inside the Option
    val existingCount = countOfProducts(existingProducts)
    existingCount must equal (0)

    // Check creation succeeds
    val response = getResponse(productsJson).code
    response must equal(SUCCESS)

    // Check count after creation. Again counting the items in list, not the option
    val countAfter = countOfProducts(Products.productsProvider.vend.getProducts(bankId1))
    countAfter must equal(standardProducts.size) // We expect N branches

    // Check that for each branch we did indeed create something good
    standardProducts.foreach(verifyProductCreated)
  }



  it must "create CRM Events ok" in {

    // Helper function expects banks and branches
    def getResponse(crmEventList : List[JValue]) = {
      val json = createImportJson(standardBanks.map(Extraction.decompose), Nil, Nil, Nil, Nil, Nil, Nil, crmEventList)
      println(json)
      postImportJson(json)
    }


    val bankId1 = BankId(bank1.id)

    // All events are at bank1
    val crmEventsJson  = standardCrmEvents.map(Extraction.decompose)

    // Check we are starting from a clean slate
    // Might be better to expect Try[List[Branch]] but then would need to modify the API stack up to the top
    val existingCrmEvents: Option[List[CrmEvent]] = CrmEvent.crmEventProvider.vend.getCrmEvents(bankId1)

    // We want the size of the list inside the Option
    val existingCount = CrmEvent.countOfCrmEvents(existingCrmEvents)
    existingCount must equal (0)

    // Check creation succeeds
    val response = getResponse(crmEventsJson).code
    response must equal(SUCCESS)

    // Check count after creation. Again counting the items in list, not the option
    val countAfter = CrmEvent.countOfCrmEvents(CrmEvent.crmEventProvider.vend.getCrmEvents(bankId1))
    countAfter must equal(standardCrmEvents.size) // We expect N events

    // Check that for each branch we did indeed create something good
    standardCrmEvents.foreach(verifyCrmEventCreated)
  }







}
