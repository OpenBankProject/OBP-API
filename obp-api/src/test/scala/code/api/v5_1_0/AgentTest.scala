package code.api.v5_1_0

import org.json4s._
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON.{postAgentJsonV510, putAgentJsonV510}
import code.api.util.ErrorMessages.{BankNotFound, UserHasMissingRoles, AuthenticatedUserIsRequired}
import code.api.v5_1_0.Http4s510.Implementations5_1_0
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.ApiVersion
import org.json4s.native.Serialization.write
import org.scalatest.Tag
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole.{canUpdateAgentStatusAtAnyBank, canUpdateAgentStatusAtOneBank}
import code.entitlement.Entitlement

class AgentTest extends V510ServerSetup {
  /**
    * Test tags
    * Example: To run tests with tag "getPermissions":
    * 	mvn test -D tagsToInclude
    *
    *  This is made possible by the scalatest maven plugin
    */
  object VersionOfApi extends Tag(ApiVersion.v5_1_0.toString)
  object CreateAgent extends Tag(nameOf(Implementations5_1_0.createAgent))
  object UpdateAgentStatus extends Tag(nameOf(Implementations5_1_0.updateAgentStatus))
  object GetAgent extends Tag(nameOf(Implementations5_1_0.getAgent))
  object GetAgents extends Tag(nameOf(Implementations5_1_0.getAgents))

  feature(s"test all endpoints") {
    scenario(s"We will test all endpoints logins", CreateAgent, UpdateAgentStatus,GetAgent, GetAgents, VersionOfApi) {
      val request = (v5_1_0_Request / "banks" / "BANK_ID" / "agents").POST
      val response = makePostRequest(request, write(postAgentJsonV510))
      response.code should equal(401)
      response.body.extract[ErrorMessage].message should equal(AuthenticatedUserIsRequired)
      
      {
        val request = (v5_1_0_Request / "banks" / "BANK_ID" / "agents"/ "agentId").PUT
        val response = makePutRequest(request, write(putAgentJsonV510))
        response.code should equal(401)
        response.body.extract[ErrorMessage].message should equal(AuthenticatedUserIsRequired) 
      }
      
      {
        val request = (v5_1_0_Request / "banks" / "BANK_ID" / "agents").GET
        val response = makeGetRequest(request)
        response.code should equal(404)
        response.body.extract[ErrorMessage].message contains (BankNotFound) shouldBe(true) 
      }
      
      {
        val request = (v5_1_0_Request / "banks" / "BANK_ID" / "agents"/"agentId").GET
        val response = makeGetRequest(request)
        response.code should equal(401)
        response.body.extract[ErrorMessage].message should equal(AuthenticatedUserIsRequired) 
      }
    }
    scenario(s"We will test all endpoints wrong Bankid", CreateAgent, UpdateAgentStatus,GetAgent, GetAgents, VersionOfApi) {
      val request = (v5_1_0_Request / "banks" / "BANK_ID" / "agents").POST <@ (user1)
      val response = makePostRequest(request, write(postAgentJsonV510))
      response.code should equal(404)
      response.body.extract[ErrorMessage].message contains (BankNotFound) shouldBe(true)

      {
        val request = (v5_1_0_Request / "banks" / "BANK_ID" / "agents"/ "agentId").PUT <@ (user1)
        val response = makePutRequest(request, write(putAgentJsonV510))
        response.code should equal(404)
        response.body.extract[ErrorMessage].message contains (BankNotFound) shouldBe(true)
      }
      
      {
        val request = (v5_1_0_Request / "banks" / "BANK_ID" / "agents").GET <@ (user1)
        val response = makeGetRequest(request)
        response.code should equal(404)
        response.body.extract[ErrorMessage].message contains (BankNotFound) shouldBe(true) 
      }
      
      {
        val request = (v5_1_0_Request / "banks" / "BANK_ID" / "agents"/"agentId").GET <@ (user1)
        val response = makeGetRequest(request)
        response.code should equal(404)
        response.body.extract[ErrorMessage].message contains (BankNotFound) shouldBe(true)
      }
    }
    
    scenario(s"We will test all endpoints roles", UpdateAgentStatus) {
      val bankId =testBankId1.value
      val bankId2 =testBankId2.value
      val request = (v5_1_0_Request / "banks" / bankId / "agents").POST  <@ (user1) 
      val response = makePostRequest(request, write(postAgentJsonV510))
      response.code should equal(201)
      val agentId = response.body.extract[AgentJsonV510].agent_id
      
      {
        val request = (v5_1_0_Request / "banks" / bankId / "agents"/ agentId).PUT <@user1
        val response = makePutRequest(request, write(putAgentJsonV510))
        response.code should equal(403)
        response.body.extract[ErrorMessage].message contains UserHasMissingRoles shouldBe(true)
      }

      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, canUpdateAgentStatusAtOneBank.toString)

      {
        val request = (v5_1_0_Request / "banks" / bankId / "agents"/ agentId).PUT <@user1
        val response = makePutRequest(request, write(putAgentJsonV510))
        response.code should equal(200)
      }

      {
        val request = (v5_1_0_Request / "banks" / bankId2 / "agents"/ agentId).PUT <@user1
        val response = makePutRequest(request, write(putAgentJsonV510))
        response.code should equal(403)
        response.body.extract[ErrorMessage].message contains UserHasMissingRoles shouldBe(true)
      }
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, canUpdateAgentStatusAtAnyBank.toString)

      {
        val request = (v5_1_0_Request / "banks" / bankId2 / "agents"/ agentId).PUT <@user1
        val response = makePutRequest(request, write(putAgentJsonV510))
        response.code should equal(200)
      }
    }
    
    scenario(s"We will test all endpoints successful cases", UpdateAgentStatus) {
      val bankId =randomBankId
      val request = (v5_1_0_Request / "banks" / bankId / "agents").POST <@ (user1) 
      val response = makePostRequest(request, write(postAgentJsonV510))
      response.code should equal(201)
      val agentId = response.body.extract[AgentJsonV510].agent_id

      {
        val request = (v5_1_0_Request / "banks" / bankId / "agents"/ agentId).GET <@ (user1)
        val response = makeGetRequest(request)
        response.code should equal(200)
        response.body.extract[AgentJsonV510].agent_id should equal(agentId)
        response.body.extract[AgentJsonV510].legal_name should equal(postAgentJsonV510.legal_name)
        response.body.extract[AgentJsonV510].currency should equal(postAgentJsonV510.currency)
        response.body.extract[AgentJsonV510].mobile_phone_number should equal(postAgentJsonV510.mobile_phone_number)
        response.body.extract[AgentJsonV510].is_pending_agent should equal(true)
        response.body.extract[AgentJsonV510].is_confirmed_agent should equal(false)
      }
      
      {
        val request = (v5_1_0_Request / "banks" / bankId / "agents").GET
        val response = makeGetRequest(request)
        response.code should equal(200)
        response.body.extract[MinimalAgentsJsonV510].agents.length shouldBe(0)
      }

      {
        Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, canUpdateAgentStatusAtAnyBank.toString)
        val request = (v5_1_0_Request / "banks" / bankId / "agents"/ agentId).PUT <@user1
        val response = makePutRequest(request, write(putAgentJsonV510))
        response.code should equal(200)
        response.body.extract[AgentJsonV510].is_pending_agent should equal(putAgentJsonV510.is_pending_agent)
        response.body.extract[AgentJsonV510].is_confirmed_agent should equal(putAgentJsonV510.is_confirmed_agent)
      }

      //After updated the status, we can get the agents back ;
      {
        val request = (v5_1_0_Request / "banks" / bankId / "agents").GET
        val response = makeGetRequest(request)
        response.code should equal(200)
        response.body.extract[MinimalAgentsJsonV510].agents.length shouldBe(1)
        response.body.extract[MinimalAgentsJsonV510].agents.head.agent_number should equal(postAgentJsonV510.agent_number)
        response.body.extract[MinimalAgentsJsonV510].agents.head.legal_name should equal(postAgentJsonV510.legal_name)
      }
      
      
    }
  }
  
}