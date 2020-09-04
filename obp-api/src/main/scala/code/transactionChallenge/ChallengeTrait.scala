package code.transactionChallenge

import code.api.util.APIUtil
import code.remotedata.RemotedataChallenges
import com.openbankproject.commons.model.enums.StrongCustomerAuthentication.SCA
import com.openbankproject.commons.model.enums.StrongCustomerAuthenticationStatus.SCAStatus
import net.liftweb.util.{Props, SimpleInjector}





object Challenges extends SimpleInjector {

  val ChallengeProvider = new Inject(buildOne _) {}

  def buildOne: ChallengeProvider =
    APIUtil.getPropsAsBoolValue("use_akka", false) match {
      case false  => MappedChallengeProvider
      case true => RemotedataChallenges      // We will use Akka as a middleware
    }
}


