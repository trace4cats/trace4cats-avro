package trace4cats.avro

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers._

class AvroInstancesSpec extends AnyFlatSpec {

  "Codec[CompletedSpan]" should "have the same schema as the generic one" in {
    AvroInstances.completedSpanCodec.schema shouldBe AvroGenericInstances.completedSpanCodec.schema
  }

  "Codec[TraceProcess]" should "have the same schema as the generic one" in {
    AvroInstances.processCodec.schema shouldBe AvroGenericInstances.processCodec.schema
  }

}
