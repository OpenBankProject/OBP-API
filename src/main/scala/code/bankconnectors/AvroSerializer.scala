package code.bankconnectors

import java.io.{ByteArrayOutputStream, InputStream}

import com.sksamuel.avro4s._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

/**
  * Provides generic serialization/deserialization
  */
trait AvroSerializer {

  def serialize[T: SchemaFor : ToRecord](event: T)(implicit executionContext: ExecutionContext): String = {
    val baos = new ByteArrayOutputStream()
    val output = AvroOutputStream.json[T](baos)
    output.write(event)
    output.close()
    val r = baos.toString("UTF-8")
    r
  }

  def serializeFuture[T: SchemaFor : ToRecord](event: T)(implicit executionContext: ExecutionContext): Future[String] =
    Future(serialize(event))

  def deserializeFuture[T >: Null : SchemaFor : FromRecord](data: String)(implicit executionContext: ExecutionContext): Future[Option[T]] =
    Future(deserialize[T](data))

  def deserialize[T >: Null : SchemaFor : FromRecord](data: String)(implicit executionContext: ExecutionContext): Option[T] = {

    val input = AvroInputStream.json[T](new StringInputStream(data))
    val result: Try[T] = input.singleEntity
    result.toOption
  }

  class StringInputStream(s: String) extends InputStream {
    private val bytes = s.getBytes

    private var pos = 0

    override def read(): Int = if (pos >= bytes.length) {
      -1
    } else {
      val r = bytes(pos)
      pos += 1
      r.toInt
    }
  }
}