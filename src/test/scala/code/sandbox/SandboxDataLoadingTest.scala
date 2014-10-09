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

import code.TestServer
import code.api.test.{SendServerRequests, APIResponse}
import code.model.BankId
import code.users.Users
import dispatch._
import net.liftweb.json.JsonAST.JObject
import net.liftweb.util.Props
import org.scalatest.{ShouldMatchers, FlatSpec}
import net.liftweb.json.JsonDSL._
import net.liftweb.json.{compact, render}
import code.bankconnectors.Connector
import net.liftweb.common.{Empty}

class SandboxDataLoadingTest extends FlatSpec with SendServerRequests with ShouldMatchers{

  val server = TestServer
  def baseRequest = host(server.host, server.port)

  def sandboxApiPrefix = baseRequest / "obp" / "vsandbox"

  //users should automatically be assigned the "hostname" as a provider (for now at least)
  val defaultProvider = Props.get("hostname").openOrThrowException("no hostname set")

  def toJsonArray(xs : List[String]) : String = {
    xs.mkString("[", ",", "]")
  }

  def createImportJson(banks: List[JObject], users: List[JObject], accounts : List[JObject], transactions : List[JObject]) : String = {
    val json =
      ("banks" -> banks) ~
      ("users" -> users) ~
      ("accounts" -> accounts) ~
      ("transactions" -> transactions)
    compact(render(json))
  }

  // posts the json with the correct secret token
  def postImportJson(json : String) : APIResponse = {
    postImportJson(json, Some(Props.get("sandbox_data_import_secret").openOrThrowException("sandbox_data_import_secret not set")))
  }

  def postImportJson(json : String, secretToken : Option[String]) : APIResponse = {
    val base = sandboxApiPrefix / "data-import"
    val request = secretToken match {
      case Some(t) => base << Map("secret_token" -> t)
      case None => base
    }
    makePostRequest(request, json)
  }

  "Data import" should "require banks to have non-empty ids" in {

    //no banks should exist initially
    Connector.connector.vend.getBanks.size should equal(0)

    val validId = "example-bank"
    val shortName = "ExBank1"
    val fullName = "Example Bank of Examplonia"
    val logo = "http://example.com/logo"
    val website = "http://example.com"

    val bankWithoutId =
      ("short_name" -> shortName) ~
      ("full_name" -> fullName) ~
      ("logo" -> logo) ~
      ("website" -> website)

    def getResponse(bankJson : JObject) = {
      val json = createImportJson(List(bankJson), Nil, Nil, Nil)
      postImportJson(json)
    }

    getResponse(bankWithoutId).code should equal(400)

    //no banks should have been created
    Connector.connector.vend.getBanks.size should equal(0)

    val bankWithEmptyId = bankWithoutId ~ ("id" -> "")
    getResponse(bankWithEmptyId).code should equal(400)

    //no banks should have been created
    Connector.connector.vend.getBanks.size should equal(0)

    //Check that the same json become valid when a non-empty id is added
    val bankWithValidId = bankWithoutId ~ ("id" -> validId)
    val response = getResponse(bankWithValidId)
    response.code should equal(201)

    //Check the bank was created
    val banks = Connector.connector.vend.getBanks
    banks.size should equal(1)
    val createdBank  = banks(0)

    createdBank.id should equal(BankId(validId))
    createdBank.shortName should equal(shortName)
    createdBank.fullName should equal(fullName)
    createdBank.logoURL should equal(logo)
    createdBank.website should equal(website)
  }

  it should "not allow multiple banks with the same id" in {
    //no banks should exist initially
    Connector.connector.vend.getBanks.size should equal(0)

    val id = "example-bank"
    val shortName = "ExBank1"
    val fullName = "Example Bank of Examplonia"
    val logo = "http://example.com/logo"
    val website = "http://example.com"

    val validBank =
      ("id" -> id) ~
      ("short_name" -> shortName) ~
      ("full_name" -> fullName) ~
      ("logo" -> logo) ~
      ("website" -> website)


    val baseOtherBank =
      ("short_name" -> {shortName + "2"}) ~
      ("full_name" -> {fullName + "2"}) ~
      ("logo" -> {logo + "2"}) ~
      ("website" -> {website + "2"})

    //same id, but different other attributes
    val bankWithSameId =
      ("id" -> id) ~
      baseOtherBank

    def getResponse(bankJsons : List[JObject]) = {
      val json = createImportJson(bankJsons, Nil, Nil, Nil)
      postImportJson(json)
    }

    getResponse(List(validBank, bankWithSameId)).code should equal(400)

    //now try again but this time with a different id
    val validOtherBank =
      ("id" -> {id + "2"}) ~
      baseOtherBank

    getResponse(List(validBank, validOtherBank)).code should equal(201)

    //check that two banks were created
    val banks = Connector.connector.vend.getBanks
    banks.size should equal(2)
  }

  it should "require users to have non-empty ids" in {

    def getResponse(userJson : JObject) = {
      val json = createImportJson(Nil, List(userJson), Nil, Nil)
      postImportJson(json)
    }

    val userWithoutId : JObject = ("display_name" -> "Ralph Bloggs")

    getResponse(userWithoutId).code should equal(400)

    val userWithEmptyId = ("id" -> "") ~ userWithoutId

    //there should be no user with a blank id before we try to add one
    Users.users.vend.getUserByProviderId(defaultProvider, "") should equal(Empty)

    getResponse(userWithEmptyId).code should equal(400)

    //there should still be no user with a blank id
    Users.users.vend.getUserByProviderId(defaultProvider, "") should equal(Empty)

    val validId = "some-valid-id"
    val userWithValidId = ("id" -> validId) ~ userWithoutId

    getResponse(userWithValidId).code should equal(201)

    //a user should now have been created
    val createdUser = Users.users.vend.getUserByProviderId(defaultProvider, validId)
    createdUser.isDefined should be true
    createdUser.get.provider should equal(defaultProvider)
    createdUser.get.idGivenByProvider should equal(validId)

  }

  it should "not allow multiple users with the same id" in {

    def getResponse(userJsons : List[JObject]) = {
      val json = createImportJson(Nil, userJsons, Nil, Nil)
      postImportJson(json)
    }

    //ids of the two users we will eventually create to show multiple users with different ids are possible
    val firstUserId = "user-one"
    val secondUserId = "user-two"

    val displayNameKey = "display_name"
    val displayNameVal = "Jessica Bloggs"
    val displayNameParam : JObject = (displayNameKey -> displayNameVal)

    //neither of these users should exist initially
    Users.users.vend.getUserByProviderId(defaultProvider, firstUserId) should equal(Empty)
    Users.users.vend.getUserByProviderId(defaultProvider, secondUserId) should equal(Empty)

    val firstUserIdParam : JObject = ("id" -> firstUserId)
    val userWithId1 = firstUserIdParam ~ displayNameParam
    val anotherUserWithId1 = firstUserIdParam ~ displayNameParam

    getResponse(List(userWithId1, anotherUserWithId1)).code should equal(400)

    //no user with firstUserId should be created
    Users.users.vend.getUserByProviderId(defaultProvider, firstUserId) should equal(Empty)

    //when we only alter the id (display name stays the same), it should work
    val userWithId2 = ("id" -> secondUserId) ~ displayNameParam

    getResponse(List(userWithId1, userWithId2)).code should equal(200)

    //and both users should be created
    val user1 = Users.users.vend.getUserByProviderId(defaultProvider, firstUserId)
    val user2 = Users.users.vend.getUserByProviderId(defaultProvider, secondUserId)

    user1.isDefined should be true
    user2.isDefined should be true

    user1.get.idGivenByProvider should equal(firstUserId)
    user2.get.idGivenByProvider should equal(secondUserId)

    user1.get.name should equal(displayNameVal)
    user2.get.name should equal(displayNameVal)
  }

  feature("Adding sandbox test data") {

    scenario("We try to import valid data with a secret token") {
      //TODO: check that everything gets created, good response code
    }

    scenario("We try to import valid data without a secret token") {
      //TODO: check we get an error and correct http code
    }

    scenario("We try to import valid data with an invalid secret token") {
      //TODO: check we get an error and correct http code
    }

    scenario("We define more than one bank with the same id") {
      val validBank =
        """{"id": "example-bank1",
          |"short_name": "ExBank1",
          |"full_name": "Example Bank 1 of Examplonia",
          |"logo": "http://example.com/logo",
          |"website: "http://example.com"}""".stripMargin
      val bankWithSameIdAsValidBank =
        """{"id": "example-bank1",
          |"short_name": "ExB",
          |"full_name": "An imposter Example Bank of Examplonia",
          |"logo": "http://example.com/logo",
          |"website: "http://example.com"}""".stripMargin

      val importJson = createImportJson(List(validBank, bankWithSameIdAsValidBank), Nil, Nil, Nil)
      val response = postImportJson(importJson)
      response.code should equal(400)
    }

    scenario("We define a user without an id") {
      val userWithoutId = """{"display_name": "Jeff Bloggs"}"""

      val importJson = createImportJson(Nil, List(userWithoutId), Nil, Nil)
      val response = postImportJson(importJson)
      response.code should equal(400)
    }

    scenario("We define a user with an empty id") {
      val userWithEmptyId = """{"id": "", "display_name": "Jeff Bloggs"}"""

      val importJson = createImportJson(Nil, List(userWithEmptyId), Nil, Nil)
      val response = postImportJson(importJson)
      response.code should equal(400)
    }

    scenario("We define more than one user with the same id") {
      val validUser = """{"id": "jeff@example.com", "display_name": "Jeff Bloggs"}"""
      val userWithSameIdAsValidUser = """{"id": jeff@example.com", "display_name": "Jeff Bloggs Number 2"}"""

      val importJson = createImportJson(Nil, List(validUser, userWithSameIdAsValidUser), Nil, Nil)
      val response = postImportJson(importJson)
      response.code should equal(400)
    }

    scenario("We define an account without an id") {
      val bankId = "test"
      val bank = importBankJson(bankId)
      val ownerId = "foobar@example.com"
      val owner = importUserJson(ownerId)


      val accountWithoutId =
        s"""{
          |"bank" : ${bankId},
          |"label" : "Foo",
          |"number" : "23432432",
          |"type" : "savings",
          |"balance" : {
          |  "currency" : "EUR",
          |  "amount" : "23.54"
          |},
          |"IBAN" : "1231321321321",
          |"owners" : ${toJsonArray(List(ownerId))}
          |}
        """.stripMargin

      //TODO: set up users and banks
      val importJson = createImportJson(Nil, Nil, List(accountWithoutId), Nil)
      val response = postImportJson(importJson)
      response.code should equal(400)
    }

    scenario("We define an account with an empty id") {
      //TODO: set up users and banks
      val importJson = createImportJson(Nil, Nil, List(accountWithEmptyId), Nil)
      val response = postImportJson(importJson)
      response.code should equal(400)
    }

    scenario("We define more than one account with the same id") {
      //TODO: set up users and banks
      val importJson = createImportJson(Nil, Nil, List(validAccount, accountWithSameIdAsValidAccount), Nil)
      val response = postImportJson(importJson)
      response.code should equal(400)
    }

    scenario("We define an account with a public view") {
      //TODO: it should work
    }

    scenario("We define an account without a public view") {
      //TODO: it should work
    }

    scenario("We define an account with an invalid bank") {
      //TODO: it shouldn't work
    }

    scenario("We define accounts with valid banks") {
      //TODO: it should work + check everything is created, owners are set correctly, etc.
    }

    scenario("We define an account without an owner") {
      //TODO: it shouldn't work
    }

    scenario("We define an account with an invalid owner") {
      //TODO: it shouldn't work
    }

    scenario("We define an account with a valid owner") {
      //TODO: it should work
    }

    scenario("We define an account with a mixture of valid and invalid owners") {
      //TODO: it shouldn't work
    }

    scenario("We define an account with more than one owners") {
      //TODO: it should work
    }

    scenario("We define a transaction without an id") {
      //TODO: it shouldn't work
    }

    scenario("We define a transaction without an empty id") {
      //TODO: it shouldn't work
    }

    scenario("We define more than one transaction with the same id") {
      //TODO: it shouldn't work
    }

    scenario("We define a transaction with an invalid this_account") {
      //TODO: it shouldn't work
    }

    scenario("We define a transaction with an invalid counterparty") {
      //TODO: it shouldn't work
    }

    scenario("We define a transaction without a counterparty") {
      //TODO: it should work, and automatically generate a counterparty
    }

    scenario("We define a transaction without a value") {
      //TODO: it shouldn't work
    }

    scenario("We define a transaction without a completed date") {
      //TODO: it shouldn't work
    }

  }

}
