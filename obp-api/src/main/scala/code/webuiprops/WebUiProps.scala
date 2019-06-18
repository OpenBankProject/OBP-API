package code.webuiprops

/* For Connector method routing, star connector use this provider to find proxy connector name */

import com.openbankproject.commons.model.{Converter, JsonFieldReName}
import net.liftweb.common.Box

trait WebUiPropsT {
  def webUiPropsId: Option[String]
  def name: String
  def value: String
}

case class WebUiPropsCommons(name: String,
                             value: String, webUiPropsId: Option[String] = None) extends WebUiPropsT with JsonFieldReName

object WebUiPropsCommons extends Converter[WebUiPropsT, WebUiPropsCommons]

trait WebUiPropsProvider {
  def getAll(): List[WebUiPropsT]

  def createOrUpdate(webUiProps: WebUiPropsT): Box[WebUiPropsT]

  def delete(webUiPropsId: String):Box[Boolean]

  def getWebUiPropsValue(nameOfProperty: String, defaultValue: String): String
}






