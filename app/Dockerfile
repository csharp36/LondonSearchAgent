FROM --platform=linux/amd64 eclipse-temurin:21-jre-jammy AS runtime

# Install Node.js (needed for Playwright install-deps) and system deps
RUN apt-get update && apt-get install -y --no-install-recommends \
    nodejs npm \
    && npx playwright install-deps chromium \
    && apt-get remove -y nodejs npm \
    && apt-get autoremove -y \
    && rm -rf /var/lib/apt/lists/* /root/.npm

WORKDIR /app

COPY build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
