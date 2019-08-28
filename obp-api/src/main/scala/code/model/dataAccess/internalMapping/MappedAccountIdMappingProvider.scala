package code.model.dataAccess.internalMapping

import code.util.Helper.MdcLoggable
import com.openbankproject.commons.model.{BankId, AccountId}
import net.liftweb.common._
import net.liftweb.mapper.By


object MappedAccountIdMappingProvider extends AccountIdMappingProvider with MdcLoggable
{

  override def getOrCreateAccountId(
    accountPlainTextReference: String
  ): Box[AccountId] =
  {

    val mappedAccountIdMapping = AccountIdMapping.find(
      By(AccountIdMapping.mAccountPlainTextReference, accountPlainTextReference)
    )

    mappedAccountIdMapping match
    {
      case Full(vImpl) =>
      {
        logger.debug(s"getOrCreateAccountId --> the mappedAccountIdMapping has been existing in server !")
        mappedAccountIdMapping.map(_.accountId)
      }
      case Empty =>
      {
        val mappedAccountIdMapping: AccountIdMapping =
          AccountIdMapping
            .create
            .mAccountPlainTextReference(accountPlainTextReference)
            .saveMe
        logger.debug(s"getOrCreateAccountId--> create mappedAccountIdMapping : $mappedAccountIdMapping")
        Full(mappedAccountIdMapping.accountId)
      }
      case Failure(msg, t, c) => Failure(msg, t, c)
      case ParamFailure(x,y,z,q) => ParamFailure(x,y,z,q)
    }
  }


  override def getAccountPlainTextReference(accountId: AccountId) = {
    AccountIdMapping.find(
      By(AccountIdMapping.mAccountId, accountId.value),
    ).map(_.accountPlainTextReference)
  }
}

