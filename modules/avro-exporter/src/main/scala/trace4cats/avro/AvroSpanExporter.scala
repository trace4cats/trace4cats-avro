package trace4cats.avro

import java.net.ConnectException

import cats.{Applicative, Traverse}
import cats.effect.kernel.syntax.spawn._
import cats.effect.kernel.{Async, Resource}
import cats.effect.std.{Queue, Semaphore}
import cats.syntax.applicative._
import cats.syntax.applicativeError._
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.syntax.option._
import cats.syntax.traverse._
import com.comcast.ip4s.{Host, IpAddress, Port, SocketAddress}
import fs2.io.net._
import fs2.{Chunk, Stream}
import org.typelevel.log4cats.Logger
import trace4cats.kernel.SpanExporter
import trace4cats.model.{Batch, CompletedSpan}

import scala.concurrent.duration._

object AvroSpanExporter {

  /** Creates a UDP exporter with an internal queue that accepts batches from the traced app.
    *
    * @param numFibers
    *   the capacity of the internal queue and the number of concurrent workers that consume the queue and send batches
    *   via UDP; use numbers greater than 1 at your own risk
    * @param encoder
    *   an optional instance of [[CompletedSpanEncoder]]. This should be used when multiple Avro exporters are used in a
    *   single instance so that encoding remains thread safe
    */
  def udp[F[_]: Async, G[_]: Traverse](
    host: String = agentHostname,
    port: Int = agentPort,
    numFibers: Int = 1,
    encoder: Option[CompletedSpanEncoder[F]] = None
  ): Resource[F, SpanExporter[F, G]] = {
    val queueCapacity = numFibers
    val maxPermits = numFibers.toLong

    def write(
      encode: CompletedSpan => F[Array[Byte]],
      address: SocketAddress[IpAddress],
      semaphore: Semaphore[F],
      queue: Queue[F, Batch[G]],
      socket: DatagramSocket[F]
    ): F[Unit] =
      Stream
        .repeatEval {
          for {
            batch <- queue.take
            _ <- semaphore.permit.surround {
              batch.spans.traverse { span =>
                for {
                  ba <- encode(span)
                  _ <- socket.write(Datagram(address, Chunk.array(ba)))
                } yield ()
              }
            }
          } yield ()
        }
        .compile
        .drain

    for {
      enc <- Resource.eval(encoder.fold(CompletedSpanEncoder[F])(Applicative[F].pure(_)))
      host <- Resource.eval(Host.fromString(host).liftTo[F](new IllegalArgumentException(s"invalid host $host")))
      port <- Resource.eval(Port.fromInt(port).liftTo[F](new IllegalArgumentException(s"invalid port $port")))
      address <- Resource.eval(SocketAddress(host, port).resolve[F])
      queue <- Resource.eval(Queue.bounded[F, Batch[G]](queueCapacity))
      semaphore <- Resource.eval(Semaphore[F](maxPermits))
      socketGroup <- Network[F].datagramSocketGroup()
      socket <- socketGroup.openDatagramSocket()
      writer = Stream
        .retry(write(enc.encode, address, semaphore, queue, socket), 5.seconds, _ + 1.second, Int.MaxValue)
        .compile
        .drain
        .background
      writers = writer.replicateA(numFibers)
      preFinalizer = Resource.unit[F].onFinalize(semaphore.acquireN(maxPermits))
      _ <- writers >> preFinalizer
    } yield new SpanExporter[F, G] {
      override def exportBatch(batch: Batch[G]): F[Unit] = queue.offer(batch)
    }
  }

  /** Creates a TCP exporter with an internal queue that accepts batches from the traced app.
    *
    * @param numFibers
    *   the capacity of the internal queue and the number of concurrent workers that consume the queue and send batches
    *   via TCP; use numbers greater than 1 at your own risk
    * @param encoder
    *   an optional instance of [[CompletedSpanEncoder]]. This should be used when multiple Avro exporters are used in a
    *   single instance so that encoding remains thread safe
    */
  def tcp[F[_]: Async: Logger, G[_]: Traverse](
    host: String = agentHostname,
    port: Int = agentPort,
    numFibers: Int = 1,
    encoder: Option[CompletedSpanEncoder[F]] = None
  ): Resource[F, SpanExporter[F, G]] = {
    val queueCapacity = numFibers
    val maxPermits = numFibers.toLong

    def connect(socketGroup: SocketGroup[F], address: SocketAddress[Host]): Stream[F, Socket[F]] =
      Stream
        .resource(socketGroup.client(address))
        .recoverWith { case _: ConnectException =>
          Stream.eval(Logger[F].warn(s"Failed to connect to tcp://$host:$port, retrying in 5s")) >> connect(
            socketGroup,
            address
          ).delayBy(5.seconds)
        }

    def write(
      encoder: CompletedSpan => F[Array[Byte]],
      address: SocketAddress[Host],
      semaphore: Semaphore[F],
      queue: Queue[F, Batch[G]],
      socketGroup: SocketGroup[F],
    ): F[Unit] =
      connect(socketGroup, address)
        .flatMap { socket =>
          Stream
            .repeatEval {
              for {
                batch <- queue.take
                _ <- semaphore.permit.surround {
                  batch.spans.traverse { span =>
                    for {
                      ba <- encoder(span)
                      withTerminator = ba ++ Array(0xc4.byteValue, 0x02.byteValue)
                      _ <- socket.write(Chunk.array(withTerminator))
                    } yield ()
                  }
                }
              } yield ()
            }
        }
        .compile
        .drain

    for {
      enc <- Resource.eval(encoder.fold(CompletedSpanEncoder[F])(Applicative[F].pure(_)))
      host <- Resource.eval(Host.fromString(host).liftTo[F](new IllegalArgumentException(s"invalid host $host")))
      port <- Resource.eval(Port.fromInt(port).liftTo[F](new IllegalArgumentException(s"invalid port $port")))
      address = SocketAddress(host, port)
      queue <- Resource.eval(Queue.bounded[F, Batch[G]](queueCapacity))
      semaphore <- Resource.eval(Semaphore[F](maxPermits))
      socketGroup <- Network[F].socketGroup()
      writer =
        Stream
          .retry(write(enc.encode, address, semaphore, queue, socketGroup), 5.seconds, _ + 1.second, Int.MaxValue)
          .compile
          .drain
          .background
      writers = writer.replicateA(numFibers)
      preFinalizer = Resource.unit[F].onFinalize(semaphore.acquireN(maxPermits))
      _ <- writers >> preFinalizer
    } yield new SpanExporter[F, G] {
      override def exportBatch(batch: Batch[G]): F[Unit] = queue.offer(batch)
    }
  }
}
