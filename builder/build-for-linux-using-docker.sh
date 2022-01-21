#!/bin/bash

cd docker-builder
rm -rf code
docker build -t smol-builder:1.0 .
docker run -v $(pwd)/code:/code smol-builder:1.0
cd ..