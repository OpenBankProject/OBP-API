package code.api.v3_0_0.custom


import code.api.util.APIUtil.{ApiRelation, CodeContext, ResourceDoc}
import code.api.util.ApiVersion
import net.liftweb.http.rest.RestHelper

import scala.collection.mutable.ArrayBuffer

trait CustomAPIMethods300 {
  //needs to be a RestHelper to get access to JsonGet, JsonPost, etc.
  self: RestHelper =>
  val ImplementationsCustom3_0_0 = new Object() {
    
    def endpointsOfCustom3_0_0 = createTransactionRequestTransferToReferenceAccountCustom :: Nil
    
    val apiVersion: ApiVersion = ApiVersion.v3_0_0
    val resourceDocs = ArrayBuffer[ResourceDoc]()
    val apiRelations = ArrayBuffer[ApiRelation]()
    val codeContext = CodeContext(resourceDocs, apiRelations)
    
    val createTransactionRequestTransferToReferenceAccountCustom = null
  }
  
}
