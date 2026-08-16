package code.api.v5_0_0

import org.scalatest.Ignore
import org.json4s.jvalue2extractable
import code.api.v4_0_0.{APIInfoJson400, BanksJson400}
import com.openbankproject.commons.util.ApiVersion
import org.scalatest.Tag

@Ignore
class RootAndBanksTest extends V500ServerSetup {

  object VersionOfApi extends Tag(ApiVersion.v5_0_0.toString)

  Feature(s"V500 public read endpoints - $VersionOfApi") {

    Scenario("GET /root returns API info", VersionOfApi) {
      val request = (v5_0_0_Request / "root").GET
      val response = makeGetRequest(request)
      response.code should equal(200)
      val apiInfo = response.body.extract[APIInfoJson400]
      apiInfo.version.nonEmpty shouldBe true
      apiInfo.version_status.nonEmpty shouldBe true
      apiInfo.git_commit.nonEmpty shouldBe true
      apiInfo.connector.nonEmpty shouldBe true
    }

    Scenario("GET /banks returns banks list", VersionOfApi) {
      val request = (v5_0_0_Request / "banks").GET
      val response = makeGetRequest(request)
      response.code should equal(200)
      val banks = response.body.extract[BanksJson400]
      banks.banks.nonEmpty shouldBe true
    }
  }
}

