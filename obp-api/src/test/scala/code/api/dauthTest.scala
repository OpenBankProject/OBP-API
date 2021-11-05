package code.api

import code.api.util.APIUtil.OAuth._
import code.api.util.ErrorMessages
import code.setup.{DefaultUsers, PropsReset, ServerSetup}
import org.scalatest._

class dauthTest extends ServerSetup with BeforeAndAfter with DefaultUsers with PropsReset{
  
  val accessControlOriginHeader = ("Access-Control-Allow-Origin", "*")
  /* Payload data. verified by wrong secret "123" -- show : DAuthJwtTokenIsNotValid
    {
  "smart_contract_address": "0xe7f1725E7734CE288F8367e1Bb143E90bb3F0512",
  "network_name": "ETHEREUM",
  "msg_sender": "0xe90980927f1725E7734CE288F8367e1Bb143E90fhku767",
  "consumer_key": "0x19255a4ec31e89cea54d1f125db7536e874ab4a96b4d4f6438668b6bb10a6adb",
  "timestamp": "2018-08-20T14:13:40Z",
  "request_id": "0Xe876987694328763492876348928736497869273649"
}
    */
  val invalidSecretJwt = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzbWFydF9jb250cmFjdF9hZGRyZXNzIjoiMHhlN2YxNzI1RTc3MzRDRTI4OEY4MzY3ZTFCYjE0M0U5MGJiM0YwNTEyIiwibmV0d29ya19uYW1lIjoiRVRIRVJFVU0iLCJtc2dfc2VuZGVyIjoiMHhlOTA5ODA5MjdmMTcyNUU3NzM0Q0UyODhGODM2N2UxQmIxNDNFOTBmaGt1NzY3IiwiY29uc3VtZXJfaWQiOiIweDE5MjU1YTRlYzMxZTg5Y2VhNTRkMWYxMjVkYjc1MzZlODc0YWI0YTk2YjRkNGY2NDM4NjY4YjZiYjEwYTZhZGIiLCJ0aW1lc3RhbXAiOiIyMDE4LTA4LTIwVDE0OjEzOjQwWiIsInJlcXVlc3RfaWQiOiIwWGU4NzY5ODc2OTQzMjg3NjM0OTI4NzYzNDg5Mjg3MzY0OTc4NjkyNzM2NDkifQ.qWI4DXwa8QDGVPJoPCJehkLKHFA2A4_77JHINluc2tc"
  
  /* Payload data. verified by correct secret "your-at-least-256-bit-secret-token"
  {
  "smart_contract_address": "0xe7f1725E7734CE288F8367e1Bb143E90bb3F05124",
  "network_name": "ETHEREUM",
  "msg_sender": "0xe90980927f1725E7734CE288F8367e1Bb143E90fhku767",
  "consumer_key": "0x19255a4ec31e89cea54d1f125db7536e874ab4a96b4d4f6438668b6bb10a6adb",
  "timestamp": "2018-08-20T14:13:40Z",
  "request_id": "0Xe876987694328763492876348928736497869273649"
}
  */
  val jwt = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzbWFydF9jb250cmFjdF9hZGRyZXNzIjoiMHhlN2YxNzI1RTc3MzRDRTI4OEY4MzY3ZTFCYjE0M0U5MGJiM0YwNTEyNCIsIm5ldHdvcmtfbmFtZSI6IkVUSEVSRVVNIiwibXNnX3NlbmRlciI6IjB4ZTkwOTgwOTI3ZjE3MjVFNzczNENFMjg4RjgzNjdlMUJiMTQzRTkwZmhrdTc2NyIsImNvbnN1bWVyX2tleSI6IjB4MTkyNTVhNGVjMzFlODljZWE1NGQxZjEyNWRiNzUzNmU4NzRhYjRhOTZiNGQ0ZjY0Mzg2NjhiNmJiMTBhNmFkYiIsInRpbWVzdGFtcCI6IjIwMTgtMDgtMjBUMTQ6MTM6NDBaIiwicmVxdWVzdF9pZCI6IjBYZTg3Njk4NzY5NDMyODc2MzQ5Mjg3NjM0ODkyODczNjQ5Nzg2OTI3MzY0OSJ9.Wg2BYYsbWK-MUYToeTUc0GvDwcwnkR6Dh4SV-pMjChk" 

  val invalidJwt = ("Authorization", ("DAuth token=%s").format(invalidSecretJwt))
  val validJwt = ("Authorization", ("DAuth token=%s").format(jwt))
  val missingParameterToken = ("Authorization", ("DAuth wrong_parameter_name=%s").format(jwt))

  def dauthRequest = baseRequest / "obp" / "v2.0.0" / "users" /"current" <@ (user1)
  def dauthNonBlockingRequest = baseRequest / "obp" / "v3.0.0" / "users" / "current" <@ (user1)

  feature("DAuth Testing") {

    scenario("Missing parameter token in a blocking way") {
      When("We try to login without parameter token in a Header")
        val response = makeGetRequest(dauthRequest, List(missingParameterToken))
        Then("We should get a 400 - Bad Request")
        logger.debug("-----------------------------------------")
        logger.debug(response)
        logger.debug("-----------------------------------------")
        response.code should equal(400)
        response.toString contains (ErrorMessages.DAuthMissingParameters) should be (true)

        When("We try to login with an invalid JWT")
        val responseInvalid = makeGetRequest(dauthRequest, List(invalidJwt))
        Then("We should get a 400 - Bad Request")
        logger.debug("-----------------------------------------")
        logger.debug("responseInvalid response: "+responseInvalid)
        logger.debug("-----------------------------------------")
        responseInvalid.code should equal(400)
        responseInvalid.toString contains (ErrorMessages.DAuthJwtTokenIsNotValid) should be (true)

        When("We try to login with an valid JWT")
        val responseValidJwt = makeGetRequest(dauthRequest, List(validJwt))
        logger.debug("-----------------------------------------")
        logger.debug("responseValidJwt response: "+responseValidJwt)
        logger.debug("-----------------------------------------")
        responseValidJwt.code should equal(200)

        When("We try to login without parameter token in a Header")
        val responseNonBlocking = makeGetRequest(dauthNonBlockingRequest, List(missingParameterToken))
        Then("We should get a 400 - Bad Request")
        logger.debug("-----------------------------------------")
        logger.debug("responseNonBlocking: "+ responseNonBlocking)
        logger.debug("-----------------------------------------")
        responseNonBlocking.code should equal(401)
        responseNonBlocking.toString contains (ErrorMessages.DAuthMissingParameters) should be (true)

        When("We try to login with an invalid JWT")
        val responseNonBlockingInvalid = makeGetRequest(dauthNonBlockingRequest, List(invalidJwt))
        Then("We should get a 400 - Bad Request")
        logger.debug("-----------------------------------------")
        logger.debug("responseNonBlockingInvalid responseNonBlocking: "+responseNonBlockingInvalid)
        logger.debug("-----------------------------------------")
        responseNonBlockingInvalid.code should equal(401)
        responseNonBlockingInvalid.toString contains (ErrorMessages.DAuthJwtTokenIsNotValid) should be (true)

        When("We try to login with an valid JWT")
        val responseNonBlockingValidJwt = makeGetRequest(dauthNonBlockingRequest, List(validJwt))
        logger.debug("-----------------------------------------")
        logger.debug("responseNonBlockingValidJwt responseNonBlocking: "+responseNonBlockingValidJwt)
        logger.debug("-----------------------------------------")
        responseValidJwt.code should equal(200)
      }
      
  }
  

 
}