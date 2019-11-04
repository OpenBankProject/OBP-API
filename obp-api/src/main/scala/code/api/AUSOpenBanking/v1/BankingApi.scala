package code.api.AUSOpenBanking.v1

import code.api.util.APIUtil._
import code.api.util.ApiTag._
import code.api.util.ErrorMessages._
import code.api.util.{ApiTag}
import com.github.dwickern.macros.NameOf.nameOf
import net.liftweb.common.Full
import net.liftweb.http.rest.RestHelper
import net.liftweb.json
import net.liftweb.json._

import scala.collection.immutable.Nil
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.ExecutionContext.Implicits.global

object APIMethods_BankingApi extends RestHelper {
    val apiVersion =  OBP_AU_OpenBanking_1_0_0.apiVersion
    val resourceDocs = ArrayBuffer[ResourceDoc]()
    val apiRelations = ArrayBuffer[ApiRelation]()
    protected implicit def JvalueToSuper(what: JValue): JvalueCaseClass = JvalueCaseClass(what)

    val endpoints = 
      getAccounts ::
      getAccountBalance ::
      Nil

            
     resourceDocs += ResourceDoc(
       getAccounts,
       apiVersion,
       nameOf(getAccounts),
       "POST",
       "/banking/accounts",
       "Get Accounts",
       s"""  ${mockedDataText(false)}
         Obtain a list of accounts  """,
       emptyObjectJson,
       json.parse(
         """{
              "data": {
                "accounts": [
                  {
                    "accountId": "string",
                    "creationDate": "string",
                    "displayName": "string",
                    "nickname": "string",
                    "openStatus": "OPEN",
                    "isOwned": true,
                    "maskedNumber": "string",
                    "productCategory": "TRANS_AND_SAVINGS_ACCOUNTS",
                    "productName": "string"
                  }
                ]
              },
              "links": {
                "self": "string",
                "first": "string",
                "prev": "string",
                "next": "string",
                "last": "string"
              },
              "meta": {
                "totalRecords": 0,
                "totalPages": 0
              }
            }"""),
       List(UserNotLoggedIn, UnknownError),
       Catalogs(notCore, notPSD2, notOBWG),
       ApiTag("Banking") :: apiTagMockedData :: apiTagBanking :: Nil
     )

     lazy val getAccounts : OBPEndpoint = {
       case "banking" :: "accounts" ::  Nil JsonGet _ => {
         cc =>
           for {
             (Full(u), callContext) <- authorizedAccess(cc)
             _ <- passesPsd2Aisp(callContext)
             
            
             } yield {
             (net.liftweb.json.parse(s"""{
              "data": {
                "accounts": [
                  {
                    "accountId": "string",
                    "creationDate": "string",
                    "displayName": "string",
                    "nickname": "string",
                    "openStatus": "OPEN",
                    "isOwned": true,
                    "maskedNumber": "string",
                    "productCategory": "TRANS_AND_SAVINGS_ACCOUNTS",
                    "productName": "string"
                  }
                ]
              },
              "links": {
                "self": "string",
                "first": "string",
                "prev": "string",
                "next": "string",
                "last": "string"
              },
              "meta": {
                "totalRecords": 0,
                "totalPages": 0
              }
            }"""), 
               callContext)
           }
         }
       }   
  
     resourceDocs += ResourceDoc(
       getAccountBalance,
       apiVersion,
       nameOf(getAccountBalance),
       "POST",
       "/banking/accounts/ACCOUNT_ID/balance",
       "Get Account Balance",
       s"""  ${mockedDataText(false)}
         Obtain a list of accounts  """,
       emptyObjectJson,
       json.parse(
         """{
            "data": {
              "accountId": "string",
              "currentBalance": "string",
              "availableBalance": "string",
              "creditLimit": "string",
              "amortisedLimit": "string",
              "currency": "string",
              "purses": [
                {
                  "amount": "string",
                  "currency": "string"
                }
              ]
            },
            "links": {
              "self": "string"
            },
            "meta": {}
          }"""),
       List(UserNotLoggedIn, UnknownError),
       Catalogs(notCore, notPSD2, notOBWG),
       ApiTag("Banking") :: apiTagMockedData :: apiTagBanking :: Nil
     )

     lazy val getAccountBalance : OBPEndpoint = {
       case "banking" :: "accounts" :: account_id :: "balance" ::  Nil JsonGet _ => {
         cc =>
           for {
             (Full(u), callContext) <- authorizedAccess(cc)
             _ <- passesPsd2Aisp(callContext)
             
            
             } yield {
             (net.liftweb.json.parse(s"""{
                "data": {
                  "accountId": "string",
                  "currentBalance": "string",
                  "availableBalance": "string",
                  "creditLimit": "string",
                  "amortisedLimit": "string",
                  "currency": "string",
                  "purses": [
                    {
                      "amount": "string",
                      "currency": "string"
                    }
                  ]
                },
                "links": {
                  "self": "string"
                },
                "meta": {}
              }"""), 
               callContext)
           }
         }
       }

}



