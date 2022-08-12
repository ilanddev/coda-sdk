# CODA-SDK
An Unofficial SDK for CODA Footprint written in Java

[![License](https://img.shields.io/badge/License-BSD%203--Clause-blue.svg)](https://opensource.org/licenses/BSD-3-Clause)

## Developers
Required:

* [x] Java >= 11
* [x] Maven

Clone repo then execute:
```shell
cp footprint.env.example footprint.env
# add your credentials to footprint.env
make install
```

### Installation
Add the following to your pom.xml:
```xml
<dependency>
    <groupId>net.codacloud</groupId>
    <artifactId>footprint</artifactId>
    <version>1.0</version>
</dependency>
```

### Example Usage
```java
public final class Sandbox {

	public static void main(String... args) throws ApiException {
		final CodaClient client = createCodaClient().login();

		// do work
		client.listRegistrations();
	}

	private static CodaClient createCodaClient() {
		// TODO: populate from properties file or pull from environment
		final String apiBasePath = "https://foo.codacloud.net/api";
		final String username = "";
		final String password = "";

		final SimpleCodaClient simpleCodaClient =
			new SimpleCodaClient(apiBasePath, username, password);
		final RetryCodaClient retryCodaClient =
			new RetryCodaClient(simpleCodaClient);
		final CachingCodaClient cachingCodaClient =
			new CachingCodaClient(retryCodaClient);

		return cachingCodaClient;
	}

}
```