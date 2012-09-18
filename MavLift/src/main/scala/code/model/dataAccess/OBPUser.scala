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
    Ayoub Benali : ayoub AT tesobe DOT com

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
import net.liftweb.json.JsonDSL._
import net.liftweb.http.SHtml
import net.liftweb.http.S



/**
 * An O-R mapped "User" class that includes first name, last name, password
 */
class OBPUser extends MegaProtoUser[OBPUser] with User{
  def getSingleton = OBPUser // what's the "meta" server
  
  def emailAddress = email.get
  def userName = firstName.get
  
  def permittedViews(bankpermalink : String, bankAccount : String) : Set[View] = {
    MongoDBLocalStorage.getAccount(bankpermalink, bankAccount) match {
      case Full(account) => {
        var views : Set[View] = Set()
        if(OBPUser.hasOurNetworkPermission(account)) views = views + OurNetwork
        if(OBPUser.hasTeamPermission(account)) views = views + Team
        if(OBPUser.hasBoardPermission(account)) views = views + Board
        if(OBPUser.hasAuthoritiesPermission(account)) views = views + Authorities
        if(OBPUser.hasOwnerPermission(account)) views = views + Owner
        if(account.anonAccess.get) views = views + Anonymous
        views
      }
      case _ => Set() 
    }
  }
  def hasMangementAccess(bankpermalink : String, bankAccountPermalink : String)  = {
    MongoDBLocalStorage.getAccount(bankpermalink, bankAccountPermalink) match {
      case Full(account) => OBPUser.hasManagementPermission(account)
      case _ => false
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
  def hasManagementPermission(account: Account) : Boolean = {
    hasPermission(account, (p: Privilege) => p.mangementPermission.is)
  }
  
  def hasMoreThanAnonAccess(account: Account) : Boolean = {
      OBPUser.hasAuthoritiesPermission(account) ||
      OBPUser.hasBoardPermission(account) ||
      OBPUser.hasOurNetworkPermission(account) ||
      OBPUser.hasOwnerPermission(account) ||
      OBPUser.hasTeamPermission(account) ||
      OBPUser.hasManagementPermission(account)
  }
  
  def hasPermission(account: Account, permissionCheck: (Privilege) => Boolean) : Boolean = {
    currentUser match{
      case Full(u) => 
        HostedAccount.find(By(HostedAccount.accountID,account.id.toString)) match {
          case Full(hostedAccount) =>
                  Privilege.find(By(Privilege.account, hostedAccount), By(Privilege.user, u)) match{
                    case Full(p) => permissionCheck(p)
                    case _ => false
                  } 
          case _ => false 
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

class Privilege extends LongKeyedMapper[Privilege] with CreatedUpdated{
  def getSingleton = Privilege
  def primaryKeyField = id
  object id extends MappedLongIndex(this) 
  object user extends MappedLongForeignKey(this, OBPUser){
    var userError = false
    override def validSelectValues =
      Full(OBPUser.findMap(OrderBy(OBPUser.email, Ascending)){
          case u: User => Full(u.id.is -> u.email.is)
      })
    override def displayHtml = <span>User email</span>  
    override def asHtml = {
      val email = (for {
        u <- OBPUser.find(user.get)
      } yield u.email.get).getOrElse("User email not found")

      <span>{email}</span>
    }
    def userEmailCheck(user : Long) : List[FieldError]=    
      if(userError) List(FieldError(this, "No user with this email")) 
      else Nil
    override def validations = userEmailCheck _ :: super.validations
    override def _toForm =
    { 
      val initialValue = user.obj match {
        case Full(theUser) => theUser.email.is
        case _ => "" 
      }
      def saveTheUser(email : String) = 
       OBPUser.find(By(OBPUser.email, email)) match {
                case Full(theUser) => user(theUser)
                case _ => userError=true
              }           
      Full(SHtml.text(initialValue, saveTheUser(_)))
    }
  }
  
  object account extends MappedLongForeignKey(this, HostedAccount){
    
    override def displayHtml = <span>Account</span>  
    override def asHtml = {
      <span>{
        HostedAccount.find(account.get) match {
          case Full(account) => account.bank + " - " + account.name
          case _ => "account not found"
        }
      }</span>  
    }
    override def validSelectValues =
    Full(
      OBPUser.currentUser match {
        case Full(user) => Privilege.findMap(By(Privilege.user,user),
          By(Privilege.ownerPermission,true),
          OrderBy(Privilege.account, Ascending)){
            case privilege: Privilege => HostedAccount.find(privilege.account.is) match {
              case Full(hosted) => Full(hosted.id.is -> (hosted.bank + " - "+ hosted.name + " - " + hosted.number) )
            } 
          }
        case _ => List()
      }
    )
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
  object mangementPermission extends ourMappedBoolean(this) {
    override def displayName = "Management"
  }
}

object Privilege extends Privilege with LongKeyedMetaMapper[Privilege] with CRUDify[Long, Privilege]{
  override def calcPrefix = List("admin",_dbTableNameLC)
  override def fieldOrder = List(account, user,updatedAt, ownerPermission, mangementPermission,
    ourNetworkPermission, teamPermission, boardPermission) 
  override def displayName = "Privilege"
  override def showAllMenuLocParams = LocGroup("admin") :: Nil
  override def createMenuLocParams = LocGroup("admin") :: Nil
  override def fieldsForDisplay = super.fieldsForDisplay -- List(createdAt)
  override def fieldsForEditing = super.fieldsForEditing -- List(createdAt, updatedAt)
  def showAll = doCrudAll(_)
  override def findForList(start : Long, count : Int)= {
    OBPUser.currentUser match {
      case Full(user) => {
        def ownerPermissionTest(privilege : Privilege) : Boolean = 
          Privilege.find(By(Privilege.user, user), By(Privilege.account, privilege.account)) match {
            case Full(currentUserPrivilege) => currentUserPrivilege.ownerPermission
            case _ => false 
          }
        //we show only the privileges that concernes accounts were the current user  
        //has owner permissions on
        Privilege.findAll(OrderBy(Privilege.account, Ascending)).filter(ownerPermissionTest _) 
      }
      case _ => List()
    }
  }
}
class HostedAccount extends LongKeyedMapper[HostedAccount] {
    def getSingleton = HostedAccount
    def primaryKeyField = id
    
    object id extends MappedLongIndex(this)  
    object accountID extends MappedString(this, 255)

    def theAccount = Account.find(("_id", accountID.toString))

    def name : String= theAccount match {
      case Full(account) => account.name.get.toString()
      case _ => "" 
    }
    def bank : String = theAccount match {
      case Full(account) => account.bankName.get
      case _ => ""
    }
    def number : String = theAccount match {
      case Full(account) => account.number.get
      case _ => ""
    }   

  }
  object HostedAccount extends HostedAccount with LongKeyedMetaMapper[HostedAccount]{}
