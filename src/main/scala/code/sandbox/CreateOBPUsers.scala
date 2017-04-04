package code.sandbox

import code.model.dataAccess.{AuthUser, ResourceUser}
import net.liftweb.common.{Box, Failure}
import net.liftweb.util.FieldError


trait CreateAuthUsers {

  self : OBPDataImport =>

  override protected def createUser(u : SandboxUserImport) : Box[ResourceUser] = {

    val existingAuthUser = AuthUser.findUserByUsername(u.user_name)

    if(existingAuthUser.isDefined) {
      logger.warn(s"Existing AuthUser with email ${u.email} detected in data import where no ResourceUser was found")
      Failure(s"User with email ${u.email} already exist (and may be different (e.g. different display_name)")
    } else {

      val authUser = AuthUser.createAuthUser(
                u.email,
                u.user_name,
                u.password
              )
              .lastName(u.user_name)

      val validationErrors = authUser.validate
      if(validationErrors.nonEmpty) Failure(s"Errors: ${validationErrors.map(_.msg)}")
      else {
        val resourceUser = authUser.user.obj
        resourceUser
      }
    }
  }

}
