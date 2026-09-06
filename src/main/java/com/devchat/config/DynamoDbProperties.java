package com.devchat.config;

import org.springframework.boot.context.properties.ConfigurationProperties;


@ConfigurationProperties(prefix = "devchat.dynamodb")
public class DynamoDbProperties {

    private String endpoint = "";
    private String region = "us-east-1";
    private String tablePrefix = "DevChat";
    private boolean autoCreateTables = true;

    public String tableName(String logicalName) {
        return tablePrefix + logicalName;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getTablePrefix() {
        return tablePrefix;
    }

    public void setTablePrefix(String tablePrefix) {
        this.tablePrefix = tablePrefix;
    }

    public boolean isAutoCreateTables() {
        return autoCreateTables;
    }

    public void setAutoCreateTables(boolean autoCreateTables) {
        this.autoCreateTables = autoCreateTables;
    }
}
