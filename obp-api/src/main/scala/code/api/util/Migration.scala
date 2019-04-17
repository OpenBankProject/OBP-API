package code.api.util

import code.api.util.APIUtil.getPropsAsBoolValue
import code.consumer.Consumers
import code.customer.Customer
import code.migration.MigrationScriptLogProvider
import code.model.dataAccess.{ViewImpl, ViewPrivileges}
import code.util.Helper.MdcLoggable
import code.views.system.{AccountAccess, ViewDefinition}
import com.github.dwickern.macros.NameOf.nameOf
import net.liftweb.mapper.{By, ByList, Like}

object Migration extends MdcLoggable {
  
  private val execute = getPropsAsBoolValue("migration_scripts.execute", false)

  private def executeScript(blockOfCode: => Boolean): Boolean = {
    if(execute) blockOfCode else execute
  }
  
  private def runOnce(name: String)(blockOfCode: => Boolean): Boolean = {
    MigrationScriptLogProvider.migrationScriptLogProvider.vend.isExecuted(name) match {
      case false => blockOfCode
      case true => true
    }
  }
  
  private def saveLog(name: String, commitId: String, isSuccessful: Boolean, startDate: Long, endDate: Long, comment: String) = {
    MigrationScriptLogProvider.migrationScriptLogProvider.vend.saveLog(name, commitId, isSuccessful, startDate, endDate, comment) match {
      case true =>
      case false =>
        logger.warn(s"Migration.database.$name is executed at this instance but the corresponding log is not saved!!!!!!")
    }
  }
  
  object database {
    
    def executeScripts(): Boolean = executeScript {
      dummyScript()
      populateTableViewDefinition()
      populateTableAccountAccess()
    }
    
    private def dummyScript(): Boolean = {
      val name = "Dummy test script"
      runOnce(name) {
        val startDate = System.currentTimeMillis()
        val commitId: String = APIUtil.gitCommit
        val comment: String = "dummy comment"
        val isSuccessful = true
        val endDate = System.currentTimeMillis()
        saveLog(name, commitId, isSuccessful, startDate, endDate, comment)
        isSuccessful
      }
    }
    
    private def populateTableAccountAccess(): Boolean = {
      val name = nameOf(populateTableAccountAccess)
      runOnce(name) {
        val startDate = System.currentTimeMillis()
        val commitId: String = APIUtil.gitCommit
        val views = ViewImpl.findAll()
        
        // Delete all rows at the table
        AccountAccess.bulkDelete_!!()
        
        // Insert rows into table "accountaccess" based on data in the tables viewimpl and viewprivileges
        val insertedRows: List[Boolean] =
          for {
            view <- views
            permission <- ViewPrivileges.findAll(By(ViewPrivileges.view, view.id))
          } yield {
            val viewId = ViewImpl.find(By(ViewImpl.id_, permission.view.get)).map(_.permalink_.get).getOrElse("")
            AccountAccess
              .create
              .bank_id(view.bankPermalink.get)
              .account_id(view.accountPermalink.get)
              .user_fk(permission.user.get)
              .view_id(viewId)
              .save()
          }
        val isSuccessful = insertedRows.forall(_ == true)
        val accountAccess = AccountAccess.findAll()
        val accountAccessSize = accountAccess.size
        val viewPrivileges = ViewPrivileges.findAll()
        val viewPrivilegesSize = viewPrivileges.size
        
        // We want to find foreign keys "viewprivileges.view_c" which cannot be mapped to "viewimpl.id_"
        val x1 = ViewPrivileges.findAll(ByList(ViewPrivileges.view, views.map(_.id))).map(_.view.get).distinct.sortWith(_>_)
        val x2 = viewPrivileges.map(_.view.get).distinct.sortWith(_>_)
        val deadForeignKeys = x2.diff(x1)

        val endDate = System.currentTimeMillis()
        val comment: String =
          s"""Account access size: $accountAccessSize;
             |View privileges size: $viewPrivilegesSize;
             |List of dead foreign keys at the field ViewPrivileges.view_c: ${deadForeignKeys.mkString(",")};
             |Duration: ${endDate - startDate} ms;
             """.stripMargin
        saveLog(name, commitId, isSuccessful, startDate, endDate, comment)
        isSuccessful
      }
    }

    private def populateTableViewDefinition(): Boolean = {
      val name = nameOf(populateTableViewDefinition)
      runOnce(name) {
        val startDate = System.currentTimeMillis()
        val commitId: String = APIUtil.gitCommit
        val views = ViewImpl.findAll()

        // Delete all rows at the table
        ViewDefinition.bulkDelete_!!()

        // Insert rows into table "viewdefinition" based on data in the table viewimpl
        val insertedRows =
          for {
            view: ViewImpl <- views
          } yield {
            val count = ((ViewDefinition.findAll(Like(ViewDefinition.view_id, view.permalink_.get + "%"))).size)
            ViewDefinition
              .create
              .isSystem_(view.isSystem)
              .isFirehose_(view.isFirehose)
              .name_(view.name)
              .view_id(view.permalink_.get + {if (count==0) "" else count.toString()})
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
        val comment: String =
          s"""View implementation size: ${views.size};
             |View definition size: $viewDefinitionSize;
             |Duration: ${endDate - startDate} ms;
             """.stripMargin
        saveLog(name, commitId, isSuccessful, startDate, endDate, comment)
        isSuccessful
      }
    }  
    
    private def generateAndPopulateMissingCustomerUUIDs(): Boolean = {
      Customer.customerProvider.vend.populateMissingUUIDs()
    }

    private def generateAndPopulateMissingConsumersUUIDs(): Boolean = {
      Consumers.consumers.vend.populateMissingUUIDs()
    }
    
  }
  
}
