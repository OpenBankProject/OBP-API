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

package code.model.traits
import java.util.Date
import net.liftweb.common.{Box,Full}
import net.liftweb.json.JsonAST.JObject
import net.liftweb.json.JsonDSL._

trait Comment {
  def id_ : String
  // The person that posted the comment
  def postedBy : Box[User] 
  
  //the id of the view related to the comment 
  def viewId : Long

  // The actual text of the comment
  def text : String
  
  def datePosted : Date
  
  def toJson : JObject = {
    val userInJson = postedBy match {
      case Full(user) => user.toJson
      case _ => ("id" -> "") ~
                ("provider" -> "") ~
                ("display_name" -> "")
    }
    
    ("id" -> id_) ~ 
    ("date" -> datePosted.toString) ~
    ("comment" -> text) ~
    ("view" -> viewId) ~
    ("user" -> userInJson) ~
    ("reply_to" -> "")
  }
}