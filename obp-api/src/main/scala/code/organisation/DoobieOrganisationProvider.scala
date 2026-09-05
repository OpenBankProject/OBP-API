package code.organisation

import code.api.util.DoobieUtil
import doobie._
import doobie.implicits._
import doobie.implicits.javasql._
import net.liftweb.common.{Box, Empty, Failure, Full}

import scala.concurrent.Future
import com.openbankproject.commons.ExecutionContext.Implicits.global

case class OrganisationRow(
  organisationId: String,
  name: String,
  website: Option[String],
  logoUrl: Option[String],
  status: String,
  visibility: String,
  createdByUserId: String,
  createdAt: java.util.Date,
  updatedAt: java.util.Date
) extends OrganisationTrait

object DoobieOrganisationProvider extends OrganisationProvider {

  private def opt(s: String): Option[String] =
    if (s == null || s.isEmpty) None else Some(s)

  private val selectColumns =
    fr"""SELECT organisationid, name, website, logourl, status, visibility, createdbyuserid, creationdate, lastupdate
         FROM organisation"""

  private def fromRow(row: (String, String, String, String, String, String, String, java.sql.Timestamp, java.sql.Timestamp)): OrganisationRow =
    row match {
      case (organisationId, name, website, logoUrl, status, visibility, createdByUserId, createdAt, updatedAt) =>
        OrganisationRow(organisationId, name, opt(website), opt(logoUrl), status, visibility, createdByUserId, createdAt, updatedAt)
    }

  override def createOrganisation(
    organisationId: String,
    name: String,
    website: Option[String],
    logoUrl: Option[String],
    status: String,
    visibility: String,
    createdByUserId: String
  ): Box[OrganisationTrait] = {
    val now = new java.sql.Timestamp(System.currentTimeMillis())
    val websiteValue = website.getOrElse("")
    val logoUrlValue = logoUrl.getOrElse("")
    try {
      DoobieUtil.runUpdate(
        sql"""INSERT INTO organisation (organisationid, name, website, logourl, status, visibility, createdbyuserid, creationdate, lastupdate)
              VALUES ($organisationId, $name, $websiteValue, $logoUrlValue, $status, $visibility, $createdByUserId, $now, $now)"""
          .update.run)
      Full(OrganisationRow(organisationId, name, website, logoUrl, status, visibility, createdByUserId, now, now))
    } catch {
      case e: Exception => Failure(e.getMessage, Full(e), Empty)
    }
  }

  override def getOrganisation(organisationId: String): Box[OrganisationTrait] =
    DoobieUtil.runQuery(
      (selectColumns ++ fr"WHERE organisationid = $organisationId")
        .query[(String, String, String, String, String, String, String, java.sql.Timestamp, java.sql.Timestamp)].option
    ) match {
      case Some(row) => Full(fromRow(row))
      case None => Empty
    }

  override def getAllOrganisations(): Future[Box[List[OrganisationTrait]]] = Future {
    try {
      Full(DoobieUtil.runQuery(
        selectColumns.query[(String, String, String, String, String, String, String, java.sql.Timestamp, java.sql.Timestamp)].to[List]
      ).map(fromRow))
    } catch {
      case e: Exception => Failure(e.getMessage, Full(e), Empty)
    }
  }

  override def updateOrganisation(
    organisationId: String,
    name: Option[String],
    website: Option[String],
    logoUrl: Option[String],
    status: Option[String],
    visibility: Option[String]
  ): Box[OrganisationTrait] =
    getOrganisation(organisationId) match {
      case Full(existing: OrganisationRow) =>
        val updated = existing.copy(
          name = name.getOrElse(existing.name),
          website = website.orElse(existing.website),
          logoUrl = logoUrl.orElse(existing.logoUrl),
          status = status.getOrElse(existing.status),
          visibility = visibility.getOrElse(existing.visibility)
        )
        val now = new java.sql.Timestamp(System.currentTimeMillis())
        try {
          DoobieUtil.runUpdate(
            sql"""UPDATE organisation SET name = ${updated.name}, website = ${updated.website.getOrElse("")},
                  logourl = ${updated.logoUrl.getOrElse("")}, status = ${updated.status}, visibility = ${updated.visibility}, lastupdate = $now
                  WHERE organisationid = $organisationId"""
              .update.run)
          Full(updated.copy(updatedAt = now))
        } catch {
          case e: Exception => Failure(e.getMessage, Full(e), Empty)
        }
      case other => other
    }

  override def deleteOrganisation(organisationId: String): Box[Boolean] =
    getOrganisation(organisationId) match {
      case Full(_) =>
        DoobieUtil.runUpdate(sql"DELETE FROM organisation WHERE organisationid = $organisationId".update.run)
        Full(true)
      case Empty => Empty
      case f: Failure => f
    }
}
