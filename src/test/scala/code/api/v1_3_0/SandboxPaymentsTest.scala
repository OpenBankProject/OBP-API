package code.api.v1_3_0

import code.api.test.ServerSetup

class SandboxPaymentsTest extends ServerSetup {

  feature("Sandbox payments with challenges/operations") {

    scenario("Payment without a security challenge") {
      //TODO
      1 should equal(2)
    }

    scenario("Payment with a single security challenge answered correctly") {
      //TODO
      1 should equal(2)
    }

    scenario("Payment with a single security challenged answered incorrectly, and then correctly on the second attempt") {
      //TODO
      1 should equal(2)
    }

    scenario("Payment fails due to too many incorrectly answered challenges") {
      //TODO
      1 should equal(2)
    }

    scenario("Payment with multiple security challenges") {
      //TODO: need to add this case to the sandbox payment processor
      1 should equal(2)
    }
  }


}
