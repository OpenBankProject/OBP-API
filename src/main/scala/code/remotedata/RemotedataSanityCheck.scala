package code.remotedata

import akka.pattern.ask
import code.sanitycheck.{RemotedataSanityCheckCaseClasses, SanityChecks}
import net.liftweb.common.Box


object RemotedataSanityCheck extends RemotedataActorInit with SanityChecks {

  val cc = RemotedataSanityCheckCaseClasses

  def remoteAkkaSanityCheck(remoteDataSecret: String): Box[Boolean] =
    extractFutureToBox(actor ? cc.remoteAkkaSanityCheck(remoteDataSecret))

}
