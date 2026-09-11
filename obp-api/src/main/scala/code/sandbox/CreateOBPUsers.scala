package code.sandbox

import code.api.util.APIUtil.fullPasswordValidation
import code.api.util.ErrorMessages
import code.model.dataAccess.{AuthUser, ResourceUser}
import code.users.Users
import net.liftweb.common.{Box, Failure, Full}

trait CreateAuthUsers {

  self : OBPDataImport =>

  override protected def createSaveableUser(u : SandboxUserImport) : Box[Saveable[ResourceUser]] = {

    def asSaveable(u : AuthUser) = new Saveable[ResourceUser] {
      lazy val value = u.createUnsavedResourceUser()
      def save() = {
        val usr = Users.users.vend.saveResourceUser(value)
        for (uu <- usr) {
          // The foreign key holds RESOURCEUSER.ID; it used to take the entity itself.
          u.copy(user = uu.id).save
        }
      }
    }

    val existingAuthUser = AuthUser.findByUsername(u.user_name)

    if(existingAuthUser.isDefined) {
      logger.warn(s"Existing AuthUser with email ${u.email} detected in data import where no ResourceUser was found")
      Failure(s"User with email ${u.email} already exist (and may be different (e.g. different display_name)")
    } else {
      val authUser = AuthUser(
        email = u.email,
        firstName = u.user_name,
        lastName = u.user_name,
        username = u.user_name,
        validated = true).withPassword(u.password)

      val validationErrors = AuthUser.validate(authUser)
      if (!fullPasswordValidation(u.password)) Failure(ErrorMessages.InvalidStrongPasswordFormat)
      else if(!validationErrors.isEmpty) Failure(s"Errors: ${validationErrors}")
      else Full(asSaveable(authUser))
    }
  }

}
