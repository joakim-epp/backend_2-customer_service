# Railway deployment helper

This Java 21 utility updates the Railway image, starts a deployment, waits for that
deployment to succeed, and checks the public health endpoint. Maven builds it
separately from the application. Its JUnit tests need no Railway credentials or database.

Run from the repository root:

```sh
./mvnw --batch-mode --no-transfer-progress -f scripts/railway-deploy/pom.xml verify
```

Deployment requires Railway CLI 5.49.0 and the environment variables `RAILWAY_TOKEN`,
`RAILWAY_SERVICE_ID`, `RAILWAY_ENVIRONMENT_ID`, `RAILWAY_HEALTH_URL`, and `RAILWAY_IMAGE`.
The image must include an explicit version tag. Start deployment with:

```sh
java -jar scripts/railway-deploy/target/railway-deploy-jar-with-dependencies.jar
```
