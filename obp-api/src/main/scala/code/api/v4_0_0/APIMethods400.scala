package code.api.v4_0_0

import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON.banksJSON
import code.api.util.APIUtil._
import code.api.util.ApiTag._
import code.api.util.ErrorMessages.UnknownError
import code.api.util.NewStyle.HttpCode
import code.api.util._
import com.github.dwickern.macros.NameOf.nameOf
import net.liftweb.http.rest.RestHelper
import net.liftweb.json._

import scala.collection.immutable.{List, Nil}
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.ExecutionContext.Implicits.global

trait APIMethods400 {
  self: RestHelper =>

  val Implementations4_0_0 = new Implementations400() 
  // note, because RestHelper have a implicit Formats, it is not correct for OBP, so here override it
  protected implicit override abstract def formats: Formats = CustomJsonFormats.formats

  class Implementations400 {

    val implementedInApiVersion = ApiVersion.v4_0_0

    val resourceDocs = ArrayBuffer[ResourceDoc]()
    val apiRelations = ArrayBuffer[ApiRelation]()
    val codeContext = CodeContext(resourceDocs, apiRelations)


    resourceDocs += ResourceDoc(
      getBanks,
      implementedInApiVersion,
      nameOf(getBanks),
      "GET",
      "/banks",
      "Get Banks",
      """Get banks on this API instance
        |Returns a list of banks supported on this server:
        |
        |* ID used as parameter in URLs
        |* Short and full name of bank
        |* Logo URL
        |* Website""",
      emptyObjectJson,
      banksJSON,
      List(UnknownError),
      Catalogs(Core, PSD2, OBWG),
      apiTagBank :: apiTagPSD2AIS :: apiTagNewStyle :: Nil)
    
    lazy val getBanks : OBPEndpoint = {
      case "banks" :: Nil JsonGet _ => {
        cc =>
          for {
            (_, callContext) <- anonymousAccess(cc)
            (banks, callContext) <- NewStyle.function.getBanks(callContext)
          } yield{
            org.scalameta.logger.elem(banks)
            (JSONFactory400.createBanksJson(banks), HttpCode.`200`(callContext))
          }
          
      }
    }

 

  }
}



object APIMethods400 {
}

