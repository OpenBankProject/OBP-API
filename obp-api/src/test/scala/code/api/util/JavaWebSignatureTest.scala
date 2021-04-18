package code.api.util

import code.api.util.JwsUtil.{getPem, signRequest, verifyJws}
import code.api.util.X509.validate
import code.api.v4_0_0.V400ServerSetup
import net.liftweb.common.Full
import net.liftweb.http.provider.HTTPParam
import org.scalatest.Tag

import scala.collection.immutable.List

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
      val httpParams = signRequest(Full(httpBody), "post", "/berlin-group/v1.3/payments/sepa-credit-transfers")

      // Hard-coded request headers
      val requestHeaders = List(
        HTTPParam("host", List(APIUtil.getPropsValue("hostname", ""))),
        HTTPParam("content-type", List("application/json")),
        HTTPParam("psu-ip-address", List("192.168.8.78")),
        HTTPParam("psu-geo-location", List("GEO:52.506931,13.144558")),
      ) ::: httpParams

      validate(getPem(requestHeaders))
      val isVerified = verifyJws(CertificateUtil.rsaPublicKey, httpBody, requestHeaders, "post", "/berlin-group/v1.3/payments/sepa-credit-transfers")
      isVerified should equal(true)
    }
  }
}
