/**
  * Open Bank Project - API
  * Copyright (C) 2011-2018, TESOBE Ltd
  **
  *This program is free software: you can redistribute it and/or modify
  *it under the terms of the Apache License, Version 2.0.
  **
  *This program is distributed in the hope that it will be useful,
  *but WITHOUT ANY WARRANTY; without even the implied warranty of
  *MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  *GNU Affero General Public License for more details.
  **
  *You should have received a copy of the Apache License, Version 2.0 License
  *along with this program. If not, see <http://www.apache.org/licenses/LICENSE-2.0>.
  **
  *Email: contact@tesobe.com
  *TESOBE Ltd
  *Osloerstrasse 16/17
  *Berlin 13359, Germany
  **
  *This product includes software developed at
  *https://github.com/kovacshuni/koauth
  *by
  *Hunor Kovács : kovacshuni@yahoo.com
  *
  */

package code.api.oauth1a

import java.net.{URLDecoder, URLEncoder}
import java.nio.charset.StandardCharsets.UTF_8
import java.util.Base64

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object Arithmetics {

  private val HmacSha1Algorithm = "HmacSHA1"
  private val FirstSlash = "(?<!/)/(?!/)"
  private val Base64Encoder = Base64.getEncoder

  private val paramSortOrder = (lhs: (String, String), rhs: (String, String)) => {
    val keyOrder = lhs._1.compareTo(rhs._1)
    if (keyOrder < 0) true
    else if (keyOrder > 0) false
    else lhs._2.compareTo(rhs._2) < 0
  }

  private def encodeAndSort(params: List[(String, String)]): List[(String, String)] = {
    params map { p => (urlEncode(p._1), urlEncode(p._2)) } sortWith paramSortOrder
  }

  def urlDecode(s: String) = URLDecoder.decode(s, "UTF-8")

  private val urlEncodePattern = """\+|\*|%7E""".r
  def urlEncode(s: String) = urlEncodePattern.replaceAllIn(URLEncoder.encode(s, "UTF-8"), m => m.group(0) match {
    case "+" => "%20"
    case "*" => "%2A"
    case "%7E" => "~"
  })

  def createAuthorizationHeader(oauthParamsList: List[(String, String)]): String = {
    "OAuth " + (encodeAndSort(oauthParamsList) map { p => p._1 + "=\"" + p._2 + "\"" } mkString ", ")
  }

  def concatItemsForSignature(method: String, urlWithoutParams: String, urlParams: List[(String, String)], bodyParams: List[(String, String)], oauthParamsList: List[(String, String)]): String = {
    val method1 = urlEncode(method)
    val url = urlEncode(toLowerCase(urlWithoutParams))
    val params =  urlEncode(normalizeRequestParams(urlParams, oauthParamsList, bodyParams))
    List(method1, url, params) mkString "&"
  }

  def normalizeRequestParams(urlParams: List[(String, String)],
                             oauthParamsList: List[(String, String)],
                             bodyParams: List[(String, String)]): String = {
    val filtered = oauthParamsList.filterNot(kv => kv._1 == OauthParams.RealmName || kv._1 == OauthParams.SignatureName)
    encodePairSortConcat(urlParams ::: filtered ::: bodyParams)
  }

  def toLowerCase(url: String): String = {
    val parts = url.split(FirstSlash, 2)
    if (parts.length > 1) parts(0).toLowerCase + "/" + parts(1)
    else parts(0).toLowerCase
  }

  def encodePairSortConcat(keyValueList: List[(String, String)]): String = {
    encodeAndSort(keyValueList) map { p =>  p._1 + "=" + p._2 } mkString "&"
  }

  def pairSortConcat(keyValueList: List[(String, String)]): String = {
    keyValueList sortWith paramSortOrder map { p =>  p._1 + "=" + p._2 } mkString "&"
  }

  def sign(base: String, consumerSecret: String, tokenSecret: String): String = {
    val key = List(consumerSecret, tokenSecret) map urlEncode mkString "&"
    val secretkeySpec = new SecretKeySpec(key.getBytes(UTF_8), HmacSha1Algorithm)
    val mac = Mac.getInstance(HmacSha1Algorithm)
    mac.init(secretkeySpec)
    val bytesToSign = base.getBytes(UTF_8)
    val digest = mac.doFinal(bytesToSign)
    val digest64 = Base64Encoder.encode(digest)
    new String(digest64, UTF_8)
  }
}
