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

import code.api.util.ErrorMessages._
import code.api.util._
import code.consumer.Consumers
import code.consumer.Consumers.consumers
import code.loginattempts.LoginAttempt
import code.model.{AppType, Consumer}
import code.scope.Scope
import code.users.Users
import code.util.Helper.MdcLoggable
import code.util.HydraUtil
import code.util.HydraUtil._
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.openid.connect.sdk.claims.IDTokenClaimsSet
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.model.User
import net.liftweb.common.Box.tryo
import net.liftweb.common._
import net.liftweb.http.rest.RestHelper
import net.liftweb.util.Helpers
import org.apache.commons.lang3.StringUtils
import sh.ory.hydra.model.OAuth2TokenIntrospection

import java.net.URI
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
          Google.applyIdTokenRules(value, cc)
        } else if (Yahoo.isIssuer(value)) {
          Yahoo.applyIdTokenRules(value, cc)
        } else if (Azure.isIssuer(value)) {
          Azure.applyIdTokenRules(value, cc)
        } else if (Keycloak.isIssuer(value)) {
          Keycloak.applyRules(value, cc)
        } else if (UnknownProvider.isIssuer(value)) {
          UnknownProvider.applyRules(value, cc)
        } else if (HydraUtil.integrateWithHydra) {
          Hydra.applyRules(value, cc)
        } else {
          (Failure(Oauth2IsNotRecognized), Some(cc))
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
          Google.applyIdTokenRulesFuture(value, cc)
        } else if (Yahoo.isIssuer(value)) {
          Yahoo.applyIdTokenRulesFuture(value, cc)
        } else if (Azure.isIssuer(value)) {
          Azure.applyIdTokenRulesFuture(value, cc)
        } else if (Keycloak.isIssuer(value)) {
          Keycloak.applyRulesFuture(value, cc)
        } else if (UnknownProvider.isIssuer(value)) {
          UnknownProvider.applyRulesFuture(value, cc)
        } else if (HydraUtil.integrateWithHydra) {
          Hydra.applyRulesFuture(value, cc)
        } else {
          Future(Failure(Oauth2IsNotRecognized), Some(cc))
        }
      case false =>
        Future((Failure(Oauth2IsNotAllowed), Some(cc)))
    }
  }


  object Hydra extends OAuth2Util {
    override def wellKnownOpenidConfiguration: URI = new URI(hydraPublicUrl)
    override def urlOfJwkSets: Box[String] = checkUrlOfJwkSets(identityProvider = hydraPublicUrl)

    override def applyAccessTokenRules(value: String, cc: CallContext): (Box[User], Some[CallContext]) = {
      // In case of Hydra issued access tokens are not self-encoded/self-contained like JWT tokens are.
      // It implies the access token can be revoked at any time.
      val introspectOAuth2Token: OAuth2TokenIntrospection = hydraAdmin.introspectOAuth2Token(value, null)
      val hydraClient = hydraAdmin.getOAuth2Client(introspectOAuth2Token.getClientId())
      var consumer: Box[Consumer] = consumers.vend.getConsumerByConsumerKey(introspectOAuth2Token.getClientId)
      logger.debug("introspectOAuth2Token.getIss: " + introspectOAuth2Token.getIss)
      logger.debug("introspectOAuth2Token.getActive: " + introspectOAuth2Token.getActive)
      logger.debug("introspectOAuth2Token.getClientId: " + introspectOAuth2Token.getClientId)
      logger.debug("introspectOAuth2Token.getAud: " + introspectOAuth2Token.getAud)
      logger.debug("introspectOAuth2Token.getUsername: " + introspectOAuth2Token.getUsername)
      logger.debug("introspectOAuth2Token.getExp: " + introspectOAuth2Token.getExp)
      logger.debug("introspectOAuth2Token.getNbf: " + introspectOAuth2Token.getNbf)
      // The access token can be disabled at any time due to fact it is NOT self-encoded/self-contained.
      if (!introspectOAuth2Token.getActive) {
        return (Failure(Oauth2IJwtCannotBeVerified), Some(cc.copy(consumer = Failure(Oauth2IJwtCannotBeVerified))))
      }
      if (!hydraSupportedTokenEndpointAuthMethods.contains(hydraClient.getTokenEndpointAuthMethod())) {
        logger.debug("hydraClient.getTokenEndpointAuthMethod(): " + hydraClient.getTokenEndpointAuthMethod().toLowerCase())
        val errorMessage = Oauth2TokenEndpointAuthMethodForbidden + hydraClient.getTokenEndpointAuthMethod()
        return (Failure(errorMessage), Some(cc.copy(consumer = Failure(errorMessage))))
      }

      // check access token binding with client certificate
      {
        if(consumer.isEmpty) {
          return (Failure(Oauth2TokenHaveNoConsumer), Some(cc.copy(consumer = Failure(Oauth2TokenHaveNoConsumer))))
        }
        val clientCert: Option[String] = APIUtil.`getPSD2-CERT`(cc.requestHeaders)
        clientCert.filter(StringUtils.isNotBlank).foreach {cert =>
          val foundConsumer = consumer.orNull
          val certInConsumer = foundConsumer.clientCertificate.get
          if(StringUtils.isBlank(certInConsumer)) {
            // In case that the certificate of a consumer is not populated in a database
            // we use the value at PSD2-CERT header in order to populate it for the first time.
            // Please note that every next call MUST match that value.
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
          } else if(!CertificateUtil.comparePemX509Certificates(certInConsumer, cert)) {
            // Cannot mat.ch the value from PSD2-CERT header and the database value Consumer.clientCertificate
            logger.debug(s"Cert in Consumer with the name ***${foundConsumer.name}*** : " + certInConsumer)
            logger.debug("Cert in Request: " + cert)
            logger.debug(s"Token: $value")
            logger.debug(s"Client ID: ${introspectOAuth2Token.getClientId}")
            return (Failure(Oauth2TokenMatchCertificateFail), Some(cc.copy(consumer = Failure(Oauth2TokenMatchCertificateFail))))
          } else {
            // Certificate is matched. Just make some debug logging.
            logger.debug("The token is linked with a proper client certificate.")
            logger.debug(s"Token: $value")
            logger.debug(s"Client Key: ${introspectOAuth2Token.getClientId}")
          }
        }
      }

      // In case a user is created via OpenID Connect flow implies provider = hydraPublicUrl
      // In case a user is created via GUI of OBP-API implies provider = Constant.localIdentityProvider
      val user = Users.users.vend.getUserByProviderAndUsername(introspectOAuth2Token.getIss, introspectOAuth2Token.getSub).or(
        Users.users.vend.getUserByProviderAndUsername(Constant.localIdentityProvider, introspectOAuth2Token.getSub)
      )
      user match {
        case Full(u) =>
          LoginAttempt.userIsLocked(u.provider, u.name) match {
            case true => (Failure(UsernameHasBeenLocked), Some(cc.copy(consumer = consumer)))
            case false => (Full(u), Some(cc.copy(consumer = consumer)))
          }
        case _ => (user, Some(cc.copy(consumer = consumer)))
      }
    }

    def applyRules(token: String, cc: CallContext): (Box[User], Some[CallContext]) = {
      isIssuer(jwtToken=token, identityProvider = hydraPublicUrl) match {
        case true => super.applyIdTokenRules(token, cc)
        case false => applyAccessTokenRules(token, cc)
      }
    }

    def applyRulesFuture(value: String, cc: CallContext): Future[(Box[User], Some[CallContext])] = Future {
      applyRules(value, cc)
    }

  }

  trait OAuth2Util {

    def wellKnownOpenidConfiguration: URI

    def urlOfJwkSets: Box[String] = Constant.oauth2JwkSetUrl

    def checkUrlOfJwkSets(identityProvider: String) = {
      val url: List[String] = Constant.oauth2JwkSetUrl.toList
      val jwksUris: List[String] = url.map(_.toLowerCase()).map(_.split(",").toList).flatten
      val jwksUri = jwksUris.filter(_.contains(identityProvider))
      jwksUri match {
        case x :: _ => Full(x)
        case Nil => Failure(Oauth2CannotMatchIssuerAndJwksUriException)
      }
    }

    def getClaim(name: String, idToken: String): Option[String] = {
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
    def validateAccessToken(accessToken: String): Box[JWTClaimsSet] = {
      urlOfJwkSets match {
        case Full(url) =>
          JwtUtil.validateAccessToken(accessToken, url)
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
      val uniqueIdGivenByProvider = JwtUtil.getSubject(idToken).getOrElse("")
      val provider = resolveProvider(idToken)
      Users.users.vend.getOrCreateUserByProviderIdFuture(
        provider = provider,
        idGivenByProvider = uniqueIdGivenByProvider,
        consentId = None,
        name = getClaim(name = "given_name", idToken = idToken).orElse(Some(uniqueIdGivenByProvider)),
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
      val uniqueIdGivenByProvider = JwtUtil.getSubject(idToken).getOrElse("")
      val provider = resolveProvider(idToken)
      Users.users.vend.getUserByProviderId(provider = provider, idGivenByProvider = uniqueIdGivenByProvider).or { // Find a user
        Users.users.vend.createResourceUser( // Otherwise create a new one
          provider = provider,
          providerId = Some(uniqueIdGivenByProvider),
          None,
          name = getClaim(name = "given_name", idToken = idToken).orElse(Some(uniqueIdGivenByProvider)),
          email = getClaim(name = "email", idToken = idToken),
          userId = None,
          createdByUserInvitationId = None,
          company = None,
          lastMarketingAgreementSignedDate = None
        )
      }
    }

    def resolveProvider(idToken: String) = {
      HydraUtil.integrateWithHydra && isIssuer(jwtToken = idToken, identityProvider = hydraPublicUrl) match {
        case true if HydraUtil.hydraUsesObpUserCredentials => // Case that source of the truth of Hydra user management is the OBP-API mapper DB
          // In case that ORY Hydra login url is "hostname/user_mgt/login" we MUST override hydraPublicUrl as provider
          // in order to avoid creation of a new user
          Constant.localIdentityProvider
        case _ => // All other cases implies a new user creation
          // TODO raise exception in case of else case
          JwtUtil.getIssuer(idToken).getOrElse("")
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
    def getOrCreateConsumer(idToken: String, userId: Box[String], description: Option[String]): Box[Consumer] = {
      val aud = Some(JwtUtil.getAudience(idToken).mkString(","))
      val azp = getClaim(name = "azp", idToken = idToken)
      val iss = getClaim(name = "iss", idToken = idToken)
      val sub = getClaim(name = "sub", idToken = idToken)
      val email = getClaim(name = "email", idToken = idToken)
      val name = getClaim(name = "name", idToken = idToken).orElse(description)
      val consumerId = if(APIUtil.checkIfStringIsUUID(azp.getOrElse(""))) azp else Some(s"{$azp}_${APIUtil.generateUUID()}")
      Consumers.consumers.vend.getOrCreateConsumer(
        consumerId = consumerId, // Use azp as consumer id if it is uuid value
        key = Some(Helpers.randomString(40).toLowerCase),
        secret = Some(Helpers.randomString(40).toLowerCase),
        aud = aud,
        azp = azp,
        iss = iss,
        sub = sub,
        Some(true),
        name = name,
        appType = Some(AppType.Confidential),
        description = description,
        developerEmail = email,
        redirectURL = None,
        createdByUserId = userId.toOption
      )

    }

    def applyIdTokenRules(token: String, cc: CallContext): (Box[User], Some[CallContext]) = {
      validateIdToken(token) match {
        case Full(_) =>
          val user = getOrCreateResourceUser(token)
          val consumer = getOrCreateConsumer(token, user.map(_.userId), Some(OpenIdConnect.openIdConnect))
          LoginAttempt.userIsLocked(user.map(_.provider).getOrElse(""), user.map(_.name).getOrElse("")) match {
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
    def applyIdTokenRulesFuture(value: String, cc: CallContext): Future[(Box[User], Some[CallContext])] = Future {
      applyIdTokenRules(value, cc)
    }

    def applyAccessTokenRules(token: String, cc: CallContext): (Box[User], Some[CallContext]) = {
      validateAccessToken(token) match {
        case Full(_) =>
          val user = getOrCreateResourceUser(token)
          val consumer: Box[Consumer] = getOrCreateConsumer(token, user.map(_.userId), Some("OAuth 2.0"))
          consumer match {
            case Full(_) =>
              LoginAttempt.userIsLocked(user.map(_.provider).getOrElse(""), user.map(_.name).getOrElse("")) match {
                case true => ((Failure(UsernameHasBeenLocked), Some(cc.copy(consumer = consumer))))
                case false => (user, Some(cc.copy(consumer = consumer)))
              }
            case ParamFailure(msg, exception, chain, apiFailure: APIFailure) =>
              logger.debug(s"ParamFailure - message: $msg, param: $apiFailure, exception: ${exception.map(_.getMessage).openOr("none")}, chain: ${chain.map(_.msg).openOr("none")}")
              (ParamFailure(msg, exception, chain, apiFailure: APIFailure), Some(cc))
            case Failure(msg, exception, c) =>
              logger.error(s"Failure - message: $msg, exception: ${exception.map(_.getMessage).openOr("none")}")
              (Failure(msg, exception, c), Some(cc))
            case _ =>
              (Failure(CreateConsumerError), Some(cc))
          }
        case ParamFailure(a, b, c, apiFailure: APIFailure) =>
          (ParamFailure(a, b, c, apiFailure: APIFailure), Some(cc))
        case Failure(msg, t, c) =>
          (Failure(msg, t, c), Some(cc))
        case _ =>
          (Failure(Oauth2IJwtCannotBeVerified), Some(cc))
      }
    }
    def applyAccessTokenRulesFuture(value: String, cc: CallContext): Future[(Box[User], Some[CallContext])] = Future {
      applyAccessTokenRules(value, cc)
    }
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

  object Azure extends OAuth2Util {
    val microsoft = "microsoft"
    /**
      * OpenID Connect Discovery.
      * Yahoo exposes OpenID Connect discovery documents ( https://YOUR_DOMAIN/.well-known/openid-configuration ).
      * These can be used to automatically configure applications.
      */
    override def wellKnownOpenidConfiguration: URI = new URI("https://login.microsoftonline.com/common/v2.0/.well-known/openid-configuration")
    override def urlOfJwkSets: Box[String] = checkUrlOfJwkSets(identityProvider = microsoft)
    def isIssuer(jwt: String): Boolean = isIssuer(jwtToken=jwt, identityProvider = microsoft)
  }

  object UnknownProvider extends OAuth2Util {
     /**
      * OpenID Connect Discovery.
      * Yahoo exposes OpenID Connect discovery documents ( https://YOUR_DOMAIN/.well-known/openid-configuration ).
      * These can be used to automatically configure applications.
      */
    override def wellKnownOpenidConfiguration: URI = new URI("")

    def isIssuer(jwt: String): Boolean = {
      val url: List[String] = Constant.oauth2JwkSetUrl.toList
      val jwksUris: List[String] = url.map(_.toLowerCase()).map(_.split(",").toList).flatten
      jwksUris.exists( url => JwtUtil.validateAccessToken(jwt, url).isDefined)
    }
    def applyRules(token: String, cc: CallContext): (Box[User], Some[CallContext]) = {
      super.applyAccessTokenRules(token, cc)
    }

    def applyRulesFuture(value: String, cc: CallContext): Future[(Box[User], Some[CallContext])] = Future {
      applyRules(value, cc)
    }
  }

  object Keycloak extends OAuth2Util {
    val keycloakHost = APIUtil.getPropsValue(nameOfProperty = "oauth2.keycloak.host", "http://localhost:7070")
    /**
      * OpenID Connect Discovery.
      * Yahoo exposes OpenID Connect discovery documents ( https://YOUR_DOMAIN/.well-known/openid-configuration ).
      * These can be used to automatically configure applications.
      */
    override def wellKnownOpenidConfiguration: URI =
      new URI(
        APIUtil.getPropsValue(nameOfProperty = "oauth2.keycloak.well_known", "http://localhost:7070/realms/master/.well-known/openid-configuration")
      )
    override def urlOfJwkSets: Box[String] = checkUrlOfJwkSets(identityProvider = keycloakHost)
    def isIssuer(jwt: String): Boolean = isIssuer(jwtToken=jwt, identityProvider = keycloakHost)

    def applyRules(token: String, cc: CallContext): (Box[User], Some[CallContext]) = {
      JwtUtil.getClaim("typ", token) match {
        case "ID" => super.applyIdTokenRules(token, cc) // Authentication
        case "Bearer" => // Authorization
          val result = super.applyAccessTokenRules(token, cc)
          result._2.flatMap(_.consumer.map(_.id.get)) match {
            case Some(consumerPrimaryKey) =>
              addScopesToConsumer(token, consumerPrimaryKey)
            case None => // Do nothing
          }
          result
        case "" => super.applyAccessTokenRules(token, cc)
      }
    }

    private def addScopesToConsumer(token: String,  consumerPrimaryKey: Long): Unit = {
      val sourceOfTruth = APIUtil.getPropsAsBoolValue(nameOfProperty = "oauth2.keycloak.source_of_truth", defaultValue = false)
      // Consumers allowed to use the source of truth feature
      val resourceAccessName = APIUtil.getPropsValue(nameOfProperty = "oauth2.keycloak.resource_access_key_name_to_trust", "open-bank-project")
      val consumerId = getClaim(name = "azp", idToken = token).getOrElse("")
      if(sourceOfTruth) {
        logger.debug("Extracting roles from Access Token")
        import net.liftweb.json._
        val jsonString = JwtUtil.getSignedPayloadAsJson(token)
        val json = parse(jsonString.getOrElse(""))
        val openBankRoles: List[String] =
        // Sync Keycloak's roles
          (json \ "resource_access" \ resourceAccessName \ "roles").extract[List[String]]
            .filter(role => tryo(ApiRole.valueOf(role)).isDefined) // Keep only the roles OBP-API can recognise
        val scopes = Scope.scope.vend.getScopesByConsumerId(consumerPrimaryKey.toString).getOrElse(Nil)
        val databaseState = scopes.map(_.roleName)
        // Already exist at DB
        val existingRoles = openBankRoles.intersect(databaseState)
        // Roles to add into DB
        val rolesToAdd = openBankRoles.toSet diff databaseState.toSet
        rolesToAdd.foreach(roleName => Scope.scope.vend.addScope("", consumerPrimaryKey.toString, roleName))
        // Roles to delete from DB
        val rolesToDelete = databaseState.toSet diff openBankRoles.toSet
        rolesToDelete.foreach( roleName =>
          Scope.scope.vend.deleteScope(scopes.find(s => s.roleName == roleName || s.consumerId == consumerId))
        )
        logger.debug(s"Consumer ID: $consumerId # Existing roles: ${existingRoles.mkString(",")} # Added roles: ${rolesToAdd.mkString(",")} # Deleted roles: ${rolesToDelete.mkString(",")}")
      } else {
        logger.debug(s"Adding scopes omitted due to oauth2.keycloak.source_of_truth = $sourceOfTruth # Consumer ID: $consumerId")
      }
    }

    def applyRulesFuture(value: String, cc: CallContext): Future[(Box[User], Some[CallContext])] = Future {
      applyRules(value, cc)
    }
  }

}