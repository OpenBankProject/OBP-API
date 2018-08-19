///**
//Open Bank Project - API
//Copyright (C) 2011-2018, TESOBE Ltd
//
//This program is free software: you can redistribute it and/or modify
//it under the terms of the GNU Affero General Public License as published by
//the Free Software Foundation, either version 3 of the License, or
//(at your option) any later version.
//
//This program is distributed in the hope that it will be useful,
//but WITHOUT ANY WARRANTY; without even the implied warranty of
//MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//GNU Affero General Public License for more details.
//
//You should have received a copy of the GNU Affero General Public License
//along with this program.  If not, see <http://www.gnu.org/licenses/>.
//
//Email: contact@tesobe.com
//TESOBE Ltd
//Osloerstrasse 16/17
//Berlin 13359, Germany
//
//  This product includes software developed at
//  TESOBE (http://www.tesobe.com/)
//  by
//  Simon Redfern : simon AT tesobe DOT com
//  Stefan Bethge : stefan AT tesobe DOT com
//  Everett Sochowski : everett AT tesobe DOT com
//  Ayoub Benali: ayoub AT tesobe DOT com
//
// */
//package code.external
//
//import java.net.URL
//
//import code.api.v1_2._
//import code.api.v3_0_0.V300ServerSetup
//import code.setup.{APIResponse, DefaultUsers, User1AllPrivileges}
//import net.liftweb.json._
//import net.liftweb.util.Helpers._
//import org.scalatest._
//
//import scala.io.Source
//
//
//class API3_0_0Test extends User1AllPrivileges with V300ServerSetup with DefaultUsers {
//
//  /************************* test tags ************************/
//
//  /**
//   * Example: To run tests with tag "ExternalTest":
//   * 	mvn test -D ExternalTest
//   *
//   *  This is made possible by the scalatest maven plugin
//   */
//  object ExternalTest extends Tag("code.external")
//
//
//
//  /********************* API test methods ********************/
//
//  def getAPIInfo : APIResponse = {
//    val request = v3_0RequestExternal
//    makeGetRequest(request)
//  }
//
//  def getBanksInfo : APIResponse  = {
//    val request = v3_0RequestExternal / "banks"
//    makeGetRequest(request)
//  }
//
//  def getBankInfo(bankId : String) : APIResponse  = {
//    val request = v3_0RequestExternal / "banks" / bankId
//    makeGetRequest(request)
//  }
//
//
//
//  feature("base line URL works"){
//    scenario("we get the api information", ExternalTest) {
//      Given("We will not use an access token")
//      When("the request is sent")
//      val reply = getAPIInfo
//      Then("we should get a 200 ok code")
//      reply.code should equal (200)
//      val apiInfo = reply.body.extract[APIInfoJSON]
//      apiInfo.version should equal ("v3.0.0")
//    }
//  }
//
//  feature("Information about the hosted banks"){
//
//    var banksIds:List[String] = Nil
//
//    scenario("We get the hosted banks information", ExternalTest) {
//      Given("We will not use an access token")
//      When("the request is sent")
//      val reply: APIResponse = getBanksInfo
//      Then("we should get a 200 ok code")
//      reply.code should equal (200)
//      val banksInfo = reply.body.extract[code.api.v1_2_1.BanksJSON]
//      banksInfo.banks.foreach(b => {
//        b.id.nonEmpty should equal (true)
//      })
//
//      // Create json file and store all banks
//      import java.io._
//      val pw = new PrintWriter(new File(server.externalHost.getOrElse("") + "-banks.json"))
//      pw.write(prettyRender(reply.body))
//      pw.close
//
//      // Parse response to get list of bank ids
//      banksIds = for {
//        JArray(banks) <- reply.body \\ "banks"
//        JObject(bank) <- banks
//        JField("id", JString(id)) <- bank
//      } yield id
//
//
//      // Try to get one by one bank from the list
//      for(bankId <- banksIds){
//        val reply = getBankInfo(bankId)
//        Then("we should get a 200 ok code")
//        reply.code should equal (200)
//        val bankInfo = reply.body.extract[code.api.v1_2_1.BankJSON]
//        bankInfo.id.nonEmpty should equal (true)
//      }
//
//      // Get data from internal json file in order to compare they with external obtained data
//      val nameOfJsonFile: String = server.externalHost.getOrElse("") + ".json"
//      val x: URL = getClass().getClassLoader().getResource(nameOfJsonFile)
//      val dataAsJsonString: String = Source.fromURL(x).mkString
//      val dataAsJsonAST = parse(dataAsJsonString)
//
//      val banksIdsFromJson = for {
//        JArray(banks) <- dataAsJsonAST \\ "banks"
//        JObject(bank) <- banks
//        JField("id", JString(id)) <- bank
//      } yield id
//
//      Then("Bank ids from internal json file have to be the same as external data")
//      banksIdsFromJson should equal(banksIds)
//    }
//  }
//
//  feature("Information about one hosted bank"){
//    scenario("we don't get the hosted bank information", ExternalTest) {
//      Given("We will not use an access token and request a random bankId")
//      When("the request is sent")
//      val reply = getBankInfo(randomString(10))
//      Then("we should get a 400 code")
//      reply.code should equal (400)
//      And("we should get an error message")
//      reply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
//    }
//  }
//
//
//}
