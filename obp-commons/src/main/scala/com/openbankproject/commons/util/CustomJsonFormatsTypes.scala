package com.openbankproject.commons.util

import com.openbankproject.commons.dto.InBoundTrait
import com.openbankproject.commons.model.TopicTrait

import scala.reflect.runtime.universe._

/**
 * The `Type` constants `code.api.util.OptionalFieldSerializer` (obp-api) dispatches on.
 *
 * Same reason as `SwaggerTypes`: `typeOf[T]` needs the Scala 2 compiler to synthesise a `TypeTag`
 * for `T`, which Scala 3 does not implement, while the runtime `Type` value itself - and every
 * operation OptionalFieldSerializer performs on it (`<:<`, `.decls`, `.typeArgs`) - works under
 * either compiler. obp-commons stays on Scala 2.13, so the six vals below are computed once, here,
 * and consumed as plain values from code that will run under Scala 3.
 */
object CustomJsonFormatsTypes {

  val tTopicTrait: Type = typeOf[TopicTrait]
  val tInBoundTraitWildcard: Type = typeOf[InBoundTrait[_]]
  val tOptionalAnnotation: Type = typeOf[optional]
  val tIterableWildcard: Type = typeOf[Iterable[_]]
  val tMapWildcardWildcard: Type = typeOf[Map[_, _]]
  val tArrayWildcard: Type = typeOf[Array[_]]
}
