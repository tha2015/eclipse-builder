#!/usr/bin/env bash
./mvnw package
java -jar target/eclipse-dropins-builder-1.0-SNAPSHOT.jar src/main/resources/macosx_4_x86_64.xml