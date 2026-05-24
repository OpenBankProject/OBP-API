package code.consent

import code.util.MappedUUID
import net.liftweb.mapper._

// consent_item denormalises key fields (bank_id, account_id, view_id, role_name) from the consent JWT
// so that bank-scoped queries can be done via a simple indexed SQL join instead of extracting and
// parsing every JWT. Rows are written at consent creation time alongside JWT generation.
class ConsentItem extends LongKeyedMapper[ConsentItem] with IdPK {
  def getSingleton = ConsentItem

  object consentItemId extends MappedUUID(this) {
    override def dbColumnName = "consent_item_id"
  }
  object consentReferenceId extends MappedString(this, 36) {
    override def dbColumnName = "consent_reference_id"
  }
  object itemType extends MappedString(this, 64) {
    override def dbColumnName = "item_type"
  }
  object bankId extends MappedString(this, 255) {
    override def dbColumnName = "bank_id"
  }
  object accountId extends MappedString(this, 255) {
    override def dbColumnName = "account_id"
    override def defaultValue = null
  }
  object viewId extends MappedString(this, 255) {
    override def dbColumnName = "view_id"
    override def defaultValue = null
  }
  object roleName extends MappedString(this, 255) {
    override def dbColumnName = "role_name"
    override def defaultValue = null
  }
}

object ConsentItem extends ConsentItem with LongKeyedMetaMapper[ConsentItem] {
  override def dbTableName = "consent_item"
  override def dbIndexes = UniqueIndex(consentItemId) :: Index(consentReferenceId) :: Index(bankId) :: Index(consentReferenceId, bankId) :: super.dbIndexes
}
