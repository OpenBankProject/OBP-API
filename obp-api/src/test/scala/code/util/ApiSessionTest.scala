/**
Open Bank Project - API
Copyright (C) 2011-2019, TESOBE GmbH.

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

package code.util

import code.api.util.{ApiSession, CallContext}
import code.util.Helper.MdcLoggable
import org.scalatest.{FeatureSpec, GivenWhenThen, Matchers}

class ApiSessionTest extends FeatureSpec with Matchers with GivenWhenThen with MdcLoggable  {
  
  feature("test ApiSession.createSessionId method") 
  {
    scenario("update the CallContext Session Id") 
    {
      val callContext = CallContext() 
      
      val callContextUpdated = ApiSession.createSessionId(Some(callContext))
      callContext.sessionId should be (None)
      callContextUpdated.get.sessionId should not be (None)
    }
  }
  
  feature("test ApiSession.updateCallContextSessionId method") 
  {
    scenario("update the CallContext Session Id") 
    {
      val callContext = CallContext() 
      
      val callContextUpdated = ApiSession.updateSessionId(Some(callContext), "12345")
      callContext.sessionId should be (None)
      callContextUpdated.get.sessionId should be (Some("12345"))
    }
  }
  
  feature("test CallContext toString secure logging masking") 
  {
    scenario("toString should mask sensitive data") 
    {
      val callContextWithSensitiveData = CallContext(
        directLoginParams = Map("password" -> "supersecret"),
        oAuthParams = Map("client_secret" -> "my_client_secret")
      )
      
      val toStringResult = callContextWithSensitiveData.toString
      
      // Verify that sensitive data is masked - should NOT contain the actual sensitive values
      toStringResult should not contain "supersecret"
      toStringResult should not contain "my_client_secret"
      
      // Verify that the result contains the case class structure (not just object reference)
      toStringResult should include("CallContext")
      
      // Verify that masking occurs by checking for masked patterns or lack of sensitive data
      val containsActualSensitiveData = toStringResult.contains("supersecret") ||
                                       toStringResult.contains("my_client_secret")
      
      containsActualSensitiveData should be (false)
    }
  }
}