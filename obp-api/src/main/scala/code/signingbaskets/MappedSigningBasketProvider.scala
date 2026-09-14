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
import net.liftweb.mapper._

object MappedSigningBasketProvider extends SigningBasketProvider {
  def getSigningBaskets(): List[SigningBasketTrait] = {
    MappedSigningBasket.findAll()
  }

  override def getSigningBasketByBasketId(entityId: String): Box[SigningBasketContent] = {
    val basket: Box[MappedSigningBasket] = MappedSigningBasket.find(By(MappedSigningBasket.BasketId, entityId))
    val payments = MappedSigningBasketPayment.findAll(By(MappedSigningBasketPayment.BasketId, entityId)).map(_.paymentId) match {
      case Nil => None
      case head :: tail => Some(head :: tail)
    }
    val consents = MappedSigningBasketConsent.findAll(By(MappedSigningBasketConsent.BasketId, entityId)).map(_.consentId) match {
      case Nil => None
      case head :: tail => Some(head :: tail)
    }
    basket.map( i => SigningBasketContent(basket = i, payments = payments, consents = consents))
  }
  override def saveSigningBasketStatus(entityId: String, status: String): Box[SigningBasketContent] = {
    val basket: Box[MappedSigningBasket] = MappedSigningBasket.find(By(MappedSigningBasket.BasketId, entityId)).map(_.Status(status).saveMe)
    val payments = MappedSigningBasketPayment.findAll(By(MappedSigningBasketPayment.BasketId, entityId)).map(_.paymentId) match {
      case Nil => None
      case head :: tail => Some(head :: tail)
    }
    val consents = MappedSigningBasketConsent.findAll(By(MappedSigningBasketConsent.BasketId, entityId)).map(_.consentId) match {
      case Nil => None
      case head :: tail => Some(head :: tail)
    }
    basket.map( i => SigningBasketContent(basket = i, payments = payments, consents = consents))
  }

  override def createSigningBasket(paymentIds: Option[List[String]],
                                   consentIds: Option[List[String]]
                                  ): Box[SigningBasketTrait] = {
    tryo {
      val entity = MappedSigningBasket.create
      entity.Status(ConstantsBG.SigningBasketsStatus.RCVD.toString)

      if (entity.validate.isEmpty) {
        entity.saveMe()
      } else {
        throw new Error(entity.validate.map(_.msg.toString()).mkString(";"))
      }
      paymentIds.getOrElse(Nil).map { paymentId =>
        MappedSigningBasketPayment.create.BasketId(entity.basketId).PaymentId(paymentId).saveMe()
      }
      consentIds.getOrElse(Nil).map { consentId =>
        MappedSigningBasketConsent.create.BasketId(entity.basketId).ConsentId(consentId).saveMe()
      }
      entity
    }
  }

  override def deleteSigningBasket(id: String): Box[Boolean] = {
    MappedSigningBasket.find(By(MappedSigningBasket.BasketId, id)) map {
      _.Status(ConstantsBG.SigningBasketsStatus.CANC.toString).save
    }
  }

}

class MappedSigningBasket extends SigningBasketTrait with LongKeyedMapper[MappedSigningBasket] with IdPK {
  override def getSingleton = MappedSigningBasket
  object BasketId extends MappedUUID(this)
  object Status extends MappedString(this, 50)



  override def basketId: String = BasketId.get
  override def status: String = Status.get

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

