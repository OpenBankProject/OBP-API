package code.api.v5_1_0


import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON.apiCollectionsJson400
import code.api.util.APIUtil._
import code.api.util.ErrorMessages._
import code.transactionrequests.TransactionRequests.TransactionRequestTypes.{apply => _}
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model._
import com.openbankproject.commons.util.ApiVersion
import net.liftweb.http.rest.RestHelper
import com.openbankproject.commons.ExecutionContext.Implicits.global
import code.api.util.ApiRole._
import code.api.util.ApiTag._
import code.api.util.NewStyle

import scala.collection.mutable.ArrayBuffer
import code.api.util.NewStyle.HttpCode
import code.api.v4_0_0.JSONFactory400

trait APIMethods510 {
  self: RestHelper =>

  val Implementations5_1_0 = new Implementations510()

  class Implementations510 {

    val implementedInApiVersion = ApiVersion.v5_1_0

    private val staticResourceDocs = ArrayBuffer[ResourceDoc]()
    def resourceDocs = staticResourceDocs 

    val apiRelations = ArrayBuffer[ApiRelation]()
    val codeContext = CodeContext(staticResourceDocs, apiRelations)

    staticResourceDocs += ResourceDoc(
      getAllApiCollections,
      implementedInApiVersion,
      nameOf(getAllApiCollections),
      "GET",
      "/management/api-collections",
      "Get All Api Collections",
      s"""Get All Api Collections.
         |
         |${authenticationRequiredMessage(true)}
         |""".stripMargin,
      EmptyBody,
      apiCollectionsJson400,
      List(
        UserHasMissingRoles,
        UnknownError
      ),
      List(apiTagApiCollection, apiTagNewStyle),
      Some(canGetAllApiCollections :: Nil)
    )

    lazy val getAllApiCollections: OBPEndpoint = {
      case "management" :: "api-collections" :: Nil JsonGet _ => {
        cc =>
          for {
            (apiCollections, callContext) <- NewStyle.function.getAllApiCollections(cc.callContext)
          } yield {
            (JSONFactory400.createApiCollectionsJsonV400(apiCollections), HttpCode.`200`(callContext))
          }
      }
    }

  }
}

object APIMethods510 extends RestHelper with APIMethods510 {
  lazy val newStyleEndpoints: List[(String, String)] = Implementations5_1_0.resourceDocs.map {
    rd => (rd.partialFunctionName, rd.implementedInApiVersion.toString())
  }.toList
}

