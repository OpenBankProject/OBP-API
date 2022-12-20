package code.api.v5_1_0

import code.api.v4_0_0._
import code.setup.{APIResponse, DefaultUsers, ServerSetupWithTestData}
import dispatch.Req
import scala.util.Random.nextInt

trait V510ServerSetup extends ServerSetupWithTestData with DefaultUsers {

  def v4_0_0_Request: Req = baseRequest / "obp" / "v4.0.0"
  def v5_0_0_Request: Req = baseRequest / "obp" / "v5.0.0"
  def v5_1_0_Request: Req = baseRequest / "obp" / "v5.1.0"
  

  def randomBankId : String = {
    def getBanksInfo : APIResponse  = {
      val request = v4_0_0_Request / "banks"
      makeGetRequest(request)
    }
    val banksJson = getBanksInfo.body.extract[BanksJson400]
    val randomPosition = nextInt(banksJson.banks.size)
    val bank = banksJson.banks(randomPosition)
    bank.id
  }
 
}