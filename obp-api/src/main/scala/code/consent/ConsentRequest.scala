package code.consent

import code.model.Consumer
import code.util.MappedUUID
import net.liftweb.common.Box
import net.liftweb.mapper._
import net.liftweb.util.Helpers.tryo

object MappedConsentRequestProvider extends ConsentRequestProvider {
  override def getConsentRequestById(consentRequestId: String): Box[ConsentRequest] = {
    ConsentRequest.find(
      By(ConsentRequest.ConsentRequestId, consentRequestId)
    )
  }
  override def createConsentRequest(consumer: Option[Consumer], payload: Option[String]): Box[ConsentRequest] ={
    tryo {
      ConsentRequest
        .create
        .ConsumerId(consumer.map(_.consumerId.get).getOrElse(null))
        .Payload(payload.getOrElse(""))
        .saveMe()
    }}
}

class ConsentRequest extends ConsentRequestTrait with LongKeyedMapper[ConsentRequest] with IdPK with CreatedUpdated {

  def getSingleton = ConsentRequest

  //the following are the obp consent.
  object ConsentRequestId extends MappedUUID(this)
  object Payload extends MappedText(this)
  object ConsumerId extends MappedUUID(this) {
    override def defaultValue = null
  }
  

  override def consentRequestId: String = ConsentRequestId.get
  override def payload: String = Payload.get
  override def consumerId: String = ConsumerId.get

}

object ConsentRequest extends ConsentRequest with LongKeyedMetaMapper[ConsentRequest] {
  override def dbIndexes: List[BaseIndex[ConsentRequest]] = UniqueIndex(ConsentRequestId) :: super.dbIndexes
}
