package code.api.berlin.group.v1

import code.setup.ServerSetupWithTestData

trait BerlinGroupV1ServerSetup extends ServerSetupWithTestData {

  def BerlinGroup_V1Request = baseRequest / "berlin-group" / "v1"

}
