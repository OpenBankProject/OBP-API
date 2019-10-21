package code.api.util.migration

import code.api.util.APIUtil
import code.api.util.migration.Migration.{DbFunction, saveLog}
import code.model.dataAccess.ViewImpl
import code.views.system.ViewDefinition
import net.liftweb.mapper.DB
import net.liftweb.util.DefaultConnectionIdentifier

object TableViewDefinition {
  def populate(name: String): Boolean = {
    DbFunction.tableExists(ViewImpl, (DB.use(DefaultConnectionIdentifier){ conn => conn})) match {
      case true =>
        val startDate = System.currentTimeMillis()
        val commitId: String = APIUtil.gitCommit
        val views = ViewImpl.findAll()

        // Make back up
        DbFunction.makeBackUpOfTable(ViewDefinition)
        // Delete all rows at the table
        ViewDefinition.bulkDelete_!!()

        // Insert rows into table "viewdefinition" based on data in the table viewimpl
        val insertedRows =
          for {
            view: ViewImpl <- views
          } yield {
            val viewDefinition = ViewDefinition
              .create
              .isSystem_(view.isSystem)
              .isFirehose_(view.isFirehose)
              .name_(view.name)
              .bank_id(view.bankId.value)
              .account_id(view.accountId.value)
              .view_id(view.viewId.value)
              .description_(view.description)
              .isPublic_(view.isPublic)
              .usePrivateAliasIfOneExists_(view.usePrivateAliasIfOneExists)
              .usePublicAliasIfOneExists_(view.usePublicAliasIfOneExists)
              .hideOtherAccountMetadataIfAlias_(view.hideOtherAccountMetadataIfAlias)
              .canSeeTransactionThisBankAccount_(view.canSeeTransactionThisBankAccount)
              .canSeeTransactionOtherBankAccount_(view.canSeeTransactionOtherBankAccount)
              .canSeeTransactionMetadata_(view.canSeeTransactionMetadata)
              .canSeeTransactionDescription_(view.canSeeTransactionDescription)
              .canSeeTransactionAmount_(view.canSeeTransactionAmount)
              .canSeeTransactionType_(view.canSeeTransactionType)
              .canSeeTransactionCurrency_(view.canSeeTransactionCurrency)
              .canSeeTransactionStartDate_(view.canSeeTransactionStartDate)
              .canSeeTransactionFinishDate_(view.canSeeTransactionFinishDate)
              .canSeeTransactionBalance_(view.canSeeTransactionBalance)
              .canSeeComments_(view.canSeeComments)
              .canSeeOwnerComment_(view.canSeeOwnerComment)
              .canSeeTags_(view.canSeeTags)
              .canSeeImages_(view.canSeeImages)
              .canSeeBankAccountOwners_(view.canSeeBankAccountOwners)
              .canSeeBankAccountType_(view.canSeeBankAccountType)
              .canSeeBankAccountBalance_(view.canSeeBankAccountBalance)
              .canSeeBankAccountCurrency_(view.canSeeBankAccountCurrency)

            viewDefinition
              .canSeeBankAccountLabel_(view.canSeeBankAccountLabel)
              .canSeeBankAccountNationalIdentifier_(view.canSeeBankAccountNationalIdentifier)
              .canSeeBankAccountSwift_bic_(view.canSeeBankAccountSwift_bic)
              .canSeeBankAccountIban_(view.canSeeBankAccountIban)
              .canSeeBankAccountNumber_(view.canSeeBankAccountNumber)
              .canSeeBankAccountBankName_(view.canSeeBankAccountBankName)
              .canSeeBankAccountBankPermalink_(view.canSeeBankAccountBankPermalink)
              .canSeeOtherAccountNationalIdentifier_(view.canSeeOtherAccountNationalIdentifier)
              .canSeeOtherAccountSWIFT_BIC_(view.canSeeOtherAccountSWIFT_BIC)
              .canSeeOtherAccountIBAN_(view.canSeeOtherAccountIBAN)
              .canSeeOtherAccountBankName_(view.canSeeOtherAccountBankName)
              .canSeeOtherAccountNumber_(view.canSeeOtherAccountNumber)
              .canSeeOtherAccountMetadata_(view.canSeeOtherAccountMetadata)
              .canSeeOtherAccountKind_(view.canSeeOtherAccountKind)
              .canSeeMoreInfo_(view.canSeeMoreInfo)
              .canSeeUrl_(view.canSeeUrl)
              .canSeeImageUrl_(view.canSeeImageUrl)
              .canSeeOpenCorporatesUrl_(view.canSeeOpenCorporatesUrl)
              .canSeeCorporateLocation_(view.canSeeCorporateLocation)
              .canSeePhysicalLocation_(view.canSeePhysicalLocation)
              .canSeePublicAlias_(view.canSeePublicAlias)
              .canSeePrivateAlias_(view.canSeePrivateAlias)
              .canAddMoreInfo_(view.canAddMoreInfo)
              .canAddURL_(view.canAddURL)
              .canAddImageURL_(view.canAddImageURL)
              .canAddOpenCorporatesUrl_(view.canAddOpenCorporatesUrl)
              .canAddCorporateLocation_(view.canAddCorporateLocation)
              .canAddPhysicalLocation_(view.canAddPhysicalLocation)
              .canAddPublicAlias_(view.canAddPublicAlias)
              .canAddPrivateAlias_(view.canAddPrivateAlias)

            viewDefinition
              .canAddCounterparty_(view.canAddCounterparty)
              .canDeleteCorporateLocation_(view.canDeleteCorporateLocation)
              .canDeletePhysicalLocation_(view.canDeletePhysicalLocation)
              .canEditOwnerComment_(view.canEditOwnerComment)
              .canAddComment_(view.canAddComment)
              .canDeleteComment_(view.canDeleteComment)
              .canAddTag_(view.canAddTag)
              .canDeleteTag_(view.canDeleteTag)
              .canAddImage_(view.canAddImage)
              .canDeleteImage_(view.canDeleteImage)
              .canAddWhereTag_(view.canAddWhereTag)
              .canSeeWhereTag_(view.canSeeWhereTag)
              .canDeleteWhereTag_(view.canDeleteWhereTag)
              .canSeeBankRoutingScheme_(view.canSeeBankRoutingScheme)
              .canSeeBankRoutingAddress_(view.canSeeBankRoutingAddress)
              .canSeeBankAccountRoutingScheme_(view.canSeeBankAccountRoutingScheme)
              .canSeeBankAccountRoutingAddress_(view.canSeeBankAccountRoutingAddress)
              .canSeeOtherBankRoutingScheme_(view.canSeeOtherBankRoutingScheme)
              .canSeeOtherBankRoutingAddress_(view.canSeeOtherBankRoutingAddress)
              .canSeeOtherAccountRoutingScheme_(view.canSeeOtherAccountRoutingScheme)
              .canSeeOtherAccountRoutingAddress_(view.canSeeOtherAccountRoutingAddress)
              .canAddTransactionRequestToOwnAccount_(view.canAddTransactionRequestToOwnAccount)
              .canAddTransactionRequestToAnyAccount_(view.canAddTransactionRequestToAnyAccount)
              .save
          }
        val isSuccessful = insertedRows.forall(_ == true)
        val viewDefinition = ViewDefinition.findAll()
        val viewDefinitionSize = viewDefinition.size
        val endDate = System.currentTimeMillis()

        //  (${viewDefinition.map(_.id).mkString(",")});

        val comment: String =
          s"""View implementation size: ${views.size};
             |View definition size: $viewDefinitionSize;
             |Duration: ${endDate - startDate} ms;
             |Primary keys of the inserted rows: NOPE too risky.
             """.stripMargin
        saveLog(name, commitId, isSuccessful, startDate, endDate, comment)
        isSuccessful
      case false =>
        val startDate = System.currentTimeMillis()
        val commitId: String = APIUtil.gitCommit
        val isSuccessful = false
        val endDate = System.currentTimeMillis()
        val comment: String =
          s"""View implementation does not exist!;
             """.stripMargin
        saveLog(name, commitId, isSuccessful, startDate, endDate, comment)
        isSuccessful
    }
  }
}
