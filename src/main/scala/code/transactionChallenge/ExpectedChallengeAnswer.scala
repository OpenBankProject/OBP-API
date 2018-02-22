package code.transactionChallenge

import code.api.util.APIUtil
import code.remotedata.RemotedataExpectedChallengeAnswerProvider
import net.liftweb.util.{Props, SimpleInjector}



trait ExpectedChallengeAnswer {
  def challengeId : String
  def expectedAnswer : String
  def salt : String
}


object ExpectedChallengeAnswer extends SimpleInjector {

  val expectedChallengeAnswerProvider = new Inject(buildOne _) {}

  def buildOne: ExpectedChallengeAnswerProvider =
    APIUtil.getPropsAsBoolValue("use_akka", false) match {
      case false  => MappedExpectedChallengeAnswerProvider
      case true => RemotedataExpectedChallengeAnswerProvider      // We will use Akka as a middleware
    }
}


