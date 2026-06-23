package code.api.v6_0_0

import code.setup.{DefaultUsers, ServerSetupWithTestData}
import com.openbankproject.commons.util.ApiShortVersions
import code.setup.OBPReq

trait V600ServerSetup extends ServerSetupWithTestData with DefaultUsers {

  def v4_0_0_Request: OBPReq = baseRequest / "obp" / "v4.0.0"
  def v5_0_0_Request: OBPReq = baseRequest / "obp" / "v5.0.0"
  def v5_1_0_Request: OBPReq = baseRequest / "obp" / "v5.1.0"
  def v6_0_0_Request: OBPReq = baseRequest / "obp" / "v6.0.0"
  def dynamicEntity_Request: OBPReq = baseRequest / "obp" / ApiShortVersions.`dynamic-entity`.toString

}