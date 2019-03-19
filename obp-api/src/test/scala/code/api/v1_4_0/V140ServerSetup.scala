package code.api.v1_4_0

import code.setup.ServerSetupWithTestData

trait V140ServerSetup extends ServerSetupWithTestData {

  def v1_4Request = baseRequest / "obp" / "v1.4.0"

}
