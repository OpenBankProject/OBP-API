/**
Open Bank Project - API
Copyright (C) 2011-2026, TESOBE GmbH.

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.

Email: contact@tesobe.com
TESOBE GmbH.
Osloer Strasse 16/17
Berlin 13359, Germany

This product includes software developed at
TESOBE (http://www.tesobe.com/)

  */

package code.signingbaskets

import code.api.berlin.group.ConstantsBG
import code.util.MappedUUID
import com.openbankproject.commons.model.{SigningBasketConsentTrait, SigningBasketContent, SigningBasketPaymentTrait, SigningBasketTrait}
import code.api.util.ErrorMessages.SigningBasketMemberStatusInvalid
import net.liftweb.common.{Box, Failure, Full}
import net.liftweb.common.Box.tryo
import net.liftweb.db.DB
import net.liftweb.mapper._
import net.liftweb.util.DefaultConnectionIdentifier

object MappedSigningBasketProvider extends SigningBasketProvider {
  private class MemberAlreadyHeld extends RuntimeException("A member of the basket is already held by another basket")

  def getSigningBaskets(): List[SigningBasketTrait] = {
    MappedSigningBasket.findAll()
  }

  private def membersOf(basketId: String): (Option[List[String]], Option[List[String]]) = {
    val payments = MappedSigningBasketPayment.findAll(By(MappedSigningBasketPayment.BasketId, basketId), OrderBy(MappedSigningBasketPayment.id, Ascending)).map(_.paymentId) match {
      case Nil => None
      case members => Some(members)
    }
    val consents = MappedSigningBasketConsent.findAll(By(MappedSigningBasketConsent.BasketId, basketId), OrderBy(MappedSigningBasketConsent.id, Ascending)).map(_.consentId) match {
      case Nil => None
      case members => Some(members)
    }
    (payments, consents)
  }

  override def getSigningBasketByBasketId(entityId: String): Box[SigningBasketContent] = {
    val basket: Box[MappedSigningBasket] = MappedSigningBasket.find(By(MappedSigningBasket.BasketId, entityId))
    val (payments, consents) = membersOf(entityId)
    basket.map(i => SigningBasketContent(basket = i, payments = payments, consents = consents))
  }

  override def createSigningBasket(paymentIds: Option[List[String]],
                                   consentIds: Option[List[String]],
                                   consumerId: String,
                                   psuUserId: Option[String]
                                  ): Box[SigningBasketTrait] = {
    // The basket and every member row are written inside one DB.use. Outside a request a failure part way
    // rolls all of it back. Inside an HTTP request the connection is the request's own, whose rollback is
    // not ours to call, so what was written is deleted again by hand where the database lets the
    // transaction go on. PostgreSQL does not: a failed statement (a unique-index violation, say) aborts the
    // transaction, the deletes fail as well, and it is the request's own commit of the aborted transaction,
    // which PostgreSQL turns into a rollback, that leaves nothing of the basket behind. That rollback takes
    // everything else the request wrote with it (an idempotency record, for one), and the refusal is
    // still answered.
    var created: Option[MappedSigningBasket] = None
    val memberKeys =
      paymentIds.getOrElse(Nil).map(id => s"payment:$id") ::: consentIds.getOrElse(Nil).map(id => s"consent:$id")
    val result = tryo {
      DB.use(DefaultConnectionIdentifier) { _ =>
        val entity = MappedSigningBasket.create
          .Status(ConstantsBG.SigningBasketsStatus.RCVD.toString)
          .ConsumerId(consumerId)
          .PsuUserId(psuUserId.getOrElse(""))
        if (entity.validate.isEmpty) {
          entity.saveMe()
        } else {
          throw new Error(entity.validate.map(_.msg.toString()).mkString(";"))
        }
        created = Some(entity)
        // Held by one active basket at a time. The check is the usual answer; the unique index on the
        // claim is what holds if two requests get past it together.
        memberKeys.foreach { key =>
          if (MappedSigningBasketMemberClaim.find(By(MappedSigningBasketMemberClaim.MemberKey, key)).isDefined)
            throw new MemberAlreadyHeld
          MappedSigningBasketMemberClaim.create.MemberKey(key).BasketId(entity.basketId).saveMe()
        }
        paymentIds.getOrElse(Nil).foreach { paymentId =>
          MappedSigningBasketPayment.create.BasketId(entity.basketId).PaymentId(paymentId).saveMe()
        }
        consentIds.getOrElse(Nil).foreach { consentId =>
          MappedSigningBasketConsent.create.BasketId(entity.basketId).ConsentId(consentId).saveMe()
        }
        entity: SigningBasketTrait
      }
    }
    if (result.isEmpty) created.foreach { basket =>
      tryo {
        MappedSigningBasketMemberExecution.bulkDelete_!!(By(MappedSigningBasketMemberExecution.BasketId, basket.basketId))
        MappedSigningBasketMemberClaim.bulkDelete_!!(By(MappedSigningBasketMemberClaim.BasketId, basket.basketId))
        MappedSigningBasketPayment.bulkDelete_!!(By(MappedSigningBasketPayment.BasketId, basket.basketId))
        MappedSigningBasketConsent.bulkDelete_!!(By(MappedSigningBasketConsent.BasketId, basket.basketId))
        basket.delete_!
      }
    }
    result match {
      case Failure(_, Full(_: MemberAlreadyHeld), _) => Failure(SigningBasketMemberStatusInvalid)
      // Two requests that both got past the check above: the unique index on the claim let one through.
      case Failure(_, Full(error), _) if isConstraintViolation(error) => Failure(SigningBasketMemberStatusInvalid)
      case other => other
    }
  }

  override def createSigningBasketMemberExecutions(basketId: String, members: List[(String, String)]): Box[Boolean] =
    tryo {
      val m = MappedSigningBasketMemberExecution
      members.zipWithIndex.foreach { case ((memberType, memberId), position) =>
        // One ledger transaction per member: a member recorded by an executor racing this one makes the insert
        // violate the unique index, which only says it is there already.
        try {
          inLedger { connection =>
            val recorded = queryLedger(connection,
              s"SELECT COUNT(*) FROM ${m.dbTableName} WHERE ${m.BasketId._dbColumnNameLC} = ? AND ${m.MemberType._dbColumnNameLC} = ? AND ${m.MemberId._dbColumnNameLC} = ?",
              List(basketId, memberType, memberId))(_.getInt(1)).headOption.getOrElse(0) > 0
            if (!recorded)
              updateLedger(connection,
                s"INSERT INTO ${m.dbTableName} (${m.BasketId._dbColumnNameLC}, ${m.MemberType._dbColumnNameLC}, ${m.MemberId._dbColumnNameLC}, " +
                  s"${m.Position._dbColumnNameLC}, ${m.State._dbColumnNameLC}, ${m.Detail._dbColumnNameLC}, ${m.Attempts._dbColumnNameLC}, " +
                  s"${m.createdAt._dbColumnNameLC}, ${m.updatedAt._dbColumnNameLC}) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                List[Any](basketId, memberType, memberId, position, SigningBasketMemberState.Pending, "", 0, now, now))
          }
        } catch {
          case error: Throwable if isConstraintViolation(error) => ()
        }
      }
      true
    }

  override def getSigningBasketMemberExecutions(basketId: String): List[SigningBasketMemberExecution] = {
    val m = MappedSigningBasketMemberExecution
    inLedger { connection =>
      queryLedger(connection,
        s"SELECT ${m.MemberType._dbColumnNameLC}, ${m.MemberId._dbColumnNameLC}, ${m.Position._dbColumnNameLC}, ${m.State._dbColumnNameLC}, " +
          s"${m.Detail._dbColumnNameLC}, ${m.Attempts._dbColumnNameLC} FROM ${m.dbTableName} WHERE ${m.BasketId._dbColumnNameLC} = ? " +
          s"ORDER BY ${m.Position._dbColumnNameLC}",
        List(basketId))(row => SigningBasketMemberExecution(
          row.getString(1), row.getString(2), row.getInt(3), row.getString(4), Option(row.getString(5)).getOrElse(""), row.getInt(6)))
    }
  }

  /**
   * Runs `work` on a connection of its own and commits it before returning, whatever request it is called from.
   *
   * The execution ledger is the record of what was done to the outside world: that a basket was claimed, that
   * a member was being executed, that it finished. An HTTP request's database work is one transaction that
   * commits only when the response is sent, so a ledger written inside it is lost together with it when the
   * node dies after a remote connector has booked a payment but before the response: the basket would be back
   * to RCVD, nothing would say a booking was under way, and the same answer could be sent again.
   *
   * It takes a connection from the pool directly, not through the connection manager, which would hand out
   * the request's own connection. A request therefore holds two connections while it executes a basket.
   * The ledger rows are written only through here, so the request's connection never holds a lock on them.
   */
  private def inLedger[A](work: java.sql.Connection => A): A = {
    val connection = code.api.util.APIUtil.vendor.newConnection(DefaultConnectionIdentifier)
      .openOrThrowException("No database connection could be taken for the signing basket ledger")
    try {
      connection.setAutoCommit(false)
      val result = work(connection)
      connection.commit()
      result
    } catch {
      case error: Throwable =>
        try connection.rollback() catch { case _: Exception => () }
        throw error
    } finally {
      try connection.close() catch { case _: Exception => () }
    }
  }

  private def updateLedger(connection: java.sql.Connection, sql: String, params: List[Any]): Int = {
    val statement = connection.prepareStatement(sql)
    try {
      params.zipWithIndex.foreach { case (value, index) => statement.setObject(index + 1, value) }
      statement.executeUpdate()
    } finally statement.close()
  }

  private def queryLedger[A](connection: java.sql.Connection, sql: String, params: List[Any])(read: java.sql.ResultSet => A): List[A] = {
    val statement = connection.prepareStatement(sql)
    try {
      params.zipWithIndex.foreach { case (value, index) => statement.setObject(index + 1, value) }
      val rows = statement.executeQuery()
      try {
        val buffer = scala.collection.mutable.ListBuffer.empty[A]
        while (rows.next()) buffer += read(rows)
        buffer.toList
      } finally rows.close()
    } finally statement.close()
  }

  private def ledgerUpdate(sql: String, params: List[Any]): Int = inLedger(updateLedger(_, sql, params))

  // Every timestamp this provider writes or compares comes from the JVM, as the Mapper's own createdAt/updatedAt
  // do. The database's CURRENT_TIMESTAMP is the database server's clock and zone, which need not be the JVM's.
  private def now = new java.sql.Timestamp(System.currentTimeMillis)

  /** Whether the failure is a unique/integrity constraint violation (SQLState class 23), however deeply wrapped. */
  private def isConstraintViolation(error: Throwable): Boolean = {
    def inChain(t: Throwable, depth: Int): Boolean =
      t != null && depth < 10 && (t match {
        case sql: java.sql.SQLException =>
          Option(sql.getSQLState).exists(_.startsWith("23")) || inChain(sql.getNextException, depth + 1) || inChain(sql.getCause, depth + 1)
        case _ => inChain(t.getCause, depth + 1)
      })
    inChain(error, 0)
  }

  override def transitionSigningBasketMemberExecution(basketId: String,
                                                      memberType: String,
                                                      memberId: String,
                                                      from: Set[String],
                                                      to: String,
                                                      detail: String): Box[Boolean] =
    tryo {
      val m = MappedSigningBasketMemberExecution
      val fromList = from.toList
      // A claim is the move to EXECUTING, and counts as an attempt.
      val attemptsSql = if (to == SigningBasketMemberState.Executing) s", ${m.Attempts._dbColumnNameLC} = ${m.Attempts._dbColumnNameLC} + 1" else ""
      ledgerUpdate(
        s"UPDATE ${m.dbTableName} SET ${m.State._dbColumnNameLC} = ?, ${m.Detail._dbColumnNameLC} = ?, " +
          s"${m.updatedAt._dbColumnNameLC} = ?$attemptsSql " +
          s"WHERE ${m.BasketId._dbColumnNameLC} = ? AND ${m.MemberType._dbColumnNameLC} = ? AND ${m.MemberId._dbColumnNameLC} = ? " +
          s"AND ${m.State._dbColumnNameLC} IN (${fromList.map(_ => "?").mkString(", ")})",
        List[Any](to, detail.take(2000), now, basketId, memberType, memberId) ++ fromList) == 1
    }

  override def markStaleSigningBasketMembersUnknown(olderThanSeconds: Long): Box[Int] =
    tryo {
      val m = MappedSigningBasketMemberExecution
      val cutoff = new java.sql.Timestamp(System.currentTimeMillis() - olderThanSeconds * 1000)
      ledgerUpdate(
        s"UPDATE ${m.dbTableName} SET ${m.State._dbColumnNameLC} = ?, ${m.Detail._dbColumnNameLC} = ?, " +
          s"${m.updatedAt._dbColumnNameLC} = ? " +
          s"WHERE ${m.State._dbColumnNameLC} = ? AND ${m.updatedAt._dbColumnNameLC} < ?",
        List[Any](SigningBasketMemberState.Unknown, "The executor stopped before recording an outcome", now, SigningBasketMemberState.Executing, cutoff))
    }

  override def getSigningBasketsAwaitingExecution(olderThanSeconds: Long, limit: Int): List[String] = {
    val cutoff = new java.util.Date(System.currentTimeMillis() - olderThanSeconds * 1000)
    MappedSigningBasket.findAll(
      ByList(MappedSigningBasket.Status, List(ConstantsBG.SigningBasketsStatus.AUTHORISING_INTERNAL, ConstantsBG.SigningBasketsStatus.EXECUTION_INCOMPLETE_INTERNAL)),
      BySql[MappedSigningBasket](s"${MappedSigningBasket.updatedAt._dbColumnNameLC} < ?", IHaveValidatedThisSQL("signing-basket", "2026-10-06"), cutoff),
      OrderBy(MappedSigningBasket.updatedAt, Ascending),
      MaxRows(limit)
    ).map(_.basketId)
  }

  // Through the ledger, like the status change it goes with: if the status were committed and the release lost
  // with a request, the basket would be final and still hold its members.
  override def releaseSigningBasketMembers(basketId: String): Box[Boolean] =
    tryo {
      val claims = MappedSigningBasketMemberClaim
      ledgerUpdate(s"DELETE FROM ${claims.dbTableName} WHERE ${claims.BasketId._dbColumnNameLC} = ?", List(basketId))
      true
    }

  override def memberHeldByBasket(memberKey: String): Boolean =
    MappedSigningBasketMemberClaim.find(By(MappedSigningBasketMemberClaim.MemberKey, memberKey)).isDefined

  override def transitionSigningBasketStatus(basketId: String, from: String, to: String): Box[Boolean] =
    tryo {
      ledgerUpdate(
        s"UPDATE ${MappedSigningBasket.dbTableName} " +
          s"SET ${MappedSigningBasket.Status._dbColumnNameLC} = ?, ${MappedSigningBasket.updatedAt._dbColumnNameLC} = ? " +
          s"WHERE ${MappedSigningBasket.BasketId._dbColumnNameLC} = ? AND ${MappedSigningBasket.Status._dbColumnNameLC} = ?",
        List[Any](to, now, basketId, from)) == 1
    }

  override def touchSigningBasket(basketId: String): Box[Boolean] =
    tryo {
      val statuses = List(ConstantsBG.SigningBasketsStatus.AUTHORISING_INTERNAL, ConstantsBG.SigningBasketsStatus.EXECUTION_INCOMPLETE_INTERNAL)
      ledgerUpdate(
        s"UPDATE ${MappedSigningBasket.dbTableName} SET ${MappedSigningBasket.updatedAt._dbColumnNameLC} = ? " +
          s"WHERE ${MappedSigningBasket.BasketId._dbColumnNameLC} = ? " +
          s"AND ${MappedSigningBasket.Status._dbColumnNameLC} IN (${statuses.map(_ => "?").mkString(", ")})",
        List[Any](now, basketId) ++ statuses) == 1
    }

  override def bindSigningBasketPsu(basketId: String, psuUserId: String): Box[Boolean] =
    tryo {
      val bound = DB.runUpdate(
        s"UPDATE ${MappedSigningBasket.dbTableName} " +
          s"SET ${MappedSigningBasket.PsuUserId._dbColumnNameLC} = ?, ${MappedSigningBasket.updatedAt._dbColumnNameLC} = ? " +
          s"WHERE ${MappedSigningBasket.BasketId._dbColumnNameLC} = ? " +
          s"AND (${MappedSigningBasket.PsuUserId._dbColumnNameLC} IS NULL OR ${MappedSigningBasket.PsuUserId._dbColumnNameLC} = '')",
        List[Any](psuUserId, now, basketId)) == 1
      // Not bound by this call: that is only a success if the basket was already bound to this PSU.
      bound || MappedSigningBasket.find(By(MappedSigningBasket.BasketId, basketId)).exists(_.psuUserId.contains(psuUserId))
    }

}

class MappedSigningBasket extends SigningBasketTrait with LongKeyedMapper[MappedSigningBasket] with IdPK with CreatedUpdated {
  override def getSingleton = MappedSigningBasket
  object BasketId extends MappedUUID(this)
  object Status extends MappedString(this, 50)
  // The consumer (TPP) that created the basket. Empty, or null, on a basket created before this was recorded.
  object ConsumerId extends MappedString(this, 255)
  // The PSU the basket is for, once known (named on creation, or bound when an authorisation starts).
  object PsuUserId extends MappedString(this, 255)

  override def basketId: String = BasketId.get
  override def status: String = Status.get
  override def consumerId: Option[String] = Option(ConsumerId.get).map(_.trim).filter(_.nonEmpty)
  override def psuUserId: Option[String] = Option(PsuUserId.get).map(_.trim).filter(_.nonEmpty)

}

object MappedSigningBasket extends MappedSigningBasket with LongKeyedMetaMapper[MappedSigningBasket]  {
  override def dbTableName = "signingbasket" // define the DB table name
  override def dbIndexes = Index(BasketId) :: super.dbIndexes
}


class MappedSigningBasketPayment extends SigningBasketPaymentTrait with LongKeyedMapper[MappedSigningBasketPayment] with IdPK {
  override def getSingleton = MappedSigningBasketPayment
  object BasketId extends MappedUUID(this)
  object PaymentId extends MappedUUID(this)


  override def basketId: String = BasketId.get
  override def paymentId: String = PaymentId.get

}
object MappedSigningBasketPayment extends MappedSigningBasketPayment with LongKeyedMetaMapper[MappedSigningBasketPayment]  {
  override def dbTableName = "SigningBasketPayment" // define the DB table name
  override def dbIndexes = Index(BasketId, PaymentId) :: super.dbIndexes
}

class MappedSigningBasketConsent extends SigningBasketConsentTrait with LongKeyedMapper[MappedSigningBasketConsent] with IdPK {
  override def getSingleton = MappedSigningBasketConsent
  object BasketId extends MappedUUID(this)
  object ConsentId extends MappedUUID(this)


  override def basketId: String = BasketId.get
  override def consentId: String = ConsentId.get

}
object MappedSigningBasketConsent extends MappedSigningBasketConsent with LongKeyedMetaMapper[MappedSigningBasketConsent]  {
  override def dbTableName = "SigningBasketConsent" // define the DB table name
  override def dbIndexes = Index(BasketId, ConsentId) :: super.dbIndexes
}

/**
 * Which basket is holding a payment or consent. A row exists while the basket is active and is deleted
 * when it reaches a final status, so a member can be in one active basket at a time without a permanent
 * unique constraint on the member itself.
 */
class MappedSigningBasketMemberClaim extends LongKeyedMapper[MappedSigningBasketMemberClaim] with IdPK with CreatedUpdated {
  override def getSingleton = MappedSigningBasketMemberClaim
  // "payment:<id>" or "consent:<id>"
  object MemberKey extends MappedString(this, 255)
  object BasketId extends MappedUUID(this)
}
object MappedSigningBasketMemberClaim extends MappedSigningBasketMemberClaim with LongKeyedMetaMapper[MappedSigningBasketMemberClaim] {
  override def dbTableName = "SigningBasketMemberClaim"
  override def dbIndexes = UniqueIndex(MemberKey) :: Index(BasketId) :: super.dbIndexes
}

/** Per member, how executing the basket's authorisation went. See SigningBasketMemberExecution. */
class MappedSigningBasketMemberExecution extends LongKeyedMapper[MappedSigningBasketMemberExecution] with IdPK with CreatedUpdated {
  override def getSingleton = MappedSigningBasketMemberExecution
  object BasketId extends MappedUUID(this)
  object MemberType extends MappedString(this, 16)
  object MemberId extends MappedString(this, 255)
  object Position extends MappedInt(this)
  object State extends MappedString(this, 16)
  object Detail extends MappedString(this, 2000)
  object Attempts extends MappedInt(this)
}
object MappedSigningBasketMemberExecution extends MappedSigningBasketMemberExecution with LongKeyedMetaMapper[MappedSigningBasketMemberExecution] {
  override def dbTableName = "SigningBasketMemberExecution"
  override def dbIndexes = UniqueIndex(BasketId, MemberType, MemberId) :: super.dbIndexes
}
