package code.mandate

import code.api.util.APIUtil
import net.liftweb.common.Box
import net.liftweb.mapper._
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

// ==================== Mapped Models ====================

class Mandate extends MandateTrait with LongKeyedMapper[Mandate] with IdPK with CreatedUpdated {
  def getSingleton = Mandate

  object MandateId extends MappedString(this, 255) {
    override def defaultValue = APIUtil.generateUUID()
  }
  object BankId extends MappedString(this, 255)
  object AccountId extends MappedString(this, 255)
  object CustomerId extends MappedString(this, 255)
  object MandateName extends MappedString(this, 255)
  object MandateReference extends MappedString(this, 255)
  object LegalText extends MappedText(this)
  object Description extends MappedText(this)
  object Status extends MappedString(this, 50) {
    override def defaultValue = "ACTIVE"
  }
  object ValidFrom extends MappedDateTime(this)
  object ValidTo extends MappedDateTime(this)
  object CreatedByUserId extends MappedString(this, 255)
  object UpdatedByUserId extends MappedString(this, 255)

  override def mandateId: String = MandateId.get
  override def bankId: String = BankId.get
  override def accountId: String = AccountId.get
  override def customerId: String = CustomerId.get
  override def mandateName: String = MandateName.get
  override def mandateReference: String = MandateReference.get
  override def legalText: String = LegalText.get
  override def description: String = Description.get
  override def status: String = Status.get
  override def validFrom: Date = ValidFrom.get
  override def validTo: Date = ValidTo.get
  override def createdByUserId: String = CreatedByUserId.get
  override def updatedByUserId: String = UpdatedByUserId.get
}

object Mandate extends Mandate with LongKeyedMetaMapper[Mandate] {
  override def dbIndexes: List[BaseIndex[Mandate]] =
    UniqueIndex(MandateId) ::
    Index(BankId, AccountId) ::
    Index(CustomerId) ::
    Index(MandateReference) ::
    super.dbIndexes
}

class MandateProvision extends MandateProvisionTrait with LongKeyedMapper[MandateProvision] with IdPK with CreatedUpdated {
  def getSingleton = MandateProvision

  object ProvisionId extends MappedString(this, 255) {
    override def defaultValue = APIUtil.generateUUID()
  }
  object MandateId extends MappedString(this, 255)
  object ProvisionName extends MappedString(this, 255)
  object ProvisionDescription extends MappedText(this)
  object LegalReference extends MappedString(this, 255)
  object ProvisionType extends MappedString(this, 50)
  object Conditions extends MappedText(this)
  object SignatoryRequirements extends MappedText(this)
  object LinkedViewId extends MappedString(this, 255)
  object LinkedAbacRuleId extends MappedString(this, 255)
  object LinkedChallengeType extends MappedString(this, 255)
  object IsActive extends MappedBoolean(this) {
    override def defaultValue = true
  }
  object SortOrder extends MappedInt(this) {
    override def defaultValue = 0
  }

  override def provisionId: String = ProvisionId.get
  override def mandateId: String = MandateId.get
  override def provisionName: String = ProvisionName.get
  override def provisionDescription: String = ProvisionDescription.get
  override def legalReference: String = LegalReference.get
  override def provisionType: String = ProvisionType.get
  override def conditions: String = Conditions.get
  override def signatoryRequirements: String = SignatoryRequirements.get
  override def linkedViewId: String = LinkedViewId.get
  override def linkedAbacRuleId: String = LinkedAbacRuleId.get
  override def linkedChallengeType: String = LinkedChallengeType.get
  override def isActive: Boolean = IsActive.get
  override def sortOrder: Int = SortOrder.get
}

object MandateProvision extends MandateProvision with LongKeyedMetaMapper[MandateProvision] {
  override def dbIndexes: List[BaseIndex[MandateProvision]] =
    UniqueIndex(ProvisionId) ::
    Index(MandateId) ::
    super.dbIndexes
}

class SignatoryPanel extends SignatoryPanelTrait with LongKeyedMapper[SignatoryPanel] with IdPK with CreatedUpdated {
  def getSingleton = SignatoryPanel

  object PanelId extends MappedString(this, 255) {
    override def defaultValue = APIUtil.generateUUID()
  }
  object MandateId extends MappedString(this, 255)
  object PanelName extends MappedString(this, 255)
  object Description extends MappedText(this)
  object UserIds extends MappedText(this)

  override def panelId: String = PanelId.get
  override def mandateId: String = MandateId.get
  override def panelName: String = PanelName.get
  override def description: String = Description.get
  override def userIds: String = UserIds.get
}

object SignatoryPanel extends SignatoryPanel with LongKeyedMetaMapper[SignatoryPanel] {
  override def dbIndexes: List[BaseIndex[SignatoryPanel]] =
    UniqueIndex(PanelId) ::
    Index(MandateId) ::
    super.dbIndexes
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

  override def getMandateById(mandateId: String): Box[MandateTrait] = {
    Mandate.find(By(Mandate.MandateId, mandateId))
  }

  override def getMandatesByBankAndAccount(bankId: String, accountId: String): Box[List[MandateTrait]] = {
    tryo {
      Mandate.findAll(
        By(Mandate.BankId, bankId),
        By(Mandate.AccountId, accountId),
        OrderBy(Mandate.updatedAt, Descending)
      )
    }
  }

  override def getActiveMandatesByBankAndAccount(bankId: String, accountId: String): Box[List[MandateTrait]] = {
    tryo {
      Mandate.findAll(
        By(Mandate.BankId, bankId),
        By(Mandate.AccountId, accountId),
        By(Mandate.Status, "ACTIVE"),
        OrderBy(Mandate.updatedAt, Descending)
      )
    }
  }

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
  ): Box[MandateTrait] = {
    tryo {
      Mandate.create
        .BankId(bankId)
        .AccountId(accountId)
        .CustomerId(customerId)
        .MandateName(mandateName)
        .MandateReference(mandateReference)
        .LegalText(legalText)
        .Description(description)
        .Status(status)
        .ValidFrom(validFrom)
        .ValidTo(validTo)
        .CreatedByUserId(createdByUserId)
        .UpdatedByUserId(createdByUserId)
        .saveMe()
    }
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
  ): Box[MandateTrait] = {
    for {
      mandate <- Mandate.find(By(Mandate.MandateId, mandateId))
      updated <- tryo {
        mandate
          .MandateName(mandateName)
          .MandateReference(mandateReference)
          .LegalText(legalText)
          .Description(description)
          .Status(status)
          .ValidFrom(validFrom)
          .ValidTo(validTo)
          .UpdatedByUserId(updatedByUserId)
          .saveMe()
      }
    } yield updated
  }

  override def deleteMandate(mandateId: String): Box[Boolean] = {
    for {
      mandate <- Mandate.find(By(Mandate.MandateId, mandateId))
      deleted <- tryo(mandate.delete_!)
    } yield deleted
  }

  // ---- Mandate Provision ----

  override def getMandateProvisionById(provisionId: String): Box[MandateProvisionTrait] = {
    MandateProvision.find(By(MandateProvision.ProvisionId, provisionId))
  }

  override def getMandateProvisionsByMandateId(mandateId: String): Box[List[MandateProvisionTrait]] = {
    tryo {
      MandateProvision.findAll(
        By(MandateProvision.MandateId, mandateId),
        OrderBy(MandateProvision.SortOrder, Ascending)
      )
    }
  }

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
  ): Box[MandateProvisionTrait] = {
    tryo {
      MandateProvision.create
        .MandateId(mandateId)
        .ProvisionName(provisionName)
        .ProvisionDescription(provisionDescription)
        .LegalReference(legalReference)
        .ProvisionType(provisionType)
        .Conditions(conditions)
        .SignatoryRequirements(signatoryRequirements)
        .LinkedViewId(linkedViewId)
        .LinkedAbacRuleId(linkedAbacRuleId)
        .LinkedChallengeType(linkedChallengeType)
        .IsActive(isActive)
        .SortOrder(sortOrder)
        .saveMe()
    }
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
  ): Box[MandateProvisionTrait] = {
    for {
      provision <- MandateProvision.find(By(MandateProvision.ProvisionId, provisionId))
      updated <- tryo {
        provision
          .ProvisionName(provisionName)
          .ProvisionDescription(provisionDescription)
          .LegalReference(legalReference)
          .ProvisionType(provisionType)
          .Conditions(conditions)
          .SignatoryRequirements(signatoryRequirements)
          .LinkedViewId(linkedViewId)
          .LinkedAbacRuleId(linkedAbacRuleId)
          .LinkedChallengeType(linkedChallengeType)
          .IsActive(isActive)
          .SortOrder(sortOrder)
          .saveMe()
      }
    } yield updated
  }

  override def deleteMandateProvision(provisionId: String): Box[Boolean] = {
    for {
      provision <- MandateProvision.find(By(MandateProvision.ProvisionId, provisionId))
      deleted <- tryo(provision.delete_!)
    } yield deleted
  }

  // ---- Signatory Panel ----

  override def getSignatoryPanelById(panelId: String): Box[SignatoryPanelTrait] = {
    SignatoryPanel.find(By(SignatoryPanel.PanelId, panelId))
  }

  override def getSignatoryPanelsByMandateId(mandateId: String): Box[List[SignatoryPanelTrait]] = {
    tryo {
      SignatoryPanel.findAll(
        By(SignatoryPanel.MandateId, mandateId),
        OrderBy(SignatoryPanel.PanelName, Ascending)
      )
    }
  }

  override def createSignatoryPanel(
    mandateId: String,
    panelName: String,
    description: String,
    userIds: String
  ): Box[SignatoryPanelTrait] = {
    tryo {
      SignatoryPanel.create
        .MandateId(mandateId)
        .PanelName(panelName)
        .Description(description)
        .UserIds(userIds)
        .saveMe()
    }
  }

  override def updateSignatoryPanel(
    panelId: String,
    panelName: String,
    description: String,
    userIds: String
  ): Box[SignatoryPanelTrait] = {
    for {
      panel <- SignatoryPanel.find(By(SignatoryPanel.PanelId, panelId))
      updated <- tryo {
        panel
          .PanelName(panelName)
          .Description(description)
          .UserIds(userIds)
          .saveMe()
      }
    } yield updated
  }

  override def deleteSignatoryPanel(panelId: String): Box[Boolean] = {
    for {
      panel <- SignatoryPanel.find(By(SignatoryPanel.PanelId, panelId))
      deleted <- tryo(panel.delete_!)
    } yield deleted
  }
}
