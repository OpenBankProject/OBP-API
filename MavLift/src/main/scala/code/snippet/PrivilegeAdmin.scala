package code.snippet

import net.liftweb.util.Helpers._
import code.model.Privilege

class PrivilegeAdmin {

  def createPrivileges = {
    ".navlink [href]" #> Privilege.createPathString
  }
  
  def listPrivileges = {
    "*" #> {
      //TODO: There must be a better way of doing this... but Privilege.showAllTemplate 
      // and many alternatives give a snippet not found error,
      Privilege.showAll(Privilege._showAllTemplate) \ "table"
    }
  }
  
}