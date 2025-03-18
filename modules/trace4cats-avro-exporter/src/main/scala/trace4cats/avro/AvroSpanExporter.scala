/*
 * Copyright (c) 2021-2025 Trace4Cats <https://github.com/trace4cats>
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package trace4cats.avro

import java.io.ByteArrayOutputStream
import java.net.ConnectException

import scala.concurrent.duration._

import cats.Applicative
import cats.Traverse
import cats.effect.kernel.Async
import cats.effect.kernel.Resource
import cats.effect.kernel.Sync
import cats.effect.kernel.syntax.spawn._
import cats.effect.std.Queue
import cats.effect.std.Semaphore
import cats.syntax.applicativeError._
import cats.syntax.apply._
import cats.syntax.either._
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.syntax.option._
import cats.syntax.traverse._

import com.comcast.ip4s.Host
import com.comcast.ip4s.IpAddress
import com.comcast.ip4s.Port
import com.comcast.ip4s.SocketAddress
import fs2.Chunk
import fs2.Stream
import fs2.io.net._
import org.apache.avro.Schema
import org.apache.avro.generic.GenericDatumWriter
import org.apache.avro.io.EncoderFactory
import org.typelevel.log4cats.Logger
import trace4cats.kernel.SpanExporter
import trace4cats.model.Batch
import trace4cats.model.CompletedSpan

object AvroSpanExporter {

  // TODO: use one from cats-core when it's merged https://github.com/typelevel/cats/pull/3705
  private def replicateA_[F[_]: Applicative, A](fa: F[A])(n: Int): F[Unit] =
    (1 to n).map(_ => fa).foldLeft(Applicative[F].unit)(_ <* _)

  private def encode[F[_]: Sync](schema: Schema)(span: CompletedSpan): F[Array[Byte]] =
    Sync[F]
      .fromEither(AvroInstances.completedSpanCodec.encode(span).leftMap(_.throwable))
      .flatMap { record =>
        Resource
          .make(
            Sync[F].delay {
              val writer = new GenericDatumWriter[Any](schema)
              val out    = new ByteArrayOutputStream

              val encoder = EncoderFactory.get.binaryEncoder(out, null) // scalafix:ok

              (writer, out, encoder)
            }
          ) { case (_, out, _) =>
            Sync[F].delay(out.close())
          }
          .use { case (writer, out, encoder) =>
            Sync[F].delay {
              writer.write(record, encoder)
              encoder.flush()
              out.toByteArray
            }
          }
      }

  /** Creates a UDP exporter with an internal queue that accepts batches from the traced app.
    *
    * @param numFibers
    *   the capacity of the internal queue and the number of concurrent workers that consume the queue and send batches
    *   via UDP; use numbers greater than 1 at your own risk
    */
  def udp[F[_]: Async, G[_]: Traverse](
      host: String = agentHostname,
      port: Int = agentPort,
      numFibers: Int = 1
  ): Resource[F, SpanExporter[F, G]] = {
    val queueCapacity = numFibers
    val maxPermits    = numFibers.toLong

    def write(
        schema: Schema,
        address: SocketAddress[IpAddress],
        semaphore: Semaphore[F],
        queue: Queue[F, Batch[G]],
        socket: DatagramSocket[F]
    ): F[Unit] =
      Stream.repeatEval {
        for {
          batch <- queue.take
          _ <- semaphore.permit.surround {
                 batch.spans.traverse { span =>
                   for {
                     ba <- encode[F](schema)(span)
                     _  <- socket.write(Datagram(address, Chunk.array(ba)))
                   } yield ()
                 }
               }
        } yield ()
      }.compile.drain

    for {
      avroSchema  <- Resource.eval(AvroInstances.completedSpanSchema[F])
      host        <- Resource.eval(Host.fromString(host).liftTo[F](new IllegalArgumentException(s"invalid host $host")))
      port        <- Resource.eval(Port.fromInt(port).liftTo[F](new IllegalArgumentException(s"invalid port $port")))
      address     <- Resource.eval(SocketAddress(host, port).resolve[F])
      queue       <- Resource.eval(Queue.bounded[F, Batch[G]](queueCapacity))
      semaphore   <- Resource.eval(Semaphore[F](maxPermits))
      socketGroup <- Network[F].datagramSocketGroup()
      socket      <- socketGroup.openDatagramSocket()
      writer = Stream
                 .retry(write(avroSchema, address, semaphore, queue, socket), 5.seconds, _ + 1.second, Int.MaxValue)
                 .compile
                 .drain
                 .background
      writers      = replicateA_(writer)(numFibers)
      preFinalizer = Resource.unit[F].onFinalize(semaphore.acquireN(maxPermits))
      _           <- writers >> preFinalizer
    } yield new SpanExporter[F, G] {
      override def exportBatch(batch: Batch[G]): F[Unit] = queue.offer(batch)
    }
  }

  /** Creates a TCP exporter with an internal queue that accepts batches from the traced app.
    *
    * @param numFibers
    *   the capacity of the internal queue and the number of concurrent workers that consume the queue and send batches
    *   via TCP; use numbers greater than 1 at your own risk
    */
  def tcp[F[_]: Async: Logger, G[_]: Traverse](
      host: String = agentHostname,
      port: Int = agentPort,
      numFibers: Int = 1
  ): Resource[F, SpanExporter[F, G]] = {
    val queueCapacity = numFibers
    val maxPermits    = numFibers.toLong

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
        schema: Schema,
        address: SocketAddress[Host],
        semaphore: Semaphore[F],
        queue: Queue[F, Batch[G]],
        socketGroup: SocketGroup[F]
    ): F[Unit] =
      connect(socketGroup, address).flatMap { socket =>
        Stream.repeatEval {
          for {
            batch <- queue.take
            _ <- semaphore.permit.surround {
                   batch.spans.traverse { span =>
                     for {
                       ba            <- encode[F](schema)(span)
                       withTerminator = ba ++ Array(0xc4.byteValue, 0x02.byteValue)
                       _             <- socket.write(Chunk.array(withTerminator))
                     } yield ()
                   }
                 }
          } yield ()
        }
      }.compile.drain

    for {
      avroSchema  <- Resource.eval(AvroInstances.completedSpanSchema[F])
      host        <- Resource.eval(Host.fromString(host).liftTo[F](new IllegalArgumentException(s"invalid host $host")))
      port        <- Resource.eval(Port.fromInt(port).liftTo[F](new IllegalArgumentException(s"invalid port $port")))
      address      = SocketAddress(host, port)
      queue       <- Resource.eval(Queue.bounded[F, Batch[G]](queueCapacity))
      semaphore   <- Resource.eval(Semaphore[F](maxPermits))
      socketGroup <- Network[F].socketGroup()
      writer =
        Stream
          .retry(write(avroSchema, address, semaphore, queue, socketGroup), 5.seconds, _ + 1.second, Int.MaxValue)
          .compile
          .drain
          .background
      writers      = replicateA_(writer)(numFibers)
      preFinalizer = Resource.unit[F].onFinalize(semaphore.acquireN(maxPermits))
      _           <- writers >> preFinalizer
    } yield new SpanExporter[F, G] {
      override def exportBatch(batch: Batch[G]): F[Unit] = queue.offer(batch)
    }
  }

}
