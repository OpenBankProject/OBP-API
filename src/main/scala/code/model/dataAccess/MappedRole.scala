package code.model.dataAccess

import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.mapper.{MappedString, Mapper}


abstract class MappedRole[T<:Mapper[T]](_fieldOwner: T) extends MappedString[T](_fieldOwner, 1024) {

  import scala.xml._
  import net.liftweb.http.SHtml._

  def buildDisplayList: List[(String, String)] = {
    Role.allRoles(Role.CAT_TEAM) map (r => (r.id.get, r.displayName))
  }

  def chosenElement = names match {
    case name :: _ => Full(name)
    case _ => Empty
  }

  override def _toForm: Box[Elem] =
    Full(selectObj[String](buildDisplayList, chosenElement, v => this.setRole(v)))

  override def asHtml = firstRole.map(_.asHtml).openOr(Text(""))

  def permissions: List[APermission] = names.flatMap(n => Role.find(n).map(_.permissions.allPerms).openOr(Nil))

  def names: List[String] = {
    val current = if (get == null) "" else get
    current.split(",").toList
  }

  def addRole(role: String*) = {
    val current = if (get == null) "" else get
    val add = (if (current.length() > 0) "," else "") + role.mkString(",")
    set(current + add)
    fieldOwner
  }

  def setRole(role: String): T = {
    removeAll
    addRole(role)
    fieldOwner
  }

  def setRole(role: Role): T = setRole(role.id.get)

  def removeAll = {
    set(""); this
  }

  def firstRole: Box[Role] = names.headOption.flatMap(name => Role.find(name))
}