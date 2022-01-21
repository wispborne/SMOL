FROM openjdk:16-jdk-slim-buster

# Add build script
COPY build.sh /
RUN chmod +x /build.sh

# Install dependencies
# binutils is needed by java to create the dist package.
RUN apt-get update
RUN apt-get install -y dos2unix git binutils

# Ensure the build script has LF line endings
RUN dos2unix /build.sh

RUN mkdir code
VOLUME code

CMD ["/build.sh"]