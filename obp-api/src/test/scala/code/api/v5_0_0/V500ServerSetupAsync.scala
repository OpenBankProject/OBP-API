package code.api.v5_0_0

import code.api.v4_0_0.BanksJson400
import code.setup._
import code.setup.OBPReq

import scala.util.Random.nextInt

trait V500ServerSetupAsync extends ServerSetupWithTestDataAsync with DefaultUsers {

  def v5_0_0_Request: OBPReq = baseRequest / "obp" / "v5.0.0"

  def randomBankId : String = {
    def getBanksInfo : APIResponse  = {
      val request = v5_0_0_Request / "banks"
      makeGetRequest(request)
    }
    val banksJson = getBanksInfo.body.extract[BanksJson400]
    val randomPosition = nextInt(banksJson.banks.size)
    val bank = banksJson.banks(randomPosition)
    bank.id
  }
}