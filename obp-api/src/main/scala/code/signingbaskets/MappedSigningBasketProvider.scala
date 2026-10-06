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
    // The basket and every member row are written inside one DB.use. Inside an HTTP request the
    // connection is the request's own, whose rollback is not ours to call, so a failure part way is
    // also undone by hand: nothing of a basket that was not fully created is left behind.
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
      DB.use(DefaultConnectionIdentifier) { _ =>
        members.zipWithIndex.foreach { case ((memberType, memberId), position) =>
          val exists = MappedSigningBasketMemberExecution.find(
            By(MappedSigningBasketMemberExecution.BasketId, basketId),
            By(MappedSigningBasketMemberExecution.MemberType, memberType),
            By(MappedSigningBasketMemberExecution.MemberId, memberId)).isDefined
          if (!exists)
            MappedSigningBasketMemberExecution.create
              .BasketId(basketId).MemberType(memberType).MemberId(memberId)
              .Position(position).State(SigningBasketMemberState.Pending).Detail("").Attempts(0)
              .saveMe()
        }
      }
      true
    }

  override def getSigningBasketMemberExecutions(basketId: String): List[SigningBasketMemberExecution] =
    MappedSigningBasketMemberExecution
      .findAll(By(MappedSigningBasketMemberExecution.BasketId, basketId), OrderBy(MappedSigningBasketMemberExecution.Position, Ascending))
      .map(row => SigningBasketMemberExecution(
        row.MemberType.get, row.MemberId.get, row.Position.get, row.State.get, Option(row.Detail.get).getOrElse(""), row.Attempts.get))

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
      DB.runUpdate(
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
      DB.runUpdate(
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

  override def releaseSigningBasketMembers(basketId: String): Box[Boolean] =
    tryo { MappedSigningBasketMemberClaim.bulkDelete_!!(By(MappedSigningBasketMemberClaim.BasketId, basketId)) }

  override def transitionSigningBasketStatus(basketId: String, from: String, to: String): Box[Boolean] =
    tryo {
      DB.runUpdate(
        s"UPDATE ${MappedSigningBasket.dbTableName} " +
          s"SET ${MappedSigningBasket.Status._dbColumnNameLC} = ?, ${MappedSigningBasket.updatedAt._dbColumnNameLC} = ? " +
          s"WHERE ${MappedSigningBasket.BasketId._dbColumnNameLC} = ? AND ${MappedSigningBasket.Status._dbColumnNameLC} = ?",
        List[Any](to, now, basketId, from)) == 1
    }

  override def touchSigningBasket(basketId: String): Box[Boolean] =
    tryo {
      val statuses = List(ConstantsBG.SigningBasketsStatus.AUTHORISING_INTERNAL, ConstantsBG.SigningBasketsStatus.EXECUTION_INCOMPLETE_INTERNAL)
      DB.runUpdate(
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
