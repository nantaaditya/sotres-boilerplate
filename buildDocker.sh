#!/bin/sh
#read the version parameter
imgname=core-api-ccl

REGISTRY_HOST=dev-registry.alto-network.com/bcad

mvn clean install -Dmaven.test.skip
version=$(xmllint --xpath "//*[local-name()='project']/*[local-name()='version']/text()" pom.xml)
mv target/core-api-ccl${version}.jar target/app.jar


docker build -f .docker/Dockerfile -t ${REGISTRY_HOST}/${imgname}:${version} .
