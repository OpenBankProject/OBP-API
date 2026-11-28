package code.api.util

import code.setup.ServerSetup

/**
 * The dynamic-code dependency validation must actually scan, not silently validate an empty list.
 *
 * `DynamicUtil.Validation.validateDependency` is the gate that stops user-supplied Scala from
 * calling restricted types - an operator switches it on with `dynamic_code_compile_validate_enable`.
 * It gets the call list from `getDynamicCodeDependentMethods`, which opened with
 * `if (SHOW_USED_CONNECTOR_METHODS)` and returned `Nil` otherwise.
 *
 * `show_used_connector_methods` is a *diagnostic* prop - it controls whether a response reports the
 * connector methods an endpoint used - and it defaults to false. So on a default deployment the
 * operator could turn the security validation on, watch it run, and have it inspect nothing: every
 * restricted call passes, because the list of calls handed to it is empty. Two unrelated switches,
 * one of them reporting-only, and the security one silently depended on it.
 *
 * Worse than a misconfiguration: `SHOW_USED_CONNECTOR_METHODS` is a `final val` on `Constant`, read
 * once when that object initialises, so it is frozen at boot and cannot be turned on later even
 * deliberately - which is why this test does not try to toggle it.
 */
class DynamicCodeDependencyScanTest extends ServerSetup {

  // A plain class whose method body provably calls something. Whatever the scanner reports for it,
  // it cannot honestly be "nothing".
  class Caller {
    def process(): String = java.util.UUID.randomUUID().toString
  }

  Feature("dynamic-code dependency scanning does not depend on a diagnostic prop") {

    Scenario("a class that calls something reports a non-empty dependency list") {
      val deps = DynamicUtil.getDynamicCodeDependentMethods(classOf[Caller], "process".==)

      withClue("the scan returned nothing, so validateDependency - the gate that blocks restricted " +
        "calls in user-supplied Scala - would have had an empty list to validate and let " +
        "everything through: ") {
        deps should not be empty
      }
    }

    Scenario("the scan finds the call the method actually makes") {
      // Not merely non-empty: it has to be a real read of the bytecode, so assert on the call that
      // is visibly in `process`'s body rather than on the list's size.
      val deps = DynamicUtil.getDynamicCodeDependentMethods(classOf[Caller], "process".==)
      val pairs = deps.map { case (typeName, method, _) => s"$typeName.$method" }

      withClue(s"expected java.util.UUID.randomUUID among the scanned dependencies, got: $pairs ") {
        pairs should contain("java.util.UUID.randomUUID")
      }
    }
  }
}
