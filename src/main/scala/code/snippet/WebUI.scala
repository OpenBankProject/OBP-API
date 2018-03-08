/**
Open Bank Project - API
Copyright (C) 2011-2016, TESOBE Ltd

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
  by
  Simon Redfern : simon AT tesobe DOT com
  Stefan Bethge : stefan AT tesobe DOT com
  Everett Sochowski : everett AT tesobe DOT com
  Ayoub Benali: ayoub AT tesobe DOT com

*/

package code.snippet

import code.api.util.APIUtil
import code.api.util.APIUtil.getRemoteIpAddress
import code.util.Helper.MdcLoggable
import net.liftweb.http.{S, SessionVar}
import net.liftweb.util.CssSel
import net.liftweb.util.Helpers._




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

  def apiExplorerLink: CssSel = {
    val tags = S.attr("tags") openOr ""
    ".api-explorer-link a [href]" #> scala.xml.Unparsed(APIUtil.getPropsValue("webui_api_explorer_url", "") + s"?ignoredefcat=true&tags=$tags")
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



  // Social Finance (Sofi)
  def sofiLink: CssSel = {
    ".sofi-link a [href]" #> scala.xml.Unparsed(APIUtil.getPropsValue("webui_sofi_url", ""))
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

  // Page title
  def pageTitle = {
    val prefix = APIUtil.getPropsValue("webui_page_title_prefix", "Open Bank Project: ")
    scala.xml.XML.loadString(s"<title>$prefix<lift:Menu.title /></title>")
  }

  def mainStyleSheet: CssSel = {
    "#main_style_sheet [href]" #> scala.xml.Unparsed(APIUtil.getPropsValue("webui_main_style_sheet", "/media/css/website.css"))
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
    implicit val formats = DefaultFormats


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
