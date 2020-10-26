package code.util

import code.api.util.APIUtil
import code.model.Consumer
import code.model.Consumer.redirectURLRegex
import code.model.dataAccess.AuthUser
import org.apache.commons.lang3.StringUtils
import sh.ory.hydra.{ApiClient, Configuration}
import sh.ory.hydra.api.{AdminApi, PublicApi}
import sh.ory.hydra.model.OAuth2Client

import scala.collection.immutable.List
import scala.jdk.CollectionConverters.{mapAsJavaMapConverter, seqAsJavaListConverter}

object HydraUtil {

  val loginWithHydra = APIUtil.getPropsAsBoolValue("login_with_hydra", false)

  val mirrorConsumerInHydra = APIUtil.getPropsAsBoolValue("mirror_consumer_in_hydra", false)

  lazy val hydraPublicUrl = APIUtil.getPropsValue("hydra_public_url")
    .openOrThrowException("If props login_with_hydra is true, hydra_public_url value should not be blank")
    .replaceFirst("/$", "")

  lazy val hydraAdminUrl = APIUtil.getPropsValue("hydra_admin_url")
    .openOrThrowException("If props login_with_hydra is true, hydra_admin_url value should not be blank")
    .replaceFirst("/$", "")

  lazy val hydraConsents = APIUtil.getPropsValue("hydra_consents")
    .openOrThrowException("If props login_with_hydra is true, hydra_client_scope value should not be blank")
    .trim.split("""\s*,\s*""").toList

  lazy val hydraAdmin = {
    val hydraAdminUrl = APIUtil.getPropsValue("hydra_admin_url")
      .openOrThrowException("If props login_with_hydra is true, hydra_admin_url value should not be blank")
    val defaultClient = Configuration.getDefaultApiClient
    defaultClient.setBasePath(hydraAdminUrl)
    new AdminApi(defaultClient)
  }

  lazy val hydraPublic = {
    val hydraPublicUrl = APIUtil.getPropsValue("hydra_public_url")
      .openOrThrowException("If props login_with_hydra is true, hydra_public_url value should not be blank")
    val apiClient = new ApiClient
    apiClient.setBasePath(hydraPublicUrl)
    new PublicApi(apiClient)
  }


  /**
   * create Hydra client, if redirectURL is not valid url, return None
   *
   * @param consumer
   * @return created Hydra client or None
   */
  def createHydraClient(consumer: Consumer): Option[OAuth2Client] = {
    val redirectUrl = consumer.redirectURL.get
    if (StringUtils.isBlank(redirectUrl) || redirectURLRegex.findFirstIn(redirectUrl).isEmpty) {
      return None
    }
    val oAuth2Client = new OAuth2Client()
    oAuth2Client.setClientId(consumer.key.get)
    oAuth2Client.setClientSecret(consumer.secret.get)
    val allConsents = "openid" :: "offline" :: hydraConsents
    oAuth2Client.setScope(allConsents.mkString(" "))

    oAuth2Client.setGrantTypes(("authorization_code" :: "client_credentials" :: "refresh_token" :: "implicit" :: Nil).asJava)
    oAuth2Client.setResponseTypes(("code" :: "id_token" :: "token" :: "code token" :: "code id_token" :: "code token id_token" :: Nil).asJava)
    oAuth2Client.setPostLogoutRedirectUris(List(redirectUrl).asJava)

    oAuth2Client.setRedirectUris(List(redirectUrl).asJava)
    // if set client certificate supplied, set it to client meta.
    if(consumer.clientCertificate != null && StringUtils.isNotBlank(consumer.clientCertificate.get)) {
      val clientMeta = Map("client_certificate" -> consumer.clientCertificate.get).asJava
      oAuth2Client.setMetadata(clientMeta)
    }

    oAuth2Client.setTokenEndpointAuthMethod("client_secret_post")

    Some(hydraAdmin.createOAuth2Client(oAuth2Client))
  }
}
