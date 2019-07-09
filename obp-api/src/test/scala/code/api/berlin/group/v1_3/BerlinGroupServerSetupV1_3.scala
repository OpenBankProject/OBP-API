package code.api.berlin.group.v1_3

import code.setup.ServerSetupWithTestData
import org.scalatest.Tag

trait BerlinGroupServerSetupV1_3 extends ServerSetupWithTestData {
  object BerlinGroupV1_3 extends Tag("BerlinGroup_v1_3")
  val V1_3_BG = baseRequest / "berlin-group" / "v1.3"
}
