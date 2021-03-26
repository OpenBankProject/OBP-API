/**
Open Bank Project - API
Copyright (C) 2011-2019, TESOBE GmbH.

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.

Email: contact@tesobe.com
TESOBE GmbH.
Osloer Strasse 16/17
Berlin 13359, Germany

This product includes software developed at
TESOBE (http://www.tesobe.com/)

 */
package code.api

import java.net.URI

import code.api.util.ErrorMessages._
import code.api.util.{APIUtil, CallContext, JwtUtil}
import code.consumer.Consumers
import code.consumer.Consumers.consumers
import code.loginattempts.LoginAttempt
import code.model.Consumer
import code.util.HydraUtil._
import code.users.Users
import code.util.Helper.MdcLoggable
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.openid.connect.sdk.claims.IDTokenClaimsSet
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.model.User
import net.liftweb.common._
import net.liftweb.http.rest.RestHelper
import net.liftweb.util.Helpers
import org.apache.commons.lang3.StringUtils
import sh.ory.hydra.model.OAuth2TokenIntrospection

import scala.concurrent.Future
import scala.jdk.CollectionConverters.mapAsJavaMapConverter

/**
* This object provides the API calls necessary to third party applications
* so they could authenticate their users.
*/

object OAuth2Login extends RestHelper with MdcLoggable {

  private def getValueOfOAuh2HeaderField(sc: CallContext) = {
    val valueOfAuthReqHeaderField = sc.authReqHeaderField.getOrElse("")
      .replaceAll("Authorization:", "")
      .replaceAll("Bearer", "")
      .trim()
    valueOfAuthReqHeaderField
  }

  /*
    Method for Old Style Endpoints
   */
  def getUser(cc: CallContext): (Box[User], Option[CallContext]) = {
    APIUtil.getPropsAsBoolValue("allow_oauth2_login", true) match {
      case true =>
        val value = getValueOfOAuh2HeaderField(cc)
        if (Google.isIssuer(value)) {
          Google.applyRules(value, cc)
        } else if (Yahoo.isIssuer(value)) {
          Yahoo.applyRules(value, cc)
        } else {
          Hydra.applyRules(value, cc)
        }
      case false =>
        (Failure(Oauth2IsNotAllowed), Some(cc))
    }
  }
  /*
    Method for New Style Endpoints
   */
  def getUserFuture(cc: CallContext): Future[(Box[User], Option[CallContext])] = {
    APIUtil.getPropsAsBoolValue("allow_oauth2_login", true) match {
      case true =>
        val value = getValueOfOAuh2HeaderField(cc)
        if (Google.isIssuer(value)) {
          Google.applyRulesFuture(value, cc)
        } else if (Yahoo.isIssuer(value)) {
          Yahoo.applyRulesFuture(value, cc)
        } else {
          Hydra.applyRulesFuture(value, cc)
        }
      case false =>
        Future((Failure(Oauth2IsNotAllowed), Some(cc)))
    }
  }

  
  object Hydra {
    def validateAccessToken(accessToken: String): Box[JWTClaimsSet] = {
      APIUtil.getPropsValue("oauth2.jwk_set.url") match {
        case Full(url) =>
          val validationList = for (item <- url.toLowerCase().split(",").toList) yield {
            JwtUtil.validateAccessToken(accessToken, item)
          }
          validationList.filter(_.isDefined).size > 0 match {
            case true => validationList.filter(_.isDefined).head
            case false => validationList.head
          }
        case ParamFailure(a, b, c, apiFailure : APIFailure) =>
          ParamFailure(a, b, c, apiFailure : APIFailure)
        case Failure(msg, t, c) =>
          Failure(msg, t, c)
        case _ =>
          Failure(Oauth2ThereIsNoUrlOfJwkSet)
      }
    }
    
    def applyRules(value: String, cc: CallContext): (Box[User], Some[CallContext]) = {
      // In case of Hydra issued access tokens are not self-encoded/self-contained like JWT tokens are.
      // It implies the access token can be revoked at any time.
      val introspectOAuth2Token: OAuth2TokenIntrospection = hydraAdmin.introspectOAuth2Token(value, null);
      var consumer: Box[Consumer] = consumers.vend.getConsumerByConsumerKey(introspectOAuth2Token.getClientId)

      // check access token binding with client certificate
      {
        if(consumer.isEmpty) {
          return (Failure(Oauth2TokenHaveNoConsumer), Some(cc.copy(consumer = Failure(Oauth2TokenHaveNoConsumer))))
        }
        val clientCert = APIUtil.`getPSD2-CERT`(cc.requestHeaders)
        clientCert.filter(StringUtils.isNotBlank).foreach {cert =>
          val foundConsumer = consumer.orNull
          val certInConsumer = foundConsumer.clientCertificate.get
          if(StringUtils.isBlank(certInConsumer)) {
            foundConsumer.clientCertificate.set(cert)
            consumer = Full(foundConsumer.saveMe())
            val clientId = foundConsumer.key.get
            // update hydra client client_certificate
            val oAuth2Client = hydraAdmin.getOAuth2Client(clientId)
            val clientMeta = oAuth2Client.getMetadata.asInstanceOf[java.util.Map[String, AnyRef]]
            if(clientMeta == null) {
              oAuth2Client.setMetadata(Map("client_certificate" -> cert).asJava)
            } else {
              clientMeta.put("client_certificate", cert)
            }
            // hydra update client endpoint have bug, So here delete and create to do update
            hydraAdmin.deleteOAuth2Client(clientId)
            hydraAdmin.createOAuth2Client(oAuth2Client)
          } else if(stringNotEq(certInConsumer, cert)) {
            return (Failure(Oauth2TokenMatchCertificateFail), Some(cc.copy(consumer = Failure(Oauth2TokenMatchCertificateFail))))
          }
        }
      }

      if (introspectOAuth2Token.getActive) { // The access token can be disabled at any time due to fact it is NOT self-encoded/self-contained.
        val user = Users.users.vend.getUserByUserName(introspectOAuth2Token.getSub)
        user match {
          case Full(u) =>
            LoginAttempt.userIsLocked(u.name) match {
              case true => (Failure(UsernameHasBeenLocked), Some(cc.copy(consumer = consumer)))
              case false => (Full(u), Some(cc.copy(consumer = consumer)))
            }
          case _ => (user, Some(cc.copy(consumer = consumer)))
        }
      } else {
        (Failure(Oauth2IJwtCannotBeVerified), Some(cc.copy(consumer = Failure(Oauth2IJwtCannotBeVerified))))
      }
    }
    def applyRulesFuture(value: String, cc: CallContext): Future[(Box[User], Some[CallContext])] = Future {
      applyRules(value, cc)
    }

    /**
     * check whether two string equal, ignore line break type
     * @param str1
     * @param str2
     * @return true if two string are different
     */
    private def stringNotEq(str1: String, str2: String): Boolean =
      str1.trim != str2.trim &&
        str1.trim.replace("\r\n", "\n") != str2.trim.replace("\r\n", "\n")
  }
  
  trait OAuth2Util {
    
    def wellKnownOpenidConfiguration: URI
    
    def urlOfJwkSets: Box[String] = APIUtil.getPropsValue(nameOfProperty = "oauth2.jwk_set.url")

    def checkUrlOfJwkSets(identityProvider: String) = {
      val url: List[String] = APIUtil.getPropsValue(nameOfProperty = "oauth2.jwk_set.url").toList
      val jwksUris: List[String] = url.map(_.toLowerCase()).map(_.split(",").toList).flatten
      val jwksUri = jwksUris.filter(_.contains(identityProvider))
      jwksUri match {
        case x :: _ => Full(x)
        case Nil => Failure(Oauth2CannotMatchIssuerAndJwksUriException)
      }
    }
    
    private def getClaim(name: String, idToken: String): Option[String] = {
      val claim = JwtUtil.getClaim(name = name, jwtToken = idToken)
      claim match {
        case null => None
        case string => Some(string)
      }
    }
    def isIssuer(jwtToken: String, identityProvider: String): Boolean = {
      JwtUtil.getIssuer(jwtToken).map(_.contains(identityProvider)).getOrElse(false)
    }
    def validateIdToken(idToken: String): Box[IDTokenClaimsSet] = {
      urlOfJwkSets match {
        case Full(url) =>
          JwtUtil.validateIdToken(idToken, url)
        case ParamFailure(a, b, c, apiFailure : APIFailure) =>
          ParamFailure(a, b, c, apiFailure : APIFailure)
        case Failure(msg, t, c) =>
          Failure(msg, t, c)
        case _ =>
          Failure(Oauth2ThereIsNoUrlOfJwkSet)
      }
    }
    /** New Style Endpoints
      * This function creates user based on "iss" and "sub" fields
      * It is mapped in next way:
      * iss => ResourceUser.provider_
      * sub => ResourceUser.providerId
      * @param idToken Google's response example:
      *                {
      *                "access_token": "ya29.GluUBg5DflrJciFikW5hqeKEp9r1whWnU5x2JXCm9rKkRMs2WseXX8O5UugFMDsIKuKCZlE7tTm1fMII_YYpvcMX6quyR5DXNHH8Lbx5TrZN__fA92kszHJEVqPc", 
      *                "id_token": "eyJhbGciOiJSUzI1NiIsImtpZCI6IjA4ZDMyNDVjNjJmODZiNjM2MmFmY2JiZmZlMWQwNjk4MjZkZDFkYzEiLCJ0eXAiOiJKV1QifQ.eyJpc3MiOiJodHRwczovL2FjY291bnRzLmdvb2dsZS5jb20iLCJhenAiOiI0MDc0MDg3MTgxOTIuYXBwcy5nb29nbGV1c2VyY29udGVudC5jb20iLCJhdWQiOiI0MDc0MDg3MTgxOTIuYXBwcy5nb29nbGV1c2VyY29udGVudC5jb20iLCJzdWIiOiIxMTM5NjY4NTQyNDU3ODA4OTI5NTkiLCJlbWFpbCI6Im1hcmtvLm1pbGljLnNyYmlqYUBnbWFpbC5jb20iLCJlbWFpbF92ZXJpZmllZCI6dHJ1ZSwiYXRfaGFzaCI6Im5HS1JUb0tOblZBMjhINk1od1hCeHciLCJuYW1lIjoiTWFya28gTWlsacSHIiwicGljdHVyZSI6Imh0dHBzOi8vbGg1Lmdvb2dsZXVzZXJjb250ZW50LmNvbS8tWGQ0NGhuSjZURG8vQUFBQUFBQUFBQUkvQUFBQUFBQUFBQUEvQUt4cndjYWR3emhtNE40dFdrNUU4QXZ4aS1aSzZrczRxZy9zOTYtYy9waG90by5qcGciLCJnaXZlbl9uYW1lIjoiTWFya28iLCJmYW1pbHlfbmFtZSI6Ik1pbGnEhyIsImxvY2FsZSI6ImVuIiwiaWF0IjoxNTQ3NzA1NjkxLCJleHAiOjE1NDc3MDkyOTF9.iUxhF_SU2vi76zPuRqAKJvFOzpb_EeP3lc5u9FO9o5xoXzVq3QooXexTfK2f1YAcWEy9LSftA34PB0QTuCZpkQChZVM359n3a3hplf6oWWkBXZN2_IG10NwEH4g0VVBCsjWBDMp6lvepN_Zn15x8opUB7272m4-smAou_WmUPTeivXRF8yPcp4J55DigcY31YP59dMQr2X-6Rr1vCRnJ6niqqJ1UDldfsgt4L7dXmUCnkDdXHwEQAZwbKbR4dUoEha3QeylCiBErmLdpIyqfKECphC6piGXZB-rRRqLz41WNfuF-3fswQvGmIkzTJDR7lQaletMp7ivsfVw8N5jFxg", 
      *                "expires_in": 3600, 
      *                "token_type": "Bearer", 
      *                "scope": "https://www.googleapis.com/auth/userinfo.profile https://www.googleapis.com/auth/userinfo.email", 
      *                "refresh_token": "1/HkTtUahtUTdG7D6urpPNz6g-_qufF-Y1YppcBf0v3Cs"
      *                }
      * @return an existing or a new user
      */
    def getOrCreateResourceUserFuture(idToken: String): Future[Box[User]] = {
      val subject = JwtUtil.getSubject(idToken).getOrElse("")
      val issuer = JwtUtil.getIssuer(idToken).getOrElse("")
      Users.users.vend.getOrCreateUserByProviderIdFuture(
        provider = issuer, 
        idGivenByProvider = subject,
        consentId = None, 
        name = getClaim(name = "given_name", idToken = idToken).orElse(Some(subject)),
        email = getClaim(name = "email", idToken = idToken)
      ).map(_._1)
    }
    /** Old Style Endpoints
      * This function creates user based on "iss" and "sub" fields
      * It is mapped in next way:
      * iss => ResourceUser.provider_
      * sub => ResourceUser.providerId
      * @param idToken Google's response example:
      *                {
      *                "access_token": "ya29.GluUBg5DflrJciFikW5hqeKEp9r1whWnU5x2JXCm9rKkRMs2WseXX8O5UugFMDsIKuKCZlE7tTm1fMII_YYpvcMX6quyR5DXNHH8Lbx5TrZN__fA92kszHJEVqPc", 
      *                "id_token": "eyJhbGciOiJSUzI1NiIsImtpZCI6IjA4ZDMyNDVjNjJmODZiNjM2MmFmY2JiZmZlMWQwNjk4MjZkZDFkYzEiLCJ0eXAiOiJKV1QifQ.eyJpc3MiOiJodHRwczovL2FjY291bnRzLmdvb2dsZS5jb20iLCJhenAiOiI0MDc0MDg3MTgxOTIuYXBwcy5nb29nbGV1c2VyY29udGVudC5jb20iLCJhdWQiOiI0MDc0MDg3MTgxOTIuYXBwcy5nb29nbGV1c2VyY29udGVudC5jb20iLCJzdWIiOiIxMTM5NjY4NTQyNDU3ODA4OTI5NTkiLCJlbWFpbCI6Im1hcmtvLm1pbGljLnNyYmlqYUBnbWFpbC5jb20iLCJlbWFpbF92ZXJpZmllZCI6dHJ1ZSwiYXRfaGFzaCI6Im5HS1JUb0tOblZBMjhINk1od1hCeHciLCJuYW1lIjoiTWFya28gTWlsacSHIiwicGljdHVyZSI6Imh0dHBzOi8vbGg1Lmdvb2dsZXVzZXJjb250ZW50LmNvbS8tWGQ0NGhuSjZURG8vQUFBQUFBQUFBQUkvQUFBQUFBQUFBQUEvQUt4cndjYWR3emhtNE40dFdrNUU4QXZ4aS1aSzZrczRxZy9zOTYtYy9waG90by5qcGciLCJnaXZlbl9uYW1lIjoiTWFya28iLCJmYW1pbHlfbmFtZSI6Ik1pbGnEhyIsImxvY2FsZSI6ImVuIiwiaWF0IjoxNTQ3NzA1NjkxLCJleHAiOjE1NDc3MDkyOTF9.iUxhF_SU2vi76zPuRqAKJvFOzpb_EeP3lc5u9FO9o5xoXzVq3QooXexTfK2f1YAcWEy9LSftA34PB0QTuCZpkQChZVM359n3a3hplf6oWWkBXZN2_IG10NwEH4g0VVBCsjWBDMp6lvepN_Zn15x8opUB7272m4-smAou_WmUPTeivXRF8yPcp4J55DigcY31YP59dMQr2X-6Rr1vCRnJ6niqqJ1UDldfsgt4L7dXmUCnkDdXHwEQAZwbKbR4dUoEha3QeylCiBErmLdpIyqfKECphC6piGXZB-rRRqLz41WNfuF-3fswQvGmIkzTJDR7lQaletMp7ivsfVw8N5jFxg", 
      *                "expires_in": 3600, 
      *                "token_type": "Bearer", 
      *                "scope": "https://www.googleapis.com/auth/userinfo.profile https://www.googleapis.com/auth/userinfo.email", 
      *                "refresh_token": "1/HkTtUahtUTdG7D6urpPNz6g-_qufF-Y1YppcBf0v3Cs"
      *                }
      * @return an existing or a new user
      */
    def getOrCreateResourceUser(idToken: String): Box[User] = {
      val subject = JwtUtil.getSubject(idToken).getOrElse("")
      val issuer = JwtUtil.getIssuer(idToken).getOrElse("")
      Users.users.vend.getUserByProviderId(provider = issuer, idGivenByProvider = subject).or { // Find a user
        Users.users.vend.createResourceUser( // Otherwise create a new one
          provider = issuer,
          providerId = Some(subject),
          None,
          name = getClaim(name = "given_name", idToken = idToken).orElse(Some(subject)),
          email = getClaim(name = "email", idToken = idToken),
          userId = None
        )
      }
    }
    /** 
      * This function creates a consumer based on "azp", "sub", "iss", "name" and "email" fields
      * Please note that a user must be created before consumer.
      * Unique criteria to decide do we create or get a consumer is pair o values: < sub : azp > i.e.
      * We cannot find consumer by sub and azp => Create
      * We can find consumer by sub and azp => Get
      * @param idToken Google's response example:
      *                {
      *                "access_token": "ya29.GluUBg5DflrJciFikW5hqeKEp9r1whWnU5x2JXCm9rKkRMs2WseXX8O5UugFMDsIKuKCZlE7tTm1fMII_YYpvcMX6quyR5DXNHH8Lbx5TrZN__fA92kszHJEVqPc", 
      *                "id_token": "eyJhbGciOiJSUzI1NiIsImtpZCI6IjA4ZDMyNDVjNjJmODZiNjM2MmFmY2JiZmZlMWQwNjk4MjZkZDFkYzEiLCJ0eXAiOiJKV1QifQ.eyJpc3MiOiJodHRwczovL2FjY291bnRzLmdvb2dsZS5jb20iLCJhenAiOiI0MDc0MDg3MTgxOTIuYXBwcy5nb29nbGV1c2VyY29udGVudC5jb20iLCJhdWQiOiI0MDc0MDg3MTgxOTIuYXBwcy5nb29nbGV1c2VyY29udGVudC5jb20iLCJzdWIiOiIxMTM5NjY4NTQyNDU3ODA4OTI5NTkiLCJlbWFpbCI6Im1hcmtvLm1pbGljLnNyYmlqYUBnbWFpbC5jb20iLCJlbWFpbF92ZXJpZmllZCI6dHJ1ZSwiYXRfaGFzaCI6Im5HS1JUb0tOblZBMjhINk1od1hCeHciLCJuYW1lIjoiTWFya28gTWlsacSHIiwicGljdHVyZSI6Imh0dHBzOi8vbGg1Lmdvb2dsZXVzZXJjb250ZW50LmNvbS8tWGQ0NGhuSjZURG8vQUFBQUFBQUFBQUkvQUFBQUFBQUFBQUEvQUt4cndjYWR3emhtNE40dFdrNUU4QXZ4aS1aSzZrczRxZy9zOTYtYy9waG90by5qcGciLCJnaXZlbl9uYW1lIjoiTWFya28iLCJmYW1pbHlfbmFtZSI6Ik1pbGnEhyIsImxvY2FsZSI6ImVuIiwiaWF0IjoxNTQ3NzA1NjkxLCJleHAiOjE1NDc3MDkyOTF9.iUxhF_SU2vi76zPuRqAKJvFOzpb_EeP3lc5u9FO9o5xoXzVq3QooXexTfK2f1YAcWEy9LSftA34PB0QTuCZpkQChZVM359n3a3hplf6oWWkBXZN2_IG10NwEH4g0VVBCsjWBDMp6lvepN_Zn15x8opUB7272m4-smAou_WmUPTeivXRF8yPcp4J55DigcY31YP59dMQr2X-6Rr1vCRnJ6niqqJ1UDldfsgt4L7dXmUCnkDdXHwEQAZwbKbR4dUoEha3QeylCiBErmLdpIyqfKECphC6piGXZB-rRRqLz41WNfuF-3fswQvGmIkzTJDR7lQaletMp7ivsfVw8N5jFxg", 
      *                "expires_in": 3600, 
      *                "token_type": "Bearer", 
      *                "scope": "https://www.googleapis.com/auth/userinfo.profile https://www.googleapis.com/auth/userinfo.email", 
      *                "refresh_token": "1/HkTtUahtUTdG7D6urpPNz6g-_qufF-Y1YppcBf0v3Cs"
      *                }
      * @return an existing or a new consumer
      */
    def getOrCreateConsumer(idToken: String, userId: Box[String]): Box[Consumer] = {
      val aud = Some(JwtUtil.getAudience(idToken).mkString(","))
      val azp = getClaim(name = "azp", idToken = idToken)
      val iss = getClaim(name = "iss", idToken = idToken)
      val sub = getClaim(name = "sub", idToken = idToken)
      val email = getClaim(name = "email", idToken = idToken)
      val name = getClaim(name = "name", idToken = idToken)
      Consumers.consumers.vend.getOrCreateConsumer(
        consumerId = None,
        key = Some(Helpers.randomString(40).toLowerCase),
        secret = Some(Helpers.randomString(40).toLowerCase),
        aud = aud,
        azp = azp,
        iss = iss,
        sub = sub,
        Some(true),
        name = name,
        appType = None,
        description = Some(OpenIdConnect.openIdConnect),
        developerEmail = email,
        redirectURL = None,
        createdByUserId = userId.toOption
      )
    }

    def applyRules(value: String, cc: CallContext): (Box[User], Some[CallContext]) = {
      validateIdToken(value) match {
        case Full(_) =>
          val user = IdentityProviderCommon.getOrCreateResourceUser(value)
          val consumer = IdentityProviderCommon.getOrCreateConsumer(value, user.map(_.userId))
          LoginAttempt.userIsLocked(user.map(_.name).getOrElse("")) match {
            case true => ((Failure(UsernameHasBeenLocked), Some(cc.copy(consumer = consumer))))
            case false => (user, Some(cc.copy(consumer = consumer)))
          }
        case ParamFailure(a, b, c, apiFailure : APIFailure) =>
          (ParamFailure(a, b, c, apiFailure : APIFailure), Some(cc))
        case Failure(msg, t, c) =>
          (Failure(msg, t, c), Some(cc))
        case _ =>
          (Failure(Oauth2IJwtCannotBeVerified), Some(cc))
      }
    }
    def applyRulesFuture(value: String, cc: CallContext): Future[(Box[User], Some[CallContext])] = {
      validateIdToken(value) match {
        case Full(_) =>
          for {
            user <-  IdentityProviderCommon.getOrCreateResourceUserFuture(value)
            consumer <-  Future{IdentityProviderCommon.getOrCreateConsumer(value, user.map(_.userId))}
          } yield {
            LoginAttempt.userIsLocked(user.map(_.name).getOrElse("")) match {
              case true => ((Failure(UsernameHasBeenLocked), Some(cc.copy(consumer = consumer))))
              case false => (user, Some(cc.copy(consumer = consumer)))
            }
          }
        case ParamFailure(a, b, c, apiFailure : APIFailure) =>
          Future((ParamFailure(a, b, c, apiFailure : APIFailure), Some(cc)))
        case Failure(msg, t, c) =>
          Future((Failure(msg, t, c), Some(cc)))
        case _ =>
          Future((Failure(Oauth2IJwtCannotBeVerified), Some(cc)))
      }
    }
  }
  
  object IdentityProviderCommon extends OAuth2Util {
    override def wellKnownOpenidConfiguration: URI = new URI("")
    override def urlOfJwkSets: Box[String] = checkUrlOfJwkSets(identityProvider = "common")
  }

  object Google extends OAuth2Util {
    val google = "google"
    /**
      * OpenID Connect Discovery.
      * Google exposes OpenID Connect discovery documents ( https://YOUR_DOMAIN/.well-known/openid-configuration ). 
      * These can be used to automatically configure applications.
      */
    override def wellKnownOpenidConfiguration: URI = new URI("https://accounts.google.com/.well-known/openid-configuration")
    override def urlOfJwkSets: Box[String] = checkUrlOfJwkSets(identityProvider = google)
    def isIssuer(jwt: String): Boolean = isIssuer(jwtToken=jwt, identityProvider = google)
  }
  
  object Yahoo extends OAuth2Util {
    val yahoo = "yahoo"
    /**
      * OpenID Connect Discovery.
      * Yahoo exposes OpenID Connect discovery documents ( https://YOUR_DOMAIN/.well-known/openid-configuration ). 
      * These can be used to automatically configure applications.
      */
    override def wellKnownOpenidConfiguration: URI = new URI("https://login.yahoo.com/.well-known/openid-configuration")
    override def urlOfJwkSets: Box[String] = checkUrlOfJwkSets(identityProvider = yahoo)
    def isIssuer(jwt: String): Boolean = isIssuer(jwtToken=jwt, identityProvider = yahoo)
  }

}