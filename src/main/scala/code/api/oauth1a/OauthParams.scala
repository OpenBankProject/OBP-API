/**
  * This particular file is marked with the Apache license (unless specified otherwise, OBP API is licensed with the AGPL v3)
  *
  * Copyright 2018 Hunor Kovács: kovacshuni@yahoo.com
  *
  * Licensed under the Apache License, Version 2.0 (the "License");
  * you may not use this file except in compliance with the License.
  * You may obtain a copy of the License at
  *
  *  http://www.apache.org/licenses/LICENSE-2.0
  *
  * Unless required by applicable law or agreed to in writing, software
  * distributed under the License is distributed on an "AS IS" BASIS,
  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  * See the License for the specific language governing permissions and
  * limitations under the License.
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
