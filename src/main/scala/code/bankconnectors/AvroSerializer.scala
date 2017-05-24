package code.bankconnectors

import java.io.ByteArrayOutputStream

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
    val input = AvroInputStream.json[T](data)
    val result: Try[T] = input.singleEntity
    result.toOption
  }

}