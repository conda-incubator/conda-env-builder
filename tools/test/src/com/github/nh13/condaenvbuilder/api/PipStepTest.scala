package com.github.nh13.condaenvbuilder.api

import com.github.nh13.condaenvbuilder.testing.UnitSpec
import io.circe.syntax._

class PipStepTest extends UnitSpec {
  "PipStep" should "store pip arguments" in {
    PipStep().args shouldBe Symbol("empty")
    PipStep().requirements shouldBe Symbol("empty")
    val step = PipStep(args=Seq("arg1", "arg2"), requirements=Seq("req1", "req2", "req2").reqs)
    step.args should contain theSameElementsInOrderAs Seq("arg1", "arg2")
    step.requirements should contain theSameElementsInOrderAs Seq("req1", "req2", "req2").reqs
  }

  "PipStep.inheritFrom" should "ignore non-pip steps" in {
    val step = PipStep()
    step.inheritFrom(new DummyStep) shouldBe step
  }

  it should "inherit from a step with args" in {
    val step    = PipStep(args=Seq("arg1", "arg2"))
    val parent1 = PipStep(args=Seq("arg3", "arg4"))
    val parent2 = PipStep(args=Seq("arg1", "arg4"))

    step.inheritFrom(step) shouldBe step
    step.inheritFrom(parent1).args should contain theSameElementsInOrderAs Seq("arg1", "arg2", "arg3", "arg4")
    step.inheritFrom(parent2).args should contain theSameElementsInOrderAs Seq("arg1", "arg2", "arg4")
  }

  it should "inherit from a step with requirements" in {
    val step    = PipStep(requirements=Seq("a==1", "b==2").reqs)
    val parent1 = PipStep(requirements=Seq("c==3", "d==4").reqs)
    val parent2 = PipStep(requirements=Seq("a==1", "d==4").reqs)

    step.inheritFrom(step) shouldBe step
    step.inheritFrom(parent1).requirements should contain theSameElementsInOrderAs Seq("a==1", "b==2", "c==3", "d==4").reqs
    step.inheritFrom(parent2).requirements should contain theSameElementsInOrderAs Seq("a==1", "b==2", "d==4").reqs
  }

  private val testCases: Seq[(PipStep, String)] = Seq(
    // no args or requirements
    (PipStep(), {
      """{
        |  "args" : [
        |  ],
        |  "requirements" : [
        |  ]
        |}""".stripMargin
    }),
    // one arg, one requirement
    (PipStep(args=Seq("some argument"), requirements=Seq("a==1").reqs), {
      """{
        |  "args" : [
        |    "some argument"
        |  ],
        |  "requirements" : [
        |    "a==1"
        |  ]
        |}""".stripMargin
    }),
    // multiple args and requirements
    (PipStep(args=Seq("arg 1", "arg 2"), requirements=Seq("a==1", "b==2").reqs), {
      """{
        |  "args" : [
        |    "arg 1",
        |    "arg 2"
        |  ],
        |  "requirements" : [
        |    "a==1",
        |    "b==2"
        |  ]
        |}""".stripMargin
    })
  )

  "PipStep.encoder" should "encode a PipStep as JSON" in {
    import Encoders.EncodePipStep
    testCases.foreach { case (step, string) =>
      step.asJson.toString shouldBe string
    }
  }

  "PipStep.decoder" should "decode PipStep from JSON" in {
    import Decoders.DecodePipStep
    testCases.foreach { case (step, string) =>
      toJson(string).as[PipStep].rightValue shouldBe step
    }
  }
}
