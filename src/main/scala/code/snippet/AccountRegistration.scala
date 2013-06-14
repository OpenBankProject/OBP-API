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

package code.snippet

import code.model.dataAccess.{OBPUser,HostedBank}
import net.liftweb.common.{Full,Box,Empty,Failure}
import scala.xml.NodeSeq
import net.liftweb.util.CssSel
import net.liftweb.util.Helpers._
import net.liftweb.util.Props
import net.liftweb.http.{S,SHtml,RequestVar}
import code.pgp.PgpEncryption
import net.liftweb.http.js.JsCmd
import net.liftweb.http.js.JsCmds.Noop
import scala.xml.Text
import net.liftweb.util.Mailer
import Mailer._
import net.liftweb.common.Loggable

class AccountRegistration extends Loggable {

	private object bankName 	extends RequestVar("")
	private object accountNumber 	extends RequestVar("")
	private object accountPIN    	extends RequestVar("")
	private object publicAccess  	extends RequestVar(false)
	private object accountHolder 	extends RequestVar("")
	private object accountKind   	extends RequestVar("")
	private object accountLabel  	extends RequestVar("")
	private object accountName 		extends RequestVar("")

	def renderForm = {
		OBPUser.currentUser match {
			case Full(user) => {
				//load the suported banks list from the database
				val banks = "Choose a Bank" :: HostedBank.findAll.map(_.name.get)
				val options = Map("yes" -> true,"no" -> false)
				val optionsSwaped	= options.map{_.swap}

				//get a boolean value from a 'yes' or 'no' string
				def getBooleanValue(text : Box[String]) =
					text match {
						case Full(value) => tryo{
							options(value)
							} match {
								case Full(boolean) => boolean
								case _ => false
							}
						case _ => false
					}

				def check() =
					//check that all the parameters are here
					if( !accountNumber.is.isEmpty && !accountPIN.is.isEmpty 	&
							!accountName.is.isEmpty	&& !accountHolder.is.isEmpty &
							!accountKind.is.isEmpty && ! accountLabel.is.isEmpty &
							bankName.is != "Choose a Bank" )
					{
						var reponceText = "Submission Failed. Please try later."
						var reponceId 	= "submissionFailed"
						val fileName = bankName.is+"-"+accountNumber.is+"-"+user.emailAddress
						for{
								//load the public key and output directory path
								publicKey 				<- Props.get("publicKeyPath")
								outputFilesDirectory 	<- Props.get("outputFilesDirectory")
						}yield tryo{
								//store the Pin code into an encrypted file
								PgpEncryption.encryptToFile(
									accountPIN.is,
									publicKey,
									outputFilesDirectory+"/"+fileName+".pin")
							} match {
								case Full(encryptedPin) => {
									//send an email to the administration so we can setup the account
									//prepare the data to be sent into the email body
									val emailBody =
											"The following account needs to activated : "	+"\n"+
											"bank name : " 		+ bankName.is 				+"\n"+
											"account number : " + accountNumber.is 			+"\n"+
											"account name : "	+ accountName.is 			+"\n"+
											"account holder : "	+ accountHolder.is 			+"\n"+
											"account label : " 	+ accountLabel.is 			+"\n"+
											"account kind : " 	+ accountKind.is 			+"\n"+
											"public view : "	+ publicAccess.is.toString	+"\n"+
											"user email : "		+ user.emailAddress			+"\n"+
											"The PIN code is in this file : "+ outputFilesDirectory+"/"+fileName+".pin"+"\n"
									val accountNotificationemails = Props.get("accountNotificationemails") match {
										case Full(emails) => emails.split(",",0)
										case _ => Array()
									}

									tryo {
										accountNotificationemails.foreach ( email =>
											Mailer.sendMail(From("noreply@openbankproject.com"),Subject("[SoFi]Bank account Activation"),
											         To(email),PlainMailBodyType(emailBody))
										)
									} match {
								    case Failure(message, exception, chain) =>
								      logger.error("problem while sending email: " + message)
								    case _ =>
								      logger.info("successfully sent email")
								  }
									reponceText = "Submission succeded. You will receive an email notification once the bank account will be setup by the administrator."
									reponceId 	= "submissionSuccess"
								}
								case _ => //nothing to do the text and Id are allready set
							}
						//set the fields to their default values
						bankName.set("Choose a Bank")
						accountNumber.set("")
						accountPIN.set("")
						publicAccess.set(false)
						accountHolder.set("")
						accountKind.set("")
						accountLabel.set("")
						accountName.set("")
						//return a message
						S.notice("submissionMessage", SHtml.span(Text(reponceText), Noop,("id",reponceId)))
					}
					else
					{
						if(bankName.is == "Choose a Bank")
							S.error("bankError","Bank not selected ! ")
						if(accountNumber.is.isEmpty)
							S.error("accountNumberError","Account Number Empty ! ")
						if(accountPIN.is.isEmpty)
							S.error("accountPINError","Account PIN Empty ! ")
						if(accountHolder.is.isEmpty)
							S.error("accountHolderError","Account Holder Empty ! ")
						if(accountKind.is.isEmpty)
							S.error("accountKindError","Account Kind Empty ! ")
						if(accountLabel.is.isEmpty)
							S.error("accountLabelError","Account label Empty ! ")
						if(accountName.is.isEmpty)
							S.error("accountNameError","Account Name Empty ! ")
					}

				//now we create the form fields
				"#bankListCol" 		#> SHtml.selectElem(banks,Full(bankName.is),("id","bankList"))((v : String) => bankName.set(v)) &
				"#accountNumberCol" #> SHtml.textElem(accountNumber,("id","accountNumber"),("placeholder","123456")) &
				"#accountPINCol" 	#> SHtml.passwordElem(accountPIN,("id","accountPIN"),("placeholder","*******")) &
				"#publicViewCol" 	#> SHtml.radioElem(options.keys.toList,Full(optionsSwaped(publicAccess.is)))((v : Box[String]) => publicAccess.set(getBooleanValue(v))).toForm &
				"#accountHolderCol" #> SHtml.textElem(accountHolder,("id","accountHolder"),("placeholder","John Doe")) &
				"#accountKindCol"	#> SHtml.textElem(accountKind,("id","accountKind"),("placeholder","saving, current, ...")) &
				"#accountLabelCol" 	#> SHtml.textElem(accountLabel,("id","accountLabel"),("placeholder","John main buisness account,...")) &
				"#accountNameCol" 	#> SHtml.textElem(accountName,("id","accountName"),("placeholder","mycompany, personal, etc")) &
				"type=submit" 		#> SHtml.onSubmitUnit(check) &
				"#loginMsg" 		#> NodeSeq.Empty
			}
			case _ =>
				//if the user is not logged in, we hide the form and ask him to login
				"#submitAccount" 	#> NodeSeq.Empty &
				"#loginMsg * " 		#> {
										Text("You must ") ++
										SHtml.link(OBPUser.loginPageURL,() => {},Text("login"),("title","signup/login")) ++
										Text(" before you can connect your bank account! ")
									}
		}
	}
}
