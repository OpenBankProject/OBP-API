package code.views.system

import code.api.util.APIUtil.{isValidCustomViewId, isValidCustomViewName, isValidSystemViewId}
import code.api.util.ErrorMessages.{CreateSystemViewError, InvalidCustomViewFormat, InvalidSystemViewFormat}
import code.util.{AccountIdString, UUIDString}
import com.openbankproject.commons.model._
import net.liftweb.common.Box
import net.liftweb.common.Box.tryo
import net.liftweb.mapper._

import scala.collection.immutable.List


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
  object canAddTransactionRequestToOwnAccount_ extends MappedBoolean(this){
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
  object canSeeViewsWithPermissionsForAllUsers_ extends MappedBoolean(this){
    override def defaultValue = false
  }
  object canSeeViewsWithPermissionsForOneUser_ extends MappedBoolean(this){
    override def defaultValue = false
  }

  //Important! If you add a field, be sure to handle it here in this function
  def setFromViewData(viewData : ViewSpecification) = {
    if(viewData.which_alias_to_use == "public"){
      usePublicAliasIfOneExists_(true)
      usePrivateAliasIfOneExists_(false)
    } else if(viewData.which_alias_to_use == "private"){
      usePublicAliasIfOneExists_(false)
      usePrivateAliasIfOneExists_(true)
    } else {
      usePublicAliasIfOneExists_(false)
      usePrivateAliasIfOneExists_(false)
    }

    hideOtherAccountMetadataIfAlias_(viewData.hide_metadata_if_alias_used)
    description_(viewData.description)
    isPublic_(viewData.is_public)
    isFirehose_(viewData.is_firehose.getOrElse(false))
    metadataView_(viewData.metadata_view)
    
    val actions = viewData.allowed_actions

    if (isSystem) { //The following are admin permissions, only system views are allowed to use them.
      canGrantAccessToCustomViews_(actions.exists(_ == "can_grant_access_to_custom_views"))
      canRevokeAccessToCustomViews_(actions.exists(_ == "can_revoke_access_to_custom_views"))
      canGrantAccessToViews_(viewData.can_grant_access_to_views.getOrElse(Nil).mkString(","))
      canRevokeAccessToViews_(viewData.can_revoke_access_to_views.getOrElse(Nil).mkString(","))
      canCreateCustomView_(actions.exists(_ == "can_create_custom_view"))
      canDeleteCustomView_(actions.exists(_ == "can_delete_custom_view"))
      canUpdateCustomView_(actions.exists(_ == "can_update_custom_view"))
    }
    
    canSeeTransactionThisBankAccount_(actions.exists(_ =="can_see_transaction_this_bank_account"))
    canSeeTransactionOtherBankAccount_(actions.exists(_ =="can_see_transaction_other_bank_account"))
    canSeeTransactionMetadata_(actions.exists(_ == "can_see_transaction_metadata"))
    canSeeTransactionDescription_(actions.exists(a => a == "can_see_transaction_label" || a == "can_see_transaction_description"))
    canSeeTransactionAmount_(actions.exists(_ == "can_see_transaction_amount"))
    canSeeTransactionType_(actions.exists(_ == "can_see_transaction_type"))
    canSeeTransactionCurrency_(actions.exists(_ == "can_see_transaction_currency"))
    canSeeTransactionStartDate_(actions.exists(_ == "can_see_transaction_start_date"))
    canSeeTransactionFinishDate_(actions.exists(_ == "can_see_transaction_finish_date"))
    canSeeTransactionBalance_(actions.exists(_ == "can_see_transaction_balance"))
    canSeeComments_(actions.exists(_ == "can_see_comments"))
    canSeeOwnerComment_(actions.exists(_ == "can_see_narrative"))
    canSeeTags_(actions.exists(_ == "can_see_tags"))
    canSeeImages_(actions.exists(_ == "can_see_images"))
    canSeeBankAccountOwners_(actions.exists(_ == "can_see_bank_account_owners"))
    canSeeBankAccountType_(actions.exists(_ == "can_see_bank_account_type"))
    canSeeBankAccountBalance_(actions.exists(_ == "can_see_bank_account_balance"))
    canQueryAvailableFunds_(actions.exists(_ == "can_query_available_funds"))
    canSeeBankAccountCurrency_(actions.exists(_ == "can_see_bank_account_currency"))
    canSeeBankAccountLabel_(actions.exists(_ == "can_see_bank_account_label"))
    canSeeBankAccountNationalIdentifier_(actions.exists(_ == "can_see_bank_account_national_identifier"))
    canSeeBankAccountSwift_bic_(actions.exists(_ == "can_see_bank_account_swift_bic"))
    canSeeBankAccountIban_(actions.exists(_ == "can_see_bank_account_iban"))
    canSeeBankAccountNumber_(actions.exists(_ == "can_see_bank_account_number"))
    canSeeBankAccountBankName_(actions.exists(_ == "can_see_bank_account_bank_name"))
    canSeeBankAccountBankPermalink_(actions.exists(_ == "can_see_bank_account_bank_permalink"))
    canSeeBankRoutingScheme_(actions.exists(_ == "can_see_bank_routing_scheme"))
    canSeeBankRoutingAddress_(actions.exists(_ == "can_see_bank_routing_address"))
    canSeeBankAccountRoutingScheme_(actions.exists(_ == "can_see_bank_account_routing_scheme"))
    canSeeBankAccountRoutingAddress_(actions.exists(_ == "can_see_bank_account_routing_address"))
    canSeeOtherAccountNationalIdentifier_(actions.exists(_ == "can_see_other_account_national_identifier"))
    canSeeOtherAccountSWIFT_BIC_(actions.exists(_ == "can_see_other_account_swift_bic"))
    canSeeOtherAccountIBAN_(actions.exists(_ == "can_see_other_account_iban"))
    canSeeOtherAccountBankName_(actions.exists(_ == "can_see_other_account_bank_name"))
    canSeeOtherAccountNumber_(actions.exists(_ == "can_see_other_account_number"))
    canSeeOtherAccountMetadata_(actions.exists(_ == "can_see_other_account_metadata"))
    canSeeOtherAccountKind_(actions.exists(_ == "can_see_other_account_kind"))
    canSeeOtherBankRoutingScheme_(actions.exists(_ == "can_see_other_bank_routing_scheme"))
    canSeeOtherBankRoutingAddress_(actions.exists(_ == "can_see_other_bank_routing_address"))
    canSeeOtherAccountRoutingScheme_(actions.exists(_ == "can_see_other_account_routing_scheme"))
    canSeeOtherAccountRoutingAddress_(actions.exists(_ == "can_see_other_account_routing_address"))
    canSeeMoreInfo_(actions.exists(_ == "can_see_more_info"))
    canSeeUrl_(actions.exists(_ == "can_see_url"))
    canSeeImageUrl_(actions.exists(_ == "can_see_image_url"))
    canSeeOpenCorporatesUrl_(actions.exists(_ == "can_see_open_corporates_url"))
    canSeeCorporateLocation_(actions.exists(_ == "can_see_corporate_location"))
    canSeePhysicalLocation_(actions.exists(_ == "can_see_physical_location"))
    canSeePublicAlias_(actions.exists(_ == "can_see_public_alias"))
    canSeePrivateAlias_(actions.exists(_ == "can_see_private_alias"))
    canAddMoreInfo_(actions.exists(_ == "can_add_more_info"))
    canAddURL_(actions.exists(_ == "can_add_url"))
    canAddImageURL_(actions.exists(_ == "can_add_image_url"))
    canAddOpenCorporatesUrl_(actions.exists(_ == "can_add_open_corporates_url"))
    canAddCorporateLocation_(actions.exists(_ == "can_add_corporate_location"))
    canAddPhysicalLocation_(actions.exists(_ == "can_add_physical_location"))
    canAddPublicAlias_(actions.exists(_ == "can_add_public_alias"))
    canAddPrivateAlias_(actions.exists(_ == "can_add_private_alias"))
    canAddCounterparty_(actions.exists(_ == "can_add_counterparty"))
    canDeleteCounterparty_(actions.exists(_ == "can_delete_counterparty"))
    canGetCounterparty_(actions.exists(_ == "can_get_counterparty"))
    canDeleteCorporateLocation_(actions.exists(_ == "can_delete_corporate_location"))
    canDeletePhysicalLocation_(actions.exists(_ == "can_delete_physical_location"))
    canEditOwnerComment_(actions.exists(_ == "can_edit_narrative"))
    canAddComment_(actions.exists(_ == "can_add_comment"))
    canDeleteComment_(actions.exists(_ == "can_delete_comment"))
    canAddTag_(actions.exists(_ == "can_add_tag"))
    canDeleteTag_(actions.exists(_ == "can_delete_tag"))
    canAddImage_(actions.exists(_ == "can_add_image"))
    canDeleteImage_(actions.exists(_ == "can_delete_image"))
    canAddWhereTag_(actions.exists(_ == "can_add_where_tag"))
    canSeeWhereTag_(actions.exists(_ == "can_see_where_tag"))
    canDeleteWhereTag_(actions.exists(_ == "can_delete_where_tag"))
    canAddTransactionRequestToOwnAccount_(actions.exists(_ == "can_add_transaction_request_to_own_account")) //added following two for payments
    canAddTransactionRequestToAnyAccount_(actions.exists(_ == "can_add_transaction_request_to_any_account"))
    canSeeBankAccountCreditLimit_(actions.exists(_ == "can_see_bank_account_credit_limit"))
    canCreateDirectDebit_(actions.exists(_ == "can_create_direct_debit"))
    canCreateStandingOrder_(actions.exists(_ == "can_create_standing_order"))
    canSeeTransactionRequests_(actions.exists(_ == "can_see_transaction_requests"))
    canSeeTransactionRequestTypes_(actions.exists(_ == "can_see_transaction_request_types"))
    canUpdateBankAccountLabel_(actions.exists(_ == "can_update_bank_account_label"))
    canSeeAvailableViewsForBankAccount_(actions.exists(_ == "can_see_available_views_for_bank_account"))
    canSeeViewsWithPermissionsForAllUsers_(actions.exists(_ == "can_see_views_with_permissions_for_all_users")) 
    canSeeViewsWithPermissionsForOneUser_(actions.exists(_ == "can_see_views_with_permissions_for_one_user"))
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

  //This current view can grant access to other views.
  override def canGrantAccessToViews : Option[List[String]] = {
    canGrantAccessToViews_.get == null || canGrantAccessToViews_.get.isEmpty() match {
      case true => None
      case _ => Some(canGrantAccessToViews_.get.split(",").toList.map(_.trim))
    }
  }

  def canGrantAccessToCustomViews : Boolean = canGrantAccessToCustomViews_.get
  
  //the current view can revoke access to other views.
  override def canRevokeAccessToViews : Option[List[String]] = {
    canRevokeAccessToViews_.get == null || canRevokeAccessToViews_.get.isEmpty()  match {
      case true => None
      case _ => Some(canRevokeAccessToViews_.get.split(",").toList.map(_.trim))
    }
  }
  override def canRevokeAccessToCustomViews : Boolean = canRevokeAccessToCustomViews_.get
  
  //reading access

  //transaction fields
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

  //transaction metadata
  def canSeeComments: Boolean = canSeeComments_.get
  def canSeeOwnerComment: Boolean = canSeeOwnerComment_.get
  def canSeeTags : Boolean = canSeeTags_.get
  def canSeeImages : Boolean = canSeeImages_.get

  //Bank account fields
  def canSeeAvailableViewsForBankAccount : Boolean = canSeeAvailableViewsForBankAccount_.get
  def canSeeBankAccountOwners : Boolean = canSeeBankAccountOwners_.get
  def canSeeBankAccountType : Boolean = canSeeBankAccountType_.get
  def canSeeBankAccountBalance : Boolean = canSeeBankAccountBalance_.get
  def canSeeBankAccountCurrency : Boolean = canSeeBankAccountCurrency_.get
  def canQueryAvailableFunds : Boolean = canQueryAvailableFunds_.get
  def canSeeBankAccountLabel : Boolean = canSeeBankAccountLabel_.get
  def canUpdateBankAccountLabel : Boolean = canUpdateBankAccountLabel_.get
  def canSeeBankAccountNationalIdentifier : Boolean = canSeeBankAccountNationalIdentifier_.get
  def canSeeBankAccountSwift_bic : Boolean = canSeeBankAccountSwift_bic_.get
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

  //other bank account fields
  def canSeeOtherAccountNationalIdentifier : Boolean = canSeeOtherAccountNationalIdentifier_.get
  def canSeeOtherAccountSWIFT_BIC : Boolean = canSeeOtherAccountSWIFT_BIC_.get
  def canSeeOtherAccountIBAN : Boolean = canSeeOtherAccountIBAN_.get
  def canSeeOtherAccountBankName : Boolean = canSeeOtherAccountBankName_.get
  def canSeeOtherAccountNumber : Boolean = canSeeOtherAccountNumber_.get
  def canSeeOtherAccountMetadata : Boolean = canSeeOtherAccountMetadata_.get
  def canSeeOtherAccountKind : Boolean = canSeeOtherAccountKind_.get
  def canSeeOtherBankRoutingScheme : Boolean = canSeeOtherBankRoutingScheme_.get
  def canSeeOtherBankRoutingAddress : Boolean = canSeeOtherBankRoutingAddress_.get
  def canSeeOtherAccountRoutingScheme : Boolean = canSeeOtherAccountRoutingScheme_.get
  def canSeeOtherAccountRoutingAddress : Boolean = canSeeOtherAccountRoutingAddress_.get

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
  def canAddCounterparty : Boolean = canAddCounterparty_.get
  def canGetCounterparty : Boolean = canGetCounterparty_.get
  def canDeleteCounterparty : Boolean = canDeleteCounterparty_.get
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

  def canAddTransactionRequestToOwnAccount: Boolean = canAddTransactionRequestToOwnAccount_.get //added following two for payments
  def canAddTransactionRequestToAnyAccount: Boolean = canAddTransactionRequestToAnyAccount_.get
  def canSeeBankAccountCreditLimit: Boolean = canSeeBankAccountCreditLimit_.get
  
  def canCreateDirectDebit: Boolean = canCreateDirectDebit_.get
  def canCreateStandingOrder: Boolean = canCreateStandingOrder_.get
  def canCreateCustomView: Boolean = canCreateCustomView_.get
  def canDeleteCustomView: Boolean = canDeleteCustomView_.get
  def canUpdateCustomView: Boolean = canUpdateCustomView_.get
  //TODO: if you add new permissions here, remember to set them wherever views are created
  // (e.g. BankAccountCreationDispatcher)
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