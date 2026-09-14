/**
Open Bank Project - API
Copyright (C) 2011-2026, TESOBE GmbH.

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

package code.bankconnectors

import java.io.{ByteArrayOutputStream, InputStream}

import com.sksamuel.avro4s._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Success

trait AvroSerializer {

  def serialize[T: Encoder](event: T)(implicit executionContext: ExecutionContext): String = {
    val baos = new ByteArrayOutputStream()
    val output = AvroOutputStream.json[T].to(baos).build()
    output.write(event)
    output.close()
    baos.toString("UTF-8")
  }

  def serializeFuture[T: Encoder](event: T)(implicit executionContext: ExecutionContext): Future[String] =
    Future(serialize(event))

  def deserializeFuture[T >: Null : Decoder](data: String)(implicit executionContext: ExecutionContext): Future[Option[T]] =
    Future(deserialize[T](data))

  def deserialize[T >: Null : Decoder](data: String)(implicit executionContext: ExecutionContext): Option[T] = {
    val schema = implicitly[Decoder[T]].schema
    val input = AvroInputStream.json[T].from(new StringInputStream(data)).build(schema)
    val result = input.tryIterator.collectFirst { case Success(v) => v }
    input.close()
    result
  }

  class StringInputStream(s: String) extends InputStream {
    private val bytes = s.getBytes("UTF-8")

    private var pos = 0

    override def read(): Int = if (pos >= bytes.length) {
      -1
    } else {
      val r = bytes(pos)
      pos += 1
      r.toInt & 0xFF
    }
  }
}
