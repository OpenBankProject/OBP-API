/**
Open Bank Project - API
Copyright (C) 2011-2018, TESOBE Ltd

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.

Email: contact@tesobe.com
TESOBE Ltd
Osloerstrasse 16/17
Berlin 13359, Germany

  This product includes software developed at
  TESOBE (http://www.tesobe.com/)


*/

package code.snippet

import code.api.util.{APIUtil, CustomJsonFormats}
import code.api.util.APIUtil.getRemoteIpAddress
import code.util.Helper.MdcLoggable
import net.liftweb.http.js.JsCmd
import net.liftweb.http.js.JsCmds.Run
import net.liftweb.http.{S, SessionVar}
import net.liftweb.util.CssSel
import net.liftweb.util.Helpers._
import net.liftweb.util.Props
import code.api.util.APIUtil.activeBrand

import scala.xml.{NodeSeq, XML}
import scala.io.Source




class WebUI extends MdcLoggable{

  @transient protected val log = logger //Logger(this.getClass)

  // Cookie Consent button.
  // Note we don't currently (7th Jan 2017) need to display the cookie consent message due to our limited use of cookies
  // If a deployment does make more use of cookies we would need to add a No button and we might want to make use of the
  // cookie consent kit available at:
  // http://ec.europa.eu/ipg/basics/legal/cookies/index_en.htm#section_2
  def cookieConsent = {
    var onclick = "removeByIdAndSaveIndicatorCookie('cookies-consent'); "
    val buttonString = """<input id="clickMe" class="btn btn-default" type="button" value="Accept and close" onclick="%s"/> <script>showIndicatorCookiePage('cookies-consent'); </script>""".format(onclick)
    val button  = scala.xml.Unparsed(s"""$buttonString""")
    "#clickMe" #> button
  }

  private object firstKnownIPAddress extends SessionVar("")
  private object updateIPaddressEachtime extends SessionVar("")

  //get the IP Address when the user first open the webpage.
  if (firstKnownIPAddress.isEmpty)
    firstKnownIPAddress(getRemoteIpAddress())

  def concurrentLoginsCookiesCheck = {
    updateIPaddressEachtime(getRemoteIpAddress())

    if(!firstKnownIPAddress.isEmpty & !firstKnownIPAddress.get.equals(updateIPaddressEachtime.get)) {
      log.warn("Warning! The Session ID is used in another IP address, it maybe be stolen or you change the network. Please check it first ! ")
      S.error("Warning! Another IP address is also using your Session ID. Did you change your network? ")
    }
    "#cookie-ipaddress-concurrent-logins" #> ""
  }

  def headerLogoLeft = {
    "img [src]" #> APIUtil.getPropsValue("webui_header_logo_left_url", "")
  }

  def headerLogoRight: CssSel = {
    "img [src]" #> APIUtil.getPropsValue("webui_header_logo_right_url", "")
  }

  def footer2LogoLeft = {
    "img [src]" #> APIUtil.getPropsValue("webui_footer2_logo_left_url", "")
  }

  def footer2MiddleText: CssSel = {
    "#footer2-middle-text *" #> scala.xml.Unparsed(APIUtil.getPropsValue("webui_footer2_middle_text", ""))
  }

  def aboutBackground: CssSel = {
    "#main-about [style]" #> ("background-image: url(" + APIUtil.getPropsValue("webui_index_page_about_section_background_image_url", "") + ");")
  }

  def aboutText: CssSel = {
    "#main-about-text *" #> scala.xml.Unparsed(APIUtil.getPropsValue("webui_index_page_about_section_text", ""))
  }

  def topText: CssSel = {
    "#top-text *" #> scala.xml.Unparsed(APIUtil.getPropsValue("webui_top_text", ""))
  }




  val brandString = activeBrand match {
    case Some(v) => s"&brand=$v"
    case _ => ""
  }


  def apiExplorerLink: CssSel = {
    val tags = S.attr("tags") openOr ""
    ".api-explorer-link a [href]" #> scala.xml.Unparsed(APIUtil.getPropsValue("webui_api_explorer_url", "") + s"?ignoredefcat=true&tags=$tags${brandString}")
  }

  // Link to API Manager
  def apiManagerLink: CssSel = {
    ".api-manager-link a [href]" #> scala.xml.Unparsed(APIUtil.getPropsValue("webui_api_manager_url", ""))
  }

  // Link to API Tester
  def apiTesterLink: CssSel = {
    ".api-tester-link a [href]" #> scala.xml.Unparsed(APIUtil.getPropsValue("webui_api_tester_url", ""))
  }

  // Link to API
  def apiLink: CssSel = {
    val hostname = scala.xml.Unparsed(APIUtil.getPropsValue("hostname", ""))
    ".api-link a *" #>  hostname &
    ".api-link a [href]" #> hostname
  }

  // Link to API Human
  def apiLinkHuman: CssSel = {
    val hostname = scala.xml.Unparsed(APIUtil.getPropsValue("hostname", ""))
      ".api-link a [href]" #> hostname
  }



  // Social Finance (Sofi)
  def sofiLink: CssSel = {
    ".sofi-link a [href]" #> scala.xml.Unparsed(APIUtil.getPropsValue("webui_sofi_url", ""))
  }

  // CreateDirectLoginToken
  def createDirectLoginToken: CssSel = {
    ".create-direct-login-token-link a [href]" #> scala.xml.Unparsed(APIUtil.getPropsValue("webui_create_directlogin_token_url", ""))
  }

  // Points to the documentation. Probably a sandbox specific link is good.
  def apiDocumentationLink: CssSel = {
    ".api-documentation-link a [href]" #> scala.xml.Unparsed(APIUtil.getPropsValue("webui_api_documentation_url", "https://github.com/OpenBankProject/OBP-API/wiki"))
  }

  // For example customers and credentials
  // This relies on the page for sandbox documentation having an anchor called example-customer-logins
  def exampleSandboxCredentialsLink: CssSel = {
    ".example_sandbox_credentials_link a [href]" #> scala.xml.Unparsed(APIUtil.getPropsValue("webui_api_documentation_url", "") + "#customer-logins")
  }

  // For link to OAuth Client SDKs
  def sdksLink: CssSel = {
    ".sdks_link a [href]" #> scala.xml.Unparsed(APIUtil.getPropsValue("webui_sdks_url", "https://github.com/OpenBankProject/OBP-API/wiki/OAuth-Client-SDKS"))
  }

  // Text about data in FAQ
  def faqDataText: CssSel = {
    ".faq-data-text *" #> scala.xml.Unparsed(APIUtil.getPropsValue("webui_faq_data_text", "This depends on the end point and/or OBP instance you are using. A combination of synthetic, anonymised and real data may be available. Please ask support for more information."))
  }

  // Link to FAQ
  def faqLink: CssSel = {
    val link = scala.xml.Unparsed(APIUtil.getPropsValue("webui_faq_url", "https://openbankproject.com/faq/"))
    //".faq-link a *" #>  link &
    ".faq-link a [href]" #> link
  }

  // Email address re FAQ
  def faqEmail: CssSel = {
    val email = scala.xml.Unparsed(APIUtil.getPropsValue("webui_faq_email", "contact@openbankproject.com"))
    val emailMailto = scala.xml.Unparsed("mailto:" + email.toString())
    ".faq-email a *" #>  email &
    ".faq-email a [href]" #> emailMailto
  }

  // API Explorer URL from Props
  val apiExplorerUrl = scala.xml.Unparsed(APIUtil.getPropsValue("webui_api_explorer_url", ""))

  // DirectLogin documentation url
  def directLoginDocumentationUrl: CssSel = {
    val directlogindocumentationurl = scala.xml.Unparsed(APIUtil.getPropsValue("webui_direct_login_documentation_url", apiExplorerUrl + "/glossary#Direct-Login"))
    ".direct-login-documentation-url a [href]" #> directlogindocumentationurl
  }

  // OAuth1.0a documentation url
  def oauth1DocumentationUrl: CssSel = {
    val oauth1documentationurl = scala.xml.Unparsed(APIUtil.getPropsValue("webui_oauth_1_documentation_url", apiExplorerUrl + "/glossary#OAuth-1.0a"))
    ".oauth-1-documentation-url a [href]" #> oauth1documentationurl
  }

  // OAuth2.0 link on FAQ
  def oauth2DocumentationUrl: CssSel = {
    val oauth2documentationurl = scala.xml.Unparsed(APIUtil.getPropsValue("webui_oauth_2_documentation_url", apiExplorerUrl + "/glossary#OAuth-2"))
    ".oauth-2-documentation-url a [href]" #> oauth2documentationurl
  }

  // Link to Glossary on API Explorer
  def glossaryLink: CssSel = {
    val glossarylink = apiExplorerUrl + "/glossary"
    ".glossary-link a [href]" #> glossarylink
  }

  // Support platform link
  def supportPlatformLink: CssSel = {
    val supportplatformlink = scala.xml.Unparsed(APIUtil.getPropsValue("webui_support_platform_url", "https://slack.openbankproject.com/"))
        ".support-platform-link a [href]" #> supportplatformlink &
          ".support-platform-link a *" #> supportplatformlink.toString().replace("https://","").replace("http://", "")
  }


  // Page title
  def pageTitle = {
    val prefix = APIUtil.getPropsValue("webui_page_title_prefix", "Open Bank Project: ")
    scala.xml.XML.loadString(s"<title>$prefix<lift:Menu.title /></title>")
  }

  def mainStyleSheet: CssSel = {
    "#main_style_sheet [href]" #> scala.xml.Unparsed(APIUtil.getPropsValue("webui_main_style_sheet", "/media/css/website.css"))
  }



  def getStartedText: CssSel = {
    "@get-started *" #> scala.xml.Unparsed(APIUtil.getPropsValue("webui_get_started_text", "Get started building your applications now!"))
  }


  val displayForBanks = if (APIUtil.getPropsAsBoolValue("webui_display_for_banks_section", true)) {
    logger.info("show for banks section")
    "block"
  } else {
    logger.info("not show for banks section")
    "none"
  }


  def forBanks: CssSel = {
    "@for-banks [style]" #> s"display: $displayForBanks"
  }



  def getStartedContentLoader: NodeSeq = {
    contentLoader("webui_get_started_content_url", "main-get-started")
  }

  def vendorSupportContentLoader: NodeSeq = {
    contentLoader("webui_vendor_support_content_url", "vendor-support")
  }

  def aboutVendorContentLoader: NodeSeq = {
    contentLoader("webui_about_vendor_content_url", "about-vendor")
  }


// This load content directly into the div that calls it.
  def getStartedDirectContentLoader: NodeSeq = {
    directContentLoader("webui_get_started_content_url")
  }

  def vendorSupporDirectContentLoader: NodeSeq = {
    directContentLoader("webui_vendor_support_content_url")
  }

  def aboutVendorDirectContentLoader: NodeSeq = {
    directContentLoader("webui_about_vendor_content_url")
  }




  /*
  This will return a script tag which will use jQuery to load HTML content from an external URL (which is specified in Props)
  - and replace the specified DIV with that content.
  Note this causes a browser warning : Synchronous XMLHttpRequest on the main thread is deprecated...
  TODO Use alternate approach to load -  see GetHtmlFromUrl.scala
   */

  def contentLoader(property: String, contentId : String) : NodeSeq = {
    // Get the url that has the content we want to use
    val url = APIUtil.getPropsValue(property, "")


    // See if we need to do something
    val activeScriptTag: NodeSeq = url match {
      case "" => <script></script> // Do nothing
      case _ => {
        // Prepare a script tag that will load the content into the div we specify by contentId

        val scriptTag : String =  s"""<script>jQuery("#$contentId").load("$url");</script>""".toString
        XML.loadString(scriptTag) // Create XML from String
      }
    }
    // Return the script tag
      activeScriptTag
  }


  /*
  Loads content directly into the div (no explicit javascript to load it.)
  Note this causes multiple comet calls.
   */
  def directContentLoader(property: String) : NodeSeq = {
    // Get the url that has the content we want to use
    val url = APIUtil.getPropsValue(property, "")

    // Try to get that content.
    val htmlTry = tryo(scala.io.Source.fromURL(url))
    logger.info("htmlTry: " + htmlTry)

    // Convert to a string
    val htmlString = htmlTry.map(_.mkString).getOrElse("")
    logger.info("htmlString: " + htmlString)

    // Create an HTML object
    val html = XML.loadString(htmlString)

    // Sleep if in development environment so can see the effects of content loading slowly
    if (Props.mode == Props.RunModes.Development) Thread.sleep(10 seconds)

    // Return the HTML
    html
  }



  def overrideStyleSheet: CssSel = {
    val stylesheet = APIUtil.getPropsValue("webui_override_style_sheet", "")
    if (stylesheet.isEmpty) {
      "#override_style_sheet" #> ""
    } else {
      "#override_style_sheet [href]" #> scala.xml.Unparsed(stylesheet)
    }
  }

  // Used to represent partners or sponsors of this API instance
  case class Partner(
      logoUrl: String,
      homePageUrl: String,
      altText: String
      )

  // Builds the grid of images / links for partners on the home page
  def createMainPartners: CssSel = {

    import net.liftweb.json._
    implicit val formats = CustomJsonFormats.formats


    val mainPartners: Option[String] = {
      APIUtil.getPropsValue("webui_main_partners")
    }


    val partners = mainPartners match {
      // If we got a value, and can parse the text to Json AST and then extract List[Partner], do that!
      // We expect the following Json string in the Props
      // webui_main_partners=[{"logoUrl":"www.example.com/logoA.png", "homePageUrl":"www.example.com/indexA.html", "altText":"Alt Text A"},{"logoUrl":"www.example.com/logoB.png", "homePageUrl":"www.example.com/indexB.html", "altText":"Alt Text B"}]

      case Some(ps) => {
        try {
          // Parse the Json string into Json AST and then extract a list of Partners
          (parse(ps)).extract[List[Partner]]
        }
        catch {
          case e: Exception => {
            logger.warn(s"You provided a value for webui_main_partners in your Props file but I can't parse it / extract it to a List[Partner]: Exception is: $e")
            Nil
          }
        }
      }
      case _ => Nil
    }

    // Select the "a" tag inside the "main-partners" div and generate a link for each partner
    "#main-partners a" #> partners.map { i =>
      "a [href]" #> s"${i.homePageUrl}" &
      "img [src]" #> s"${i.logoUrl}" &
      "img [alt]" #> s"${i.altText}"
    }
  }
}
