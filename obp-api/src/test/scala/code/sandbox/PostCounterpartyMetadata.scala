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

import code.api.util.CustomJsonFormats
import code.setup.SendServerRequests
import code.util.ObpJson.{BarebonesAccountsJson, _}
import code.util.{Header, OAuthClient, ObpGet, ObpPost}
import net.liftweb.common.Full
import net.liftweb.json.JsonDSL._
import net.liftweb.json._

import scala.collection.mutable.ListBuffer
import scala.io.Source

case class CounterpartyJSONRecord(name: String, category: String, superCategory: String, logoUrl: String, homePageUrl: String, region: String)
case class UserJSONRecord(email: String, password: String, user_name: String)


// Import counterparty metadata
// Instructions for using this:
// Run a copy of the API somewhere (else)
// Set the paths for users and counterparties.

// TODO Extract this into a separate application.

object PostCounterpartyMetadata extends SendServerRequests {


  def debugBreak() {
    println("Breakpoint hit!") // Manually set a breakpoint here
  }




  def main(args : Array[String]) {
    implicit val formats = CustomJsonFormats.formats

    //load json for counterpaties
    // val counterpartyDataPath = "/Users/simonredfern/Documents/OpenBankProject/DATA/korea/loaded_06/OBP_sandbox_counterparties_pretty.json"

    val counterpartyDataPath = "/Users/simonredfern/Documents/OpenBankProject/DATA/May_2018_ABN_Netherlands_extra/loaded_01/OBP_sandbox_counterparties_pretty.json"



    // This contains a list of counterparty lists. one list for each region
    val counerpartyListData = JsonParser.parse(Source.fromFile(counterpartyDataPath).mkString)
    var counterparties = ListBuffer[CounterpartyJSONRecord]()

    // Loop through the lists
    for(l <- counerpartyListData.children){

      // For each list, loop through the counterparties
      for(c <- l.children) {
        //logger.info(s" extract counterparty records")
        val rec = c.extract[CounterpartyJSONRecord]
        println(rec.name + "in region " + rec.region)
        counterparties.append(rec)
      }
    }



    //collect counterparties records
//    for(r <- counerpartyData.children){
//      //logger.info(s" extract counterparty records")
//      val rec = r.extract[CounterpartyJSONRecord]
//      println (rec.name + "in region " + rec.region)
//      counterparties.append(rec)
//    }
//




    println("Got " + counterparties.length + " counterparty records")

    //load sandbox users from json

    // val mainDataPath = "/Users/simonredfern/Documents/OpenBankProject/DATA/korea/loaded_06/OBP_sandbox_pretty.json"


    val mainDataPath = "/Users/simonredfern/Documents/OpenBankProject/DATA/May_2018_ABN_Netherlands_extra/loaded_01/OBP_sandbox_pretty.json"


    val mainData = JsonParser.parse(Source.fromFile(mainDataPath).mkString)
    val users = (mainData \ "users").children
    println("got " + users.length + " users")

    //loop over users from json
    for (u <- users) {
      val user = u.extract[UserJSONRecord]
      println(" ")
      print("login as user: ")
      println (user.user_name + " - " + user.password)

      if(!OAuthClient.loggedIn) {
        OAuthClient.authenticateWithOBPCredentials(user.user_name, user.password)
        //println(" - ok.")
      }

      print("get users private accounts")
      val accountsJson = ObpGet("/v1.2.1/accounts/private").flatMap(_.extractOpt[BarebonesAccountsJson])
      val accounts : List[BarebonesAccountJson] = accountsJson match {
        case Full(as) => as.accounts.get
        case _ => List[BarebonesAccountJson]()
      }
      println(" - ok.")

      println("get other accounts for the accounts")
      for(a : BarebonesAccountJson <- accounts) {
        print("account: " + a.label.get + " ")
        println(a.bank_id.get)

        val headers : List[Header] = List(Header("obp_limit", "9999999"))  //prevent pagination
        val otherAccountsJson =
          ObpGet("/v1.2.1/banks/"+a.bank_id.get+"/accounts/"+a.id.get+"/owner/other_accounts", headers).flatMap(_.extractOpt[OtherAccountsJson])

        val otherAccounts : List[OtherAccountJson] = otherAccountsJson match {
          case Full(oa) => oa.other_accounts.get
          case _ => List[OtherAccountJson]()
        }



        val bankId = a.bank_id.get

        println(s"bankId is ${bankId}")

        // In the counterparty json, counterparties have a region which refers to the physical location area (for the purposes of local shops etc.)

        // In sandboxes expect the format of bankId = s"$sandboxGroupName.$sandboxGroupInstance.$bankCode.$counterpartyCode"
        // This is rather turning the bankId into a composite surrogate key but only for sandbox creation.

        // Split by dot (.) except split can take a reg expression so must escape the .
        val bits = bankId.split("\\.")

        val region = bits(2) // Use the counterpartyCode from the bankId

        println(s"region is ${region}")


        println("get matching json counterparty data for each transaction's other_account")

        for(oa : OtherAccountJson <- otherAccounts) {
          val name = oa.holder.get.name.get.trim


          println(s"Filtering counterparties by region ${region} and counterparty name ${name}")

          val regionCounterparties = counterparties.filter(x => ( x.region == region))

          val records = regionCounterparties.filter(x => (x.name equalsIgnoreCase(name)) )


          var found = false

          // region == "enbd-lon" &&
          if (records.size == 0 && name.toLowerCase().indexOf("gas natural") > 0) debugBreak() // else println(s"Condition not met. region is ${region} name is ${name}")

          println(s"Found ${records.size} records")

          //loop over all counterparties (from json) and match to other_account (counterparties), update data
          for (cp: CounterpartyJSONRecord <- records) {
            println(s"cp is Region ${cp.region} Name ${cp.name} Home Page ${cp.homePageUrl}")
            val logoUrl = if(cp.logoUrl.contains("http://www.brandprofiles.com")) cp.homePageUrl else cp.logoUrl
            if (logoUrl.startsWith("http") && oa.metadata.get.image_URL.isEmpty) {
              val json = ("image_URL" -> logoUrl)
              ObpPost("/v1.2.1/banks/" + a.bank_id.get + "/accounts/" + a.id.get + "/owner/other_accounts/" + oa.id.get + "/metadata/image_url", json)
              println("saved " + logoUrl + " as imageURL for counterparty "+ oa.id.get)
              found = true
            } else {
              println("did NOT save " + logoUrl + " as imageURL for counterparty "+ oa.id.get)
          }

            if(cp.homePageUrl.startsWith("http") && !cp.homePageUrl.endsWith("jpg") && !cp.homePageUrl.endsWith("png") && oa.metadata.get.URL.isEmpty) {
              val json = ("URL" -> cp.homePageUrl)
              ObpPost("/v1.2.1/banks/" + a.bank_id.get + "/accounts/" + a.id.get + "/owner/other_accounts/" + oa.id.get + "/metadata/url", json)
              println("saved " + cp.homePageUrl + " as URL for counterparty "+ oa.id.get)
            } else {
              println("did NOT save " + cp.homePageUrl + " as URL for counterparty "+ oa.id.get)
            }

            if(!cp.category.isEmpty && oa.metadata.get.more_info.isEmpty) {

              // In some cases we might have something like Police_1 . We remove the _1
              val categoryBits = cp.category.split("_")
              val moreInfo = (categoryBits(0) )

              val json = ("more_info" -> moreInfo)
              val result = ObpPost("/v1.2.1/banks/" + a.bank_id.get + "/accounts/" + a.id.get + "/owner/other_accounts/" + oa.id.get + "/metadata/more_info", json)
              if(!result.isEmpty)
                println("saved " + moreInfo + " as more_info for counterparty "+ oa.id.get)
            } else {
              println("did NOT save more_info for counterparty "+ oa.id.get)
            }
          }

        }

      /*println("get transactions for the accounts")
      for(a : BarebonesAccountJson <- accounts) {
        print("account: " + a.label.get + " ")
        println(a.bank_id.get)

        val headers : List[Header] = List(Header("obp_limit", "9999999"))
        val transactionsJson =
          ObpGet("/v1.2.1/banks/"+a.bank_id.get+"/accounts/"+a.id.get+"/owner/transactions", headers).flatMap(_.extractOpt[TransactionsJson])

        val transactions : List[TransactionJson] = transactionsJson match {
          case Full(ts) => ts.transactions.get
          case _ => List[TransactionJson]()
        }

        //uh, matching very specific to rbs data
        val bits = a.bank_id.get.split("-")
        val region = bits(bits.length - 2)

        println("get matching json counterparty data for each transaction's other_account")

        for(t : TransactionJson <- transactions) {
          val name = t.other_account.get.holder.get.name
          val records = counterparties.filter(x => ((x.name equalsIgnoreCase(name.get)) && (x.region equals region)))
          var found = false
          for (cp: CounterpartyJSONRecord <- records) {
            val logoUrl = if(cp.logoUrl.contains("http://www.brandprofiles.com")) cp.homePageUrl else cp.logoUrl
            if (logoUrl.startsWith("http") && t.metadata.get.images.get.isEmpty) {
              val json = ("label" -> "Logo") ~ ("URL" -> logoUrl)
              ObpPost("/v1.2.1/banks/" + a.bank_id.get + "/accounts/" + a.id.get + "/owner/transactions/" + t.id.get + "/metadata/images", json)
              println("saved " + logoUrl + " for transaction "+ t.id.get)
              found = true
            }
          }
        }*/
      }


      OAuthClient.logoutAll()
    }

    sys.exit(0)
  }
}
