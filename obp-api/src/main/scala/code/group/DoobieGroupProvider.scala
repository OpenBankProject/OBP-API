package code.group

import code.api.util.{APIUtil, DoobieUtil}
import doobie._
import doobie.implicits._
import doobie.implicits.javasql._
import net.liftweb.common.{Box, Empty, Failure, Full}

import scala.concurrent.Future
import com.openbankproject.commons.ExecutionContext.Implicits.global

case class GroupRow(
  groupId: String,
  bankId: Option[String],
  groupName: String,
  groupDescription: String,
  listOfRoles: List[String],
  isEnabled: Boolean
) extends GroupTrait

object DoobieGroupProvider extends GroupProvider {

  private val selectColumns =
    fr"SELECT groupid, bankid, groupname, groupdescription, listofroles, isenabled FROM groupofroles"

  private def fromRow(row: (String, String, String, String, String, Boolean)): GroupTrait =
    row match {
      case (groupId, bankId, groupName, groupDescription, listOfRoles, isEnabled) =>
        val bankIdOpt = if (bankId == null || bankId.isEmpty) None else Some(bankId)
        val roles = if (listOfRoles == null || listOfRoles.isEmpty) List.empty
          else listOfRoles.split(",").map(_.trim).filter(_.nonEmpty).toList
        GroupRow(groupId, bankIdOpt, groupName, groupDescription, roles, isEnabled)
    }

  override def createGroup(
    bankId: Option[String],
    groupName: String,
    groupDescription: String,
    listOfRoles: List[String],
    isEnabled: Boolean
  ): Box[GroupTrait] = {
    val newGroupId = APIUtil.generateUUID()
    val bankIdValue = bankId.getOrElse("")
    val rolesValue = listOfRoles.mkString(",")
    val now = new java.sql.Timestamp(System.currentTimeMillis())
    try {
      DoobieUtil.runUpdate(
        sql"""INSERT INTO groupofroles (groupid, bankid, groupname, groupdescription, listofroles, isenabled, createdat, updatedat)
              VALUES ($newGroupId, $bankIdValue, $groupName, $groupDescription, $rolesValue, $isEnabled, $now, $now)"""
          .update.run)
      Full(GroupRow(newGroupId, bankId.filter(_.nonEmpty), groupName, groupDescription, listOfRoles, isEnabled))
    } catch {
      case e: Exception => Failure(e.getMessage, Full(e), Empty)
    }
  }

  override def getGroup(groupId: String): Box[GroupTrait] =
    DoobieUtil.runQuery(
      (selectColumns ++ fr"WHERE groupid = $groupId")
        .query[(String, String, String, String, String, Boolean)].option
    ) match {
      case Some(row) => Full(fromRow(row))
      case None => Empty
    }

  override def getGroupsByBankId(bankId: Option[String]): Future[Box[List[GroupTrait]]] = Future {
    val bankIdValue = bankId.getOrElse("")
    try {
      Full(DoobieUtil.runQuery(
        (selectColumns ++ fr"WHERE bankid = $bankIdValue")
          .query[(String, String, String, String, String, Boolean)].to[List]
      ).map(fromRow))
    } catch {
      case e: Exception => Failure(e.getMessage, Full(e), Empty)
    }
  }

  override def getAllGroups(): Future[Box[List[GroupTrait]]] = Future {
    try {
      Full(DoobieUtil.runQuery(
        selectColumns.query[(String, String, String, String, String, Boolean)].to[List]
      ).map(fromRow))
    } catch {
      case e: Exception => Failure(e.getMessage, Full(e), Empty)
    }
  }

  override def updateGroup(
    groupId: String,
    groupName: Option[String],
    groupDescription: Option[String],
    listOfRoles: Option[List[String]],
    isEnabled: Option[Boolean]
  ): Box[GroupTrait] =
    getGroup(groupId) match {
      case Full(existing: GroupRow) =>
        val updated = existing.copy(
          groupName = groupName.getOrElse(existing.groupName),
          groupDescription = groupDescription.getOrElse(existing.groupDescription),
          listOfRoles = listOfRoles.getOrElse(existing.listOfRoles),
          isEnabled = isEnabled.getOrElse(existing.isEnabled)
        )
        val now = new java.sql.Timestamp(System.currentTimeMillis())
        try {
          DoobieUtil.runUpdate(
            sql"""UPDATE groupofroles SET groupname = ${updated.groupName}, groupdescription = ${updated.groupDescription},
                  listofroles = ${updated.listOfRoles.mkString(",")}, isenabled = ${updated.isEnabled}, updatedat = $now
                  WHERE groupid = $groupId"""
              .update.run)
          Full(updated)
        } catch {
          case e: Exception => Failure(e.getMessage, Full(e), Empty)
        }
      case other => other
    }

  override def deleteGroup(groupId: String): Box[Boolean] =
    getGroup(groupId) match {
      case Full(_) =>
        DoobieUtil.runUpdate(sql"DELETE FROM groupofroles WHERE groupid = $groupId".update.run)
        Full(true)
      case Empty => Empty
      case f: Failure => f
    }
}
