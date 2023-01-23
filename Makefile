SHELL := /bin/bash

all: update install

version:
	@echo -n "Current "
	@grep version src/main/resources/swagger.yml | head -n 1 | awk '{ print $2 }'
	@echo -n "Latest "
	@curl -sS https://preview.codacloud.net/api/openapi/ | grep version | head -n 1 | awk '{ print $2 }'

update:
	wget -O src/main/resources/swagger.yml https://preview.codacloud.net/api/openapi/

clean:
	mvn clean

test: clean
	source footprint.env && mvn test

install: clean
	source footprint.env && mvn install

quick: clean
	source footprint.env && mvn install -Dmaven.test.skip=true