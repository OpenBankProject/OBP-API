package code.transactionChallenge

import code.api.util.APIUtil
import code.remotedata.RemotedataChallenges
import com.openbankproject.commons.model.enums.StrongCustomerAuthentication.SCA
import com.openbankproject.commons.model.enums.StrongCustomerAuthenticationStatus.SCAStatus
import net.liftweb.util.{Props, SimpleInjector}



trait ChallengeTrait {
  def challengeId : String
  def transactionRequestId : String
  def expectedAnswer : String
  def expectedUserId : String
  def salt : String
  def successful : Boolean
  
  //OBP will support many different challenge types:
  //OBP_Payment, OBP_Consent, OBP_General, BerlinGroup_Payment, BerlinGroup_Consent,
  def challengeType: String
  
  //NOTE: following are from BerlinGroup, we try to share the same challenges for different standard.
  //for OBP standard, all the following can be Optional: 
  def consentId: Option[String] // Note: consentId and transactionRequestId are exclusive here.
  def scaMethod: Option[SCA]
  def scaStatus: Option[SCAStatus]
  def authenticationMethodId: Option[String]
}


object Challenges extends SimpleInjector {

  val ChallengeProvider = new Inject(buildOne _) {}

  def buildOne: ChallengeProvider =
    APIUtil.getPropsAsBoolValue("use_akka", false) match {
      case false  => MappedChallengeProvider
      case true => RemotedataChallenges      // We will use Akka as a middleware
    }
}


