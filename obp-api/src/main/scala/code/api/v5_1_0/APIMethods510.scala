package code.api.v5_1_0


import code.api.util.APIUtil._

import code.transactionrequests.TransactionRequests.TransactionRequestTypes.{apply => _}
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.BankId
import com.openbankproject.commons.util.ApiVersion
import net.liftweb.http.rest.RestHelper
import com.openbankproject.commons.ExecutionContext.Implicits.global
import scala.collection.mutable.ArrayBuffer


trait APIMethods510 {
  self: RestHelper =>

  val Implementations5_1_0 = new Implementations510()

  class Implementations510 {

    val implementedInApiVersion = ApiVersion.v5_1_0

    private val staticResourceDocs = ArrayBuffer[ResourceDoc]()
    def resourceDocs = staticResourceDocs 

    val apiRelations = ArrayBuffer[ApiRelation]()
    val codeContext = CodeContext(staticResourceDocs, apiRelations)

  }
}

object APIMethods510 extends RestHelper with APIMethods510 {
  lazy val newStyleEndpoints: List[(String, String)] = Implementations5_1_0.resourceDocs.map {
    rd => (rd.partialFunctionName, rd.implementedInApiVersion.toString())
  }.toList
}

