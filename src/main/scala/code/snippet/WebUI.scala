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

import net.liftweb.util.Helpers._
import net.liftweb.util.{CssSel, Props}

import scala.xml.NodeSeq

class WebUI {
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
}
