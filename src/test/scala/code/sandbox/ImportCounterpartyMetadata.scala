package code.sandbox

/**
Open Bank Project

Copyright 2011,2015 TESOBE / Music Pictures Ltd.

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

case class CounterpartyJSONRecord(name: String, category: String, superCategory: String, logoUrl: String, homePageUrl: String, region: String)
case class UserJSONRecord(email: String, password: String, display_name: String)


object ImportCounterpartyMetadata extends SendServerRequests {
  def main(args : Array[String]) {
    implicit val formats = DefaultFormats

    //load json for counterpaties
    var path = "/Users/stefan/Downloads/OBP_sandbox_counterparties_pretty.json"
    var records = JsonParser.parse(Source.fromFile(path) mkString)
    var counterparties = ListBuffer[CounterpartyJSONRecord]()

    //collect counterparties records
    for(r <- records.children){
      val rec = r.extract[CounterpartyJSONRecord]
      //println (rec.name + "in region " + rec.region)
      counterparties.append(rec)
    }

    println("Got " + counterparties.length + " counterparty records")

    //load sandbox users from json
    path = "/Users/stefan/Downloads/OBP_sandbox_pretty.json"
    records = JsonParser.parse(Source.fromFile(path) mkString)
    val users = (records \ "users").children
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

      print("get users private accounts")
      val accountsJson = ObpGet("/v1.2.1/accounts/private").flatMap(_.extractOpt[BarebonesAccountsJson])
      val accounts : List[BarebonesAccountJson] = accountsJson match {
        case Full(as) => as.accounts.get
        case _ => List[BarebonesAccountJson]()
      }
      println(" - ok.")

      println("get transactions for the accounts")
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
        }
      }


      OAuthClient.logoutAll()
    }

    sys.exit(0)
  }
}
