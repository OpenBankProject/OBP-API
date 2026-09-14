/**
Open Bank Project - API
Copyright (C) 2011-2026, TESOBE GmbH.

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.

Email: contact@tesobe.com
TESOBE GmbH.
Osloer Strasse 16/17
Berlin 13359, Germany

This product includes software developed at
TESOBE (http://www.tesobe.com/)

  */

package code.connector

import org.json4s._
import code.api.v2_2_0.JSONFactory220.MessageDocJson
import code.api.v2_2_0.Http4s220.Implementations2_2_0
import code.api.v2_2_0.V220ServerSetup
import code.bankconnectors.LocalMappedConnector
import code.setup.DefaultUsers
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.util.ApiVersion
import com.openbankproject.commons.util.json
import org.json4s.JValue
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
