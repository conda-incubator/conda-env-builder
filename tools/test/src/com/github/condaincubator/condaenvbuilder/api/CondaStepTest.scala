package com.github.condaincubator.condaenvbuilder.api

import com.github.condaincubator.condaenvbuilder.api
import com.github.condaincubator.condaenvbuilder.testing.UnitSpec
import io.circe.syntax._

object CondaStepTest extends UnitSpec {
  val TestCases: Seq[(CondaStep, String)] = Seq(
    // no channels or requirements
    (CondaStep(), {
      """{
        |  "channels" : [
        |  ],
        |  "requirements" : [
        |  ]
        |}""".stripMargin
    }),
    // one channel, one requirement, one platform
    (api.CondaStep(channels=Seq("some channel"), requirements=Seq("a==1").reqs, platforms=Seq("linux-32")), {
      """{
        |  "channels" : [
        |    "some channel"
        |  ],
        |  "requirements" : [
        |    "a==1"
        |  ],
        |  "platforms" : [
        |    "linux-32"
        |  ]
        |}""".stripMargin
    }),
    // multiple channels, requirements, and platforms
    (api.CondaStep(channels=Seq("channel 1", "channel 2"), requirements=Seq("a==1", "b==2").reqs, platforms=Seq("linux-32", "win-32")), {
      """{
        |  "channels" : [
        |    "channel 1",
        |    "channel 2"
        |  ],
        |  "requirements" : [
        |    "a==1",
        |    "b==2"
        |  ],
        |  "platforms" : [
        |    "linux-32",
        |    "win-32"
        |  ]
        |}""".stripMargin
    }),
    // empty requirements, empty channels
    (CondaStep(channels=Seq.empty, requirements=Seq.empty, platforms=Seq.empty), {
      """{
        |  "channels" : [
        |  ],
        |  "requirements" : [
        |  ]
        |}""".stripMargin
    })
  )
}

class CondaStepTest extends UnitSpec {
  "CondaStep" should "store channels and requirements" in {
    CondaStep().channels shouldBe Symbol("empty")
    CondaStep().requirements shouldBe Symbol("empty")
    val step = api.CondaStep(channels=Seq("channel1", "channel2"), requirements=Seq("req1", "req2", "req2").reqs)
    step.channels should contain theSameElementsInOrderAs Seq("channel1", "channel2")
    step.requirements should contain theSameElementsInOrderAs Seq("req1", "req2", "req2").reqs
  }

  "CondaStep.inheritFrom" should "ignore non-pip steps" in {
    val step = CondaStep()
    step.inheritFrom(new DummyStep) shouldBe step
  }

  it should "inherit from a step with channels, prioritizing inherited channels" in {
    val step    = CondaStep(channels=Seq("channel1", "channel2"))
    val parent1 = CondaStep(channels=Seq("channel3", "channel4"))
    val parent2 = CondaStep(channels=Seq("channel1", "channel4"))

    step.inheritFrom(step) shouldBe step
    step.inheritFrom(parent1).channels should contain theSameElementsInOrderAs Seq("channel3", "channel4", "channel1", "channel2")
    step.inheritFrom(parent2).channels should contain theSameElementsInOrderAs Seq("channel1", "channel4", "channel2")
  }

  it should "inherit from a step with requirements" in {
    val step    = api.CondaStep(requirements=Seq("a==5", "b==2").reqs)
    val parent1 = api.CondaStep(requirements=Seq("c==3", "d==4").reqs)
    val parent2 = api.CondaStep(requirements=Seq("a==1", "d==4").reqs)

    step.inheritFrom(step) shouldBe step
    // distinct requirements, so inherit them all!
    step.inheritFrom(parent1).requirements should contain theSameElementsInOrderAs Seq("c==3", "d==4", "a==5", "b==2").reqs
    // overlapping requirements, keep child
    step.inheritFrom(parent2).requirements should contain theSameElementsInOrderAs Seq("d==4", "a==5", "b==2").reqs
  }

  it should "prepend inherited channel, even when the parent has default channels applied" in {
    val defaults = CondaStep(channels=Seq("c1"))
    val parent   = CondaStep(channels=Seq.empty)
    val child    = CondaStep(channels=Seq("c2"))

    child.inheritFrom(parent.withDefaults(defaults)).channels should contain theSameElementsInOrderAs Seq("c1", "c2")
  }

  "CondaStep.withDefaults" should "ignore non-conda steps" in {
    val step = CondaStep()
    step.withDefaults(new DummyStep) shouldBe step
  }

  it should "append default channels to the current list of channels" in {
    val step1 = CondaStep(channels=Seq("arg3", "arg4"))
    val step2 = CondaStep(channels=Seq("arg1", "arg2"))
    val step3 = CondaStep(channels=Seq("arg4", "arg5"))
    step1.withDefaults(step1) shouldBe step1
    step1.withDefaults(step2).channels should contain theSameElementsInOrderAs Seq("arg3", "arg4", "arg1", "arg2")
    step2.withDefaults(step1).channels should contain theSameElementsInOrderAs Seq("arg1", "arg2", "arg3", "arg4")
    step1.withDefaults(step3).channels should contain theSameElementsInOrderAs Seq("arg3", "arg4", "arg5")
    step3.withDefaults(step1).channels should contain theSameElementsInOrderAs Seq("arg4", "arg5", "arg3")
  }

  it should "apply defaults to requirements with defaults" in {
    val step1 = api.CondaStep(requirements=Seq("a==1", f"b==${Requirement.DefaultVersion}", "c==3").reqs)
    val step2 = api.CondaStep(requirements=Seq(f"a==${Requirement.DefaultVersion}", "b==2").reqs)
    step1.withDefaults(step2).requirements should contain theSameElementsInOrderAs Seq("a==1", "b==2", "c==3").reqs
    step2.withDefaults(step1).requirements should contain theSameElementsInOrderAs Seq("a==1", "b==2").reqs
  }

  "CondaStep.encoder" should "encode a CondaStep as JSON" in {
    import Encoders.EncodeCondaStep
    CondaStepTest.TestCases.foreach { case (step, string) =>
      step.asJson.toString shouldBe string
    }
  }

  "CondaStep.decoder" should "decode CondaStep from JSON" in {
    import Decoders.DecodeCondaStep
    CondaStepTest.TestCases.foreach { case (step, string) =>
      toJson(string).as[CondaStep].rightValue shouldBe step
    }
  }
}
