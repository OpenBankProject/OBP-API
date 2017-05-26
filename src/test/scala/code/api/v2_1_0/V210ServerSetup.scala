package code.api.v2_1_0

import code.setup.ServerSetupWithTestData

/**
 * Created by markom on 10/14/16.
 */
trait V210ServerSetup extends ServerSetupWithTestData {

  def v1_4Request = baseRequest / "obp" / "v1.4.0"
  def v2_0Request = baseRequest / "obp" / "v2.0.0"
  def v2_1Request = baseRequest / "obp" / "v2.1.0"

}