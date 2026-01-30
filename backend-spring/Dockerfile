# Étape 1 : Construction du JAR 
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
# Compiler l'application (ignorer les tests pour accélérer la construction)
RUN mvn clean package -DskipTests

#  Étape 2 : Exécution du JAR 
FROM eclipse-temurin:17-jre
WORKDIR /app
# Copier le JAR généré lors de l'étape précédente
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]