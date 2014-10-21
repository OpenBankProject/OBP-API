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
package code.model.dataAccess

import net.liftweb.mapper._
import net.liftweb.util._
import net.liftweb.common._
import scala.xml.NodeSeq
import net.liftweb.http.{SessionVar,Templates}
import code.model.User
import net.liftweb.json.JsonDSL._
import net.liftweb.http.SHtml
import net.liftweb.http.S
import com.mongodb.DBObject
import net.liftweb.http.js.JsCmds.FocusOnLoad


/**
 * An O-R mapped "User" class that includes first name, last name, password
 */
class OBPUser extends MegaProtoUser[OBPUser] with Logger{
  def getSingleton = OBPUser // what's the "meta" server

  object user extends MappedLongForeignKey(this, APIUser)

  override def save(): Boolean = {
    if(! (user defined_?)){
      info("user reference is null. We will create an API User")
      val displayName = {
        if(firstName.get.isEmpty) {
          lastName.get
        } else if(lastName.get.isEmpty) {
          firstName.get
        } else {
          firstName.get + " " + lastName.get
        }
      }
      val apiUser = APIUser.create
      .name_(displayName)
      .email(email)
      .provider_(Props.get("hostname",""))
      .providerId(email)
      .saveMe
      user(apiUser)
    }
    else {
      info("user reference is no null. Tying to update the API User")
      user.obj.map{ u =>{
          info("API User found ")
          u.name_(lastName.get + " " + firstName.get)
          .email(email)
          .save
        }
      }
    }
    super.save()
  }

  override def delete_!(): Boolean = {
    user.obj.map{_.delete_!}
    super.delete_!
  }
}

/**
 * The singleton that has methods for accessing the database
 */
object OBPUser extends OBPUser with MetaMegaProtoUser[OBPUser]{
import net.liftweb.util.Helpers._


  override def dbTableName = "users" // define the DB table name

  override def screenWrap = Full(<lift:surround with="default" at="content"><lift:bind /></lift:surround>)
  // define the order fields will appear in forms and output
  override def fieldOrder = List(id, firstName, lastName, email, password)
  override def signupFields = List(firstName, lastName, email, password)

  // comment this line out to require email validations
  override def skipEmailValidation = true

  override def loginXhtml = {
    import net.liftweb.http.js.JsCmds.Noop
    val loginXml = Templates(List("templates-hidden","_login")).map({
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

  /**
   * Set this to redirect to a certain page after a failed login
   */
  object failedLoginRedirect extends SessionVar[Box[String]](Empty) {
    override lazy val __nameSalt = Helpers.nextFuncName
  }

  override def signupXhtml (user:OBPUser) =  {
    <form id="signupForm" method="post" action={S.uri}>
      <table>
        <tr>
          <td colspan="2">{ S.?("sign.up") }</td>
        </tr>
          {localForm(user, false, signupFields)}
        <tr>
          <td>&nbsp;</td>
          <td><user:submit/></td>
        </tr>
      </table>
    </form>
  }

  //overridden to allow a redirection if login fails
  override def login = {
    if (S.post_?) {
      S.param("username").
      flatMap(username => findUserByUserName(username)) match {
        case Full(user) if user.validated_? &&
          user.testPassword(S.param("password")) => {
            val preLoginState = capturePreLoginState()
            info("login redir: " + loginRedirect.is)
            val redir = loginRedirect.is match {
              case Full(url) =>
                loginRedirect(Empty)
              url
              case _ =>
                homePage
            }

            logUserIn(user, () => {
              S.notice(S.??("logged.in"))

              preLoginState()

              S.redirectTo(redir)
            })
          }

        case _ => {
          info("failed: " + failedLoginRedirect.get)
          failedLoginRedirect.get.foreach(S.redirectTo(_))
        }
      }
    }

    bind("user", loginXhtml,
         "email" -> (FocusOnLoad(<input type="text" name="username"/>)),
         "password" -> (<input type="password" name="password"/>),
         "submit" -> loginSubmitButton(S.??("log.in")))
  }

  //overridden to allow redirect to loginRedirect after signup. This is mostly to allow
  // loginFirst menu items to work if the user doesn't have an account. Without this,
  // if a user tries to access a logged-in only page, and then signs up, they don't get redirected
  // back to the proper page.
  override def signup = {
    val theUser: TheUserType = mutateUserOnSignup(createNewUserInstance())
    val theName = signUpPath.mkString("")

    //save the intented login redirect here, as it gets wiped (along with the session) on login
    val loginRedirectSave = loginRedirect.is

    def testSignup() {
      validateSignup(theUser) match {
        case Nil =>
          actionsAfterSignup(theUser, () => {
            //here we check loginRedirectSave (different from implementation in super class)
            val redir = loginRedirectSave match {
              case Full(url) =>
                loginRedirect(Empty)
                url
              case _ =>
                homePage
            }
            S.redirectTo(redir)
          })

        case xs => S.error(xs) ; signupFunc(Full(innerSignup _))
      }
    }

    def innerSignup = bind("user",
      signupXhtml(theUser),
      "submit" -> signupSubmitButton(S.?("sign.up"), testSignup _))

    innerSignup
  }


}

//TODO: remove this
@deprecated
class HostedAccount extends LongKeyedMapper[HostedAccount] with OneToMany[Long, HostedAccount]{
  def getSingleton = HostedAccount
  def primaryKeyField = id

  object id extends MappedLongIndex(this)
  //the object id of the mongoDB Account
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

//TODO: remove this
@deprecated
object HostedAccount extends HostedAccount with LongKeyedMetaMapper[HostedAccount]{
  override def dbIndexes = UniqueIndex(accountID):: super.dbIndexes
}