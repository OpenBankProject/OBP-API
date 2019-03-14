package code.api.util

import code.entitlement.Entitlement
import code.users.Users
import com.openbankproject.commons.model.User
import net.liftweb.common.{Box, Failure, Full}
import net.liftweb.json.JsonParser.ParseException
import net.liftweb.json.MappingException

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

case class Consent(subject: String, // An identifier for the user, unique among all OBP-API users and never reused
                   provider: String, // The Issuer Identifier for the Issuer of the response. 
                   name: Option[String],
                   email: Option[String],
                   issued_at: Long, // The time the Consent-ID token was issued, represented in Unix time (integer seconds).
                   expiration_time: Long, // The time the Consent-ID token expires, represented in Unix time (integer seconds).
                   entitlements: List[Role]
                  )
case class Role(role_name: String, 
                bank_id: String
               )

object Consent {
  
  private def chechExpiration(iat: Long, exp: Long): Box[Boolean] = {
    (System.currentTimeMillis / 1000) match {
      case currentTimeInSeconds if currentTimeInSeconds < iat => 
        Failure("The time Consent-ID token was issued is set in the future.")
      case currentTimeInSeconds if currentTimeInSeconds > exp => 
        Failure("Consent-Id is expired.")
      case _ => 
        Full(true)
    }
  } 


  private def hasConsentInternal(consentIdAsJwt: String): Future[Box[User]] = {
    implicit val dateFormats = net.liftweb.json.DefaultFormats
    JwtUtil.getSignedPayloadAsJson(consentIdAsJwt) match {
      case Full(jsonAsString) =>
        try {
          val consent = net.liftweb.json.parse(jsonAsString).extract[Consent]
          chechExpiration(consent.issued_at, consent.expiration_time) match { // Check is it Consent-Id expired
            case (Full(true)) => // OK
              // 1. Get or Create a User
              getOrCreateUser(consent.subject, consent.provider, None, None) map {
                case (Full(user)) =>
                  // 2. Assign entitlements to the User
                  addEntitlements(user, consent)
                case _ =>
                  Failure("Cannot create or get the user based on: " + consentIdAsJwt)
              }
            case failure@Failure(_, _, _) => // Handled errors
              Future(failure)
            case _ => // Unexpected errors
              Future(Failure("Cannot check is Consent-Id expired."))
          }
        } catch {
          case e: ParseException => Future(Failure("ParseException: " + e.getMessage))
          case e: MappingException => Future(Failure("MappingException: " + e.getMessage))
          case e: Exception => Future(Failure("parsing failed: " + e.getMessage))
        }
      case failure@Failure(_, _, _) =>
        Future(failure)
      case _ =>
        Future(Failure("Cannot extract data from: " + consentIdAsJwt))
    }
  }
  
  def hasConsent(consentIdAsJwt: String, calContext: Option[CallContext]): Future[(Box[User], Option[CallContext])] = {
    hasConsentInternal(consentIdAsJwt) map (result => (result, calContext))
  }
  
  def getOrCreateUser(subject: String, provider: String, name: Option[String], email: Option[String]): Future[Box[User]] = {
    Users.users.vend.getOrCreateUserByProviderIdFuture(
      provider = provider,
      idGivenByProvider = subject,
      name = name,
      email = email
    )
  }
  
  def addEntitlements(user: User, consent: Consent): Box[User] = {
    val entitlements: List[Role] = consent.entitlements
    Entitlement.entitlement.vend.getEntitlementsByUserId(user.userId) match {
      case Full(existingEntitlements) =>
        val triedToAdd =
          for {
            entitlement <- entitlements
          } yield {
            ApiRole.availableRoles.exists(_.toString == entitlement.role_name) match { // Check a role name
              case true =>
                val role = ApiRole.valueOf(entitlement.role_name)
                existingEntitlements.exists(_.roleName == entitlement.role_name) match { // Check is a role already added to a user
                  case false =>
                    val result = Entitlement.entitlement.vend.addEntitlement(if (role.requiresBankId) entitlement.bank_id else "", user.userId, entitlement.role_name)
                    (entitlement, result)
                  case true =>
                    (entitlement, Full(existingEntitlements.filter(_.roleName == entitlement.role_name).head))
                }
              case false =>
                (entitlement, Failure("There is no role: " + entitlement))
            }

          }
        val failedToAdd: List[(Role, Box[Entitlement])] = triedToAdd.filter(_._2.isDefined == false)
        failedToAdd match {
          case Nil => Full(user)
          case _ => Failure("Cannot add next entitlements: " + failedToAdd.map(_._1).mkString(", ") + ", please check the names.")
        }
      case _ =>
        Failure("Cannot get entitlements for user id: " + user.userId)
    }
    
  }
}
