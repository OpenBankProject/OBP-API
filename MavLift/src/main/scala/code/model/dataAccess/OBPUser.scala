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
import net.liftweb.util.Helpers._
import org.bson.types.ObjectId
import com.mongodb.DBObject
import net.liftweb.json.JsonAST.JObject


/**
 * An O-R mapped "User" class that includes first name, last name, password
 */
class OBPUser extends MegaProtoUser[OBPUser] with User{
  def getSingleton = OBPUser // what's the "meta" server
  def id_ = id.is.toString  
  def emailAddress = email.get
  def theFistName : String = firstName.get
  def theLastName : String = lastName.get

  def permittedViews(account: BankAccount): Set[View] = {
    var views: Set[View] = Set()
    if (OBPUser.hasOurNetworkPermission(account)) views = views + OurNetwork
    if (OBPUser.hasTeamPermission(account)) views = views + Team
    if (OBPUser.hasBoardPermission(account)) views = views + Board
    if (OBPUser.hasAuthoritiesPermission(account)) views = views + Authorities
    if (OBPUser.hasOwnerPermission(account)) views = views + Owner
    if (account.allowAnnoymousAccess) views = views + Anonymous
    views
  }
  
  def hasMangementAccess(bankAccount: BankAccount)  = {
    OBPUser.hasManagementPermission(bankAccount)
  }
  
  def accountsWithMoreThanAnonAccess : Set[BankAccount] = {
    
    val hostedAccountTable = HostedAccount._dbTableNameLC
    val privilegeTable = Privilege._dbTableNameLC
    val userTable = OBPUser._dbTableNameLC
    
    val hostedId = hostedAccountTable + "." + HostedAccount.id.dbColumnName
    val hostedAccId = hostedAccountTable + "." + HostedAccount.accountID.dbColumnName
    val privilegeAccId = privilegeTable + "." + Privilege.account.dbColumnName
    val privilegeUserId = privilegeTable + "." + Privilege.user.dbColumnName
    val userId = this.id.get
    
    val ourNetworkPrivilege = privilegeTable + "." + Privilege.ourNetworkPermission.dbColumnName
    val teamPrivilege = privilegeTable + "." + Privilege.teamPermission.dbColumnName
    val boardPrivilege = privilegeTable + "." + Privilege.boardPermission.dbColumnName
    val authoritiesPrivilege = privilegeTable + "." + Privilege.authoritiesPermission.dbColumnName
    val ownerPrivilege = privilegeTable + "." + Privilege.ownerPermission.dbColumnName
    
    val query = "SELECT " + hostedId + ", " + hostedAccId + 
    			" FROM " + hostedAccountTable + ", " + privilegeTable + ", " + userTable +
    			" WHERE " + "( " + hostedId + " = " + privilegeAccId + ")" +
    				" AND " + "( " + privilegeUserId + " = " + userId + ")" +
    				" AND " + "( " + ourNetworkPrivilege + " = true" +
    					" OR " + teamPrivilege + " = true" +
    					" OR " + boardPrivilege + " = true" +
    					" OR " + authoritiesPrivilege + " = true" +
    					" OR " + ownerPrivilege + " = true)"
    
    val moreThanAnon = HostedAccount.findAllByInsecureSql(query, IHaveValidatedThisSQL("everett", "nov. 15 2012"))
    val mongoIds = moreThanAnon.map(hAcc => new ObjectId(hAcc.accountID.get))
    
    Account.findAll(mongoIds).map(Account.toBankAccount).toSet
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
  
  override def loginXhtml = {
    import net.liftweb.http.TemplateFinder
    import net.liftweb.http.js.JsCmds.Noop
    val loginXml = TemplateFinder.findAnyTemplate(List("templates-hidden","_login")).map({
        "form [action]" #> {S.uri} &
        "#loginText * " #> {S.??("log.in")} &
        "#emailAddressText * " #> {S.??("email.address")} &
        "#passwordText * " #> {S.??("password")} &  
        "#recoverPasswordLink * " #> {
          "a [href]" #> {lostPasswordPath.mkString("/", "/", "")} &
          "a *" #> {S.??("recover.password")}
        } &  
        "#SignUpLink * " #> {
          "a [href]" #> {OBPUser.signUpPath.foldLeft("")(_ + "/" + _)} &
          "a *" #> {S.??("sign.up")}
        }
      })
      SHtml.span(loginXml getOrElse NodeSeq.Empty,Noop)
  } 

  //Set the login referer
  override def login = {
    for(r <- S.referer if loginReferer.is.equals("/")) loginReferer.set(r)
    super.login
  }
  
  def hasOurNetworkPermission(account: BankAccount) : Boolean = {
    hasPermission(account, (p: Privilege) => p.ourNetworkPermission.is)
  }
  
  def hasTeamPermission(account: BankAccount) : Boolean = {
    hasPermission(account, (p: Privilege) => p.teamPermission.is)
  }
  
  def hasBoardPermission(account: BankAccount) : Boolean = {
    hasPermission(account, (p: Privilege) => p.boardPermission.is)
  }
  
  def hasAuthoritiesPermission(account: BankAccount) : Boolean = {
    hasPermission(account, (p: Privilege) => p.authoritiesPermission.is)
  }
  
  def hasOwnerPermission(account: BankAccount) : Boolean = {
    hasPermission(account, (p: Privilege) => p.ownerPermission.is)
  }
  def hasManagementPermission(account: BankAccount) : Boolean = {
    hasPermission(account, (p: Privilege) => p.mangementPermission.is)
  }
  
  def hasMoreThanAnonAccess(account: BankAccount) : Boolean = {
      OBPUser.hasAuthoritiesPermission(account) ||
      OBPUser.hasBoardPermission(account) ||
      OBPUser.hasOurNetworkPermission(account) ||
      OBPUser.hasOwnerPermission(account) ||
      OBPUser.hasTeamPermission(account) ||
      OBPUser.hasManagementPermission(account)
  }
  
  def hasPermission(bankAccount: BankAccount, permissionCheck: (Privilege) => Boolean) : Boolean = {
    currentUser match{
      case Full(u) => 
        HostedAccount.find(By(HostedAccount.accountID, bankAccount.id)) match {
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
              case _ => Empty
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
        //TODO: This is inefficient (it loads all privileges)
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
      case Full(account) => account.bankName
      case _ => ""
    }
    def number : String = theAccount match {
      case Full(account) => account.number.get
      case _ => ""
    }   

  }
  object HostedAccount extends HostedAccount with LongKeyedMetaMapper[HostedAccount]{}
