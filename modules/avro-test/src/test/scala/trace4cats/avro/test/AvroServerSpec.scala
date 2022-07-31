package trace4cats.avro.test

import cats.data.NonEmptyList
import cats.effect.std.Queue
import cats.effect.unsafe.implicits.global
import cats.effect.{IO, Resource}
import cats.kernel.Eq
import cats.syntax.all._
import fs2.{Chunk, Stream}
import org.scalacheck.{Arbitrary, Gen, Shrink}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import trace4cats.CompleterConfig
import trace4cats.avro.server.AvroServer
import trace4cats.avro.{AvroSpanCompleter, AvroSpanExporter, CompletedSpanDecoder, CompletedSpanEncoder}
import trace4cats.model.{Batch, CompletedSpan, TraceProcess}
import trace4cats.test.ArbitraryInstances

import scala.concurrent.duration._

class AvroServerSpec extends AnyFlatSpec with ScalaCheckDrivenPropertyChecks with ArbitraryInstances {
  implicit val logger: Logger[IO] = Slf4jLogger.getLogger[IO]

  implicit override val generatorDrivenConfig: PropertyCheckConfiguration =
    PropertyCheckConfiguration(minSuccessful = 1, maxDiscardedFactor = 50.0)

  implicit val nBatchesArbitrary: Arbitrary[(Int, List[Batch[Chunk]])] = Arbitrary(for {
    n <- Gen.choose(10, 20)
    list <- Gen.listOfN(n, batchArb.arbitrary)
  } yield (n, list))

  implicit val completedSpanBuilderNel: Arbitrary[NonEmptyList[CompletedSpan.Builder]] =
    Arbitrary(Gen.nonEmptyListOf(completedSpanBuilderArb.arbitrary).map(NonEmptyList.fromListUnsafe))

  implicit def noShrink[T]: Shrink[T] = Shrink.shrinkAny

  val decoder = Some(CompletedSpanDecoder[IO].unsafeRunSync())
  val encoder = Some(CompletedSpanEncoder[IO].unsafeRunSync())

  behavior.of("Avro TCP")

  it should "send batches" in {
    forAll { (batch: Batch[Chunk]) =>
      Queue
        .unbounded[IO, CompletedSpan]
        .flatMap { queue =>
          (for {
            server <- AvroServer.tcp[IO](_.evalMap(queue.offer), decoder = decoder)
            _ <- server.compile.drain.background
            _ <- Resource.eval(IO.sleep(2.seconds))
            completer <- AvroSpanExporter.tcp[IO, Chunk](encoder = encoder)
          } yield completer).use(_.exportBatch(batch) >> IO.sleep(3.seconds)) >> Stream
            .fromQueueUnterminated(queue)
            .take(batch.spans.size.toLong)
            .compile
            .to(Chunk)
            .map { spans =>
              Eq.eqv(spans, batch.spans)
            }
        }
        .unsafeRunSync()
    }
  }

  it should "send batches via multiple fibers" in {
    forAll { (nBatches: (Int, List[Batch[Chunk]])) =>
      val (n, batches) = nBatches
      Queue
        .unbounded[IO, CompletedSpan]
        .flatMap { queue =>
          (for {
            server <- AvroServer.tcp[IO](_.evalMap(queue.offer), decoder = decoder)
            _ <- server.compile.drain.background
            _ <- Resource.eval(IO.sleep(2.seconds))
            completer <- AvroSpanExporter.tcp[IO, Chunk](numFibers = n, encoder = encoder)
          } yield completer).use(c => batches.traverse_(c.exportBatch)) >> Stream
            .fromQueueUnterminated(queue)
            .take(batches.foldMap(_.spans.size.toLong))
            .compile
            .toList
            .map { spans =>
              Eq.eqv(spans.sortBy(_.context.spanId.show), batches.flatMap(_.spans.toList).sortBy(_.context.spanId.show))
            }
        }
        .unsafeRunSync()
    }
  }

  it should "send individual spans in batches" in {
    forAll { (process: TraceProcess, spans: NonEmptyList[CompletedSpan.Builder]) =>
      Queue
        .unbounded[IO, CompletedSpan]
        .flatMap { queue =>
          (for {
            server <- AvroServer.tcp[IO](_.evalMap(queue.offer), port = 7778, decoder = decoder)
            _ <- server.compile.drain.background
            _ <- Resource.eval(IO.sleep(2.seconds))
            completer <- AvroSpanCompleter.tcp[IO](
              process,
              port = 7778,
              config = CompleterConfig(batchTimeout = 1.second),
              encoder = encoder
            )
          } yield completer).use(c => spans.traverse(c.complete) >> IO.sleep(3.seconds)) >> Stream
            .fromQueueUnterminated(queue)
            .take(spans.size.toLong)
            .compile
            .toList
            .map { s =>
              assert(Eq.eqv(s, spans.toList.map(_.build(process))))
            }
        }
        .unsafeRunSync()

    }
  }

  behavior.of("Avro UDP")

  it should "send batches" in {
    forAll { (batch: Batch[Chunk]) =>
      Queue
        .unbounded[IO, CompletedSpan]
        .flatMap { queue =>
          (for {
            server <- AvroServer.udp[IO](_.evalMap(queue.offer), port = 7779, decoder = decoder)
            _ <- server.compile.drain.background
            _ <- Resource.eval(IO.sleep(2.seconds))
            completer <- AvroSpanExporter.udp[IO, Chunk](port = 7779, encoder = encoder)
          } yield completer).use(_.exportBatch(batch) >> IO.sleep(3.seconds)) >> Stream
            .fromQueueUnterminated(queue)
            .take(batch.spans.size.toLong)
            .compile
            .to(Chunk)
            .map { spans =>
              Eq.eqv(spans, batch.spans)
            }
        }
        .unsafeRunSync()
    }
  }

  it should "send batches via multiple fibers" in {
    forAll { (nBatches: (Int, List[Batch[Chunk]])) =>
      val (n, batches) = nBatches
      Queue
        .unbounded[IO, CompletedSpan]
        .flatMap { queue =>
          (for {
            server <- AvroServer.udp[IO](_.evalMap(queue.offer), port = 7781, decoder = decoder)
            _ <- server.compile.drain.background
            _ <- Resource.eval(IO.sleep(2.seconds))
            completer <- AvroSpanExporter.udp[IO, Chunk](port = 7781, numFibers = n, encoder = encoder)
          } yield completer).use(c => batches.traverse_(c.exportBatch) >> IO.sleep(3.seconds)) >> Stream
            .fromQueueUnterminated(queue)
            .take(batches.foldMap(_.spans.size.toLong))
            .compile
            .toList
            .map { spans =>
              Eq.eqv(spans.sortBy(_.context.spanId.show), batches.flatMap(_.spans.toList).sortBy(_.context.spanId.show))
            }
        }
        .unsafeRunSync()
    }
  }

  it should "send individual spans in batches" in {
    forAll { (process: TraceProcess, spans: NonEmptyList[CompletedSpan.Builder]) =>
      Queue
        .unbounded[IO, CompletedSpan]
        .flatMap { queue =>
          (for {
            server <- AvroServer.udp[IO](_.evalMap(queue.offer), port = 7780, decoder = decoder)
            _ <- server.compile.drain.background
            _ <- Resource.eval(IO.sleep(2.seconds))
            completer <- AvroSpanCompleter.udp[IO](
              process,
              port = 7780,
              config = CompleterConfig(batchTimeout = 1.second),
              encoder = encoder
            )
          } yield completer).use(c => spans.traverse(c.complete) >> IO.sleep(3.seconds)) >> Stream
            .fromQueueUnterminated(queue)
            .take(spans.size.toLong)
            .compile
            .toList
            .map { s =>
              assert(Eq.eqv(s, spans.toList.map(_.build(process))))
            }
        }
        .unsafeRunSync()
    }
  }
}
