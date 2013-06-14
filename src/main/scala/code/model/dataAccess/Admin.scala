/**
Open Bank Project - Transparency / Social Finance Web Application
Copyright (C) 2011, 2012, TESOBE / Music Pictures Ltd

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
package code.model.dataAccess

import net.liftweb.mapper._
import net.liftweb.util._
import net.liftweb.common._
import scala.xml.NodeSeq
import net.liftweb.sitemap.Loc.LocGroup
import net.liftweb.http.{S,SessionVar,Templates}
import net.liftweb.json.JsonDSL._
import net.liftweb.http.{SHtml,S}
import net.liftweb.util.Helpers._
import org.bson.types.ObjectId
import com.mongodb.DBObject
import net.liftweb.json.JsonAST.JObject


/**
 * This class  to handel the administration of the API, like the API OAuth keys.
 */
class Admin extends MegaProtoUser[Admin] {
  def getSingleton = Admin // what's the "meta" server
}

object Admin extends Admin with MetaMegaProtoUser[Admin]{

  override def dbTableName = "admins" // define the DB table name

  override def screenWrap = Full(<lift:surround with="default" at="content">
             <lift:bind /></lift:surround>)
  // define the order fields will appear in forms and output
  override def fieldOrder = List(id, firstName, lastName, email,
  locale, timezone, password)

  // comment this line out to require email validations
  override def skipEmailValidation = true

  //Keep track of the referer on login
  object loginReferer extends SessionVar("/")

  //This is where the user gets redirected to after login
  override def homePage = {
    val ret = loginReferer.is
    loginReferer.remove()
    ret
  }

  override def loginXhtml = {
    import net.liftweb.http.TemplateFinder
    import net.liftweb.http.js.JsCmds.Noop
    val loginXml = Templates(List("templates-hidden","_Adminlogin")).map({
        "form [action]" #> {S.uri} &
        "#loginText * " #> {S.??("log.in")} &
        "#emailAddressText * " #> {S.??("email.address")} &
        "#passwordText * " #> {S.??("password")} &
        "#recoverPasswordLink * " #> {
          "a [href]" #> {lostPasswordPath.mkString("/", "/", "")} &
          "a *" #> {S.??("recover.password")}
        }
      })
      SHtml.span(loginXml getOrElse NodeSeq.Empty,Noop)
  }

  //disable the sign up page
  override def createUserMenuLoc = Empty

  // the admin edit page
  override def editUserMenuLoc = Empty

  //Set the login referer
  override def login = {
    for(
      r <- S.referer
      if loginReferer.is.equals("/")
    ) loginReferer.set(r)
    super.login
  }
}