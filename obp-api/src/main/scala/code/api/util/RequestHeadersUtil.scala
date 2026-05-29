package code.api.util

import code.api.RequestHeader._
import code.api.util.APIUtil.HTTPParam

object RequestHeadersUtil {
  def checkEmptyRequestHeaderValues(requestHeaders: List[HTTPParam]): List[String] = {
    val emptyValues = requestHeaders
      .filter(header => header != null && (header.values == null || header.values.isEmpty || header.values.exists(_.trim.isEmpty)))
      .map(_.name) // Extract header names with empty values

    emptyValues
  }
  def checkEmptyRequestHeaderNames(requestHeaders: List[HTTPParam]): List[String] = {
    val emptyNames = requestHeaders
      .filter(header => header == null || header.name == null || header.name.trim.isEmpty)
      .map(_.values.mkString("'")) // List values without names

    emptyNames
  }

}
