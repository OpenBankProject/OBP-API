package code.bankconnectors.opencorridor

import net.liftweb.common.Box
import net.liftweb.mapper._

/**
 * Per-bank RabbitMQ broker coordinates for Open Corridor Interface C publishing.
 *
 * Each onboarded bank's Bank Node consumes on its OWN vhost (e.g. `/bank.ke.01.kcs`)
 * with its own credentials — permission isolation is enforced at the broker level.
 * Even a single corridor involves two vhosts (`obp_credit_notification` goes to the
 * creditor bank's vhost, `obp_settlement_instruction` to the debtor's), so a single
 * global broker connection cannot serve the flow; publishing is keyed by bank_id
 * through this registry (populated at onboarding).
 */
class OpenCorridorBankBroker extends LongKeyedMapper[OpenCorridorBankBroker] with IdPK with CreatedUpdated {
  def getSingleton = OpenCorridorBankBroker

  object BankId extends MappedString(this, 255)
  object Host extends MappedString(this, 255)
  object Port extends MappedInt(this) {
    override def defaultValue = 5672
  }
  object VirtualHost extends MappedString(this, 255)
  object Username extends MappedString(this, 255)
  object Password extends MappedString(this, 255)
  object UseSsl extends MappedBoolean(this) {
    override def defaultValue = false
  }
  // The settlement-rail receiving address is NOT stored here (closes open decision
  // §8.5 of the publish plan): it lives as the CARDANO account routing on the
  // bank's OBP-INCOMING-SETTLEMENT-ACCOUNT — the broker row is transport only.

  def bankId: String = BankId.get
  def host: String = Host.get
  def port: Int = Port.get
  def virtualHost: String = VirtualHost.get
  def username: String = Username.get
  def password: String = Password.get
  def useSsl: Boolean = UseSsl.get
}

object OpenCorridorBankBroker extends OpenCorridorBankBroker with LongKeyedMetaMapper[OpenCorridorBankBroker] {
  override def dbIndexes: List[BaseIndex[OpenCorridorBankBroker]] = UniqueIndex(BankId) :: super.dbIndexes

  def findByBankId(bankId: String): Box[OpenCorridorBankBroker] =
    OpenCorridorBankBroker.find(By(OpenCorridorBankBroker.BankId, bankId))

  /** Upsert the broker coordinates for a bank (one row per bank, enforced by the unique index). */
  def upsert(
    bankId: String,
    host: String,
    port: Int,
    virtualHost: String,
    username: String,
    password: String,
    useSsl: Boolean
  ): OpenCorridorBankBroker = {
    val row = findByBankId(bankId).getOrElse(OpenCorridorBankBroker.create.BankId(bankId))
    row
      .Host(host)
      .Port(port)
      .VirtualHost(virtualHost)
      .Username(username)
      .Password(password)
      .UseSsl(useSsl)
      .saveMe()
  }

  def deleteByBankId(bankId: String): Boolean =
    OpenCorridorBankBroker.bulkDelete_!!(By(OpenCorridorBankBroker.BankId, bankId))
}
