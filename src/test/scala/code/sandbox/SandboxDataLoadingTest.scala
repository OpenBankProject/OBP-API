/**
Open Bank Project - API
Copyright (C) 2011, 2013, TESOBE / Music Pictures Ltd

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.

Email: contact@tesobe.com
TESOBE / Music Pictures Ltd
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

import java.util.Date

import code.TestServer
import code.api.test.{SendServerRequests, APIResponse}
import code.api.v1_2_1.APIMethods121
import code.model.dataAccess._
import code.model.{TransactionId, AccountId, BankId}
import code.users.Users
import dispatch._
import net.liftweb.json.JsonAST.JObject
import net.liftweb.mapper.By
import net.liftweb.util.Props
import org.scalatest.{BeforeAndAfterEach, ShouldMatchers, FlatSpec}
import net.liftweb.json.JsonDSL._
import net.liftweb.json._
import net.liftweb.json.Serialization.write
import code.bankconnectors.Connector
import net.liftweb.common.{Full, Empty}
import net.liftweb.mongodb._

class SandboxDataLoadingTest extends FlatSpec with SendServerRequests with ShouldMatchers with BeforeAndAfterEach {

  implicit val formats = Serialization.formats(NoTypeHints)

  val server = TestServer
  def baseRequest = host(server.host, server.port)

  def sandboxApiPrefix = baseRequest / "obp" / "vsandbox"

  //users should automatically be assigned the "hostname" as a provider (for now at least)
  val defaultProvider = Props.get("hostname").openOrThrowException("no hostname set")

  val theImportToken = Props.get("sandbox_data_import_secret").openOrThrowException("sandbox_data_import_secret not set")


  override def afterEach() = {
    //drop database tables after the tests
    MongoDB.getDb(DefaultMongoIdentifier).foreach(_.dropDatabase())
    ViewImpl.findAll.foreach(_.delete_!)
    ViewPrivileges.findAll.foreach(_.delete_!)
    HostedAccount.findAll.foreach(_.delete_!)
    MappedAccountHolder.findAll.foreach(_.delete_!)
    OBPUser.findAll.foreach(_.delete_!)
  }


  def toJsonArray(xs : List[String]) : String = {
    xs.mkString("[", ",", "]")
  }

  def createImportJson(banks: List[JValue], users: List[JValue], accounts : List[JValue], transactions : List[JValue]) : String = {
    val json =
      ("banks" -> banks) ~
      ("users" -> users) ~
      ("accounts" -> accounts) ~
      ("transactions" -> transactions)
    compact(render(json))
  }

  // posts the json with the correct secret token
  def postImportJson(json : String) : APIResponse = {
    postImportJson(json, Some(theImportToken))
  }

  def postImportJson(json : String, secretToken : Option[String]) : APIResponse = {
    val base = sandboxApiPrefix / "v1.0" / "data-import"
    val request = secretToken match {
      case Some(t) => base <<? Map("secret_token" -> t)
      case None => base
    }
    makePostRequest(request, json)
  }

  def verifyBankCreated(bank : SandboxBankImport) = {
    val bankId = BankId(bank.id)
    val foundBankBox = Connector.connector.vend.getBank(bankId)

    foundBankBox.isDefined should equal(true)

    val foundBank = foundBankBox.get

    foundBank.id should equal(bankId)
    foundBank.shortName should equal(bank.short_name)
    foundBank.fullName should equal(bank.full_name)
    foundBank.logoURL should equal(bank.logo)
    foundBank.website should equal(bank.website)
  }

  def verifyUserCreated(user : SandboxUserImport) = {
    val foundUserBox = Users.users.vend.getUserByProviderId(defaultProvider, user.email)
    foundUserBox.isDefined should equal(true)

    val foundUser = foundUserBox.get

    foundUser.provider should equal(defaultProvider)
    foundUser.idGivenByProvider should equal(user.email)
    foundUser.emailAddress should equal(user.email)
    foundUser.name should equal(user.display_name)
  }

  def verifyAccountCreated(account : SandboxAccountImport) = {
    val accId = AccountId(account.id)
    val bankId = BankId(account.bank)
    val foundAccountBox = Connector.connector.vend.getBankAccount(bankId, accId)
    foundAccountBox.isDefined should equal(true)

    val foundAccount = foundAccountBox.get

    foundAccount.bankId should equal(bankId)
    foundAccount.accountId should equal(accId)
    foundAccount.label should equal(account.label)
    foundAccount.number should equal(account.number)
    foundAccount.accountType should equal(account.`type`)
    foundAccount.iban should equal(Some(account.IBAN))
    foundAccount.balance.toString should equal(account.balance.amount)
    foundAccount.currency should equal(account.balance.currency)

    foundAccount.owners.map(_.id) should equal(account.owners.toSet)

    if(account.generate_public_view) {
      foundAccount.publicViews.size should equal(1)
    } else {
      foundAccount.publicViews.size should equal(0)
    }

    val owner = Users.users.vend.getUserByProviderId(defaultProvider, foundAccount.owners.toList.head.id).get

    //there should be an owner view
    val views = foundAccount.views(owner).get
    views.exists(v => v.permalink == "owner") should equal(true)
  }

  def verifyTransactionCreated(transaction : SandboxTransactionImport, accountsUsed : List[SandboxAccountImport]) = {
    val bankId = BankId(transaction.this_account.bank)
    val accountId = AccountId(transaction.this_account.id)
    val transactionId = TransactionId(transaction.id)
    val foundTransactionBox = Connector.connector.vend.getTransaction(bankId, accountId, transactionId)

    foundTransactionBox.isDefined should equal(true)

    val foundTransaction = foundTransactionBox.get

    foundTransaction.id should equal(transactionId)
    foundTransaction.bankId should equal(bankId)
    foundTransaction.accountId should equal(accountId)
    foundTransaction.description should equal(transaction.details.description)
    foundTransaction.balance.toString should equal(transaction.details.new_balance)
    foundTransaction.amount.toString should equal(transaction.details.value)

    def toDate(dateString : String) : Date = {
      APIMethods121.DateParser.parse(dateString).get
    }

    foundTransaction.startDate should equal(toDate(transaction.details.posted))
    foundTransaction.finishDate should equal(toDate(transaction.details.completed))

    //a counterparty should exist
    val otherAcc = foundTransaction.otherAccount
    otherAcc.id should not be empty
    otherAcc.originalPartyAccountId should equal(accountId)
    otherAcc.originalPartyBankId should equal(bankId)
    val otherAccMeta = otherAcc.metadata
    otherAccMeta.publicAlias should not be empty

    //if a counterparty was originally specified in the import data, it should correspond to that
    //counterparty
    if(transaction.counterparty.isDefined) {
      val counterParty = transaction.counterparty.get
      val accountUsedOpt = accountsUsed.find(a => a.id == counterParty.id && a.bank == counterParty.bank)
      accountUsedOpt.isDefined should equal(true)

      val accountUsed = accountUsedOpt.get
      otherAcc.label should equal(accountUsed.label)
      otherAcc.number should equal(accountUsed.number)
      otherAcc.kind should equal(accountUsed.`type`)
      otherAcc.iban should equal(Some(accountUsed.IBAN))
    }

  }

  def addField(json : JValue, fieldName : String, fieldValue : String) = {
    json.transform{
      case JObject(fields) => JObject(JField(fieldName, fieldValue) :: fields)
    }
  }

  def removeField(json : JValue, fieldName : String) = {
    json.remove {
      case JField(fieldName, _) => true
      case _ => false
    }
  }

  def removeField(json : JValue, fieldSpecifier : List[String]) = {
    json.replace(fieldSpecifier, JNothing)
  }

  def replaceField(json : JValue, fieldSpecifier : List[String], fieldValue : String) =
    json.replace(fieldSpecifier, fieldValue)

  def replaceField(json : JValue, fieldName : String, fieldValue : String) =
    json.replace(List(fieldName), fieldValue)

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

  val user1 = SandboxUserImport(email = "user1@example.com", password = "qwerty", display_name = "User 1")
  val user2 = SandboxUserImport(email = "user2@example.com", password = "qwerty", display_name = "User 2")

  val standardUsers = user1 :: user2 :: Nil

  val account1AtBank1 = SandboxAccountImport(id = "account1", bank = "bank1", label = "Account 1 at Bank 1",
    number = "1", `type` = "savings", IBAN = "1234567890", generate_public_view = true, owners = List(user1.email),
    balance = SandboxBalanceImport(currency = "EUR", amount = 1000.00))

  val account2AtBank1 = SandboxAccountImport(id = "account2", bank = "bank1", label = "Account 2 at Bank 1",
    number = "2", `type` = "current", IBAN = "91234567890", generate_public_view = false, owners = List(user2.email),
    balance = SandboxBalanceImport(currency = "EUR", amount = 2000.00))

  val account1AtBank2 = SandboxAccountImport(id = "account1", bank = "bank2", label = "Account 1 at Bank 2",
    number = "22", `type` = "savings", IBAN = "21234567890", generate_public_view = false, owners = List(user1.email, user2.email),
    balance = SandboxBalanceImport(currency = "EUR", amount = 1500.00))

  val standardAccounts = account1AtBank1 :: account2AtBank1 :: account1AtBank2 :: Nil

  val transactionWithCounterparty = SandboxTransactionImport(id = "transaction-with-counterparty",
    this_account = SandboxAccountIdImport(id = account1AtBank1.id, bank=account1AtBank1.bank),
    counterparty = Some(SandboxAccountIdImport(id = account2AtBank1.id, bank=account2AtBank1.bank)),
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

  /**
   *
   *
   * Tests below
   *
   *
   */

  "Data import" should "work in the general case" in {

    //same transaction id as another one used, but for a different bank account, so it should work
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

    val banks = standardBanks
    val users = standardUsers
    val accounts = standardAccounts
    val transactions = anotherTransaction :: standardTransactions

    val importJson = SandboxDataImport(banks, users, accounts, transactions)
    val response = postImportJson(write(importJson))

    response.code should equal(201)

    banks.foreach(verifyBankCreated)
    users.foreach(verifyUserCreated)
    accounts.foreach(verifyAccountCreated)
    transactions.foreach(verifyTransactionCreated(_, accounts))
  }

  it should "not allow data to be imported without a secret token" in {
    val importJson = SandboxDataImport(standardBanks, standardUsers, standardAccounts, standardTransactions)
    val response = postImportJson(write(importJson), None)

    response.code should equal(403)

    //nothing should be created
    Connector.connector.vend.getBanks should equal(Nil)
  }

  it should "not allow data to be imported with an invalid secret token" in {
    val importJson = SandboxDataImport(standardBanks, standardUsers, standardAccounts, standardTransactions)
    val badToken = "12345"
    badToken should not equal(theImportToken)
    val response = postImportJson(write(importJson), Some(badToken))

    response.code should equal(403)

    //nothing should be created
    Connector.connector.vend.getBanks should equal(Nil)
  }

  it should "require banks to have non-empty ids" in {

    //no banks should exist initially
    Connector.connector.vend.getBanks.size should equal(0)

    val bank1Json = Extraction.decompose(bank1)

    val bankWithoutId = removeIdField(bank1Json)

    def getResponse(bankJson : JValue) = {
      val json = createImportJson(List(bankJson), Nil, Nil, Nil)
      postImportJson(json)
    }

    getResponse(bankWithoutId).code should equal(400)

    //no banks should have been created
    Connector.connector.vend.getBanks.size should equal(0)

    val bankWithEmptyId = addIdField(bankWithoutId, "")
    getResponse(bankWithEmptyId).code should equal(400)

    //no banks should have been created
    Connector.connector.vend.getBanks.size should equal(0)

    //Check that the same json becomes valid when a non-empty id is added
    val validId = "foo"
    val bankWithValidId = addIdField(bankWithoutId, validId)
    val response = getResponse(bankWithValidId)
    response.code should equal(201)

    //Check the bank was created
    val banks = Connector.connector.vend.getBanks
    banks.size should equal(1)
    val createdBank  = banks(0)

    createdBank.id should equal(BankId(validId))
    createdBank.shortName should equal(bank1.short_name)
    createdBank.fullName should equal(bank1.full_name)
    createdBank.logoURL should equal(bank1.logo)
    createdBank.website should equal(bank1.website)
  }

  it should "not allow multiple banks with the same id" in {
    //no banks should exist initially
    Connector.connector.vend.getBanks.size should equal(0)

    val bank1AsJValue = Extraction.decompose(bank1)

    val baseOtherBank =
      ("short_name" -> {bank1.short_name + "2"}) ~
      ("full_name" -> {bank1.full_name + "2"}) ~
      ("logo" -> {bank1.logo + "2"}) ~
      ("website" -> {bank1.website + "2"})

    //same id as bank1, but different other attributes
    val bankWithSameId = addIdField(baseOtherBank, bank1.id)

    def getResponse(bankJsons : List[JValue]) = {
      val json = createImportJson(bankJsons, Nil, Nil, Nil)
      postImportJson(json)
    }

    getResponse(List(bank1AsJValue, bankWithSameId)).code should equal(400)

    //now try again but this time with a different id
    val validOtherBank = addIdField(baseOtherBank, {bank1.id + "2"})

    getResponse(List(bank1AsJValue, validOtherBank)).code should equal(201)

    //check that two banks were created
    val banks = Connector.connector.vend.getBanks
    banks.size should equal(2)
  }

  it should "fail if a specified bank already exists" in {
    def getResponse(bankJsons : List[JValue]) = {
      val json = createImportJson(bankJsons, Nil, Nil, Nil)
      postImportJson(json)
    }

    val bank1Json = Extraction.decompose(bank1)

    //add bank1
    getResponse(List(bank1Json)).code should equal(201)


    val otherBank = bank2
    //when we try to add bank1 and another valid bank it should now fail
    getResponse(List(bank1Json, Extraction.decompose(bank2))).code should equal(400)

    //and the other bank should not have been created
    Connector.connector.vend.getBank(BankId(otherBank.id)).isDefined should equal(false)
  }

  it should "require users to have valid emails" in {

    def getResponse(userJson : JValue) = {
      val json = createImportJson(Nil, List(userJson), Nil, Nil)
      postImportJson(json)
    }

    val user1AsJson = Extraction.decompose(user1)

    val userWithoutEmail = removeEmailField(user1AsJson)

    getResponse(userWithoutEmail).code should equal(400)

    val userWithEmptyEmail = addEmailField(userWithoutEmail, "")

    //there should be no user with a blank id before we try to add one
    Users.users.vend.getUserByProviderId(defaultProvider, "") should equal(Empty)

    getResponse(userWithEmptyEmail).code should equal(400)

    //there should still be no user with a blank email
    Users.users.vend.getUserByProviderId(defaultProvider, "") should equal(Empty)

    //invalid email should fail
    val invalidEmail = "foooo"
    val userWithInvalidEmail = addEmailField(userWithoutEmail, invalidEmail)

    getResponse(userWithInvalidEmail).code should equal(400)

    //there should still be no user
    Users.users.vend.getUserByProviderId(defaultProvider, invalidEmail) should equal(Empty)

    val validEmail = "test@example.com"
    val userWithValidEmail = addEmailField(userWithoutEmail, validEmail)

    getResponse(userWithValidEmail).code should equal(201)

    //a user should now have been created
    val createdUser = Users.users.vend.getUserByProviderId(defaultProvider, validEmail)
    createdUser.isDefined should equal(true)
    createdUser.get.provider should equal(defaultProvider)
    createdUser.get.idGivenByProvider should equal(validEmail)
    createdUser.get.emailAddress should equal(validEmail)

  }

  it should "not allow multiple users with the same email" in {

    def getResponse(userJsons : List[JValue]) = {
      val json = createImportJson(Nil, userJsons, Nil, Nil)
      postImportJson(json)
    }

    //emails of the user we will eventually create to show multiple users with different ids are possible
    val secondUserEmail = "user-two@example.com"

    val user1Json = Extraction.decompose(user1)

    val differentDisplayName = "Jessica Bloggs"
    differentDisplayName should not equal(user1.display_name)
    val userWithSameEmailAsUser1 = replaceDisplayName(user1Json, differentDisplayName)

    //neither of the users should exist initially
    Users.users.vend.getUserByProviderId(defaultProvider, user1.email) should equal(Empty)
    Users.users.vend.getUserByProviderId(defaultProvider, secondUserEmail) should equal(Empty)

    getResponse(List(user1Json, userWithSameEmailAsUser1)).code should equal(400)

    //no user with firstUserId should be created
    Users.users.vend.getUserByProviderId(defaultProvider, user1.email) should equal(Empty)

    //when we only alter the id (display name stays the same), it should work
    val userWithEmail2 = replaceField(userWithSameEmailAsUser1, "email", secondUserEmail)

    getResponse(List(user1Json, userWithEmail2)).code should equal(200)

    //and both users should be created
    val firstUser = Users.users.vend.getUserByProviderId(defaultProvider, user1.email)
    val secondUser = Users.users.vend.getUserByProviderId(defaultProvider, secondUserEmail)

    firstUser.isDefined should equal(true)
    secondUser.isDefined should equal(true)

    firstUser.get.idGivenByProvider should equal(user1.email)
    secondUser.get.idGivenByProvider should equal(secondUserEmail)

    firstUser.get.emailAddress should equal(user1.email)
    secondUser.get.emailAddress should equal(secondUserEmail)

    firstUser.get.name should equal(user1.display_name)
    secondUser.get.name should equal(differentDisplayName)
  }

  it should "fail if a specified user already exists" in {
    def getResponse(userJsons : List[JValue]) = {
      val json = createImportJson(Nil, userJsons, Nil, Nil)
      postImportJson(json)
    }

    val user1Json = Extraction.decompose(user1)

    //add user1
    getResponse(List(user1Json)).code should equal(201)


    val otherUser = user2
    //when we try to add user1 and another valid new user it should now fail
    getResponse(List(user1Json, Extraction.decompose(otherUser))).code should equal(400)

    //and the other user should not have been created
    Users.users.vend.getUserByProviderId(defaultProvider, otherUser.email)
  }

  it should "fail if a user's password is missing or empty" in {
    def getResponse(userJsons : List[JValue]) = {
      val json = createImportJson(Nil, userJsons, Nil, Nil)
      postImportJson(json)
    }

    val goodUser = Extraction.decompose(user1)

    val userWithoutPassword = removeField(goodUser, "password")
    getResponse(List(userWithoutPassword)).code should equal(400)
    //no user should be created
    Users.users.vend.getUserByProviderId(defaultProvider, user1.email).isDefined should equal(false)

    val userWithBlankPassword = replaceField(goodUser, "password", "")
    getResponse(List(userWithBlankPassword)).code should equal(400)
    //no user should be created
    Users.users.vend.getUserByProviderId(defaultProvider, user1.email).isDefined should equal(false)

    //check that a normal password is okay
    getResponse(List(goodUser)).code should equal(201)
    Users.users.vend.getUserByProviderId(defaultProvider, user1.email).isDefined should equal(true)
  }

  it should "set user passwords properly" in {
    def getResponse(userJsons : List[JValue]) = {
      val json = createImportJson(Nil, userJsons, Nil, Nil)
      postImportJson(json)
    }

    getResponse(List(Extraction.decompose(user1))).code should equal(201)

    //TODO: we shouldn't reference OBPUser here as it is an implementation, but for now there
    //is no way to check User (the trait) passwords
    val createdOBPUserBox = OBPUser.find(By(OBPUser.email, user1.email))
    createdOBPUserBox.isDefined should equal(true)

    val createdOBPUser = createdOBPUserBox.get
    createdOBPUser.password.match_?(user1.password) should equal(true)
  }

  it should "require accounts to have non-empty ids" in {

    def getResponse(accountJsons : List[JValue]) = {
      val banks = standardBanks.map(Extraction.decompose)
      val users = standardUsers.map(Extraction.decompose)
      val json = createImportJson(banks, users, accountJsons, Nil)
      postImportJson(json)
    }

    val acc1AtBank1Json = Extraction.decompose(account1AtBank1)
    val accountWithoutId = removeIdField(acc1AtBank1Json)

    getResponse(List(accountWithoutId)).code should equal(400)

    val accountWithEmptyId = addIdField(accountWithoutId, "")

    getResponse(List(accountWithEmptyId)).code should equal(400)

    //no account should exist with an empty id
    Connector.connector.vend.getBankAccount(BankId(account1AtBank1.bank), AccountId("")) should equal(Empty)

    getResponse(List(acc1AtBank1Json)).code should equal(201)

    //an account should now exist
    verifyAccountCreated(account1AtBank1)
  }

  it should "not allow multiple accounts at the same bank with the same id" in {

    def getResponse(accountJsons : List[JValue]) = {
      val banks = standardBanks.map(Extraction.decompose)
      val users = standardUsers.map(Extraction.decompose)
      val json = createImportJson(banks, users, accountJsons, Nil)
      postImportJson(json)
    }

    val account1AtBank1Json = Extraction.decompose(account1AtBank1)
    val accountWithSameId = replaceField(Extraction.decompose(account1AtBank2), "id", account1AtBank1.id)
    //might be nice to test a case where the only similar attribute between the accounts is the id
    getResponse(List(account1AtBank1Json, accountWithSameId)).code should equal(400)

    //no accounts should have been created
    Connector.connector.vend.getBankAccount(BankId(account1AtBank1.bank), AccountId(account1AtBank1.id)) should equal(Empty)
    Connector.connector.vend.getBankAccount(BankId(account1AtBank2.bank), AccountId(account1AtBank1.id)) should equal(Empty)

    val accountIdTwo = "2"
    val accountWithDifferentId = replaceField(accountWithSameId, "id", accountIdTwo)

    getResponse(List(account1AtBank1Json, accountWithDifferentId)).code should equal(201)

    //two accounts should have been created
    Connector.connector.vend.getBankAccount(BankId(account1AtBank1.bank), AccountId(account1AtBank1.id)).isDefined should equal(true)
    Connector.connector.vend.getBankAccount(BankId(account1AtBank2.bank), AccountId(accountIdTwo)).isDefined should equal(true)

  }

  it should "fail if a specified account already exists" in {
    def getResponse(accountJsons : List[JValue]) = {
      val banks = standardBanks.map(Extraction.decompose)
      val users = standardUsers.map(Extraction.decompose)
      val json = createImportJson(banks, users, accountJsons, Nil)
      postImportJson(json)
    }
    val account1AtBank1Json = Extraction.decompose(account1AtBank1)

    //add account1AtBank1
    getResponse(List(account1AtBank1Json)).code should equal(201)

    val otherAccount = account1AtBank2
    //when we try to add account1AtBank1 and another valid account it should now fail
    getResponse(List(account1AtBank1Json, Extraction.decompose(otherAccount))).code should equal(400)

    //and the other account should not have been created
    Connector.connector.vend.getBankAccount(BankId(otherAccount.bank), AccountId(otherAccount.id)).isDefined should equal(false)
  }

  it should "not allow an account to have a bankId not specified in the imported banks" in {

    val badBankId = "asdf"

    def getResponse(accountJsons : List[JValue]) = {
      standardBanks.exists(b => b.id == badBankId) should equal(false)
      val banks = standardBanks.map(Extraction.decompose)

      val users = standardUsers.map(Extraction.decompose)
      val json = createImportJson(banks, users, accountJsons, Nil)
      postImportJson(json)
    }

    val badBankAccount = replaceField(Extraction.decompose(account1AtBank1), "id", badBankId)

    getResponse(List(badBankAccount)).code should equal(400)

    //no account should have been created
    Connector.connector.vend.getBankAccount(BankId(badBankId), AccountId(account1AtBank1.id)).isDefined should equal(false)
  }

  it should "not allow an account to be created without an owner" in {
    def getResponse(accountJsons : List[JValue]) = {
      val banks = standardBanks.map(Extraction.decompose)
      val users = standardUsers.map(Extraction.decompose)

      val json = createImportJson(banks, users, accountJsons, Nil)
      postImportJson(json)
    }

    val acc1AtBank1Json = Extraction.decompose(account1AtBank1)

    val accountWithNoOwnerField = removeField(acc1AtBank1Json, "owners")

    getResponse(List(accountWithNoOwnerField)).code should equal(400)

    val accountWithNilOwners = Extraction.decompose(account1AtBank1.copy(owners = Nil))

    getResponse(List(accountWithNilOwners)).code should equal(400)
  }

  it should "not allow an account to be created with an owner not specified in data import users" in {

    val users = standardUsers
    val banks = standardBanks

    def getResponse(accountJsons : List[JValue]) = {
      val json = createImportJson(banks.map(Extraction.decompose), users.map(Extraction.decompose), accountJsons, Nil)
      postImportJson(json)
    }

    val nonExistentOwnerEmail = "asdfasdfasdf@example.com"
    users.exists(u => u.email == nonExistentOwnerEmail) should equal(false)

    val accountWithInvalidOwner = account1AtBank1.copy(owners = List(nonExistentOwnerEmail))

    getResponse(List(Extraction.decompose(accountWithInvalidOwner))).code should equal(400)

    //it should not have been created
    Connector.connector.vend.getBankAccount(BankId(accountWithInvalidOwner.bank), AccountId(accountWithInvalidOwner.id)) should equal(Empty)

    //a mix of valid an invalid owners should also not work
    val accountWithSomeValidSomeInvalidOwners = accountWithInvalidOwner.copy(owners = List(accountWithInvalidOwner.owners + user1.email))
    getResponse(List(Extraction.decompose(accountWithSomeValidSomeInvalidOwners))).code should equal(400)

    //it should not have been created
    Connector.connector.vend.getBankAccount(BankId(accountWithSomeValidSomeInvalidOwners.bank), AccountId(accountWithSomeValidSomeInvalidOwners.id)) should equal(Empty)

  }

  it should "not allow multiple accounts at the same bank with the same account number" in {
    val users = standardUsers
    val banks = standardBanks

    def getResponse(accountJsons : List[JValue]) = {
      val json = createImportJson(banks.map(Extraction.decompose), users.map(Extraction.decompose), accountJsons, Nil)
      postImportJson(json)
    }

    val acc1 = account1AtBank1
    val acc2 = account2AtBank1

    val acc1Json = Extraction.decompose(acc1)
    val acc2Json = Extraction.decompose(acc2)
    val sameNumberJson = replaceField(acc2Json, "number", acc1.number)

    getResponse(List(acc1Json, sameNumberJson)).code should equal(400)

    //no accounts should have been created
    Connector.connector.vend.getBankAccount(BankId(acc1.bank), AccountId(acc1.id)) should equal(Empty)
    Connector.connector.vend.getBankAccount(BankId(acc1.bank), AccountId(acc2.id)) should equal(Empty)

    //check it works with the normal different number
    getResponse(List(acc1Json, acc2Json)).code should equal(201)

    //and the accounts should be created
    Connector.connector.vend.getBankAccount(BankId(acc1.bank), AccountId(acc1.id)).isDefined should equal(true)
    Connector.connector.vend.getBankAccount(BankId(acc1.bank), AccountId(acc2.id)).isDefined should equal(true)
  }

  it should "require transactions to have non-empty ids" in {

    def getResponse(transactionJsons : List[JValue]) = {
      val json = createImportJson(standardBanks.map(Extraction.decompose),
        standardUsers.map(Extraction.decompose), standardAccounts.map(Extraction.decompose), transactionJsons)
      postImportJson(json)
    }

    def transactionExists() : Boolean = {
      Connector.connector.vend.getTransaction(BankId(transactionWithoutCounterparty.this_account.bank),
        AccountId(transactionWithoutCounterparty.this_account.id),
        TransactionId(transactionWithoutCounterparty.id)).isDefined
    }

    val transactionJson = Extraction.decompose(transactionWithoutCounterparty)

    val missingIdTransaction = removeIdField(transactionJson)
    getResponse(List(missingIdTransaction)).code should equal(400)
    transactionExists() should equal(false)

    val emptyIdTransaction = replaceField(transactionJson, "id", "")
    getResponse(List(emptyIdTransaction)).code should equal(400)
    transactionExists() should equal(false)

    //the original transaction should work too (just to make sure it's not failing because we have, e.g. a bank id that doesn't exist)
    getResponse(List(transactionJson)).code should equal(201)

    //it should exist now
    transactionExists() should equal(true)
  }

  it should "require transactions for a single account do not have the same id" in {

    def getResponse(transactionJsons : List[JValue]) = {
      val json = createImportJson(standardBanks.map(Extraction.decompose),
        standardUsers.map(Extraction.decompose), standardAccounts.map(Extraction.decompose), transactionJsons)
      postImportJson(json)
    }

    val t1 = transactionWithoutCounterparty
    val t2 = transactionWithCounterparty

    //make sure the two transactions are for the same account have different ids
    t1.this_account.bank should equal(t2.this_account.bank)
    t1.this_account.id should equal(t2.this_account.id)
    t1.id should not equal(t2.id)

    val transactionJson = Extraction.decompose(t1)
    val transaction2Json = Extraction.decompose(t2)

    //now edit the second transaction to give it the same id as the first one
    val sameIdAsOtherTransaction = replaceField(transaction2Json, "id", t1.id)

    getResponse(List(transactionJson, sameIdAsOtherTransaction)).code should equal(400)

    //Neither should exist
    Connector.connector.vend.getTransaction(BankId(t1.this_account.bank),
      AccountId(t1.this_account.id),
      TransactionId(t1.id)).isDefined should equal(false)

    //now make sure it's not failing because we have, e.g. a bank id that doesn't exist by checking the originals worked
    getResponse(List(transactionJson, transaction2Json)).code should equal(201)

    //both should exist now
    Connector.connector.vend.getTransaction(BankId(t1.this_account.bank),
      AccountId(t1.this_account.id),
      TransactionId(t1.id)).isDefined should equal(true)

    Connector.connector.vend.getTransaction(BankId(t2.this_account.bank),
      AccountId(t2.this_account.id),
      TransactionId(t2.id)).isDefined should equal(true)
  }

  it should "fail if a specified transaction already exists" in {
    def getResponse(transactionJsons : List[JValue]) = {
      val json = createImportJson(standardBanks.map(Extraction.decompose),
        standardUsers.map(Extraction.decompose), standardAccounts.map(Extraction.decompose), transactionJsons)
      postImportJson(json)
    }

    val t1Json = Extraction.decompose(transactionWithoutCounterparty)

    //add transaction
    getResponse(List(t1Json)).code should equal(201)

    //when we try to add t1Json and another valid transaction it should now fail
    getResponse(List(t1Json, Extraction.decompose(transactionWithoutCounterparty))).code should equal(400)

    //and no new transaction should exist
    Connector.connector.vend.getTransaction(BankId(transactionWithoutCounterparty.this_account.bank),
      AccountId(transactionWithoutCounterparty.this_account.id),
      TransactionId(transactionWithoutCounterparty.id)).isDefined should equal(false)
  }

  it should "not create any transactions when one has an invalid this_account" in {
    val banks = standardBanks
    val users = standardUsers
    val accounts = standardAccounts

    def getResponse(transactionJsons : List[JValue]) = {
      val json = createImportJson(banks.map(Extraction.decompose),
        users.map(Extraction.decompose), accounts.map(Extraction.decompose), transactionJsons)
      postImportJson(json)
    }

    val t = transactionWithCounterparty

    val validTransaction = Extraction.decompose(t)

    //ensure bank is correct
    banks.exists(b => b.id == t.this_account.bank) should equal(true)

    val invalidAccountId = "asdfasdfasdf"
    //ensure account id is invalid
    accounts.exists(a => a.bank == t.this_account.bank && a.id == invalidAccountId) should equal(false)

    //check one where the bank id exists, but the account id doesn't
    val invalidAccTransaction = replaceField(validTransaction, List("this_account","id"), invalidAccountId)

    getResponse(List(invalidAccTransaction)).code should equal(400)

    //transaction should not exist
    Connector.connector.vend.getTransaction(BankId(t.this_account.bank),
      AccountId(invalidAccountId),
      TransactionId(t.id)).isDefined should equal(false)

    //now check one where the bankId is invalid

    val invalidBankId = "omommomom"
    //ensure bank is invalid
    banks.exists(b => b.id == invalidBankId) should equal(false)

    val invalidBankTransaction = replaceField(validTransaction, List("this_account", "bank"), invalidBankId)

    getResponse(List(invalidBankTransaction)).code should equal(400)

    //transaction should not exist
    Connector.connector.vend.getTransaction(BankId(invalidBankId),
      AccountId(t.this_account.id),
      TransactionId(t.id)).isDefined should equal(false)

    //now make sure it works when all is well
    getResponse(List(validTransaction)).code should equal(201)

    //transaction should exist
    Connector.connector.vend.getTransaction(BankId(t.this_account.bank),
      AccountId(t.this_account.id),
      TransactionId(t.id)).isDefined should equal(true)

  }

  it should "not create any transactions when one has an invalid counterparty" in {
    val (banks, users, accounts) = (standardBanks, standardUsers, standardAccounts)

    def getResponse(transactionJsons: List[JValue]) = {
      val json = createImportJson(banks.map(Extraction.decompose),
        users.map(Extraction.decompose), accounts.map(Extraction.decompose), transactionJsons)
      postImportJson(json)
    }

    val t = transactionWithCounterparty

    val validTransaction = Extraction.decompose(t)


    val invalidCounterpartyBank = "pdmowmxs"

    //ensure invalid bank does't exit
    banks.exists(b => b.id == invalidCounterpartyBank) should equal(false)

    val withBadCounterpartyBank = replaceField(validTransaction, List("counterparty", "bank"), invalidCounterpartyBank)

    //need a new transaction id too:
    val newTransId = "9032902030209"
    t.id should not equal (newTransId)

    def checkNoTransactionsExist() = checkTransactions(false)
    def checkTransactionsExist() = checkTransactions(true)

    def checkTransactions(exist: Boolean) = {
      Connector.connector.vend.getTransaction(BankId(t.this_account.bank),
        AccountId(t.this_account.id),
        TransactionId(t.id)).isDefined should equal(exist)

      Connector.connector.vend.getTransaction(BankId(t.this_account.bank),
        AccountId(t.this_account.id),
        TransactionId(newTransId)).isDefined should equal(exist)
    }

    val invalidCounterPartyTransaction = replaceField(withBadCounterpartyBank, "id", newTransId)

    //it shouldn't work with a single transaction with an invalid counterparty
    getResponse(List(invalidCounterPartyTransaction)).code should equal(400)
    checkNoTransactionsExist()

    //it shouldn't work when there are multiple transactions, one of which has an invalid counterparty
    getResponse(List(validTransaction, invalidCounterPartyTransaction)).code should equal(400)

    //transactions shouldn't exist
    checkNoTransactionsExist()

    //it should work if we make the counterparty valid again
    val anotherValidTransaction = replaceField(validTransaction, List("counterparty", "bank"), t.counterparty.get.bank)

    getResponse(List(validTransaction, anotherValidTransaction)).code should equal(201)
    checkTransactionsExist()
  }

  it should "not create any transactions when one has an invalid or missing value" in {
    val (banks, users, accounts) = (standardBanks, standardUsers, standardAccounts)

    def getResponse(transactionJsons : List[JValue]) = {
      val json = createImportJson(banks.map(Extraction.decompose),
        users.map(Extraction.decompose), accounts.map(Extraction.decompose), transactionJsons)
      postImportJson(json)
    }

    val t = transactionWithCounterparty

    val validTransaction = Extraction.decompose(t)

    val newTransId = "0239403294322343"
    newTransId should not equal(t.id)

    val baseNewTransaction = replaceField(validTransaction, "id", newTransId)

    val transactionWithoutValue = removeField(baseNewTransaction, List("details", "value"))

    //shouldn't work
    getResponse(List(validTransaction, transactionWithoutValue)).code should equal(400)

    def checkNoTransactionsExist() = checkTransactions(false)
    def checkTransactionsExist() = checkTransactions(true)

    def checkTransactions(exist: Boolean) = {
      Connector.connector.vend.getTransaction(BankId(t.this_account.bank),
        AccountId(t.this_account.id),
        TransactionId(t.id)).isDefined should equal(exist)

      Connector.connector.vend.getTransaction(BankId(t.this_account.bank),
        AccountId(t.this_account.id),
        TransactionId(newTransId)).isDefined should equal(exist)
    }

    //no transactions should be created
    checkNoTransactionsExist()

    //check transaction with bad value
    val transactionWithBadValue = replaceField(baseNewTransaction, List("details", "value"), "ABCD")

    //shouldn't work
    getResponse(List(validTransaction, transactionWithBadValue)).code should equal(400)
    checkNoTransactionsExist()

    //now make sure it works with a good value
    val transactionWithGoodValue = replaceField(baseNewTransaction, List("details", "value"), "-34.65")

    getResponse(List(validTransaction, transactionWithGoodValue)).code should equal(201)
    checkTransactionsExist()
  }

  it should "not create any transactions when one has an invalid or missing completed date" in {
    val (banks, users, accounts) = (standardBanks, standardUsers, standardAccounts)

    def getResponse(transactionJsons : List[JValue]) = {
      val json = createImportJson(banks.map(Extraction.decompose),
        users.map(Extraction.decompose), accounts.map(Extraction.decompose), transactionJsons)
      postImportJson(json)
    }

    val t = transactionWithCounterparty

    val validTransaction = Extraction.decompose(t)

    val newTransId = "0239403294322343"
    newTransId should not equal(t.id)

    def checkNoTransactionsExist() = checkTransactions(false)
    def checkTransactionsExist() = checkTransactions(true)

    def checkTransactions(exist: Boolean) = {
      Connector.connector.vend.getTransaction(BankId(t.this_account.bank),
        AccountId(t.this_account.id),
        TransactionId(t.id)).isDefined should equal(exist)

      Connector.connector.vend.getTransaction(BankId(t.this_account.bank),
        AccountId(t.this_account.id),
        TransactionId(newTransId)).isDefined should equal(exist)
    }

    val baseNewTransaction = replaceField(validTransaction, "id", newTransId)

    val transactionWithMissingCompleted = removeField(baseNewTransaction, List("details", "completed"))

    //shouldn't work
    getResponse(List(validTransaction, transactionWithMissingCompleted)).code should equal(400)
    checkNoTransactionsExist()

    //check transaction with bad completed date
    val transactionWithBadCompleted = replaceField(baseNewTransaction, List("details", "completed"), "ASDF")

    //shouldn't work
    getResponse(List(validTransaction, transactionWithBadCompleted)).code should equal(400)
    checkNoTransactionsExist()

    //now make sure it works with a valid completed date
    val transactionWithGoodcompleted = replaceField(baseNewTransaction, List("details", "completed"), "2016-11-07T05:25:33.001Z")

    //should work
    getResponse(List(validTransaction, transactionWithGoodcompleted)).code should equal(201)
    checkTransactionsExist()
  }

  it should "check that counterparty specified is not generated if it already exists (for the original account in question)" in {
    val (banks, users, accounts) = (standardBanks, standardUsers, standardAccounts)

    def getResponse(transactionJsons : List[JValue]) = {
      val json = createImportJson(banks.map(Extraction.decompose),
        users.map(Extraction.decompose), accounts.map(Extraction.decompose), transactionJsons)
      postImportJson(json)
    }

    val t = transactionWithCounterparty

    val validTransaction = Extraction.decompose(t)

    val newTransId = "0239403294322343"
    newTransId should not equal(t.id)

    val transactionWithSameCounterparty = replaceField(validTransaction, "id", newTransId)

    getResponse(List(validTransaction, transactionWithSameCounterparty)).code should equal(201)

    def getCreatedTransaction(id : String) =
      Connector.connector.vend.getTransaction(BankId(t.this_account.bank),
        AccountId(t.this_account.id),
        TransactionId(id)).get

    val t1 = getCreatedTransaction(t.id)
    val t2 = getCreatedTransaction(newTransId)

    //check the created transactions have the same counterparty id
    t1.otherAccount.id should equal(t2.otherAccount.id)
  }

}
