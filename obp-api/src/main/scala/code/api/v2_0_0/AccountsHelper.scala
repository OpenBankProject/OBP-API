package code.api.v2_0_0

import code.api.util.ErrorMessages.InvalidFilterParameterFormat
import code.api.util.{CallContext, NewStyle}
import code.api.util.APIUtil.unboxFullOrFail
import com.openbankproject.commons.model.{BankIdAccountId, CoreAccount}
import net.liftweb.http.Req
import net.liftweb.util.Helpers.tryo

import scala.collection.immutable.{List, Nil}
import scala.concurrent.Future
import com.openbankproject.commons.ExecutionContext.Implicits.global

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


  /**
    * get parameter of account_type_filter_operation and account_type_filter_operation, do filter with CoreAccount#accountType
    * @param coreAccounts list of CoreAccount, to do filter
    * @param req request
    * @return result after do filter
    */
  private def filterWithAccountType(coreAccounts: List[CoreAccount], req: Req): List[CoreAccount] = {
    val filters = req.params.get("account_type_filter").map(_.flatMap(_.split(","))).getOrElse(Nil)
    val filtersOperation = req.params.get("account_type_filter_operation").flatMap(_.headOption).getOrElse("INCLUDE")

    val failMsg = s"""${InvalidFilterParameterFormat}request parameter account_type_filter_operation must be either INCLUDE or EXCLUDE, current it is: ${filtersOperation} """

    // validate account_type_filter_operation parameter
    unboxFullOrFail(tryo {
      assume(filtersOperation == "INCLUDE" || filtersOperation == "EXCLUDE")
    }, None, failMsg)

    coreAccounts.filter({ account =>
      (filters, filtersOperation) match {
        case (f, "INCLUDE") if f.nonEmpty => filters.contains(account.accountType)
        case (f, "EXCLUDE") if f.nonEmpty => !filters.contains(account.accountType)
        case _ => true
      }
    })
  }

  /**
    * get CoreAccount and the result is filtered according request parameter: account_type_filter_operation and account_type_filter_operatio
    * @param bankIdAcountIds list of BankIdAccountId
    * @param req http request
    * @param callContext callContext
    * @return list of CoreAccount Future
    */
  def getFilteredCoreAccounts(bankIdAcountIds: List[BankIdAccountId], req: Req, callContext: Option[CallContext]): Future[(List[CoreAccount], Option[CallContext])] = {
    NewStyle.function.getCoreBankAccountsFuture(bankIdAcountIds, callContext) map { it =>
      val (coreAccounts, cc) = it
      (filterWithAccountType(coreAccounts, req), cc)
    }
  }
}
