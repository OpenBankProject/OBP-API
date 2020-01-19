package com.openbankproject.commons.util

/**
 * function utils
 */
object Functions {

  /**
   * A placeholder PartialFunction, do nothing because the isDefinedAt method always return false
   * @tparam T function parameter type
   * @tparam D function return type
   * @return function
   */
  def doNothing[T, D]: PartialFunction[T,D] = {
    case _ if false => ???
  }
}
