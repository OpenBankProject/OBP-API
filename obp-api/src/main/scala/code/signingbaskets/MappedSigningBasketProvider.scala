package code.signingbaskets

import code.util.MappedUUID
import com.openbankproject.commons.model.{RegulatedEntityTrait, SigningBasketConsentTrait, SigningBasketPaymentTrait, SigningBasketTrait}
import net.liftweb.common.Box
import net.liftweb.common.Box.tryo
import net.liftweb.mapper._

import scala.concurrent.Future

object MappedSigningBasketProvider extends SigningBasketProvider {
  def getSigningBaskets(): List[SigningBasketTrait] = {
    MappedSigningBasket.findAll()
  }

  override def getSigningBasketByBasketId(entityId: String): Box[SigningBasketTrait] = {
    MappedSigningBasket.find(By(MappedSigningBasket.BasketId, entityId))
  }

  override def createSigningBasket(basketId: Option[String],
                                     status: Option[String],
                                     description: Option[String],
                                    ): Box[SigningBasketTrait] = {
    tryo {
      val entity = MappedSigningBasket.create
      basketId match {
        case Some(v) => entity.BasketId(v)
        case None =>
      }
      status match {
        case Some(v) => entity.Status(v)
        case None =>
      }
      description match {
        case Some(v) => entity.Description(v)
        case None =>
      }

      if (entity.validate.isEmpty) {
        entity.saveMe()
      } else {
        throw new Error(entity.validate.map(_.msg.toString()).mkString(";"))
      }
    }
  }

  override def deleteSigningBasket(id: String): Box[Boolean] = {
    tryo(
      MappedSigningBasket.bulkDelete_!!(By(MappedSigningBasket.BasketId, id))
    )
  }

}

class MappedSigningBasket extends SigningBasketTrait with LongKeyedMapper[MappedSigningBasket] with IdPK {
  override def getSingleton = MappedSigningBasket
  object BasketId extends MappedUUID(this)
  object Status extends MappedString(this, 50)
  object Description extends MappedText(this)



  override def basketId: String = BasketId.get
  override def status: String = Status.get
  override def description: String = Description.get


}

object MappedSigningBasket extends MappedSigningBasket with LongKeyedMetaMapper[MappedSigningBasket]  {
  override def dbTableName = "SigningBasket" // define the DB table name
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

