package com.londonsearch.infra;

import software.amazon.awscdk.CfnOutput;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.dynamodb.TableV2;
import software.amazon.awscdk.services.ec2.Vpc;
import software.amazon.awscdk.services.ecs.Cluster;
import software.amazon.awscdk.services.ecs.ContainerImage;
import software.amazon.awscdk.services.ecs.patterns.ApplicationLoadBalancedFargateService;
import software.amazon.awscdk.services.ecs.patterns.ApplicationLoadBalancedTaskImageOptions;
import software.amazon.awscdk.services.s3.Bucket;
import software.constructs.Construct;

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
                        .cpu(512)
                        .memoryLimitMiB(1024)
                        .desiredCount(1)
                        .taskImageOptions(ApplicationLoadBalancedTaskImageOptions.builder()
                                .image(ContainerImage.fromAsset("../app"))
                                .containerPort(8080)
                                .environment(Map.of(
                                        "SPRING_PROFILES_ACTIVE", "prod",
                                        "AWS_REGION", props.getEnv().getRegion(),
                                        "PROPERTIES_TABLE", propertiesTable.getTableName(),
                                        "LISTINGS_TABLE", listingsTable.getTableName(),
                                        "SEARCH_CONFIGS_TABLE", searchConfigsTable.getTableName(),
                                        "MONITORED_SITES_TABLE", monitoredSitesTable.getTableName(),
                                        "ALERTS_TABLE", alertsTable.getTableName()
                                ))
                                .build())
                        .publicLoadBalancer(true)
                        .build();

        propertiesTable.grantReadWriteData(service.getTaskDefinition().getTaskRole());
        listingsTable.grantReadWriteData(service.getTaskDefinition().getTaskRole());
        searchConfigsTable.grantReadWriteData(service.getTaskDefinition().getTaskRole());
        monitoredSitesTable.grantReadWriteData(service.getTaskDefinition().getTaskRole());
        alertsTable.grantReadWriteData(service.getTaskDefinition().getTaskRole());
        imagesBucket.grantReadWrite(service.getTaskDefinition().getTaskRole());

        CfnOutput.Builder.create(this, "PortalUrl")
                .value("http://" + service.getLoadBalancer().getLoadBalancerDnsName())
                .description("Portal URL")
                .build();
    }
}
