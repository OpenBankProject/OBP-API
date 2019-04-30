package code.sandbox

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
* import.main_data_path=path_to.json
* import.customer_data_path=path_to.json
* import.admin_user.username=username-of-user-that-has-correct-roles
* import.admin_user.password=password
*
* into your props file.
* */

import java.util.{Date, UUID}

import code.api.util.{APIUtil, CustomJsonFormats}
import com.openbankproject.commons.model.AmountOfMoneyJsonV121
import code.api.v1_4_0.JSONFactory1_4_0.CustomerFaceImageJson
import code.api.v2_0_0.JSONFactory200.UserJsonV200
import code.api.v2_1_0.{CustomerCreditRatingJSON, PostCustomerJsonV210}
import code.setup.SendServerRequests
import code.util.ObpJson._
import code.util.{OAuthClient, ObpGet, ObpPost}
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

object PostCustomer extends SendServerRequests {


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

    //load json for customers
    val customerDataPath = APIUtil.getPropsValue("import.customer_data_path")

    println(s"customerDataPath is $customerDataPath")

    // This contains a list of customers.
    val customerListData = JsonParser.parse(Source.fromFile(customerDataPath.getOrElse("ERROR")).mkString)

    var customers = ListBuffer[CustomerFullJson]()

    println(s"We have the following customer numbers, emails:")

    // Get customers from json
    for(i <- customerListData.children){
        //logger.info(s" extract customer records")
        val c = i.extract[CustomerFullJson]
        println(c.customer_number + ", " + c.email)
        customers.append(c)

    }


    println("Got " + customers.length + " records")

    //load sandbox users from json

    val mainDataPath = APIUtil.getPropsValue("import.main_data_path")

    println(s"mainDataPath is $mainDataPath")

    val mainData = JsonParser.parse(Source.fromFile(mainDataPath.getOrElse("ERROR")).mkString)

    val users = (mainData \ "users").children
    println("got " + users.length + " users")

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


    // Loop over the users found in the json
    for (u <- users) {
      val user = u.extract[UserJSONRecord]
      println(" ")

      val filteredCustomers = customers.filter(x => ( x.email == user.email))

      println(s"we got ${filteredCustomers.length} filtered customers by email ")

      filteredCustomers.foreach(c =>  { //(c.customer_number == "westpac1638421674")

        println (s"   email is ${c.email} customer number is ${c.customer_number} name is ${c.legal_name} and has ${c.dependants} dependants born on ${c.dob_of_dependants.map(d => s"${d}")} ")

          // We are able to post this (no need to convert to string explicitly)
          val json = Extraction.decompose(c)

          // Create Customer for Each bank
          for (b <- banks) { // (b.shortName == "uk")

              println(s"Posting a customer for bank ${b.shortName}")

              val url = s"/v2.1.0/banks/${b.id}/customers"

              val customerId: String = APIUtil.generateUUID()

              val customerFaceImageJson = CustomerFaceImageJson(url = c.face_image.url, date = c.face_image.date)

              // Get user_id
              var currentUser =  ObpGet(s"/v3.0.0/users/username/${user.user_name}").flatMap(_.extractOpt[UserJsonV200])

              val customerCreditRatingJSON: CustomerCreditRatingJSON = CustomerCreditRatingJSON(rating = "A", source = "Unknown")
              val creditLimit: AmountOfMoneyJsonV121 = AmountOfMoneyJsonV121(currency="AUD", amount="3000.00")

              val cucstomerJsonV210 =
                PostCustomerJsonV210(
                  user_id = currentUser.openOrThrowException("User not found by username").user_id,
    customer_number = c.customer_number,
    legal_name = c.legal_name,
    mobile_phone_number = c.mobile_phone_number,
    email = c.email,
    face_image = customerFaceImageJson,
    date_of_birth = c.date_of_birth,
    relationship_status = c.relationship_status,
    dependants = c.dependants,
    dob_of_dependants = c.dob_of_dependants,
    credit_rating = customerCreditRatingJSON,  // Option[CustomerCreditRatingJSON],
    credit_limit = creditLimit,
    highest_education_attained = c.highest_education_attained,
    employment_status = c.employment_status,
    kyc_status = c.kyc_status,
    last_ok_date = c.last_ok_date)



              val json = Extraction.decompose(cucstomerJsonV210)

              println(s"json to post is $json")

              val lala = prettyRender(json)

              println(s"lala to post is $lala")

              val result = ObpPost(url, json)

              if (!result.isEmpty) {
                println("saved " + c.customer_number + " as customer " + result)
              } else {
                println("did NOT save customer " + result)
              }

          }

      })

      //OAuthClient.logoutAll()
    }

    OAuthClient.logoutAll()
    sys.exit(0)
  }
}
