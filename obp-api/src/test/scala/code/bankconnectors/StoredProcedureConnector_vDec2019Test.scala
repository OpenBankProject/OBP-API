package code.bankconnectors.vMay2019

/*
Open Bank Project - API
Copyright (C) 2011-2019, TESOBE GmbH

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program. If not, see http://www.gnu.org/licenses/.

Email: contact@tesobe.com
TESOBE GmbH
Osloerstrasse 16/17
Berlin 13359, Germany
*/


import code.actorsystem.ObpActorInit
import code.api.JSONFactoryGateway.PayloadOfJwtJSON
import code.api.util.{APIUtil, CallContext, CustomJsonFormats}
import code.bankconnectors.Connector
import code.bankconnectors.storedprocedure.StoredProcedureConnector_vDec2019
import code.bankconnectors.vMar2017.InboundBank
import code.bankconnectors.vSept2018._
import code.kafka.KafkaHelper
import code.setup.{DefaultUsers, KafkaSetup, ServerSetupWithTestData}
import com.openbankproject.commons.dto.InBoundGetBanks
import com.openbankproject.commons.model._
import net.liftweb.common.{Box, Failure, Full}
import org.scalatest.Tag

class StoredProcedureConnector_vDec2019Test extends ServerSetupWithTestData with DefaultUsers with ObpActorInit{

  override implicit val formats = CustomJsonFormats.formats

  object StoredProcedureConnector_vDec2019Test extends Tag("StoredProcedureConnector_vDec2019")

  val callContext = Some(
    CallContext(
      gatewayLoginRequestPayload = Some(PayloadOfJwtJSON(
        login_user_name = "",
        is_first = false,
        app_id = "",
        app_name = "",
        time_stamp = "",
        cbs_token = Some(""),
        cbs_id = "",
        session_id = Some(""))),
      user = Full(resourceUser1)
    )
  )

  val PropsConnectorVersion = APIUtil.getPropsValue("connector").openOrThrowException("connector props filed `connector` not set")


  feature("Test all stored_procedure methods") {
    if (PropsConnectorVersion == "stored_procedure_vDec2019") {
//      scenario("test `checkBankAccountExists` method, there no need Adapter message for this method!", StoredProcedureConnector_vDec2019Test) {
//        val checkBankAccountExists = StoredProcedureConnector_vDec2019.checkBankAccountExists(testBankId1,testAccountId1,callContext)
//        getValueFromFuture(checkBankAccountExists)._1.isDefined equals (true)
//      }
//
//      scenario("test `getBankAccounts` method, there no need Adapter message for this method!", StoredProcedureConnector_vDec2019Test) {
//        val checkBankAccountExists = StoredProcedureConnector_vDec2019.checkBankAccountExists(testBankId1,testAccountId1,callContext)
//        getValueFromFuture(checkBankAccountExists)._1.isDefined equals (true)
//      }
//      scenario("test `getBank` method, there no need Adapter message for this method!", StoredProcedureConnector_vDec2019Test) {
//        val transantionRequests210 = StoredProcedureConnector_vDec2019.getBank(testBankId1, callContext)
//        getValueFromFuture(transantionRequests210).isDefined equals (true)
//      }
//
//
//      scenario("test `getBanks` method, there no need Adapter message for this method!", StoredProcedureConnector_vDec2019Test) {
//        val transantionRequests210 = StoredProcedureConnector_vDec2019.getBanks(callContext)
//        getValueFromFuture(transantionRequests210).isDefined equals (true)
//      }
//      
//      scenario("test `getTransactionRequests210` method, there no need Adapter message for this method!", StoredProcedureConnector_vDec2019Test) {
//        val transantionRequests210: Box[(List[TransactionRequest], Option[CallContext])] = StoredProcedureConnector_vDec2019.getTransactionRequests210(resourceUser1, null, callContext)
//        transantionRequests210.isDefined equals(true)
//      }

      scenario("test `getTransactions` method, there no need Adapter message for this method!", StoredProcedureConnector_vDec2019Test) {
        val transactions = StoredProcedureConnector_vDec2019.getTransactions(testBankId1, testAccountId1, callContext, Nil)
        val trans = getValueFromFuture(transactions)._1.openOrThrowException("Should not be empty!")
        trans.head.description.isDefined equals (true)
      }
      
    } else {
      ignore("ignore test getObpConnectorLoopback, if it is mapped connector", StoredProcedureConnector_vDec2019Test) {}
    }
  }
}