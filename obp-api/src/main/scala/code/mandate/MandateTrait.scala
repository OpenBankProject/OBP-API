package code.mandate

import code.api.util.{APIUtil, DoobieUtil}
import doobie._
import doobie.implicits._
import doobie.implicits.javasql._
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.util.Helpers.tryo

import java.util.Date

// ==================== Traits ====================

trait MandateTrait {
  def mandateId: String
  def bankId: String
  def accountId: String
  def customerId: String
  def mandateName: String
  def mandateReference: String
  def legalText: String
  def description: String
  def status: String
  def validFrom: Date
  def validTo: Date
  def createdByUserId: String
  def updatedByUserId: String
}

trait MandateProvisionTrait {
  def provisionId: String
  def mandateId: String
  def provisionName: String
  def provisionDescription: String
  def legalReference: String
  def provisionType: String
  def conditions: String
  def signatoryRequirements: String
  def linkedViewId: String
  def linkedAbacRuleId: String
  def linkedChallengeType: String
  def isActive: Boolean
  def sortOrder: Int
}

trait SignatoryPanelTrait {
  def panelId: String
  def mandateId: String
  def panelName: String
  def description: String
  def userIds: String
}

// ==================== Rows ====================

/**
 * The legal authority under which someone acts on an account.
 *
 * `mandateId` is the business id every caller uses; the surrogate key never leaves this file. The
 * two child tables point back here by `mandateId` as well, which is why its unique index matters.
 *
 * Free-text columns are bound as Option and read back with orNull so a null stays a SQL NULL and
 * comes back null, exactly as MappedString and MappedText behaved. Callers reach this store through
 * the v6.0.0 endpoints, which fill every optional field in with "" before calling, so a null is not
 * expected — but a store that throws on one would turn a tolerated input into a 500.
 */
case class Mandate(
  mandateId: String,
  bankId: String,
  accountId: String,
  customerId: String,
  mandateName: String,
  mandateReference: String,
  legalText: String,
  description: String,
  status: String,
  validFrom: Date,
  validTo: Date,
  createdByUserId: String,
  updatedByUserId: String
) extends MandateTrait

object Mandate {

  /** The status a mandate carries unless the caller names another one. */
  val activeStatus: String = "ACTIVE"

  private val selectColumns =
    fr"""SELECT mandateid, bankid, accountid, customerid, mandatename, mandatereference,
                legaltext, description, status, validfrom, validto, createdbyuserid, updatedbyuserid
         FROM mandate"""

  private type Row = (Option[String], Option[String], Option[String], Option[String],
    Option[String], Option[String], Option[String], Option[String], Option[String],
    Option[java.sql.Timestamp], Option[java.sql.Timestamp], Option[String], Option[String])

  private def fromRow(row: Row): Mandate = row match {
    case (mandateId, bankId, accountId, customerId, mandateName, mandateReference, legalText,
          description, status, validFrom, validTo, createdByUserId, updatedByUserId) =>
      Mandate(mandateId.orNull, bankId.orNull, accountId.orNull, customerId.orNull,
        mandateName.orNull, mandateReference.orNull, legalText.orNull, description.orNull,
        status.orNull, validFrom.map(ts => ts: Date).orNull, validTo.map(ts => ts: Date).orNull,
        createdByUserId.orNull, updatedByUserId.orNull)
  }

  private def query(condition: Fragment): List[Mandate] =
    DoobieUtil.runQuery((selectColumns ++ condition).query[Row].to[List]).map(fromRow)

  private def opt(value: String): Option[String] = Option(value)

  private def ts(value: Date): Option[java.sql.Timestamp] =
    Option(value).map(d => new java.sql.Timestamp(d.getTime))

  def findByMandateId(mandateId: String): Box[Mandate] =
    query(fr"WHERE mandateid = ${opt(mandateId)} LIMIT 1").headOption match {
      case Some(row) => Full(row)
      case None => Empty
    }

  /** Newest first — the API hands this order straight to the client. */
  def findAllByBankIdAndAccountId(bankId: String, accountId: String): List[Mandate] =
    query(fr"WHERE bankid = ${opt(bankId)} AND accountid = ${opt(accountId)} ORDER BY updatedat DESC")

  def findAllActiveByBankIdAndAccountId(bankId: String, accountId: String): List[Mandate] =
    query(fr"""WHERE bankid = ${opt(bankId)} AND accountid = ${opt(accountId)}
                 AND status = $activeStatus
               ORDER BY updatedat DESC""")

  def insert(bankId: String, accountId: String, customerId: String, mandateName: String,
             mandateReference: String, legalText: String, description: String, status: String,
             validFrom: Date, validTo: Date, createdByUserId: String): Mandate = {
    val mandateId = APIUtil.generateUUID()
    val now = new java.sql.Timestamp(System.currentTimeMillis())
    DoobieUtil.runUpdate(
      sql"""INSERT INTO mandate
            (mandateid, bankid, accountid, customerid, mandatename, mandatereference, legaltext,
             description, status, validfrom, validto, createdbyuserid, updatedbyuserid,
             createdat, updatedat)
            VALUES ($mandateId, ${opt(bankId)}, ${opt(accountId)}, ${opt(customerId)},
             ${opt(mandateName)}, ${opt(mandateReference)}, ${opt(legalText)}, ${opt(description)},
             ${opt(status)}, ${ts(validFrom)}, ${ts(validTo)}, ${opt(createdByUserId)},
             ${opt(createdByUserId)}, $now, $now)"""
        .update.run)
    // The creator is also the last updater of a brand new mandate.
    Mandate(mandateId, bankId, accountId, customerId, mandateName, mandateReference, legalText,
      description, status, validFrom, validTo, createdByUserId, createdByUserId)
  }

  /**
   * Rewrites the mutable half of a mandate. Bank, account, customer and creator are fixed at
   * creation; updatedat is restamped, which is what moves the row to the head of the listing.
   */
  def updateByMandateId(mandateId: String, mandateName: String, mandateReference: String,
                        legalText: String, description: String, status: String, validFrom: Date,
                        validTo: Date, updatedByUserId: String): Box[Mandate] = {
    val now = new java.sql.Timestamp(System.currentTimeMillis())
    DoobieUtil.runUpdate(
      sql"""UPDATE mandate
            SET mandatename = ${opt(mandateName)}, mandatereference = ${opt(mandateReference)},
                legaltext = ${opt(legalText)}, description = ${opt(description)},
                status = ${opt(status)}, validfrom = ${ts(validFrom)}, validto = ${ts(validTo)},
                updatedbyuserid = ${opt(updatedByUserId)}, updatedat = $now
            WHERE mandateid = ${opt(mandateId)}"""
        .update.run)
    findByMandateId(mandateId)
  }

  def deleteByMandateId(mandateId: String): Boolean =
    DoobieUtil.runUpdate(sql"DELETE FROM mandate WHERE mandateid = ${opt(mandateId)}".update.run) > 0

  def deleteAll(): Unit = {
    DoobieUtil.runUpdate(sql"DELETE FROM mandate".update.run)
    ()
  }
}

/**
 * One clause of a mandate.
 *
 * `signatoryRequirements` is a JSON array the API layer serialises before it gets here, and
 * `linkedViewId` / `linkedAbacRuleId` / `linkedChallengeType` name the mechanism that enforces the
 * clause, empty when nothing enforces it.
 */
case class MandateProvision(
  provisionId: String,
  mandateId: String,
  provisionName: String,
  provisionDescription: String,
  legalReference: String,
  provisionType: String,
  conditions: String,
  signatoryRequirements: String,
  linkedViewId: String,
  linkedAbacRuleId: String,
  linkedChallengeType: String,
  isActive: Boolean,
  sortOrder: Int
) extends MandateProvisionTrait

object MandateProvision {

  private val selectColumns =
    fr"""SELECT provisionid, mandateid, provisionname, provisiondescription, legalreference,
                provisiontype, conditions, signatoryrequirements, linkedviewid, linkedabacruleid,
                linkedchallengetype, isactive, sortorder
         FROM mandateprovision"""

  private type Row = (Option[String], Option[String], Option[String], Option[String],
    Option[String], Option[String], Option[String], Option[String], Option[String],
    Option[String], Option[String], Option[Boolean], Option[Int])

  private def fromRow(row: Row): MandateProvision = row match {
    case (provisionId, mandateId, provisionName, provisionDescription, legalReference,
          provisionType, conditions, signatoryRequirements, linkedViewId, linkedAbacRuleId,
          linkedChallengeType, isActive, sortOrder) =>
      MandateProvision(provisionId.orNull, mandateId.orNull, provisionName.orNull,
        provisionDescription.orNull, legalReference.orNull, provisionType.orNull,
        conditions.orNull, signatoryRequirements.orNull, linkedViewId.orNull,
        linkedAbacRuleId.orNull, linkedChallengeType.orNull,
        // The two readers differ. MappedBoolean's getter is `data openOr false` and a NULL sets
        // `data = Empty`, so Lift read a NULL flag as false however the field declared defaultValue.
        // MappedInt's is `if (isNull) defaultValue else v`, so a NULL count really did read as 0.
        isActive.getOrElse(false), sortOrder.getOrElse(0))
  }

  private def query(condition: Fragment): List[MandateProvision] =
    DoobieUtil.runQuery((selectColumns ++ condition).query[Row].to[List]).map(fromRow)

  private def opt(value: String): Option[String] = Option(value)

  def findByProvisionId(provisionId: String): Box[MandateProvision] =
    query(fr"WHERE provisionid = ${opt(provisionId)} LIMIT 1").headOption match {
      case Some(row) => Full(row)
      case None => Empty
    }

  /** Ordered by sortOrder — the clauses of a mandate are read in the order the drafter chose. */
  def findAllByMandateId(mandateId: String): List[MandateProvision] =
    query(fr"WHERE mandateid = ${opt(mandateId)} ORDER BY sortorder ASC")

  def insert(mandateId: String, provisionName: String, provisionDescription: String,
             legalReference: String, provisionType: String, conditions: String,
             signatoryRequirements: String, linkedViewId: String, linkedAbacRuleId: String,
             linkedChallengeType: String, isActive: Boolean, sortOrder: Int): MandateProvision = {
    val provisionId = APIUtil.generateUUID()
    val now = new java.sql.Timestamp(System.currentTimeMillis())
    DoobieUtil.runUpdate(
      sql"""INSERT INTO mandateprovision
            (provisionid, mandateid, provisionname, provisiondescription, legalreference,
             provisiontype, conditions, signatoryrequirements, linkedviewid, linkedabacruleid,
             linkedchallengetype, isactive, sortorder, createdat, updatedat)
            VALUES ($provisionId, ${opt(mandateId)}, ${opt(provisionName)},
             ${opt(provisionDescription)}, ${opt(legalReference)}, ${opt(provisionType)},
             ${opt(conditions)}, ${opt(signatoryRequirements)}, ${opt(linkedViewId)},
             ${opt(linkedAbacRuleId)}, ${opt(linkedChallengeType)}, $isActive, $sortOrder,
             $now, $now)"""
        .update.run)
    MandateProvision(provisionId, mandateId, provisionName, provisionDescription, legalReference,
      provisionType, conditions, signatoryRequirements, linkedViewId, linkedAbacRuleId,
      linkedChallengeType, isActive, sortOrder)
  }

  def updateByProvisionId(provisionId: String, provisionName: String, provisionDescription: String,
                          legalReference: String, provisionType: String, conditions: String,
                          signatoryRequirements: String, linkedViewId: String,
                          linkedAbacRuleId: String, linkedChallengeType: String, isActive: Boolean,
                          sortOrder: Int): Box[MandateProvision] = {
    val now = new java.sql.Timestamp(System.currentTimeMillis())
    DoobieUtil.runUpdate(
      sql"""UPDATE mandateprovision
            SET provisionname = ${opt(provisionName)},
                provisiondescription = ${opt(provisionDescription)},
                legalreference = ${opt(legalReference)}, provisiontype = ${opt(provisionType)},
                conditions = ${opt(conditions)},
                signatoryrequirements = ${opt(signatoryRequirements)},
                linkedviewid = ${opt(linkedViewId)}, linkedabacruleid = ${opt(linkedAbacRuleId)},
                linkedchallengetype = ${opt(linkedChallengeType)}, isactive = $isActive,
                sortorder = $sortOrder, updatedat = $now
            WHERE provisionid = ${opt(provisionId)}"""
        .update.run)
    findByProvisionId(provisionId)
  }

  def deleteByProvisionId(provisionId: String): Boolean =
    DoobieUtil.runUpdate(
      sql"DELETE FROM mandateprovision WHERE provisionid = ${opt(provisionId)}".update.run) > 0

  def deleteAll(): Unit = {
    DoobieUtil.runUpdate(sql"DELETE FROM mandateprovision".update.run)
    ()
  }
}

/**
 * A named group of users a mandate provision can require signatures from.
 *
 * `userIds` is a comma-separated list in one column, not a child table — the API layer joins and
 * splits it, and this store passes it through untouched.
 */
case class SignatoryPanel(
  panelId: String,
  mandateId: String,
  panelName: String,
  description: String,
  userIds: String
) extends SignatoryPanelTrait

object SignatoryPanel {

  private val selectColumns =
    fr"SELECT panelid, mandateid, panelname, description, userids FROM signatorypanel"

  private type Row = (Option[String], Option[String], Option[String], Option[String],
    Option[String])

  private def fromRow(row: Row): SignatoryPanel = row match {
    case (panelId, mandateId, panelName, description, userIds) =>
      SignatoryPanel(panelId.orNull, mandateId.orNull, panelName.orNull, description.orNull,
        userIds.orNull)
  }

  private def query(condition: Fragment): List[SignatoryPanel] =
    DoobieUtil.runQuery((selectColumns ++ condition).query[Row].to[List]).map(fromRow)

  private def opt(value: String): Option[String] = Option(value)

  def findByPanelId(panelId: String): Box[SignatoryPanel] =
    query(fr"WHERE panelid = ${opt(panelId)} LIMIT 1").headOption match {
      case Some(row) => Full(row)
      case None => Empty
    }

  def findAllByMandateId(mandateId: String): List[SignatoryPanel] =
    query(fr"WHERE mandateid = ${opt(mandateId)} ORDER BY panelname ASC")

  def insert(mandateId: String, panelName: String, description: String,
             userIds: String): SignatoryPanel = {
    val panelId = APIUtil.generateUUID()
    val now = new java.sql.Timestamp(System.currentTimeMillis())
    DoobieUtil.runUpdate(
      sql"""INSERT INTO signatorypanel
            (panelid, mandateid, panelname, description, userids, createdat, updatedat)
            VALUES ($panelId, ${opt(mandateId)}, ${opt(panelName)}, ${opt(description)},
             ${opt(userIds)}, $now, $now)"""
        .update.run)
    SignatoryPanel(panelId, mandateId, panelName, description, userIds)
  }

  def updateByPanelId(panelId: String, panelName: String, description: String,
                      userIds: String): Box[SignatoryPanel] = {
    val now = new java.sql.Timestamp(System.currentTimeMillis())
    DoobieUtil.runUpdate(
      sql"""UPDATE signatorypanel
            SET panelname = ${opt(panelName)}, description = ${opt(description)},
                userids = ${opt(userIds)}, updatedat = $now
            WHERE panelid = ${opt(panelId)}"""
        .update.run)
    findByPanelId(panelId)
  }

  def deleteByPanelId(panelId: String): Boolean =
    DoobieUtil.runUpdate(
      sql"DELETE FROM signatorypanel WHERE panelid = ${opt(panelId)}".update.run) > 0

  def deleteAll(): Unit = {
    DoobieUtil.runUpdate(sql"DELETE FROM signatorypanel".update.run)
    ()
  }
}

// ==================== Provider ====================

trait MandateProvider {
  // Mandate CRUD
  def getMandateById(mandateId: String): Box[MandateTrait]
  def getMandatesByBankAndAccount(bankId: String, accountId: String): Box[List[MandateTrait]]
  def getActiveMandatesByBankAndAccount(bankId: String, accountId: String): Box[List[MandateTrait]]
  def createMandate(
    bankId: String,
    accountId: String,
    customerId: String,
    mandateName: String,
    mandateReference: String,
    legalText: String,
    description: String,
    status: String,
    validFrom: Date,
    validTo: Date,
    createdByUserId: String
  ): Box[MandateTrait]
  def updateMandate(
    mandateId: String,
    mandateName: String,
    mandateReference: String,
    legalText: String,
    description: String,
    status: String,
    validFrom: Date,
    validTo: Date,
    updatedByUserId: String
  ): Box[MandateTrait]
  def deleteMandate(mandateId: String): Box[Boolean]

  // Mandate Provision CRUD
  def getMandateProvisionById(provisionId: String): Box[MandateProvisionTrait]
  def getMandateProvisionsByMandateId(mandateId: String): Box[List[MandateProvisionTrait]]
  def createMandateProvision(
    mandateId: String,
    provisionName: String,
    provisionDescription: String,
    legalReference: String,
    provisionType: String,
    conditions: String,
    signatoryRequirements: String,
    linkedViewId: String,
    linkedAbacRuleId: String,
    linkedChallengeType: String,
    isActive: Boolean,
    sortOrder: Int
  ): Box[MandateProvisionTrait]
  def updateMandateProvision(
    provisionId: String,
    provisionName: String,
    provisionDescription: String,
    legalReference: String,
    provisionType: String,
    conditions: String,
    signatoryRequirements: String,
    linkedViewId: String,
    linkedAbacRuleId: String,
    linkedChallengeType: String,
    isActive: Boolean,
    sortOrder: Int
  ): Box[MandateProvisionTrait]
  def deleteMandateProvision(provisionId: String): Box[Boolean]

  // Signatory Panel CRUD
  def getSignatoryPanelById(panelId: String): Box[SignatoryPanelTrait]
  def getSignatoryPanelsByMandateId(mandateId: String): Box[List[SignatoryPanelTrait]]
  def createSignatoryPanel(
    mandateId: String,
    panelName: String,
    description: String,
    userIds: String
  ): Box[SignatoryPanelTrait]
  def updateSignatoryPanel(
    panelId: String,
    panelName: String,
    description: String,
    userIds: String
  ): Box[SignatoryPanelTrait]
  def deleteSignatoryPanel(panelId: String): Box[Boolean]
}

// ==================== Mapped Provider ====================

object MappedMandateProvider extends MandateProvider {

  // ---- Mandate ----

  override def getMandateById(mandateId: String): Box[MandateTrait] =
    Mandate.findByMandateId(mandateId)

  override def getMandatesByBankAndAccount(bankId: String, accountId: String): Box[List[MandateTrait]] =
    tryo(Mandate.findAllByBankIdAndAccountId(bankId, accountId))

  override def getActiveMandatesByBankAndAccount(bankId: String, accountId: String): Box[List[MandateTrait]] =
    tryo(Mandate.findAllActiveByBankIdAndAccountId(bankId, accountId))

  override def createMandate(
    bankId: String,
    accountId: String,
    customerId: String,
    mandateName: String,
    mandateReference: String,
    legalText: String,
    description: String,
    status: String,
    validFrom: Date,
    validTo: Date,
    createdByUserId: String
  ): Box[MandateTrait] =
    tryo {
      Mandate.insert(bankId, accountId, customerId, mandateName, mandateReference, legalText,
        description, status, validFrom, validTo, createdByUserId)
    }

  override def updateMandate(
    mandateId: String,
    mandateName: String,
    mandateReference: String,
    legalText: String,
    description: String,
    status: String,
    validFrom: Date,
    validTo: Date,
    updatedByUserId: String
  ): Box[MandateTrait] =
    // Look the mandate up first so an unknown id stays Empty rather than becoming a silent no-op
    // that reports success.
    for {
      existing <- Mandate.findByMandateId(mandateId)
      updated <- Mandate.updateByMandateId(existing.mandateId, mandateName, mandateReference,
        legalText, description, status, validFrom, validTo, updatedByUserId)
    } yield updated

  override def deleteMandate(mandateId: String): Box[Boolean] =
    for {
      existing <- Mandate.findByMandateId(mandateId)
      deleted <- tryo(Mandate.deleteByMandateId(existing.mandateId))
    } yield deleted

  // ---- Mandate Provision ----

  override def getMandateProvisionById(provisionId: String): Box[MandateProvisionTrait] =
    MandateProvision.findByProvisionId(provisionId)

  override def getMandateProvisionsByMandateId(mandateId: String): Box[List[MandateProvisionTrait]] =
    tryo(MandateProvision.findAllByMandateId(mandateId))

  override def createMandateProvision(
    mandateId: String,
    provisionName: String,
    provisionDescription: String,
    legalReference: String,
    provisionType: String,
    conditions: String,
    signatoryRequirements: String,
    linkedViewId: String,
    linkedAbacRuleId: String,
    linkedChallengeType: String,
    isActive: Boolean,
    sortOrder: Int
  ): Box[MandateProvisionTrait] =
    tryo {
      MandateProvision.insert(mandateId, provisionName, provisionDescription, legalReference,
        provisionType, conditions, signatoryRequirements, linkedViewId, linkedAbacRuleId,
        linkedChallengeType, isActive, sortOrder)
    }

  override def updateMandateProvision(
    provisionId: String,
    provisionName: String,
    provisionDescription: String,
    legalReference: String,
    provisionType: String,
    conditions: String,
    signatoryRequirements: String,
    linkedViewId: String,
    linkedAbacRuleId: String,
    linkedChallengeType: String,
    isActive: Boolean,
    sortOrder: Int
  ): Box[MandateProvisionTrait] =
    for {
      existing <- MandateProvision.findByProvisionId(provisionId)
      updated <- MandateProvision.updateByProvisionId(existing.provisionId, provisionName,
        provisionDescription, legalReference, provisionType, conditions, signatoryRequirements,
        linkedViewId, linkedAbacRuleId, linkedChallengeType, isActive, sortOrder)
    } yield updated

  override def deleteMandateProvision(provisionId: String): Box[Boolean] =
    for {
      existing <- MandateProvision.findByProvisionId(provisionId)
      deleted <- tryo(MandateProvision.deleteByProvisionId(existing.provisionId))
    } yield deleted

  // ---- Signatory Panel ----

  override def getSignatoryPanelById(panelId: String): Box[SignatoryPanelTrait] =
    SignatoryPanel.findByPanelId(panelId)

  override def getSignatoryPanelsByMandateId(mandateId: String): Box[List[SignatoryPanelTrait]] =
    tryo(SignatoryPanel.findAllByMandateId(mandateId))

  override def createSignatoryPanel(
    mandateId: String,
    panelName: String,
    description: String,
    userIds: String
  ): Box[SignatoryPanelTrait] =
    tryo(SignatoryPanel.insert(mandateId, panelName, description, userIds))

  override def updateSignatoryPanel(
    panelId: String,
    panelName: String,
    description: String,
    userIds: String
  ): Box[SignatoryPanelTrait] =
    for {
      existing <- SignatoryPanel.findByPanelId(panelId)
      updated <- SignatoryPanel.updateByPanelId(existing.panelId, panelName, description, userIds)
    } yield updated

  override def deleteSignatoryPanel(panelId: String): Box[Boolean] =
    for {
      existing <- SignatoryPanel.findByPanelId(panelId)
      deleted <- tryo(SignatoryPanel.deleteByPanelId(existing.panelId))
    } yield deleted
}
