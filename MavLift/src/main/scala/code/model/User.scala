/** 
Open Bank Project

Copyright 2011,2012 TESOBE / Music Pictures Ltd.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and 
limitations under the License.       

Open Bank Project (http://www.openbankproject.com)
      Copyright 2011,2012 TESOBE / Music Pictures Ltd

      This product includes software developed at
      TESOBE (http://www.tesobe.com/)
		by 
		Simon Redfern : simon AT tesobe DOT com
		Everett Sochowski: everett AT tesobe DOT com

 */
package code.model

import net.liftweb.mapper._
import net.liftweb.util._
import net.liftweb.common._
import net.liftweb.record.field.StringField
import scala.xml.NodeSeq
import net.liftweb.sitemap.Loc.LocGroup
import net.liftweb.http.S
import net.liftweb.http.SessionVar

/**
 * An O-R mapped "User" class that includes first name, last name, password
 */
class User extends MegaProtoUser[User] with OneToMany[Long, User]{
  def getSingleton = User // what's the "meta" server
  
  object Privileges extends MappedOneToMany(Privilege, Privilege.user)
}

/**
 * The singleton that has methods for accessing the database
 */
object User extends User with MetaMegaProtoUser[User]{
  override def dbTableName = "users" // define the DB table name
    
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
    var ret = loginReferer.is
    loginReferer.remove()
    ret
  }
  
  //Set the login referer
  override def login = {
    for(r <- S.referer if loginReferer.is.equals("/")) loginReferer.set(r)
    super.login
  }
  
  def hasOurNetworkPermission(account: Account) : Boolean = {
    hasPermission(account, (p: Privilege) => p.ourNetworkPermission.is)
  }
  
  def hasTeamPermission(account: Account) : Boolean = {
    hasPermission(account, (p: Privilege) => p.teamPermission.is)
  }
  
  def hasBoardPermission(account: Account) : Boolean = {
    hasPermission(account, (p: Privilege) => p.boardPermission.is)
  }
  
  def hasAuthoritiesPermission(account: Account) : Boolean = {
    hasPermission(account, (p: Privilege) => p.authoritiesPermission.is)
  }
  
  def hasOwnerPermission(account: Account) : Boolean = {
    hasPermission(account, (p: Privilege) => p.ownerPermission.is)
  }
  
  def hasMoreThanAnonAccess(account: Account) : Boolean = {
      User.hasAuthoritiesPermission(account) ||
      User.hasBoardPermission(account) ||
      User.hasOurNetworkPermission(account) ||
      User.hasOwnerPermission(account) ||
      User.hasTeamPermission(account)
  }
  
  def hasPermission(account: Account, permissionCheck: (Privilege) => Boolean) : Boolean = {
    currentUser match{
      case Full(u) => {
        val permission = u.Privileges.find(_.accountID.equals(account.id.toString))
        permission match{
          case Some(p) => {
        	permissionCheck(p)
          }
          case _ => false
        }
      }
      case _ => false
    }
  }
}

/**
 * Yes, MappedBoolean has a default value of false, but in the very small chance
 * that this changes, we won't break any authentication.
 */
class ourMappedBoolean[T<:Mapper[T]](fieldOwner: T) extends MappedBoolean[T](fieldOwner){
  override def defaultValue = false
}

class Privilege extends LongKeyedMapper[Privilege] with IdPK with CreatedUpdated
	with OneToMany[Long, Privilege]{
  def getSingleton = Privilege
  object user extends LongMappedMapper(this, User){
    override def validSelectValues =
    	Full(User.findMap(
    			OrderBy(User.email, Ascending)){
    			case u: User => Full(u.id.is -> u.email.is)
    	})
    override def displayHtml = <span>User email</span>  //TODO: we don't want HTML in the code
    override def asHtml = {
      val email = (for {
    	  u <- User.find(user.get)
      } yield u.email.get).getOrElse("User email not found")

      <span>{email}</span>
    }
  }
  
  object accountID extends MappedString(this, 255)
  object ourNetworkPermission extends ourMappedBoolean(this){
    override def displayName = "Our Network"
  }
  object teamPermission extends ourMappedBoolean(this) {
    override def displayName= "Team"
  }
  object boardPermission extends ourMappedBoolean(this) {
    override def displayName = "Board"
  }
  object authoritiesPermission extends ourMappedBoolean(this) {
    override def displayName = "Authorities"
  }
  object ownerPermission extends ourMappedBoolean(this) {
    override def displayName = "Owner"
  }
}

object Privilege extends Privilege with LongKeyedMetaMapper[Privilege] with CRUDify[Long, Privilege]{
  override def calcPrefix = List("admin",_dbTableNameLC)
  override def displayName = "Privilege"
  override def showAllMenuLocParams = LocGroup("admin") :: Nil
  override def createMenuLocParams = LocGroup("admin") :: Nil
  override def fieldsForDisplay = super.fieldsForDisplay -- List(createdAt)
  override def fieldsForEditing = super.fieldsForEditing -- List(createdAt, updatedAt)
  
  def showAll = doCrudAll(_)
}

