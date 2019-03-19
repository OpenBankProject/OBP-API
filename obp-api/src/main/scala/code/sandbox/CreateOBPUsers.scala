package code.sandbox

import code.model.dataAccess.{AuthUser, ResourceUser}
import code.users.Users
import net.liftweb.common.{Box, Failure, Full}
import net.liftweb.mapper.By

trait CreateAuthUsers {

  self : OBPDataImport =>

  override protected def createSaveableUser(u : SandboxUserImport) : Box[Saveable[ResourceUser]] = {

    def asSaveable(u : AuthUser) = new Saveable[ResourceUser] {
      val value = u.createUnsavedResourceUser()
      def save() = {
        val usr = Users.users.vend.saveResourceUser(value)
        for (uu <- usr) {
          u.user(uu).save()
        }
      }
    }

    val existingAuthUser = AuthUser.find(By(AuthUser.username, u.user_name))

    if(existingAuthUser.isDefined) {
      logger.warn(s"Existing AuthUser with email ${u.email} detected in data import where no ResourceUser was found")
      Failure(s"User with email ${u.email} already exist (and may be different (e.g. different display_name)")
    } else {
      val authUser = AuthUser.create
        .email(u.email)
        .lastName(u.user_name)
        .username(u.user_name)
        .password(u.password)
        .validated(true)

      val validationErrors = authUser.validate
      if(!validationErrors.isEmpty) Failure(s"Errors: ${validationErrors.map(_.msg)}")
      else Full(asSaveable(authUser))
    }
  }

}
