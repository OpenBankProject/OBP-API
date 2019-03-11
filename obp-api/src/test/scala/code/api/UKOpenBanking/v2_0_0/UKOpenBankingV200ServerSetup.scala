package code.api.UKOpenBanking.v2_0_0

import code.setup.ServerSetupWithTestData

trait UKOpenBankingV200ServerSetup extends ServerSetupWithTestData {

  def UKOpenBankingV200Request = baseRequest / "open-banking" / "v2.0"

}
