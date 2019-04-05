package code.migration

trait MigrationScriptLog {
  def migrationScriptLogId: String
  def name: String
  def commitId: String
  def wasExecuted: Boolean
  def startDate: Long
  def endDate: Long
  def comment: String
}