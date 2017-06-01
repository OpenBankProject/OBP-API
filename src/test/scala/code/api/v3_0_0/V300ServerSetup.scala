package code.api.v3_0_0

import code.setup.ServerSetupWithTestData

/**
 * Created by Hongwei Zhang on 05/05/17.
 */
trait V300ServerSetup extends ServerSetupWithTestData {

  def v1_2Request = baseRequest / "obp" / "v1.2"
  def v1_4Request = baseRequest / "obp" / "v1.4.0"
  def v2_0Request = baseRequest / "obp" / "v2.0.0"
  def v2_1Request = baseRequest / "obp" / "v2.1.0"
  def v2_2Request = baseRequest / "obp" / "v2.2.0"
  def v3_0Request = baseRequest / "obp" / "v3.0.0"
}