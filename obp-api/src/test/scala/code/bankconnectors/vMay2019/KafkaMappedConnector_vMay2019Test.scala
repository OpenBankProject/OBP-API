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


import code.api.JSONFactoryGateway.PayloadOfJwtJSON
import code.api.util.{APIUtil, CallContext, CustomJsonFormats}
import code.bankconnectors.Connector
import code.bankconnectors.vMar2017.InboundBank
import code.bankconnectors.vSept2018._
import code.kafka.KafkaHelper
import code.setup.{KafkaSetup, ServerSetupWithTestData}
import com.openbankproject.commons.dto.InBoundGetBanks
import com.openbankproject.commons.model._
import net.liftweb.common.{Box, Failure, Full}
import org.scalatest.Tag

class KafkaMappedConnector_vMay2019Test extends KafkaSetup with ServerSetupWithTestData {

  override implicit val formats = CustomJsonFormats.formats

  object kafkaTest extends Tag("kafkaTest")

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


  feature("Send and retrieve message") {
    if (PropsConnectorVersion == "mapped") {
      ignore("ignore test getObpConnectorLoopback, if it is mapped connector", kafkaTest) {}
    } else
      scenario("1st test `getObpConnectorLoopback` method, there no need Adapter message for this method!", kafkaTest) {
        //This method is only used for `kafka` connector, should first set `connector=kafka_vSept2018` in test.default.props. 
        //and also need to set up `api_instance_id` and `remotedata.timeout` field for it.
        val propsApiInstanceId = APIUtil.getPropsValue("api_instance_id").openOrThrowException("connector props filed `api_instance_id` not set")
        val propsRemotedataTimeout = APIUtil.getPropsValue("remotedata.timeout").openOrThrowException("connector props filed `remotedata.timeout` not set")

        PropsConnectorVersion contains ("kafka") should be(true)
        propsApiInstanceId should be("1")
        propsRemotedataTimeout should be("10")

        When("We call this method, and get the response. ")
        val future = KafkaHelper.echoKafkaServer
        val result = future.getContent

        Then("If it return value successfully, that mean api <--> kafka is working well. We only need check one filed of response.")
        val connectorVersion = result.connectorVersion
        connectorVersion should be(PropsConnectorVersion)

        Then("For KafkaMappedConnector_vSept2018 connector, we need to make these two methods work `getAuthInfoFirstCbsCall` and `getAuthInfo`")

        val firstAuthInfo: Box[AuthInfo] = for {
          firstGetAuthInfo <- KafkaMappedConnector_vSept2018.getAuthInfoFirstCbsCall("", callContext)
        } yield {
          (firstGetAuthInfo)
        }
        firstAuthInfo.openOrThrowException("firstAuthInfo Can not be empty here. ")

        val authInfo: Box[AuthInfo] = for {
          getAuthInfo <- KafkaMappedConnector_vSept2018.getAuthInfo(callContext)
        } yield {
          getAuthInfo
        }
        authInfo.openOrThrowException("firstAuthInfo Can not be empty here. ")

      }

    if (PropsConnectorVersion == "mapped") {
      ignore("ignore test processRequest, if it is mapped connector", kafkaTest) {}
    } else
      scenario("Send and retrieve message directly to and from kafka", kafkaTest) {
        val emptyStatusMessage = InboundStatusMessage("", "", "", "")
        val inBound = InboundGetBanks(InboundAuthInfo("", ""), Status("", List(emptyStatusMessage)), List(InboundBank("1", "2", "3", "4")))
        When("send a OutboundGetBanks message")

        dispathResponse(inBound)
        val req = OutboundGetBanks(AuthInfo())

        val future = processRequest[InboundGetBanks](req)
        val result: Box[InboundGetBanks] = future.getContent

        result should be(Full(inBound))
      }

  }

  feature("Test the getBank error cases") {

    if (PropsConnectorVersion == "mapped") {
      ignore("ignore test getBanks, if it is mapped connector", kafkaTest) {}
    } else
      scenario(s"test getBanksFuture -- status.hasError", kafkaTest) {
        val inbound = Connector.connector.vend.messageDocs.filter(_.exampleInboundMessage.isInstanceOf[InBoundGetBanks]).map(_.exampleInboundMessage).head.asInstanceOf[InBoundGetBanks]
        //This inBound.status.errorCode != "", so it will throw the error back.
        val expectedValue = Failure("INTERNAL-"+ inbound.status.errorCode+". + CoreBank-Status:" + inbound.status.backendMessages)
        dispathResponse(inbound)
        val future = Connector.connector.vend.getBanks(callContext)

        dispathResponse(inbound)
        val result = future.getContent
        result should be(expectedValue)
      }

    if (PropsConnectorVersion == "mapped") {
      ignore("ignore test getBanksFuture -- status.hasNoError,  if it is mapped connector", kafkaTest) {}
    } else 
      scenario(s"test getBanksFuture -- status.hasNoError", kafkaTest) {
        val inbound = Connector.connector.vend.messageDocs
          .filter(_.exampleInboundMessage.isInstanceOf[InBoundGetBanks])
          .map(_.exampleInboundMessage).head.asInstanceOf[InBoundGetBanks]
          .copy(status = Status("", Nil)) // This will set errorCode to "", no it works
        //This inBound.status.errorCode != "", so it will throw the error back.
        val expectedValue = Full(inbound.data.head.bankId).toString
        dispathResponse(inbound)
        val future = Connector.connector.vend.getBanks(callContext)
  
        dispathResponse(inbound)
        val result = future.getContent
        result.map(_._1.head.bankId).toString should be(expectedValue)
      }

  }
}