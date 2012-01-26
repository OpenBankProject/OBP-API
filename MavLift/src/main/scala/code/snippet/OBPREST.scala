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
 */
package com.tesobe.utils {

import net.liftweb.http._
import net.liftweb.http.rest._
import net.liftweb.json.JsonDSL._
import net.liftweb.json.Printer._
import net.liftweb.json.Extraction._
import net.liftweb.json.JsonAST._
import java.util.Calendar
import net.liftweb.common.Failure

// this has render in it.

import net.liftweb.json._  // Yep everything
import net.liftweb.common.Full
import net.liftweb.common.Empty
import net.liftweb.mongodb._
import net.liftweb.json.JsonAST.JString


import com.mongodb.casbah.Imports._



import net.liftweb.mongodb._

import _root_.java.math.MathContext
import net.liftweb.mongodb._

//import net.liftweb.mongodb.record._
//import net.liftweb.mongodb.record.field._
//import net.liftweb.record.field._
//import net.liftweb.record._
import org.bson.types._
import org.joda.time.{DateTime, DateTimeZone}

//import com.foursquare.rogue
//import com.foursquare.rogue.Rogue._


import java.util.regex.Pattern


//import org.junit._
//import org.specs.SpecsMatchers

//import com.foursquare.rogue.MongoHelpers._

////////////

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
//import code.model.Location

// Note: on mongo console db.chooseitems.ensureIndex( { location : "2d" } )

// Call like http://localhost:8080/api/balance/theaccountnumber/call.json
// See http://www.assembla.com/spaces/liftweb/wiki/REST_Web_Services


object OBPRest extends RestHelper {
    println("here we are in OBPRest")
    serve {

      
      
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
      
      for{
        t <- OBPEnvelope.fromJValue(json._1)
        saved <- t.saveTheRecord()
      } yield saved.asMediatedJValue("authorities") //this _should_ provide full access view, but with incorrect access settings it won't
    } 
    
    //curl -H "Accept: application/json" -H "Context-Type: application/json" -X GET "http://localhost:8080/api/accounts/tesobe/anonymous"
    //This is for demo purposes only, as it's showing every single transaction rather than everything tesobe specific. This will need
    //to be completely reworked.
    case "api" :: "accounts" :: "tesobe" :: accessLevel :: Nil JsonGet _ => {
      val allEnvelopes = OBPEnvelope.findAll(QueryBuilder.start().get)
      
      
      val envelopeJson = allEnvelopes.map(envelope => envelope.asMediatedJValue(accessLevel))
      
      
      JsonResponse(envelopeJson)
    }
      
    }
}


} // end of package