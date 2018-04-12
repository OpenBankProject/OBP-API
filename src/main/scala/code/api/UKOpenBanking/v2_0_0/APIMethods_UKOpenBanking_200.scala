package code.api.UKOpenBanking.v2_0_0

import code.api.util.APIUtil._
import code.api.util.ApiVersion
import net.liftweb.http.rest.RestHelper

import scala.collection.mutable.ArrayBuffer

trait APIMethods_UKOpenBanking_200 {
  //needs to be a RestHelper to get access to JsonGet, JsonPost, etc.
  self: RestHelper =>

  val ImplementationsUKOpenBanking200 = new Object() {
    val implementedInApiVersion: ApiVersion = ApiVersion.ukOpenBankingV200 // was noV

    val resourceDocs = ArrayBuffer[ResourceDoc]()
    val apiRelations = ArrayBuffer[ApiRelation]()
    val codeContext = CodeContext(resourceDocs, apiRelations)



  }

}



