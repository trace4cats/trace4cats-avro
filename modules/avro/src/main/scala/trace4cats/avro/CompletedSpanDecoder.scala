package trace4cats.avro

import cats.effect.kernel.Sync
import cats.syntax.either._
import cats.syntax.flatMap._
import cats.syntax.functor._
import org.apache.avro.generic.GenericDatumReader
import org.apache.avro.io.{BinaryDecoder, DecoderFactory}
import trace4cats.model.CompletedSpan

trait CompletedSpanDecoder[F[_]] {
  def decode(bytes: Array[Byte]): F[CompletedSpan]
}

object CompletedSpanDecoder {
  // Avro decoding isn't thread safe. This ensures that there is exactly one avro decoder for each thread
  def apply[F[_]: Sync]: F[CompletedSpanDecoder[F]] = for {
    schema <- AvroInstances.completedSpanSchema[F]
    factory <- Sync[F].delay(DecoderFactory.get())
    decoder <- Sync[F].delay(new ThreadLocal[BinaryDecoder])
    reader <- Sync[F].delay(ThreadLocal.withInitial(() => new GenericDatumReader[Any](schema)))
  } yield new CompletedSpanDecoder[F] {
    override def decode(bytes: Array[Byte]): F[CompletedSpan] =
      Sync[F]
        .delay {
          val decoderInstance = factory.binaryDecoder(bytes, decoder.get())

          val r = reader.get()
          r.read(null, decoderInstance)
        }
        .flatMap { record =>
          Sync[F].fromEither(AvroInstances.completedSpanCodec.decode(record, schema).leftMap(_.throwable))
        }
  }
}
