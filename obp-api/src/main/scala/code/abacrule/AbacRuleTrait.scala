package code.abacrule

import code.api.util.{APIUtil, DoobieUtil}
import com.openbankproject.commons.model._
import doobie._
import doobie.implicits._
import doobie.implicits.javasql._
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.util.Helpers.tryo

import java.util.Date

trait AbacRuleTrait {
  def abacRuleId: String
  def ruleName: String
  def ruleCode: String
  def isActive: Boolean
  def description: String
  def policy: String
  def createdByUserId: String
  def updatedByUserId: String
}

/**
 * One ABAC rule.
 *
 * `policy` is a comma-joined tag list in a single column, which is why the by-policy queries filter
 * in memory rather than in SQL — a LIKE would match a policy name that is a substring of another.
 *
 * The table has three plain indexes and no unique one, though abacRuleId is the handle the update
 * and delete key off and getAbacRuleByName reads by rulename, so two rules may share a name.
 * Pre-existing; the lookups pin id ASC so which row wins is deterministic.
 */
case class AbacRule(
  abacRuleId: String,
  ruleName: String,
  ruleCode: String,
  isActive: Boolean,
  description: String,
  policy: String,
  createdByUserId: String,
  updatedByUserId: String
) extends AbacRuleTrait

object AbacRule {

  private val selectColumns =
    fr"""SELECT abacruleid, rulename, rulecode, isactive, description, policy, createdbyuserid,
                updatedbyuserid
         FROM abacrule"""

  private type Row = (Option[String], Option[String], Option[String], Option[Boolean],
    Option[String], Option[String], Option[String], Option[String])

  private def fromRow(row: Row): AbacRule = row match {
    case (abacRuleId, ruleName, ruleCode, isActive, description, policy, createdByUserId,
          updatedByUserId) =>
        // MappedBoolean read a NULL column as false - `data openOr false`, with a NULL
        // setting `data = Empty` - so it never failed the read and never returned the
        // field's declared defaultValue. Binding the column as Option keeps both halves.
      AbacRule(abacRuleId.orNull, ruleName.orNull, ruleCode.orNull, isActive.getOrElse(false),
        description.orNull, policy.orNull, createdByUserId.orNull, updatedByUserId.orNull)
  }

  private def query(condition: Fragment): List[AbacRule] =
    DoobieUtil.runQuery((selectColumns ++ condition).query[Row].to[List]).map(fromRow)

  private def one(condition: Fragment): Box[AbacRule] =
    query(condition ++ fr"ORDER BY id ASC LIMIT 1").headOption match {
      case Some(row) => Full(row)
      case None => Empty
    }

  def insert(ruleName: String, ruleCode: String, description: String, policy: String,
             isActive: Boolean, createdBy: String): AbacRule = {
    val abacRuleId = APIUtil.generateUUID()
    val now = new java.sql.Timestamp(System.currentTimeMillis())
    DoobieUtil.runUpdate(
      sql"""INSERT INTO abacrule
            (abacruleid, rulename, rulecode, isactive, description, policy, createdbyuserid,
             updatedbyuserid, createdat, updatedat)
            VALUES ($abacRuleId, $ruleName, $ruleCode, $isActive, $description, $policy,
             $createdBy, $createdBy, $now, $now)"""
        .update.run)
    AbacRule(abacRuleId, ruleName, ruleCode, isActive, description, policy, createdBy, createdBy)
  }

  /** createdByUserId is deliberately left alone — only updatedByUserId moves on an edit. */
  def update(abacRuleId: String, ruleName: String, ruleCode: String, description: String,
             policy: String, isActive: Boolean, updatedBy: String): Box[AbacRule] = {
    DoobieUtil.runUpdate(
      sql"""UPDATE abacrule SET rulename = $ruleName, rulecode = $ruleCode,
              description = $description, policy = $policy, isactive = $isActive,
              updatedbyuserid = $updatedBy,
              updatedat = ${new java.sql.Timestamp(System.currentTimeMillis())}
            WHERE abacruleid = $abacRuleId""".update.run)
    findById(abacRuleId)
  }

  def findById(abacRuleId: String): Box[AbacRule] = one(fr"WHERE abacruleid = $abacRuleId")

  def findByName(ruleName: String): Box[AbacRule] = one(fr"WHERE rulename = $ruleName")

  def findAll(): List[AbacRule] = query(fr"ORDER BY id ASC")

  def findAllActive(): List[AbacRule] = query(fr"WHERE isactive = true ORDER BY id ASC")

  def delete(abacRuleId: String): Boolean =
    DoobieUtil.runUpdate(sql"DELETE FROM abacrule WHERE abacruleid = $abacRuleId".update.run) > 0

  def deleteAll(): Unit = {
    DoobieUtil.runUpdate(sql"DELETE FROM abacrule".update.run)
    ()
  }
}

trait AbacRuleProvider {
  def getAbacRuleById(ruleId: String): Box[AbacRuleTrait]
  def getAbacRuleByName(ruleName: String): Box[AbacRuleTrait]
  def getAllAbacRules(): List[AbacRuleTrait]
  def getActiveAbacRules(): List[AbacRuleTrait]
  def getAbacRulesByPolicy(policy: String): List[AbacRuleTrait]
  def getActiveAbacRulesByPolicy(policy: String): List[AbacRuleTrait]
  def createAbacRule(
    ruleName: String,
    ruleCode: String,
    description: String,
    policy: String,
    isActive: Boolean,
    createdBy: String
  ): Box[AbacRuleTrait]
  def updateAbacRule(
    ruleId: String,
    ruleName: String,
    ruleCode: String,
    description: String,
    policy: String,
    isActive: Boolean,
    updatedBy: String
  ): Box[AbacRuleTrait]
  def deleteAbacRule(ruleId: String): Box[Boolean]
}

object MappedAbacRuleProvider extends AbacRuleProvider {

  override def getAbacRuleById(ruleId: String): Box[AbacRuleTrait] = AbacRule.findById(ruleId)

  override def getAbacRuleByName(ruleName: String): Box[AbacRuleTrait] = AbacRule.findByName(ruleName)

  override def getAllAbacRules(): List[AbacRuleTrait] = AbacRule.findAll()

  override def getActiveAbacRules(): List[AbacRuleTrait] = AbacRule.findAllActive()

  // policy is a comma-joined tag list in one column, so membership is decided in memory: a SQL LIKE
  // would also match a policy name that is a substring of another.
  override def getAbacRulesByPolicy(policy: String): List[AbacRuleTrait] =
    AbacRule.findAll().filter { rule =>
      Option(rule.policy).exists(_.split(",").map(_.trim).contains(policy))
    }

  override def getActiveAbacRulesByPolicy(policy: String): List[AbacRuleTrait] =
    AbacRule.findAllActive().filter { rule =>
      Option(rule.policy).exists(_.split(",").map(_.trim).contains(policy))
    }

  override def createAbacRule(
    ruleName: String,
    ruleCode: String,
    description: String,
    policy: String,
    isActive: Boolean,
    createdBy: String
  ): Box[AbacRuleTrait] =
    tryo(AbacRule.insert(ruleName, ruleCode, description, policy, isActive, createdBy))

  override def updateAbacRule(
    ruleId: String,
    ruleName: String,
    ruleCode: String,
    description: String,
    policy: String,
    isActive: Boolean,
    updatedBy: String
  ): Box[AbacRuleTrait] =
    for {
      _ <- AbacRule.findById(ruleId)
      updatedRule <- tryo(AbacRule.update(ruleId, ruleName, ruleCode, description, policy, isActive,
        updatedBy)).flatMap(identity)
    } yield updatedRule

  override def deleteAbacRule(ruleId: String): Box[Boolean] =
    for {
      _ <- AbacRule.findById(ruleId)
      deleted <- tryo(AbacRule.delete(ruleId))
    } yield deleted
}
