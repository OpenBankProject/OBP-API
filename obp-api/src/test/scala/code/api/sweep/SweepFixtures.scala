package code.api.sweep

import code.api.util.ApiRole
import code.entitlement.Entitlement
import code.setup.DefaultUsers

/**
 * Shared setup for sweeps that call the API with a fully-entitled caller.
 *
 * FailureSweepTest and SuccessSweepTest each grew an identical "grant every role, then build a
 * DirectLogin header" construction, and AuthSweepTest, FailureSweepTest and SuccessSweepTest each
 * looked up the fixture bank independently. One shared definition here, called from all three,
 * means a future change to either only has one site to update.
 */
trait SweepFixtures { self: DefaultUsers =>

  /** The first sandbox bank the fixtures created, if any. */
  def realBankId: Option[String] =
    code.bankconnectors.LocalMappedConnector.getBanksLegacy(None)
      .map(_._1).getOrElse(Nil).headOption.map(_.bankId.value)

  /**
   * A caller holding every role in the system.
   *
   * Granted directly through the Entitlement provider rather than over the API -- the same thing
   * 161 existing test files do -- because the goal is to get PAST authorisation, not to test it.
   */
  def omniscientCaller: Map[String, String] = {
    ApiRole.availableRoles.foreach { role =>
      // Bank-scoped roles need a bank; system-wide ones must be granted with an empty bankId.
      // valueOf throws on a name it does not recognise, and availableRoles includes dynamic
      // roles whose backing entity may not exist in this database -- a grant that cannot be
      // made is not a reason to abandon the other several hundred.
      try {
        val bankId = if (ApiRole.valueOf(role).requiresBankId) realBankId.getOrElse("") else ""
        Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, role)
      } catch { case _: Exception => () }
    }
    Map("DirectLogin" -> s"token=${token1.value}")
  }
}
