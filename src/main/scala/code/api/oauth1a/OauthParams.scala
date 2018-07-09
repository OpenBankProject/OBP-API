/**
  * Open Bank Project - API
  * Copyright (C) 2011-2018, TESOBE Ltd
  *
  * This program is free software: you can redistribute it and/or modify
  * it under the terms of the Apache License, Version 2.0.
  **
  * This program is distributed in the hope that it will be useful,
  * but WITHOUT ANY WARRANTY; without even the implied warranty of
  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. the Apache License, Version 2.0 for more details.
  **
  * You should have received a copy of the Apache License, Version 2.0 License
  * along with this program. If not, see <http://www.apache.org/licenses/LICENSE-2.0>.
  **
  * Email: contact@tesobe.com
  * TESOBE Ltd
  * Osloerstrasse 16/17
  * Berlin 13359, Germany
  **
  * This product includes software developed at
  * https://github.com/kovacshuni/koauth
  * by
  * Hunor Kovács: kovacshuni@yahoo.com
  *
  */

package code.api.oauth1a

/**
  * Constant names should be in upper camel case. Similar to Java’s static final members,
  * if the member is final, immutable and it belongs to a package object or an object,
  * it may be considered a constant:
  * object Container {
  *   val MyConstant = ...
  * }
  */

object OauthParams {
  val ConsumerKeyName = "oauth_consumer_key"
  val ConsumerSecretName = "oauth_consumer_secret"
  val TokenName = "oauth_token"
  val TokenSecretName = "oauth_token_secret"
  val SignatureMethodName = "oauth_signature_method"
  val SignatureName = "oauth_signature"
  val TimestampName = "oauth_timestamp"
  val NonceName = "oauth_nonce"
  val VersionName = "oauth_version"
  val CallbackName = "oauth_callback"
  val CallbackConfirmedName = "oauth_callback_confirmed"
  val VerifierName = "oauth_verifier"
  val RealmName = "realm"
}
