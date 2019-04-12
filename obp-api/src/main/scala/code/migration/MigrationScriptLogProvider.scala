package code.migration

import net.liftweb.util.SimpleInjector


object MigrationScriptLogProvider extends SimpleInjector {

  val migrationScriptLogProvider = new Inject(buildOne _) {}

  def buildOne: MigrationScriptLogProvider = MappedMigrationScriptLogProvider
}

trait MigrationScriptLogProvider {
  def saveLog(name: String, commitId: String, isSuccessful: Boolean, startDate: Long, endDate: Long, comment: String): Boolean
  def isExecuted(name: String): Boolean
}
