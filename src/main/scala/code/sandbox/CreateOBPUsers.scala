package code.sandbox

import code.model.dataAccess.{AuthUser, ResourceUser}
import code.users.Users
import net.liftweb.common.{Box, Failure, Full}
import net.liftweb.mapper.By
import net.liftweb.util.Props

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
        //AuthUser.create
        //.email(u.email)
        //.lastName(u.user_name)
        //.username(u.user_name)
        //.password(u.password)
        //.validated(true)

      println("--------------------------------> " + authUser)

      val validationErrors = authUser.validate
      if(validationErrors.nonEmpty) Failure(s"Errors: ${validationErrors.map(_.msg)}")
      else {
        val resourceUser = authUser.user.obj //Users.users.vend.createResourceUser(
          //authUser.provider,
          //Some(authUser.provider),
          //Some(u.user_name),
          //Some(u.email),
          //Some(authUser.resourceUserId)
          //)
        //authUser.save()
        println("--------------------------------> " + authUser)
        println("--------------------------------> " + resourceUser)
        resourceUser
      }
    }
  }

}

/*
  def createAuthUser(mail: String, prov: String, uname: String, pass: String): AuthUser = {
    Users.users.vend.createResourceUser(
      prov,
      Some(resourceUserId.get),
      Some(uname),
      Some(mail),
      Some(resourceUserId.get))
    AuthUser.create
      .firstName(uname)
      .email(mail)
      .username(uname)
      // No need to store password, so store dummy string instead
      .password(pass)
      .provider(prov)
      .validated(true)
      .saveMe
  }
 */