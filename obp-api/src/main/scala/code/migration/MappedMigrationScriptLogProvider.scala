package code.migration

import code.util.Helper.MdcLoggable
import net.liftweb.common.Full
import net.liftweb.mapper.By

object MappedMigrationScriptLogProvider extends MigrationScriptLogProvider with MdcLoggable {
  override def saveLog(name: String, commitId: String, isSuccessful: Boolean, startDate: Long, endDate: Long, comment: String): Boolean = {
    MigrationScriptLog.find(By(MigrationScriptLog.Name, name), By(MigrationScriptLog.IsSuccessful, isSuccessful)) match {
      case Full(log) => 
        log
          .Name(name)
          .CommitId(commitId)
          .IsSuccessful(isSuccessful)
          .StartDate(startDate)
          .EndDate(endDate)
          .Remark(comment)
          .save()
      case _ =>
        MigrationScriptLog
          .create
          .Name(name)
          .CommitId(commitId)
          .IsSuccessful(isSuccessful)
          .StartDate(startDate)
          .EndDate(endDate)
          .Remark(comment)
          .save()
    }
  }
  override def isExecuted(name: String): Boolean = {
    MigrationScriptLog.find(
      By(MigrationScriptLog.Name, name),
      By(MigrationScriptLog.IsSuccessful, true)
    ).isDefined
  }
}

