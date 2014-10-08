package code.sandbox

import code.api.test.ServerSetup
import dispatch._
import net.liftweb.util.Props

class SandboxDataLoadingTest extends ServerSetup {

  val validBank1 =
    """{"id": "example-bank1",
      |"short_name": "ExBank1",
      |"full_name": "Example Bank 1 of Examplonia",
      |"logo": "http://example.com/logo",
      |"website: "http://example.com"}""".stripMargin
  val validBank2 =
    """{"id": "example-bank2",
      |"short_name": "ExBank2",
      |"full_name": "Example Bank 2 of Examplonia",
      |"logo": "http://example.com/logo",
      |"website: "http://example.com"}""".stripMargin
  val bankWithSameIdAsValidBank1 =
    """{"id": "example-bank1",
      |"short_name": "ExB",
      |"full_name": "An imposter Example Bank of Examplonia",
      |"logo": "http://example.com/logo",
      |"website: "http://example.com"}""".stripMargin
  val bankWithoutId =
    """{"short_name": "ExBank1",
      |"full_name": "Example Bank 1 of Examplonia",
      |"logo": "http://example.com/logo",
      |"website: "http://example.com"}""".stripMargin
  val bankWithEmptyId =
    """{"id": "",
      |"short_name": "ExBank1",
      |"full_name": "Example Bank 1 of Examplonia",
      |"logo": "http://example.com/logo",
      |"website: "http://example.com"}""".stripMargin

  val validUser1 = """{"id": "jeff@example.com", "display_name": "Jeff Bloggs"}"""
  val validUser2 = """{"id": "bob@example.com", "display_name": "Bob Bloggs"}"""
  val userWithSameIdAsValidUser1 = """{"id": jeff@example.com", "display_name": "Jeff Bloggs Number 2"}"""
  val userWithoutId = """{"display_name": "Jeff Bloggs"}"""
  val userWithEmptyId = """{"id": "", "display_name": "Jeff Bloggs"}"""

  def toJsonArray(xs : List[String]) : String = {
    xs.mkString("[", ",", "]")
  }

  def createImportJson(banks: List[String], users: List[String], accounts : List[String], transactions : List[String]) : String = {
    s"""
       |"banks" : ${toJsonArray(banks)},
       |"users" : ${toJsonArray(users)},
       |"accounts" : ${toJsonArray(accounts)},
       |"transactions" : ${toJsonArray(transactions)}
     """.stripMargin
  }


  feature("Adding sandbox test data") {

    scenario("We try to import valid data with a secret token") {
      //TODO: check that everything gets created, good response code
    }

    scenario("We try to import valid data without a secret token") {
      //TODO: check we get an error and correct http code
    }

    scenario("We define a bank without an id") {
      //TODO: it shouldn't work
    }

    scenario("We define a bank with an empty id") {
      //TODO: it shouldn't work
    }

    scenario("We define more than one bank with the same id") {
      //TODO: it shouldn't work
    }

    scenario("We define a user without an id") {
      //TODO: it shouldn't work
    }

    scenario("We define a user with an empty id") {
      //TODO: it shouldn't work
    }

    scenario("We define more than one user with the same id") {
      //TODO: it shouldn't work
    }

    scenario("We define an account without an id") {
      //TODO: it shouldn't work
    }

    scenario("We define an account with an empty id") {
      //TODO: it shouldn't work
    }

    scenario("We define more than one account with the same id") {
      //TODO: it shouldn't work
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
