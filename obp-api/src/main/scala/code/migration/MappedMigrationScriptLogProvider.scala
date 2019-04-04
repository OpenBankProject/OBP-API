package code.migration

import code.util.Helper.MdcLoggable
import net.liftweb.mapper.By

object MappedMigrationScriptLogProvider extends MigrationScriptLogProvider with MdcLoggable {
  override def saveLog(name: String, commitId: String, isExecuted: Boolean, executedAt: Long): Boolean = {
    MappedMigrationScriptLog
      .create
      .mName(name)
      .mCommitId(commitId)
      .mIsExecuted(isExecuted)
      .mExecutedAt(executedAt)
      .save()
  }
  override def isExecuted(name: String): Boolean = {
    MappedMigrationScriptLog.find(
      By(MappedMigrationScriptLog.mName, name),
      By(MappedMigrationScriptLog.mIsExecuted, true)
    ).isDefined
  }
}

