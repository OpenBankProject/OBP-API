package code.connector

import code.api.v2_2_0.JSONFactory220.MessageDocJson
import code.api.v2_2_0.OBPAPI2_2_0.Implementations2_2_0
import code.api.v2_2_0.V220ServerSetup
import code.bankconnectors.LocalMappedConnector
import code.setup.DefaultUsers
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.util.ApiVersion
import net.liftweb.json
import net.liftweb.json.JValue
import org.scalatest.Tag

import scala.collection.immutable.List
import scala.reflect.ManifestFactory

class MessageDocTest extends V220ServerSetup with DefaultUsers {
  /**
   * Test tags
   * Example: To run tests with tag "getPermissions":
   * 	mvn test -D tagsToInclude
   *
   *  This is made possible by the scalatest maven plugin
   */
  object VersionOfApi extends Tag(ApiVersion.v2_0_0.toString)
  object ApiEndpoint1 extends Tag(nameOf(Implementations2_2_0.getMessageDocs))

  override implicit val formats = LocalMappedConnector.formats

  feature(s"test $ApiEndpoint1 version $VersionOfApi - get all MessageDocs of stored_procedure_vDec2019 connector.") {
    scenario("We will call the endpoint getMessageDocs to get all MessageDocs and deserialize to InBound instances", ApiEndpoint1, VersionOfApi) {

      When("We make a request v2.2.0 get messageDocs")
      val request = (v2_2Request / "message-docs" / "stored_procedure_vDec2019").GET
      val response = makeGetRequest(request)
      Then("We should get a 200")
      response.code should equal(200)

      val value: JValue = response.body \ "message_docs"

//      val zson = fromURL(request.url).mkString
//      val value: JValue = json.parse(zson) \ "message_docs"


      noException should be thrownBy {
        val jsons = value.extract[List[MessageDocJson]]
        jsons.map(it => getInOutBound(it.process, it.example_outbound_message, it.example_inbound_message))
      }

    }

  }


  private def getInOutBound(processName: String, outBoundJson: JValue, inBoundJson: JValue): (String, AnyRef, AnyRef) = {

    val method = processName.replace("obp.", "")
    val outBoundManifest = ManifestFactory.classType[AnyRef](Class.forName(s"com.openbankproject.commons.dto.OutBound${method.capitalize}"));
    val inBoundManifest = ManifestFactory.classType[AnyRef](Class.forName(s"com.openbankproject.commons.dto.InBound${method.capitalize}"));
    println(s"processName: $processName")

    val outBound = outBoundJson.extract(formats, outBoundManifest)
    println("outBoundJson:")
    println(json.prettyRender(outBoundJson))

    val inBound = inBoundJson.extract(formats, inBoundManifest)
    println("inBoundJson:")
    println(json.prettyRender(inBoundJson))
    (method, outBound, inBound)
  }

}
