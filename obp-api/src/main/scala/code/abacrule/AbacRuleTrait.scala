package code.abacrule

import code.api.util.APIUtil
import com.openbankproject.commons.model._
import net.liftweb.common.Box
import net.liftweb.mapper._
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

class AbacRule extends AbacRuleTrait with LongKeyedMapper[AbacRule] with IdPK with CreatedUpdated {
  def getSingleton = AbacRule

  object AbacRuleId extends MappedString(this, 255) {
    override def defaultValue = APIUtil.generateUUID()
  }
  object RuleName extends MappedString(this, 255) 
  object RuleCode extends MappedText(this) 
  object IsActive extends MappedBoolean(this) {
    override def defaultValue = true
  }
  object Description extends MappedText(this) 
  object Policy extends MappedText(this)
  object CreatedByUserId extends MappedString(this, 255) 
  object UpdatedByUserId extends MappedString(this, 255)

  override def abacRuleId: String = AbacRuleId.get
  override def ruleName: String = RuleName.get
  override def ruleCode: String = RuleCode.get
  override def isActive: Boolean = IsActive.get
  override def description: String = Description.get
  override def policy: String = Policy.get
  override def createdByUserId: String = CreatedByUserId.get
  override def updatedByUserId: String = UpdatedByUserId.get
}

object AbacRule extends AbacRule with LongKeyedMetaMapper[AbacRule] {
  override def dbIndexes: List[BaseIndex[AbacRule]] = Index(AbacRuleId) :: Index(RuleName) :: Index(CreatedByUserId) :: super.dbIndexes
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
  
  override def getAbacRuleById(ruleId: String): Box[AbacRuleTrait] = {
    AbacRule.find(By(AbacRule.AbacRuleId, ruleId))
  }

  override def getAbacRuleByName(ruleName: String): Box[AbacRuleTrait] = {
    AbacRule.find(By(AbacRule.RuleName, ruleName))
  }

  override def getAllAbacRules(): List[AbacRuleTrait] = {
    AbacRule.findAll()
  }

  override def getActiveAbacRules(): List[AbacRuleTrait] = {
    AbacRule.findAll(By(AbacRule.IsActive, true))
  }

  override def getAbacRulesByPolicy(policy: String): List[AbacRuleTrait] = {
    AbacRule.findAll().filter { rule =>
      rule.policy.split(",").map(_.trim).contains(policy)
    }
  }

  override def getActiveAbacRulesByPolicy(policy: String): List[AbacRuleTrait] = {
    AbacRule.findAll(By(AbacRule.IsActive, true)).filter { rule =>
      rule.policy.split(",").map(_.trim).contains(policy)
    }
  }

  override def createAbacRule(
    ruleName: String,
    ruleCode: String,
    description: String,
    policy: String,
    isActive: Boolean,
    createdBy: String
  ): Box[AbacRuleTrait] = {
    tryo {
      AbacRule.create
        .RuleName(ruleName)
        .RuleCode(ruleCode)
        .Description(description)
        .Policy(policy)
        .IsActive(isActive)
        .CreatedByUserId(createdBy)
        .UpdatedByUserId(createdBy)
        .saveMe()
    }
  }

  override def updateAbacRule(
    ruleId: String,
    ruleName: String,
    ruleCode: String,
    description: String,
    policy: String,
    isActive: Boolean,
    updatedBy: String
  ): Box[AbacRuleTrait] = {
    for {
      rule <- AbacRule.find(By(AbacRule.AbacRuleId, ruleId))
      updatedRule <- tryo {
        rule
          .RuleName(ruleName)
          .RuleCode(ruleCode)
          .Description(description)
          .Policy(policy)
          .IsActive(isActive)
          .UpdatedByUserId(updatedBy)
          .saveMe()
      }
    } yield updatedRule
  }

  override def deleteAbacRule(ruleId: String): Box[Boolean] = {
    for {
      rule <- AbacRule.find(By(AbacRule.AbacRuleId, ruleId))
      deleted <- tryo(rule.delete_!)
    } yield deleted
  }
}