package code.api.util

import java.time.{ZoneOffset, ZonedDateTime}

import code.api.util.APIUtil.OAuth._
import code.api.util.JwsUtil.{getPem, signRequest, verifyJws}
import code.api.util.X509.validate
import code.api.v4_0_0.OBPAPI4_0_0.Implementations4_0_0
import code.api.v4_0_0.V400ServerSetup
import com.github.dwickern.macros.NameOf.nameOf
import net.liftweb.common.Full
import net.liftweb.util.Helpers._
import org.scalatest.Tag

class JavaWebSignatureTest extends V400ServerSetup {
  /**
   * Test tags
   * Example: To run tests with tag "getPermissions":
   * 	mvn test -D tagsToInclude
   *
   *  This is made possible by the scalatest maven plugin
   */
  object File extends Tag("JwsUtil.scala")
  object Function1 extends Tag("signRequest")
  object Function2 extends Tag("verifyJws")
  object ApiEndpoint1 extends Tag(nameOf(Implementations4_0_0.verifyRequestSignResponse))
  override def beforeAll() {
    super.beforeAll()
  }

  override def afterAll() {
    super.afterAll()
  }

  feature(s"test functions: $Function1, $Function2 at file $File") {
    scenario("We will sing with a private RSA key and then verify with public RSA key") {
      When("We make a request v4.0.0")
      val httpBody =
        s"""{
           |"instructedAmount": {"currency": "EUR", "amount": "123.50"},
           |"debtorAccount": {"iban": "DE40100100103307118608"},
           |"creditorName": "Merchant123",
           |"creditorAccount": {"iban": "DE02100100109307118603"},
           |"remittanceInformationUnstructured": "Ref Number Merchant"
           |}
           |""".stripMargin


      // x-jws-signature and digest
      val httpParams = signRequest(Full(httpBody), "post", "/berlin-group/v1.3/payments/sepa-credit-transfers", "application/json;charset=utf-8")

      // Hard-coded request headers
      val requestHeaders = httpParams

      validate(getPem(requestHeaders))
      val isVerified = verifyJws(CertificateUtil.rsaPublicKey, httpBody, requestHeaders, "post", "/berlin-group/v1.3/payments/sepa-credit-transfers")
      isVerified should equal(true)
    }
    
    scenario("We will sing with a private RSA key and then verify with public RSA key - fails due to signing time is set in the future") {
      When("We make a request v4.0.0")
      val httpBody =
        s"""{
           |"instructedAmount": {"currency": "EUR", "amount": "123.50"},
           |"debtorAccount": {"iban": "DE40100100103307118608"},
           |"creditorName": "Merchant123",
           |"creditorAccount": {"iban": "DE02100100109307118603"},
           |"remittanceInformationUnstructured": "Ref Number Merchant"
           |}
           |""".stripMargin


      // x-jws-signature and digest
      val httpParams = signRequest(
        Full(httpBody), 
        "post", 
        "/berlin-group/v1.3/payments/sepa-credit-transfers", 
        "application/json;charset=utf-8",
        signingTime = Some(ZonedDateTime.now(ZoneOffset.UTC).plusSeconds(3))
      )

      // Hard-coded request headers
      val requestHeaders = httpParams

      validate(getPem(requestHeaders))
      val isVerified = verifyJws(CertificateUtil.rsaPublicKey, httpBody, requestHeaders, "post", "/berlin-group/v1.3/payments/sepa-credit-transfers")
      isVerified should equal(false)
    }
    
    scenario("We will sing with a private RSA key and then verify with public RSA key - fails due to signing time is set 60 seconds in the past") {
      When("We make a request v4.0.0")
      val httpBody =
        s"""{
           |"instructedAmount": {"currency": "EUR", "amount": "123.50"},
           |"debtorAccount": {"iban": "DE40100100103307118608"},
           |"creditorName": "Merchant123",
           |"creditorAccount": {"iban": "DE02100100109307118603"},
           |"remittanceInformationUnstructured": "Ref Number Merchant"
           |}
           |""".stripMargin


      // x-jws-signature and digest
      val httpParams = signRequest(
        Full(httpBody), 
        "post", 
        "/berlin-group/v1.3/payments/sepa-credit-transfers", 
        "application/json;charset=utf-8",
        signingTime = Some(ZonedDateTime.now(ZoneOffset.UTC).minusSeconds(60))
      )

      // Hard-coded request headers
      val requestHeaders = httpParams

      validate(getPem(requestHeaders))
      val isVerified = verifyJws(CertificateUtil.rsaPublicKey, httpBody, requestHeaders, "post", "/berlin-group/v1.3/payments/sepa-credit-transfers")
      isVerified should equal(false)
    }
  }

  feature(s"Assuring that endpoint $ApiEndpoint1 works as expected - v2.1.0") {
    scenario("We try to make ur call - successful", ApiEndpoint1) {
      When("We make the request")
      val requestGet = (v4_0_0_Request / "development" / "echo" / "jws-verified-request-jws-signed-response").GET <@ (user1)
      val signHeaders = signRequest(
        Full(""), 
        "get", 
        "/obp/v4.0.0/development/echo/jws-verified-request-jws-signed-response", 
        "application/json;charset=UTF-8"
      ).map(i => (i.name, i.values.mkString(",")))
      val responseGet = makeGetRequest(requestGet, signHeaders)
      Then("We should get a 200")
      responseGet.code should equal(200)
    }
    scenario("We try to make ur call - unsuccessful", ApiEndpoint1) {
      When("We make the request")
      val requestGet = (v4_0_0_Request / "development" / "echo" / "jws-verified-request-jws-signed-response").GET <@ (user1)
      val signHeaders = signRequest(
        Full(""),
        "get",
        "/obp/v4.0.0/development/echo/jws-verified-request-jws-signed-response",
        "application/json;charset=UTF-8"
      ).map(i => (i.name, i.values.mkString(",")))
      Thread.sleep(60 seconds)
      val responseGet = makeGetRequest(requestGet, signHeaders)
      Then("We should get a 401")
      responseGet.code should equal(401)
    }
  }

}
