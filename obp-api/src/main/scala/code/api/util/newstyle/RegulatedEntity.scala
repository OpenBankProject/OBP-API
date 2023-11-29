package code.api.util.newstyle

import code.api.util.APIUtil.{OBPReturnType, unboxFull}
import code.api.util.{APIUtil, CallContext}
import code.consumer.Consumers
import code.model.{AppType, Consumer}
import code.regulatedentities.MappedRegulatedEntityProvider
import com.openbankproject.commons.model.RegulatedEntityTrait

import scala.concurrent.Future

object RegulatedEntityNewStyle {

  import com.openbankproject.commons.ExecutionContext.Implicits.global

  def createRegulatedEntityNewStyle(certificateAuthorityCaOwnerId: Option[String],
                                    entityCertificatePublicKey: Option[String],
                                    entityCode: Option[String],
                                    entityType: Option[String],
                                    entityAddress: Option[String],
                                    entityTownCity: Option[String],
                                    entityPostCode: Option[String],
                                    entityCountry: Option[String],
                                    entityWebSite: Option[String],
                                    services: Option[String],
                                    callContext: Option[CallContext]): OBPReturnType[RegulatedEntityTrait] = {
    Future {
      MappedRegulatedEntityProvider.createRegulatedEntity(
        certificateAuthorityCaOwnerId: Option[String],
        entityCertificatePublicKey: Option[String],
        entityCode: Option[String],
        entityType: Option[String],
        entityAddress: Option[String],
        entityTownCity: Option[String],
        entityPostCode: Option[String],
        entityCountry: Option[String],
        entityWebSite: Option[String],
        services: Option[String]
      ) map {
        (_, callContext)
      }
    } map {
      unboxFull(_)
    }
  }

  def deleteRegulatedEntityNewStyle(id: String,
                                callContext: Option[CallContext]
                               ): OBPReturnType[Boolean] = {
    Future {
      MappedRegulatedEntityProvider.deleteRegulatedEntity(
        id
      ) map {
        (_, callContext)
      }
    } map {
      unboxFull(_)
    }
  }


}
