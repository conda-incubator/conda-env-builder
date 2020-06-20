package com.github.nh13.condaenvbuilder.api

import java.nio.file.{Path, Paths}

import com.github.nh13.condaenvbuilder.api.CodeStep.Command
import io.circe.Decoder.Result
import io.circe.syntax._
import io.circe.{Decoder, Encoder, HCursor, Json}

/** Represents one or more custom commands to execute after conda and pip packages have been installed.
  *
  * @param path the working directory to use when executing the command
  * @param commands the commands to execute
  */
case class CodeStep(path: Path, commands: Seq[Command]=Seq.empty) extends Step {
  /** Inherit commands from the given step(s).  The inherited commands will prepended (i.e. executed prior to) the
    * current commands
    *
    * @param step the step(s) to inherit from
    */
  override def inheritFrom(step: Step*): CodeStep = {
    val commands = step.collect { case s: CodeStep => s }.flatMap { _step =>
      if (_step.path == this.path) _step.commands
      else Seq(f"pushd ${_step.path}; " + _step.commands.mkString("; ") + "; popd")
    }
    this.copy(commands=commands ++ this.commands)
  }
}

/** Example YAML encoding for [[CodeStep]]
  * {{{
  *   - code:
  *     path: /some/path
  *     commands:
  *       - some command 1
  *       - some command 2
  * }}}
  */
object CodeStep {
  /** Represents a command string */
  type Command = String

  import Encoders.EncodePath

  /** Alternate constructor, used for testing */
  private[api] def apply(path: String, commands: Command*): CodeStep = {
    new CodeStep(path=Paths.get(path), commands=commands)
  }

  /** Returns an YAML encoder for [[CodeStep]] */
  def encoder: Encoder[CodeStep] = new Encoder[CodeStep] {
    final def apply(step: CodeStep): Json = Json.obj(
      ("path", step.path.asJson),
      ("commands", Json.fromValues(step.commands.map(_.asJson)))
    )
  }

  /** Returns a YAML decoder for [[CodeStep]] */
  def decoder: Decoder[CodeStep] = new Decoder[CodeStep] {
    import Decoders.DecodePath

    final def apply(c: HCursor): Decoder.Result[CodeStep] = {
      val keys: Seq[String] = c.keys.map(_.toSeq).getOrElse(Seq.empty)

      val pathResult: Result[Path] = {
        if (keys.contains("path")) c.downField("path").as[Path]
        else Right(Paths.get("."))
      }

      val commandsResult: Result[Seq[String]] = {
        if (keys.contains("commands")) c.downField("commands").as[Seq[String]]
        else Right(Seq.empty)
      }

      for {
        commands <- commandsResult
        path <- pathResult
      } yield {
        CodeStep(path=path, commands=commands)
      }
    }
  }
}