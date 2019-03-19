package code.errormessages

import code.api.util.ErrorMessages.getDuplicatedMessageNumbers
import code.setup.ServerSetup

class DuplicatedMessages extends ServerSetup {
  feature("Try to find duplicated message numbers") {
    scenario("Parse the file ErrorMessages.scala") {
      Then("Size of list of duplicated message numbers has to be 0")
      getDuplicatedMessageNumbers.size should equal(0)
    }
  }
}
