FROM buildpack-deps:22.04-scm

ARG BUILD_TYPE=RelWithDebInfo
ARG user=clever_user
ARG home=/home/$user

WORKDIR /tmp

# Build SeaHorn
RUN apt-get update && \
    apt-get install -yqq software-properties-common && \
    apt-get update && \
    apt-get upgrade -yqq && \
    apt-get install -yqq \
        cmake cmake-data unzip \
        zlib1g-dev \
        ninja-build libgraphviz-dev \
        libgmp-dev libmpfr-dev \
        libboost1.74-dev \
        python3-pip \
        less vim \
        gcc-multilib \
        sudo \
        graphviz libgraphviz-dev python3-pygraphviz \
        lcov gcovr rsync \
        clang-14 lldb-14 lld-14 clang-format-14 \
        openjdk-8-jdk && \
    pip3 install lit OutputCheck && \
    pip3 install networkx && \
# Install z3 v4.8.9 since bionic comes with much older version
    curl -LO https://github.com/Z3Prover/z3/releases/download/z3-4.8.9/z3-4.8.9-x64-ubuntu-16.04.zip && \
    unzip z3-4.8.9-x64-ubuntu-16.04.zip && \
    mv z3-4.8.9-x64-ubuntu-16.04 /opt/z3-4.8.9 && \
    rm z3-4.8.9-x64-ubuntu-16.04.zip && \
    curl -L https://yices.csl.sri.com/releases/2.6.1/yices-2.6.1-x86_64-pc-linux-gnu-static-gmp.tar.gz \
        | tar xzf - && \
    cd yices-2.6.1 && \
    ./install-yices /opt/yices-2.6.1 && \
    cd .. && \
    rm -rf yices-2.6.1 && \
    git clone https://github.com/seahorn/seahorn.git /opt/seahorn && \
    cd /opt/seahorn && \
    git checkout 8425d467934972a4823d7647beb76039e80459ca && \
# Re-create the build directory that might have been present in the source tree
    rm -rf build debug release && \
# Remove any third-party dependencies that build process clones
    rm -rf clam sea-dsa llvm-seahorn && \
    mkdir build && \
    cd build && \
    cmake .. -GNinja \
        -DCMAKE_BUILD_TYPE=${BUILD_TYPE} \
        -DZ3_ROOT=/opt/z3-4.8.9 \
        -DYICES2_HOME=/opt/yices-2.6.1 \
        -DCMAKE_INSTALL_PREFIX=run \
        -DCMAKE_CXX_COMPILER=clang++-14 \
        -DCMAKE_C_COMPILER=clang-14 \
        -DSEA_ENABLE_LLD=ON \
        -DCPACK_GENERATOR="TGZ" \
        -DCMAKE_EXPORT_COMPILE_COMMANDS=ON && \
    cmake --build . --target extra  && cmake .. && \
    cmake --build . --target crab  && cmake .. && \
    cmake --build . --target install && \
    cmake --build . --target units_z3 && \
    cmake --build . --target units_yices2 && \
    cmake --build . --target test_type_checker && \
    cmake --build . --target test_hex_dump && \
    cmake --build . --target package && \
    units/units_z3 && \
    units/units_yices2 && \
# Add User
    useradd -m -d $home $user
ENV PATH "$PATH::/opt/seahorn/build/run/bin"

USER $user
WORKDIR $home

# Install Scala with cs setup
RUN curl -fL https://github.com/coursier/coursier/releases/latest/download/cs-x86_64-pc-linux.gz \
        | gzip -d > cs && \
    chmod +x cs && \
    ./cs setup -y && \
    rm cs && \
    PATH=$PATH:$home/.local/share/coursier/bin && \
# Download Ubuntu Headers for TypeChef-BusyboxAnalysis and TypeChef-LinuxAnalysis
    mkdir headers && \
    curl -L https://github.com/stepwise-alan/TypeChef-Ubuntu-Headers/releases/download/v0.0.1/includes-ubuntu.tar.bz2 \
        | tar xjf - -C headers && \
# Install TypeChef
    git clone https://github.com/stepwise-alan/TypeChef.git && \
    cd TypeChef && \
    sbt publishLocal mkrun && \
    cd .. && \
# Download TypeChef-BusyboxAnalysis
    git clone https://github.com/stepwise-alan/TypeChef-BusyboxAnalysis.git && \
    cd TypeChef-BusyboxAnalysis && \
    sbt mkrun && \
    mkdir -p systems/ubuntu && \
    ln -s $home/headers/usr $(pwd)/systems/ubuntu && \
    curl -L https://busybox.net/downloads/busybox-1.18.5.tar.bz2 \
        | tar xjf - && \
    ./prepareBusybox.sh && \
    cd $home && \
# Download TypeChef-LinuxAnalysis
    git clone https://github.com/stepwise-alan/TypeChef-LinuxAnalysis.git && \
    cd TypeChef-LinuxAnalysis && \
    sbt mkrun && \
    mkdir -p systems/ubuntu && \
    ln -s $home/headers/usr $(pwd)/systems/ubuntu && \
    cd linux26333 && \
    curl -L https://www.kernel.org/pub/linux/kernel/v2.6/linux-2.6.33.3.tar.bz2 \
        | tar xjf - && \
    ln -s linux-2.6.33.3 linux && \
    ./prepareLinuxTestCase.sh
ENV PATH "$PATH:$home/.local/share/coursier/bin"

COPY --chown=$user . clever-v
WORKDIR $home/clever-v
RUN pip3 install -r requirements.txt && \
    sbt compile
ENTRYPOINT ["sbt"]
CMD ["--version"]
