package code.api.UKOpenBanking.v3_1_0

import code.api.berlin.group.v1_3.JvalueCaseClass
import code.api.util.APIUtil._
import code.api.util.ApiTag
import code.api.util.ApiTag._
import code.api.util.ErrorMessages._
import com.github.dwickern.macros.NameOf.nameOf
import net.liftweb.common.Full
import code.api.RestHelperX
import net.liftweb.json
import net.liftweb.json._

import scala.collection.immutable.Nil
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.ExecutionContext.Implicits.global

object APIMethods_ProductsApi extends RestHelperX {
    val apiVersion = OBP_UKOpenBanking_310.apiVersion
    val resourceDocs = ArrayBuffer[ResourceDoc]()
    val apiRelations = ArrayBuffer[ApiRelation]()
    protected implicit def JvalueToSuper(what: JValue): JvalueCaseClass = JvalueCaseClass(what)

    val endpoints = 
      getAccountsAccountIdProduct ::
      getProducts ::
      Nil

            
     resourceDocs += ResourceDoc(
       getAccountsAccountIdProduct, 
       apiVersion, 
       nameOf(getAccountsAccountIdProduct),
       "GET", 
       "/accounts/ACCOUNTID/product", 
       "Get Products",
       s"""${mockedDataText(true)}""", 
       json.parse(""""""),
       json.parse(""""""),
       List(UserNotLoggedIn, UnknownError),
       Catalogs(notCore, notPSD2, notOBWG), 
       ApiTag("Products") :: apiTagMockedData :: Nil
     )

     lazy val getAccountsAccountIdProduct : OBPEndpoint = {
       case "accounts" :: accountid:: "product" :: Nil JsonGet _ => {
         cc =>
           for {
             (Full(u), callContext) <- authorizedAccess(cc)
             } yield {
             (NotImplemented, callContext)
           }
         }
       }
            
     resourceDocs += ResourceDoc(
       getProducts, 
       apiVersion, 
       nameOf(getProducts),
       "GET", 
       "/products", 
       "Get Products",
       s"""${mockedDataText(true)}
""", 
       json.parse(""""""),
       json.parse(""""""),
       List(UserNotLoggedIn, UnknownError),
       Catalogs(notCore, notPSD2, notOBWG), 
       ApiTag("Products") :: apiTagMockedData :: Nil
     )

     lazy val getProducts : OBPEndpoint = {
       case "products" :: Nil JsonGet _ => {
         cc =>
           for {
             (Full(u), callContext) <- authorizedAccess(cc)
             } yield {
             (NotImplemented, callContext)
           }
         }
       }

}



