package code.api.v2_0_0

import com.openbankproject.commons.model.CoreAccount

/**
  * this helper is make sure some common value or function can be used by different APIMethodsXxx
  * because they are in different scope, any value defined in one trait, can't be access by others, just copy
  * pass cause duplicated code.
  */
object AccountsHelper {
  // accountTypeFilter doc part text
  def accountTypeFilterText(url: String) =
    s"""
      |optional request parameters:
      |
      |* account_type_filter: one or many accountType value, split by comma
      |* account_type_filter_operation: the filter type of account_type_filter, value must be INCLUDE or EXCLUDE
      |
      |whole url example:
      |$url?account_type_filter=330,CURRENT+PLUS&account_type_filter_operation=INCLUDE
    """.stripMargin


}
