FROM --platform=linux/amd64 eclipse-temurin:21-jre-jammy AS runtime

# Install system dependencies required by Playwright's bundled Chromium
RUN apt-get update && apt-get install -y --no-install-recommends \
    fonts-freefont-ttf \
    libnss3 \
    libatk1.0-0 \
    libatk-bridge2.0-0 \
    libcups2 \
    libdrm2 \
    libxkbcommon0 \
    libxcomposite1 \
    libxdamage1 \
    libxrandr2 \
    libgbm1 \
    libpango-1.0-0 \
    libcairo2 \
    libasound2 \
    libxshmfence1 \
    libdbus-1-3 \
    libx11-xcb1 \
    && rm -rf /var/lib/apt/lists/*

# Let Playwright download and manage its own Chromium (compatible version)
# System chromium-browser on Ubuntu is a snap wrapper that doesn't work in Docker

WORKDIR /app

COPY build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
