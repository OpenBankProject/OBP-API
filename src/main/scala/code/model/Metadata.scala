/**
Open Bank Project - API
Copyright (C) 2011, 2013, TESOBE / Music Pictures Ltd

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

package code.model
import java.util.Date
import java.net.URL
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

  //if this is a reply, the id of the original comment
  def replyToID : String

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

trait Tag {

  def id_ : String
  def datePosted : Date
  def postedBy : Box[User]
  def viewId : Long
  def value : String
}

trait GeoTag {

  def datePosted : Date
  def postedBy : Box[User]
  def viewId : Long
  def longitude : Double
  def latitude : Double
}

trait TransactionImage {

  def id_ : String
  def datePosted : Date
  def postedBy : Box[User]
  def viewId : Long
  def description : String
  def imageUrl : URL
}

class OtherBankAccountMetadata(
  val publicAlias : String,
  val privateAlias : String,
  val moreInfo : String,
  val url : String,
  val imageURL : String,
  val openCorporatesURL : String,
  val corporateLocation : Option[GeoTag],
  val physicalLocation : Option[GeoTag],
  val addMoreInfo : (String) => Boolean,
  val addURL : (String) => Boolean,
  val addImageURL : (String) => Boolean,
  val addOpenCorporatesURL : (String) => Boolean,

  /**
  * @param: userId
  * @param: viewId
  * @param: datePosted
  * @param: longitude
  * @param: latitude
  */
  val addCorporateLocation : (String, Long, Date, Double, Double) => Boolean,
  val deleteCorporateLocation : () => Boolean,
  /**
  * @param: userId
  * @param: viewId
  * @param: datePosted
  * @param: longitude
  * @param: latitude
  */
  val addPhysicalLocation : (String, Long, Date, Double, Double) => Boolean,
  val deletePhysicalLocation : () => Boolean,
  val addPublicAlias : (String) => Boolean,
  val addPrivateAlias : (String) => Boolean
)

class TransactionMetadata(
  val ownerComment : String,
  val addOwnerComment : String => Unit,
  val comments: List[Comment],
  /**
  * @param: userId
  * @param: viewId
  * @param: text
  * @param: datePosted
  */
  val addComment : (String,Long, String, Date) => Comment,
  /**
  * @param: commentId
  */
  val deleteComment : (String) => Box[Unit],

  val tags: List[Tag],
  /**
  * @param: userId
  * @param: viewId
  * @param: tag
  * @param: datePosted
  */
  val addTag: (String, Long, String, Date) => Tag,
  /**
  * @param: tagId
  */
  val deleteTag : (String) => Box[Unit],
  val images : List[TransactionImage],
  /**
  * @param: userId
  * @param: viewId
  * @param: description
  * @param: datePosted
  * @param: imageURL
  */
  val addImage : (String, Long, String, Date, URL) => TransactionImage,
  /**
  * @param: imageId
  */
  val deleteImage : String => Unit,
  /**
  * @param: userId
  * @param: viewId
  * @param: datePosted
  * @param: longitude
  * @param: latitude
  */
  val whereTags : List[GeoTag],
  /**
  * @param: userId
  * @param: viewId
  * @param: datePosted
  * @param: longitude
  * @param: latitude
  */
  val addWhereTag : (String, Long, Date, Double, Double) => Boolean,
  /**
  * @param: viewId
  */
  val deleteWhereTag : (Long) => Boolean
)