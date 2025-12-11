package code.group

import net.liftweb.common.Box
import net.liftweb.util.SimpleInjector

import scala.concurrent.Future

object GroupTrait extends SimpleInjector {
  val group = new Inject(buildOne _) {}
  
  def buildOne: GroupProvider = MappedGroupProvider
}

trait GroupProvider {
  def createGroup(
    bankId: Option[String],
    groupName: String,
    groupDescription: String,
    listOfRoles: List[String],
    isEnabled: Boolean
  ): Box[GroupTrait]
  
  def getGroup(groupId: String): Box[GroupTrait]
  def getGroupsByBankId(bankId: Option[String]): Future[Box[List[GroupTrait]]]
  def getAllGroups(): Future[Box[List[GroupTrait]]]
  
  def updateGroup(
    groupId: String,
    groupName: Option[String],
    groupDescription: Option[String],
    listOfRoles: Option[List[String]],
    isEnabled: Option[Boolean]
  ): Box[GroupTrait]
  
  def deleteGroup(groupId: String): Box[Boolean]
}

trait GroupTrait {
  def groupId: String
  def bankId: Option[String]
  def groupName: String
  def groupDescription: String
  def listOfRoles: List[String]
  def isEnabled: Boolean
}