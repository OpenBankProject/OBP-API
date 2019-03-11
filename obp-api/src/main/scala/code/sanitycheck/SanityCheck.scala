package code.sanitycheck

import code.api.util.APIUtil
import code.remotedata.RemotedataSanityCheck
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.util.{Props, SimpleInjector}



object SanityCheck extends SimpleInjector {

  val sanityCheck = new Inject(buildOne _) {}

  def buildOne: SanityChecks = RemotedataSanityCheck

}

trait SanityChecks {
  def remoteAkkaSanityCheck(remoteDataSecret: String): Box[Boolean]
}

class RemotedataSanityCheckCaseClasses {
  case class remoteAkkaSanityCheck(remoteDataSecret: String)
}

object RemotedataSanityCheckCaseClasses extends RemotedataSanityCheckCaseClasses

object SanityChecksImpl extends SanityChecks {
  override def remoteAkkaSanityCheck(remoteDataSecret: String): Box[Boolean] = {
    APIUtil.getPropsValue("remotedata.secret") match {
      case Full(x) => Full(remoteDataSecret == x)
      case _       => Empty
    }
  }
}
