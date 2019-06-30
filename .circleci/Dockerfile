FROM ubuntu:19.04

RUN apt-get update
RUN apt-get -y install git openjdk-12-jdk

RUN useradd -ms /bin/bash elastic
USER elastic

ENV JAVA_HOME="/usr/lib/jvm/java-12-openjdk-amd64"

RUN java -version
RUN which java
RUN ls $JAVA_HOME

CMD /bin/sh
