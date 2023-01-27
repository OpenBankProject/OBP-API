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
package code.api.v5_1_0

import okhttp3.{OkHttpClient, Request}


class IndexPageTest extends V510ServerSetup {

  /**d
   * Test tags
   * Example: To run tests with tag "getPermissions":
   * 	mvn test -D tagsToInclude
   *
   *  This is made possible by the scalatest maven plugin
   */
/*

  feature(s"Test the response of the page http://${server.host}:${server.port}/index.html") {
    scenario(s"We try to load the page at http://${server.host}:${server.port}/index.html") {
      When("We make the request")
      val client = new OkHttpClient
      val request = new Request.Builder().url(s"http://${server.host}:${server.port}/index.html").build
      val responseFirst = client.newCall(request).execute
      val startTime: Long = System.currentTimeMillis()
      val responseSecond = client.newCall(request).execute
      val endTime: Long = System.currentTimeMillis()
      Then("We should get a 200")
      responseSecond.code should equal(200)
      val duration: Long = endTime - startTime
      And(s"And duration($duration) is less than 1000 ms")
      println(s"Duration of the loading the page http://${server.host}:${server.port}/index.html is: $duration ms")
      // duration should be <= 1000L // TODO Make this check adjustable
    } 
  }
*/

}
