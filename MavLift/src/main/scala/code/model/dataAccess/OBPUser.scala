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
package code.model.dataAccess

import net.liftweb.mapper._
import net.liftweb.util._
import net.liftweb.common._
import net.liftweb.record.field.StringField
import scala.xml.NodeSeq
import net.liftweb.sitemap.Loc.LocGroup
import net.liftweb.http.S
import net.liftweb.http.SessionVar
import com.mongodb.QueryBuilder
import code.model.traits.{View,BankAccount,User}
import code.model.implementedTraits._
/**
 * An O-R mapped "User" class that includes first name, last name, password
 */
class OBPUser extends MegaProtoUser[OBPUser] with OneToMany[Long, OBPUser] with User{
  def getSingleton = OBPUser // what's the "meta" server
  
  def emailAddress = email.get
  def userName = firstName.get
  
  def permittedViews(bankAccount : String) : Set[View] = {
    MongoDBLocalStorage.getAccount(bankAccount) match {
      case Full(account) => {
        var views : Set[View] = Set()
        if(OBPUser.hasOurNetworkPermission(account)) views = views + OurNetwork
        if(OBPUser.hasTeamPermission(account)) views = views + Team
        if(OBPUser.hasBoardPermission(account)) views = views + Board
        if(OBPUser.hasAuthoritiesPermission(account)) views = views + Authorities
        if(account.anonAccess.get) views = views + Anonymous
        views
      }
      case _ => Set() 
    }
  }
}

/**
 * The singleton that has methods for accessing the database
 */
object OBPUser extends OBPUser with MetaMegaProtoUser[OBPUser]{
  
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
      OBPUser.hasAuthoritiesPermission(account) ||
      OBPUser.hasBoardPermission(account) ||
      OBPUser.hasOurNetworkPermission(account) ||
      OBPUser.hasOwnerPermission(account) ||
      OBPUser.hasTeamPermission(account)
  }
  
  def hasPermission(account: Account, permissionCheck: (Privilege) => Boolean) : Boolean = {
    currentUser match{
      case Full(u) => {
        val permission = Privilege.find(By(Privilege.accountID, account.id.toString), 
            							 By(Privilege.user, u))
        permission match{
          case Full(p) => {
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
  object user extends LongMappedMapper(this, OBPUser){
    override def validSelectValues =
    	Full(OBPUser.findMap(
    			OrderBy(OBPUser.email, Ascending)){
    			case u: User => Full(u.id.is -> u.email.is)
    	})
    override def displayHtml = <span>User email</span>  //TODO: we don't want HTML in the code
    override def asHtml = {
      val email = (for {
    	  u <- OBPUser.find(user.get)
      } yield u.email.get).getOrElse("User email not found")

      <span>{email}</span>
    }
  }
  
  object accountID extends MappedString(this, 255){
    //
    // WARNING!
    //
    // Once we extend the OBP functionality to support multiple accounts, this will need to be changed
    // or else any account owner (i.e. anyone who sets privileges) will be setting privileges on the 
    // music pictures account instead of their own.
    //
    override def defaultValue = {
      val qry = QueryBuilder.start("obp_transaction.other_account.holder").is("Music Pictures Limited").get
      val currentAcc = Account.currentAccount
      
      currentAcc match{
        case Full(a) => {
          a.id.get.toString
        }
        case _ => "no_acc_id_defined"
      }
    }
  }
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
  override def fieldsForDisplay = super.fieldsForDisplay -- List(createdAt, accountID)
  override def fieldsForEditing = super.fieldsForEditing -- List(createdAt, updatedAt, accountID)
  
  def showAll = doCrudAll(_)
}

