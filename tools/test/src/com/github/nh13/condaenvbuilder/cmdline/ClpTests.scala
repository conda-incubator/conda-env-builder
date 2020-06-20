package com.github.nh13.condaenvbuilder.cmdline

import com.fulcrumgenomics.sopt.{arg, clp}
import com.github.nh13.condaenvbuilder.testing.UnitSpec

@clp(group=ClpGroups.Util, description="A test class")
class TestClp
(
  @arg(flag='e', doc="If set, exit with this code.")    val exitCode: Option[Int],
  @arg(flag='m', doc="If set, fail with this message.") val message: Option[String],
  @arg(flag='p', doc="Print this message.")             val printMe: Option[String]
) extends CondaEnvironmentBuilderTool {
  override def execute(): Unit = {
    (exitCode, message) match {
      case (Some(ex), Some(msg)) => fail(ex, msg)
      case (Some(ex), None     ) => fail(ex)
      case (None,     Some(msg)) => fail(msg)
      case (None,     None     ) => printMe.foreach(println)
    }
  }
}

/** Some basic test for the CLP classes. */
class ClpTests extends UnitSpec {
  "CondaEnvironmentBuilderMain" should "find a CLP and successfully set it up and execute it" in {
    new CondaEnvironmentBuilderMain().makeItSo("TestClp --print-me=hello".split(' ')) shouldBe 0
  }

  it should "fail with the provided exit code" in {
    new CondaEnvironmentBuilderMain().makeItSo("TestClp -e 7".split(' ')) shouldBe 7
    new CondaEnvironmentBuilderMain().makeItSo("TestClp --exit-code=5".split(' ')) shouldBe 5
    new CondaEnvironmentBuilderMain().makeItSo("TestClp --exit-code=9 --message=FailBabyFail".split(' ')) shouldBe 9
    new CondaEnvironmentBuilderMain().makeItSo("TestClp --message=FailBabyFail".split(' ')) should not be 0
  }

  it should "fail and print usage" in {
    new CondaEnvironmentBuilderMain().makeItSo("SomeProgram --with-args=that-dont-exist".split(' ')) should not be 0
  }
}
