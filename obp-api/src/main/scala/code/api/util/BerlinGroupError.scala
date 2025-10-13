package code.api.util

object BerlinGroupError {

  /*
  +---------------------------+---------------------------------------------+------------------------------------------------------------------------------------------------+---------------------------------+
  | Code                      | HTTP Code                                   | Description                                                                                    | Endpoint Method                 |
  +---------------------------+---------------------------------------------+------------------------------------------------------------------------------------------------+---------------------------------+
  | FORMAT_ERROR              | 400                                         | The format of certain fields in the request does not meet the requirements.                    | /consents, /accounts, /payments |
  | PARAMETER_NOT_CONSISTENT  | 400                                         | The parameters sent by TPP are inconsistent (only for query parameters).                       | /consents, /accounts, /payments |
  | PARAMETER_NOT_SUPPORTED   | 400                                         | The parameter is not supported by the ASPSP API.                                               | /consents, /accounts            |
  | SERVICE_INVALID           | 400 (if payload) / 405 (if HTTP method)     | The requested service is not valid for the requested resources.                                | /consents, /accounts, /payments |
  | RESOURCE_UNKNOWN          | 400 (if payload) / 403 / 404                | The requested resource cannot be found with respect to the TPP.                                | /consents, /accounts, /payments |
  | RESOURCE_EXPIRED          | 400 (if payload) / 403                      | The requested resource has expired and is no longer accessible.                                | /consents, /accounts, /payments |
  | RESOURCE_BLOCKED          | 400                                         | The requested resource cannot be accessed because it is blocked.                               | /consents, /accounts, /payments |
  | TIMESTAMP_INVALID         | 400                                         | The time is not within an accepted period.                                                     | /consents, /accounts, /payments |
  | PERIOD_INVALID            | 400                                         | The requested time period is out of range.                                                     | /consents, /accounts, /payments |
  | SCA_METHOD_UNKNOWN        | 400                                         | The SCA method selected is unknown or cannot be compared by ASPSP with PSU.                    | /consents, /accounts, /payments |
  | CONSENT_UNKNOWN           | 400 (if header) / 403 (if path)             | Consent-ID cannot be found by ASPSP with respect to TPP.                                       | /consents, /accounts, /payments |
  | SESSIONS_NOT_SUPPORTED    | 400                                         | The combined service indicator cannot be used with this ASPSP.                                 | /consents, /accounts, /payments |
  | PAYMENT_FAILED            | 400                                         | The POST request for initiating the payment failed.                                            | /payments                       |
  | EXECUTION_DATE_INVALID    | 400                                         | The requested execution date is invalid for ASPSP.                                             | /payments                       |
  | CERTIFICATE_INVALID       | 401                                         | The signature certificate content is invalid.                                                  | /consents, /accounts, /payments |
  | ROLE_INVALID              | 403                                         | The TPP does not have the necessary role.                                                      | /consents, /accounts            |
  | CERTIFICATE_EXPIRED       | 401                                         | The signature certificate has expired.                                                         | /consents, /accounts, /payments |
  | CERTIFICATE_BLOCKED       | 401                                         | The signature certificate has been blocked.                                                    | /consents, /accounts, /payments |
  | CERTIFICATE_REVOKED       | 401                                         | The signature certificate has been revoked.                                                    | /consents, /accounts, /payments |
  | CERTIFICATE_MISSING       | 401                                         | The signature certificate was not available in the request but is required.                    | /consents, /accounts, /payments |
  | SIGNATURE_INVALID         | 401                                         | The signature applied for TPP authentication is invalid.                                       | /consents, /accounts, /payments |
  | SIGNATURE_MISSING         | 401                                         | The signature applied for TPP authentication is missing.                                       | /consents, /accounts, /payments |
  | CORPORATE_ID_INVALID      | 401                                         | PSU-Corporate-ID cannot be found by ASPSP.                                                     | /consents, /accounts, /payments |
  | PSU_CREDENTIALS_INVALID   | 401                                         | PSU-ID cannot be found by ASPSP, or it is blocked, or the password/OTP is incorrect.           | /consents, /accounts, /payments |
  | CONSENT_INVALID           | 401                                         | The consent created by TPP is not valid for the requested service/resource.                    | /consents, /accounts, /payments |
  | CONSENT_EXPIRED           | 401                                         | The consent created by TPP has expired and needs to be renewed.                                | /consents, /accounts, /payments |
  | TOKEN_UNKNOWN             | 401                                         | The OAuth2 token cannot be found by ASPSP with respect to TPP.                                 | /consents, /accounts, /payments |
  | TOKEN_INVALID             | 401                                         | The OAuth2 token is associated with the TPP but is not valid for the requested service.        | /consents, /accounts, /payments |
  | TOKEN_EXPIRED             | 401                                         | The OAuth2 token has expired and needs to be renewed.                                          | /consents, /accounts, /payments |
  | SERVICE_BLOCKED           | 403                                         | The service is not accessible to PSU due to a block by ASPSP.                                  | /consents, /accounts, /payments |
  | PRODUCT_INVALID           | 403                                         | The requested payment product is not available for PSU.                                        | /payments                       |
  | PRODUCT_UNKNOWN           | 404                                         | The requested payment product is not supported by ASPSP.                                       | /payments                       |
  | CANCELLATION_INVALID      | 405                                         | Payments cannot be cancelled due to a time limit or legal restrictions.                        | /payments                       |
  | REQUESTED_FORMATS_INVALID | 406                                         | The formats requested in the Accept header do not match the formats offered by ASPSP.          | /consents, /accounts            |
  | STATUS_INVALID            | 409                                         | The requested resource does not allow additional authorizations.                               | /consents, /accounts, /payments |
  | ACCESS_EXCEEDED           | 429                                         | Access to the account has exceeded the consented frequency per day.                            | /consents, /accounts            |
  +---------------------------+---------------------------------------------+------------------------------------------------------------------------------------------------+---------------------------------+
   */
  def translateToBerlinGroupError(code: String, message: String): String = {
    code match {
      // If this error occurs it implies that its error handling MUST be refined in OBP code
      case "400" if message.contains("OBP-50005") => "INTERNAL_ERROR"

      case "401" if message.contains("OBP-20001") => "PSU_CREDENTIALS_INVALID"
      case "401" if message.contains("OBP-20201") => "PSU_CREDENTIALS_INVALID"
      case "401" if message.contains("OBP-20214") => "PSU_CREDENTIALS_INVALID"
      case "401" if message.contains("OBP-20013") => "PSU_CREDENTIALS_INVALID"
      case "401" if message.contains("OBP-20202") => "PSU_CREDENTIALS_INVALID"
      case "401" if message.contains("OBP-20203") => "PSU_CREDENTIALS_INVALID"
      case "401" if message.contains("OBP-20206") => "PSU_CREDENTIALS_INVALID"
      case "401" if message.contains("OBP-20207") => "PSU_CREDENTIALS_INVALID"

      case "401" if message.contains("OBP-20204") => "TOKEN_EXPIRED"
      case "401" if message.contains("OBP-20215") => "TOKEN_INVALID"
      case "401" if message.contains("OBP-20205") => "TOKEN_INVALID"
      case "401" if message.contains("OBP-20204") => "TOKEN_INVALID"

      case "401" if message.contains("OBP-35003") => "CONSENT_EXPIRED"

      case "401" if message.contains("OBP-35004") => "CONSENT_INVALID"
      case "401" if message.contains("OBP-35015") => "CONSENT_INVALID"
      case "401" if message.contains("OBP-35017") => "CONSENT_INVALID"
      case "401" if message.contains("OBP-35019") => "CONSENT_INVALID"
      case "401" if message.contains("OBP-35018") => "CONSENT_INVALID"
      case "401" if message.contains("OBP-35005") => "CONSENT_INVALID"

      case "401" if message.contains("OBP-20300") => "CERTIFICATE_BLOCKED"
      case "401" if message.contains("OBP-34102") => "CERTIFICATE_BLOCKED"
      case "401" if message.contains("OBP-34103") => "CERTIFICATE_BLOCKED"

      case "401" if message.contains("OBP-20312") => "CERTIFICATE_INVALID"
      case "401" if message.contains("OBP-20310") => "SIGNATURE_INVALID"

      case "403" if message.contains("OBP-20307") => "ROLE_INVALID"
      case "403" if message.contains("OBP-20060") => "ROLE_INVALID"

      case "400" if message.contains("OBP-10034") => "PARAMETER_NOT_CONSISTENT"

      case "400" if message.contains("OBP-35018") => "CONSENT_UNKNOWN"
      case "400" if message.contains("OBP-35001") => "CONSENT_UNKNOWN"
      case "403" if message.contains("OBP-35001") => "CONSENT_UNKNOWN"

      case "400" if message.contains("OBP-50200") => "RESOURCE_UNKNOWN"
      case "404" if message.contains("OBP-30076") => "RESOURCE_UNKNOWN"
      case "404" if message.contains("OBP-40001") => "RESOURCE_UNKNOWN"

      case "400" if message.contains("OBP-10005") => "TIMESTAMP_INVALID"

      case "400" if message.contains("OBP-10001") => "FORMAT_ERROR"
      case "400" if message.contains("OBP-10002") => "FORMAT_ERROR"
      case "400" if message.contains("OBP-10003") => "FORMAT_ERROR"
      case "400" if message.contains("OBP-10006") => "FORMAT_ERROR"
      case "400" if message.contains("OBP-20062") => "FORMAT_ERROR"
      case "400" if message.contains("OBP-20063") => "FORMAT_ERROR"
      case "400" if message.contains("OBP-20252") => "FORMAT_ERROR"
      case "400" if message.contains("OBP-20253") => "FORMAT_ERROR"
      case "400" if message.contains("OBP-20254") => "FORMAT_ERROR"
      case "400" if message.contains("OBP-20255") => "FORMAT_ERROR"
      case "400" if message.contains("OBP-20256") => "FORMAT_ERROR"
      case "400" if message.contains("OBP-20257") => "FORMAT_ERROR"
      case "400" if message.contains("OBP-20251") => "FORMAT_ERROR"
      case "400" if message.contains("OBP-20088") => "FORMAT_ERROR"
      case "400" if message.contains("OBP-20089") => "FORMAT_ERROR"
      case "400" if message.contains("OBP-20090") => "FORMAT_ERROR"
      case "400" if message.contains("OBP-20091") => "FORMAT_ERROR"
      case "400" if message.contains("OBP-40008") => "FORMAT_ERROR"

      case "400" if message.contains("OBP-50221") => "PAYMENT_FAILED"

      case "405" if message.contains("OBP-10404") => "SERVICE_INVALID"

      case "429" if message.contains("OBP-10018") => "ACCESS_EXCEEDED"


      case _ => code
    }
  }

}
