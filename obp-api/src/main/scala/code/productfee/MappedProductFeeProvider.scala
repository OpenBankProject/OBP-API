package code.productfee

import code.api.util.APIUtil
import code.util.UUIDString
import com.openbankproject.commons.model.{BankId, ProductCode, ProductFee}
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.mapper.{MappedBoolean, _}
import net.liftweb.util.Helpers.tryo

import java.math.MathContext
import scala.math.BigDecimal
import com.openbankproject.commons.ExecutionContext.Implicits.global

import scala.concurrent.Future

object MappedProductFeeProvider extends ProductFeeProvider {

  override def getProductFeesFromProvider(bankId: BankId, productCode: ProductCode): Future[Box[List[ProductFee]]] =
    Future {
      Box !!  MappedProductFee.findAll(
          By(MappedProductFee.BankId, bankId.value),
          By(MappedProductFee.ProductCode, productCode.value)
        )
    }

  override def getProductFeeById(productFeeId: String): Future[Box[ProductFee]] = Future {
     MappedProductFee.find(By(MappedProductFee.ProductFeeId, productFeeId))
  }

  override def createOrUpdateProductFee(
    bankId: BankId,
    productCode: ProductCode,
    productFeeId: Option[String],
    name: String,
    isActive: Boolean,
    moreInfo: String,
    currency: String,
    amount: BigDecimal,
    frequency: String,
    `type`: String
  ): Future[Box[ProductFee]] =  {
     productFeeId match {
      case Some(id) => Future {
         MappedProductFee.find(By(MappedProductFee.ProductFeeId, id)) match {
            case Full(productFee) => tryo {
              productFee
                .BankId(bankId.value)
                .ProductCode(productCode.value)
                .Name(name)
                .IsActive(isActive)
                .MoreInfo(moreInfo)
                .Currency(currency)
                .Amount(amount)
                .Frequency(frequency)
                .Type(`type`)
                .saveMe()
            }
            case _ => Empty
          }
      }
      case None => Future {
        Full {
          MappedProductFee
            .create
            .ProductFeeId(APIUtil.generateUUID)
            .BankId(bankId.value)
            .ProductCode(productCode.value)
            .Name(name)
            .IsActive(isActive)
            .MoreInfo(moreInfo)
            .Currency(currency)
            .Amount(amount)
            .Frequency(frequency)
            .Type(`type`)
            .saveMe()
        }
      }
    }
  }

  override def deleteProductFee(productFeeId: String): Future[Box[Boolean]] = Future {
    tryo(
      MappedProductFee.bulkDelete_!!(By(MappedProductFee.ProductFeeId, productFeeId))
    )
  }
}

class MappedProductFee extends ProductFee with LongKeyedMapper[MappedProductFee] with IdPK {

  override def getSingleton = MappedProductFee

  object BankId extends UUIDString(this) 

  object ProductCode extends MappedString(this, 50) 
  
  object ProductFeeId extends UUIDString(this) 

  object Name extends MappedString(this, 50)
  
  object IsActive extends MappedBoolean(this) {
    override def defaultValue = true
  }
  
  object MoreInfo extends MappedString(this, 255)

  object Currency extends MappedString(this, 50)
  
  object Amount extends MappedDecimal(this, MathContext.DECIMAL64, 0)
  
  object Frequency extends MappedString(this, 255)
  
  object Type extends MappedString(this, 255)
  

  override def bankId: BankId = com.openbankproject.commons.model.BankId(BankId.get)

  override def productCode: ProductCode = com.openbankproject.commons.model.ProductCode(ProductCode.get)
  
  override def productFeeId: String = ProductFeeId.get

  override def name: String = Name.get

  override def isActive: Boolean = IsActive.get

  override def moreInfo: String = MoreInfo.get

  override def currency: String = Currency.get

  override def amount: BigDecimal = Amount.get
  
  override def frequency: String = Frequency.get
  
  override def `type`: String = Type.get
  
}

object MappedProductFee extends MappedProductFee with LongKeyedMetaMapper[MappedProductFee]  {
  override def dbIndexes = Index(BankId) :: Index(ProductFeeId) :: super.dbIndexes 
}

