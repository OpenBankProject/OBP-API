package code.util

import code.api.util.PegdownOptions.{convertPegdownToHtmlTweaked,convertHtmlMarkdown}
import net.liftweb.util.Html5
import org.scalatest.{FlatSpec, Matchers, Tag}

import scala.xml.NodeSeq

class PegdownOptionsTest extends FlatSpec with Matchers {
  /**
   * this is the method from api_explorer to show the description filed to browser.
   * @param html
   * @return
   */
  def stringToNodeSeq(html : String) : NodeSeq = {
    val newHtmlString =scala.xml.XML.loadString("<div>" + html + "</div>").toString()
    //Note: `parse` method: We much enclose the div, otherwise only the first element is returned. 
    Html5.parse(newHtmlString).head
  }
  object FunctionsTag extends Tag("PegdownOptions")

  "description string" should "be transfer to proper html, no exception is good" taggedAs FunctionsTag in {

    val descriptionString =
      """Get basic information about the Adapter listening on behalf of this bank.
        |
        |Authentication is Optional**URL Parameters:**
        |
        |[BANK_ID](/glossary#Bank.bank_id):gh.29.uk
        |
        |""".stripMargin
    val descriptionString2 ="""Get basic information about the Adapter listening on behalf of this bank.
        |
        |Authentication is Optional
        |
        |      **URL Parameters:**
        |
        |* [BANK_ID](/glossary#Bank.bank_id):gh.29.uk
        |
        |""".stripMargin
    val descriptionHtml= convertPegdownToHtmlTweaked(descriptionString)
    val descriptionHtml2= convertPegdownToHtmlTweaked(descriptionString2)
    
    val descriptionApiExplorer = stringToNodeSeq(descriptionHtml)
    val descriptionApiExplorer2 = stringToNodeSeq(descriptionHtml2)
      
//    println(descriptionHtml)
//    println(descriptionHtml2)

    val html ="""<h3>
                |    Description</h3>\\nThe PISP sent a Payment/Transfer Request through a POST command.
                |<br>\\n  The ASPSP registered the Payment/Transfer Request, updated if necessary the relevant identifiers in order to avoid duplicates and returned the location of the updated Request.
                |<br>\\n  The PISP got the Payment/Transfer Request that has been updated with the resource identifiers, and eventually the status of the Payment/Transfer Request and the status of the subsequent credit transfer.
                |<br>\\n  The PISP request for the payment cancellation (global cancellation) or for some payment instructions cancellation (partial cancellation)
                |<br>\\n  No other modification of the Payment/Transfer Request is allowed.
                |<br/>\\n<h3>Prerequisites</h3>\\n
                |<ul>\\n
                |    <li>The TPP was registered by the Registration Authority for the PISP role
                |    </li>
                |    \\n
                |    <li>The TPP was provided with an OAUTH2 \\\"Client Credential\\\" access
                |        token by the ASPSP (cf. § 3.4.3).
                |    </li>
                |    \\n
                |    <li>The TPP previously posted a Payment/Transfer Request which was saved by
                |        the ASPSP (cf. § 4.5.3)
                |    </li>
                |    \\n
                |    <ul>\\n
                |        <li>The ASPSP answered with a location link to the saved
                |            Payment/Transfer Request (cf. § 4.5.4)
                |        </li>
                |        \\n
                |        <li>The PISP retrieved the saved Payment/Transfer Request (cf. §
                |            4.5.4)
                |        </li>
                |        \\n
                |    </ul>
                |    \\n
                |    <li>The TPP and the ASPSP successfully processed a mutual check and
                |        authentication
                |    </li>
                |    \\n
                |    <li>The TPP presented its \\\"OAUTH2 Client Credential\\\" access token.
                |    </li>
                |    \\n
                |    <li>The TPP presented the payment/transfer request.</li>
                |    \\n
                |    <li>The PSU was successfully authenticated.</li>
                |    \\n
                |</ul>\\n<h3>Business flow</h3>\\nthe following cases can be applied:\\n
                |<ul>\\n
                |    <li>Case of a payment with multiple instructions or a standing order, the
                |        PISP asks to cancel the whole Payment/Transfer or Standing Order Request
                |        including all non-executed payment instructions by setting the
                |        [paymentInformationStatus] to \\\"RJCT\\\" and the relevant
                |        [statusReasonInformation] to \\\"DS02\\\" at payment level.
                |    </li>
                |    \\n
                |    <li>Case of a payment with multiple instructions, the PISP asks to cancel
                |        one or several payment instructions by setting the [transactionStatus]
                |        to \\\"RJCT\\\" and the relevant [statusReasonInformation] to
                |        \\\"DS02\\\" at each relevant instruction level.
                |    </li>
                |    \\n
                |</ul>\\nSince the modification request needs a PSU authentication before committing, the modification request includes:</li>\\n
                |<ul>\\n
                |    <li>The specification of the authentication approaches that are supported by
                |        the PISP (any combination of \\\"REDIRECT\\\", \\\"EMBEDDED\\\" and
                |        \\\"DECOUPLED\\\" values).
                |    </li>
                |    \\n
                |    <li>In case of possible REDIRECT or DECOUPLED authentication approach, one
                |        or two call-back URLs to be used by the ASPSP at the finalisation of the
                |        authentication and consent process :
                |    </li>
                |    \\n
                |    <ul>\\n
                |        <li>The first call-back URL will be called by the ASPSP if the Transfer
                |            Request is processed without any error or rejection by the PSU
                |        </li>
                |        \\n
                |        <li>The second call-back URL is to be used by the ASPSP in case of
                |            processing error or rejection by the PSU. Since this second URL is
                |            optional, the PISP might not provide it. In this case, the ASPSP
                |            will use the same URL for any processing result.
                |        </li>
                |        \\n
                |        <li>Both call-back URLS must be used in a TLS-secured request.</li>
                |        \\n
                |    </ul>
                |    \\n
                |    <li>In case of possible \\\"EMBEDDED\\\" or \\\"DECOUPLED\\\" approaches, a
                |        PSU identifier that can be processed by the ASPSP for PSU recognition.
                |    </li>
                |    \\n
                |</ul>\\n
                |<li>The ASPSP saves the updated Payment/Transfer Request and answers to the
                |    PISP. The answer embeds
                |</li>\\n
                |<ul>\\n
                |    <li>The specification of the chosen authentication approach taking into
                |        account both the PISP and the PSU capabilities.
                |    </li>
                |    \\n
                |    <li>In case of chosen REDIRECT authentication approach, the URL to be used
                |        by the PISP for redirecting the PSU in order to perform an
                |        authentication.
                |    </li>
                |    \\n
                |</ul>\\n</ul>\\n<h3>Authentication flows for both use cases</h3>\\n<h4>Redirect
                |    authentication
                |    approach </h4>\\nWhen the chosen authentication approach within the ASPSP answers is set to \\\"REDIRECT\\\":
                |<br>\\n
                |<ul>\\n
                |    <li>The PISP redirects the PSU to the ASPSP which authenticates the PSU</li>
                |    \\n
                |    <li>The ASPSP asks the PSU to give (or deny) his/her consent to the Payment
                |        Request global or partial Cancellation
                |    </li>
                |    \\n
                |    <li>The ASPSP is then able to initiate the subsequent cancellation</li>
                |    \\n
                |    <li>The ASPSP redirects the PSU to the PISP using one of the call-back URLs
                |        provided within the posted Payment Request cancellation
                |    </li>
                |    \\n
                |</ul>\\nIf the PSU neither gives nor denies his/her consent, the Cancellation Request shall expire and is then rejected to the PISP. The expiration delay is specified by each ASPSP.
                |<br>\\n<h4>Decoupled authentication
                |    approach</h4>\\nWhen the chosen authentication approach is \\\"DECOUPLED\\\":
                |<br>\\n
                |<ul>\\n
                |    <li>Based on the PSU identifier provided within the Payment Request by the
                |        PISP, the ASPSP provides the PSU with the Cancellation Request details
                |        and challenges the PSU for a Strong Customer Authentication on a
                |        decoupled device or application.
                |    </li>
                |    \\n
                |    <li>The PSU confirms or not the Payment Request global or partial
                |        Cancellation
                |    </li>
                |    \\n
                |    <li>The ASPSP is then able to initiate the subsequent cancellation</li>
                |    \\n
                |    <li>The ASPSP notifies the PISP about the finalisation of the authentication
                |        and cancellation process by using one of the call-back URLs provided
                |        within the posted Payment Request
                |    </li>
                |    \\n
                |</ul>\\nIf the PSU neither gives nor denies his/her consent, the Cancellation Request shall expire and is then rejected to the PISP. The expiration delay is specified by each ASPSP.
                |<br>\\n<h4>Embedded authentication
                |    approach</h4>\\nWhen the chosen authentication approach within the ASPSP answers is set to \\\"EMBEDDED\\\":
                |<br>\\n
                |<ul>\\n
                |    <li>The TPP informs the PSU that a challenge is needed for completing the
                |        Payment Request cancellation processing. This challenge will be one of
                |        the following:
                |    </li>
                |    \\n
                |    <ul>\\n
                |        <li>A One-Time-Password sent by the ASPSP to the PSU on a separate
                |            device or application.
                |        </li>
                |        \\n
                |        <li>A response computed by a specific device on base of a challenge sent
                |            by the ASPSP to the PSU on a separate device or application.
                |        </li>
                |        \\n
                |    </ul>
                |    \\n
                |    <li>The PSU unlock the device or application through a \\\"knowledge
                |        factor\\\" and/or an \\\"inherence factor\\\" (biometric), retrieves the
                |        cancellation details.
                |    </li>
                |    \\n
                |    <li>The PSU confirms or not the Payment Request global or partial
                |        Cancellation
                |    </li>
                |    \\n
                |    <li>When agreeing the Payment Request cancellation, the PSU enters the
                |        resulting authentication factor through the PISP interface which will
                |        forward it to the ASPSP through a confirmation request (cf. § 4.7)
                |    </li>
                |    \\n
                |</ul>\\nCase of the PSU neither gives nor denies his/her consent, the Cancellation Request shall expire and is then rejected to the PISP. The expiration delay is specified by each ASPSP.
                |<br>""".stripMargin

    // this response still contains `\[` there, need to be fixed.
    val markdown = convertHtmlMarkdown(html)

//    println(markdown)
    stringToNodeSeq(markdown)
  }

  "description string" should "test the markdown * -> html <li> tag" taggedAs FunctionsTag in {

    // This string is from Foobar Property List: format
    val descriptionString = """Update exists Foo Bar33.

Description of this entity, can be markdown text.


**Property List:**

* name: * description of **name** field, can be markdown text.
* number: * description of **number** field, can be markdown text.



Authentication is Mandatory""".stripMargin
    val descriptionHtml= convertPegdownToHtmlTweaked(descriptionString)
    val descriptionApiExplorer = stringToNodeSeq(descriptionHtml)

    descriptionHtml contains("<li>name: * description of <strong>name</strong> field, can be markdown text.</li>") should be (true)


    //This string is from obp JSON response body fields: format
    val descriptionString2 ="""Get basic information about the Adapter listening on behalf of this bank.
                              |
                              |Authentication is Optional
                              |
                              |      **URL Parameters:**
                              |
                              |* [BANK_ID](/glossary#Bank.bank_id):gh.29.uk
                              |
                              |""".stripMargin
    val descriptionHtml2= convertPegdownToHtmlTweaked(descriptionString2)

    descriptionHtml2 contains("<li><a href=\"/glossary#Bank.bank_id\">BANK_ID</a>:gh.29.uk</li>") should be (true)
    
  }


  "description string" should " Authentication is Mandatory should have more space " taggedAs FunctionsTag in {

    // This string is from Foobar Property List: format
    val descriptionString = """Update exists Foo Bar33.

Description of this entity, can be markdown text.


**Property List:**

* name: * description of **name** field, can be markdown text.
* number: * description of **number** field, can be markdown text.



Authentication is Mandatory""".stripMargin
    val descriptionHtml= convertPegdownToHtmlTweaked(descriptionString)
    val descriptionApiExplorer = stringToNodeSeq(descriptionHtml)

    descriptionHtml contains("<li>name: * description of <strong>name</strong> field, can be markdown text.</li>") should be (true)


    //This string is from obp JSON response body fields: format
    val descriptionString2 ="""See [FPML](http://www.fpml.org/) for more examples.
                              |
                              |The type field must be one of "STRING", "INTEGER", "DOUBLE" or DATE_WITH_DAY"
                              |
                              |Authentication is Mandatory
                              |
                              |**URL Parameters:**
                              |
                              |
                              |
                              |* [ACCOUNT_ID](/glossary#Account.account_id): 8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0
                              |""".stripMargin
    val descriptionHtml2= convertPegdownToHtmlTweaked(descriptionString2)

    descriptionHtml2 contains("<p>Authentication is Mandatory</p>") should be (true)

    
    val descriptionString3 ="""Returns information about:
      |
      |* The default bank_id
      |* Akka configuration
      |* Elastic Search configuration
      |* Cached functions
      |
      |Authentication is Mandatory
      |
      |
      |**JSON response body fields:**
      |
      |
      |
      |* [akka](/glossary#Adapter.Akka.Intro): no-example-provided
      |""".stripMargin

    val descriptionHtml3= convertPegdownToHtmlTweaked(descriptionString3)

    descriptionHtml3 contains("<p>Authentication is Mandatory</p>") should be (true)

  }
  
  
  
  
 
}
