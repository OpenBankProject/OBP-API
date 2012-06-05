package code.model.implementedTraits

import code.model.traits.{Transaction,FullView,BaseView,ModeratedTransaction,ModeratedMetaData,AccountName,Alias,NoAlias,AliasType,Public,Private}

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
  
    /**
   * Current rules: 
   * 
   * If anonymous, and a public alias exists : Show the public alias
   * If anonymous, and no public alias exists : Show the real account holder
   * If our network, and a private alias exists : Show the private alias
   * If our network, and no private alias exists : Show the real account holder
   */
  
  override def name = "Anonymous"
   
  override def moderate(transaction: Transaction): ModeratedTransaction = {
    
    val accountHolderName: AccountName = {
        val publicAlias = transaction.metaData.publicAlias
        if (publicAlias != "") AccountName(publicAlias, Public)
        else AccountName(transaction.metaData.accountHolderName, NoAlias)
    }
    val accountDisplayName = Some(accountHolderName.display)
    val accountAliasType = accountHolderName.aliasType
    val transactionId = Some(transaction.id)
    val otherPartyAccountId = Some(transaction.metaData.id)

    def isPublicAlias = accountAliasType match {
      case Public => true
      case _ => false
    }
    val moreInfo = {
      if (isPublicAlias) None
      else Some(transaction.metaData.moreInfo)
    }

    val url = {
      if (isPublicAlias) None
      else Some(transaction.metaData.url)
    }

    val imageUrl = {
      if (isPublicAlias) None
      else Some(transaction.metaData.imageUrl)
    }

    val openCorporatesUrl = {
      if (isPublicAlias) None
      else Some(transaction.metaData.openCorporatesUrl)
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
    
    val filteredNonObpAccount = new ModeratedMetaData(otherPartyAccountId, accountDisplayName, accountAliasType, moreInfo, url, imageUrl, openCorporatesUrl);

    new ModeratedTransaction(transactionId, Some(transaction.account), Some(filteredNonObpAccount), transactionType, transactionAmount,
      transactionCurrency, transactionLabel, ownerComment, comments, transactionStartDate, transactionFinishDate, transactionBalance, None)

  }
  
}

  object OurNetwork extends BaseView {
  override def moderate(transaction: Transaction): ModeratedTransaction = {
    val accountHolderName: AccountName = {
        val privateAlias = transaction.metaData.privateAlias
        if (privateAlias != "") AccountName(privateAlias, Private)
        else AccountName(transaction.metaData.accountHolderName, NoAlias)
    }
    val accountDisplayName = Some(accountHolderName.display)
    val accountAliasType = accountHolderName.aliasType
    val transactionId = Some(transaction.id)
    val otherPartyAccountId = Some(transaction.metaData.id)

    val moreInfo = Some(transaction.metaData.moreInfo)

    val url =  Some(transaction.metaData.url)

    val imageUrl = Some(transaction.metaData.imageUrl)

    val openCorporatesUrl = Some(transaction.metaData.openCorporatesUrl)

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

    val filteredNonObpAccount = new ModeratedMetaData(otherPartyAccountId, accountDisplayName, accountAliasType, moreInfo, url, imageUrl, openCorporatesUrl);

    new ModeratedTransaction(transactionId, Some(transaction.account), Some(filteredNonObpAccount), transactionType, transactionAmount,
      transactionCurrency, transactionLabel, ownerComment, comments, transactionStartDate, transactionFinishDate, transactionBalance, Some(transaction.addComment _))
  	}
  }

object OwnerView extends FullView {
  
}