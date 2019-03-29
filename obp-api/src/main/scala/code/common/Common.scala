package code.common

import java.util.Date

import com.openbankproject.commons.model._

// We use traits so we can override in the Provider for database access etc.
// We use case classes based on the traits so we can easily construct a data structure like the trait.

case class Routing(
  scheme: String,
  address: String
) extends RoutingT
