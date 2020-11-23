package code.api.util

import code.util.Helper.MdcLoggable
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
}
