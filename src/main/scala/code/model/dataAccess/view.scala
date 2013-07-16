package code.model.dataAccess

import net.liftweb.mapper._
import code.model.{View, BankAccount}

class ViewPrivileges extends LongKeyedMapper[ViewPrivileges] with IdPK {
  def getSingleton = ViewPrivileges
  object privilege extends MappedLongForeignKey(this, Privilege)
  object view extends MappedLongForeignKey(this, ViewImpl)
}
object ViewPrivileges extends ViewPrivileges with LongKeyedMetaMapper[ViewPrivileges]

class ViewImpl extends View with LongKeyedMapper[ViewImpl] with ManyToMany with CreatedUpdated{
  def getSingleton = ViewImpl

  def primaryKeyField = id_
  object privileges extends MappedManyToMany(ViewPrivileges, ViewPrivileges.view, ViewPrivileges.privilege, Privilege)
  object account extends MappedLongForeignKey(this, HostedAccount)

  object id_ extends MappedLongIndex(this)
  object name_ extends MappedString(this, 255)
  object description_ extends MappedString(this, 255)
  object permalink_ extends MappedString(this, 255)

  object isPublic_ extends MappedBoolean(this){
    override def dbIndexed_? = true
  }
  object usePrivateAliasIfOneExists_ extends MappedBoolean(this)
  object usePublicAliasIfOneExists_ extends MappedBoolean(this)
  object hideOtherAccountMetadataIfAlias_ extends MappedBoolean(this)

  object canSeeTransactionThisBankAccount_ extends MappedBoolean(this)
  object canSeeTransactionOtherBankAccount_ extends MappedBoolean(this)
  object canSeeTransactionMetadata_ extends MappedBoolean(this)
  object canSeeTransactionLabel_ extends MappedBoolean(this)
  object canSeeTransactionAmount_ extends MappedBoolean(this)
  object canSeeTransactionType_ extends MappedBoolean(this)
  object canSeeTransactionCurrency_ extends MappedBoolean(this)
  object canSeeTransactionStartDate_ extends MappedBoolean(this)
  object canSeeTransactionFinishDate_ extends MappedBoolean(this)
  object canSeeTransactionBalance_ extends MappedBoolean(this)
  object canSeeComments_ extends MappedBoolean(this)
  object canSeeOwnerComment_ extends MappedBoolean(this)
  object canSeeTags_ extends MappedBoolean(this)
  object canSeeImages_ extends MappedBoolean(this)
  object canSeeBankAccountOwners_ extends MappedBoolean(this)
  object canSeeBankAccountType_ extends MappedBoolean(this)
  object canSeeBankAccountBalance_ extends MappedBoolean(this)
  object canSeeBankAccountBalancePositiveOrNegative_ extends MappedBoolean(this)
  object canSeeBankAccountCurrency_ extends MappedBoolean(this)
  object canSeeBankAccountLabel_ extends MappedBoolean(this)
  object canSeeBankAccountNationalIdentifier_ extends MappedBoolean(this)
  object canSeeBankAccountSwift_bic_ extends MappedBoolean(this)
  object canSeeBankAccountIban_ extends MappedBoolean(this)
  object canSeeBankAccountNumber_ extends MappedBoolean(this)
  object canSeeBankAccountBankName_ extends MappedBoolean(this)
  object canSeeBankAccountBankPermalink_ extends MappedBoolean(this)
  object canSeeOtherAccountNationalIdentifier_ extends MappedBoolean(this)
  object canSeeSWIFT_BIC_ extends MappedBoolean(this)
  object canSeeOtherAccountIBAN_ extends MappedBoolean(this)
  object canSeeOtherAccountBankName_ extends MappedBoolean(this)
  object canSeeOtherAccountNumber_ extends MappedBoolean(this)
  object canSeeOtherAccountMetadata_ extends MappedBoolean(this)
  object canSeeOtherAccountKind_ extends MappedBoolean(this)
  object canSeeMoreInfo_ extends MappedBoolean(this)
  object canSeeUrl_ extends MappedBoolean(this)
  object canSeeImageUrl_ extends MappedBoolean(this)
  object canSeeOpenCorporatesUrl_ extends MappedBoolean(this)
  object canSeeCorporateLocation_ extends MappedBoolean(this)
  object canSeePhysicalLocation_ extends MappedBoolean(this)
  object canSeePublicAlias_ extends MappedBoolean(this)
  object canSeePrivateAlias_ extends MappedBoolean(this)
  object canAddMoreInfo_ extends MappedBoolean(this)
  object canAddURL_ extends MappedBoolean(this)
  object canAddImageURL_ extends MappedBoolean(this)
  object canAddOpenCorporatesUrl_ extends MappedBoolean(this)
  object canAddCorporateLocation_ extends MappedBoolean(this)
  object canAddPhysicalLocation_ extends MappedBoolean(this)
  object canAddPublicAlias_ extends MappedBoolean(this)
  object canAddPrivateAlias_ extends MappedBoolean(this)
  object canDeleteCorporateLocation_ extends MappedBoolean(this)
  object canDeletePhysicalLocation_ extends MappedBoolean(this)
  object canEditOwnerComment_ extends MappedBoolean(this)
  object canAddComment_ extends MappedBoolean(this)
  object canDeleteComment_ extends MappedBoolean(this)
  object canAddTag_ extends MappedBoolean(this)
  object canDeleteTag_ extends MappedBoolean(this)
  object canAddImage_ extends MappedBoolean(this)
  object canDeleteImage_ extends MappedBoolean(this)
  object canAddWhereTag_ extends MappedBoolean(this)
  object canSeeWhereTag_ extends MappedBoolean(this)
  object canDeleteWhereTag_ extends MappedBoolean(this)

  //e.g. "Public", "Authorities", "Our Network", etc.
  def id: Long = id_.get
  def name: String = name_.get
  def description : String = description_.get
  def permalink : String = permalink_.get
  def isPublic : Boolean = isPublic_.get

  //the view settings
  def usePrivateAliasIfOneExists: Boolean = usePrivateAliasIfOneExists_.get
  def usePublicAliasIfOneExists: Boolean = usePublicAliasIfOneExists_.get
  def hideOtherAccountMetadataIfAlias: Boolean = hideOtherAccountMetadataIfAlias_.get

  //reading access

  //transaction fields
  def canSeeTransactionThisBankAccount : Boolean = canSeeTransactionThisBankAccount_.get
  def canSeeTransactionOtherBankAccount : Boolean = canSeeTransactionOtherBankAccount_.get
  def canSeeTransactionMetadata : Boolean = canSeeTransactionMetadata_.get
  def canSeeTransactionLabel: Boolean = canSeeTransactionLabel_.get
  def canSeeTransactionAmount: Boolean = canSeeTransactionAmount_.get
  def canSeeTransactionType: Boolean = canSeeTransactionType_.get
  def canSeeTransactionCurrency: Boolean = canSeeTransactionCurrency_.get
  def canSeeTransactionStartDate: Boolean = canSeeTransactionStartDate_.get
  def canSeeTransactionFinishDate: Boolean = canSeeTransactionFinishDate_.get
  def canSeeTransactionBalance: Boolean = canSeeTransactionBalance_.get

  //transaction metadata
  def canSeeComments: Boolean = canSeeComments_.get
  def canSeeOwnerComment: Boolean = canSeeOwnerComment_.get
  def canSeeTags : Boolean = canSeeTags_.get
  def canSeeImages : Boolean = canSeeImages_.get

  //Bank account fields
  def canSeeBankAccountOwners : Boolean = canSeeBankAccountOwners_.get
  def canSeeBankAccountType : Boolean = canSeeBankAccountType_.get
  def canSeeBankAccountBalance : Boolean = canSeeBankAccountBalance_.get
  def canSeeBankAccountBalancePositiveOrNegative : Boolean = canSeeBankAccountBalancePositiveOrNegative_.get
  def canSeeBankAccountCurrency : Boolean = canSeeBankAccountCurrency_.get
  def canSeeBankAccountLabel : Boolean = canSeeBankAccountLabel_.get
  def canSeeBankAccountNationalIdentifier : Boolean = canSeeBankAccountNationalIdentifier_.get
  def canSeeBankAccountSwift_bic : Boolean = canSeeBankAccountSwift_bic_.get
  def canSeeBankAccountIban : Boolean = canSeeBankAccountIban_.get
  def canSeeBankAccountNumber : Boolean = canSeeBankAccountNumber_.get
  def canSeeBankAccountBankName : Boolean = canSeeBankAccountBankName_.get
  def canSeeBankAccountBankPermalink : Boolean = canSeeBankAccountBankPermalink_.get

  //other bank account fields
  def canSeeOtherAccountNationalIdentifier : Boolean = canSeeOtherAccountNationalIdentifier_.get
  def canSeeSWIFT_BIC : Boolean = canSeeSWIFT_BIC_.get
  def canSeeOtherAccountIBAN : Boolean = canSeeOtherAccountIBAN_.get
  def canSeeOtherAccountBankName : Boolean = canSeeOtherAccountBankName_.get
  def canSeeOtherAccountNumber : Boolean = canSeeOtherAccountNumber_.get
  def canSeeOtherAccountMetadata : Boolean = canSeeOtherAccountMetadata_.get
  def canSeeOtherAccountKind : Boolean = canSeeOtherAccountKind_.get

  //other bank account meta data
  def canSeeMoreInfo: Boolean = canSeeMoreInfo_.get
  def canSeeUrl: Boolean = canSeeUrl_.get
  def canSeeImageUrl: Boolean = canSeeImageUrl_.get
  def canSeeOpenCorporatesUrl: Boolean = canSeeOpenCorporatesUrl_.get
  def canSeeCorporateLocation : Boolean = canSeeCorporateLocation_.get
  def canSeePhysicalLocation : Boolean = canSeePhysicalLocation_.get
  def canSeePublicAlias : Boolean = canSeePublicAlias_.get
  def canSeePrivateAlias : Boolean = canSeePrivateAlias_.get
  def canAddMoreInfo : Boolean = canAddMoreInfo_.get
  def canAddURL : Boolean = canAddURL_.get
  def canAddImageURL : Boolean = canAddImageURL_.get
  def canAddOpenCorporatesUrl : Boolean = canAddOpenCorporatesUrl_.get
  def canAddCorporateLocation : Boolean = canAddCorporateLocation_.get
  def canAddPhysicalLocation : Boolean = canAddPhysicalLocation_.get
  def canAddPublicAlias : Boolean = canAddPublicAlias_.get
  def canAddPrivateAlias : Boolean = canAddPrivateAlias_.get
  def canDeleteCorporateLocation : Boolean = canDeleteCorporateLocation_.get
  def canDeletePhysicalLocation : Boolean = canDeletePhysicalLocation_.get

  //writing access
  def canEditOwnerComment: Boolean = canEditOwnerComment_.get
  def canAddComment : Boolean = canAddComment_.get
  def canDeleteComment: Boolean = canDeleteComment_.get
  def canAddTag : Boolean = canAddTag_.get
  def canDeleteTag : Boolean = canDeleteTag_.get
  def canAddImage : Boolean = canAddImage_.get
  def canDeleteImage : Boolean = canDeleteImage_.get
  def canAddWhereTag : Boolean = canAddWhereTag_.get
  def canSeeWhereTag : Boolean = canSeeWhereTag_.get
  def canDeleteWhereTag : Boolean = canDeleteWhereTag_.get
}

object ViewImpl extends ViewImpl with LongKeyedMetaMapper[ViewImpl]{
  override def dbIndexes = Index(permalink_, account):: super.dbIndexes
}