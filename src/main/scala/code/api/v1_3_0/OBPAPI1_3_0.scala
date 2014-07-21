package code.api.v1_3_0

import code.api.OBPRestHelper
import code.api.v1_2_1.APIMethods121
import net.liftweb.common.Loggable

//has APIMethods121 as all api calls that went unchanged from 1.2.1 to 1.3.0 will use the old
//implementation
object OBPAPI1_3_0 extends OBPRestHelper with APIMethods130 with APIMethods121 with Loggable {

  val VERSION = "1.3.0"

}
