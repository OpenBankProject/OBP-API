package com.openbankproject.adapter

package object actor {
  implicit def toOption[T](any :T) = Option(any)
}
