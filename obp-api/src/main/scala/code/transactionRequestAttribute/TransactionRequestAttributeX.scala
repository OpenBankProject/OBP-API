package code.transactionRequestAttribute

import code.api.util.APIUtil
import code.remotedata.RemotedataTransactionRequestAttribute
import com.openbankproject.commons.model.TransactionRequestAttribute
import net.liftweb.util.SimpleInjector

import scala.collection.immutable.List

object TransactionRequestAttributeX extends SimpleInjector {

  val transactionRequestAttributeProvider = new Inject(buildOne _) {}

  def buildOne: TransactionRequestAttributeProvider =
    if (APIUtil.getPropsAsBoolValue("use_akka", defaultValue = false)) {
      RemotedataTransactionRequestAttribute
    } else {
      MappedTransactionRequestAttributeProvider
    }

  // Helper to get the count out of an option
  def countOfTransactionRequestAttribute(listOpt: Option[List[TransactionRequestAttribute]]): Int = {
    val count = listOpt match {
      case Some(list) => list.size
      case None => 0
    }
    count
  }
}
