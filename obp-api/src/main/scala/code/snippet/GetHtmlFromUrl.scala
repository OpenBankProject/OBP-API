/**
Open Bank Project - API
Copyright (C) 2011-2019, TESOBE GmbH.

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
TESOBE GmbH.
Osloer Strasse 16/17
Berlin 13359, Germany

This product includes software developed at
TESOBE (http://www.tesobe.com/)

  */

package code.snippet

import net.liftweb.util.{Helpers, Props}
import Helpers._
import code.webuiprops.MappedWebUiPropsProvider.getWebUiPropsValue
import code.util.Helper.MdcLoggable

import scala.xml.{NodeSeq, XML}

/**
  * Do something that can take a long time to do
  */
object GetHtmlFromUrl extends MdcLoggable {

    def vendorSupport(): NodeSeq = {
      val vendorSupportHtmlUrl = getWebUiPropsValue("webui_vendor_support_html_url", "")

      def vendorSupportHtml = tryo(scala.io.Source.fromURL(vendorSupportHtmlUrl))
      logger.debug("vendorSupportHtml: " + vendorSupportHtml)
      def vendorSupportHtmlScript = vendorSupportHtml.map(_.mkString).getOrElse("")
      logger.debug("vendorSupportHtmlScript: " + vendorSupportHtmlScript)
      val jsVendorSupportHtml: NodeSeq = vendorSupportHtmlScript match {
        case "" => <script></script>
        case _ => XML.loadString(vendorSupportHtmlScript)
      }
      logger.debug("jsVendorSupportHtml: " + jsVendorSupportHtml)

      // sleep for up to 5 seconds at development environment
      if (Props.mode == Props.RunModes.Development) Thread.sleep(randomLong(3 seconds))

      jsVendorSupportHtml
    }

}
