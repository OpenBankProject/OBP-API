package code.api.util


import code.api.OAuth2Login.Keycloak
import code.model.{AppType, Consumer}
import code.util.Helper.MdcLoggable
import net.liftweb.common.{Box, Failure, Full}
import okhttp3._
import okhttp3.logging.HttpLoggingInterceptor


object KeycloakAdmin extends MdcLoggable {

  val integrateWithKeycloak: Boolean = APIUtil.getPropsAsBoolValue("integrate_with_keycloak", defaultValue = false)
  // Define variables (replace with actual values)
  private val keycloakHost = Keycloak.keycloakHost
  private val realm = APIUtil.getPropsValue(nameOfProperty = "oauth2.keycloak.realm", "master")
  private val accessToken = APIUtil.getPropsValue(nameOfProperty = "keycloak.admin.access_token", "")

  def createHttpClientWithLogback(): OkHttpClient = {
    val builder = new OkHttpClient.Builder()
    val logging = new HttpLoggingInterceptor(new HttpLoggingInterceptor.Logger {
      override def log(message: String): Unit = logger.debug(message)
    })
    logging.setLevel(HttpLoggingInterceptor.Level.BODY) // Log full request/response details
    builder.addInterceptor(logging)
    builder.build()
  }
  // Create OkHttp client with logging
  val client: OkHttpClient = createHttpClientWithLogback()

  def createKeycloakConsumer(consumer: Consumer): Box[Boolean] = {
    val isPublic =
      AppType.valueOf(consumer.appType.get) match {
        case AppType.Confidential => false
        case _ => true
      }
    createClient(
      clientId = consumer.key.get,
      secret = consumer.secret.get,
      name = consumer.name.get,
      description = consumer.description.get,
      redirectUri = consumer.redirectURL.get,
      isPublic = isPublic,
    )
  }
  def createClient(clientId: String,
                   secret: String,
                   name: String,
                   description: String,
                   redirectUri: String,
                   isPublic: Boolean,
                   realm: String = realm
                  ) = {
    val url = s"$keycloakHost/admin/realms/$realm/clients"
    // JSON request body
    val jsonBody =
      s"""{
        |  "clientId": "$clientId",
        |  "name": "$name",
        |  "description": "$description",
        |  "redirectUris": ["$redirectUri"],
        |  "enabled": true,
        |  "clientAuthenticatorType": "client-secret",
        |  "directAccessGrantsEnabled": true,
        |  "standardFlowEnabled": true,
        |  "implicitFlowEnabled": false,
        |  "serviceAccountsEnabled": true,
        |  "publicClient": $isPublic,
        |  "secret": "$secret"
        |}""".stripMargin

    // Define the request with headers and JSON body
    val requestBody = RequestBody.create(MediaType.get("application/json; charset=utf-8"), jsonBody)

    val request = new Request.Builder()
      .url(url)
      .post(requestBody)
      .addHeader("Authorization", s"Bearer $accessToken")
      .addHeader("Content-Type", "application/json")
      .build()

    makeAndHandleHttpCall(request)
  }

  private def makeAndHandleHttpCall(request: Request): Box[Boolean] = {
    // Execute the request
    try {
      val response = client.newCall(request).execute()
      if (response.isSuccessful) {
        logger.debug(s"Response: ${response.body.string}")
        Full(response.isSuccessful)
      } else {
        logger.error(s"Request failed with status code: ${response.code}")
        logger.debug(s"Response: ${response}")
        Failure(s"code: ${response.code} message: ${response.message}")
      }
    } catch {
      case e: Exception =>
        logger.error(s"Error occurred: ${e.getMessage}")
        Failure(e.getMessage)
    }
  }
}
