package com.github.condaincubator.condaenvbuilder.testing

import java.nio.file.{Files, Path}
import com.fulcrumgenomics.commons.util.CaptureSystemStreams
import com.github.condaincubator.condaenvbuilder.api.{Requirement, Step}
import com.github.condaincubator.condaenvbuilder.api.Requirement
import io.circe.{Json, _}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.{EitherValues, OptionValues, TryValues}

/** Base class for unit testing. */
trait UnitSpec extends AnyFlatSpec with Matchers with OptionValues with EitherValues with TryValues with CaptureSystemStreams {

  implicit class EitherRightOrLeft[L, R](either: Either[L, R]) {
    def leftValue: L  = either.swap.toOption.value
    def rightValue: R = either.toOption.value
  }

  implicit class RequirementsFromStrings(requirements: Seq[String]) {
    def reqs: Seq[Requirement] = this.requirements.map(Requirement(_))
  }

  implicit class StringsFromRequirements(requirements: Seq[Requirement]) {
    def strings: Seq[String] = this.requirements.map(_.toString)
  }

  class DummyStep extends Step
  object DummyStep {
    def apply(): DummyStep = new DummyStep
  }

  def maybeJson(value: String): Either[ParsingFailure, Json] = {
    yaml.parser.parse(value)
  }

  def toJson(value: String): Json = maybeJson(value=value).rightValue

  /** Creates a new temp file for use in testing that will be deleted when the VM exits. */
  protected def makeTempFile(prefix: String, suffix: String) : Path = {
    val path = Files.createTempFile(prefix, suffix)
    path.toFile.deleteOnExit()
    path
  }
}
