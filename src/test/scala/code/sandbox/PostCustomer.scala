package code.sandbox

/**
Open Bank Project

Copyright 2011,2016 TESOBE Ltd.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and 
limitations under the License. 
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

import java.text.SimpleDateFormat
import java.util.Date

import scala.collection.mutable.ListBuffer
import scala.io.Source
import dispatch._
import net.liftweb.json._
import net.liftweb.json.JsonDSL._
import net.liftweb.common.Full
import code.api.test.SendServerRequests
import code.api.ObpJson._
import code.api.util.APIUtil._
import code.api._
import code.api.ObpJson.BarebonesAccountsJson



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
// Run a copy of the API somewhere (else)
// Set the paths for users and counterparties.

// TODO Extract this into a separate application.

object PostCustomer extends SendServerRequests {


  def debugBreak() {
    println("Breakpoint hit!") // Manually set a breakpoint here
  }




  def main(args : Array[String]) {

// Use this so we can extract dates from the json which are like this: 2016-04-11T12:39:02.605Z

    implicit val formats = new Formats {
      val dateFormat = DefaultFormats.lossless.dateFormat
    }







    //load json for customers
    val customerDataPath = "/Users/simonredfern/Documents/OpenBankProject/DATA/ENBD/load_013/OBP_sandbox_customers_pretty.json"

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

    val mainDataPath = "/Users/simonredfern/Documents/OpenBankProject/DATA/ENBD/load_013/OBP_sandbox_pretty.json"

    val mainData = JsonParser.parse(Source.fromFile(mainDataPath) mkString)
    val users = (mainData \ "users").children
    println("got " + users.length + " users")

    //loop over users from json
    for (u <- users) {
      val user = u.extract[UserJSONRecord]
      println(" ")
      print("login as user: ")
      println (user.email + " - " + user.password)

      if(!OAuthClient.loggedIn) {
        OAuthClient.authenticateWithOBPCredentials(user.email, user.password)
        println(" - ok.")
      }



      val customer = customers.filter(x => ( x.email == user.email))

      println(s"we got customer that matches ")

      customer.map(c =>  {
        println (s"email is ${c.email} has ${c.dependants} dependants born on ${c.dob_of_dependants.map(d => s"${d}")} ")


//        if(!cp.category.isEmpty && oa.metadata.get.more_info.isEmpty) {


        val bankId = "enbd-uae--p"


          val json = Extraction.decompose(c)
          val result = ObpPut(s"/v2.0.0/banks/$bankId/customer", json)
          if(!result.isEmpty){
            println("saved " + c.customer_number + " as customer " + result)
        } else {
          println("did NOT save customer "+ result )
        }


      })


   // {  "customer_number":"687687678",  "legal_name":"Joe David Bloggs",  "mobile_phone_number":"+44 07972 444 876",  "email":"person@example.com",  "face_image":{    "url":"www.example.com/person/123/image.png",    "date":"2013-01-22T00:08:00Z"  },  "date_of_birth":"2013-01-22T00:08:00Z",  "relationship_status":"Single",  "dependants":1,  "dob_of_dependants":["2013-01-22T00:08:00Z"],  "highest_education_attained":"Bachelor’s Degree",  "employment_status":"Employed",  "kyc_status":true,  "last_ok_date":"2013-01-22T00:08:00Z"}







//              val json = ("image_URL" -> logoUrl)
//              ObpPost("/v1.2.1/banks/" + a.bank_id.get + "/accounts/" + a.id.get + "/owner/other_accounts/" + oa.id.get + "/metadata/image_url", json)
//              println("saved " + logoUrl + " as imageURL for counterparty "+ oa.id.get)
//              found = true
//            } else {
//              println("did NOT save " + logoUrl + " as imageURL for counterparty "+ oa.id.get)
//          }







      OAuthClient.logoutAll()
    }

    sys.exit(0)
  }
}
