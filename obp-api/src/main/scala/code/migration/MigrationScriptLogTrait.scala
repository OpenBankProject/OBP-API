package code.migration

trait MigrationScriptLogTrait {
  def primaryKey: Long
  def migrationScriptLogId: String
  def name: String
  def commitId: String
  def isSuccessful: Boolean
  def startDate: Long
  def endDate: Long
  def remark: String
}