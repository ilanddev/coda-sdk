# Modifying Templates

## Documentation
[Using Templates](https://openapi-generator.tech/docs/templating/)
[Feature Request](https://github.com/OpenAPITools/openapi-generator/issues/19969)

Pull latest templates with:
```
# If necessary, install openapi-generator
npm install @openapitools/openapi-generator-cli

./node_modules/.bin/openapi-generator-cli author template -g java --library okhttp-gson
cp -f out/libraries/okhttp-gson/anyof_model.mustache src/main/resources/templates/libraries/okhttp-gson/
cp -f out/libraries/okhttp-gson/oneof_model.mustache src/main/resources/templates/libraries/okhttp-gson/
cp -f out/libraries/okhttp-gson/pojo.mustache src/main/resources/templates/libraries/okhttp-gson/

# Clean up
rm -Rf node_modules/ out/ openapitools.json package-lock.json package.jso
```

Finally, edit *.mustache files to disable #validateJsonElement, e.g.
```java
public static void validateJsonElement(JsonElement jsonElement) throws IOException {
	// do nothing
}
```