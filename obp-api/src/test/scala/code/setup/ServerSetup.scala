/**
Open Bank Project - API
Copyright (C) 2011-2018, TESOBE Ltd.

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
TESOBE Ltd.
Osloer Strasse 16/17
Berlin 13359, Germany

This product includes software developed at
TESOBE (http://www.tesobe.com/)

  */

package code.setup

import _root_.net.liftweb.json.JsonAST.JObject
import code.TestServer
import code.api.util.{APIUtil, CustomJsonFormats}
import code.api.util.APIUtil._
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.model.{AccountId, BankId}
import dispatch._
import net.liftweb.common.{Empty, Full}
import net.liftweb.json.JsonDSL._
import org.scalatest._

trait ServerSetup extends FeatureSpec with SendServerRequests
  with BeforeAndAfterEach with GivenWhenThen
  with BeforeAndAfterAll
  with Matchers with MdcLoggable with CustomJsonFormats{

  val server = TestServer
  def baseRequest = host(server.host, server.port)
  val secured = APIUtil.getPropsAsBoolValue("external.https", false)
  def externalBaseRequest = (server.externalHost, server.externalPort) match {
    case (Full(h), Full(p)) if secured  => host(h, p).secure
    case (Full(h), Full(p)) if !secured => host(h, p)
    case (Full(h), Empty) if secured  => host(h).secure
    case (Full(h), Empty) if !secured => host(h)
    case (Full(h), Empty) => host(h)
    case _ => baseRequest
  }
  
  val exampleDate = DateWithSecondsExampleObject
  
  // @code.setup.TestConnectorSetup.createBanks we can know, the bankIds in test database.
  val testBankId1 = BankId("testBank1")
  val testBankId2 = BankId("testBank2")
  
 // @code.setup.TestConnectorSetup.createAccounts we can know, the accountIds in test database.
  val testAccountId1 = AccountId("testAccount1")
  val testAccountId2 = AccountId("testAccount2")
  
  val mockCustomerNumber1 = "93934903201"
  val mockCustomerNumber2 = "93934903202"
  
  val mockCustomerNumber = "93934903208565488"
  val mockCustomerId = "cba6c9ef-73fa-4032-9546-c6f6496b354a"
  
  val emptyJSON : JObject = ("error" -> "empty List")
  val errorAPIResponse = new APIResponse(400,emptyJSON)
  
}

trait ServerSetupWithTestData extends ServerSetup with DefaultConnectorTestSetup with DefaultUsers{

  override def beforeEach() = {
    super.beforeEach()
    //create fake data for the tests
    //fake banks
    val banks = createBanks()
    //fake bank accounts
    val accounts = createAccounts(resourceUser1, banks)
    //fake transactions
    createTransactions(accounts)
    //fake transactionRequests
    createTransactionRequests(accounts)
  }

  override def afterEach() = {
    super.afterEach()
    wipeTestData()
  }

}