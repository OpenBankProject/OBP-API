package code.migration

import net.liftweb.util.SimpleInjector


object MigrationScriptLogProvider extends SimpleInjector {

  val migrationScriptLogProvider = new Inject(buildOne _) {}

  def buildOne: MigrationScriptLogProvider = MappedMigrationScriptLogProvider
}

trait MigrationScriptLogProvider {
  def saveLog(name: String, commitId: String, isExecuted: Boolean, executedAt: Long): Boolean
  def isExecuted(name: String): Boolean
}
