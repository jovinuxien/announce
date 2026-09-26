# Admin edge: build the jar first (with the SPA):  mvn -pl gateway-admin -P webapp -DskipTests package
# then:  docker build -t announce/gateway-admin gateway-admin
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY target/gateway-admin-*.jar app.jar
ENV SPRING_PROFILES_ACTIVE=prod \
    ANNOUNCE_R2DBC_URL=r2dbc:mysql://mysql:3306/announce_reactive_db \
    ANNOUNCE_EUREKA_ENABLED=false
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
