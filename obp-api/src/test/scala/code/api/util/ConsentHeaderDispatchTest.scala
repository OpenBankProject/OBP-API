package code.api.util

import code.api.RequestHeader
import code.api.util.APIUtil.HTTPParam
import code.setup.ServerSetup
import net.liftweb.common.Failure

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Try

/**
 * This class tests which consent scheme authenticates a request that carries a consent id.
 *
 * OBP's own consent header is `Consent-Id`, and Berlin Group's is `Consent-ID`. The two names differ
 * only in letter case, and HTTP header names are case-insensitive (over HTTP/2 both arrive as
 * `consent-id`), so the name cannot tell the schemes apart. The request path does:
 * `APIUtil.getUserAndSessionContextFuture` applies Berlin Group's consent rules only on a Berlin
 * Group path, and OBP's everywhere else, whichever spelling the client used.
 *
 * Each scenario sends a consent id that matches no consent and reads which scheme refused it. OBP's
 * rules refuse it as `ConsentHeaderValueInvalid` (neither a known consent id nor a JWT); Berlin
 * Group's refuse it as `ConsentNotFound`.
 */
class ConsentHeaderDispatchTest extends ServerSetup {

  private val obpPath = "/obp/v5.1.0/users/current"
  private val berlinGroupPath = "/berlin-group/v1.3/accounts"
  private val unknownConsentId = "no-such-consent"

  /** This runs authentication for a GET of `path` with one header, and returns the error message. */
  private def errorFor(path: String, headerName: String): String = {
    setPropsValues("consents.allowed" -> "true")
    val callContext = CallContext(url = path, verb = "GET", requestHeaders = List(HTTPParam(headerName, List(unknownConsentId))))
    Try(Await.result(APIUtil.getUserAndSessionContextFuture(callContext), 30.seconds)).map(_._1) match {
      case scala.util.Success(Failure(message, _, _)) => message
      case scala.util.Success(other) => fail(s"expected the consent to be refused, got $other")
      case scala.util.Failure(exception) => exception.getMessage
    }
  }

  feature("The request path, not the header's letter case, picks the consent scheme") {

    List(RequestHeader.`Consent-Id`, RequestHeader.`Consent-ID`, "consent-id").foreach { headerName =>
      scenario(s"On an OBP path, a $headerName header is checked by OBP's consent rules") {
        val message = errorFor(obpPath, headerName)
        message should include(ErrorMessages.ConsentHeaderValueInvalid)
      }

      scenario(s"On a Berlin Group path, a $headerName header is checked by Berlin Group's consent rules") {
        val message = errorFor(berlinGroupPath, headerName)
        message should include(ErrorMessages.ConsentNotFound)
        message should include(unknownConsentId)
      }
    }
  }
}
