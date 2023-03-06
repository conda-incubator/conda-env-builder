package com.github.condaincubator.condaenvbuilder.util
import com.fulcrumgenomics.commons.util.LazyLogging
import com.github.condaincubator.condaenvbuilder.testing.UnitSpec

class ProcessTest extends UnitSpec with LazyLogging {
  "Process.run" should "run a simple command" in {
    Process.run(logger=logger, processBuilder=f"echo 'Hello World'")
  }

  it should "fail if the command cannot be parsed" in {
    an[Exception] should be thrownBy {
      Process.run(logger = logger, processBuilder = f"echo 'Hello World") // unmatched quote
    }
  }

  it should "fail if the command cannot be found" in {
    an[java.io.IOException] should be thrownBy {
      Process.run(logger = logger, processBuilder = f"foo-bar-123")
    }
  }

  it should "fail if the command exits non-zero" in {
    an[IllegalStateException] should be thrownBy {
      Process.run(logger = logger, processBuilder = f"conda foo")
    }
  }
}