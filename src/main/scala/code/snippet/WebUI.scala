/**
Open Bank Project - API
Copyright (C) 2011-2015, TESOBE / Music Pictures Ltd

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
TESOBE / Music Pictures Ltd
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

import net.liftweb.common.Loggable

import net.liftweb.util.{CssSel, Props}

import net.liftweb.util._
import Helpers._




class WebUI extends Loggable{
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
    ".about-text *" #> scala.xml.Unparsed(Props.get("webui_index_page_about_section_text", ""))
  }

  def apiExplorerLink: CssSel = {
    ".api-explorer-link a [href]" #> scala.xml.Unparsed(Props.get("webui_api_explorer_url", ""))
  }

  def apiDocumentationLink: CssSel = {
    ".api-documentation-link a [href]" #> scala.xml.Unparsed(Props.get("webui_api_documentation_url", "https://github.com/OpenBankProject/OBP-API/wiki"))
  }


  def mainStyleSheet: CssSel = {
    "#main_style_sheet [href]" #> scala.xml.Unparsed(Props.get("webui_main_style_sheet", "/media/css/website.css"))
  }

  def overrideStyleSheet: CssSel = {
    "#override_style_sheet [href]" #> scala.xml.Unparsed(Props.get("webui_override_style_sheet", ""))
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