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

object APIMethods_AISPApi extends RestHelper {
    val apiVersion =  OBP_STET_1_4.apiVersion
    val resourceDocs = ArrayBuffer[ResourceDoc]()
    val apiRelations = ArrayBuffer[ApiRelation]()
    protected implicit def JvalueToSuper(what: JValue): JvalueCaseClass = JvalueCaseClass(what)

    val endpoints = 
      accountsBalancesGet ::
      accountsGet ::
      accountsTransactionsGet ::
      consentsPut ::
      endUserIdentityGet ::
      trustedBeneficiariesGet ::
      Nil

            
     resourceDocs += ResourceDoc(
       accountsBalancesGet, 
       apiVersion, 
       nameOf(accountsBalancesGet),
       "GET", 
       "/accounts/ACCOUNTRESOURCEID/balances", 
       "Retrieval of an account balances report (AISP)",
       s"""${mockedDataText(true)}
&lt;h3&gt;Description&lt;/h3&gt;
  This call returns a set of balances for a given PSU account that is specified by the AISP through an account resource Identification
&lt;h3&gt;Prerequisites&lt;/h3&gt;
  &lt;ul&gt;
    &lt;li&gt;The TPP has been registered by the Registration Authority for the AISP role&lt;/li&gt;
    &lt;li&gt;The TPP and the PSU have a contract that has been enrolled by the ASPSP
    &lt;ul style&#x3D;&quot;list-style-type:circle;&quot;&gt;
      &lt;li&gt;At this step, the ASPSP has delivered an OAUTH2 &amp;ldquo;Authorization Code&amp;rdquo; or &amp;ldquo;Resource Owner Password&amp;rdquo; access token to the TPP (cf. &amp;sect; 3.4.2).&lt;/li&gt;
    &lt;/ul&gt;
    &lt;/li&gt;
    &lt;li&gt;The TPP and the ASPSP have successfully processed a mutual check and authentication&lt;/li&gt;
    &lt;li&gt;The TPP has presented its OAUTH2 &amp;ldquo;Authorization Code&amp;rdquo; or &amp;ldquo;Resource Owner Password&amp;rdquo; access token which allows the ASPSP to identify the relevant PSU and retrieve the linked PSU context (cf. &amp;sect; 3.4.2) if any.&lt;/li&gt;
    &lt;li&gt;The ASPSP takes into account the access token that establishes the link between the PSU and the AISP.&lt;/li&gt;
    &lt;li&gt;The TPP has previously retrieved the list of available accounts for the PSU&lt;/li&gt;
  &lt;/ul&gt;
&lt;h3&gt;Business flow&lt;/h3&gt;
  The AISP requests the ASPSP on one of the PSU&amp;rsquo;s accounts.&lt;br /&gt;
  The ASPSP answers by providing a list of balances on this account.
  &lt;ul&gt;
    &lt;li&gt;The ASPSP must provide at least the accounting balance on the account.&lt;/li&gt;
    &lt;li&gt;The ASPSP can provide other balance restitutions, e.g. instant balance, as well, if possible.&lt;/li&gt;
    &lt;li&gt;Actually, from the PSD2 perspective, any other balances that are provided through the Web-Banking service of the ASPSP must also be provided by this ASPSP through the API.&lt;/li&gt;
  &lt;/ul&gt;
""", 
       json.parse(""""""),
       json.parse(""""""),
       List(UserNotLoggedIn, UnknownError),
       Catalogs(notCore, notPSD2, notOBWG), 
       ApiTag("AISP") :: apiTagMockedData :: Nil
     )

     lazy val accountsBalancesGet : OBPEndpoint = {
       case "accounts" :: accountresourceid:: "balances" :: Nil JsonGet _ => {
         cc =>
           for {
             (Full(u), callContext) <- authorizeEndpoint(UserNotLoggedIn, cc)
             } yield {
             (json.parse(""""""), callContext)
           }
         }
       }
            
     resourceDocs += ResourceDoc(
       accountsGet, 
       apiVersion, 
       nameOf(accountsGet),
       "GET", 
       "/accounts", 
       "Retrieval of the PSU accounts (AISP)",
       s"""${mockedDataText(true)}
&lt;h3&gt;Description&lt;/h3&gt;
  This call returns all payment accounts that are relevant the PSU on behalf of whom the AISP is connected.
  Thanks to HYPERMEDIA, each account is returned with the links aiming to ease access to the relevant transactions and balances.
  The result may be subject to pagination (i.e. retrieving a partial result in case of having too many results) through a set of pages by the ASPSP. Thereafter, the AISP may ask for the first, next, previous or last page of results.
&lt;h3&gt;Prerequisites&lt;/h3&gt;
  &lt;ul&gt;
    &lt;li&gt;The TPP has been registered by the Registration Authority for the AISP role.&lt;/li&gt;
    &lt;li&gt;The TPP and the PSU have a contract that has been enrolled by the ASPSP&lt;/li&gt;
      &lt;ul&gt;
        &lt;li&gt;At this step, the ASPSP has delivered an OAUTH2 &quot;Authorization Code&quot; or &quot;Resource Owner Password&quot; access token to the TPP (cf. § 3.4.2).&lt;/li&gt;
      &lt;/ul&gt;
    &lt;li&gt;The TPP and the ASPSP have successfully processed a mutual check and authentication&lt;/li&gt;
    &lt;li&gt;The TPP has presented its OAUTH2 &quot;Authorization Code&quot; or &quot;Resource Owner Password&quot; access token which allows the ASPSP to identify the relevant PSU and retrieve the linked PSU context (cf. § 3.4.2) if any.&lt;/li&gt;
    &lt;li&gt;The ASPSP takes into account the access token that establishes the link between the PSU and the AISP.&lt;/li&gt;
  &lt;/ul&gt;
&lt;h3&gt;Business Flow&lt;/h3&gt;
  The TPP sends a request to the ASPSP for retrieving the list of the PSU payment accounts.
  The ASPSP computes the relevant PSU accounts and builds the answer as an accounts list. 
  The result may be subject to pagination in order to avoid an excessive result set. 
  Each payment account will be provided with its characteristics.
""", 
       json.parse(""""""),
       json.parse(""""""),
       List(UserNotLoggedIn, UnknownError),
       Catalogs(notCore, notPSD2, notOBWG), 
       ApiTag("AISP") :: apiTagMockedData :: Nil
     )

     lazy val accountsGet : OBPEndpoint = {
       case "accounts" :: Nil JsonGet _ => {
         cc =>
           for {
             (Full(u), callContext) <- authorizeEndpoint(UserNotLoggedIn, cc)
             } yield {
             (json.parse(""""""), callContext)
           }
         }
       }
            
     resourceDocs += ResourceDoc(
       accountsTransactionsGet, 
       apiVersion, 
       nameOf(accountsTransactionsGet),
       "GET", 
       "/accounts/ACCOUNTRESOURCEID/transactions", 
       "Retrieval of an account transaction set (AISP)",
       s"""${mockedDataText(true)}
&lt;h3&gt;Description&lt;/h3&gt;
  This call returns transactions for an account for a given PSU account that is specified by the AISP through an account resource identification.
  The request may use some filter parameter in order to restrict the query 
  &lt;ul&gt;
    &lt;li&gt;on a given imputation date range&lt;/li&gt;
    &lt;li&gt;past a given incremental technical identification&lt;/li&gt;
  &lt;/ul&gt;
  The result may be subject to pagination (i.e. retrieving a partial result in case of having too many results) through a set of pages by the ASPSP. Thereafter, the AISP may ask for the first, next, previous or last page of results.
&lt;h3&gt;Prerequisites&lt;/h3&gt;
  &lt;ul&gt;
    &lt;li&gt;The TPP has been registered by the Registration Authority for the AISP role&lt;/li&gt;
    &lt;li&gt;The TPP and the PSU have a contract that has been enrolled by the ASPSP&lt;/li&gt;
    &lt;ul&gt;
      &lt;li&gt;At this step, the ASPSP has delivered an OAUTH2 &quot;Authorization Code&quot; or &quot;Resource Owner Password&quot; access token to the TPP (cf. § 3.4.2).&lt;/li&gt;
    &lt;/ul&gt;
    &lt;li&gt;The TPP and the ASPSP have successfully processed a mutual check and authentication &lt;/li&gt;
    &lt;li&gt;The TPP has presented its OAUTH2 &quot;Authorization Code&quot; or &quot;Resource Owner Password&quot; access token which allows the ASPSP to identify the relevant PSU and retrieve the linked PSU context (cf. § 3.4.2) is any.&lt;/li&gt;
    &lt;li&gt;The ASPSP takes into account the access token that establishes the link between the PSU and the AISP.&lt;/li&gt;
    &lt;li&gt;The TPP has previously retrieved the list of available accounts for the PSU&lt;/li&gt;
  &lt;/ul&gt;
&lt;h3&gt;Business flow&lt;/h3&gt;
  The AISP requests the ASPSP on one of the PSU’s accounts. It may specify some selection criteria.
  The ASPSP answers by a set of transactions that matches the query. The result may be subject to pagination in order to avoid an excessive result set.
""", 
       json.parse(""""""),
       json.parse(""""""),
       List(UserNotLoggedIn, UnknownError),
       Catalogs(notCore, notPSD2, notOBWG), 
       ApiTag("AISP") :: apiTagMockedData :: Nil
     )

     lazy val accountsTransactionsGet : OBPEndpoint = {
       case "accounts" :: accountresourceid:: "transactions" :: Nil JsonGet _ => {
         cc =>
           for {
             (Full(u), callContext) <- authorizeEndpoint(UserNotLoggedIn, cc)
             } yield {
             (json.parse(""""""), callContext)
           }
         }
       }
            
     resourceDocs += ResourceDoc(
       consentsPut, 
       apiVersion, 
       nameOf(consentsPut),
       "PUT", 
       "/consents", 
       "Forwarding the PSU consent (AISP)",
       s"""${mockedDataText(true)}
&lt;h3&gt;Description&lt;/h3&gt;
  In the mixed detailed consent on accounts
  &lt;ul&gt;
    &lt;li&gt;the AISP captures the consent of the PSU&lt;/li&gt;
    &lt;li&gt;then it forwards this consent to the ASPSP&lt;/li&gt;
  &lt;/ul&gt;
  This consent replaces any prior consent that was previously sent by the AISP.
&lt;h3&gt;Prerequisites&lt;/h3&gt;
  &lt;ul&gt;
    &lt;li&gt;The TPP has been registered by the Registration Authority for the AISP role.&lt;/li&gt;
    &lt;li&gt;The TPP and the PSU have a contract that has been enrolled by the ASPSP&lt;/li&gt;
      &lt;ul&gt;
      &lt;li&gt;At this step, the ASPSP has delivered an OAUTH2 &quot;Authorization Code&quot; or &quot;Resource Owner Password&quot; access token to the TPP (cf. § 3.4.2).&lt;/li&gt;
      &lt;/ul&gt;
    &lt;li&gt;The TPP and the ASPSP have successfully processed a mutual check and authentication&lt;/li&gt;
    &lt;li&gt;The TPP has presented its OAUTH2 &quot;Authorization Code&quot; or &quot;Resource Owner Password&quot; access token which allows the ASPSP to identify the relevant PSU and retrieve the linked PSU context (cf. § 3.4.2) if any.&lt;/li&gt;
    &lt;li&gt;The ASPSP takes into account the access token that establishes the link between the PSU and the AISP.&lt;/li&gt;
  &lt;/ul&gt;
&lt;h3&gt;Business Flow&lt;/h3&gt;
  The PSU specifies to the AISP which of his/her accounts will be accessible and which functionalities should be available.
  The AISP forwards these settings to the ASPSP.
  The ASPSP answers by HTTP201 return code.
""", 
       json.parse("""{
  "balances" : [ {
    "iban" : "YY64COJH41059545330222956960771321"
  } ],
  "trustedBeneficiaries" : true,
  "psuIdentity" : true
}"""),
       json.parse(""""""),
       List(UserNotLoggedIn, UnknownError),
       Catalogs(notCore, notPSD2, notOBWG), 
       ApiTag("AISP") :: apiTagMockedData :: Nil
     )

     lazy val consentsPut : OBPEndpoint = {
       case "consents" :: Nil JsonPut _ => {
         cc =>
           for {
             (Full(u), callContext) <- authorizeEndpoint(UserNotLoggedIn, cc)
             } yield {
             (json.parse(""""""), callContext)
           }
         }
       }
            
     resourceDocs += ResourceDoc(
       endUserIdentityGet, 
       apiVersion, 
       nameOf(endUserIdentityGet),
       "GET", 
       "/end-user-identity", 
       "Retrieval of the identity of the end-user (AISP)",
       s"""${mockedDataText(true)}
&lt;h3&gt;Description&lt;/h3&gt;
  This call returns the identity of the PSU (end-user).
&lt;h3&gt;Prerequisites&lt;/h3&gt;
  &lt;ul&gt;
    &lt;li&gt;The TPP has been registered by the Registration Authority for the AISP role.&lt;/li&gt;
    &lt;li&gt;The TPP and the PSU have a contract that has been enrolled by the ASPSP&lt;/li&gt;
      &lt;ul&gt;
        &lt;li&gt;At this step, the ASPSP has delivered an OAUTH2 &quot;Authorization Code&quot; or &quot;Resource Owner Password&quot; access token to the TPP (cf. § 3.4.2).&lt;/li&gt;
      &lt;/ul&gt;
    &lt;li&gt;The TPP and the ASPSP have successfully processed a mutual check and authentication&lt;/li&gt;
    &lt;li&gt;The TPP has presented its OAUTH2 &quot;Authorization Code&quot; or &quot;Resource Owner Password&quot; access token which allows the ASPSP to identify the relevant PSU and retrieve the linked PSU context (cf. § 3.4.2) if any.&lt;/li&gt;
    &lt;li&gt;The ASPSP takes into account the access token that establishes the link between the PSU and the AISP.&lt;/li&gt;
  &lt;/ul&gt;
&lt;h3&gt;Business Flow&lt;/h3&gt;
  The AISP asks for the identity of the PSU.
  The ASPSP answers with the identity, i.e. first and last names of the end-user.
""", 
       json.parse(""""""),
       json.parse(""""""),
       List(UserNotLoggedIn, UnknownError),
       Catalogs(notCore, notPSD2, notOBWG), 
       ApiTag("AISP") :: apiTagMockedData :: Nil
     )

     lazy val endUserIdentityGet : OBPEndpoint = {
       case "end-user-identity" :: Nil JsonGet _ => {
         cc =>
           for {
             (Full(u), callContext) <- authorizeEndpoint(UserNotLoggedIn, cc)
             } yield {
             (json.parse(""""""), callContext)
           }
         }
       }
            
     resourceDocs += ResourceDoc(
       trustedBeneficiariesGet, 
       apiVersion, 
       nameOf(trustedBeneficiariesGet),
       "GET", 
       "/trusted-beneficiaries", 
       "Retrieval of the trusted beneficiaries list (AISP)",
       s"""${mockedDataText(true)}
&lt;h3&gt;Description&lt;/h3&gt;
  This call returns all trusted beneficiaries that have been set by the PSU.
  Those beneficiaries can benefit from an SCA exemption during payment initiation.
  The result may be subject to pagination (i.e. retrieving a partial result in case of having too many results) through a set of pages by the ASPSP. Thereafter, the AISP may ask for the first, next, previous or last page of results.
&lt;h3&gt;Prerequisites&lt;/h3&gt;
  &lt;ul&gt;
    &lt;li&gt;The TPP has been registered by the Registration Authority for the AISP role.&lt;/li&gt;
    &lt;li&gt;The TPP and the PSU have a contract that has been enrolled by the ASPSP&lt;/li&gt;
      &lt;ul&gt;
        &lt;li&gt;At this step, the ASPSP has delivered an OAUTH2 &quot;Authorization Code&quot; or &quot;Resource Owner Password&quot; access token to the TPP (cf. § 3.4.2).&lt;/li&gt;
      &lt;/ul&gt;
    &lt;li&gt;The TPP and the ASPSP have successfully processed a mutual check and authentication&lt;/li&gt;
    &lt;li&gt;The TPP has presented its OAUTH2 &quot;Authorization Code&quot; or &quot;Resource Owner Password&quot; access token which allows the ASPSP to identify the relevant PSU and retrieve the linked PSU context (cf. § 3.4.2) if any.&lt;/li&gt;
    &lt;li&gt;The ASPSP takes into account the access token that establishes the link between the PSU and the AISP.&lt;/li&gt;
  &lt;/ul&gt;
&lt;h3&gt;Business Flow&lt;/h3&gt;
  The AISP asks for the trusted beneficiaries list.
  The ASPSP answers with a list of beneficiary details structure.
""", 
       json.parse(""""""),
       json.parse(""""""),
       List(UserNotLoggedIn, UnknownError),
       Catalogs(notCore, notPSD2, notOBWG), 
       ApiTag("AISP") :: apiTagMockedData :: Nil
     )

     lazy val trustedBeneficiariesGet : OBPEndpoint = {
       case "trusted-beneficiaries" :: Nil JsonGet _ => {
         cc =>
           for {
             (Full(u), callContext) <- authorizeEndpoint(UserNotLoggedIn, cc)
             } yield {
             (json.parse(""""""), callContext)
           }
         }
       }

}



