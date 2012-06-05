package code.model
import code.snippet.CustomEditable
import net.liftweb.http.SHtml

class AliasType
class Alias extends AliasType
object Public extends Alias
object Private extends Alias
object NoAlias extends AliasType

case class AccountName(display: String, aliasType: AliasType)

trait View {
	
  //the view settings 
  def usePrivateAliasIfOneExists: Boolean
  def usePublicAliasIfOneExists: Boolean
  
  //reading access
  def canSeeMoreInfo: Boolean
  def canSeeUrl: Boolean
  def canSeeImageUrl: Boolean
  def canSeeOpenCorporatesUrl: Boolean
  def canSeeComments: Boolean
  def canSeeOwnerComment: Boolean
  def canSeeTransactionLabel: Boolean
  def canSeeTransactionAmount: Boolean
  def canSeeTransactionType: Boolean
  def canSeeTransactionCurrency: Boolean
  def canSeeTransactionStartDate: Boolean
  def canSeeTransactionFinishDate: Boolean
  def canSeeTransactionBalance: Boolean
  
  //writing access
  def canEditOwnerComment: Boolean
  def canAddComments : Boolean
  // In the future we can add a method here to allow someone to show only transactions over a certain limit
  
  
  //e.g. "Anonymous", "Authorities", "Our Network", etc.
  def name: String

  def moderate(transaction: Transaction): FilteredTransaction = {
    val moreInfo = {
      if (canSeeMoreInfo) Some(transaction.otherParty.moreInfo)
      else None
    }

    val url = {
      if (canSeeUrl) Some(transaction.otherParty.url)
      else None
    }

    val imageUrl = {
      if (canSeeImageUrl) Some(transaction.otherParty.imageUrl)
      else None
    }

    val openCorporatesUrl = {
      if (canSeeOpenCorporatesUrl) Some(transaction.otherParty.openCorporatesUrl)
      else None
    }

    val comments = {
      if (canSeeComments) Some(transaction.comments)
      else None
    }

    val ownerComment = {
//      if(canEditOwnerComment) {
//        var comment =transaction.ownerComment.getOrElse("");
//        CustomEditable.editable(comment, SHtml.text(comment, comment = _), () => {transaction.ownerComment(comment)}, "Owner Comment")
//      }
//      else 
        if (canSeeOwnerComment) transaction.ownerComment
      else None
    }

    val transactionLabel = {
      if (canSeeTransactionLabel) Some(transaction.label)
      else None
    }

    val transactionAmount = {
      if (canSeeTransactionAmount) Some(transaction.amount)
      else None
    }
    val transactionType = {
      if (canSeeTransactionType) Some(transaction.transactionType)
      else None
    }
    val transactionCurrency = {
      if (canSeeTransactionCurrency) Some(transaction.currency)
      else None
    }
    val transactionStartDate = {
      if (canSeeTransactionStartDate) Some(transaction.startDate)
      else None
    }
    val transactionFinishDate = {
      if (canSeeTransactionFinishDate) Some(transaction.finishDate)
      else None
    }

    val accountHolderName: AccountName = {
      val realName = transaction.otherParty.accountHolderName
      if (usePublicAliasIfOneExists) {

        val publicAlias = transaction.otherParty.publicAlias

        if (publicAlias != "") AccountName(publicAlias, Public)
        else AccountName(realName, NoAlias)

      } else if (usePrivateAliasIfOneExists) {

        val privateAlias = transaction.otherParty.privateAlias

        if (privateAlias != "") AccountName(privateAlias, Private)
        else AccountName(realName, Private)
      } else {
        AccountName(realName, NoAlias)
      }
    }

    val accountDisplayName = Some(accountHolderName.display)
    val accountAliasType = accountHolderName.aliasType
    val transactionId = Some(transaction.id)
    val otherPartyAccountId = Some(transaction.otherParty.id)
    val transactionBalance = {
      if (canSeeTransactionBalance) transaction.balance.toString()
      else ""
    }
    val addCommentFunc= if(canAddComments) Some(transaction.addComment _) else None
    
    val filteredNonObpAccount = new FilteredNonObpAccount(otherPartyAccountId, accountDisplayName, accountAliasType, moreInfo, url, imageUrl, openCorporatesUrl);

    new FilteredTransaction(transactionId, Some(transaction.account), Some(filteredNonObpAccount), transactionType, transactionAmount,
      transactionCurrency, transactionLabel, ownerComment, comments, transactionStartDate, transactionFinishDate, transactionBalance, 
      addCommentFunc)
  }
}

trait ViewCompanion {
  //this method must be used to transforme the url into a view (if the user is allowed)
  def fromUrl(a: String): View
}

//An implementation that has the least amount of permissions possible
class BaseView extends View {
  def name = "Restricted"

  def usePrivateAliasIfOneExists = true
  def usePublicAliasIfOneExists = true

  def canSeeMoreInfo = false
  def canSeeUrl = false
  def canSeeImageUrl = false
  def canSeeOpenCorporatesUrl = false
  def canSeeComments = false
  def canSeeOwnerComment = false
  def canSeeTransactionLabel = false
  def canSeeTransactionAmount = false
  def canSeeTransactionType = false
  def canSeeTransactionCurrency = false
  def canSeeTransactionStartDate = false
  def canSeeTransactionFinishDate = false
  def canSeeTransactionBalance = false
  def canEditOwnerComment= false
  def canAddComments = false
}

class FullView extends View {
  def name = "Full"

  def usePrivateAliasIfOneExists = false
  def usePublicAliasIfOneExists = false

  def canSeeMoreInfo = true
  def canSeeUrl = true
  def canSeeImageUrl = true
  def canSeeOpenCorporatesUrl = true
  def canSeeComments = true
  def canSeeOwnerComment = true
  def canSeeTransactionLabel = true
  def canSeeTransactionAmount = true
  def canSeeTransactionType = true
  def canSeeTransactionCurrency = true
  def canSeeTransactionStartDate = true
  def canSeeTransactionFinishDate = true
  def canSeeTransactionBalance = true
  def canEditOwnerComment= true
  def canAddComments = true
}

object Team extends FullView {
  override def name = "Team"
  override def canEditOwnerComment= false
}
object Board extends FullView {
  override def name = "Board"
  override def canEditOwnerComment= false    
}
object Authorities extends FullView {
  override def name = "Authorities"
  override def canEditOwnerComment= false    
}

object Anonymous extends BaseView { 
  //the actual class extends the BaseView but in fact it does not matters be cause we don't care about the values 
  //of the canSeeMoreInfo, canSeeUrl,etc  attributes and we implement a specific moderate method
  
  override def name = "Anonymous"

  override def moderate(transaction: Transaction): FilteredTransaction = {
    
    val accountHolderName: AccountName = {
        val publicAlias = transaction.otherParty.publicAlias
        if (publicAlias != "") AccountName(publicAlias, Public)
        else AccountName(transaction.otherParty.accountHolderName, NoAlias)
    }
    val accountDisplayName = Some(accountHolderName.display)
    val accountAliasType = accountHolderName.aliasType
    val transactionId = Some(transaction.id)
    val otherPartyAccountId = Some(transaction.otherParty.id)

    def isPublicAlias = accountAliasType match {
      case Public => true
      case _ => false
    }
    val moreInfo = {
      if (isPublicAlias) None
      else Some(transaction.otherParty.moreInfo)
    }

    val url = {
      if (isPublicAlias) None
      else Some(transaction.otherParty.url)
    }

    val imageUrl = {
      if (isPublicAlias) None
      else Some(transaction.otherParty.imageUrl)
    }

    val openCorporatesUrl = {
      if (isPublicAlias) None
      else Some(transaction.otherParty.openCorporatesUrl)
    }

    val comments = None

    val ownerComment = {
      if (canSeeOwnerComment) transaction.ownerComment
      else None
    }

    val transactionLabel = Some(transaction.label)

    val transactionAmount = {
      Some(transaction.amount)
    }
    val transactionType = {
      if (canSeeTransactionType) Some(transaction.transactionType)
      else None
    }
    val transactionCurrency = Some(transaction.currency)

    val transactionStartDate = Some(transaction.startDate)

    val transactionFinishDate = Some(transaction.finishDate)
    val transactionBalance =  if (transaction.balance.toString().startsWith("-")) "-" else "+"
    
    val filteredNonObpAccount = new FilteredNonObpAccount(otherPartyAccountId, accountDisplayName, accountAliasType, moreInfo, url, imageUrl, openCorporatesUrl);

    new FilteredTransaction(transactionId, Some(transaction.account), Some(filteredNonObpAccount), transactionType, transactionAmount,
      transactionCurrency, transactionLabel, ownerComment, comments, transactionStartDate, transactionFinishDate, transactionBalance, None)

  }
  
}

  object OurNetwork extends BaseView {
  override def moderate(transaction: Transaction): FilteredTransaction = {
    val accountHolderName: AccountName = {
        val privateAlias = transaction.otherParty.privateAlias
        if (privateAlias != "") AccountName(privateAlias, Private)
        else AccountName(transaction.otherParty.accountHolderName, NoAlias)
    }
    val accountDisplayName = Some(accountHolderName.display)
    val accountAliasType = accountHolderName.aliasType
    val transactionId = Some(transaction.id)
    val otherPartyAccountId = Some(transaction.otherParty.id)

    val moreInfo = Some(transaction.otherParty.moreInfo)

    val url =  Some(transaction.otherParty.url)

    val imageUrl = Some(transaction.otherParty.imageUrl)

    val openCorporatesUrl = Some(transaction.otherParty.openCorporatesUrl)

    val comments = Some(transaction.comments)

    val ownerComment = transaction.ownerComment

    val transactionLabel = Some(transaction.label)

    val transactionAmount = Some(transaction.amount)

    val transactionType = None
    //transaction Type is not actually not shown
    
    val transactionCurrency = Some(transaction.currency)

    val transactionStartDate = Some(transaction.startDate)

    val transactionFinishDate = Some(transaction.finishDate)
    
    val transactionBalance = transaction.balance.toString()

    val filteredNonObpAccount = new FilteredNonObpAccount(otherPartyAccountId, accountDisplayName, accountAliasType, moreInfo, url, imageUrl, openCorporatesUrl);

    new FilteredTransaction(transactionId, Some(transaction.account), Some(filteredNonObpAccount), transactionType, transactionAmount,
      transactionCurrency, transactionLabel, ownerComment, comments, transactionStartDate, transactionFinishDate, transactionBalance, Some(transaction.addComment _))
  	}
  }

object OwnerView extends FullView {
  
}
