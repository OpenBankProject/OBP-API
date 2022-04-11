package code.api.v5_0_0

import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON._
import code.api.util.APIUtil._
import code.api.util.ApiTag._
import code.api.util.ErrorMessages._
import code.api.util.NewStyle.HttpCode
import code.transactionrequests.TransactionRequests.TransactionRequestTypes.{apply => _}
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.util.ApiVersion
import net.liftweb.http.rest.RestHelper

import scala.collection.immutable.{List, Nil}
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Future

trait APIMethods500 {
  self: RestHelper =>

  val Implementations5_0_0 = new Implementations500()

  class Implementations500 {

    val implementedInApiVersion = ApiVersion.v5_0_0

    private val staticResourceDocs = ArrayBuffer[ResourceDoc]()
    def resourceDocs = staticResourceDocs 

    val apiRelations = ArrayBuffer[ApiRelation]()
    val codeContext = CodeContext(staticResourceDocs, apiRelations)

}}

object APIMethods500 extends RestHelper with APIMethods500 {
  lazy val newStyleEndpoints: List[(String, String)] = Implementations5_0_0.resourceDocs.map {
    rd => (rd.partialFunctionName, rd.implementedInApiVersion.toString())
  }.toList
}

