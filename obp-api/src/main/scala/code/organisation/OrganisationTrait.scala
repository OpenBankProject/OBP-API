package code.organisation

import net.liftweb.common.Box
import net.liftweb.util.SimpleInjector

import scala.concurrent.Future

object Organisations extends SimpleInjector {
  val organisation = new Inject(() => buildOne) {}

  def buildOne: OrganisationProvider = DoobieOrganisationProvider
}

trait OrganisationProvider {
  def createOrganisation(
    organisationId: String,
    name: String,
    website: Option[String],
    logoUrl: Option[String],
    status: String,
    visibility: String,
    createdByUserId: String
  ): Box[OrganisationTrait]

  def getOrganisation(organisationId: String): Box[OrganisationTrait]

  def getAllOrganisations(): Future[Box[List[OrganisationTrait]]]

  def updateOrganisation(
    organisationId: String,
    name: Option[String],
    website: Option[String],
    logoUrl: Option[String],
    status: Option[String],
    visibility: Option[String]
  ): Box[OrganisationTrait]

  def deleteOrganisation(organisationId: String): Box[Boolean]
}

trait OrganisationTrait {
  def organisationId: String
  def name: String
  def website: Option[String]
  def logoUrl: Option[String]
  def status: String
  def visibility: String
  def createdByUserId: String
  def createdAt: java.util.Date
  def updatedAt: java.util.Date
}
