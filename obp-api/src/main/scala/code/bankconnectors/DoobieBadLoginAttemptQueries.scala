package code.bankconnectors

import java.sql.Timestamp
import java.util.Date

import code.api.util.DoobieUtil
import code.loginattempts.BadLoginAttempt
import doobie._
import doobie.implicits._
import doobie.implicits.javasql._

/** One bad-login-attempt row, standing in for the Lift entity in return types. */
case class BadLoginAttemptRow(
  username: String,
  provider: String,
  badAttemptsSinceLastSuccessOrReset: Int,
  lastFailureDate: Date
) extends BadLoginAttempt

object DoobieBadLoginAttemptQueries {

  private def rowOf(r: (String, String, Int, Timestamp)): BadLoginAttemptRow =
    BadLoginAttemptRow(r._1, r._2, r._3, new Date(r._4.getTime))

  private val selectCols =
    fr"""SELECT musername, provider, mbadattemptssincelastsuccessorreset, mlastfailuredate
         FROM mappedbadloginattempt"""

  def find(provider: String, username: String): Option[BadLoginAttemptRow] =
    DoobieUtil.runQuery(
      (selectCols ++ fr"WHERE provider = $provider AND musername = $username LIMIT 1")
        .query[(String, String, Int, Timestamp)].option
    ).map(rowOf)

  /** Every (provider, username) whose bad-attempt counter exceeds maxBadLoginAttempts. */
  def usernamesOverThreshold(maxBadLoginAttempts: Int): List[String] =
    DoobieUtil.runQuery(
      sql"""SELECT musername FROM mappedbadloginattempt
            WHERE mbadattemptssincelastsuccessorreset > $maxBadLoginAttempts"""
        .query[String].to[List])

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

  def create(provider: String, username: String, badAttempts: Int): BadLoginAttemptRow = {
    val now = new Timestamp(System.currentTimeMillis)
    DoobieUtil.runUpdate(
      sql"""INSERT INTO mappedbadloginattempt (musername, provider, mbadattemptssincelastsuccessorreset, mlastfailuredate)
            VALUES ($username, $provider, $badAttempts, $now)"""
        .update.run)
    BadLoginAttemptRow(username, provider, badAttempts, new Date(now.getTime))
  }

  def resetBadLoginAttempts(provider: String, username: String): Int = {
    val now = new Timestamp(System.currentTimeMillis)
    DoobieUtil.runUpdate(
      sql"""UPDATE mappedbadloginattempt
            SET mbadattemptssincelastsuccessorreset = 0, mlastfailuredate = $now
            WHERE provider = $provider AND musername = $username"""
        .update.run)
  }
}
