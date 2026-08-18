package code.model

import code.model.dataAccess.AuthUser
import code.setup.ServerSetup
import net.liftweb.common.Full
import net.liftweb.util.Helpers
import org.mindrot.jbcrypt.BCrypt

/**
 * The two password columns are a contract with other services, not an implementation detail.
 *
 * `v_oidc_users` (obp-api/src/main/scripts/sql/OIDC/cre_v_oidc_users.sql) selects au.password_pw
 * and au.password_slt straight out of authuser, and both OBP-OIDC and the Keycloak user storage
 * provider verify logins from that view - the Keycloak one by JDBC, without going through this
 * codebase at all. So the split MappedPassword invented has to survive the move to Doobie exactly:
 * PASSWORD_PW is "b;" plus the first 44 characters of the 60-character bcrypt string, PASSWORD_SLT
 * is the remaining 16, and putting them back together yields a hash bcrypt accepts.
 *
 * Nothing pinned that before this: the login tests all go through matchPassword, so a change that
 * altered the stored format consistently on both sides would pass them while locking every existing
 * user out of OBP-OIDC and Keycloak. These scenarios assert the stored bytes, not the round trip.
 */
class AuthUserPasswordFormatTest extends ServerSetup {

  private val plainPassword = "n0t-a-real-password!"

  feature("the stored password columns") {

    scenario("keep the shape v_oidc_users and its consumers expect") {
      val (passwordPw, passwordSlt) = AuthUser.hashPassword(plainPassword)

      passwordPw.startsWith("b;") should equal(true)
      // "b;" + 44 characters of the bcrypt string; the column is VARCHAR(48).
      passwordPw.length should equal(46)
      // The remaining 16 characters; the column is VARCHAR(20).
      passwordSlt.length should equal(16)
    }

    scenario("reassemble into a hash bcrypt accepts, which is what OBP-OIDC does") {
      val (passwordPw, passwordSlt) = AuthUser.hashPassword(plainPassword)

      // Exactly the reassembly an external verifier performs from the two view columns.
      val reassembled = passwordPw.substring(2) + passwordSlt
      reassembled.length should equal(60)
      BCrypt.checkpw(plainPassword, reassembled) should equal(true)
      BCrypt.checkpw("some other password", reassembled) should equal(false)
    }

    scenario("verify a hash written the way Lift's MappedPassword wrote it") {
      // A row that predates this migration: bcrypt, split at 44, exactly as the Mapper field did.
      val bcrypted = BCrypt.hashpw(plainPassword, BCrypt.gensalt())
      val legacyPw = "b;" + bcrypted.substring(0, 44)
      val legacySlt = bcrypted.substring(44)

      AuthUser.matchPassword(plainPassword, legacyPw, legacySlt) should equal(true)
      AuthUser.matchPassword("wrong", legacyPw, legacySlt) should equal(false)
    }

    scenario("still verify the pre-bcrypt digest older rows carry") {
      // Rows written before bcrypt keep a salted digest and no "b;" prefix. MappedPassword kept
      // accepting them, so this branch has to survive too or those users cannot log in.
      val salt = "0123456789abcdef"
      val digest = Helpers.hash("{" + plainPassword + "} salt={" + salt + "}")

      AuthUser.matchPassword(plainPassword, digest, salt) should equal(true)
      AuthUser.matchPassword("wrong", digest, salt) should equal(false)
    }

    scenario("reject a password against a row that never had one set") {
      // hashPassword refuses anything too short, storing "*" - which must not match anything.
      val (unsetPw, unsetSlt) = AuthUser.hashPassword("abc")
      unsetPw should equal("*")

      AuthUser.matchPassword("abc", unsetPw, unsetSlt) should equal(false)
      AuthUser.matchPassword("*", unsetPw, unsetSlt) should equal(false)
      AuthUser.matchPassword(plainPassword, null, null) should equal(false)
    }
  }

  feature("a saved AuthUser") {

    scenario("stores the two columns so the view can serve it") {
      val username = "pwformat_" + Helpers.randomString(10).toLowerCase
      val saved = AuthUser(
        firstName = "Password",
        lastName = "Format",
        email = username + "@example.com",
        username = username,
        validated = true).withPassword(plainPassword).saveMe()

      AuthUser.findByUsername(username) match {
        case Full(reloaded) =>
          // Read back from the database, not from the in-memory row.
          reloaded.passwordPw.startsWith("b;") should equal(true)
          reloaded.passwordSlt.length should equal(16)
          BCrypt.checkpw(plainPassword, reloaded.passwordPw.substring(2) + reloaded.passwordSlt) should equal(true)
          reloaded.testPassword(Full(plainPassword)) should equal(true)
          reloaded.testPassword(Full("wrong")) should equal(false)
          // The view inner-joins on user_c, so a saved AuthUser has to carry its ResourceUser key.
          reloaded.user should not equal 0L
        case other => fail(s"the user that was just saved must be readable, got $other")
      }

      AuthUser.deleteAllByUsername(username)
      saved.id should not equal 0L
    }
  }
}
