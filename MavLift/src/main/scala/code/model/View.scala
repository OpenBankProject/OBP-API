package code.model

package code.model

class AliasType
object NoAlias extends AliasType
class Alias extends AliasType
object Public extends Alias
object Private extends Alias

case class AccountName(display: String, aliasType: AliasType)

trait View {

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
      if (canSeeOwnerComment) Some(transaction.ownerComment)
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

    val filteredNonObpAccount = new FilteredNonObpAccount(otherPartyAccountId, accountDisplayName, accountAliasType, moreInfo, url, imageUrl, openCorporatesUrl);

    new FilteredTransaction(transactionId, Some(transaction.account), Some(filteredNonObpAccount), transactionType, transactionAmount,
      transactionCurrency, transactionLabel, ownerComment, comments, transactionStartDate, transactionFinishDate, transaction.addComment _)
  }

  def usePrivateAliasIfOneExists: Boolean
  def usePublicAliasIfOneExists: Boolean
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
  // In the future we can add a method here to allow someone to show only transactions over a certain limit
}

trait ViewCompanion {
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
}

object Team extends FullView {

  override def name = "Team"
}
object Board extends FullView {

  override def name = "Board"
}
object Authorities extends FullView {

  override def name = "Authorities"
}
object Anonymous extends BaseView { 
  //the actual class extends the BaseView but in fact it does not matters be cause we don't care about the values of the canSeeMoreInfo, canSeeUrl,etc  attributes
  
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
      if (isPublicAlias) Some(transaction.otherParty.moreInfo)
      else None
    }

    val url = {
      if (isPublicAlias) Some(transaction.otherParty.url)
      else None
    }

    val imageUrl = {
      if (isPublicAlias) Some(transaction.otherParty.imageUrl)
      else None
    }

    val openCorporatesUrl = {
      if (isPublicAlias) Some(transaction.otherParty.openCorporatesUrl)
      else None
    }

    val comments = None

    val ownerComment = {
      if (canSeeOwnerComment) Some(transaction.ownerComment)
      else None
    }

    val transactionLabel = Some(transaction.label)

    val transactionAmount = {
      if (canSeeTransactionAmount) Some(transaction.amount)
      else None
    }
    val transactionType = {
      if (canSeeTransactionType) Some(transaction.transactionType)
      else None
    }
    val transactionCurrency = Some(transaction.currency)

    val transactionStartDate = Some(transaction.startDate)

    val transactionFinishDate = Some(transaction.finishDate)

    val filteredNonObpAccount = new FilteredNonObpAccount(otherPartyAccountId, accountDisplayName, accountAliasType, moreInfo, url, imageUrl, openCorporatesUrl);

    new FilteredTransaction(transactionId, Some(transaction.account), Some(filteredNonObpAccount), transactionType, transactionAmount,
      transactionCurrency, transactionLabel, ownerComment, comments, transactionStartDate, transactionFinishDate, transaction.addComment _)

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

    val ownerComment = Some(transaction.ownerComment)

    val transactionLabel = Some(transaction.label)

    val transactionAmount = Some(transaction.amount)

    val transactionType = None
    //transaction Type is not actually not shown
    
    val transactionCurrency = Some(transaction.currency)

    val transactionStartDate = Some(transaction.startDate)

    val transactionFinishDate = Some(transaction.finishDate)

    val filteredNonObpAccount = new FilteredNonObpAccount(otherPartyAccountId, accountDisplayName, accountAliasType, moreInfo, url, imageUrl, openCorporatesUrl);

    new FilteredTransaction(transactionId, Some(transaction.account), Some(filteredNonObpAccount), transactionType, transactionAmount,
      transactionCurrency, transactionLabel, ownerComment, comments, transactionStartDate, transactionFinishDate, transaction.addComment _)
  	}
  }
  
}


