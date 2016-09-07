package code.sandbox

/**
Open Bank Project - API
Copyright (C) 2011-2016, TESOBE Ltd.

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
* It requires the credentials of the user and logs in via OAuth using selenium.
* TODO Move out of test - or into a separate project
*
* To use this one-time script, put e.g.
* target_api_hostname=https://localhost:8080
* obp_consumer_key=xxx
* obp_secret_key=yyy
*
* into your props file.
* */

import java.util.Date

import code.api.ObpJson._
import code.api.{SendServerRequests, _}
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.http.RequestVar
import net.liftweb.json._

import scala.collection.mutable.ListBuffer
import scala.io.Source

case class CustomerFullJson(customer_number : String,
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




// Copied from 1.4 API
case class CustomerFaceImageJson(url : String, date : Date)






// Post customer data
// Instructions for using this:
// Run a copy of the API (here or somewhere else)
// Set the paths for users and counterparties.

// TODO Extract this into a separate application.

object PostCustomer extends SendServerRequests {


  def debugBreak() {
    println("Breakpoint hit!") // Manually set a breakpoint here
  }




  def main(args : Array[String]) {


    // this sets the date format to "yyyy-MM-dd'T'HH:mm:ss'Z'" i.e. ISO 8601 No milliseconds UTC
    implicit val formats = DefaultFormats // Brings in default date formats etc.


    //load json for customers
    val customerDataPath = "/Users/simonredfern/Documents/OpenBankProject/DATA/API_sandbox/unicredit_to_load_04/OBP_sandbox_customers_pretty.json"

    // This contains a list of customers.
    val customerListData = JsonParser.parse(Source.fromFile(customerDataPath) mkString)
    var customers = ListBuffer[CustomerFullJson]()


    // Get customers from json
    for(i <- customerListData.children){
        //logger.info(s" extract customer records")
        val c = i.extract[CustomerFullJson]
        println(c.customer_number + "  " + c.email)
        customers.append(c)

    }


    println("Got " + customers.length + " records")

    //load sandbox users from json

    val mainDataPath = "/Users/simonredfern/Documents/OpenBankProject/DATA/API_sandbox/unicredit_to_load_04/OBP_sandbox_pretty.json"

    val mainData = JsonParser.parse(Source.fromFile(mainDataPath) mkString)
    val users = (mainData \ "users").children
    println("got " + users.length + " users")

    object allBanksVar extends RequestVar[Box[BanksJson]] (Empty)

    def allBanks : Box[BanksJson]= {
      allBanksVar.get match {
        case Full(a) => Full(a)
        case _ => ObpGet("/v1.2/banks").flatMap(_.extractOpt[BanksJson]) // TODO use more recent API version
      }
    }

    case class SimpleBank(
                     id : String,
                     shortName : String,
                     fullName : String,
                     logo : String,
                     website : String)


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
        b.website.getOrElse("")) // Add a flag to say if this bank is featured.


    //loop over users from json
    for (u <- users) {
      val user = u.extract[UserJSONRecord]
      println(" ")
      print("login as user: ")

      println (user.email + " - " + user.password)

      if(!OAuthClient.loggedIn) {
        OAuthClient.authenticateWithOBPCredentials(user.email, user.password)
        //println(" - ok.")
      }

      val customer = customers.filter(x => ( x.email == user.email))

      println(s"we got customer that matches ")

      customer.foreach(c =>  {
        println (s"email is ${c.email} has ${c.dependants} dependants born on ${c.dob_of_dependants.map(d => s"${d}")} ")

        // We are able to post this (no need to convert to string explicitly)
        val json = Extraction.decompose(c)

        // For now, create a customer
        for (b <- banks) {
          val url = s"/v2.0.0/banks/${b.id}/customers"
          val result = ObpPost(url, json)
          if (!result.isEmpty) {
            println("saved " + c.customer_number + " as customer " + result)
          } else {
            println("did NOT save customer " + result)
          }
        }

      })

      OAuthClient.logoutAll()
    }

    sys.exit(0)
  }
}
