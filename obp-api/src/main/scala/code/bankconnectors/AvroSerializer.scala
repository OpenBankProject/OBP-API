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
