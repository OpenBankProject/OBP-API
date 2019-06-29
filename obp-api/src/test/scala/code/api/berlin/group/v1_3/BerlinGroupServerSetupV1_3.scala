package code.api.berlin.group.v1_3

import code.setup.ServerSetupWithTestData

trait BerlinGroupServerSetupV1_3 extends ServerSetupWithTestData {
  val urpPrefix = baseRequest / "berlin-group" / "v1.3"
}
