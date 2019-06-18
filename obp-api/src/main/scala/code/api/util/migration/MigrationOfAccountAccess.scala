package code.api.util.migration

import code.api.util.APIUtil
import code.api.util.migration.Migration.{DbFunction, saveLog}
import code.model.dataAccess.{ViewImpl, ViewPrivileges}
import code.views.system.{AccountAccess, ViewDefinition}
import net.liftweb.mapper.{By, ByList, DB}
import net.liftweb.util.DefaultConnectionIdentifier

object TableAccountAccess {
  def populate(name: String): Boolean = {
    DbFunction.tableExists(ViewPrivileges, (DB.use(DefaultConnectionIdentifier){ conn => conn})) match {
      case true =>
        val startDate = System.currentTimeMillis()
        val commitId: String = APIUtil.gitCommit
        val views = ViewImpl.findAll()

        // Make back up
        DbFunction.makeBackUpOfTable(AccountAccess)
        // Delete all rows at the table
        AccountAccess.bulkDelete_!!()
    
        // Insert rows into table "accountaccess" based on data in the tables viewimpl and viewprivileges
        val insertedRows: List[Boolean] =
          for {
            view <- views
            permission <- ViewPrivileges.findAll(By(ViewPrivileges.view, view.id))
          } yield {
            val viewId = ViewImpl.find(By(ViewImpl.id_, permission.view.get)).map(_.permalink_.get).getOrElse("")
            val viewFk: Long = ViewDefinition.findByUniqueKey(view.bankId.value, view.accountId.value, view.viewId.value).map(_.id_.get).getOrElse(0)
            AccountAccess
              .create
              .bank_id(view.bankPermalink.get)
              .account_id(view.accountPermalink.get)
              .user_fk(permission.user.get)
              .view_id(viewId)
              .view_fk(viewFk)
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

        ////  (${accountAccess.map(_.id).mkString(",")});


        val comment: String =
          s"""Account access size: $accountAccessSize;
             |View privileges size: $viewPrivilegesSize;
             |List of dead foreign keys at the field ViewPrivileges.view_c: ${deadForeignKeys.mkString(",")};
             |Duration: ${endDate - startDate} ms;
             |Primary keys of the inserted rows: NOPE too risky
                 """.stripMargin
        saveLog(name, commitId, isSuccessful, startDate, endDate, comment)
        isSuccessful
      case false =>
        val startDate = System.currentTimeMillis()
        val commitId: String = APIUtil.gitCommit
        val isSuccessful = false
        val endDate = System.currentTimeMillis()
        val comment: String =
          s"""View privileges does not exist;
                 """.stripMargin
        saveLog(name, commitId, isSuccessful, startDate, endDate, comment)
        isSuccessful
    }
  }
}
