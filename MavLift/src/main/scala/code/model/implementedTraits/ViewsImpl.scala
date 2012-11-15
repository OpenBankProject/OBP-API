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
    Benali Ayoub : ayoub AT tesobe DOT com

 */

package code.model.implementedTraits

import code.model.traits._
import net.liftweb.common.{Box,Empty, Full}
object View 
{
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
}
object Team extends FullView {
  override def name = "Team"
  override def permalink = "team"
  override def description = "A view for team members related to the account. E.g. for a company bank account -> employees/contractors"
  override def canEditOwnerComment= false
}
object Board extends FullView {
  override def name = "Board"
  override def permalink = "board"
  override def description = "A view for board members of a company to view that company's account data."
  override def canEditOwnerComment= false    
}
object Authorities extends FullView {
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
  
  override def name = "Anonymous"
  override def permalink = "anonymous" 
  override def description = "A view of the account accessible by anyone."
  override def moderate(transaction: Transaction): ModeratedTransaction = {
    
    val transactionId = transaction.id //toString().startsWith("-")) "-" else "+"
    val thisBankAccount = Some(new ModeratedBankAccount(transaction.thisAccount.id, 
      Some(transaction.thisAccount.owners), Some(transaction.thisAccount.accountType), 
      if(transaction.thisAccount.toString().startsWith("-")) "-" else "+", Some(transaction.thisAccount.currency), 
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
    val transactionMetadata = Some(new ModeratedTransactionMetadata(transaction.metadata.ownerComment,None,None,None))
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
    override def name = "Our Network"
    override def permalink ="our-network"
    override def description = "A view for people related to the account in some way. E.g. for a company account this could include investors" +
    	" or current/potential clients"
    override def moderate(transaction: Transaction): ModeratedTransaction = {
    val transactionId = transaction.id
    val thisBankAccount = Some(new ModeratedBankAccount(transaction.thisAccount.id, None, None, 
      transaction.thisAccount.toString(), Some(transaction.thisAccount.currency), 
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
      Some(transaction.metadata.comments),None,Some(transaction.metadata.addComment _)))

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
  override def name="Owner"
  override def permalink = "owner"
}