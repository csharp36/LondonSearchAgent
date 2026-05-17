package com.londonsearch.infra;

import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.ec2.*;
import software.constructs.Construct;

import java.util.Map;

public class NetworkStack extends Stack {

    private final Vpc vpc;

    public NetworkStack(Construct scope, String id, StackProps props) {
        super(scope, id, props);

        this.vpc = Vpc.Builder.create(this, "Vpc")
                .maxAzs(2)
                .natGateways(0)
                .gatewayEndpoints(Map.of(
                        "S3", GatewayVpcEndpointOptions.builder()
                                .service(GatewayVpcEndpointAwsService.S3)
                                .build(),
                        "DynamoDB", GatewayVpcEndpointOptions.builder()
                                .service(GatewayVpcEndpointAwsService.DYNAMODB)
                                .build()
                ))
                .build();

        vpc.addInterfaceEndpoint("Ecr", InterfaceVpcEndpointOptions.builder()
                .service(InterfaceVpcEndpointAwsService.ECR)
                .build());
        vpc.addInterfaceEndpoint("EcrDocker", InterfaceVpcEndpointOptions.builder()
                .service(InterfaceVpcEndpointAwsService.ECR_DOCKER)
                .build());
        vpc.addInterfaceEndpoint("CloudWatchLogs", InterfaceVpcEndpointOptions.builder()
                .service(InterfaceVpcEndpointAwsService.CLOUDWATCH_LOGS)
                .build());
        // Bedrock calls go via public internet (task has public IP)
    }

    public Vpc getVpc() {
        return vpc;
    }
}
