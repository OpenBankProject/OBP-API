package code.injections

import net.liftweb.util.SimpleInjector
import code.metrics.Metrics
import code.metrics.MongoMetrics
import code.model.dataAccess.MongoConfig

object MetricsInjector extends SimpleInjector {
  
  private val mongoMetrics = 
    new MongoMetrics(
      host = MongoConfig.host,
      port = MongoConfig.port,
      dbName = MongoConfig.dbName)
  
  def buildOne: Metrics = mongoMetrics

  val m = new Inject(buildOne _) {}
}