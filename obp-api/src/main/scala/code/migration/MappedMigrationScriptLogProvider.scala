package code.migration

import code.util.Helper.MdcLoggable
import net.liftweb.mapper.By

object MappedMigrationScriptLogProvider extends MigrationScriptLogProvider with MdcLoggable {
  override def saveLog(name: String, commitId: String, wasExecuted: Boolean, startDate: Long, endDate: Long, comment: String): Boolean = {
    MappedMigrationScriptLog
      .create
      .mName(name)
      .mCommitId(commitId)
      .mWasExecuted(wasExecuted)
      .mStartDate(startDate)
      .mEndDate(endDate)
      .mComment(comment)
      .save()
  }
  override def isExecuted(name: String): Boolean = {
    MappedMigrationScriptLog.find(
      By(MappedMigrationScriptLog.mName, name),
      By(MappedMigrationScriptLog.mWasExecuted, true)
    ).isDefined
  }
}

