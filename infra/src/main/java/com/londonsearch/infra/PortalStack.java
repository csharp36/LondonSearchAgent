package com.londonsearch.infra;

import software.amazon.awscdk.CfnOutput;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.dynamodb.TableV2;
import software.amazon.awscdk.services.ec2.SubnetSelection;
import software.amazon.awscdk.services.ec2.SubnetType;
import software.amazon.awscdk.services.ec2.Vpc;
import software.amazon.awscdk.services.ecs.Cluster;
import software.amazon.awscdk.services.ecs.ContainerImage;
import software.amazon.awscdk.services.ecs.patterns.ApplicationLoadBalancedFargateService;
import software.amazon.awscdk.services.ecs.patterns.ApplicationLoadBalancedTaskImageOptions;
import software.amazon.awscdk.services.iam.PolicyStatement;
import software.amazon.awscdk.services.s3.Bucket;
import software.constructs.Construct;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PortalStack extends Stack {

    public PortalStack(Construct scope, String id, StackProps props,
                       Vpc vpc, TableV2 propertiesTable, TableV2 listingsTable,
                       TableV2 searchConfigsTable, TableV2 monitoredSitesTable,
                       Bucket imagesBucket, TableV2 alertsTable) {
        super(scope, id, props);

        Cluster cluster = Cluster.Builder.create(this, "Cluster")
                .vpc(vpc)
                .build();

        ApplicationLoadBalancedFargateService service =
                ApplicationLoadBalancedFargateService.Builder.create(this, "Portal")
                        .cluster(cluster)
                        .cpu(1024)
                        .memoryLimitMiB(2048)
                        .desiredCount(1)
                        .taskImageOptions(ApplicationLoadBalancedTaskImageOptions.builder()
                                .image(ContainerImage.fromAsset("../app"))
                                .containerPort(8080)
                                .environment(new HashMap<>(Map.of(
                                        "SPRING_PROFILES_ACTIVE", "prod",
                                        "AWS_REGION", props.getEnv().getRegion(),
                                        "EXTRACTOR_TYPE", "bedrock",
                                        "BEDROCK_REGION", "us-east-1",
                                        "APP_PASSWORD", System.getenv("LONDONSEARCH_PASSWORD") != null
                                                ? System.getenv("LONDONSEARCH_PASSWORD") : "changeme",
                                        "PROPERTIES_TABLE", propertiesTable.getTableName(),
                                        "LISTINGS_TABLE", listingsTable.getTableName(),
                                        "SEARCH_CONFIGS_TABLE", searchConfigsTable.getTableName(),
                                        "MONITORED_SITES_TABLE", monitoredSitesTable.getTableName(),
                                        "ALERTS_TABLE", alertsTable.getTableName()
                                )))
                                .build())
                        .publicLoadBalancer(true)
                        .assignPublicIp(true)
                        .taskSubnets(SubnetSelection.builder()
                                .subnetType(SubnetType.PUBLIC)
                                .build())
                        .build();

        // Increase task memory for Playwright/Chromium
        // (default 512/1024 may not be enough with headless browser)

        // Health check on /agent/ping (public endpoint, returns 200)
        service.getTargetGroup().configureHealthCheck(
                software.amazon.awscdk.services.elasticloadbalancingv2.HealthCheck.builder()
                        .path("/agent/ping")
                        .build());

        // Grant DynamoDB access
        propertiesTable.grantReadWriteData(service.getTaskDefinition().getTaskRole());
        listingsTable.grantReadWriteData(service.getTaskDefinition().getTaskRole());
        searchConfigsTable.grantReadWriteData(service.getTaskDefinition().getTaskRole());
        monitoredSitesTable.grantReadWriteData(service.getTaskDefinition().getTaskRole());
        alertsTable.grantReadWriteData(service.getTaskDefinition().getTaskRole());
        imagesBucket.grantReadWrite(service.getTaskDefinition().getTaskRole());

        // Grant Bedrock access for Nova Micro (extraction) and Claude Sonnet (AI assessment)
        service.getTaskDefinition().getTaskRole().addToPrincipalPolicy(PolicyStatement.Builder.create()
                .actions(List.of("bedrock:InvokeModel"))
                .resources(List.of(
                        "arn:aws:bedrock:us-east-1::foundation-model/*",
                        "arn:aws:bedrock:us-east-1:710703498172:inference-profile/*"
                ))
                .build());

        CfnOutput.Builder.create(this, "PortalUrl")
                .value("http://" + service.getLoadBalancer().getLoadBalancerDnsName())
                .description("Portal URL")
                .build();
    }
}
