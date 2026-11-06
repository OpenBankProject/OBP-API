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
import code.api.util.{APIUtil, DoobieUtil}
import com.openbankproject.commons.model.{SigningBasketConsentTrait, SigningBasketContent, SigningBasketPaymentTrait, SigningBasketTrait}
import doobie._
import doobie.implicits._
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.common.Box.tryo

object MappedSigningBasketProvider extends SigningBasketProvider {

  def getSigningBaskets(): List[SigningBasketTrait] = MappedSigningBasket.findAll()

  override def getSigningBasketByBasketId(entityId: String): Box[SigningBasketContent] =
    MappedSigningBasket.findByBasketId(entityId).map(content)

  override def saveSigningBasketStatus(entityId: String, status: String): Box[SigningBasketContent] =
    MappedSigningBasket.findByBasketId(entityId)
      .map { basket =>
        MappedSigningBasket.updateStatus(basket.basketId, status)
        basket.copy(status = status)
      }
      .map(content)

  override def createSigningBasket(paymentIds: Option[List[String]],
                                   consentIds: Option[List[String]],
                                   consumerId: String
                                  ): Box[SigningBasketTrait] = {
    tryo {
      // Mapper ran entity.validate here and threw on a violation. The only validated field was
      // Status against MappedString(50), and the only status ever written on create is the
      // constant RCVD, so the branch could not fire; the column length still holds it.
      val basket = MappedSigningBasket.insert(ConstantsBG.SigningBasketsStatus.RCVD.toString, consumerId)
      paymentIds.getOrElse(Nil).foreach(MappedSigningBasketPayment.insert(basket.basketId, _))
      consentIds.getOrElse(Nil).foreach(MappedSigningBasketConsent.insert(basket.basketId, _))
      basket
    }
  }

  override def transitionSigningBasketStatus(basketId: String, from: String, to: String): Box[Boolean] =
    tryo(MappedSigningBasket.transitionStatus(basketId, from, to))

  /**
   * Cancelling a basket is a status change, not a delete — the basket and its membership rows stay
   * so an authorisation that referenced them can still be explained afterwards.
   */
  override def deleteSigningBasket(id: String): Box[Boolean] =
    MappedSigningBasket.findByBasketId(id).map { basket =>
      MappedSigningBasket.updateStatus(basket.basketId, ConstantsBG.SigningBasketsStatus.CANC.toString)
      true
    }

  /** Empty membership reads as None rather than an empty list — the API distinguishes the two. */
  private def content(basket: MappedSigningBasket): SigningBasketContent = {
    val payments = MappedSigningBasketPayment.findAllByBasketId(basket.basketId).map(_.paymentId)
    val consents = MappedSigningBasketConsent.findAllByBasketId(basket.basketId).map(_.consentId)
    SigningBasketContent(
      basket = basket,
      payments = if (payments.isEmpty) None else Some(payments),
      consents = if (consents.isEmpty) None else Some(consents))
  }
}

/**
 * A Berlin Group signing basket: several payments and/or consents the PSU authorises in one go.
 *
 * Holds the id, the status and the consumer (TPP) that created it; what is in the basket lives in
 * the two join tables below.
 */
case class MappedSigningBasket(basketId: String, status: String, private val consumerIdRaw: Option[String] = None)
  extends SigningBasketTrait {
  // Empty, or null, on a basket created before the creating consumer was recorded.
  override def consumerId: Option[String] = consumerIdRaw.map(_.trim).filter(_.nonEmpty)
}

object MappedSigningBasket {

  private val selectColumns = fr"SELECT basketid, status, consumerid FROM signingbasket"

  private type Row = (Option[String], Option[String], Option[String])

  private def fromRow(row: Row): MappedSigningBasket =
    MappedSigningBasket(row._1.orNull, row._2.orNull, row._3)

  private def query(condition: Fragment): List[MappedSigningBasket] =
    DoobieUtil.runQuery((selectColumns ++ condition).query[Row].to[List]).map(fromRow)

  def findAll(): List[MappedSigningBasket] = query(Fragment.empty)

  /**
   * BASKETID is indexed but not unique, so this takes the first match by insertion order rather
   * than assuming there is only one. That is what Mapper's find did.
   */
  def findByBasketId(basketId: String): Box[MappedSigningBasket] =
    query(fr"WHERE basketid = ${Option(basketId)} ORDER BY id ASC LIMIT 1").headOption match {
      case Some(row) => Full(row)
      case None => Empty
    }

  def insert(status: String, consumerId: String): MappedSigningBasket = {
    val basketId = APIUtil.generateUUID()
    DoobieUtil.runUpdate(
      sql"""INSERT INTO signingbasket (basketid, status, consumerid)
            VALUES ($basketId, ${Option(status)}, ${Option(consumerId)})""".update.run)
    MappedSigningBasket(basketId, status, Option(consumerId))
  }

  /**
   * Moves a basket from one status to another only if it still has the status the caller read: one
   * conditional update, so two callers racing for the same transition have exactly one winner.
   */
  def transitionStatus(basketId: String, from: String, to: String): Boolean =
    DoobieUtil.runUpdate(
      sql"""UPDATE signingbasket SET status = ${Option(to)}
            WHERE basketid = ${Option(basketId)} AND status = ${Option(from)}""".update.run) == 1

  def updateStatus(basketId: String, status: String): Unit = {
    DoobieUtil.runUpdate(
      sql"UPDATE signingbasket SET status = ${Option(status)} WHERE basketid = ${Option(basketId)}"
        .update.run)
    ()
  }

  def deleteAll(): Unit = {
    DoobieUtil.runUpdate(sql"DELETE FROM signingbasket".update.run)
    ()
  }
}

/** One payment in a basket. A join table with no uniqueness: the same payment can be listed twice. */
case class MappedSigningBasketPayment(basketId: String, paymentId: String)
  extends SigningBasketPaymentTrait

object MappedSigningBasketPayment {

  private val selectColumns = fr"SELECT basketid, paymentid FROM signingbasketpayment"

  private type Row = (Option[String], Option[String])

  private def query(condition: Fragment): List[MappedSigningBasketPayment] =
    DoobieUtil.runQuery((selectColumns ++ condition).query[Row].to[List])
      .map(row => MappedSigningBasketPayment(row._1.orNull, row._2.orNull))

  def findAllByBasketId(basketId: String): List[MappedSigningBasketPayment] =
    query(fr"WHERE basketid = ${Option(basketId)} ORDER BY id ASC")

  def insert(basketId: String, paymentId: String): MappedSigningBasketPayment = {
    DoobieUtil.runUpdate(
      sql"""INSERT INTO signingbasketpayment (basketid, paymentid)
            VALUES (${Option(basketId)}, ${Option(paymentId)})""".update.run)
    MappedSigningBasketPayment(basketId, paymentId)
  }

  def deleteAll(): Unit = {
    DoobieUtil.runUpdate(sql"DELETE FROM signingbasketpayment".update.run)
    ()
  }
}

/** One consent in a basket. Same shape as the payment join table. */
case class MappedSigningBasketConsent(basketId: String, consentId: String)
  extends SigningBasketConsentTrait

object MappedSigningBasketConsent {

  private val selectColumns = fr"SELECT basketid, consentid FROM signingbasketconsent"

  private type Row = (Option[String], Option[String])

  private def query(condition: Fragment): List[MappedSigningBasketConsent] =
    DoobieUtil.runQuery((selectColumns ++ condition).query[Row].to[List])
      .map(row => MappedSigningBasketConsent(row._1.orNull, row._2.orNull))

  def findAllByBasketId(basketId: String): List[MappedSigningBasketConsent] =
    query(fr"WHERE basketid = ${Option(basketId)} ORDER BY id ASC")

  def insert(basketId: String, consentId: String): MappedSigningBasketConsent = {
    DoobieUtil.runUpdate(
      sql"""INSERT INTO signingbasketconsent (basketid, consentid)
            VALUES (${Option(basketId)}, ${Option(consentId)})""".update.run)
    MappedSigningBasketConsent(basketId, consentId)
  }

  def deleteAll(): Unit = {
    DoobieUtil.runUpdate(sql"DELETE FROM signingbasketconsent".update.run)
    ()
  }
}
