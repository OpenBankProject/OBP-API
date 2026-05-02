package code.organisation

import net.liftweb.common.Box
import net.liftweb.mapper._
import net.liftweb.util.Helpers.tryo
import com.openbankproject.commons.ExecutionContext.Implicits.global

import scala.concurrent.Future

object MappedOrganisationProvider extends OrganisationProvider {

  override def createOrganisation(
    organisationId: String,
    name: String,
    website: Option[String],
    logoUrl: Option[String],
    status: String,
    visibility: String,
    createdByUserId: String
  ): Box[OrganisationTrait] = {
    tryo {
      MappedOrganisation.create
        .OrganisationId(organisationId)
        .Name(name)
        .Website(website.getOrElse(""))
        .LogoUrl(logoUrl.getOrElse(""))
        .Status(status)
        .Visibility(visibility)
        .CreatedByUserId(createdByUserId)
        .saveMe()
    }
  }

  override def getOrganisation(organisationId: String): Box[OrganisationTrait] = {
    MappedOrganisation.find(By(MappedOrganisation.OrganisationId, organisationId))
  }

  override def getAllOrganisations(): Future[Box[List[OrganisationTrait]]] = {
    Future {
      tryo { MappedOrganisation.findAll() }
    }
  }

  override def updateOrganisation(
    organisationId: String,
    name: Option[String],
    website: Option[String],
    logoUrl: Option[String],
    status: Option[String],
    visibility: Option[String]
  ): Box[OrganisationTrait] = {
    MappedOrganisation.find(By(MappedOrganisation.OrganisationId, organisationId)).flatMap { org =>
      tryo {
        name.foreach(v => org.Name(v))
        website.foreach(v => org.Website(v))
        logoUrl.foreach(v => org.LogoUrl(v))
        status.foreach(v => org.Status(v))
        visibility.foreach(v => org.Visibility(v))
        org.LastUpdate(new java.util.Date())
        org.saveMe()
      }
    }
  }

  override def deleteOrganisation(organisationId: String): Box[Boolean] = {
    MappedOrganisation.find(By(MappedOrganisation.OrganisationId, organisationId)).flatMap { org =>
      tryo { org.delete_! }
    }
  }
}

class MappedOrganisation extends OrganisationTrait with LongKeyedMapper[MappedOrganisation] with IdPK {

  def getSingleton = MappedOrganisation

  object OrganisationId extends MappedString(this, 64)
  object Name extends MappedString(this, 255)
  object Website extends MappedString(this, 1024)
  object LogoUrl extends MappedString(this, 1024)
  object Status extends MappedString(this, 32)
  object Visibility extends MappedString(this, 32)
  object CreatedByUserId extends MappedString(this, 255)
  object CreationDate extends MappedDateTime(this) {
    override def defaultValue = new java.util.Date()
  }
  object LastUpdate extends MappedDateTime(this) {
    override def defaultValue = new java.util.Date()
  }

  override def organisationId: String = OrganisationId.get
  override def name: String = Name.get
  override def website: Option[String] = {
    val v = Website.get
    if (v == null || v.isEmpty) None else Some(v)
  }
  override def logoUrl: Option[String] = {
    val v = LogoUrl.get
    if (v == null || v.isEmpty) None else Some(v)
  }
  override def status: String = Status.get
  override def visibility: String = Visibility.get
  override def createdByUserId: String = CreatedByUserId.get
  override def createdAt: java.util.Date = CreationDate.get
  override def updatedAt: java.util.Date = LastUpdate.get
}

object MappedOrganisation extends MappedOrganisation with LongKeyedMetaMapper[MappedOrganisation] {
  override def dbTableName = "Organisation"
  override def dbIndexes = UniqueIndex(OrganisationId) :: super.dbIndexes
}
