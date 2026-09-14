/**
Open Bank Project - API
Copyright (C) 2011-2026, TESOBE GmbH.

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.

Email: contact@tesobe.com
TESOBE GmbH.
Osloer Strasse 16/17
Berlin 13359, Germany

This product includes software developed at
TESOBE (http://www.tesobe.com/)

  */

package code.group

import code.util.MappedUUID
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.mapper._
import net.liftweb.util.Helpers.tryo

import scala.concurrent.Future
import com.openbankproject.commons.ExecutionContext.Implicits.global

object MappedGroupProvider extends GroupProvider {
  
  override def createGroup(
    bankId: Option[String],
    groupName: String,
    groupDescription: String,
    listOfRoles: List[String],
    isEnabled: Boolean
  ): Box[GroupTrait] = {
    tryo {
      Group.create
        .BankId(bankId.getOrElse(""))
        .GroupName(groupName)
        .GroupDescription(groupDescription)
        .ListOfRoles(listOfRoles.mkString(","))
        .IsEnabled(isEnabled)
        .saveMe()
    }
  }
  
  override def getGroup(groupId: String): Box[GroupTrait] = {
    Group.find(By(Group.GroupId, groupId))
  }
  
  override def getGroupsByBankId(bankId: Option[String]): Future[Box[List[GroupTrait]]] = {
    Future {
      tryo {
        bankId match {
          case Some(id) => 
            Group.findAll(By(Group.BankId, id))
          case None => 
            Group.findAll(By(Group.BankId, ""))
        }
      }
    }
  }
  
  override def getAllGroups(): Future[Box[List[GroupTrait]]] = {
    Future {
      tryo {
        Group.findAll()
      }
    }
  }
  
  override def updateGroup(
    groupId: String,
    groupName: Option[String],
    groupDescription: Option[String],
    listOfRoles: Option[List[String]],
    isEnabled: Option[Boolean]
  ): Box[GroupTrait] = {
    Group.find(By(Group.GroupId, groupId)).flatMap { group =>
      tryo {
        groupName.foreach(name => group.GroupName(name))
        groupDescription.foreach(desc => group.GroupDescription(desc))
        listOfRoles.foreach(roles => group.ListOfRoles(roles.mkString(",")))
        isEnabled.foreach(enabled => group.IsEnabled(enabled))
        group.saveMe()
      }
    }
  }
  
  override def deleteGroup(groupId: String): Box[Boolean] = {
    Group.find(By(Group.GroupId, groupId)).flatMap { group =>
      tryo {
        group.delete_!
      }
    }
  }
}

class Group extends GroupTrait with LongKeyedMapper[Group] with IdPK with CreatedUpdated {
  
  def getSingleton = Group
  
  object GroupId extends MappedUUID(this)
  object BankId extends MappedString(this, 255) // Empty string for system-level groups
  object GroupName extends MappedString(this, 255)
  object GroupDescription extends MappedText(this)
  object ListOfRoles extends MappedText(this) // Comma-separated list of roles
  object IsEnabled extends MappedBoolean(this)
  
  override def groupId: String = GroupId.get.toString
  override def bankId: Option[String] = {
    val id = BankId.get
    if (id == null || id.isEmpty) None else Some(id)
  }
  override def groupName: String = GroupName.get
  override def groupDescription: String = GroupDescription.get
  override def listOfRoles: List[String] = {
    val rolesStr = ListOfRoles.get
    if (rolesStr == null || rolesStr.isEmpty) List.empty
    else rolesStr.split(",").map(_.trim).filter(_.nonEmpty).toList
  }
  override def isEnabled: Boolean = IsEnabled.get
}

object Group extends Group with LongKeyedMetaMapper[Group] {
  override def dbTableName = "GroupOfRoles" // define the DB table name
  override def dbIndexes = Index(GroupId) :: Index(BankId) :: super.dbIndexes
}