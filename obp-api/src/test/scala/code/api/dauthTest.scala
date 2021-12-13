package code.api

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
  val wrongPublicKeyJwt = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzbWFydF9jb250cmFjdF9hZGRyZXNzIjoiMHhlN2YxNzI1RTc3MzRDRTI4OEY4MzY3ZTFCYjE0M0U5MGJiM0YwNTEyIiwibmV0d29ya19uYW1lIjoiRVRIRVJFVU0iLCJtc2dfc2VuZGVyIjoiMHhlOTA5ODA5MjdmMTcyNUU3NzM0Q0UyODhGODM2N2UxQmIxNDNFOTBmaGt1NzY3IiwiY29uc3VtZXJfa2V5IjoiMHgxOTI1NWE0ZWMzMWU4OWNlYTU0ZDFmMTI1ZGI3NTM2ZTg3NGFiNGE5NmI0ZDRmNjQzODY2OGI2YmIxMGE2YWRiIiwidGltZXN0YW1wIjoiMjAxOC0wOC0yMFQxNDoxMzo0MFoiLCJyZXF1ZXN0X2lkIjoiMFhlODc2OTg3Njk0MzI4NzYzNDkyODc2MzQ4OTI4NzM2NDk3ODY5MjczNjQ5In0.Wa1aaoHKSWAKcX_tnueVf3PQze-BcPeZ_EfhovxRvv9WIGn86WSShT2x2W_VGfySYJhfYYhpg2N-l-trA2T9jru5u3Mp_yQcSJZ9D1kCg3kmy2AqYp_qbPIakVQthWo1Ys7hkGB6bZHau87BOXv9v4v97LrpRfva5lw62qzdhpN67lTK1hdUc677nsneFdtnA78Ddm6u_ta_KIf_mC0t-lxSfUcuLb7LQgp2biYyYMgVB7dyexPQ7ZSBa2B8ARGXBXo0iOCjvi-Su4IYUomklRwKWYI-waaigaCDd9FZZQyDfjEySQToAG7UO0mPBRQiIVrSumecz1VESlO_c0Bm0w" 
  
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
  val jwt = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzbWFydF9jb250cmFjdF9hZGRyZXNzIjoiMHhlN2YxNzI1RTc3MzRDRTI4OEY4MzY3ZTFCYjE0M0U5MGJiM0YwNTEyIiwibmV0d29ya19uYW1lIjoiRVRIRVJFVU0iLCJtc2dfc2VuZGVyIjoiMHhlOTA5ODA5MjdmMTcyNUU3NzM0Q0UyODhGODM2N2UxQmIxNDNFOTBmaGt1NzY3IiwiY29uc3VtZXJfa2V5IjoiMHgxOTI1NWE0ZWMzMWU4OWNlYTU0ZDFmMTI1ZGI3NTM2ZTg3NGFiNGE5NmI0ZDRmNjQzODY2OGI2YmIxMGE2YWRiIiwidGltZXN0YW1wIjoiMjAxOC0wOC0yMFQxNDoxMzo0MFoiLCJyZXF1ZXN0X2lkIjoiMFhlODc2OTg3Njk0MzI4NzYzNDkyODc2MzQ4OTI4NzM2NDk3ODY5MjczNjQ5In0.dkAy32AjskvOaQ-gzXEiwU7RslJIawrOPsFsrqAlGHeKr6NyLJPJLYQ6e8_ABK2N-Pw43PiIzefV5QdiGxtWXCuVMRldrdNVC2VdBLVicDVWOmHCLyQ-mFbUvBR3wx8ZsU9nauEchVBsI9UY-_YYYI4yF9DsUazdMoesIjDl-zr68Dzm_ljnxv1wL4fbFpT7wq7MRFQBSy5UTN9o0JxGN_sm9dYeGf-kINQP8-zmJKQM0CRlMegdcBJdonSjlJDib_cKdbyeiSYwWTnqu9pAsOKarY7sX7uIa4A2hVkGY9hkSaGoeQcTxUHFTrJFdEeDm2num2MNLjFul3roAEG0Uw" 

  val invalidJwt = ("DAuth", ("%s").format(wrongPublicKeyJwt))
  val validJwt = ("DAuth", ("%s").format(jwt))

  def dauthRequest = baseRequest / "obp" / "v2.0.0" / "users" /"current" 
  def dauthNonBlockingRequest = baseRequest / "obp" / "v3.0.0" / "users" / "current"

  feature("DAuth Testing") {

    scenario("Missing parameter token in a blocking way") {
      When("We try to login without parameter token in a Header")

        When("We try to login with an invalid JWT")
        val responseInvalid = makeGetRequest(dauthRequest, List(invalidJwt))
        Then("We should get a 400 - Bad Request")
        logger.debug("-----------------------------------------")
        logger.debug("responseInvalid response: "+ responseInvalid)
        logger.debug("-----------------------------------------")
        responseInvalid.code should equal(400)
        responseInvalid.toString contains (ErrorMessages.DAuthJwtTokenIsNotValid) should be (true)

        When("We try to login with an valid JWT")
        val responseValidJwt = makeGetRequest(dauthRequest, List(validJwt))
        logger.debug("-----------------------------------------")
        logger.debug("responseValidJwt response: " + responseValidJwt)
        logger.debug("-----------------------------------------")
        responseValidJwt.code should equal(200)

        When("We try to login with an invalid JWT")
        val responseNonBlockingInvalid = makeGetRequest(dauthNonBlockingRequest, List(invalidJwt))
        Then("We should get a 400 - Bad Request")
        logger.debug("-----------------------------------------")
        logger.debug("responseNonBlockingInvalid responseNonBlocking: " + responseNonBlockingInvalid)
        logger.debug("-----------------------------------------")
        responseNonBlockingInvalid.code should equal(401)
        responseNonBlockingInvalid.toString contains (ErrorMessages.DAuthJwtTokenIsNotValid) should be (true)

        When("We try to login with an valid JWT")
        val responseNonBlockingValidJwt = makeGetRequest(dauthNonBlockingRequest, List(validJwt))
        logger.debug("-----------------------------------------")
        logger.debug("responseNonBlockingValidJwt responseNonBlocking: " + responseNonBlockingValidJwt)
        logger.debug("-----------------------------------------")
        responseValidJwt.code should equal(200)
      }
      
  }
  

 
}