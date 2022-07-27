package trace4cats.avro.server

import cats.effect.kernel.{Async, Resource}
import cats.syntax.applicativeError._
import cats.syntax.functor._
import cats.syntax.option._
import cats.{Applicative, ApplicativeThrow}
import com.comcast.ip4s.Port
import fs2.io.net.Network
import fs2.{Chunk, Pipe, Pull, Stream}
import org.typelevel.log4cats.Logger
import trace4cats.avro.{agentPort, CompletedSpanDecoder}
import trace4cats.model.CompletedSpan

object AvroServer {

  private def buffer[F[_]]: Pipe[F, Byte, Chunk[Byte]] =
    bytes => {
      def go(lastBytes: (Byte, Byte), state: List[Byte], stream: Stream[F, Byte]): Pull[F, Chunk[Byte], Unit] =
        stream.pull.uncons1.flatMap {
          case Some((hd, tl)) =>
            val newLastBytes = (lastBytes._2, hd)
            val newState = hd :: state
            if (newLastBytes == (0xc4.byteValue -> 0x02.byteValue))
              Pull.output1(Chunk(newState.drop(2).reverse: _*)) >> go((0, 0), List.empty, tl)
            else
              go(newLastBytes, newState, tl)

          case None => Pull.done
        }

      go((0, 0), List.empty, bytes).stream
    }

  private def decodeOption[F[_]: ApplicativeThrow: Logger](
    decoder: CompletedSpanDecoder[F]
  ): Chunk[Byte] => F[Option[CompletedSpan]] =
    bytes =>
      decoder
        .decode(bytes.toArray)
        .map[Option[CompletedSpan]](Some(_))
        .handleErrorWith(th => Logger[F].warn(th)("Failed to decode span batch").as(Option.empty[CompletedSpan]))

  /** Creates a TCP server listens for traces from an Avro exporter.
    *
    * @param decoder
    *   an optional instance of [[CompletedSpanDecoder]]. This should be used when multiple Avro exporters are used in a
    *   single instance so that decoding remains thread safe
    */
  def tcp[F[_]: Async: Logger](
    sink: Pipe[F, CompletedSpan, Unit],
    port: Int = agentPort,
    decoder: Option[CompletedSpanDecoder[F]] = None
  ): Resource[F, Stream[F, Unit]] =
    for {
      dec <- Resource.eval(decoder.fold(CompletedSpanDecoder[F])(Applicative[F].pure(_)))
      decode = decodeOption(dec)
      socketGroup <- Network[F].socketGroup()
      port <- Resource.eval(Port.fromInt(port).liftTo[F](new IllegalArgumentException(s"invalid port $port")))
    } yield socketGroup
      .server(port = Some(port))
      .map { socket =>
        socket.reads
          .through(buffer[F])
          .evalMap(decode)
          .unNone
          .through(sink)
      }
      .parJoin(100)

  /** Creates a UDP server listens for traces from an Avro exporter.
    *
    * @param decoder
    *   an optional instance of [[CompletedSpanDecoder]]. This should be used when multiple Avro exporters are used in a
    *   single instance so that decoding remains thread safe
    */
  def udp[F[_]: Async: Logger](
    sink: Pipe[F, CompletedSpan, Unit],
    port: Int = agentPort,
    decoder: Option[CompletedSpanDecoder[F]] = None
  ): Resource[F, Stream[F, Unit]] =
    for {
      dec <- Resource.eval(decoder.fold(CompletedSpanDecoder[F])(Applicative[F].pure(_)))
      decode = decodeOption(dec)
      port <- Resource.eval(Port.fromInt(port).liftTo[F](new IllegalArgumentException(s"invalid port $port")))
      socketGroup <- Network[F].datagramSocketGroup()
      socket <- socketGroup.openDatagramSocket(port = Some(port))
    } yield socket.reads
      .map(_.bytes)
      .evalMap(decode)
      .unNone
      .through(sink)
}
