package code.util

import java.util.UUID

import code.api.util.APIUtil
import code.model.Consumer
import code.model.Consumer.redirectURLRegex
import code.util.Helper.MdcLoggable
import com.nimbusds.jose.jwk.gen.{ECKeyGenerator, JWKGenerator, RSAKeyGenerator}
import com.nimbusds.jose.jwk.{AsymmetricJWK, Curve, ECKey, JWK, KeyUse, RSAKey}
import com.nimbusds.jose.{Algorithm, JWSAlgorithm}
import org.apache.commons.lang3.StringUtils
import sh.ory.hydra.api.{AdminApi, PublicApi}
import sh.ory.hydra.model.OAuth2Client
import sh.ory.hydra.{ApiClient, Configuration}

import scala.collection.immutable.List
import scala.jdk.CollectionConverters.{mapAsJavaMapConverter, seqAsJavaListConverter}

object HydraUtil extends MdcLoggable{

  private val INTEGRATE_WITH_HYDRA = "integrate_with_hydra"

  val integrateWithHydra = APIUtil.getPropsAsBoolValue(INTEGRATE_WITH_HYDRA, false)

  val mirrorConsumerInHydra = APIUtil.getPropsAsBoolValue("mirror_consumer_in_hydra", false)

  val useObpUserAtHydra = APIUtil.getPropsAsBoolValue("use_obp_user_at_hydra", false)

  val clientSecretPost = "client_secret_post"
  
  val hydraTokenEndpointAuthMethod =
    APIUtil.getPropsValue("hydra_token_endpoint_auth_method", "private_key_jwt")  
  val hydraSupportedTokenEndpointAuthMethods =
    APIUtil.getPropsValue("hydra_supported_token_endpoint_auth_methods", "client_secret_basic,client_secret_post,private_key_jwt")

  lazy val hydraPublicUrl = APIUtil.getPropsValue("hydra_public_url")
    .openOrThrowException(s"If props $INTEGRATE_WITH_HYDRA is true, hydra_public_url value should not be blank")
    .replaceFirst("/$", "")

  lazy val hydraAdminUrl = APIUtil.getPropsValue("hydra_admin_url")
    .openOrThrowException(s"If props $INTEGRATE_WITH_HYDRA is true, hydra_admin_url value should not be blank")
    .replaceFirst("/$", "")

  lazy val hydraConsents = APIUtil.getPropsValue("hydra_consents")
    .openOrThrowException(s"If props $INTEGRATE_WITH_HYDRA is true, hydra_client_scope value should not be blank")
    .trim.split("""\s*,\s*""").toList

  private lazy val allConsents = hydraConsents.mkString("openid offline email profile ", " ","")


  val grantTypes = ("authorization_code" :: "client_credentials" :: "refresh_token" :: "implicit" :: Nil).asJava

  lazy val hydraAdmin = {
    val hydraAdminUrl = APIUtil.getPropsValue("hydra_admin_url")
      .openOrThrowException(s"If props $INTEGRATE_WITH_HYDRA is true, hydra_admin_url value should not be blank")
    val defaultClient = Configuration.getDefaultApiClient
    defaultClient.setBasePath(hydraAdminUrl)
    new AdminApi(defaultClient)
  }

  lazy val hydraPublic = {
    val hydraPublicUrl = APIUtil.getPropsValue("hydra_public_url")
      .openOrThrowException(s"If props $INTEGRATE_WITH_HYDRA is true, hydra_public_url value should not be blank")
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
  def createHydraClient(consumer: Consumer, fun: OAuth2Client => OAuth2Client = identity): Option[OAuth2Client] = {
    logger.info("createHydraClient process is starting")
    val redirectUrl = consumer.redirectURL.get
    if (StringUtils.isBlank(redirectUrl) || redirectURLRegex.findFirstIn(redirectUrl).isEmpty) {
      return None
    }
    val oAuth2Client = new OAuth2Client()
    oAuth2Client.setClientId(consumer.key.get)
    oAuth2Client.setClientSecret(consumer.secret.get)
    oAuth2Client.setClientName(consumer.name.get)

    oAuth2Client.setScope(allConsents)

    oAuth2Client.setGrantTypes(grantTypes)
    oAuth2Client.setResponseTypes(("code" :: "id_token" :: "token" :: "code id_token" :: Nil).asJava)
    oAuth2Client.setPostLogoutRedirectUris(List(redirectUrl).asJava)

    oAuth2Client.setRedirectUris(List(redirectUrl).asJava)
    // if set client certificate supplied, set it to client meta.
    if(consumer.clientCertificate != null && StringUtils.isNotBlank(consumer.clientCertificate.get)) {
      val clientMeta = Map("client_certificate" -> consumer.clientCertificate.get).asJava
      oAuth2Client.setMetadata(clientMeta)
    }
    // TODO Set token_endpoint_auth_method in accordance to the Consumer.AppType value
    // Consumer.AppType = Confidential => client_secret_post
    // Consumer.AppType = Public => private_key_jwt
    // Consumer.AppType = Unknown => private_key_jwt
    oAuth2Client.setTokenEndpointAuthMethod(HydraUtil.hydraTokenEndpointAuthMethod)

    val decoratedClient = fun(oAuth2Client)
    val oAuth2ClientResult = Some(hydraAdmin.createOAuth2Client(decoratedClient))
    logger.info("createHydraClient process is successful.")
    oAuth2ClientResult
  }


  /**
   * create jwk
   * @param signingAlg signing algorithm name
   * @return private key json string to public key
   */
  def createJwk(signingAlg: String): (String, String) = {
    val keyGenerator = if(signingAlg.startsWith("ES")) {
      val curves = Curve.forJWSAlgorithm(JWSAlgorithm.parse(signingAlg))
      val curve:Curve = curves.iterator().next()
      new ECKeyGenerator(curve)
    } else {
      new RSAKeyGenerator(RSAKeyGenerator.MIN_KEY_SIZE_BITS)
    }
    val jwk: JWK = keyGenerator.keyUse(KeyUse.SIGNATURE) // indicate the intended use of the key
      .keyID(UUID.randomUUID().toString()) // give the key a unique ID
      .algorithm(new Algorithm(signingAlg))
      .generate()

    jwk.toJSONString -> jwk.toPublicJWK().toJSONString
  }
}
