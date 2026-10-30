# TasteTrail – container image for cloud hosting (Render, Railway, any Docker host)
# Stage 1: compile the Java sources
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY lib lib
COPY src src
RUN mkdir -p out && javac -encoding UTF-8 -d out -cp "lib/*" $(find src/main/java -name "*.java")

# Stage 2: small runtime image
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/out out
COPY lib lib
COPY web web
ENV PORT=8080
EXPOSE 8080
# The database is created and filled with demo data automatically on first start
CMD ["java", "-cp", "out:lib/*", "com.tastetrail.Main"]
