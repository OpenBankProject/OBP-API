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
import code.api.Constant._
import code.api.util.{CallContext, CustomJsonFormats}
import code.bankconnectors._
import code.setup.{DefaultConnectorTestSetup, DefaultUsers, ServerSetup}
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.model.{AccountId, BankId, BankIdAccountId, InboundAccount, InboundAccountCommons}
import net.liftweb.common.{Box, Full}

import scala.collection.immutable.List
import com.openbankproject.commons.ExecutionContext.Implicits.global
import org.json4s.Formats

import scala.concurrent.Future

/**
  * Created by zhanghongwei on 14/07/2017.
  */
object MockedCbsConnector extends ServerSetup  
  with Connector with DefaultUsers  
  with DefaultConnectorTestSetup with MdcLoggable {
  override implicit val formats: Formats = CustomJsonFormats.nullTolerateFormats
  implicit override val nameOfConnector = "MockedCardConnector"
  
  //These bank id and account ids are real data over adapter  
  val bankIdAccountId = BankIdAccountId(BankId("obp-bank-x-gh"),AccountId("KOa4M8UfjUuWPIXwPXYPpy5FoFcTUwpfHgXC1qpSluc"))
  val bankIdAccountId2 = BankIdAccountId(BankId("obp-bank-x-gh"),AccountId("tKWSUBy6sha3Vhxc/vw9OK96a0RprtoxUuObMYR29TI"))
  
  override def getBankAccountsForUserLegacy(provider: String, username:String, callContext: Option[CallContext]): Box[(List[InboundAccount], Option[CallContext])] = {
    Full(
      List(InboundAccountCommons(
        bankId = bankIdAccountId.bankId.value,
        branchId = "",
        accountId = bankIdAccountId.accountId.value,
        accountNumber = "",
        accountType = "",
        balanceAmount = "",
        balanceCurrency = "",
        owners = List(""),
        viewsToGenerate = SYSTEM_STANDARD_VIEW_ID :: SYSTEM_OWNER_VIEW_ID :: CUSTOM_PUBLIC_VIEW_ID :: SYSTEM_ACCOUNTANT_VIEW_ID :: SYSTEM_AUDITOR_VIEW_ID :: Nil,
        bankRoutingScheme = "",
        bankRoutingAddress = "",
        branchRoutingScheme = "",
        branchRoutingAddress = "",
        accountRoutingScheme = "",
        accountRoutingAddress = "",
      ),InboundAccountCommons(
        bankId = bankIdAccountId2.bankId.value,
        branchId = "",
        accountId = bankIdAccountId2.accountId.value,
        accountNumber = "",
        accountType = "",
        balanceAmount = "",
        balanceCurrency = "",
        owners = List(""),
        viewsToGenerate = SYSTEM_STANDARD_VIEW_ID :: SYSTEM_OWNER_VIEW_ID :: CUSTOM_PUBLIC_VIEW_ID :: SYSTEM_ACCOUNTANT_VIEW_ID :: SYSTEM_AUDITOR_VIEW_ID :: Nil,
        bankRoutingScheme = "",
        bankRoutingAddress = "",
        branchRoutingScheme = "",
        branchRoutingAddress = "",
        accountRoutingScheme = "",
        accountRoutingAddress = "",
      )),
      callContext
    )
  }

  override def getBankAccountsForUser(provider: String, username:String, callContext: Option[CallContext]):  Future[Box[(List[InboundAccount], Option[CallContext])]] = Future{
    getBankAccountsForUserLegacy(provider, username,callContext)
  }
}

