/*
 * Copyright 2006-2011 WorldWide Conferencing, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.openbankproject.commons.util

import net.liftweb.common.{Box, Empty, Failure, Full, ParamFailure}
import net.liftweb.json.Extraction.{decompose, extract}
import net.liftweb.json.JsonAST.{JField, JNothing, JNull, JObject, JString, JValue}
import net.liftweb.json.{Formats, MappingException, Serializer, TypeInfo}
import org.apache.commons.codec.binary.Base64

import java.io._
import java.lang.reflect.ParameterizedType

class JsonBoxSerializer extends Serializer[Box[_]] {
  private val BoxClass = classOf[Box[_]]

  def deserialize(implicit format: Formats): PartialFunction[(TypeInfo, JValue), Box[_]] = {
    case (TypeInfo(BoxClass, ptype), json) => json match {
      case JNull | JNothing => Empty
      case JObject(JField("box_failure", JString("Failure")) ::
                   JField("msg", JString(msg)) ::
                   JField("exception", exception) ::
                   JField("chain", chain) :: Nil) =>
                     Failure(msg, deserializeException(exception),
                             extract(chain, TypeInfo(BoxClass, Some(typeHoldingFailure))).asInstanceOf[Box[Failure]])
      case JObject(JField("box_failure", JString("ParamFailure")) ::
                   JField("msg", JString(msg)) ::
                   JField("exception", exception) ::
                   JField("chain", chain) ::
                   JField("paramType", JString(paramType)) ::
                   JField("param", param) :: Nil) =>
                     val clazz = Thread.currentThread.getContextClassLoader.loadClass(paramType)
                     ParamFailure(msg, deserializeException(exception),
                                  extract(chain, TypeInfo(BoxClass, Some(typeHoldingFailure))).asInstanceOf[Box[Failure]],
                                  extract(param, TypeInfo(clazz, None)))
      case x =>
        val t = ptype.getOrElse(throw new MappingException("parameterized type not known for Box"))
        Full(extract(x, TypeInfo(t.getActualTypeArguments()(0).asInstanceOf[Class[_]], None)))
    }
  }

  def serialize(implicit format: Formats): PartialFunction[Any, JValue] = {
    case Full(x) => decompose(x)
    case Empty => JNull
    case ParamFailure(msg, exception, chain, param) =>
      JObject(JField("box_failure", JString("ParamFailure")) ::
              JField("msg", JString(msg)) ::
              JField("exception", serializeException(exception)) ::
              JField("chain", decompose(chain)) ::
              JField("paramType", JString(param.asInstanceOf[AnyRef].getClass.getName)) ::
              JField("param", decompose(param)) :: Nil)
    case Failure(msg, exception, chain) =>
      JObject(JField("box_failure", JString("Failure")) ::
              JField("msg", JString(msg)) ::
              JField("exception", serializeException(exception)) ::
              JField("chain", decompose(chain)) :: Nil)
  }

  private val typeHoldingFailure = new ParameterizedType {
    def getActualTypeArguments = Array(classOf[Failure])
    def getOwnerType = classOf[Box[Failure]]
    def getRawType = classOf[Box[Failure]]
  }

  private def serializeException(exception: Box[Throwable]) = exception match {
    case Full(x) => JString(javaSerialize(x))
    case _ => JNull
  }

  private def deserializeException(json: JValue) = json match {
    case JString(s) => Full(javaDeserialize(s).asInstanceOf[Throwable])
    case _ => Empty
  }

  private def javaSerialize(obj: AnyRef): String = {
    val bytes = new ByteArrayOutputStream
    val out = new ObjectOutputStream(bytes)
    out.writeObject(obj)
    new String((new Base64).encode(bytes.toByteArray))
  }

  private def javaDeserialize(s: String): Any = {
    val bytes = new ByteArrayInputStream((new Base64).decode(s.getBytes("UTF-8")))
    val in = new ObjectInputStream(bytes)
    in.readObject
  }
}

