package code.amqpbroker

import code.api.util.DoobieUtil
import doobie._
import doobie.implicits._
import doobie.implicits.javasql._
import net.liftweb.common.{Box, Empty, Full}

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
 *
 * `password` is write-only by contract: accepted on registration and used when
 * connecting, never echoed by any endpoint.
 */
case class AmqpBankBroker(
  bankId: String,
  host: String,
  port: Int,
  virtualHost: String,
  username: String,
  password: String,
  useSsl: Boolean
)

object AmqpBankBroker {

  /** Mapper's `MappedInt` default for the port column. */
  private val DefaultPort = 5672

  private val selectColumns =
    fr"SELECT bank_id, host, port, virtual_host, username, password, use_ssl FROM amqp_bank_broker"

  private type Row = (Option[String], Option[String], Option[Int], Option[String],
    Option[String], Option[String], Option[Boolean])

  private def fromRow(row: Row): AmqpBankBroker = row match {
    case (bankId, host, port, virtualHost, username, password, useSsl) =>
      // MappedInt read a NULL as the declared default (5672); MappedBoolean read one as false.
      // Both columns predate no row today, but neither reader ever failed, and a bare Int or
      // Boolean here would fail the whole query on a row that has been through an upgrade.
      AmqpBankBroker(bankId.orNull, host.orNull, port.getOrElse(DefaultPort), virtualHost.orNull,
        username.orNull, password.orNull, useSsl.getOrElse(false))
  }

  def findByBankId(bankId: String): Box[AmqpBankBroker] =
    DoobieUtil.runQuery(
      (selectColumns ++ fr"WHERE bank_id = $bankId LIMIT 1").query[Row].option
    ) match {
      case Some(row) => Full(fromRow(row))
      case None => Empty
    }

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
    val now = new java.sql.Timestamp(System.currentTimeMillis())
    findByBankId(bankId) match {
      case Full(_) =>
        DoobieUtil.runUpdate(
          sql"""UPDATE amqp_bank_broker
                SET host = $host, port = $port, virtual_host = $virtualHost, username = $username,
                    password = $password, use_ssl = $useSsl, updated_at = $now
                WHERE bank_id = $bankId"""
            .update.run)
      case _ =>
        DoobieUtil.runUpdate(
          sql"""INSERT INTO amqp_bank_broker
                (bank_id, host, port, virtual_host, username, password, use_ssl, created_at, updated_at)
                VALUES ($bankId, $host, $port, $virtualHost, $username, $password, $useSsl, $now, $now)"""
            .update.run)
    }
    AmqpBankBroker(bankId, host, port, virtualHost, username, password, useSsl)
  }

  def deleteByBankId(bankId: String): Boolean =
    DoobieUtil.runUpdate(sql"DELETE FROM amqp_bank_broker WHERE bank_id = $bankId".update.run) > 0

  def deleteAll(): Unit = {
    DoobieUtil.runUpdate(sql"DELETE FROM amqp_bank_broker".update.run)
    ()
  }
}
