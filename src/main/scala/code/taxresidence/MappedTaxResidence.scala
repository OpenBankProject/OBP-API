package code.taxresidence

import code.customer.MappedCustomer
import code.util.MappedUUID
import net.liftweb.common.Box
import net.liftweb.mapper._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object MappedTaxResidenceProvider extends TaxResidenceProvider {

  def getTaxResidenceRemote(customerId: String): Box[List[TaxResidence]] = {
    val id: Box[MappedCustomer] = MappedCustomer.find(By(MappedCustomer.mCustomerId, customerId))
    id.map(customer => MappedTaxResidence.findAll(By(MappedTaxResidence.mCustomerId, customer.id.get)))
  }
  override def getTaxResidence(customerId: String): Future[Box[List[TaxResidence]]] = {
    Future(getTaxResidenceRemote(customerId))
  }
  def addTaxResidenceRemote(customerId: String, domain: String, taxNumber: String): Box[TaxResidence] = {
    val id: Box[MappedCustomer] = MappedCustomer.find(By(MappedCustomer.mCustomerId, customerId))
    id.map(customer => MappedTaxResidence.create.mCustomerId(customer.id.get).mDomain(domain).mTaxNumber(taxNumber).saveMe())
  }
  override def addTaxResidence(customerId: String, domain: String, taxNumber: String): Future[Box[TaxResidence]] = {
    Future(addTaxResidenceRemote(customerId, domain, taxNumber))
  }
}

class MappedTaxResidence extends TaxResidence with LongKeyedMapper[MappedTaxResidence] with IdPK with CreatedUpdated {

  def getSingleton = MappedTaxResidence

  object mCustomerId extends MappedLongForeignKey(this, MappedCustomer)
  object mTaxResidenceId extends MappedUUID(this)
  object mDomain extends MappedString(this, 255)
  object mTaxNumber extends MappedString(this, 255)

  override def customerId: Long = mCustomerId.get
  override def taxResidenceId: String = mTaxResidenceId.get
  override def domain: String = mDomain.get
  override def taxNumber: String = mTaxNumber.get

}

object MappedTaxResidence extends MappedTaxResidence with LongKeyedMetaMapper[MappedTaxResidence] {
  override def dbIndexes = UniqueIndex(mCustomerId, mDomain, mTaxNumber) :: super.dbIndexes
}
