package code.model.dataAccess




/* APermission.scala */

/*
 * A permission that has three parts; domain, actions, and entities
 */
case class APermission(
                        val domain: String,
                        val actions: Set[String] = Set(APermission.wildcardToken),
                        val entities: Set[String] = Set(APermission.wildcardToken))
{
  def implies(p: APermission) = p match {
    case APermission(d, a, e) =>
      if (d == APermission.wildcardToken)
        true
      else if (d == this.domain) {
        if (a.contains(APermission.wildcardToken))
          true
        else if (this.actions.headOption.map(it => a.contains(it)).getOrElse(false))
          if (e.contains(APermission.wildcardToken))
            true
          else
            this.entities.headOption.map(it => e.contains(it)).getOrElse(false)
        else
          false
      }
      else
        false
    case _ => false
  }

  def implies(ps: Set[APermission]): Boolean = ps.exists(this.implies)

  override def toString = {
    domain+APermission.partDivider+actions.mkString(APermission.subpartDivider)+APermission.partDivider+entities.mkString(APermission.subpartDivider)
  }

}

object APermission {
  // Permission
  val permissionWilcardToken = "*"
  val permissionPartDivider = ":"
  val permissionSubpartDivider = ","
  val permissionCaseSensitive = true

  lazy val wildcardToken  = permissionWilcardToken
  lazy val partDivider    = permissionPartDivider
  lazy val subpartDivider = permissionSubpartDivider

  def apply(domain: String, actions: String): APermission =
    apply(domain, actions.split(APermission.subpartDivider).toSet)

  def apply(domain: String, actions: String, entities: String): APermission =
    apply(domain, actions.split(APermission.subpartDivider).toSet, entities.split(APermission.subpartDivider).toSet)

  def fromString(s: String): APermission = s.split(partDivider).toList match {
    case s :: Nil if (s == wildcardToken) => all
    case s :: Nil if (s.length == 0) => none
    case dom :: Nil => APermission(dom)
    case dom :: acts :: Nil => APermission(dom, acts)
    case dom :: acts :: ents :: Nil => APermission(dom, acts, ents)
    case _ => none
  }

  lazy val all = APermission(wildcardToken)
  lazy val none = APermission("")
}