package code.views.system

import code.api.Constant._
import code.api.util.APIUtil.{isValidCustomViewId, isValidSystemViewId}
import code.api.util.DoobieUtil
import code.api.util.ErrorMessages.{CreateSystemViewError, InvalidCustomViewFormat, InvalidSystemViewFormat}
import com.openbankproject.commons.model._
import doobie._
import doobie.implicits._
import doobie.implicits.javasql._
import net.liftweb.common.{Box, Empty, Full}

/**
 * One view: what it is allowed to show and do on an account.
 *
 * A SYSTEM view has a null bank and account and is scoped by view id alone; a CUSTOM view belongs
 * to one account and is scoped by all three. Reads therefore have to use IS NULL rather than an
 * equality test, and uniqueness is enforced through the composed `composite_unique_key` rather than
 * a column tuple - SQL treats NULLs as distinct, so a unique index over the three columns would not
 * stop two system views sharing a view id.
 *
 * `viewPrimaryKey` stays on the row because the ViewPermission rows reference a view by it.
 *
 * Every can* accessor reads the ViewPermission table through `allowed_actions`; the row carries no
 * permission state of its own. `canGrantAccessToViews_` / `canRevokeAccessToViews_` mirror two dead
 * columns and are read by nothing.
 */
case class ViewDefinition(
  viewPrimaryKey: Long = 0L,
  name_ : String = "",
  description_ : String = "",
  bank_id: String = null,
  account_id: String = null,
  view_id: String = "",
  composite_unique_key: String = "",
  metadataView_ : String = "",
  isSystem_ : Boolean = false,
  isPublic_ : Boolean = false,
  isFirehose_ : Boolean = true,
  usePrivateAliasIfOneExists_ : Boolean = false,
  usePublicAliasIfOneExists_ : Boolean = false,
  hideOtherAccountMetadataIfAlias_ : Boolean = false,
  canGrantAccessToViews_ : String = "",
  canRevokeAccessToViews_ : String = ""
) extends View {

  /**
   * Returns this view with the specification applied, and resets its permission rows as a side
   * effect.
   *
   * Replaces the former setFromViewData / createViewAndPermissions pair, whose bodies were
   * identical. The permission reset needs no primary key - it scopes by isSystem plus the bank,
   * account and view ids - so it still works on a row that has not been written yet, which is when
   * the create paths call it.
   *
   * ORDER MATTERS at the call sites: createSystemView applies the specification BEFORE setting
   * isSystem, so the reset takes the custom-view branch with a null bank and account. That lands
   * the permission rows with both id columns NULL, which is exactly where the system branch would
   * have put them. Preserved rather than tidied.
   */
  def withViewData(viewSpecification: ViewSpecification): ViewDefinition = {
    val (usePublic, usePrivate) =
      if (viewSpecification.which_alias_to_use == "public") (true, false)
      else if (viewSpecification.which_alias_to_use == "private") (false, true)
      else (false, false)

    val updated = copy(
      usePublicAliasIfOneExists_ = usePublic,
      usePrivateAliasIfOneExists_ = usePrivate,
      hideOtherAccountMetadataIfAlias_ = viewSpecification.hide_metadata_if_alias_used,
      description_ = viewSpecification.description,
      isPublic_ = viewSpecification.is_public,
      isFirehose_ = viewSpecification.is_firehose.getOrElse(false),
      metadataView_ = viewSpecification.metadata_view)

    ViewPermission.resetViewPermissions(
      updated,
      viewSpecification.allowed_actions,
      viewSpecification.can_grant_access_to_views.getOrElse(Nil),
      viewSpecification.can_revoke_access_to_views.getOrElse(Nil)
    )

    updated
  }

  /**
   * The `View` trait's mutating entry point. It returns Unit, which on an immutable row can only
   * carry the permission-reset side effect - the updated field values are lost. Every call site
   * uses withViewData above and writes the row it returns; this exists to satisfy the trait.
   */
  override def createViewAndPermissions(viewSpecification: ViewSpecification): Unit = {
    withViewData(viewSpecification)
    ()
  }

  def deleteViewPermissions: List[Boolean] =
    ViewPermission.findViewPermissions(this).map(ViewPermission.deleteRow)

  def id: Long = viewPrimaryKey
  def viewId : ViewId = ViewId(view_id)
  
  @deprecated("This field is not used in api code anymore","13-12-2019")
  def viewIdInternal: String = composite_unique_key
  //if metadataView_ = null or empty, we need use the current view's viewId.
  def metadataView = if (metadataView_ == null || metadataView_ == "") view_id else metadataView_
  def users : List[User] = Nil
  def bankId = BankId(bank_id)
  def accountId = AccountId(account_id)
  def name: String = name_
  def description : String = description_
  def isPublic : Boolean = isPublic_
  def isPrivate : Boolean = !isPublic_
  def isFirehose : Boolean = isFirehose_
  def isSystem: Boolean = isSystem_
  //the view settings
  def usePrivateAliasIfOneExists: Boolean = usePrivateAliasIfOneExists_
  def usePublicAliasIfOneExists: Boolean = usePublicAliasIfOneExists_
  def hideOtherAccountMetadataIfAlias: Boolean = hideOtherAccountMetadataIfAlias_

  override def allowed_actions : List[String] = ViewPermission.findViewPermissions(this).map(_.permission).distinct

  override def canGrantAccessToViews : Option[List[String]] = {
   ViewPermission.findViewPermission(this, CAN_GRANT_ACCESS_TO_VIEWS).flatMap(vp => 
    {
      vp.extraData.filter(_.nonEmpty).map(_.split(",").toList.map(_.trim))
    })
  }
  
  override def canRevokeAccessToViews : Option[List[String]] = {
    ViewPermission.findViewPermission(this, CAN_REVOKE_ACCESS_TO_VIEWS).flatMap(vp =>
    {
      vp.extraData.filter(_.nonEmpty).map(_.split(",").toList.map(_.trim))
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

object ViewDefinition {

  private val selectColumns =
    fr"""SELECT id_, name_, description_, bank_id, account_id, view_id, composite_unique_key,
                metadataview_, issystem_, ispublic_, isfirehose_, useprivatealiasifoneexists_,
                usepublicaliasifoneexists_, hideotheraccountmetadataifalias_,
                cangrantaccesstoviews_, canrevokeaccesstoviews_
         FROM viewdefinition"""

  private type Row = (Long, Option[String], Option[String], Option[String], Option[String],
    Option[String], Option[String], Option[String], Option[Boolean], Option[Boolean],
    Option[Boolean], Option[Boolean], Option[Boolean], Option[Boolean], Option[String],
    Option[String])

  private def fromRow(row: Row): ViewDefinition = row match {
    case (id, name, description, bankId, accountId, viewId, compositeUniqueKey, metadataView,
          isSystem, isPublic, isFirehose, usePrivateAlias, usePublicAlias, hideOtherMetadata,
          canGrantAccessToViews, canRevokeAccessToViews) =>
      ViewDefinition(id, name.orNull, description.orNull, bankId.orNull, accountId.orNull,
        viewId.orNull, compositeUniqueKey.orNull, metadataView.orNull,
        // A NULL flag reads back as false, which is what Mapper did - and NOT as the field's
        // defaultValue. MappedBoolean seeds `data` with defaultValue for a NEW instance, but a
        // NULL column sets data = Empty on read and the getter is `data openOr false`. isFirehose_
        // is the one where those two differ (its defaultValue is true), so reading it as true
        // would widen a permission flag that Lift read as false. The case-class default above
        // still carries true, which is the new-instance half of the same behaviour.
        isSystem.getOrElse(false), isPublic.getOrElse(false), isFirehose.getOrElse(false),
        usePrivateAlias.getOrElse(false), usePublicAlias.getOrElse(false),
        hideOtherMetadata.getOrElse(false), canGrantAccessToViews.orNull,
        canRevokeAccessToViews.orNull)
  }

  private def query(condition: Fragment): List[ViewDefinition] =
    DoobieUtil.runQuery((selectColumns ++ condition).query[Row].to[List]).map(fromRow)

  private def opt(value: String): Option[String] = Option(value)

  private def one(condition: Fragment): Box[ViewDefinition] =
    query(condition ++ fr"ORDER BY id_ ASC LIMIT 1").headOption match {
      case Some(row) => Full(row)
      case None => Empty
    }

  private def count(condition: Fragment): Long =
    DoobieUtil.runQuery(
      (fr"SELECT COUNT(*) FROM viewdefinition" ++ condition).query[Long].unique)

  /**
   * The three checks Mapper ran in beforeSave, in the same order.
   *
   * NOTE the last one compares the FIELD, not its value: `bank_id == null` was written against the
   * Mapper field object, which is never null, so the sanity check has never fired. Preserved
   * verbatim rather than corrected under a storage swap - fixing it would start rejecting rows
   * that are being written today.
   */
  private def validateBeforeSave(row: ViewDefinition): Unit = {
    if (row.isSystem_ && !isValidSystemViewId(row.view_id)) {
      throw new RuntimeException(InvalidSystemViewFormat + s"Current view_id (${row.view_id})")
    }
    if (!row.isSystem_ && !isValidCustomViewId(row.view_id)) {
      throw new RuntimeException(InvalidCustomViewFormat + s"Current view_id (${row.view_id})")
    }
    // Never true: this tests the field, not the value. See the note above.
    if (!row.isSystem_ && (false)) {
      throw new RuntimeException(CreateSystemViewError +
        s"Current view.isSystem${row.isSystem_}, bank_id${row.bank_id}, account_id${row.account_id}")
    }
  }

  /** A system view is scoped by view id alone, so its bank and account really are SQL NULL. */
  private def isNullOr(column: Fragment, value: String): Fragment =
    Option(value) match {
      case Some(v) => column ++ fr" = $v"
      case None => column ++ fr" IS NULL"
    }

  def findSystemView(viewId: String): Box[ViewDefinition] =
    one(fr"""WHERE bank_id IS NULL AND account_id IS NULL AND issystem_ = true
               AND view_id = ${opt(viewId)}""")

  def getSystemViews(): List[ViewDefinition] = query(fr"WHERE issystem_ = true")

  def findCustomView(bankId: String, accountId: String, viewId: String): Box[ViewDefinition] =
    one(fr"WHERE " ++ isNullOr(fr"bank_id", bankId) ++ fr"AND" ++
      isNullOr(fr"account_id", accountId) ++
      fr"AND issystem_ = false AND view_id = ${opt(viewId)}")

  def getCustomViews(): List[ViewDefinition] = query(fr"WHERE issystem_ = false")

  def findByPrimaryKey(viewPrimaryKey: Long): Box[ViewDefinition] =
    one(fr"WHERE id_ = $viewPrimaryKey")

  @deprecated("This is method only used for migration stuff, please use @findCustomView and @findSystemView instead.","13-12-2019")
  def findByUniqueKey(bankId: String, accountId: String, viewId: String): Box[ViewDefinition] =
    one(fr"WHERE composite_unique_key = ${opt(getUniqueKey(bankId, accountId, viewId))}")

  /** Every view of one account: its own custom views, plus nothing else. */
  def findAllByBankAccount(bankId: String, accountId: String): List[ViewDefinition] =
    query(fr"WHERE " ++ isNullOr(fr"bank_id", bankId) ++ fr"AND" ++
      isNullOr(fr"account_id", accountId))

  /** System views scoped to one bank (bank set, account null). */
  def findAllBankSystemViews(bankId: String): List[ViewDefinition] =
    query(fr"WHERE " ++ isNullOr(fr"bank_id", bankId) ++
      fr"AND account_id IS NULL AND issystem_ = true")

  /** System views scoped to no bank at all. */
  def findAllSandboxSystemViews(): List[ViewDefinition] =
    query(fr"WHERE bank_id IS NULL AND account_id IS NULL AND issystem_ = true")

  /** Views that name a bank, an account AND a view id - what the system-to-custom migration walks. */
  def findAllFullyScoped(): List[ViewDefinition] =
    query(fr"""WHERE bank_id IS NOT NULL AND account_id IS NOT NULL AND view_id IS NOT NULL""")

  /** System views scoped to a bank but no account. */
  def findAllBankScopedSystemViews(): List[ViewDefinition] =
    query(fr"WHERE bank_id IS NOT NULL AND account_id IS NULL AND issystem_ = true")

  def setIsSystem(viewPrimaryKey: Long, isSystem: Boolean): Boolean =
    DoobieUtil.runUpdate(
      sql"""UPDATE viewdefinition SET issystem_ = $isSystem,
              updatedat = ${new java.sql.Timestamp(System.currentTimeMillis())}
            WHERE id_ = $viewPrimaryKey"""
        .update.run) > 0

  def findAll(): List[ViewDefinition] = query(Fragment.empty)

  def findAllPublic(): List[ViewDefinition] = query(fr"WHERE ispublic_ = true")

  def findAllPublicByBankAndSystem(bankId: String, isSystem: Boolean): List[ViewDefinition] =
    query(fr"WHERE ispublic_ = true AND " ++ isNullOr(fr"bank_id", bankId) ++
      fr"AND issystem_ = $isSystem")

  def findAllPublicBySystem(isSystem: Boolean): List[ViewDefinition] =
    query(fr"WHERE ispublic_ = true AND issystem_ = $isSystem")

  def countSystemView(viewId: String): Long =
    count(fr"""WHERE view_id = ${opt(viewId)} AND bank_id IS NULL AND account_id IS NULL""")

  /** Rows matching one exact (bank, account, view) triple, whatever their isSystem flag. */
  def countByBankAccountView(bankId: String, accountId: String, viewId: String): Long =
    count(fr"WHERE " ++ isNullOr(fr"bank_id", bankId) ++ fr"AND" ++
      isNullOr(fr"account_id", accountId) ++ fr"AND view_id = ${opt(viewId)}")

  def countCustomView(bankId: String, accountId: String, viewId: String): Long =
    count(fr"WHERE view_id = ${opt(viewId)} AND " ++ isNullOr(fr"bank_id", bankId) ++
      fr"AND" ++ isNullOr(fr"account_id", accountId))

  /**
   * Writes a view, computing the composite key and running the same validation Mapper's beforeSave
   * ran. The unique index on the composite key is what rejects a concurrent duplicate - the write
   * is deliberately not preceded by a read.
   */
  def insert(row: ViewDefinition): ViewDefinition = {
    validateBeforeSave(row)
    val compositeUniqueKey = getUniqueKey(row.bank_id, row.account_id, row.view_id)
    val now = new java.sql.Timestamp(System.currentTimeMillis())
    val id = DoobieUtil.runUpdate(
      sql"""INSERT INTO viewdefinition
            (name_, description_, bank_id, account_id, view_id, composite_unique_key,
             metadataview_, issystem_, ispublic_, isfirehose_, useprivatealiasifoneexists_,
             usepublicaliasifoneexists_, hideotheraccountmetadataifalias_, cangrantaccesstoviews_,
             canrevokeaccesstoviews_, createdat, updatedat)
            VALUES (${opt(row.name_)}, ${opt(row.description_)}, ${opt(row.bank_id)},
             ${opt(row.account_id)}, ${opt(row.view_id)}, ${opt(compositeUniqueKey)},
             ${opt(row.metadataView_)}, ${row.isSystem_}, ${row.isPublic_}, ${row.isFirehose_},
             ${row.usePrivateAliasIfOneExists_}, ${row.usePublicAliasIfOneExists_},
             ${row.hideOtherAccountMetadataIfAlias_}, ${opt(row.canGrantAccessToViews_)},
             ${opt(row.canRevokeAccessToViews_)}, $now, $now)"""
        .update.withUniqueGeneratedKeys[Long]("id_"))
    row.copy(viewPrimaryKey = id, composite_unique_key = compositeUniqueKey)
  }

  /** Rewrites an existing view by its primary key, with the same validation as insert. */
  def update(row: ViewDefinition): ViewDefinition = {
    validateBeforeSave(row)
    val compositeUniqueKey = getUniqueKey(row.bank_id, row.account_id, row.view_id)
    val now = new java.sql.Timestamp(System.currentTimeMillis())
    DoobieUtil.runUpdate(
      sql"""UPDATE viewdefinition
            SET name_ = ${opt(row.name_)}, description_ = ${opt(row.description_)},
                bank_id = ${opt(row.bank_id)}, account_id = ${opt(row.account_id)},
                view_id = ${opt(row.view_id)}, composite_unique_key = ${opt(compositeUniqueKey)},
                metadataview_ = ${opt(row.metadataView_)}, issystem_ = ${row.isSystem_},
                ispublic_ = ${row.isPublic_}, isfirehose_ = ${row.isFirehose_},
                useprivatealiasifoneexists_ = ${row.usePrivateAliasIfOneExists_},
                usepublicaliasifoneexists_ = ${row.usePublicAliasIfOneExists_},
                hideotheraccountmetadataifalias_ = ${row.hideOtherAccountMetadataIfAlias_},
                cangrantaccesstoviews_ = ${opt(row.canGrantAccessToViews_)},
                canrevokeaccesstoviews_ = ${opt(row.canRevokeAccessToViews_)}, updatedat = $now
            WHERE id_ = ${row.viewPrimaryKey}"""
        .update.run)
    row.copy(composite_unique_key = compositeUniqueKey)
  }

  /**
   * Deletes a view and the account access that referenced it, as Mapper's beforeDelete did.
   *
   * A system view (or one whose bank/account is null) is scoped by view id alone; a custom view by
   * all three. Same split as before.
   */
  def delete(row: ViewDefinition): Boolean = {
    if (row.isSystem || row.bank_id == null || row.account_id == null)
      AccountAccess.deleteByViewId(row.view_id)
    else
      AccountAccess.deleteByBankIdAccountIdViewId(
        BankId(row.bank_id), AccountId(row.account_id), ViewId(row.view_id))
    DoobieUtil.runUpdate(
      sql"DELETE FROM viewdefinition WHERE id_ = ${row.viewPrimaryKey}".update.run) > 0
  }

  /**
   * Bulk delete by account. Does NOT touch AccountAccess - Mapper's bulkDelete_!! bypassed the
   * beforeDelete hook too, and the one caller removes the access rows itself.
   */
  def deleteByBankAccount(bankId: String, accountId: String): Boolean = {
    DoobieUtil.runUpdate(
      (fr"DELETE FROM viewdefinition WHERE " ++ isNullOr(fr"bank_id", bankId) ++ fr"AND" ++
        isNullOr(fr"account_id", accountId)).update.run)
    true
  }

  def deleteAll(): Boolean = {
    DoobieUtil.runUpdate(sql"DELETE FROM viewdefinition".update.run)
    true
  }

  @deprecated("This is method only used for migration stuff, do not use api code.","13-12-2019")
  def getUniqueKey(bankId: String, accountId: String, viewId: String) = List(bankId, accountId, viewId).mkString("|","|--|","|")
}
