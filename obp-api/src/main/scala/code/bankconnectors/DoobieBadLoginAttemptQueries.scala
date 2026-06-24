package code.bankconnectors

import code.api.util.DoobieUtil
import doobie._
import doobie.implicits._

object DoobieBadLoginAttemptQueries {

  private def atomicIncrement(provider: String, username: String): ConnectionIO[Int] =
    for {
      _ <- sql"""SELECT mbadattemptssincelastsuccessorreset
                 FROM mappedbadloginattempt
                 WHERE provider = $provider AND musername = $username
                 FOR UPDATE""".query[Int].option
      rows <- sql"""UPDATE mappedbadloginattempt
                    SET mbadattemptssincelastsuccessorreset = mbadattemptssincelastsuccessorreset + 1,
                        mlastfailuredate = NOW()
                    WHERE provider = $provider AND musername = $username""".update.run
    } yield rows

  def incrementBadLoginAttempts(provider: String, username: String): Int =
    DoobieUtil.runUpdate(atomicIncrement(provider, username))
}
