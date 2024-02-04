FROM ubuntu:18.04

RUN sed -i 's/archive.ubuntu.com/mirrors.ustc.edu.cn/g' /etc/apt/sources.list
RUN sed -i 's/security.ubuntu.com/mirrors.ustc.edu.cn/g' /etc/apt/sources.list

RUN apt-get clean && apt-get update && apt-get install -y \
    --no-install-recommends \
    apt-utils \
    software-properties-common \
    make \
    cmake \
    gcc \
    g++ \
    wget
