package code.remotedata

import code.actorsystem.ObpActorInit
import code.api.attributedefinition.RemotedatAttributeDefinitionCaseClasses
import code.api.pemusage.PemUsageProviderTrait


object RemotedataPemUsage extends ObpActorInit with PemUsageProviderTrait {

  val cc = RemotedatAttributeDefinitionCaseClasses


}
