package code.api.pemusage

import code.api.util.APIUtil
import net.liftweb.util.SimpleInjector

object PemUsageDI extends SimpleInjector {
  val pemUsage = new Inject(buildOne _) {}
  def buildOne: PemUsageProviderTrait = MappedPemUsageProvider
  
}

trait PemUsageProviderTrait {
  
}

trait PemUsageTrait {
  def pemHash: String 
  def consumerId: String
  def lastUserId: String
}
