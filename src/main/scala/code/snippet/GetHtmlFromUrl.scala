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
  by
  Simon Redfern : simon AT tesobe DOT com
  Stefan Bethge : stefan AT tesobe DOT com
  Everett Sochowski : everett AT tesobe DOT com
  Ayoub Benali: ayoub AT tesobe DOT com

*/

package code.snippet

import net.liftweb.util.{Helpers, Props}
import Helpers._
import code.api.util.APIUtil

import scala.xml.{NodeSeq, XML}

/**
  * Do something that can take a long time to do
  */
object GetHtmlFromUrl {

    def vendorSupport(): NodeSeq = {
      val vendorSupportHtmlUrl = APIUtil.getPropsValue("webui_vendor_support_html_url", "")

      def vendorSupportHtmlScript = tryo(scala.io.Source.fromURL(vendorSupportHtmlUrl)).map(_.mkString).getOrElse("")

      val jsVendorSupportHtml: NodeSeq = vendorSupportHtmlScript match {
        case "" => <script></script>
        case _ => XML.loadString(vendorSupportHtmlScript)
      }

      // sleep for up to 5 seconds at development environment
      if (Props.mode == Props.RunModes.Development) Thread.sleep(randomLong(5 seconds))

      jsVendorSupportHtml
    }

}
