/** 
Open Bank Project

Copyright 2011,2012 TESOBE / Music Pictures Ltd.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and 
limitations under the License. 

Open Bank Project (http://www.openbankproject.com)
      Copyright 2011,2012 TESOBE / Music Pictures Ltd

      This product includes software developed at
      TESOBE (http://www.tesobe.com/)
    by 
    Simon Redfern : simon AT tesobe DOT com
    Everett Sochowski: everett AT tesobe DOT com

 */
package com.tesobe.utils {

import code.actors.EnvelopeInserter
import net.liftweb.http._
import net.liftweb.http.rest._
import net.liftweb.json.JsonDSL._
import net.liftweb.json.Printer._
import net.liftweb.json.Extraction._
import net.liftweb.json.JsonAST._
import java.util.Calendar
import net.liftweb.common.Failure
import net.liftweb.common.Full
import net.liftweb.common.Empty
import net.liftweb.mongodb._
import net.liftweb.json.JsonAST.JString
import com.mongodb.casbah.Imports._
import net.liftweb.mongodb._
import _root_.java.math.MathContext
import net.liftweb.mongodb._
import org.bson.types._
import org.joda.time.{DateTime, DateTimeZone}
import java.util.regex.Pattern
import _root_.net.liftweb.common._
import _root_.net.liftweb.util._
import _root_.net.liftweb.http._
import _root_.net.liftweb.mapper._
import _root_.net.liftweb.util.Helpers._
import _root_.net.liftweb.sitemap._
import _root_.scala.xml._
import _root_.net.liftweb.http.S._
import _root_.net.liftweb.http.RequestVar
import _root_.net.liftweb.util.Helpers._
import _root_.net.liftweb.common.Full
import net.liftweb.mongodb.{Skip, Limit}
import _root_.net.liftweb.http.S._
import _root_.net.liftweb.mapper.view._
import com.mongodb._
import code.model._

// Note: on mongo console db.chooseitems.ensureIndex( { location : "2d" } )

// Call like http://localhost:8080/api/balance/theaccountnumber/call.json
// See http://www.assembla.com/spaces/liftweb/wiki/REST_Web_Services


object OBPRest extends RestHelper with Loggable 
{
    println("here we are in OBPRest")
    serve 
    {

      
      
    /**
     * curl -i -H "Content-Type: application/json" -X POST -d '{
         "obp_transaction":{
            "this_account":{
               "holder":"Music Pictures Limited",
               "number":"123567",
               "kind":"current",
               "bank":{
                  "IBAN":"DE1235123612",
                  "national_identifier":"de.10010010",
                  "name":"Postbank"
               }
            },
            "other_account":{
               "holder":"Client 1",
               "number":"123567",
               "kind":"current",
               "bank":{
                  "IBAN":"UK12222879",
                  "national_identifier":"uk.10010010",
                  "name":"HSBC"
               }
            },
            "details":{
               "type_en":"Transfer",
               "type_de":"Überweisung",
               "posted":{
            "$dt":"2012-01-04T18:06:22.000Z"
        },
               "completed":{
            "$dt":"2012-09-04T18:52:13.000Z"
        },
               "new_balance":{
                  "currency":"EUR",
                  "amount":"4323.45"
               },
               "value":{
                  "currency":"EUR",
                  "amount":"123.45"
               },
               "other_data":"9"
            }
         }
   }  ' http://localhost:8080/api/transactions  
     */
    case "api" :: "transactions" :: Nil JsonPost json => {
      
      //
      // WARNING!
      //
      // If you have not configured a web server to restrict this URL 
      // appropriately, anyone will be
      // able to post transactions to your database. This would obviously 
      // be undesirable. So you should
      // definitely sort that out.
      //
      //
      
      val rawEnvelopes = json._1.children
      
      val envelopes = rawEnvelopes.map(e => {
        OBPEnvelope.fromJValue(e)
      })
      
      val ipAddress = json._2.remoteAddr
      logger.info("Received " + rawEnvelopes.size + 
        " json transactions to insert from ip address " + ipAddress)
      logger.info("Received " + envelopes.size + 
        " valid transactions to insert from ip address " + ipAddress)
      
      /**
       * Using an actor to do insertions avoids concurrency issues with 
       * duplicate transactions by processing transaction batches one 
       * at a time. We'll have to monitor this to see if non-concurrent I/O 
       * is too inefficient. If it is, we could break it up into one actor
       * per "Account".
       */
      val createdEnvelopes = EnvelopeInserter !? (3 seconds, envelopes.flatten)
      
      createdEnvelopes match{
        case Full(l : List[JObject]) => JsonResponse(JArray(l))
        case _ => InternalServerErrorResponse()
      }
    } 
    /*
      returns the anonymous view automatically.
      For the moment the is only one account so there is no check to do 
      before returning the Json
    */
    case Req("api" :: "accounts" :: "tesobe":: "anonymous" :: Nil,_ ,GetRequest) => 
    {
      val allEnvelopes = OBPEnvelope.findAll(QueryBuilder.start().get)
      val envelopeJson = allEnvelopes.map(envelope => 
      envelope.asMediatedJValue("accessLevel"))
      Full(JsonResponse(envelopeJson))  
    }    
    /*
      return a JSon of all the transactions.
      requires an OAuth token as authentication mechanism. 
    */
    case Req("api" :: "accounts" :: "tesobe":: accessLevel :: Nil,_ ,GetRequest) => 
    {
        //check if the accessLeve required already exists 
        def doesTheViewExists(accessLevel : String) : Boolean = 
        {
          /* 
            for the moment the number of views is limited this why 
            they are hard coded. The work on 'dataAbstraction' branch 
            will avoid this kind of verification. 
          */   
          val currentExistingViews = List("anonymous", "our-network", "team", 
            "board", "my-view")
          currentExistingViews.contains(accessLevel)
        }
        //check of the token authorize the application to have access 
        //to accessLevel
        def doesTheTokenHasAccess(accessLevel : String, tokenID : String) : Boolean = 
        {
            //check if the privileges of the user authorize if to access
            // to the access level
            def doesTheUserHasAccess(accessLevel : String, user : User) : Boolean =
            {
                import code.model.{Account, Privilege} 
                Account.find(("holder", "Music Pictures Limited")) match {
                case Full(account) => if(accessLevel=="anonymous")
                                        account.anonAccess.is
                                      else
                                        Privilege.find(By(Privilege.accountID,account.id.toString), By(Privilege.user, user)) match {
                                          case Full(privilege) => privilegeCheck(accessLevel,privilege)
                                          case _ => false 
                                        }
                case _ => false  
                }
            }

            //due to the actual privilege mechanism : the access level (API) 
            //and the privilege of the account are stored differently 
            //So the function do the match 
            def privilegeCheck(accessLevel : String, privilege : Privilege) : Boolean = 
              if(accessLevel == "my-view")
                privilege.ownerPermission.is
              else if(accessLevel == "our-network")
                privilege.ourNetworkPermission.is
              else if(accessLevel ==  "team")
                privilege.teamPermission.is  
              else if(accessLevel == "board")
                privilege.boardPermission.is
              else 
                false 

            import code.model.Token._     
            Token.find(By(Token.key,tokenID)) match {
              case Full(token) => User.find(By(User.id,token.userId)) match {
                                    case Full(user) => 
                                            doesTheUserHasAccess(accessLevel, user) 
                                    case _ => false 
                                  }
              case _ => false  
            }
        }
        
        import code.snippet.OAuthHandshake._
        val headers = ("Content-type" -> "application/x-www-form-urlencoded") :: Nil
        //Extract the OAuth parameters from the header and test if the request is valid
        var (httpCode, data, oAuthParameters) = validator("protectedResource", "GET")
        //Test if the view exists and the OAuth request is valid
        if(doesTheViewExists(accessLevel) && httpCode==200)
        {
            //check that the token gives access to the required view
            if(doesTheTokenHasAccess(accessLevel, oAuthParameters.get("oauth_token").get))
            {
              val allEnvelopes = OBPEnvelope.findAll(QueryBuilder.start().get)
              val envelopeJson = allEnvelopes.map(envelope => 
              envelope.asMediatedJValue(accessLevel))
              Full(JsonResponse(envelopeJson))  
            }
            else
              Full(InMemoryResponse(data,headers,Nil,401)) 
        }
        else if(!doesTheViewExists(accessLevel) && httpCode==200)
          Full(InMemoryResponse(data,headers,Nil,404))
        else 
          Full(InMemoryResponse(data,headers,Nil,httpCode))
    }
  }
}
} 