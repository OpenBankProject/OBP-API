package code.util

import java.util.UUID

import code.api.util.APIUtil
import code.model.Consumer
import code.model.Consumer.redirectURLRegex
import com.nimbusds.jose.jwk.gen.{ECKeyGenerator, JWKGenerator, RSAKeyGenerator}
import com.nimbusds.jose.jwk.{AsymmetricJWK, Curve, ECKey, JWK, KeyUse, RSAKey}
import com.nimbusds.jose.{Algorithm, JWSAlgorithm}
import org.apache.commons.lang3.StringUtils
import sh.ory.hydra.api.{AdminApi, PublicApi}
import sh.ory.hydra.model.OAuth2Client
import sh.ory.hydra.{ApiClient, Configuration}

import scala.collection.immutable.List
import scala.jdk.CollectionConverters.{mapAsJavaMapConverter, seqAsJavaListConverter}

object HydraUtil {

  private val INTEGRATE_WITH_HYDRA = "integrate_with_hydra"

  val integrateWithHydra = APIUtil.getPropsAsBoolValue(INTEGRATE_WITH_HYDRA, false)

  val mirrorConsumerInHydra = APIUtil.getPropsAsBoolValue("mirror_consumer_in_hydra", false)

  lazy val hydraPublicUrl = APIUtil.getPropsValue("hydra_public_url")
    .openOrThrowException(s"If props $INTEGRATE_WITH_HYDRA is true, hydra_public_url value should not be blank")
    .replaceFirst("/$", "")

  lazy val hydraAdminUrl = APIUtil.getPropsValue("hydra_admin_url")
    .openOrThrowException(s"If props $INTEGRATE_WITH_HYDRA is true, hydra_admin_url value should not be blank")
    .replaceFirst("/$", "")

  lazy val hydraConsents = APIUtil.getPropsValue("hydra_consents")
    .openOrThrowException(s"If props $INTEGRATE_WITH_HYDRA is true, hydra_client_scope value should not be blank")
    .trim.split("""\s*,\s*""").toList

  private lazy val allConsents = hydraConsents.mkString("openid offline ", " ","")


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
    val redirectUrl = consumer.redirectURL.get
    if (StringUtils.isBlank(redirectUrl) || redirectURLRegex.findFirstIn(redirectUrl).isEmpty) {
      return None
    }
    val oAuth2Client = new OAuth2Client()
    oAuth2Client.setClientId(consumer.key.get)
    oAuth2Client.setClientSecret(consumer.secret.get)

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
    oAuth2Client.setTokenEndpointAuthMethod("client_secret_post")

    val decoratedClient = fun(oAuth2Client)
    Some(hydraAdmin.createOAuth2Client(decoratedClient))
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
