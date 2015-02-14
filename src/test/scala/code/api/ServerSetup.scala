/**
Open Bank Project - API
Copyright (C) 2011, 2013, TESOBE / Music Pictures Ltd

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
TESOBE / Music Pictures Ltd
Osloerstrasse 16/17
Berlin 13359, Germany

  This product includes software developed at
  TESOBE (http://www.tesobe.com/)
  by
  Simon Redfern : simon AT tesobe DOT com
  Stefan Bethge : stefan AT tesobe DOT com
  Everett Sochowski : everett AT tesobe DOT com
  Ayoub Benali: ayoub AT tesobe DOT com

 */

package code.api.test

import code.TestServer
import code.api.{DefaultConnectorTestSetup, TestConnectorSetup, LocalConnectorTestSetup}
import org.scalatest._
import dispatch._
import net.liftweb.json.{Serialization, NoTypeHints}
import net.liftweb.common._

trait ServerSetup extends FeatureSpec with SendServerRequests
  with BeforeAndAfterEach with GivenWhenThen
  with BeforeAndAfterAll
  with ShouldMatchers with Loggable {

  var server = TestServer
  implicit val formats = Serialization.formats(NoTypeHints)
  val h = Http
  def baseRequest = host(server.host, server.port)

}

trait ServerSetupWithTestData extends ServerSetup with DefaultConnectorTestSetup {

  override def beforeEach() = {
    super.beforeEach()

    implicit val dateFormats = net.liftweb.json.DefaultFormats
    //create fake data for the tests

    //fake banks
    val banks = createBanks()
    //fake bank accounts
    val accounts = createAccounts(banks)
    //fake transactions
    createTransactions(accounts)
  }

  override def afterEach() = {
    super.afterEach()
    wipeTestData()
  }

}