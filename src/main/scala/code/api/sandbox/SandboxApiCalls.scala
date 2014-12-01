package code.api.sandbox

import code.api.{APIFailure, OBPRestHelper}
import code.api.util.APIUtil._
import code.sandbox.{OBPDataImport, SandboxDataImport}
import code.util.Helper
import net.liftweb.common.{Box, Full, Failure, Loggable}
import net.liftweb.http.{JsonResponse, ForbiddenResponse, S}
import net.liftweb.http.js.JE.JsRaw
import net.liftweb.http.rest.RestHelper
import net.liftweb.util.Helpers._
import net.liftweb.util.Props

object SandboxApiCalls extends OBPRestHelper with Loggable {

  //needs to be a RestHelper to get access to JsonGet, JsonPost, etc.
  self: RestHelper =>

  val VERSION = "sandbox"

  oauthServe(apiPrefix{

    case "v1.0" :: "data-import" :: Nil JsonPost json -> _ => {
      user =>

        for{
          correctToken <- Props.get("sandbox_data_import_secret") ~> APIFailure("Data import is disabled for this API instance.", 403)
          providedToken <- S.param("secret_token") ~> APIFailure("secret_token parameter required", 403)
          tokensMatch <- Helper.booleanToBox(providedToken == correctToken) ~> APIFailure("incorrect secret token", 403)
          importData <- tryo{json.extract[SandboxDataImport]} ?~ "invalid json"
          importWorked <- OBPDataImport.importer.vend.tmpImportData(importData)
        } yield {
          successJsonResponse(JsRaw("{}"), 201)
        }

    }

  })


}
