# CODA-SDK
An Unofficial SDK for CODA Footprint written in Java

[![License](https://img.shields.io/badge/License-BSD%203--Clause-blue.svg)](https://opensource.org/licenses/BSD-3-Clause)

## Developers / Testing
Required:

* [x] Java >= 8
* [x] Maven

```shell
git clone git@github.com:ilanddev/coda-sdk.git
cd coda-sdk
cp footprint.env.example footprint.env
# add your credentials to footprint.env
make test # mvn clean, validate, compile, and test
make install # mvn clean, validate, compile, test, package, verify, and install
```

### Installation
Add the following to your pom.xml:
```xml
<repositories>
    <repository>
        <id>iland-maven-foss</id>
        <url>
            https://us-central1-maven.pkg.dev/iland-software-engineering/iland-maven-foss
        </url>
        <releases>
            <enabled>true</enabled>
        </releases>
        <snapshots>
            <enabled>false</enabled>
        </snapshots>
    </repository>
</repositories>

<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>net.codacloud</groupId>
            <artifactId>footprint</artifactId>
            <version>${footprint.version}</version>
        </dependency>
    </dependencies>
</dependencyManagement>
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