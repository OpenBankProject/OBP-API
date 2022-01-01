package code.api.util

import code.model.Consumer
import code.model.dataAccess.AuthUser
import code.util.Helper.MdcLoggable
import net.liftweb.mapper.MappedString
import net.liftweb.util.{FieldError, Helpers}

import scala.collection.immutable.List

object CommonFunctions extends MdcLoggable {
  /**
   * This function is added in order to support iOS/macOS requirements for callbacks.
   * For instance next callback has to be valid: x-com.tesobe.helloobp.ios://callback
   * @param field object which has to be validated
   * @param s is a URI string
   * @return Empty list if URI is valid or FieldError otherwise
   */
  def validUri[T <: MappedString[_]](field: T)(s: String): List[FieldError] = {
    import java.net.URI
    import Helpers.tryo
    if(s.isEmpty)
      Nil
    else if(tryo{new URI(s)}.isEmpty)
      List(FieldError(field, {field.displayName + " must be a valid URI"}))
    else
      Nil
  }

  private def validUrl[T <: MappedString[_]](field: T)(s: String): List[FieldError] = {
    import java.net.URL

    import Helpers.tryo
    if(s.isEmpty)
      Nil
    else if(tryo{new URL(s)}.isEmpty)
      List(FieldError(field, {field.displayName + " must be a valid URL"}))
    else
      Nil
  }
  
}
