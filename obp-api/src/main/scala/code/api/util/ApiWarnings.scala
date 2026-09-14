/**
Open Bank Project - API
Copyright (C) 2011-2026, TESOBE GmbH.

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

package code.api.util

import code.util.Helper.MdcLoggable
import code.views.system.ViewDefinition
import net.liftweb.util.Props

object ApiWarnings extends MdcLoggable {
  def logWarningsRegardingProperties(): Unit = {
    Props.mode match {
      case Props.RunModes.Production =>
        consentsScaEnabledWarning()
      case _ => // No warnings
    }
  }
  def consentsScaEnabledWarning(): Unit = {
    val scaEnabled = APIUtil.getPropsAsBoolValue("consents.sca.enabled", true)
    if(scaEnabled == false) {
      logger.warn(s"!!!!!!!!!!!!!! Security Consideration: consents.sca.enabled=$scaEnabled !!!!!!!!!!!!!!")
    }
  }
  
  def customViewNamesCheck() = {
    val incorrectViews = ViewDefinition.getCustomViews().filter { view =>
      view.viewId.value.startsWith("_") == false
    }
    if(incorrectViews.size > 0) {
      logger.warn(s"VIEW_NAME_CHECK")
      logger.warn(s"!!!!!!!!!!!!!! There are ${incorrectViews.size} custom view(s) with incorrect names !!!!!!!!!!!!!!")
    } else {
      logger.info(s"Custom VIEW_NAME_CHECK passed")
    }
  }  
  def systemViewNamesCheck() = {
    val incorrectViews = ViewDefinition.getSystemViews().filter { view =>
      view.viewId.value.startsWith("_") == true
    }
    if(incorrectViews.size > 0) {
      logger.warn(s"VIEW_NAME_CHECK")
      logger.warn(s"!!!!!!!!!!!!!! There are ${incorrectViews.size} system view(s) with incorrect names !!!!!!!!!!!!!!")
    } else {
      logger.info(s"System VIEW_NAME_CHECK passed")
    }
  }
  
}
