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

package code.api.util.newstyle

import code.api.util.APIUtil.{OBPReturnType, unboxFull, unboxFullOrFail}
import code.api.util.CallContext
import code.api.util.ErrorMessages.CreateConsumerError
import code.consumer.Consumers
import code.model.{AppType, Consumer}

import scala.concurrent.Future

object Consumer {

  import com.openbankproject.commons.ExecutionContext.Implicits.global

  def createConsumerNewStyle(key: Option[String],
                             secret: Option[String],
                             isActive: Option[Boolean],
                             name: Option[String],
                             appType: Option[AppType],
                             description: Option[String],
                             developerEmail: Option[String],
                             company: Option[String],
                             redirectURL: Option[String],
                             createdByUserId: Option[String],
                             clientCertificate: Option[String],
                             logoURL: Option[String],
                             callContext: Option[CallContext]): OBPReturnType[Consumer] = {
    Future {
      Consumers.consumers.vend.createConsumer(
        key,
        secret,
        isActive,
        name,
        appType,
        description,
        developerEmail,
        redirectURL,
        createdByUserId,
        clientCertificate,
        company,
        logoURL
      )
    } map {
      (_, callContext)
    } map {
      x => (unboxFullOrFail(x._1, callContext, CreateConsumerError, 400), x._2)
    }
  }


}
