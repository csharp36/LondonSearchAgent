package com.londonsearch.infra;

import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.events.Rule;
import software.amazon.awscdk.services.events.Schedule;
import software.amazon.awscdk.services.events.CronOptions;
import software.constructs.Construct;

public class ScheduleStack extends Stack {

    public ScheduleStack(Construct scope, String id, StackProps props) {
        super(scope, id, props);

        Rule.Builder.create(this, "AgentSchedule-0600")
                .schedule(Schedule.cron(CronOptions.builder().hour("6").minute("0").build()))
                .description("LondonSearchAgent pipeline run - 06:00 UTC")
                .build();

        Rule.Builder.create(this, "AgentSchedule-1200")
                .schedule(Schedule.cron(CronOptions.builder().hour("12").minute("0").build()))
                .description("LondonSearchAgent pipeline run - 12:00 UTC")
                .build();

        Rule.Builder.create(this, "AgentSchedule-1800")
                .schedule(Schedule.cron(CronOptions.builder().hour("18").minute("0").build()))
                .description("LondonSearchAgent pipeline run - 18:00 UTC")
                .build();

        Rule.Builder.create(this, "AgentSchedule-0000")
                .schedule(Schedule.cron(CronOptions.builder().hour("0").minute("0").build()))
                .description("LondonSearchAgent pipeline run - 00:00 UTC")
                .build();
    }
}
