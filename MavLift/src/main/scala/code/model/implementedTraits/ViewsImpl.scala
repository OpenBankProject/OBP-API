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

package code.model.implementedTraits

import code.model.traits._
import net.liftweb.common.{Box,Empty, Full}
import net.liftweb.json.JsonDSL._
import net.liftweb.json.JsonAST.JObject

object View {
  //transforme the url into a view 
  //TODO : load the view from the Data base
  def fromUrl(viewNameURL: String): Box[View] =
    viewNameURL match {
      case "authorities" => Full(Authorities)
      case "board" => Full(Board)
      case "our-network" => Full(OurNetwork)
      case "team" => Full(Team)
      case "owner" => Full(Owner)
      case "anonymous" => Full(Anonymous)
      case _ => Empty
    }

  def linksJson(views: Set[View], accountPermalink: String, bankPermalink: String): JObject = {
    val viewsJson = views.map(view => {
      ("rel" -> "account") ~
        ("href" -> { "/" + bankPermalink + "/account/" + accountPermalink + "/" + view.permalink }) ~
        ("method" -> "GET") ~
        ("title" -> "Get information about one account")
    })

    ("links" -> viewsJson)
  }
}
object Team extends FullView {
  override def id = 3
  override def name = "Team"
  override def permalink = "team"
  override def description = "A view for team members related to the account. E.g. for a company bank account -> employees/contractors"
  override def canEditOwnerComment= false
}
object Board extends FullView {
  override def id = 4
  override def name = "Board"
  override def permalink = "board"
  override def description = "A view for board members of a company to view that company's account data."
  override def canEditOwnerComment= false    
}
object Authorities extends FullView {
  override def id = 5
  override def name = "Authorities"
  override def permalink = "authorities"
  override def description = "A view for authorities such as tax officials to view an account's data"
  override def canEditOwnerComment= false    
}

object Anonymous extends BaseView { 
  //the actual class extends the BaseView but in fact it does not matters be cause we don't care about the values 
  //of the canSeeMoreInfo, canSeeUrl,etc  attributes and we implement a specific moderate method
  
    /**
   * Current rules: 
   * 
   * If anonymous, and a public alias exists : Show the public alias
   * If anonymous, and no public alias exists : Show the real account holder
   * If our network, and a private alias exists : Show the private alias
   * If our network, and no private alias exists : Show the real account holder
   */
  override def id = 6  
  override def name = "Anonymous"
  override def permalink = "anonymous" 
  override def description = "A view of the account accessible by anyone."
    
  //Bank account fields
  override def canSeeBankAccountOwners = true
  override def canSeeBankAccountType = true
  override def canSeeBankAccountBalancePositiveOrNegative = true
  override def canSeeBankAccountCurrency = true
  override def canSeeBankAccountLabel = true
  override def canSeeBankAccountNationalIdentifier = true
  override def canSeeBankAccountSwift_bic = true
  override def canSeeBankAccountIban = true
  override def canSeeBankAccountNumber = true
  override def canSeeBankAccountName = true
    
  override def moderate(transaction: Transaction): ModeratedTransaction = {
    
    val transactionId = transaction.id //toString().startsWith("-")) "-" else "+"
    val accountBalance = "" //not used when displaying transactions, but we might eventually need it. if so, we need a ref to
      //the bank account so we could do something like if(canSeeBankAccountBalance) bankAccount.balance else if
      // canSeeBankAccountBalancePositiveOrNegative {show + or -} else ""
    val thisBankAccount = Some(new ModeratedBankAccount(transaction.thisAccount.id, 
      Some(transaction.thisAccount.owners), Some(transaction.thisAccount.accountType), 
      accountBalance, Some(transaction.thisAccount.currency), 
      Some(transaction.thisAccount.label),None, None, None, Some(transaction.thisAccount.number),
      Some(transaction.thisAccount.bankName)))
    val otherBankAccount = {
      val otherAccountLabel = {
        val publicAlias = transaction.otherAccount.metadata.publicAlias
        if(publicAlias.isEmpty)
          AccountName(transaction.otherAccount.label, NoAlias)
        else
          AccountName(publicAlias, Public)
      }
      val otherAccountMetadata = {
        def isPublicAlias = otherAccountLabel.aliasType match {
          case Public => true
          case _ => false
        }
        val moreInfo = if (isPublicAlias) None else Some(transaction.otherAccount.metadata.moreInfo)
        val url = if (isPublicAlias) None else Some(transaction.otherAccount.metadata.url)
        val imageUrl = if (isPublicAlias) None else Some(transaction.otherAccount.metadata.imageUrl)
        val openCorporatesUrl = if (isPublicAlias) None else Some(transaction.otherAccount.metadata.openCorporatesUrl)

        Some(new ModeratedOtherBankAccountMetadata(moreInfo, url, imageUrl, openCorporatesUrl))
      } 

      Some(new ModeratedOtherBankAccount(transaction.otherAccount.id,otherAccountLabel,None,None,
          None, None, None, otherAccountMetadata))
    }
    val transactionMetadata = Some(new ModeratedTransactionMetadata(
      transaction.metadata.ownerComment,Some(transaction.metadata.comments.filter(comment => comment.viewId==id)),
      None,Some(transaction.metadata.addComment _)))
    val transactionType = Some(transaction.transactionType)
    val transactionAmount = Some(transaction.amount)
    val transactionCurrency = Some(transaction.currency)
    val transactionLabel = Some(transaction.label)
    val transactionStartDate = Some(transaction.startDate)
    val transactionFinishDate = Some(transaction.finishDate)
    val transactionBalance =  if (transaction.balance.toString().startsWith("-")) "-" else "+"
    
    new ModeratedTransaction(transactionId, thisBankAccount, otherBankAccount, transactionMetadata,
     transactionType, transactionAmount, transactionCurrency, transactionLabel, transactionStartDate,
      transactionFinishDate, transactionBalance)

  }
  
}

  object OurNetwork extends BaseView 
  {
    override def id = 7
    override def name = "Our Network"
    override def permalink ="our-network"
    override def description = "A view for people related to the account in some way. E.g. for a company account this could include investors" +
    	" or current/potential clients"
    override def moderate(transaction: Transaction): ModeratedTransaction = {
    val transactionId = transaction.id
    val accountBalance = "" //not used when displaying transactions, but we might eventually need it. if so, we need a ref to
      //the bank account so we could do something like if(canSeeBankAccountBalance) bankAccount.balance else if
      // canSeeBankAccountBalancePositiveOrNegative {show + or -} else ""
    val thisBankAccount = Some(new ModeratedBankAccount(transaction.thisAccount.id, None, None, 
      accountBalance, Some(transaction.thisAccount.currency), 
      Some(transaction.thisAccount.label),None, None, None, Some(transaction.thisAccount.number),
      Some(transaction.thisAccount.bankName)))
    val otherBankAccount = {
      val otherAccountLabel = {
        val privateAlias = transaction.otherAccount.metadata.privateAlias
        if(privateAlias.isEmpty)
          AccountName(transaction.otherAccount.label, NoAlias)
        else
          AccountName(privateAlias, Private)
      }
      val otherAccountMetadata = 
        Some(new ModeratedOtherBankAccountMetadata(Some(transaction.otherAccount.metadata.moreInfo), 
          Some(transaction.otherAccount.metadata.url), Some(transaction.otherAccount.metadata.imageUrl), 
          Some(transaction.otherAccount.metadata.openCorporatesUrl)))

      Some(new ModeratedOtherBankAccount(transaction.otherAccount.id,otherAccountLabel,None,None,None,
          None, None, otherAccountMetadata))
    }      
    val transactionMetadata = Some(new ModeratedTransactionMetadata(transaction.metadata.ownerComment,
      Some(transaction.metadata.comments.filter(comment => comment.viewId==id)),None,Some(transaction.metadata.addComment _)))

    val transactionType = Some(transaction.transactionType)
    val transactionAmount = Some(transaction.amount)
    val transactionCurrency = Some(transaction.currency)
    val transactionLabel = Some(transaction.label)
    val transactionStartDate = Some(transaction.startDate)
    val transactionFinishDate = Some(transaction.finishDate)
    val transactionBalance =  transaction.balance.toString()
    
    new ModeratedTransaction(transactionId, thisBankAccount, otherBankAccount, transactionMetadata,
     transactionType, transactionAmount, transactionCurrency, transactionLabel, transactionStartDate,
      transactionFinishDate, transactionBalance)
  	}
  }

object Owner extends FullView {
  override def id = 8  
  override def name="Owner"
  override def permalink = "owner"
}