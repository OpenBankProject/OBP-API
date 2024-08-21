package code.transactionChallenge

import code.api.util.APIUtil
import com.openbankproject.commons.model.enums.StrongCustomerAuthentication.SCA
import com.openbankproject.commons.model.enums.StrongCustomerAuthenticationStatus.SCAStatus
import net.liftweb.util.{Props, SimpleInjector}





object Challenges extends SimpleInjector {

  val ChallengeProvider = new Inject(buildOne _) {}

  def buildOne: ChallengeProvider = MappedChallengeProvider
  
}


