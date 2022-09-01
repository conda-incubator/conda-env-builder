# conda-env-builder

[![Build Status][github-badge]][github-link]
[![Code Coverage][codecov-badge]][codecov-link]
[![Language][scala-badge]][scala-link]
[![License][license-badge]][license-link]
[![Conda Version][conda-badge]][conda-anaconda-link]

[codecov-badge]:       https://codecov.io/gh/conda-incubator/conda-env-builder/branch/main/graph/badge.svg
[codecov-link]:        https://codecov.io/gh/conda-incubator/conda-env-builder
[license-badge]:       https://img.shields.io/badge/license-MIT-blue.svg
[license-link]:        https://github.com/conda-incubator/conda-env-builder/blob/main/LICENSE
[scala-badge]:         https://img.shields.io/badge/language-scala-c22d40.svg
[scala-link]:          https://www.scala-lang.org/
[scalafmt-badge]:      https://img.shields.io/badge/code_style-scalafmt-c22d40.svg
[github-badge]:        https://github.com/conda-incubator/conda-env-builder/workflows/conda-env-builder%20unit%20tests/badge.svg
[github-link]:         https://github.com/conda-incubator/conda-env-builder/actions?query=workflow%3A%22conda-env-builder+unit+tests%22
[conda-badge]:         https://img.shields.io/conda/vn/conda-forge/conda-env-builder.svg
[conda-anaconda-link]: https://anaconda.org/conda-forge/conda-env-builder


Build and maintain multiple custom conda environments all in once place.

<!---toc start-->
  * [Installation](#installation)
  * [Goals](#goals)
  * [Overview](#overview)
  * [List of tools](#list-of-tools)
  * [Example](#example)
    * [Compile](#compile)
    * [Assemble](#assemble)
    * [Solve](#solve)
    * [Tabulate](#tabulate) 
  * [Why](#why)
  * [Building](#building)
  * [Command line](#command-line)
  * [Include in your project](#include-in-your-project)
  * [Contributing](#contributing)
  * [Authors](#authors)
  * [License](#license)

---

<!---toc end-->

## Installation

[![Conda Recipe](https://img.shields.io/badge/recipe-conda--env--builder-green.svg)](https://anaconda.org/conda-forge/conda-env-builder) 

Install with [`conda`](https://conda.io/projects/conda/en/latest/index.html): `conda install --channel conda-forge conda-env-builder`.



## Goals


* Specify multiple environments in one place
* Reduce duplication with cross-environment defaults and environment inheritance
* Install `pip` packages into your conda environment, as well as custom commands
* Produce easy scripts to build your environments

## Overview

conda-env-builder is a set of command line tools to maintain and build conda environments **in one place**.
A single configuration YAML specifies one or more conda environments to be built.
Environments can inherit from each other to remove duplication, for example common conda package requirements.
A default ("defaults") environment can be used to list default conda and pip package versions, conda channels, and pip
install arguments.

There are three main steps to build an environment:
1. `conda`: the list of channels (`channels`) and conda packages (`requirements`)
2. `pip`: the list of pip install arguments (`args`) and pip packages (`requirements`).  
3. `code`: one or more custom commands to run after the conda environment has been built and activated.

Try to always specify packages via `conda`, and only use `pip` when the package is not available in a `conda` channel.
Use custom code sparingly, for example to install developer or custom version of a package manually.

A brief example is

See the [list of tools](#list-of-tools) for more detail on the tools

## List of tools

For a full list of available tools please see the help message.

Below we highlight a few tools that you may find useful.

* `Compile`: compiles the environments by applying the cross-environment defaults and applying inherited environments.
  * default conda channels, conda and pip package versions, and pip install arguments are supported 
* `Assemble`: builds per-environment conda environment and custom command build scripts.
  * Builds `<env-name>.yaml` for your conda+pip environment specification YAML.
  * Builds `<env-name>.build-conda.sh` to build your conda environment.
  * Builds `<env-name>.build-local.sh` to execute any custom commands after creating the conda envirnment.
* `Solve`: updates the configuration with a full list of packages and versions for the environment.
  * For each environment, builds it (`conda env create`), exports it (`conda env export`), and update the specification
* `Tabulate`: writes the specification in a tabular format. 
  * Conda/pip requirement or custom comand per line, with each line specifying the environment name and group
  
## Example

The following example has four conda environments to build: `samtools`, `bwa`, `hisat2`, and `conda-env-builder`.  It also
has a `defaults ` environment from which conda channels, conda package versions, and pip package versions are applied.
Next, the `bwa` and `hisat2` environments inherit from the `samtools` environment, thus the former two environments will
have `samtools` available, but version `1.9` (not `1.10` as is specified in the defaults) since the `samtools` 
environment specifies the `samtools` version.  Any package requirement without a version will have the version from the
`defaults` environment, for example `bwa` and `hisat2`.  Next, `conda-env-builder` shows how custom code can be 
specified as to execute after the conda environment has been built and activated.  Finally, environments can have the 
`group` attribute which can be used in the `Assemble` or `Solve` tools to subset the environments to build or to solve.

<details>
<summary>`example.yaml`</summary>

```yaml
name: example
environments:
  defaults:
    steps:
      - conda:
          channels:
            - conda-forge
            - bioconda
          requirements:
            - bwa=0.7.17
            - hisat2=2.2.0
            - pybedtools=0.8.1
            - python=3.6.10
            - samtools=1.10
            - yaml=0.1.7
      - pip:
          requirements:
            - defopt==5.1.0
            - samwell==0.0.1
            - distutils-strtobool==0.1.0
  samtools:
    group: alignment
    steps:
      - conda:
          requirements:
            - samtools=1.9
  bwa:
    group: alignment
    inherits:
      - samtools
    steps:
      - conda:
          requirements:
            - bwa
  hisat2:
    group: alignment
    inherits:
      - samtools
    steps:
      - conda:
          requirements:
            - hisat2
  conda-env-builder:
    steps:
      - conda:
          requirements:
            - pybedtools
            - yaml
      - pip:
          requirements:
            - defopt
            - samwell
            - distutils-strtobool
      - code:
          commands:
            - "python setup.py develop"
```

</details> 

### Compile

The `Compile` tool compiles each environment, adding inherited conda channels, conda and pip package requirements, pip
install arguments, and custom commands.  It also applies the default package versions to package requirements without
versions (ex `bwa` or `hisat2=default`).

<details>
<summary>`compiled.yaml`</summary>

```yaml
name: example
environments:
  conda-env-builder:
    group: conda-env-builder
    steps:
    - conda:
        channels:
        - conda-forge
        - bioconda
        requirements:
        - pybedtools=0.8.1
        - yaml=0.1.7
    - pip:
        args: []
        requirements:
        - defopt==5.1.0
        - samwell==0.0.1
        - distutils-strtobool==0.1.0
    - code:
        path: .
        commands:
        - python setup.py develop
  hisat2:
    group: alignment
    steps:
    - conda:
        channels:
        - conda-forge
        - bioconda
        requirements:
        - hisat2=2.2.0
        - samtools=1.9
  bwa:
    group: alignment
    steps:
    - conda:
        channels:
        - conda-forge
        - bioconda
        requirements:
        - bwa=0.7.17
        - samtools=1.9
  samtools:
    group: alignment
    steps:
    - conda:
        channels:
        - conda-forge
        - bioconda
        requirements:
        - samtools=1.9
```

</details> 

### Assemble

The `Assemble` tool will create per-environment build files.  For example, for `bwa`, we have the environment YAML in
`bwa.yaml`, the script to build the conda environment in `bwa.build-conda.sh`, and the script to execute custom commands
in `bwa.build-local.sh`.

<details>

<summary>`bwa.yaml`</summary>

```yaml
name: bwa
channels:
  - conda-forge
  - bioconda
dependencies:
  - bwa=0.7.17
  - samtools=1.9
```

</details>

<details>

<summary>`bwa.build-conda.sh`</summary>

```bash
#/bin/bash
  
# Conda build file for environment: bwa
set -xeuo pipefail

# Move to the scripts directory
pushd $(dirname $0)

# Build the conda environment
conda env create --force --verbose --quiet --name bwa --file bwa.yaml

popd
```

</details>

<details>

<summary>`bwa.build-local.sh`</summary>

```bash
#/bin/bash
# Custom code build file for environment: bwa
set -xeuo pipefail

repo_root=${1:-"."}

# No custom commands
```

</details>

### Solve


The `Solve` tool will create a platform-specific set of requirements for each environment.  Use the `--no-builds` option
to obtain a platform agnostic but less specific set of requirements (no build numbers). Below we see additional packages
requirements which are the dependencies from our original package requirements.

<details>

<summary>`solved.yaml`</summary>

```yaml
name: example
environments:
  samtools:
    group: alignment
    steps:
    - conda:
        channels:
        - conda-forge
        - bioconda
        requirements:
        - bzip2=1.0.8=h0b31af3_2
        - ca-certificates=2020.4.5.1=hecc5488_0
        - curl=7.69.1=h2d98d24_0
        - htslib=1.9=h356306b_9
        - krb5=1.17.1=h1752a42_0
        - libcurl=7.69.1=hc0b9707_0
        - libcxx=10.0.0=h1af66ff_2
        - libdeflate=1.3=h01d97ff_0
        - libedit=3.1.20170329=hcfe32e1_1001
        - libssh2=1.9.0=h39bdce6_2
        - ncurses=6.1=h0a44026_1002
        - openssl=1.1.1g=h0b31af3_0
        - samtools=1.9=h8aa4d43_12
        - tk=8.6.10=hbbe82c9_0
        - xz=5.2.5=h0b31af3_0
        - zlib=1.2.11=h0b31af3_1006
  bwa:
    group: alignment
    steps:
    - conda:
        channels:
        - conda-forge
        - bioconda
        requirements:
        - bwa=0.7.17=h2573ce8_7
        - bzip2=1.0.8=h0b31af3_2
        - ca-certificates=2020.4.5.1=hecc5488_0
        - curl=7.69.1=h2d98d24_0
        - htslib=1.9=h356306b_9
        - krb5=1.17.1=h1752a42_0
        - libcurl=7.69.1=hc0b9707_0
        - libcxx=10.0.0=h1af66ff_2
        - libdeflate=1.3=h01d97ff_0
        - libedit=3.1.20170329=hcfe32e1_1001
        - libssh2=1.9.0=h39bdce6_2
        - ncurses=6.1=h0a44026_1002
        - openssl=1.1.1g=h0b31af3_0
        - perl=5.26.2=haec8ef5_1006
        - samtools=1.9=h8aa4d43_12
        - tk=8.6.10=hbbe82c9_0
        - xz=5.2.5=h0b31af3_0
        - zlib=1.2.11=h0b31af3_1006
  hisat2:
    group: alignment
    steps:
    - conda:
        channels:
        - conda-forge
        - bioconda
        requirements:
        - bzip2=1.0.8=h0b31af3_2
        - ca-certificates=2020.4.5.1=hecc5488_0
        - certifi=2020.4.5.1=py37hc8dfbb8_0
        - curl=7.69.1=h2d98d24_0
        - hisat2=2.2.0=py37h6de7cb9_1
        - htslib=1.9=h356306b_9
        - krb5=1.17.1=h1752a42_0
        - libcurl=7.69.1=hc0b9707_0
        - libcxx=10.0.0=h1af66ff_2
        - libdeflate=1.3=h01d97ff_0
        - libedit=3.1.20170329=hcfe32e1_1001
        - libffi=3.2.1=h4a8c4bd_1007
        - libssh2=1.9.0=h39bdce6_2
        - ncurses=6.1=h0a44026_1002
        - openssl=1.1.1g=h0b31af3_0
        - perl=5.26.2=haec8ef5_1006
        - pip=20.1.1=pyh9f0ad1d_0
        - python=3.7.6=h90870a6_5_cpython
        - python_abi=3.7=1_cp37m
        - readline=8.0=hcfe32e1_0
        - samtools=1.9=h8aa4d43_12
        - setuptools=46.4.0=py37hc8dfbb8_0
        - sqlite=3.30.1=h93121df_0
        - tk=8.6.10=hbbe82c9_0
        - wheel=0.34.2=py_1
        - xz=5.2.5=h0b31af3_0
        - zlib=1.2.11=h0b31af3_1006
  conda-env-builder:
    group: conda-env-builder
    steps:
    - conda:
        channels:
        - conda-forge
        - bioconda
        requirements:
        - bedtools=2.29.2=h37cfd92_0
        - bzip2=1.0.8=h0b31af3_2
        - ca-certificates=2020.4.5.1=hecc5488_0
        - certifi=2020.4.5.1=py37hc8dfbb8_0
        - curl=7.69.1=h2d98d24_0
        - krb5=1.17.1=h1752a42_0
        - libblas=3.8.0=16_openblas
        - libcblas=3.8.0=16_openblas
        - libcurl=7.69.1=hc0b9707_0
        - libcxx=10.0.0=h1af66ff_2
        - libdeflate=1.5=h01d97ff_0
        - libedit=3.1.20170329=hcfe32e1_1001
        - libffi=3.2.1=h4a8c4bd_1007
        - libgfortran=4.0.0=2
        - liblapack=3.8.0=16_openblas
        - libopenblas=0.3.9=h3d69b6c_0
        - libssh2=1.9.0=h39bdce6_2
        - llvm-openmp=10.0.0=h28b9765_0
        - ncurses=6.1=h0a44026_1002
        - numpy=1.18.4=py37h7687784_0
        - openssl=1.1.1g=h0b31af3_0
        - pandas=1.0.3=py37h94625e5_1
        - pip=20.1.1=pyh9f0ad1d_0
        - pybedtools=0.8.1=py37h8d6d27b_1
        - pysam=0.15.4=py37hdbf7ba2_1
        - python=3.7.6=h90870a6_5_cpython
        - python-dateutil=2.8.1=py_0
        - python_abi=3.7=1_cp37m
        - pytz=2020.1=pyh9f0ad1d_0
        - readline=8.0=hcfe32e1_0
        - setuptools=46.4.0=py37hc8dfbb8_0
        - six=1.15.0=pyh9f0ad1d_0
        - sqlite=3.30.1=h93121df_0
        - tk=8.6.10=hbbe82c9_0
        - wheel=0.34.2=py_1
        - xz=5.2.5=h0b31af3_0
        - yaml=0.1.7=h1de35cc_1001
        - zlib=1.2.11=h0b31af3_1006
    - pip:
        args: []
        requirements:
        - attrs==19.3.0
        - cython==0.29.19
        - defopt==5.1.0
        - distutils-strtobool==0.1.0
        - docutils==0.16
        - intervaltree==3.0.2
        - mypy-extensions==0.4.3
        - pockets==0.9.1
        - samwell==0.0.1
        - sortedcontainers==2.1.0
        - sphinxcontrib-napoleon==0.7
        - typing-extensions==3.7.4.2
        - typing-inspect==0.6.0
    - code:
        path: .
        commands:
        - python setup.py develop
```

`Assemble` can be run on this YAML configuration file to also build the environments reproducibly.

</details>

### Tabulate

The Tabulate writes the specification in a tabular format.
The columns are:

1. The environment group
2. The environment name
3. The conda/pip requirement or custom command line
4. The source of (3), either "conda", "pip" or "custom command"

Each requirement for conda and pip steps will be on its own line; similarly for each command for code steps.
Below is the output from the example YAML

<details>

<summary>`example.tab`</summary>

```
group              name               value                       source
alignment          hisat2             samtools=1.9                conda
alignment          hisat2             hisat2=2.2.0                conda
alignment          bwa                samtools=1.9                conda
alignment          bwa                bwa=0.7.17                  conda
alignment          samtools           samtools=1.9                conda
conda-env-builder  conda-env-builder  pybedtools=0.8.1            conda
conda-env-builder  conda-env-builder  yaml=0.1.7                  conda
conda-env-builder  conda-env-builder  defopt==5.1.0               conda
conda-env-builder  conda-env-builder  samwell==0.0.1              conda
conda-env-builder  conda-env-builder  distutils-strtobool==0.1.0  conda
conda-env-builder  conda-env-builder  python setup.py develop     custom command
```

</details>

## Why

Why did I build this tool?  Well, I have a number of repositories with multiple [Snakemake](http://snakemake.readthedocs.io/) pipelines.
Each pipeline may use one or more conda environments.  For example, Picard needs java 8+ but Varscan2 needs java7.  Or the MuTect JAR
needs to be added and registered manually to the conda environment.  I also want to make sure I use the same tool versions across pipelines,
by leveraging inheritance and pipeline-wide defaults.  I can then choose which environments to build into my Docker image for a given pipeline,
assuming one Docker image per pipeline.  And then I can choose which enviroment to use for each rule (task) in my Snakemake` pipeline.


For example, if I assing the same value to the `group` key for the environments for each pipeline, I can run `java -jar jars/conda-env-builder.jar Assembly -g <group-name>` to assemble only the environments I care about.  Then I can build my conda environments at the end of the Docker build process (for the best chance of caching) as follows:

<details>

<summary>`Dockerfile`</summary>

```
#####################################################
# Args required below
#####################################################

# Developer note: we pre-build the environments directory **outside** this Dockerfile so
# that we do not need to re-build the conda environments if nothing has changed.
ARG ENVIRONMENTS_DIRECTORY

#############################################
# Build pipeline conda environments 
#############################################

COPY ${ENVIRONMENTS_DIRECTORY}/*.yml ${ENVIRONMENTS_DIRECTORY}/*.build-conda.sh /tmp/environments/

RUN find /tmp/environments -name '*.build-conda.sh' -print0 | xargs -0 -n 1 -I '{}' bash {} \;

#############################################
# Add local scripts to the conda
#############################################

COPY ${ENVIRONMENTS_DIRECTORY}/*.build-local.sh /tmp/environments/

RUN mkdir /pipeline

WORKDIR /pipeline

# Copy everything, since the build-locals will reference items here
COPY ./ ./

RUN find /tmp/environments -name '*.build-local.sh' -print0 | xargs -0 -n 1 -I '{}' bash {} /pipeline \;
```

</details>

## Building 
### Cloning the Repository

To clone the repository: `git clone https://github.com/conda-incubator/conda-env-builder.git`

### Running the build
conda-env-builder is built using [mill](http://www.lihaoyi.com/mill/).

Use ```mill tools.localJar``` to build an executable jar in ```jars```.

Tests may be run with ```mill tools.test```.

Java SE 8 is required.

## Command line

`java -jar jars/conda-env-builder.jar` to see the commands supported.  Use `java -jar jars/conda-env-builder.jar <command>` to see the help message for a particular command.

## Contributing

Contributions are welcome and encouraged.
We will do our best to provide an initial response to any pull request or issue within one-week.
For urgent matters, please contact us directly.

## Authors

* [Nils Homer](https://github.com/nh13) (maintainer)

## License

`conda-env-builder` is open source software released under the [MIT License](https://github.com/conda-incubator/conda-env-builder/blob/master/LICENSE).
