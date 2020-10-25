package code.api.util.newstyle

import code.api.util.APIUtil.{OBPReturnType, unboxFull}
import code.api.util.CallContext
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
                             redirectURL: Option[String],
                             createdByUserId: Option[String],
                             clientCertificate: Option[String],
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
        clientCertificate
      ) map {
        (_, callContext)
      }
    } map {
      unboxFull(_)
    }
  }


}
