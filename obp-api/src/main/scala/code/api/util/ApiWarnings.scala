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
