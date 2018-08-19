package code.api.APIBuilder
import code.api.util.ApiVersion
import code.api.util.ErrorMessages._
import net.liftweb.http.rest.RestHelper
import scala.collection.immutable.Nil
import scala.collection.mutable.ArrayBuffer
import code.api.util.APIUtil._
import net.liftweb.json
import net.liftweb.json.JValue
import code.api.APIBuilder.JsonFactory_APIBuilder._
trait APIMethods_APIBuilder { self: RestHelper =>
  val ImplementationsBuilderAPI = new Object() {
    val apiVersion: ApiVersion = ApiVersion.apiBuilder
    val resourceDocs = ArrayBuffer[ResourceDoc]()
    val apiRelations = ArrayBuffer[ApiRelation]()
    val codeContext = CodeContext(resourceDocs, apiRelations)
    def endpointsOfBuilderAPI = getBooks :: Nil
    resourceDocs += ResourceDoc(getBooks, apiVersion, "getBooks", "GET", "/my/good/books", "Get Books", "Return All my books. Authentication is Mandatory. ", emptyObjectJson, rootInterface, List(UnknownError), Catalogs(notCore, notPSD2, notOBWG), apiTagBank :: Nil)
    lazy val getBooks: OBPEndpoint = {
      case ("my"  ::  "good"  ::  "books" :: Nil) JsonGet req =>
        cc => {
          for (u <- cc.user ?~ UserNotLoggedIn; jsonString = scala.io.Source.fromFile("src/main/scala/code/api/APIBuilder/newAPis.json").mkString; jsonObject: JValue = json.parse(jsonString) \\ "success_response_body") yield {
            successJsonResponse(jsonObject)
          }
        }
    }
  }
}