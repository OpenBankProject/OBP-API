package code.transactionRequestAttribute

import code.api.util.APIUtil
import com.openbankproject.commons.model.TransactionRequestAttributeTrait
import net.liftweb.util.SimpleInjector

import scala.collection.immutable.List

object TransactionRequestAttributeX extends SimpleInjector {

  val transactionRequestAttributeProvider = new Inject(buildOne _) {}

  def buildOne: TransactionRequestAttributeProvider = MappedTransactionRequestAttributeProvider

  // Helper to get the count out of an option
  def countOfTransactionRequestAttribute(listOpt: Option[List[TransactionRequestAttributeTrait]]): Int = {
    val count = listOpt match {
      case Some(list) => list.size
      case None => 0
    }
    count
  }
}
