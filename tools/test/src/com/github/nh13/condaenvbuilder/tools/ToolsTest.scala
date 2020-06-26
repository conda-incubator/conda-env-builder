package com.github.nh13.condaenvbuilder.tools

import java.nio.file.Files

import com.fulcrumgenomics.commons.io.Io
import com.github.nh13.condaenvbuilder.cmdline.CondaEnvironmentBuilderTool
import com.github.nh13.condaenvbuilder.testing.UnitSpec

class ToolsTest extends UnitSpec {

  // YAML input to the Compile tool
  private val specString: String =  {
    """name: example
      |environments:
      |  defaults:
      |    steps:
      |      - conda:
      |          channels:
      |            - conda-forge
      |            - bioconda
      |          requirements:
      |            - bwa=0.7.17
      |            - hisat2=2.2.0
      |            - pybedtools=0.8.1
      |            - python=3.6.10
      |            - samtools=1.10
      |            - yaml=0.1.7
      |      - pip:
      |          requirements:
      |            - defopt==5.1.0
      |            - samwell==0.0.1
      |            - distutils-strtobool==0.1.0
      |  samtools:
      |    group: alignment
      |    steps:
      |      - conda:
      |          requirements:
      |            - samtools=1.9
      |  bwa:
      |    group: alignment
      |    inherits:
      |      - samtools
      |    steps:
      |      - conda:
      |          requirements:
      |            - bwa
      |  hisat2:
      |    group: alignment
      |    inherits:
      |      - samtools
      |    steps:
      |      - conda:
      |          requirements:
      |            - hisat2
      |  conda-env-builder:
      |    steps:
      |      - conda:
      |          requirements:
      |            - pybedtools
      |            - yaml
      |      - pip:
      |          requirements:
      |            - defopt
      |            - samwell
      |            - distutils-strtobool
      |      - code:
      |          commands:
      |            - "python setup.py develop"""".stripMargin
  }

  // YAML compiled by the Compile tool
  val compiledString: String = {
    """name: example
      |environments:
      |  conda-env-builder:
      |    group: conda-env-builder
      |    steps:
      |    - conda:
      |        channels:
      |        - conda-forge
      |        - bioconda
      |        requirements:
      |        - pybedtools=0.8.1
      |        - yaml=0.1.7
      |    - pip:
      |        args: []
      |        requirements:
      |        - defopt==5.1.0
      |        - samwell==0.0.1
      |        - distutils-strtobool==0.1.0
      |    - code:
      |        path: .
      |        commands:
      |        - python setup.py develop
      |  hisat2:
      |    group: alignment
      |    steps:
      |    - conda:
      |        channels:
      |        - conda-forge
      |        - bioconda
      |        requirements:
      |        - hisat2=2.2.0
      |        - samtools=1.9
      |  bwa:
      |    group: alignment
      |    steps:
      |    - conda:
      |        channels:
      |        - conda-forge
      |        - bioconda
      |        requirements:
      |        - bwa=0.7.17
      |        - samtools=1.9
      |  samtools:
      |    group: alignment
      |    steps:
      |    - conda:
      |        channels:
      |        - conda-forge
      |        - bioconda
      |        requirements:
      |        - samtools=1.9""".stripMargin
  }

  // Compiled but after running Solve with dryRun=true
  val compiledReformatted: String = {
    """name: example
      |environments:
      |  samtools:
      |    group: alignment
      |    steps:
      |    - conda:
      |        channels:
      |        - conda-forge
      |        - bioconda
      |        requirements:
      |        - samtools=1.9
      |  bwa:
      |    group: alignment
      |    steps:
      |    - conda:
      |        channels:
      |        - conda-forge
      |        - bioconda
      |        requirements:
      |        - bwa=0.7.17
      |        - samtools=1.9
      |  hisat2:
      |    group: alignment
      |    steps:
      |    - conda:
      |        channels:
      |        - conda-forge
      |        - bioconda
      |        requirements:
      |        - hisat2=2.2.0
      |        - samtools=1.9
      |  conda-env-builder:
      |    group: conda-env-builder
      |    steps:
      |    - conda:
      |        channels:
      |        - conda-forge
      |        - bioconda
      |        requirements:
      |        - pybedtools=0.8.1
      |        - yaml=0.1.7
      |        - pip==default
      |    - pip:
      |        args: []
      |        requirements:
      |        - defopt==5.1.0
      |        - samwell==0.0.1
      |        - distutils-strtobool==0.1.0
      |    - code:
      |        path: .
      |        commands:
      |        - python setup.py develop""".stripMargin
  }

  // Compiled but after running Solve with dryRun=true and groups=Set("alignment")
  val compiledReformattedAlignmentOnly: String = {
    """name: example
      |environments:
      |  samtools:
      |    group: alignment
      |    steps:
      |    - conda:
      |        channels:
      |        - conda-forge
      |        - bioconda
      |        requirements:
      |        - samtools=1.9
      |  bwa:
      |    group: alignment
      |    steps:
      |    - conda:
      |        channels:
      |        - conda-forge
      |        - bioconda
      |        requirements:
      |        - bwa=0.7.17
      |        - samtools=1.9
      |  hisat2:
      |    group: alignment
      |    steps:
      |    - conda:
      |        channels:
      |        - conda-forge
      |        - bioconda
      |        requirements:
      |        - hisat2=2.2.0
      |        - samtools=1.9
      |  conda-env-builder:
      |    group: conda-env-builder
      |    steps:
      |    - conda:
      |        channels:
      |        - conda-forge
      |        - bioconda
      |        requirements:
      |        - pybedtools=0.8.1
      |        - yaml=0.1.7
      |    - pip:
      |        args: []
      |        requirements:
      |        - defopt==5.1.0
      |        - samwell==0.0.1
      |        - distutils-strtobool==0.1.0
      |    - code:
      |        path: .
      |        commands:
      |        - python setup.py develop""".stripMargin
  }

  // Solved YAML for OSX
  val solved: String = {
    """name: example
      |environments:
      |  samtools:
      |    group: alignment
      |    steps:
      |    - conda:
      |        channels:
      |        - conda-forge
      |        - bioconda
      |        requirements:
      |        - bzip2=1.0.8=h0b31af3_2
      |        - ca-certificates=2020.4.5.1=hecc5488_0
      |        - curl=7.69.1=h2d98d24_0
      |        - htslib=1.9=h356306b_9
      |        - krb5=1.17.1=h1752a42_0
      |        - libcurl=7.69.1=hc0b9707_0
      |        - libcxx=10.0.0=h1af66ff_2
      |        - libdeflate=1.3=h01d97ff_0
      |        - libedit=3.1.20170329=hcfe32e1_1001
      |        - libssh2=1.9.0=h39bdce6_2
      |        - ncurses=6.1=h0a44026_1002
      |        - openssl=1.1.1g=h0b31af3_0
      |        - samtools=1.9=h8aa4d43_12
      |        - tk=8.6.10=hbbe82c9_0
      |        - xz=5.2.5=h0b31af3_0
      |        - zlib=1.2.11=h0b31af3_1006
      |  bwa:
      |    group: alignment
      |    steps:
      |    - conda:
      |        channels:
      |        - conda-forge
      |        - bioconda
      |        requirements:
      |        - bwa=0.7.17=h2573ce8_7
      |        - bzip2=1.0.8=h0b31af3_2
      |        - ca-certificates=2020.4.5.1=hecc5488_0
      |        - curl=7.69.1=h2d98d24_0
      |        - htslib=1.9=h356306b_9
      |        - krb5=1.17.1=h1752a42_0
      |        - libcurl=7.69.1=hc0b9707_0
      |        - libcxx=10.0.0=h1af66ff_2
      |        - libdeflate=1.3=h01d97ff_0
      |        - libedit=3.1.20170329=hcfe32e1_1001
      |        - libssh2=1.9.0=h39bdce6_2
      |        - ncurses=6.1=h0a44026_1002
      |        - openssl=1.1.1g=h0b31af3_0
      |        - perl=5.26.2=haec8ef5_1006
      |        - samtools=1.9=h8aa4d43_12
      |        - tk=8.6.10=hbbe82c9_0
      |        - xz=5.2.5=h0b31af3_0
      |        - zlib=1.2.11=h0b31af3_1006
      |  hisat2:
      |    group: alignment
      |    steps:
      |    - conda:
      |        channels:
      |        - conda-forge
      |        - bioconda
      |        requirements:
      |        - bzip2=1.0.8=h0b31af3_2
      |        - ca-certificates=2020.4.5.1=hecc5488_0
      |        - certifi=2020.4.5.1=py37hc8dfbb8_0
      |        - curl=7.69.1=h2d98d24_0
      |        - hisat2=2.2.0=py37h6de7cb9_1
      |        - htslib=1.9=h356306b_9
      |        - krb5=1.17.1=h1752a42_0
      |        - libcurl=7.69.1=hc0b9707_0
      |        - libcxx=10.0.0=h1af66ff_2
      |        - libdeflate=1.3=h01d97ff_0
      |        - libedit=3.1.20170329=hcfe32e1_1001
      |        - libffi=3.2.1=h4a8c4bd_1007
      |        - libssh2=1.9.0=h39bdce6_2
      |        - ncurses=6.1=h0a44026_1002
      |        - openssl=1.1.1g=h0b31af3_0
      |        - perl=5.26.2=haec8ef5_1006
      |        - pip=20.1.1=pyh9f0ad1d_0
      |        - python=3.7.6=h90870a6_5_cpython
      |        - python_abi=3.7=1_cp37m
      |        - readline=8.0=hcfe32e1_0
      |        - samtools=1.9=h8aa4d43_12
      |        - setuptools=46.4.0=py37hc8dfbb8_0
      |        - sqlite=3.30.1=h93121df_0
      |        - tk=8.6.10=hbbe82c9_0
      |        - wheel=0.34.2=py_1
      |        - xz=5.2.5=h0b31af3_0
      |        - zlib=1.2.11=h0b31af3_1006
      |  conda-env-builder:
      |    group: conda-env-builder
      |    steps:
      |    - conda:
      |        channels:
      |        - conda-forge
      |        - bioconda
      |        requirements:
      |        - bedtools=2.29.2=h37cfd92_0
      |        - bzip2=1.0.8=h0b31af3_2
      |        - ca-certificates=2020.4.5.1=hecc5488_0
      |        - certifi=2020.4.5.1=py37hc8dfbb8_0
      |        - curl=7.69.1=h2d98d24_0
      |        - krb5=1.17.1=h1752a42_0
      |        - libblas=3.8.0=16_openblas
      |        - libcblas=3.8.0=16_openblas
      |        - libcurl=7.69.1=hc0b9707_0
      |        - libcxx=10.0.0=h1af66ff_2
      |        - libdeflate=1.5=h01d97ff_0
      |        - libedit=3.1.20170329=hcfe32e1_1001
      |        - libffi=3.2.1=h4a8c4bd_1007
      |        - libgfortran=4.0.0=2
      |        - liblapack=3.8.0=16_openblas
      |        - libopenblas=0.3.9=h3d69b6c_0
      |        - libssh2=1.9.0=h39bdce6_2
      |        - llvm-openmp=10.0.0=h28b9765_0
      |        - ncurses=6.1=h0a44026_1002
      |        - numpy=1.18.4=py37h7687784_0
      |        - openssl=1.1.1g=h0b31af3_0
      |        - pandas=1.0.3=py37h94625e5_1
      |        - pip=20.1.1=pyh9f0ad1d_0
      |        - pybedtools=0.8.1=py37h8d6d27b_1
      |        - pysam=0.15.4=py37hdbf7ba2_1
      |        - python=3.7.6=h90870a6_5_cpython
      |        - python-dateutil=2.8.1=py_0
      |        - python_abi=3.7=1_cp37m
      |        - pytz=2020.1=pyh9f0ad1d_0
      |        - readline=8.0=hcfe32e1_0
      |        - setuptools=46.4.0=py37hc8dfbb8_0
      |        - six=1.15.0=pyh9f0ad1d_0
      |        - sqlite=3.30.1=h93121df_0
      |        - tk=8.6.10=hbbe82c9_0
      |        - wheel=0.34.2=py_1
      |        - xz=5.2.5=h0b31af3_0
      |        - yaml=0.1.7=h1de35cc_1001
      |        - zlib=1.2.11=h0b31af3_1006
      |    - pip:
      |        args: []
      |        requirements:
      |        - attrs==19.3.0
      |        - cython==0.29.19
      |        - defopt==5.1.0
      |        - distutils-strtobool==0.1.0
      |        - docutils==0.16
      |        - intervaltree==3.0.2
      |        - mypy-extensions==0.4.3
      |        - pockets==0.9.1
      |        - samwell==0.0.1
      |        - sortedcontainers==2.1.0
      |        - sphinxcontrib-napoleon==0.7
      |        - typing-extensions==3.7.4.2
      |        - typing-inspect==0.6.0
      |    - code:
      |        path: .
      |        commands:
      |        - python setup.py develop""".stripMargin
  }

  "Compile" should "compile a YAML configuration file" in {
    val specPath     = makeTempFile("in.", CondaEnvironmentBuilderTool.FileExtension)
    val compiledPath = makeTempFile("out.", CondaEnvironmentBuilderTool.FileExtension)

    Io.writeLines(path=specPath, lines=Seq(specString))

    val compile = new Compile(config=specPath, output=compiledPath)
    compile.execute()

    Io.readLines(path=compiledPath).mkString("\n") shouldBe compiledString
  }

  Seq(true, false).foreach { compile =>
    val compileString = if (compile) "a pre-compiled" else "and compile a"
    "Assemble" should s"assemble $compileString YAML configuration file" in {
      val compiledPath = makeTempFile("compiled.", CondaEnvironmentBuilderTool.FileExtension)
      val outputDir    = Files.createTempDirectory("output")

      val lines = if (compile) Seq(compiledString) else Seq(specString)
      Io.writeLines(path=compiledPath, lines=lines)

      val assemble = new Assemble(config=compiledPath, output=outputDir, compile=compile)
      assemble.execute()

      val bwaYamlPath  = outputDir.resolve(s"bwa.${CondaEnvironmentBuilderTool.FileExtension}")
      val bwaCondaPath = outputDir.resolve("bwa.build-conda.sh")
      val bwaCodePath  = outputDir.resolve("bwa.build-local.sh")

      Io.readLines(bwaYamlPath).mkString("\n") shouldBe {
            """name: bwa
          |channels:
          |  - conda-forge
          |  - bioconda
          |dependencies:
          |  - bwa=0.7.17
          |  - samtools=1.9""".stripMargin
      }
      Io.readLines(bwaCondaPath).mkString("\n") shouldBe {
        """#/bin/bash
          |
          |# Conda build file for environment: bwa
          |set -xeuo pipefail
          |
          |# Move to the scripts directory
          |pushd $(dirname $0)
          |
          |# Build the conda environment
          |conda env create --force --verbose --quiet --name bwa --file bwa.yml
          |
          |popd
          |""".stripMargin
      }
      Io.readLines(bwaCodePath).mkString("\n") shouldBe {
        """#/bin/bash
          |# Custom code build file for environment: bwa
          |set -xeuo pipefail
          |
          |repo_root=${1:-"."}
          |
          |# No custom commands""".stripMargin
      }

      val condaEnvBuilderCodePath  = outputDir.resolve("conda-env-builder.build-local.sh")
      Io.readLines(condaEnvBuilderCodePath).mkString("\n") shouldBe {
        """#/bin/bash
          |# Custom code build file for environment: conda-env-builder
          |set -xeuo pipefail
          |
          |repo_root=${1:-"."}
          |
          |# Activate conda environment: conda-env-builder
          |set +eu
          |PS1=dummy
          |
          |. $(conda info --base | tail -n 1)/etc/profile.d/conda.sh
          |conda activate conda-env-builder
          |
          |set -eu
          |pushd ${repo_root}
          |python setup.py develop
          |popd
          |
          |""".stripMargin
      }

    }
  }

  "Solve" should s"solve a compiled YAML file" in {
    val compiledPath = makeTempFile("compiled.", CondaEnvironmentBuilderTool.FileExtension)
    val solvedPath = makeTempFile("output.", CondaEnvironmentBuilderTool.FileExtension)

    Io.writeLines(path=compiledPath, lines=Seq(compiledString))

    val solve = new Solve(config=compiledPath, output=solvedPath, dryRun=true)
    solve.execute()

    Io.readLines(path=solvedPath).mkString("\n") shouldBe compiledReformatted // since we skipped the internal solving step
  }

  "Solve" should s"solve a compiled YAML file for a given group" in {
    val compiledPath = makeTempFile("compiled.", CondaEnvironmentBuilderTool.FileExtension)
    val solvedPath = makeTempFile("output.", CondaEnvironmentBuilderTool.FileExtension)

    Io.writeLines(path=compiledPath, lines=Seq(compiledString))

    val solve = new Solve(config=compiledPath, output=solvedPath, groups=Set("alignment"), dryRun=true)
    solve.execute()

    Io.readLines(path=solvedPath).mkString("\n") shouldBe compiledReformattedAlignmentOnly // since we skipped the internal solving step
  }
}
