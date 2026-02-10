package code.apiproduct

import code.util.Helper.MdcLoggable
import net.liftweb.common.Box
import net.liftweb.mapper.By
import net.liftweb.util.Helpers.tryo

trait ApiProductsProvider {
  def createOrUpdateApiProduct(
    bankId: String,
    apiProductCode: String,
    parentApiProductCode: String,
    name: String,
    category: String,
    moreInfoUrl: String,
    termsAndConditionsUrl: String,
    description: String
  ): Box[ApiProductTrait]

  def getApiProductByBankIdAndCode(
    bankId: String,
    apiProductCode: String
  ): Box[ApiProductTrait]

  def getApiProductsByBankId(
    bankId: String
  ): List[ApiProductTrait]

  def deleteApiProduct(
    bankId: String,
    apiProductCode: String
  ): Box[Boolean]
}

object MappedApiProductsProvider extends MdcLoggable with ApiProductsProvider {

  override def createOrUpdateApiProduct(
    bankId: String,
    apiProductCode: String,
    parentApiProductCode: String,
    name: String,
    category: String,
    moreInfoUrl: String,
    termsAndConditionsUrl: String,
    description: String
  ): Box[ApiProductTrait] = {
    val existing = ApiProduct.find(
      By(ApiProduct.BankId, bankId),
      By(ApiProduct.ApiProductCode, apiProductCode)
    )
    existing match {
      case net.liftweb.common.Full(product) =>
        tryo(
          product
            .ParentApiProductCode(parentApiProductCode)
            .Name(name)
            .Category(category)
            .MoreInfoUrl(moreInfoUrl)
            .TermsAndConditionsUrl(termsAndConditionsUrl)
            .Description(description)
            .saveMe()
        )
      case _ =>
        tryo(
          ApiProduct
            .create
            .BankId(bankId)
            .ApiProductCode(apiProductCode)
            .ParentApiProductCode(parentApiProductCode)
            .Name(name)
            .Category(category)
            .MoreInfoUrl(moreInfoUrl)
            .TermsAndConditionsUrl(termsAndConditionsUrl)
            .Description(description)
            .saveMe()
        )
    }
  }

  override def getApiProductByBankIdAndCode(
    bankId: String,
    apiProductCode: String
  ): Box[ApiProductTrait] = ApiProduct.find(
    By(ApiProduct.BankId, bankId),
    By(ApiProduct.ApiProductCode, apiProductCode)
  )

  override def getApiProductsByBankId(
    bankId: String
  ): List[ApiProductTrait] = ApiProduct.findAll(By(ApiProduct.BankId, bankId))

  override def deleteApiProduct(
    bankId: String,
    apiProductCode: String
  ): Box[Boolean] = ApiProduct.find(
    By(ApiProduct.BankId, bankId),
    By(ApiProduct.ApiProductCode, apiProductCode)
  ).map(_.delete_!)
}
