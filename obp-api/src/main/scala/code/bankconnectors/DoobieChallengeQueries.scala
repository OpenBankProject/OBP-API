package code.bankconnectors

import code.api.util.DoobieUtil
import doobie._
import doobie.implicits._
import net.liftweb.common.Box
import net.liftweb.util.Helpers.tryo

object DoobieChallengeQueries {

  private def incrementAndSelectCounter(challengeId: String): ConnectionIO[Int] =
    for {
      _ <- sql"""SELECT attemptcounter
                 FROM ExpectedChallengeAnswer
                 WHERE challengeid = $challengeId
                 FOR UPDATE""".query[Int].option
      _ <- sql"""UPDATE ExpectedChallengeAnswer
                 SET attemptcounter = attemptcounter + 1
                 WHERE challengeid = $challengeId""".update.run
      counter <- sql"""SELECT attemptcounter FROM ExpectedChallengeAnswer
                       WHERE challengeid = $challengeId""".query[Int].unique
    } yield counter

  def incrementAndGetChallengeCounter(challengeId: String): Int =
    DoobieUtil.runUpdate(incrementAndSelectCounter(challengeId))
}
