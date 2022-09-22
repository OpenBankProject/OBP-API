package code.api.v5_0_0

import code.api.v4_0_0.BanksJson400
import code.setup.{APIResponse, DefaultUsers, ServerSetupWithTestData}
import com.openbankproject.commons.util.ApiShortVersions
import dispatch.Req

import scala.util.Random.nextInt

trait V500ServerSetup extends ServerSetupWithTestData with DefaultUsers {

  def v5_0_0_Request: Req = baseRequest / "obp" / "v5.0.0"
  def dynamicEndpoint_Request: Req = baseRequest / "obp" / ApiShortVersions.`dynamic-endpoint`.toString
  def dynamicEntity_Request: Req = baseRequest / "obp" / ApiShortVersions.`dynamic-entity`.toString

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