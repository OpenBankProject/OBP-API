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
import net.liftweb.common.Box
import net.liftweb.common.Box.tryo
import net.liftweb.db.DB
import net.liftweb.mapper._
import net.liftweb.util.DefaultConnectionIdentifier

object MappedSigningBasketProvider extends SigningBasketProvider {
  def getSigningBaskets(): List[SigningBasketTrait] = {
    MappedSigningBasket.findAll()
  }

  private def membersOf(basketId: String): (Option[List[String]], Option[List[String]]) = {
    val payments = MappedSigningBasketPayment.findAll(By(MappedSigningBasketPayment.BasketId, basketId)).map(_.paymentId) match {
      case Nil => None
      case members => Some(members)
    }
    val consents = MappedSigningBasketConsent.findAll(By(MappedSigningBasketConsent.BasketId, basketId)).map(_.consentId) match {
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
        MappedSigningBasketPayment.bulkDelete_!!(By(MappedSigningBasketPayment.BasketId, basket.basketId))
        MappedSigningBasketConsent.bulkDelete_!!(By(MappedSigningBasketConsent.BasketId, basket.basketId))
        basket.delete_!
      }
    }
    result
  }

  override def transitionSigningBasketStatus(basketId: String, from: String, to: String): Box[Boolean] =
    tryo {
      DB.runUpdate(
        s"UPDATE ${MappedSigningBasket.dbTableName} " +
          s"SET ${MappedSigningBasket.Status._dbColumnNameLC} = ?, ${MappedSigningBasket.updatedAt._dbColumnNameLC} = CURRENT_TIMESTAMP " +
          s"WHERE ${MappedSigningBasket.BasketId._dbColumnNameLC} = ? AND ${MappedSigningBasket.Status._dbColumnNameLC} = ?",
        List(to, basketId, from)) == 1
    }

  override def bindSigningBasketPsu(basketId: String, psuUserId: String): Box[Boolean] =
    tryo {
      val bound = DB.runUpdate(
        s"UPDATE ${MappedSigningBasket.dbTableName} " +
          s"SET ${MappedSigningBasket.PsuUserId._dbColumnNameLC} = ?, ${MappedSigningBasket.updatedAt._dbColumnNameLC} = CURRENT_TIMESTAMP " +
          s"WHERE ${MappedSigningBasket.BasketId._dbColumnNameLC} = ? " +
          s"AND (${MappedSigningBasket.PsuUserId._dbColumnNameLC} IS NULL OR ${MappedSigningBasket.PsuUserId._dbColumnNameLC} = '')",
        List(psuUserId, basketId)) == 1
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

