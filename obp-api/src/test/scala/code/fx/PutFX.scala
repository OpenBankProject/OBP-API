package code.fx

/**
Open Bank Project - API
Copyright (C) 2011-2018, TESOBE Ltd.

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
TESOBE Ltd.
Osloer Strasse 16/17
Berlin 13359, Germany

This product includes software developed at
TESOBE (http://www.tesobe.com/)

  */

/*
* This is a utility script that can be used to POST data via the API as a logged-in User.
* It POSTS customers and links them to existing Users
* It requires the credentials of the user and logs in via OAuth using selenium.
*
* We use an "admin user" e.g. a user which has been assigned certain roles to perform the actions.
* The roles required include CanGetAnyUser, CanCreateCustomerAtAnyBank , CanCreateUserCustomerLinkAtAnyBank
*
* To use this one-time script, put e.g.
* target_api_hostname=https://localhost:8080
* obp_consumer_key=xxx
* obp_secret_key=yyy
* import.fx_data_path=path_to.json
* import.admin_user.username=username-of-user-that-has-correct-roles
* import.admin_user.password=password
*
* into your props file.
* */

import java.util.Date

import code.api.util.{APIUtil, CustomJsonFormats}
import code.api.v2_2_0.FXRateJsonV220
import code.setup.SendServerRequests
import code.util.ObpJson._
import code.util.{OAuthClient, ObpGet, ObpPut}
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.http.RequestVar
import net.liftweb.json._
import net.liftweb.util.Props

import scala.collection.mutable.ListBuffer
import scala.io.Source


case class FxJson(from_currency_code: String,
                  to_currency_code: String,
                  conversion_value: Double,
                  inverse_conversion_value: Double,
                  effective_date: Date)

object PutFX extends SendServerRequests {


  def debugBreak() {
    println("Breakpoint hit!") // Manually set a breakpoint here
  }

  def main(args : Array[String]) {

    // this sets the date format to "yyyy-MM-dd'T'HH:mm:ss'Z'" i.e. ISO 8601 No milliseconds UTC
    implicit val formats = CustomJsonFormats.formats // Brings in default date formats etc.

    val adminUserUsername = APIUtil.getPropsValue("import.admin_user.username").getOrElse("ERROR")
    println(s"adminUserUsername is $adminUserUsername")

    val adminUserPassword = APIUtil.getPropsValue("import.admin_user.password").getOrElse("ERROR")
    println(s"adminUserPassword is $adminUserPassword")

    //println("Got " + customers.length + " records")

    object allBanksVar extends RequestVar[Box[BanksJson]] (Empty)

    def allBanks : Box[BanksJson]= {
      allBanksVar.get match {
        case Full(a) => Full(a)
        case _ => ObpGet("/v1.2.1/banks").flatMap(_.extractOpt[BanksJson]) // TODO use more recent API version
      }
    }

    case class SimpleBank(
                           id : String,
                           shortName : String,
                           fullName : String,
                           logo : String,
                           website : String)


    // Login once as an admin user. Will need to have some admin Roles
    if(!OAuthClient.loggedIn) {
      print("login as user: ")
      println (adminUserUsername)
      OAuthClient.authenticateWithOBPCredentials(adminUserUsername, adminUserPassword)
      println(" - ok.")
    }


    val banks = for {
      a <- allBanks.toList
      b <- a.bankJsons
      // This filtering could be turned on/off by Props setting
      // Filter out banks if we have a list of ones to use, else use all of them.
      // Also, show all if requested by url parameter
      // if featuredBankIds.length == 0  || featuredBankIds.contains(b.id.get)  || listAllBanks
    } yield SimpleBank (b.id.get,
      b.short_name.getOrElse(""),
      b.full_name.getOrElse(""),
      b.logo.getOrElse(""),
      b.website.getOrElse("")
    ) // Add a flag to say if this bank is featured.

    for (b <- banks) { // (b.shortName == "uk")
      println(s"Posting FX Rate for bank ${b.shortName}")

      val url = s"/v3.0.0/banks/${b.id}/fx"

      //load json for fx rates
      val fxDataPath = APIUtil.getPropsValue("import.fx_data_path")

      println(s"fxDataPath is $fxDataPath")

      // This contains a list of fx rates.
      val fxListData = JsonParser.parse(Source.fromFile(fxDataPath.getOrElse("ERROR")).mkString)

      var fxrates = ListBuffer[FxJson]()

      // Get fx rate data from json
      for(i <- fxListData.children){
        //logger.info(s" extract fx rate records")
        val f = i.extract[FxJson]
        val fxJsonV210 = FXRateJsonV220(
          bank_id = b.id,
          from_currency_code = f.from_currency_code,
          to_currency_code = f.to_currency_code,
          conversion_value = f.conversion_value,
          inverse_conversion_value = f.inverse_conversion_value,
          effective_date = f.effective_date
        )

        val json = Extraction.decompose(fxJsonV210)
        println(s"json to post is $json")

        val result = ObpPut(url, json)

        if (!result.isEmpty) {
          println("saved " + f.from_currency_code + " to " + f.to_currency_code + " as currency exchange rate " + result)
        } else {
          println("did NOT save fx rate " + result)
        }

      }


      //OAuthClient.logoutAll()
    }

    OAuthClient.logoutAll()
    sys.exit(0)
  }
}