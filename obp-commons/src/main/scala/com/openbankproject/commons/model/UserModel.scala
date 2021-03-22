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

/**
 * An O-R mapped "User" class that includes first name, last name, password
  *
  * 1 AuthUser : is used for authentication, only for webpage Login in stuff
  *   1) It is MegaProtoUser, has lots of methods for validation username, password, email ....
  *      Such as lost password, reset password .....
  *      Lift have some helper methods to make these things easily.
  *
  *
  *
  * 2 ResourceUser: is only a normal LongKeyedMapper
  *   1) All the accounts, transactions ,roles, views, accountHolders, customers... should be linked to ResourceUser.userId_ field.
  *   2) The consumer keys, tokens are also belong ResourceUser
  *
  *
  * 3 RelationShips:
  *   1)When `Sign up` new user --> create AuthUser --> call AuthUser.save() --> create ResourceUser user.
  *      They share the same username and email.
  *   2)AuthUser `user` field as the Foreign Key to link to Resource User.
  *      one AuthUser <---> one ResourceUser
  *
 */
trait User {

  /**This will return resouceUser primary key: it is a long value !!!
    * This should not be exposed to outside. */
  def userPrimaryKey : UserPrimaryKey
  /** This will be a UUID for Resource User Document */
  def userId: String
  def idGivenByProvider: String
  def provider : String
  def emailAddress : String
  def name : String
  
  //this will be consentId which create the user, if the user is created by obp or other approaches, it will be None. 
  def createdByConsentId: Option[String]
  def isOriginalUser  = createdByConsentId.isEmpty
  def isConsentUser  = createdByConsentId.nonEmpty
}

case class UserPrimaryKey(val value : Long) {
  override def toString = value.toString
}

trait UserAuthContextUpdate {
  def userAuthContextUpdateId : String
  def userId : String
  def key : String
  def value : String
  def challenge: String
  def status: String
}
case class UserAuthContextUpdateCommons(
                                         userAuthContextUpdateId: String,
                                         userId: String,
                                         key: String,
                                         value: String,
                                         challenge: String,
                                         status: String
                                       ) extends UserAuthContextUpdate

object UserAuthContextUpdateStatus extends Enumeration {
  type ConsentStatus = Value
  val INITIATED, ACCEPTED, REJECTED = Value
}