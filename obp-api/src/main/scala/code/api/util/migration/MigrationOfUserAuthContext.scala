package code.api.util.migration

import java.time.format.DateTimeFormatter
import java.time.{ZoneId, ZonedDateTime}

import code.api.util.{APIUtil, DoobieUtil}
import code.api.util.migration.Migration.{DbFunction, saveLog}
import doobie.Fragments
import doobie.implicits._

/**
 * One-time historical migration: deletes redundant (userId, key) rows in the user-auth-context
 * table, keeping only the most recently updated one per group.
 *
 * Originally used the Lift MappedUserAuthContext entity's typed findAll/delete_! for the delete
 * step and DbFunction.makeBackUpOfTable(MetaMapper) for the backup; that entity is gone - the
 * table is now created by Liquibase (the table is in db/changelog/db.changelog-baseline.yaml) - so both
 * go through DoobieUtil with plain SQL and the table-name overload of the backup helper. Every
 * environment that had already run this migration has it recorded in migration_script_log and
 * runOnce skips it; the group-by query itself finds nothing to delete on a fresh instance, so the
 * rewrite is a no-op there. Kept only so migration_script_log stays a complete history.
 */
object MigrationOfUserAuthContext {

  val oneDayAgo = ZonedDateTime.now(ZoneId.of("UTC")).minusDays(1)
  val oneYearInFuture = ZonedDateTime.now(ZoneId.of("UTC")).plusYears(1)
  val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm'Z'")

  def removeDuplicates(name: String): Boolean = {

    DbFunction.makeBackUpOfTableByName("mappeduserauthcontext")

    val startDate = System.currentTimeMillis()
    val commitId: String = APIUtil.gitCommit

    case class DuplicateGroup(userId: String, key: String)

    val duplicateGroups = DoobieUtil.runQuery(
      sql"""select muserid, mkey from mappeduserauthcontext
            group by muserid, mkey having count(mkey) > 1"""
        .query[(String, String)].to[List]
    ).map { case (userId, key) => DuplicateGroup(userId, key) }

    // Keep the most recently updated row per (userId, key) group, delete the rest.
    val deleted: List[Boolean] = duplicateGroups.map { group =>
      val idsNewestFirst = DoobieUtil.runQuery(
        sql"""select muserauthcontextid from mappeduserauthcontext
              where muserid = ${group.userId} and mkey = ${group.key}
              order by updatedat desc"""
          .query[String].to[List])
      idsNewestFirst match {
        case _ :: id2 :: moreIds =>
          val staleIds = cats.data.NonEmptyList(id2, moreIds)
          DoobieUtil.runUpdate(
            (fr"delete from mappeduserauthcontext where" ++
              Fragments.in(fr"muserauthcontextid", staleIds)).update.run)
          true
        case _ => true
      }
    }

    val isSuccessful = deleted.forall(_ == true)
    val endDate = System.currentTimeMillis()
    val comment: String =
      s"""Deleted all redundant rows in the table mappeduserauthcontext
         |""".stripMargin
    saveLog(name, commitId, isSuccessful, startDate, endDate, comment)
    println(s"comment = $comment")
    isSuccessful
  }
}
