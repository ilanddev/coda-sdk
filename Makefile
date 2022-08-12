SHELL := /bin/bash

all: update install

update:
	wget -O src/main/resources/swagger.yml https://preview.codacloud.net/api/openapi/

clean:
	mvn clean

install: clean
	source footprint.env && mvn install