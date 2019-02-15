package code.api.STET.v1_4

import code.api.APIFailureNewStyle
import code.api.berlin.group.v1_3.JvalueCaseClass
import net.liftweb.json
import net.liftweb.json._
import code.api.util.APIUtil.{defaultBankId, _}
import code.api.util.{ApiVersion, NewStyle}
import code.api.util.ErrorMessages._
import code.api.util.ApiTag._
import code.api.util.NewStyle.HttpCode
import code.bankconnectors.Connector
import code.model._
import code.util.Helper
import code.views.Views
import net.liftweb.common.Full
import net.liftweb.http.rest.RestHelper
import com.github.dwickern.macros.NameOf.nameOf
import scala.collection.immutable.Nil
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import code.api.STET.v1_4.OBP_STET_1_4
import code.api.util.ApiTag

object APIMethods_CBPIIApi extends RestHelper {
    val apiVersion =  OBP_STET_1_4.apiVersion
    val resourceDocs = ArrayBuffer[ResourceDoc]()
    val apiRelations = ArrayBuffer[ApiRelation]()
    protected implicit def JvalueToSuper(what: JValue): JvalueCaseClass = JvalueCaseClass(what)

    val endpoints = 
      fundsConfirmationsPost ::
      Nil

            
     resourceDocs += ResourceDoc(
       fundsConfirmationsPost, 
       apiVersion, 
       nameOf(fundsConfirmationsPost),
       "POST", 
       "/funds-confirmations", 
       "Payment coverage check request (CBPII)",
       s"""${mockedDataText(true)}
&lt;h3&gt;Description&lt;/h3&gt;
The CBPII can ask an ASPSP to check if a given amount can be covered by the liquidity that is available on a PSU cash account or payment card.
&lt;h3&gt;Prerequisites&lt;/h3&gt;
&lt;ul&gt;
  &lt;li&gt;The TPP has been registered by the Registration Authority for the CBPII role&lt;/li&gt;
  &lt;li&gt;The TPP and the PSU have a contract that has been registered by the ASPSP&lt;/li&gt;
  &lt;ul&gt;
    &lt;li&gt;At this step, the ASPSP has delivered an &quot;Authorization Code&quot;, a &quot;Resource Owner Password&quot; or a &quot;Client Credential&quot; OAUTH2 access token to the TPP (cf. § 3.4.2).&lt;/li&gt;
    &lt;li&gt;Each ASPSP has to implement either the &quot;Authorization Code&quot;/&quot;Resource Owner Password&quot; or the &quot;Client Credential&quot; OAUTH2 access token model.&lt;/li&gt;
    &lt;li&gt;Doing this, it will edit the [security] section on this path in order to specify which model it has chosen&lt;/li&gt;
  &lt;/ul&gt;
  &lt;li&gt;The TPP and the ASPSP have successfully processed a mutual check and authentication &lt;/li&gt;
  &lt;li&gt;The TPP has presented its OAUTH2 &quot;Authorization Code&quot;, &quot;Resource Owner Password&quot; or &quot;Client Credential&quot; access token which allows the ASPSP to identify the relevant PSU.&lt;/li&gt;
&lt;/ul&gt;
&lt;h3&gt;Business flow&lt;/h3&gt;
The CBPII requests the ASPSP for a payment coverage check against either a bank account or a card primary identifier.
The ASPSP answers with a structure embedding the original request and the result as a Boolean.      
""", 
       json.parse("""{
  "paymentCoverageRequestId" : "MyCoverage123456",
  "instructedAmount" : {
    "currency" : "EUR",
    "amount" : "12345"
  },
  "accountId" : {
    "iban" : "YY13RDHN98392489481620896668799742"
  }
}"""),
       json.parse(""""""),
       List(UserNotLoggedIn, UnknownError),
       Catalogs(notCore, notPSD2, notOBWG), 
       ApiTag("CBPII") :: apiTagMockedData :: Nil
     )

     lazy val fundsConfirmationsPost : OBPEndpoint = {
       case "funds-confirmations" :: Nil JsonPost _ => {
         cc =>
           for {
             (Full(u), callContext) <- authorizedAccess(UserNotLoggedIn, cc)
             } yield {
             (NotImplemented, callContext)
           }
         }
       }

}



