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

package com.openbankproject.commons.model

import java.net.URL
import java.util.Date

import net.liftweb.common.Box


trait Comment {
  def id_ : String
  // The person that posted the comment
  def postedBy : Box[User]

  //the id of the view related to the comment
  def viewId : ViewId

  // The actual text of the comment
  def text : String

  def datePosted : Date

  //if this is a reply, the id of the original comment
  def replyToID : String
}

trait TransactionTag {

  def id_ : String
  def datePosted : Date
  def postedBy : Box[User]
  def viewId : ViewId
  def value : String
}

trait GeoTag {

  def datePosted : Date
  def postedBy : Box[User]
  def longitude : Double
  def latitude : Double
}

trait TransactionImage {

  def id_ : String
  def datePosted : Date
  def postedBy : Box[User]
  def viewId : ViewId
  def description : String
  def imageUrl : URL
}

/*
Counterparty metadata
 */
trait CounterpartyMetadata {
  //metadataId == counterpartyId, so they are the same thing now.
  def getCounterpartyId: String
  def getCounterpartyName: String
  def getPublicAlias: String
  def getPrivateAlias: String
  def getMoreInfo: String
  def getUrl: String
  def getImageURL: String
  def getOpenCorporatesURL: String
  def getCorporateLocation: Option[GeoTag]
  def getPhysicalLocation: Option[GeoTag]
  val addMoreInfo: (String) => Boolean
  val addURL: (String) => Boolean
  val addImageURL: (String) => Boolean
  val addOpenCorporatesURL: (String) => Boolean

  /**
   * @param: userId
   * @param: datePosted
   * @param: longitude
   * @param: latitude
   */
  val addCorporateLocation: (UserPrimaryKey, Date, Double, Double) => Boolean
  val deleteCorporateLocation: () => Boolean
  /**
   * @param: userId
   * @param: datePosted
   * @param: longitude
   * @param: latitude
   */
  val addPhysicalLocation: (UserPrimaryKey, Date, Double, Double) => Boolean
  val deletePhysicalLocation: () => Boolean
  val addPublicAlias: (String) => Boolean
  val addPrivateAlias: (String) => Boolean
}

class TransactionMetadata(
  val ownerComment : () => String,
  val addOwnerComment : String => Boolean,
  
  /**
    * @param: viewId
    */
  val comments: (ViewId) => List[Comment],
  /**
  * @param: userId
  * @param: viewId
  * @param: text
  * @param: datePosted
  */
  val addComment : (UserPrimaryKey, ViewId, String, Date) => Box[Comment],
  /**
  * @param: commentId
  */
  val deleteComment : (String) => Box[Boolean],

  val tags: (ViewId) => List[TransactionTag],
  /**
  * @param: userId
  * @param: viewId
  * @param: tag
  * @param: datePosted
  */
  val addTag: (UserPrimaryKey, ViewId, String, Date) => Box[TransactionTag],
  /**
  * @param: tagId
  */
  val deleteTag : (String) => Box[Boolean],
  val images : (ViewId) => List[TransactionImage],
  /**
  * @param: userId
  * @param: viewId
  * @param: description
  * @param: datePosted
  * @param: imageURL
  */
  val addImage : (UserPrimaryKey, ViewId, String, Date, String) => Box[TransactionImage],
  /**
  * @param: imageId
  */
  val deleteImage : String => Box[Boolean],
  /**
  * @param: userId
  * @param: viewId
  * @param: datePosted
  * @param: longitude
  * @param: latitude
  */
  val whereTags : (ViewId) => Option[GeoTag],
  /**
  * @param: userId
  * @param: viewId
  * @param: datePosted
  * @param: longitude
  * @param: latitude
  */
  val addWhereTag : (UserPrimaryKey, ViewId, Date, Double, Double) => Boolean,
  /**
  * @param: viewId
  */
  val deleteWhereTag : (ViewId) => Boolean
)