package trace4cats.avro

import java.io.ByteArrayOutputStream

import cats.effect.kernel.{Resource, Sync}
import cats.syntax.either._
import cats.syntax.flatMap._
import cats.syntax.functor._
import org.apache.avro.generic.GenericDatumWriter
import org.apache.avro.io.{BinaryEncoder, EncoderFactory}
import trace4cats.model.CompletedSpan

trait CompletedSpanEncoder[F[_]] {
  def encode(span: CompletedSpan): F[Array[Byte]]
}

object CompletedSpanEncoder {
  // Avro encoding isn't thread safe. This ensures that there is exactly one avro decoder for each thread
  def apply[F[_]: Sync]: F[CompletedSpanEncoder[F]] = {
    for {
      schema <- AvroInstances.completedSpanSchema[F]
      factory <- Sync[F].delay(EncoderFactory.get())
      encoder <- Sync[F].delay(new ThreadLocal[BinaryEncoder])
      writer <- Sync[F].delay(ThreadLocal.withInitial(() => new GenericDatumWriter[Any](schema)))
    } yield new CompletedSpanEncoder[F] {
      override def encode(span: CompletedSpan): F[Array[Byte]] = Resource
        .fromAutoCloseable(Sync[F].delay(new ByteArrayOutputStream))
        .use { out =>
          Sync[F]
            .fromEither(AvroInstances.completedSpanCodec.encode(span).leftMap(_.throwable))
            .flatMap { record =>
              Sync[F].delay {
                val encoderInstance = factory.directBinaryEncoder(out, encoder.get())
                encoder.set(encoderInstance)

                val w = writer.get()
                w.write(record, encoderInstance)
                out.toByteArray
              }
            }
        }
    }
  }
}
