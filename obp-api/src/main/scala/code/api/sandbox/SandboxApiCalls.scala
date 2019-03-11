package code.api.sandbox

import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON._
import code.api.util.APIUtil._
import code.api.util.{APIUtil, ApiVersion, ErrorMessages}
import code.api.{APIFailure, OBPRestHelper}
import code.sandbox.{OBPDataImport, SandboxDataImport}
import code.util.Helper
import code.util.Helper.MdcLoggable
import net.liftweb.http.S
import net.liftweb.http.rest.RestHelper
import net.liftweb.json.Extraction
import net.liftweb.util.Helpers._


object SandboxApiCalls extends OBPRestHelper with MdcLoggable {

  //needs to be a RestHelper to get access to JsonGet, JsonPost, etc.
  self: RestHelper =>
  logger.debug("Hello from SandboxApiCalls")
  val version = ApiVersion.sandbox // "sandbox"
  val versionStatus = "DEPRECIATED"

  oauthServe(apiPrefix{

    case "v1.0" :: "data-import" :: Nil JsonPost json -> _ => {
      cc =>
        logger.debug("Hello from v1.0 data-import")
        for{
          correctToken <- APIUtil.getPropsValue("sandbox_data_import_secret") ~> APIFailure("Data import is disabled for this API instance.", 403)
          providedToken <- S.param("secret_token") ~> APIFailure("secret_token parameter required", 403)
          tokensMatch <- Helper.booleanToBox(providedToken == correctToken) ~> APIFailure("incorrect secret token", 403)
          importData <- tryo{json.extract[SandboxDataImport]} ?~ ErrorMessages.InvalidJsonFormat
          importWorked <- OBPDataImport.importer.vend.importData(importData)
        } yield {
          successJsonResponse(Extraction.decompose(successMessage), 201)
        }

    }

  })


}
