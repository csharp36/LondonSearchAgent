package com.londonsearch.infra;

import software.amazon.awscdk.App;
import software.amazon.awscdk.Environment;
import software.amazon.awscdk.StackProps;

public class InfraApp {

    public static void main(String[] args) {
        App app = new App();

        Environment env = Environment.builder()
                .account(System.getenv("CDK_DEFAULT_ACCOUNT"))
                .region(System.getenv("CDK_DEFAULT_REGION"))
                .build();

        StackProps stackProps = StackProps.builder().env(env).build();

        NetworkStack network = new NetworkStack(app, "LondonSearch-Network", stackProps);
        DataStack data = new DataStack(app, "LondonSearch-Data", stackProps);
        PortalStack portal = new PortalStack(app, "LondonSearch-Portal", stackProps,
                network.getVpc(), data.getPropertiesTable(), data.getListingsTable(),
                data.getSearchConfigsTable(), data.getMonitoredSitesTable(),
                data.getImagesBucket());

        app.synth();
    }
}
