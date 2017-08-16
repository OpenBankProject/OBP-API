package code.transactionChallenge

import code.remotedata.RemotedataExpectedChallengeAnswerProvider
import net.liftweb.util.{Props, SimpleInjector}



trait ExpectedChallengeAnswer {
  def challengeId : String
  def encryptedAnswer : String
  def salt : String
}


object ExpectedChallengeAnswer extends SimpleInjector {

  val expectedChallengeAnswerProvider = new Inject(buildOne _) {}

  def buildOne: ExpectedChallengeAnswerProvider =
    Props.getBool("use_akka", false) match {
      case false  => MappedExpectedChallengeAnswerProvider
      case true => RemotedataExpectedChallengeAnswerProvider      // We will use Akka as a middleware
    }
}


