package code.api.ResourceDocs1_4_0

import code.setup.ServerSetupWithTestData

trait ResourceDocsV140ServerSetup extends ServerSetupWithTestData {

  def ResourceDocsV1_4Request = baseRequest / "obp" / "v1.4.0"
  def ResourceDocsV2_0Request = baseRequest / "obp" / "v2.0.0"
  def ResourceDocsV2_1Request = baseRequest / "obp" / "v2.1.0"
  def ResourceDocsV2_2Request = baseRequest / "obp" / "v2.2.0"
  def ResourceDocsV3_0Request = baseRequest / "obp" / "v3.0.0"
  def ResourceDocsV3_1Request = baseRequest / "obp" / "v3.1.0"
  def ResourceDocsV4_0Request = baseRequest / "obp" / "v4.0.0"

}
