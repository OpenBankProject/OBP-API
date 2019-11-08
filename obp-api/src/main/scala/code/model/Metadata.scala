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

package code.model

import code.util.Helper
import com.openbankproject.commons.model._
import net.liftweb.common.{Box, Full}
import net.liftweb.json.JsonAST.JObject
import net.liftweb.json.JsonDSL._


case class CommentExtended(val comment: Comment) {

  @deprecated(Helper.deprecatedJsonGenerationMessage)
  def toJson : JObject = {
    val userInJson = comment.postedBy match {
      case Full(user) => user.toJson
      case _ => ("id" -> "") ~
        ("provider" -> "") ~
        ("display_name" -> "")
    }

    ("id" -> comment.id_) ~
      ("date" -> comment.datePosted.toString) ~
      ("comment" -> comment.text) ~
      ("view" -> comment.viewId.value) ~
      ("user" -> userInJson) ~
      ("reply_to" -> "")
  }
}
