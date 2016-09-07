package code.sandbox

import code.model.dataAccess.{OBPUser, APIUser}
import net.liftweb.common.{Full, Failure, Box}
import net.liftweb.mapper.By

trait CreateOBPUsers {

  self : OBPDataImport =>

  override protected def createSaveableUser(u : SandboxUserImport) : Box[Saveable[APIUser]] = {

    def asSaveable(u : OBPUser) = new Saveable[APIUser] {
      val value = u.createUnsavedApiUser()
      def save() = {
        value.save()
        u.user(value).save()
      }
    }

    val existingObpUser = OBPUser.find(By(OBPUser.username, u.user_name))

    if(existingObpUser.isDefined) {
      logger.warn(s"Existing OBPUser with email ${u.email} detected in data import where no APIUser was found")
      Failure(s"User with email ${u.email} already exist (and may be different (e.g. different display_name)")
    } else {
      val obpUser = OBPUser.create
        .email(u.email)
        .lastName(u.user_name)
        .username(u.user_name)
        .password(u.password)
        .validated(true)

      val validationErrors = obpUser.validate
      if(!validationErrors.isEmpty) Failure(s"Errors: ${validationErrors.map(_.msg)}")
      else Full(asSaveable(obpUser))
    }
  }

}
