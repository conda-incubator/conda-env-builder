package com.github.condaincubator.condaenvbuilder.api

import com.github.condaincubator.condaenvbuilder.testing.UnitSpec

import java.nio.file.Paths
import io.circe.syntax._

object CodeStepTest extends UnitSpec {
  val TestCases: Seq[(CodeStep, String)] = Seq(
    // empty set of commands
    (CodeStep(path="."), {
      """{
        |  "path" : ".",
        |  "commands" : [
        |  ]
        |}""".stripMargin
    }),
    // single command
    (CodeStep(path="/root", "foo"), {
      """{
        |  "path" : "/root",
        |  "commands" : [
        |    "foo"
        |  ]
        |}""".stripMargin
    }),
    // multiple command
    (CodeStep(path="/root", "foo", "bar"), {
      """{
        |  "path" : "/root",
        |  "commands" : [
        |    "foo",
        |    "bar"
        |  ]
        |}""".stripMargin
    }),
    // no path, empty command
    (CodeStep(path="."), {
      """{
        |  "path" : ".",
        |  "commands" : [
        |  ]
        |}""".stripMargin
    })
  )
}

class CodeStepTest extends UnitSpec {

  "CodeStep" should "store one or more commands" in {
    CodeStep(path=".") shouldBe new CodeStep(path=Paths.get("."), commands=Seq.empty)
    CodeStep(path=".", "foo") shouldBe new CodeStep(path=Paths.get("."), commands=Seq("foo"))
    val step = CodeStep(path="/some/path", "foo", "bar")
    step.path shouldBe Paths.get("/some/path")
    step.commands should contain theSameElementsInOrderAs Seq("foo", "bar")
  }

  "CodeStep.inheritFrom" should "ignore non-code steps" in {
    val step = CodeStep(path=".", "foo")
    step.inheritFrom(new DummyStep) shouldBe step
  }

  it should "inherit from a step with the same path" in {
    val parent = CodeStep(path=".", "parent-command")
    val child  = CodeStep(path=".", "child-command")
    child.inheritFrom(parent) shouldBe CodeStep(path=".", "parent-command", "child-command")
  }

  it should "inherit from a step with a different path" in {
    val parent = CodeStep(path="/parent", "parent-command-1", "parent-command-2")
    val child  = CodeStep(path="/child", "child-command")
    child.inheritFrom(parent) shouldBe CodeStep(
      path     = "/child",
      commands = "pushd /parent; parent-command-1; parent-command-2; popd", "child-command"
    )
  }

  "CodeStep.encoder" should "encode a CodeStep as JSON" in {
    import Encoders.EncodeCodeStep
    CodeStepTest.TestCases.foreach { case (step, string) =>
      step.asJson.toString shouldBe string
    }
  }

  "CodeStep.decoder" should "decode CodeStep from JSON" in {
    import Decoders.DecodeCodeStep
    CodeStepTest.TestCases.foreach { case (step, string) =>
      toJson(string).as[CodeStep].rightValue shouldBe step
    }
  }
}
