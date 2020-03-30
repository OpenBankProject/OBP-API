package code.api.pemusage

import code.api.util.APIUtil
import code.remotedata.RemotedataPemUsage
import net.liftweb.util.SimpleInjector

object PemUsageDI extends SimpleInjector {
  val pemUsage = new Inject(buildOne _) {}
  def buildOne: PemUsageProviderTrait = APIUtil.getPropsAsBoolValue("use_akka", false) match {
    case false  => MappedPemUsageProvider
    case true => RemotedataPemUsage   // We will use Akka as a middleware
  }
}

trait PemUsageProviderTrait {
  
}

trait PemUsageTrait {
  def pemHash: String 
  def consumerId: String
  def lastUserId: String
}


class RemotedataPemUsageCaseClasses {
  
}

object RemotedatPemUsageCaseClasses extends RemotedataPemUsageCaseClasses
