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
  
  object canGrantAccessToViews_ extends MappedText(this){
    override def defaultValue = ""
  }

  object canRevokeAccessToViews_ extends MappedText(this){
    override def defaultValue = ""
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

  // These permission accessors now derive from the ViewPermission table via `allowed_actions`,
  // not the legacy per-permission boolean columns (issue #26). The boolean columns have been
  // retired and the ViewPermission table is the single source of truth. Each accessor maps to
  // exactly one permission-string constant (see code.api.Constant) — the same 1:1 mapping the
  // former migration used via StringHelpers.camelifyMethod.
  private def hasPermission(permission: String): Boolean = allowed_actions.exists(_ == permission)

  override def canRevokeAccessToCustomViews : Boolean = hasPermission(CAN_REVOKE_ACCESS_TO_CUSTOM_VIEWS)
  override def canGrantAccessToCustomViews : Boolean = hasPermission(CAN_GRANT_ACCESS_TO_CUSTOM_VIEWS)
  def canSeeTransactionThisBankAccount : Boolean = hasPermission(CAN_SEE_TRANSACTION_THIS_BANK_ACCOUNT)
  def canSeeTransactionRequests : Boolean = hasPermission(CAN_SEE_TRANSACTION_REQUESTS)
  def canSeeTransactionRequestTypes: Boolean = hasPermission(CAN_SEE_TRANSACTION_REQUEST_TYPES)
  def canSeeTransactionOtherBankAccount : Boolean = hasPermission(CAN_SEE_TRANSACTION_OTHER_BANK_ACCOUNT)
  def canSeeTransactionMetadata : Boolean = hasPermission(CAN_SEE_TRANSACTION_METADATA)
  def canSeeTransactionDescription: Boolean = hasPermission(CAN_SEE_TRANSACTION_DESCRIPTION)
  def canSeeTransactionAmount: Boolean = hasPermission(CAN_SEE_TRANSACTION_AMOUNT)
  def canSeeTransactionType: Boolean = hasPermission(CAN_SEE_TRANSACTION_TYPE)
  def canSeeTransactionCurrency: Boolean = hasPermission(CAN_SEE_TRANSACTION_CURRENCY)
  def canSeeTransactionStartDate: Boolean = hasPermission(CAN_SEE_TRANSACTION_START_DATE)
  def canSeeTransactionFinishDate: Boolean = hasPermission(CAN_SEE_TRANSACTION_FINISH_DATE)
  def canSeeTransactionBalance: Boolean = hasPermission(CAN_SEE_TRANSACTION_BALANCE)
  def canSeeTransactionStatus: Boolean = hasPermission(CAN_SEE_TRANSACTION_STATUS)
  def canSeeComments: Boolean = hasPermission(CAN_SEE_COMMENTS)
  def canSeeOwnerComment: Boolean = hasPermission(CAN_SEE_OWNER_COMMENT)
  def canSeeTags : Boolean = hasPermission(CAN_SEE_TAGS)
  def canSeeImages : Boolean = hasPermission(CAN_SEE_IMAGES)
  def canSeeAvailableViewsForBankAccount : Boolean = hasPermission(CAN_SEE_AVAILABLE_VIEWS_FOR_BANK_ACCOUNT)
  def canSeeBankAccountOwners : Boolean = hasPermission(CAN_SEE_BANK_ACCOUNT_OWNERS)
  def canSeeBankAccountType : Boolean = hasPermission(CAN_SEE_BANK_ACCOUNT_TYPE)
  def canSeeBankAccountBalance : Boolean = hasPermission(CAN_SEE_BANK_ACCOUNT_BALANCE)
  def canSeeBankAccountCurrency : Boolean = hasPermission(CAN_SEE_BANK_ACCOUNT_CURRENCY)
  def canQueryAvailableFunds : Boolean = hasPermission(CAN_QUERY_AVAILABLE_FUNDS)
  def canSeeBankAccountLabel : Boolean = hasPermission(CAN_SEE_BANK_ACCOUNT_LABEL)
  def canUpdateBankAccountLabel : Boolean = hasPermission(CAN_UPDATE_BANK_ACCOUNT_LABEL)
  def canSeeBankAccountNationalIdentifier : Boolean = hasPermission(CAN_SEE_BANK_ACCOUNT_NATIONAL_IDENTIFIER)
  def canSeeBankAccountSwiftBic : Boolean = hasPermission(CAN_SEE_BANK_ACCOUNT_SWIFT_BIC)
  def canSeeBankAccountIban : Boolean = hasPermission(CAN_SEE_BANK_ACCOUNT_IBAN)
  def canSeeBankAccountNumber : Boolean = hasPermission(CAN_SEE_BANK_ACCOUNT_NUMBER)
  def canSeeBankAccountBankName : Boolean = hasPermission(CAN_SEE_BANK_ACCOUNT_BANK_NAME)
  def canSeeBankAccountBankPermalink : Boolean = hasPermission(CAN_SEE_BANK_ACCOUNT_BANK_PERMALINK)
  def canSeeBankRoutingScheme : Boolean = hasPermission(CAN_SEE_BANK_ROUTING_SCHEME)
  def canSeeBankRoutingAddress : Boolean = hasPermission(CAN_SEE_BANK_ROUTING_ADDRESS)
  def canSeeBankAccountRoutingScheme : Boolean = hasPermission(CAN_SEE_BANK_ACCOUNT_ROUTING_SCHEME)
  def canSeeBankAccountRoutingAddress : Boolean = hasPermission(CAN_SEE_BANK_ACCOUNT_ROUTING_ADDRESS)
  def canSeeViewsWithPermissionsForOneUser: Boolean = hasPermission(CAN_SEE_VIEWS_WITH_PERMISSIONS_FOR_ONE_USER)
  def canSeeViewsWithPermissionsForAllUsers : Boolean = hasPermission(CAN_SEE_VIEWS_WITH_PERMISSIONS_FOR_ALL_USERS)
  def canSeeOtherAccountNationalIdentifier : Boolean = hasPermission(CAN_SEE_OTHER_ACCOUNT_NATIONAL_IDENTIFIER)
  def canSeeOtherAccountSwiftBic : Boolean = hasPermission(CAN_SEE_OTHER_ACCOUNT_SWIFT_BIC)
  def canSeeOtherAccountIban : Boolean = hasPermission(CAN_SEE_OTHER_ACCOUNT_IBAN)
  def canSeeOtherAccountBankName : Boolean = hasPermission(CAN_SEE_OTHER_ACCOUNT_BANK_NAME)
  def canSeeOtherAccountNumber : Boolean = hasPermission(CAN_SEE_OTHER_ACCOUNT_NUMBER)
  def canSeeOtherAccountMetadata : Boolean = hasPermission(CAN_SEE_OTHER_ACCOUNT_METADATA)
  def canSeeOtherAccountKind : Boolean = hasPermission(CAN_SEE_OTHER_ACCOUNT_KIND)
  def canSeeOtherBankRoutingScheme : Boolean = hasPermission(CAN_SEE_OTHER_BANK_ROUTING_SCHEME)
  def canSeeOtherBankRoutingAddress : Boolean = hasPermission(CAN_SEE_OTHER_BANK_ROUTING_ADDRESS)
  def canSeeOtherAccountRoutingScheme : Boolean = hasPermission(CAN_SEE_OTHER_ACCOUNT_ROUTING_SCHEME)
  def canSeeOtherAccountRoutingAddress : Boolean = hasPermission(CAN_SEE_OTHER_ACCOUNT_ROUTING_ADDRESS)
  def canSeeMoreInfo: Boolean = hasPermission(CAN_SEE_MORE_INFO)
  def canSeeUrl: Boolean = hasPermission(CAN_SEE_URL)
  def canSeeImageUrl: Boolean = hasPermission(CAN_SEE_IMAGE_URL)
  def canSeeOpenCorporatesUrl: Boolean = hasPermission(CAN_SEE_OPEN_CORPORATES_URL)
  def canSeeCorporateLocation : Boolean = hasPermission(CAN_SEE_CORPORATE_LOCATION)
  def canSeePhysicalLocation : Boolean = hasPermission(CAN_SEE_PHYSICAL_LOCATION)
  def canSeePublicAlias : Boolean = hasPermission(CAN_SEE_PUBLIC_ALIAS)
  def canSeePrivateAlias : Boolean = hasPermission(CAN_SEE_PRIVATE_ALIAS)
  def canAddMoreInfo : Boolean = hasPermission(CAN_ADD_MORE_INFO)
  def canAddUrl : Boolean = hasPermission(CAN_ADD_URL)
  def canAddImageUrl : Boolean = hasPermission(CAN_ADD_IMAGE_URL)
  def canAddOpenCorporatesUrl : Boolean = hasPermission(CAN_ADD_OPEN_CORPORATES_URL)
  def canAddCorporateLocation : Boolean = hasPermission(CAN_ADD_CORPORATE_LOCATION)
  def canAddPhysicalLocation : Boolean = hasPermission(CAN_ADD_PHYSICAL_LOCATION)
  def canAddPublicAlias : Boolean = hasPermission(CAN_ADD_PUBLIC_ALIAS)
  def canAddPrivateAlias : Boolean = hasPermission(CAN_ADD_PRIVATE_ALIAS)
  def canAddCounterparty : Boolean = hasPermission(CAN_ADD_COUNTERPARTY)
  def canGetCounterparty : Boolean = hasPermission(CAN_GET_COUNTERPARTY)
  def canDeleteCounterparty : Boolean = hasPermission(CAN_DELETE_COUNTERPARTY)
  def canDeleteCorporateLocation : Boolean = hasPermission(CAN_DELETE_CORPORATE_LOCATION)
  def canDeletePhysicalLocation : Boolean = hasPermission(CAN_DELETE_PHYSICAL_LOCATION)
  def canEditOwnerComment: Boolean = hasPermission(CAN_EDIT_OWNER_COMMENT)
  def canAddComment : Boolean = hasPermission(CAN_ADD_COMMENT)
  def canDeleteComment: Boolean = hasPermission(CAN_DELETE_COMMENT)
  def canAddTag : Boolean = hasPermission(CAN_ADD_TAG)
  def canDeleteTag : Boolean = hasPermission(CAN_DELETE_TAG)
  def canAddImage : Boolean = hasPermission(CAN_ADD_IMAGE)
  def canDeleteImage : Boolean = hasPermission(CAN_DELETE_IMAGE)
  def canAddWhereTag : Boolean = hasPermission(CAN_ADD_WHERE_TAG)
  def canSeeWhereTag : Boolean = hasPermission(CAN_SEE_WHERE_TAG)
  def canDeleteWhereTag : Boolean = hasPermission(CAN_DELETE_WHERE_TAG)
  def canAddTransactionRequestToOwnAccount: Boolean = false //we do not need this field, set this to false.
  def canAddTransactionRequestToAnyAccount: Boolean = hasPermission(CAN_ADD_TRANSACTION_REQUEST_TO_ANY_ACCOUNT)
  def canAddTransactionRequestToBeneficiary: Boolean = hasPermission(CAN_ADD_TRANSACTION_REQUEST_TO_BENEFICIARY)
  def canSeeBankAccountCreditLimit: Boolean = hasPermission(CAN_SEE_BANK_ACCOUNT_CREDIT_LIMIT)
  def canCreateDirectDebit: Boolean = hasPermission(CAN_CREATE_DIRECT_DEBIT)
  def canCreateStandingOrder: Boolean = hasPermission(CAN_CREATE_STANDING_ORDER)
  def canCreateCustomView: Boolean = hasPermission(CAN_CREATE_CUSTOM_VIEW)
  def canDeleteCustomView: Boolean = hasPermission(CAN_DELETE_CUSTOM_VIEW)
  def canUpdateCustomView: Boolean = hasPermission(CAN_UPDATE_CUSTOM_VIEW)
  def canGetCustomView: Boolean = hasPermission(CAN_GET_CUSTOM_VIEW)
}

object ViewDefinition extends ViewDefinition with LongKeyedMetaMapper[ViewDefinition] {
  override def dbIndexes: List[BaseIndex[ViewDefinition]] = UniqueIndex(composite_unique_key) :: Index(isSystem_, view_id) :: Index(bank_id, account_id, view_id) :: super.dbIndexes
  override def beforeDelete = List(
    vd => {
      val conditions: Seq[QueryParam[AccountAccess]] =
        if (vd.isSystem || vd.bank_id.get == null || vd.account_id.get == null)
          Seq(By(AccountAccess.view_id, vd.view_id.get))
        else
          Seq(
            By(AccountAccess.bank_id, vd.bank_id.get),
            By(AccountAccess.account_id, vd.account_id.get),
            By(AccountAccess.view_id, vd.view_id.get)
          )
      AccountAccess.bulkDelete_!!(conditions: _*)
    }
  )

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