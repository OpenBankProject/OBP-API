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

import net.liftweb.common.{Loggable, Logger}
import net.liftweb.http.{S, SessionVar}
import net.liftweb.util.{CssSel, Props}

import net.liftweb.util._
import Helpers._




class WebUI extends Loggable{

  @transient protected val log = Logger(this.getClass)

  // Cookie Consent button.
  // Note we don't currently (7th Jan 2017) need to display the cookie consent message due to our limited use of cookies
  // If a deployment does make more use of cookies we would need to add a No button and we might want to make use of the
  // cookie consent kit available at:
  // http://ec.europa.eu/ipg/basics/legal/cookies/index_en.htm#section_2
  def cookieConsent = {
    var onclick = "removeByIdAndSaveIndicatorCookie('cookies-consent'); "
    val buttonString = """<input id="clickMe" type="button" value="Accept and close" onclick="%s"/> <script>showIndicatorCookiePage('cookies-consent'); </script>""".format(onclick)
    val button  = scala.xml.Unparsed(s"""$buttonString""")
    "#clickMe" #> button
  }

  private object firstKnownIPAddress extends SessionVar("")
  private object updateIPaddressEachtime extends SessionVar("")

  //get the IP Address when the user first open the webpage.
  if (firstKnownIPAddress.isEmpty)
    firstKnownIPAddress(S.containerRequest.map(_.remoteAddress).openOr("Unknown"))

  def concurrentLoginsCookiesCheck = {
    updateIPaddressEachtime(S.containerRequest.map(_.remoteAddress).openOr("Unknown"))

    if(!firstKnownIPAddress.isEmpty & !firstKnownIPAddress.get.equals(updateIPaddressEachtime.get)) {
      log.warn("Warning! The Session ID is used in another IP address, it maybe be stolen or you change the network. Please check it first ! ")
      S.error("Warning! Another IP address is also using your Session ID. Did you change your network? ")
    }
    "#cookie-ipaddress-concurrent-logins" #> ""
  }

  def headerLogoLeft = {
    "img [src]" #> Props.get("webui_header_logo_left_url", "")
  }

  def headerLogoRight: CssSel = {
    "img [src]" #> Props.get("webui_header_logo_right_url", "")
  }

  def aboutBackground: CssSel = {
    "#main-about [style]" #> ("background-image: url(" + Props.get("webui_index_page_about_section_background_image_url", "") + ");")
  }

  def aboutText: CssSel = {
    "#main-about-text *" #> scala.xml.Unparsed(Props.get("webui_index_page_about_section_text", ""))
  }

  def apiExplorerLink: CssSel = {
    val tags = S.attr("tags") openOr ""
    ".api-explorer-link a [href]" #> scala.xml.Unparsed(Props.get("webui_api_explorer_url", "") + s"?ignoredefcat=true&tags=$tags")
  }

  // Link to API Manager
  def apiManagerLink: CssSel = {
    ".api-manager-link a [href]" #> scala.xml.Unparsed(Props.get("webui_api_manager_url", ""))
  }




  // Link to API
  def apiLink: CssSel = {
    val hostname = scala.xml.Unparsed(Props.get("hostname", ""))
    ".api-link a *" #>  hostname &
    ".api-link a [href]" #> hostname
  }



  // Social Finance (Sofi)
  def sofiLink: CssSel = {
    ".sofi-link a [href]" #> scala.xml.Unparsed(Props.get("webui_sofi_url", ""))
  }


  // Points to the documentation. Probably a sandbox specific link is good.
  def apiDocumentationLink: CssSel = {
    ".api-documentation-link a [href]" #> scala.xml.Unparsed(Props.get("webui_api_documentation_url", "https://github.com/OpenBankProject/OBP-API/wiki"))
  }

  // For example customers and credentials
  // This relies on the page for sandbox documentation having an anchor called example-customer-logins
  def exampleSandboxCredentialsLink: CssSel = {
    ".example_sandbox_credentials_link a [href]" #> scala.xml.Unparsed(Props.get("webui_api_documentation_url", "") + "#customer-logins")
  }

  // For link to OAuth Client SDKs
  def sdksLink: CssSel = {
    ".sdks_link a [href]" #> scala.xml.Unparsed(Props.get("webui_sdks_url", "https://github.com/OpenBankProject/OBP-API/wiki/OAuth-Client-SDKS"))
  }

  def mainStyleSheet: CssSel = {
    "#main_style_sheet [href]" #> scala.xml.Unparsed(Props.get("webui_main_style_sheet", "/media/css/website.css"))
  }

  def overrideStyleSheet: CssSel = {
    val stylesheet = Props.get("webui_override_style_sheet", "")
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
      Props.get("webui_main_partners")
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
