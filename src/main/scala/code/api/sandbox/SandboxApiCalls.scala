package code.api.sandbox

import code.api.OBPRestHelper
import net.liftweb.common.{Failure, Loggable}
import net.liftweb.http.rest.RestHelper

object SandboxApiCalls extends OBPRestHelper with Loggable {

  //needs to be a RestHelper to get access to JsonGet, JsonPost, etc.
  self: RestHelper =>

  val VERSION = "sandbox"

  oauthServe(apiPrefix{

    case "data-import" :: Nil JsonPost json -> _ => {
      user =>
        Failure("TODO")
    }

  })


}
