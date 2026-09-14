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
  object ConsumerId extends MappedString(this, 250) {
    override def defaultValue = null
  }
  

  override def consentRequestId: String = ConsentRequestId.get
  override def payload: String = Payload.get
  override def consumerId: String = ConsumerId.get

}

object ConsentRequest extends ConsentRequest with LongKeyedMetaMapper[ConsentRequest] {
  override def dbIndexes: List[BaseIndex[ConsentRequest]] = UniqueIndex(ConsentRequestId) :: super.dbIndexes
}
