package code.api.berlin.group

import code.api.util.APIUtil
import com.openbankproject.commons.util.ApiVersion.berlinGroupV13
import com.openbankproject.commons.util.ScannedApiVersion
import net.liftweb.common.Full

object ConstantsBG {
  val berlinGroupVersion1: ScannedApiVersion = APIUtil.getPropsValue("berlin_group_version_1_canonical_path") match {
    case Full(props) => berlinGroupV13.copy(apiShortVersion = props)
    case _ => berlinGroupV13
  }
  object SigningBasketsStatus extends Enumeration {
    type SigningBasketsStatus = Value
    // Only the codes
    // 1) RCVD (Received),
    // 2) PATC (PartiallyAcceptedTechnical Correct) The payment initiation needs multiple authentications, where some but not yet all have been performed. Syntactical and semantical validations are successful.,
    // 3) ACTC (AcceptedTechnicalValidation) ,
    // 4) CANC (Cancelled) and
    // 5) RJCT (Rejected) are supported for signing baskets.
    val RCVD, PATC, ACTC, CANC, RJCT = Value
  }
}
