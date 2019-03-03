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
  /** This will be a UUID for Resource User Docment */
  def userId: String

  def idGivenByProvider: String
  def provider : String
  def emailAddress : String
  def name : String
}

case class UserPrimaryKey(val value : Long) {
  override def toString = value.toString
}