package code.views.system

import code.api.Constant._
import code.api.util.APIUtil.{isValidCustomViewId, isValidSystemViewId}
import code.api.util.ErrorMessages.{CreateSystemViewError, InvalidCustomViewFormat, InvalidSystemViewFormat}
import code.util.{AccountIdString, UUIDString}
import com.openbankproject.commons.model._
import net.liftweb.common.Box
import net.liftweb.common.Box.tryo
import net.liftweb.mapper._

class ViewDefinition extends View with LongKeyedMapper[ViewDefinition] with ManyToMany with CreatedUpdated{
  def getSingleton = ViewDefinition

  def primaryKeyField = id_

  object id_ extends MappedLongIndex(this)
  object name_ extends MappedString(this, 125)
  object description_ extends MappedString(this, 255)
  object bank_id extends UUIDString(this) {
    override def defaultValue: Null = null
  }
  object account_id extends AccountIdString(this) {
    override def defaultValue: Null = null
  }
  object view_id extends UUIDString(this)
  
  @deprecated("This field is not used in api code anymore","13-12-2019")
  object composite_unique_key extends MappedString(this, 512)
  object metadataView_ extends UUIDString(this)
  object isSystem_ extends MappedBoolean(this){
    override def defaultValue = false
    override def dbIndexed_? = true
  }
  object isPublic_ extends MappedBoolean(this){
    override def defaultValue = false
    override def dbIndexed_? = true
  }
  object isFirehose_ extends MappedBoolean(this){
    override def defaultValue = true
    override def dbIndexed_? = true
  }
  object usePrivateAliasIfOneExists_ extends MappedBoolean(this){
    override def defaultValue = false
  }
  object usePublicAliasIfOneExists_ extends MappedBoolean(this){
    override def defaultValue = false
  }
  object hideOtherAccountMetadataIfAlias_ extends MappedBoolean(this){
    override def defaultValue = false
  }
  
  //This is the system views list, custom views please check `canGrantAccessToCustomViews_` field
  object canGrantAccessToViews_ extends MappedText(this){
    override def defaultValue = ""
  }

  //This is the system views list.custom views please check `canRevokeAccessToCustomViews_` field
  object canRevokeAccessToViews_ extends MappedText(this){
    override def defaultValue = ""
  }
  
  object canRevokeAccessToCustomViews_ extends MappedBoolean(this){
    override def defaultValue = false
  }
  object canGrantAccessToCustomViews_ extends MappedBoolean(this) {
    override def defaultValue = false
  }
  object canSeeTransactionThisBankAccount_ extends MappedBoolean(this){
    override def defaultValue = false
  }
  object canSeeTransactionRequests_ extends MappedBoolean(this){
    override def defaultValue = false
  }
  object canSeeTransactionRequestTypes_ extends MappedBoolean(this){
    override def defaultValue = false
  }
  object canSeeTransactionOtherBankAccount_ extends MappedBoolean(this){
    override def defaultValue = false
  }
  object canSeeTransactionMetadata_ extends MappedBoolean(this){
    override def defaultValue = false
  }
  object canSeeTransactionDescription_ extends MappedBoolean(this){
    override def defaultValue = false
  }
  object canSeeTransactionAmount_ extends MappedBoolean(this){
    override def defaultValue = false
  }
  object canSeeTransactionType_ extends MappedBoolean(this){
    override def defaultValue = false
  }
  object canSeeTransactionCurrency_ extends MappedBoolean(this){
    override def defaultValue = false
  }
  object canSeeTransactionStartDate_ extends MappedBoolean(this){
    override def defaultValue = false
  }
  object canSeeTransactionFinishDate_ extends MappedBoolean(this){
    override def defaultValue = false
  }
  object canSeeTransactionBalance_ extends MappedBoolean(this){
    override def defaultValue = false
  }
  object canSeeComments_ extends MappedBoolean(this){
    override def defaultValue = false
  }
  object canSeeOwnerComment_ extends MappedBoolean(this){
    override def defaultValue = false
  }
  object canSeeTags_ extends MappedBoolean(this){
    override def defaultValue = false
  }
  object canSeeImages_ extends MappedBoolean(this){
    override def defaultValue = false
  }
  object canSeeBankAccountOwners_ extends MappedBoolean(this){
    override def defaultValue = false
  }
  object canSeeAvailableViewsForBankAccount_ extends MappedBoolean(this){
    override def defaultValue = true
  }
  object canSeeBankAccountType_ extends MappedBoolean(this){
    override def defaultValue = false
  }
  object canSeeBankAccountBalance_ extends MappedBoolean(this){
    override def defaultValue = false
  }
  object canQueryAvailableFunds_ extends MappedBoolean(this){
    override def defaultValue = false
  }
  object canSeeBankAccountCurrency_ extends MappedBoolean(this){
    override def defaultValue = false
  }
  object canSeeBankAccountLabel_ extends MappedBoolean(this){
    override def defaultValue = false
  }
  object canUpdateBankAccountLabel_ extends MappedBoolean(this){
    override def defaultValue = false
  }
  object canSeeBankAccountNationalIdentifier_ extends MappedBoolean(this){
    override def defaultValue = false
  }
  object canSeeBankAccountSwift_bic_ extends MappedBoolean(this){
    override def defaultValue = false
  }
  object canSeeBankAccountIban_ extends MappedBoolean(this){
    override def defaultValue = false
  }
  object canSeeBankAccountNumber_ extends MappedBoolean(this){
    override def defaultValue = false
  }
  object canSeeBankAccountBankName_ extends MappedBoolean(this){
    override def defaultValue = false
  }
  object canSeeBankAccountBankPermalink_ extends MappedBoolean(this){
    override def defaultValue = false
  }
  object canSeeBankRoutingScheme_ extends MappedBoolean(this){
    override def defaultValue = false
  }
  object canSeeBankRoutingAddress_ extends MappedBoolean(this){
    override def defaultValue = false
  }
  object canSeeBankAccountRoutingScheme_ extends MappedBoolean(this){
    override def defaultValue = false
  }
  object canSeeBankAccountRoutingAddress_ extends MappedBoolean(this){
    override def defaultValue = false
  }
  object canSeeOtherAccountNationalIdentifier_ extends MappedBoolean(this){
    override def defaultValue = false
  }
  object canSeeOtherAccountSWIFT_BIC_ extends MappedBoolean(this){
    override def defaultValue = false
  }
  object canSeeOtherAccountIBAN_ extends MappedBoolean(this){
    override def defaultValue = false
  }
  object canSeeOtherAccountBankName_ extends MappedBoolean(this){
    override def defaultValue = false
  }
  object canSeeOtherAccountNumber_ extends MappedBoolean(this){
    override def defaultValue = false
  }
  object canSeeOtherAccountMetadata_ extends MappedBoolean(this){
    override def defaultValue = false
  }
  object canSeeOtherAccountKind_ extends MappedBoolean(this){
    override def defaultValue = false
  }
  object canSeeOtherBankRoutingScheme_ extends MappedBoolean(this){
    override def defaultValue = false
  }
  object canSeeOtherBankRoutingAddress_ extends MappedBoolean(this){
    override def defaultValue = false
  }
  object canSeeOtherAccountRoutingScheme_ extends MappedBoolean(this){
    override def defaultValue = false
  }
  object canSeeOtherAccountRoutingAddress_ extends MappedBoolean(this){
    override def defaultValue = false
  }
  object canSeeMoreInfo_ extends MappedBoolean(this){
    override def defaultValue = false
  }
  object canSeeUrl_ extends MappedBoolean(this){
    override def defaultValue = false
  }
  object canSeeImageUrl_ extends MappedBoolean(this){
    override def defaultValue = false
  }
  object canSeeOpenCorporatesUrl_ extends MappedBoolean(this){
    override def defaultValue = false
  }
  object canSeeCorporateLocation_ extends MappedBoolean(this){
    override def defaultValue = false
  }
  object canSeePhysicalLocation_ extends MappedBoolean(this){
    override def defaultValue = false
  }
  object canSeePublicAlias_ extends MappedBoolean(this){
    override def defaultValue = false
  }
  object canSeePrivateAlias_ extends MappedBoolean(this){
    override def defaultValue = false
  }
  object canAddMoreInfo_ extends MappedBoolean(this){
    override def defaultValue = false
  }
  object canAddURL_ extends MappedBoolean(this){
    override def defaultValue = false
  }
  object canAddImageURL_ extends MappedBoolean(this){
    override def defaultValue = false
  }
  object canAddOpenCorporatesUrl_ extends MappedBoolean(this){
    override def defaultValue = false
  }
  object canAddCorporateLocation_ extends MappedBoolean(this){
    override def defaultValue = false
  }
  object canAddPhysicalLocation_ extends MappedBoolean(this){
    override def defaultValue = false
  }
  object canAddPublicAlias_ extends MappedBoolean(this){
    override def defaultValue = false
  }
  object canAddPrivateAlias_ extends MappedBoolean(this){
    override def defaultValue = false
  }
  object canAddCounterparty_ extends MappedBoolean(this){
    override def defaultValue = false
  }
  object canGetCounterparty_ extends MappedBoolean(this){
    override def defaultValue = false
  }
  object canDeleteCounterparty_ extends MappedBoolean(this){
    override def defaultValue = false
  }
  object canDeleteCorporateLocation_ extends MappedBoolean(this){
    override def defaultValue = false
  }
  object canDeletePhysicalLocation_ extends MappedBoolean(this){
    override def defaultValue = false
  }
  object canEditOwnerComment_ extends MappedBoolean(this){
    override def defaultValue = false
  }
  object canAddComment_ extends MappedBoolean(this){
    override def defaultValue = false
  }
  object canDeleteComment_ extends MappedBoolean(this){
    override def defaultValue = false
  }
  object canAddTag_ extends MappedBoolean(this){
    override def defaultValue = false
  }
  object canDeleteTag_ extends MappedBoolean(this){
    override def defaultValue = false
  }
  object canAddImage_ extends MappedBoolean(this){
    override def defaultValue = false
  }
  object canDeleteImage_ extends MappedBoolean(this){
    override def defaultValue = false
  }
  object canAddWhereTag_ extends MappedBoolean(this){
    override def defaultValue = false
  }
  object canSeeWhereTag_ extends MappedBoolean(this){
    override def defaultValue = false
  }
  object canDeleteWhereTag_ extends MappedBoolean(this){
    override def defaultValue = false
  }
  
  //internal transfer between my own accounts

  @deprecated("we added new field `canAddTransactionRequestToBeneficiary_`","25-07-2024")
  object canAddTransactionRequestToOwnAccount_ extends MappedBoolean(this){
    override def defaultValue = false
  }

  object canAddTransactionRequestToBeneficiary_ extends MappedBoolean(this){
    override def defaultValue = false
  }
  
  // transfer to any account
  object canAddTransactionRequestToAnyAccount_ extends MappedBoolean(this){
    override def defaultValue = false
  }
  object canSeeBankAccountCreditLimit_ extends MappedBoolean(this){
    override def defaultValue = false
  }
  object canCreateDirectDebit_ extends MappedBoolean(this){
    override def defaultValue = false
  }
  object canCreateStandingOrder_ extends MappedBoolean(this){
    override def defaultValue = false
  }
  
  object canCreateCustomView_ extends MappedBoolean(this){
    override def defaultValue = false
  }
  object canDeleteCustomView_ extends MappedBoolean(this){
    override def defaultValue = false
  }
  object canUpdateCustomView_ extends MappedBoolean(this){
    override def defaultValue = false
  }
  object canGetCustomView_ extends MappedBoolean(this){
    override def defaultValue = false
  }
  object canSeeViewsWithPermissionsForAllUsers_ extends MappedBoolean(this){
    override def defaultValue = false
  }
  object canSeeViewsWithPermissionsForOneUser_ extends MappedBoolean(this){
    override def defaultValue = false
  }
  object canSeeTransactionStatus_ extends MappedBoolean(this){
    override def defaultValue = false
  }

  //Important! If you add a field, be sure to handle it here in this function
  def setFromViewData(viewSpecification : ViewSpecification) = {
    if(viewSpecification.which_alias_to_use == "public"){
      usePublicAliasIfOneExists_(true)
      usePrivateAliasIfOneExists_(false)
    } else if(viewSpecification.which_alias_to_use == "private"){
      usePublicAliasIfOneExists_(false)
      usePrivateAliasIfOneExists_(true)
    } else {
      usePublicAliasIfOneExists_(false)
      usePrivateAliasIfOneExists_(false)
    }

    hideOtherAccountMetadataIfAlias_(viewSpecification.hide_metadata_if_alias_used)
    description_(viewSpecification.description)
    isPublic_(viewSpecification.is_public)
    isFirehose_(viewSpecification.is_firehose.getOrElse(false))
    metadataView_(viewSpecification.metadata_view)
    
    ViewPermission.resetViewPermissions(
      this,
      viewSpecification.allowed_actions,
      viewSpecification.can_grant_access_to_views.getOrElse(Nil),
      viewSpecification.can_revoke_access_to_views.getOrElse(Nil)
    )
    
  }

  def createViewAndPermissions(viewSpecification : ViewSpecification) = {
    if(viewSpecification.which_alias_to_use == "public"){
      usePublicAliasIfOneExists_(true)
      usePrivateAliasIfOneExists_(false)
    } else if(viewSpecification.which_alias_to_use == "private"){
      usePublicAliasIfOneExists_(false)
      usePrivateAliasIfOneExists_(true)
    } else {
      usePublicAliasIfOneExists_(false)
      usePrivateAliasIfOneExists_(false)
    }

    hideOtherAccountMetadataIfAlias_(viewSpecification.hide_metadata_if_alias_used)
    description_(viewSpecification.description)
    isPublic_(viewSpecification.is_public)
    isFirehose_(viewSpecification.is_firehose.getOrElse(false))
    metadataView_(viewSpecification.metadata_view)

    ViewPermission.resetViewPermissions(
      this,
      viewSpecification.allowed_actions,
      viewSpecification.can_grant_access_to_views.getOrElse(Nil),
      viewSpecification.can_revoke_access_to_views.getOrElse(Nil)
    )

  }
  
  def deleteViewPermissions = {
    ViewPermission.findViewPermissions(this).map(_.delete_!)
  }

  

  def id: Long = id_.get
  def viewId : ViewId = ViewId(view_id.get)
  
  @deprecated("This field is not used in api code anymore","13-12-2019")
  def viewIdInternal: String = composite_unique_key.get
  //if metadataView_ = null or empty, we need use the current view's viewId.
  def metadataView = if (metadataView_.get ==null || metadataView_.get == "") view_id.get else metadataView_.get
  def users : List[User] = Nil
  def bankId = BankId(bank_id.get)
  def accountId = AccountId(account_id.get)
  def name: String = name_.get
  def description : String = description_.get
  def isPublic : Boolean = isPublic_.get
  def isPrivate : Boolean = !isPublic_.get
  def isFirehose : Boolean = isFirehose_.get
  def isSystem: Boolean = isSystem_.get
  //the view settings
  def usePrivateAliasIfOneExists: Boolean = usePrivateAliasIfOneExists_.get
  def usePublicAliasIfOneExists: Boolean = usePublicAliasIfOneExists_.get
  def hideOtherAccountMetadataIfAlias: Boolean = hideOtherAccountMetadataIfAlias_.get

  override def allowed_actions : List[String] = ViewPermission.findViewPermissions(this).map(_.permission.get).distinct

  override def canGrantAccessToViews : Option[List[String]] = {
   ViewPermission.findViewPermission(this, CAN_GRANT_ACCESS_TO_VIEWS).flatMap(vp => 
    {
      vp.extraData.get match {
        case value if(value != null && !value.isEmpty) => Some(value.split(",").toList.map(_.trim))
        case _ => None
      }
    })
  }
  
  override def canRevokeAccessToViews : Option[List[String]] = {
    ViewPermission.findViewPermission(this, CAN_REVOKE_ACCESS_TO_VIEWS).flatMap(vp =>
    {
      vp.extraData.get match {
        case value if(value != null && !value.isEmpty) => Some(value.split(",").toList.map(_.trim))
        case _ => None
      }
    })
  }

  //TODO All the following methods can be removed later, we use ViewPermission table instead. 
  override def canRevokeAccessToCustomViews : Boolean = canRevokeAccessToCustomViews_.get
  override def canGrantAccessToCustomViews : Boolean = canGrantAccessToCustomViews_.get
  def canSeeTransactionThisBankAccount : Boolean = canSeeTransactionThisBankAccount_.get
  def canSeeTransactionRequests : Boolean = canSeeTransactionRequests_.get
  def canSeeTransactionRequestTypes: Boolean = canSeeTransactionRequestTypes_.get
  def canSeeTransactionOtherBankAccount : Boolean = canSeeTransactionOtherBankAccount_.get
  def canSeeTransactionMetadata : Boolean = canSeeTransactionMetadata_.get
  def canSeeTransactionDescription: Boolean = canSeeTransactionDescription_.get
  def canSeeTransactionAmount: Boolean = canSeeTransactionAmount_.get
  def canSeeTransactionType: Boolean = canSeeTransactionType_.get
  def canSeeTransactionCurrency: Boolean = canSeeTransactionCurrency_.get
  def canSeeTransactionStartDate: Boolean = canSeeTransactionStartDate_.get
  def canSeeTransactionFinishDate: Boolean = canSeeTransactionFinishDate_.get
  def canSeeTransactionBalance: Boolean = canSeeTransactionBalance_.get
  def canSeeTransactionStatus: Boolean = canSeeTransactionStatus_.get
  def canSeeComments: Boolean = canSeeComments_.get
  def canSeeOwnerComment: Boolean = canSeeOwnerComment_.get
  def canSeeTags : Boolean = canSeeTags_.get
  def canSeeImages : Boolean = canSeeImages_.get
  def canSeeAvailableViewsForBankAccount : Boolean = canSeeAvailableViewsForBankAccount_.get
  def canSeeBankAccountOwners : Boolean = canSeeBankAccountOwners_.get
  def canSeeBankAccountType : Boolean = canSeeBankAccountType_.get
  def canSeeBankAccountBalance : Boolean = canSeeBankAccountBalance_.get
  def canSeeBankAccountCurrency : Boolean = canSeeBankAccountCurrency_.get
  def canQueryAvailableFunds : Boolean = canQueryAvailableFunds_.get
  def canSeeBankAccountLabel : Boolean = canSeeBankAccountLabel_.get
  def canUpdateBankAccountLabel : Boolean = canUpdateBankAccountLabel_.get
  def canSeeBankAccountNationalIdentifier : Boolean = canSeeBankAccountNationalIdentifier_.get
  def canSeeBankAccountSwiftBic : Boolean = canSeeBankAccountSwift_bic_.get
  def canSeeBankAccountIban : Boolean = canSeeBankAccountIban_.get
  def canSeeBankAccountNumber : Boolean = canSeeBankAccountNumber_.get
  def canSeeBankAccountBankName : Boolean = canSeeBankAccountBankName_.get
  def canSeeBankAccountBankPermalink : Boolean = canSeeBankAccountBankPermalink_.get
  def canSeeBankRoutingScheme : Boolean = canSeeBankRoutingScheme_.get
  def canSeeBankRoutingAddress : Boolean = canSeeBankRoutingAddress_.get
  def canSeeBankAccountRoutingScheme : Boolean = canSeeBankAccountRoutingScheme_.get
  def canSeeBankAccountRoutingAddress : Boolean = canSeeBankAccountRoutingAddress_.get
  def canSeeViewsWithPermissionsForOneUser: Boolean = canSeeViewsWithPermissionsForOneUser_.get
  def canSeeViewsWithPermissionsForAllUsers : Boolean = canSeeViewsWithPermissionsForAllUsers_.get
  def canSeeOtherAccountNationalIdentifier : Boolean = canSeeOtherAccountNationalIdentifier_.get
  def canSeeOtherAccountSwiftBic : Boolean = canSeeOtherAccountSWIFT_BIC_.get
  def canSeeOtherAccountIban : Boolean = canSeeOtherAccountIBAN_.get
  def canSeeOtherAccountBankName : Boolean = canSeeOtherAccountBankName_.get
  def canSeeOtherAccountNumber : Boolean = canSeeOtherAccountNumber_.get
  def canSeeOtherAccountMetadata : Boolean = canSeeOtherAccountMetadata_.get
  def canSeeOtherAccountKind : Boolean = canSeeOtherAccountKind_.get
  def canSeeOtherBankRoutingScheme : Boolean = canSeeOtherBankRoutingScheme_.get
  def canSeeOtherBankRoutingAddress : Boolean = canSeeOtherBankRoutingAddress_.get
  def canSeeOtherAccountRoutingScheme : Boolean = canSeeOtherAccountRoutingScheme_.get
  def canSeeOtherAccountRoutingAddress : Boolean = canSeeOtherAccountRoutingAddress_.get
  def canSeeMoreInfo: Boolean = canSeeMoreInfo_.get
  def canSeeUrl: Boolean = canSeeUrl_.get
  def canSeeImageUrl: Boolean = canSeeImageUrl_.get
  def canSeeOpenCorporatesUrl: Boolean = canSeeOpenCorporatesUrl_.get
  def canSeeCorporateLocation : Boolean = canSeeCorporateLocation_.get
  def canSeePhysicalLocation : Boolean = canSeePhysicalLocation_.get
  def canSeePublicAlias : Boolean = canSeePublicAlias_.get
  def canSeePrivateAlias : Boolean = canSeePrivateAlias_.get
  def canAddMoreInfo : Boolean = canAddMoreInfo_.get
  def canAddUrl : Boolean = canAddURL_.get
  def canAddImageUrl : Boolean = canAddImageURL_.get
  def canAddOpenCorporatesUrl : Boolean = canAddOpenCorporatesUrl_.get
  def canAddCorporateLocation : Boolean = canAddCorporateLocation_.get
  def canAddPhysicalLocation : Boolean = canAddPhysicalLocation_.get
  def canAddPublicAlias : Boolean = canAddPublicAlias_.get
  def canAddPrivateAlias : Boolean = canAddPrivateAlias_.get
  def canAddCounterparty : Boolean = canAddCounterparty_.get
  def canGetCounterparty : Boolean = canGetCounterparty_.get
  def canDeleteCounterparty : Boolean = canDeleteCounterparty_.get
  def canDeleteCorporateLocation : Boolean = canDeleteCorporateLocation_.get
  def canDeletePhysicalLocation : Boolean = canDeletePhysicalLocation_.get
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
  def canAddTransactionRequestToOwnAccount: Boolean = false //we do not need this field, set this to false.
  def canAddTransactionRequestToAnyAccount: Boolean = canAddTransactionRequestToAnyAccount_.get
  def canAddTransactionRequestToBeneficiary: Boolean = canAddTransactionRequestToBeneficiary_.get
  def canSeeBankAccountCreditLimit: Boolean = canSeeBankAccountCreditLimit_.get
  def canCreateDirectDebit: Boolean = canCreateDirectDebit_.get
  def canCreateStandingOrder: Boolean = canCreateStandingOrder_.get
  def canCreateCustomView: Boolean = canCreateCustomView_.get
  def canDeleteCustomView: Boolean = canDeleteCustomView_.get
  def canUpdateCustomView: Boolean = canUpdateCustomView_.get
  def canGetCustomView: Boolean = canGetCustomView_.get
}

object ViewDefinition extends ViewDefinition with LongKeyedMetaMapper[ViewDefinition] {
  override def dbIndexes: List[BaseIndex[ViewDefinition]] = UniqueIndex(composite_unique_key) :: super.dbIndexes
  override def beforeSave = List(
    t =>{
      tryo {
        val compositeUniqueKey = getUniqueKey(t.bank_id.get, t.account_id.get, t.view_id.get)
        t.composite_unique_key(compositeUniqueKey)
      }

      if (t.isSystem && !isValidSystemViewId(t.view_id.get)) {
        throw new RuntimeException(InvalidSystemViewFormat+s"Current view_id (${t.view_id.get})")
      }
      if (!t.isSystem && !isValidCustomViewId(t.view_id.get)) {
        throw new RuntimeException(InvalidCustomViewFormat+s"Current view_id (${t.view_id.get})")
      }
      
      //sanity checks
      if (!t.isSystem && (t.bank_id ==null || t.account_id == null)) {
        throw new RuntimeException(CreateSystemViewError+s"Current view.isSystem${t.isSystem}, bank_id${t.bank_id}, account_id${t.account_id}")
      }
    }
  )

  def findSystemView(viewId: String): Box[ViewDefinition] = {
    ViewDefinition.find(
      NullRef(ViewDefinition.bank_id),
      NullRef(ViewDefinition.account_id),
      By(ViewDefinition.isSystem_, true),
      By(ViewDefinition.view_id, viewId),
    )
  }
  def getSystemViews(): List[ViewDefinition] = {
    ViewDefinition.findAll(
      By(ViewDefinition.isSystem_, true)
    )
  }

  def findCustomView(bankId: String, accountId: String, viewId: String): Box[ViewDefinition] = {
    ViewDefinition.find(
      By(ViewDefinition.bank_id, bankId),
      By(ViewDefinition.account_id, accountId),
      By(ViewDefinition.isSystem_, false),
      By(ViewDefinition.view_id, viewId),
    )
  }
  def getCustomViews(): List[ViewDefinition] = {
    ViewDefinition.findAll(
      By(ViewDefinition.isSystem_, false)
    )
  }
  
  @deprecated("This is method only used for migration stuff, please use @findCustomView and @findSystemView instead.","13-12-2019")
  def findByUniqueKey(bankId: String, accountId: String, viewId: String): Box[ViewDefinition] = {
    val uniqueKey = getUniqueKey(bankId, accountId, viewId)
    ViewDefinition.find(
      By(ViewDefinition.composite_unique_key, uniqueKey)
    )
  }

  def accountFilter(bankId : BankId, accountId : AccountId) : List[QueryParam[ViewDefinition]] = {
    By(bank_id, bankId.value) :: By(account_id, accountId.value) :: Nil
  }
  
  @deprecated("This is method only used for migration stuff, do not use api code.","13-12-2019")
  def getUniqueKey(bankId: String, accountId: String, viewId: String) = List(bankId, accountId, viewId).mkString("|","|--|","|")
}