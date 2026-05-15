plugins {
    application
}

application {
    mainClass = "com.londonsearch.infra.InfraApp"
}

dependencies {
    implementation(libs.aws.cdk.lib)
    implementation(libs.constructs)
}
