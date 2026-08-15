package code.amqpbroker

import net.liftweb.common.Box
import net.liftweb.mapper._

/**
 * Per-bank AMQP broker coordinates — where OBP-API publishes messages destined
 * for a bank's own infrastructure.
 *
 * Named by transport, not by consumer: the fields (host/port/vhost/credentials)
 * are AMQP 0-9-1 concepts, and any feature that needs to push AMQP messages to
 * a specific bank resolves its coordinates here. The first consumer is Open
 * Corridor Interface C: each onboarded bank's Bank Node consumes on its OWN
 * vhost (e.g. `/bank.ke.01.kcs`) with its own credentials — permission
 * isolation is enforced at the broker level, and publishing is keyed by
 * bank_id through this registry (populated at onboarding).
 *
 * Transport coordinates only: the bank's on-chain settlement address is NOT
 * stored here — it is the CARDANO account routing on the bank's
 * OBP-INCOMING-SETTLEMENT-ACCOUNT.
 */
class AmqpBankBroker extends LongKeyedMapper[AmqpBankBroker] with IdPK {
  def getSingleton: code.amqpbroker.AmqpBankBroker.type = AmqpBankBroker

  object BankId extends MappedString(this, 255) {
    override def dbColumnName = "bank_id"
  }
  object Host extends MappedString(this, 255) {
    override def dbColumnName = "host"
  }
  object Port extends MappedInt(this) {
    override def dbColumnName = "port"
    override def defaultValue = 5672
  }
  object VirtualHost extends MappedString(this, 255) {
    override def dbColumnName = "virtual_host"
  }
  object Username extends MappedString(this, 255) {
    override def dbColumnName = "username"
  }
  /** Write-only: accepted on registration, never echoed by any endpoint. */
  object Password extends MappedString(this, 255) {
    override def dbColumnName = "password"
  }
  object UseSsl extends MappedBoolean(this) {
    override def dbColumnName = "use_ssl"
    override def defaultValue = false
  }
  object CreatedAt extends MappedDateTime(this) {
    override def dbColumnName = "created_at"
    override def defaultValue = new java.util.Date()
  }
  object UpdatedAt extends MappedDateTime(this) {
    override def dbColumnName = "updated_at"
    override def defaultValue = new java.util.Date()
  }

  def bankId: String = BankId.get
  def host: String = Host.get
  def port: Int = Port.get
  def virtualHost: String = VirtualHost.get
  def username: String = Username.get
  def password: String = Password.get
  def useSsl: Boolean = UseSsl.get

  override def save: Boolean = {
    UpdatedAt(new java.util.Date())
    super.save
  }
}

object AmqpBankBroker extends AmqpBankBroker with LongKeyedMetaMapper[AmqpBankBroker] {
  override def dbTableName = "amqp_bank_broker"

  override def dbIndexes: List[BaseIndex[AmqpBankBroker]] = UniqueIndex(BankId) :: super.dbIndexes

  def findByBankId(bankId: String): Box[AmqpBankBroker] =
    AmqpBankBroker.find(By(AmqpBankBroker.BankId, bankId))

  /** Upsert the broker coordinates for a bank (one row per bank, enforced by the unique index). */
  def upsert(
    bankId: String,
    host: String,
    port: Int,
    virtualHost: String,
    username: String,
    password: String,
    useSsl: Boolean
  ): AmqpBankBroker = {
    val row = findByBankId(bankId).getOrElse(AmqpBankBroker.create.BankId(bankId))
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
    AmqpBankBroker.bulkDelete_!!(By(AmqpBankBroker.BankId, bankId))
}
