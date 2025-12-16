+ Monorepo should not have a `src/main/java` only the `pom.xml` is necesary

# Before running the microservices (DEV_MODE)

+ For those which use `gRPC` run via `Dockerfile` or `cmd`: `project-service mvn compile`
+ Prod environment already compiles the project so it does not need the step before