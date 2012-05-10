package code.model

package code.model

trait View {
  
  //e.g. "Anonymous", "Authorities", "Our Network", etc.
  def name : String

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

    class AliasType
    object NoAlias extends AliasType
    class Alias extends AliasType
    object Public extends Alias
    object Private extends Alias

    case class AccountName(display: String, aliasType: AliasType)

    val accountDisplayName = accountHolderName.display
    val accountAliasType = accountHolderName.aliasType
    val id = Some(transaction.id)
    
    new FilteredTransaction()
  }
  
  def usePrivateAliasIfOneExists : Boolean
  def usePublicAliasIfOneExists : Boolean
  def canSeeMoreInfo : Boolean
  def canSeeUrl : Boolean
  def canSeeImageUrl : Boolean
  def canSeeOpenCorporatesUrl : Boolean
  def canSeeComments : Boolean
  def canSeeOwnerComment : Boolean
  def canSeeTransactionLabel : Boolean
  def canSeeTransactionAmount : Boolean
  
  // In the future we can add a method here to allow someone to show only transactions over a certain limit
}

trait ViewCompanion {
  def fromUrl(a : String) : View
}

//An implementation that has the least amount of permissions possible
class BaseView extends View {
  def name = "Restricted"
  
  def moderate(transaction : Transaction) : Transaction
  
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
}

class AnonymousView extends BaseView {
  
}