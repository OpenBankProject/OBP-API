/**
Open Bank Project - API
Copyright (C) 2011-2019, TESOBE GmbH

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
TESOBE GmbH
Osloerstrasse 16/17
Berlin 13359, Germany

This product includes software developed at
TESOBE (http://www.tesobe.com/)
  */
package code.api.v3_1_0

import com.openbankproject.commons.model.ErrorMessage
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON
import code.api.util.APIUtil.OAuth._
import com.openbankproject.commons.util.ApiVersion
import code.api.util.ErrorMessages.{InvalidJsonFormat, UserNotLoggedIn}
import code.api.v2_0_0.CreateMeetingJson
import code.api.v3_1_0.OBPAPI3_1_0.Implementations3_1_0
import com.github.dwickern.macros.NameOf.nameOf
import net.liftweb.json.Serialization.write
import org.scalatest.Tag

class MeetingsTest extends V310ServerSetup {

  /**
    * Test tags
    * Example: To run tests with tag "getPermissions":
    * 	mvn test -D tagsToInclude
    *
    *  This is made possible by the scalatest maven plugin
    */
  object VersionOfApi extends Tag(ApiVersion.v3_1_0.toString)
  object ApiEndpoint1 extends Tag(nameOf(Implementations3_1_0.createMeeting))
  object ApiEndpoint2 extends Tag(nameOf(Implementations3_1_0.getMeeting))
  object ApiEndpoint3 extends Tag(nameOf(Implementations3_1_0.getMeetings))

  feature("Test Create Meetings, get Meetings - v3.1.0")
  {
    scenario("We will Create Meetings  - NOT logged in", ApiEndpoint1, VersionOfApi) {
      When("We make a request v3.1.0")
      val request310 = (v3_1_0_Request / "banks" / randomBankId / "meetings" ).POST
      val createMeetingJson = SwaggerDefinitionsJSON.createMeetingJsonV310
      val response310 = makePostRequest(request310, write(createMeetingJson))
      Then("We should get a 401")
      response310.code should equal(401)
      And("error should be " + UserNotLoggedIn)
      response310.body.extract[ErrorMessage].message should equal (UserNotLoggedIn)
    }
    
    scenario("We will Create Meetings  - Wrong Json format", ApiEndpoint1, VersionOfApi) {
      When("We make a request v3.1.0")
      val request310 = (v3_1_0_Request / "banks" / randomBankId / "meetings" ).POST<@(user1)
      //Following is totally wrong json
      val createMeetingJson = SwaggerDefinitionsJSON.inviteeJson
      val response310 = makePostRequest(request310, write(createMeetingJson))
      Then("We should get a 400")
      response310.code should equal(400)
      And("error should be " + s"$InvalidJsonFormat The Json body should be the $CreateMeetingJson ")
      response310.body.extract[ErrorMessage].message should startWith (s"$InvalidJsonFormat The Json body should be the $CreateMeetingJson ")
    }
    
    scenario("We will Create Meetings and Get meetings back", ApiEndpoint1, VersionOfApi) {
      When("We make a request v3.1.0")
      
      val bankId = randomBankId
      val request310 = (v3_1_0_Request / "banks" / bankId / "meetings" ).POST<@(user1)
      val createMeetingJson = SwaggerDefinitionsJSON.createMeetingJsonV310
      val response310 = makePostRequest(request310, write(createMeetingJson))
      Then("We should get a 201")
      response310.code should equal(201)
      val meetingJsonV310: MeetingJsonV310 = response310.body.extract[MeetingJsonV310]
      meetingJsonV310.creator shouldBe createMeetingJson.creator
      meetingJsonV310.invitees shouldBe createMeetingJson.invitees
      
      Then("We can test getMeetings")
      val requestGetAll310 = (v3_1_0_Request / "banks" / bankId / "meetings" ).GET<@(user1)
      val responseGetAll310 = makeGetRequest(requestGetAll310)
      Then("We should get a 200")
      responseGetAll310.code should equal(200)
      val meetingsJsonV310: MeetingsJsonV310 = responseGetAll310.body.extract[MeetingsJsonV310]
      meetingsJsonV310.meetings.head shouldBe meetingJsonV310
      
      Then("We can test getMeetings")
      val meetingId = meetingJsonV310.meeting_id
      val requestGetOne310 = (v3_1_0_Request / "banks" / bankId / "meetings" /meetingId ).GET<@(user1)
      val responseGetOne310 = makeGetRequest(requestGetOne310)
      Then("We should get a 200")
      responseGetOne310.code should equal(200)
      val meetingGetJsonV310: MeetingJsonV310 = responseGetOne310.body.extract[MeetingJsonV310]
      meetingGetJsonV310 shouldBe meetingJsonV310
    }
  }

}
