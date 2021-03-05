package net.hamnaberg.schema
package internal

import cats._
import cats.syntax.all._
import cats.free.FreeApplicative
import io.circe.{Encoder, Json, JsonObject}
import io.circe.syntax._

object encoding {
  import structure._

  def fromSchema[A](schema: Schema[A]): Encoder[A] = schema match {
    case SInt(_) =>
      Encoder.encodeJsonNumber
    case SNum(_) =>
      Encoder.encodeJsonNumber
    case SBool =>
      Encoder.encodeBoolean
    case Str(_) =>
      Encoder.encodeString
    case Sequence(value, _, _) =>
      encodeList(value)
    case Record(rec) =>
      Encoder.instance(encodeObject(rec).andThen(_.asJson))
    case Isos(xmap) =>
      fromSchema(xmap.schema).contramap(xmap.w)
    case Defer(f) => fromSchema(f())
    case Custom(_, encoder, _) => encoder
  }

  def encodeList[A](schema: Schema[A]): Encoder[List[A]] =
    Encoder.encodeList[A](fromSchema[A](schema))

  def encodeObject[R](record: FreeApplicative[Field[R, *], R]) = {
    type Target[A] = R => Vector[(String, Json)]

    record
      .analyze {
        new (Field[R, *] ~> Target) {
          def write[E](name: String, schema: Schema[E], elem: E) =
            Vector(name -> fromSchema(schema).apply(elem))

          override def apply[A](fa: Field[R, A]): Target[A] =
            fa match {
              case Field.Optional(name, elemSchema, get) =>
                (r: R) => {
                  val elem = get(r)
                  elem.foldMap(write(name, elemSchema, _))
                }

              case Field.Required(name, elemSchema, get) =>
                (r: R) => {
                  val elem = get(r)
                  write(name, elemSchema, elem)
                }
            }
        }
      }
      .andThen(JsonObject.fromIterable(_))
  }

}