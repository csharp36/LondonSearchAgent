plugins {
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
}

dependencyManagement {
    imports {
        mavenBom("software.amazon.awssdk:bom:${libs.versions.aws.sdk.get()}")
    }
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.thymeleaf.extras:thymeleaf-extras-springsecurity6")
    implementation(libs.htmx.spring.boot)
    implementation(libs.aws.dynamodb.enhanced)
    implementation(libs.aws.url.connection.client)
    implementation(libs.aws.s3)
    implementation(libs.jsoup)
    implementation(libs.aws.bedrockruntime)
    implementation(libs.aws.ses)

    developmentOnly("org.springframework.boot:spring-boot-devtools")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
}

tasks.test {
    useJUnitPlatform()
}
