package com.epam.edp.demo.config;

import com.epam.edp.demo.entity.Report;
import com.epam.edp.demo.entity.Waiter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.auth.StsAssumeRoleCredentialsProvider;

import static software.amazon.awssdk.enhanced.dynamodb.TableSchema.fromBean;

@Configuration
public class AWSConfig {

    @Value("${aws.access.key.id}")
    private String accessKeyId;

    @Value("${aws.secret.access.key}")
    private String secretAccessKey;

    @Value("${aws.session.token}")
    private String sessionToken;

    @Value("${aws.region}")
    private String awsRegion;

    @Value("${aws.roleArn}")
    private String roleArn;

    @Value("${dynamodb.tables.waiter}")
    private String waiterTableName;

    @Value("${dynamodb.tables.report}")
    private String reportTableName;


    @Bean
    public DynamoDbClient dynamoDbClient() {
        // Step 1: Use initial temporary session credentials
        AwsSessionCredentials sessionCredentials = AwsSessionCredentials.create(
                accessKeyId,
                secretAccessKey,
                sessionToken
        );

        // Step 2: Create STS client using the initial session credentials
        StsClient stsClient = StsClient.builder()
                .region(Region.of(awsRegion))
                .credentialsProvider(StaticCredentialsProvider.create(sessionCredentials))
                .build();

        // Step 3: Assume Role credentials provider
        StsAssumeRoleCredentialsProvider assumeRoleCredentialsProvider = StsAssumeRoleCredentialsProvider.builder()
                .refreshRequest(r -> r.roleArn(roleArn).roleSessionName("assume-role-session"))
                .stsClient(stsClient)
                .build();

        // Step 4: Create DynamoDB client using assumed credentials
        return DynamoDbClient.builder()
                .region(Region.of(awsRegion))
                .credentialsProvider(assumeRoleCredentialsProvider)
                .build();
    }

    @Bean
    public DynamoDbEnhancedClient dynamoDbEnhancedClient(DynamoDbClient dynamoDbClient) {
        return DynamoDbEnhancedClient.builder()
                .dynamoDbClient(dynamoDbClient)
                .build();
    }

    @Bean
    public DynamoDbTable<Waiter> waiterTable(DynamoDbEnhancedClient enhancedClient) {
        return enhancedClient.table(waiterTableName, fromBean(Waiter.class));
    }

    @Bean
    public DynamoDbTable<Report> reportTable(DynamoDbEnhancedClient enhancedClient) {
        return enhancedClient.table(reportTableName, fromBean(Report.class));
    }

    @Bean
    public SesClient sesClient() {
        AwsSessionCredentials sessionCredentials = AwsSessionCredentials.create(
                accessKeyId,
                secretAccessKey,
                sessionToken
        );

        StsClient stsClient = StsClient.builder()
                .region(Region.of(awsRegion))
                .credentialsProvider(StaticCredentialsProvider.create(sessionCredentials))
                .build();

        StsAssumeRoleCredentialsProvider assumeRoleCredentialsProvider = StsAssumeRoleCredentialsProvider.builder()
                .refreshRequest(r -> r.roleArn(roleArn).roleSessionName("ses-assume-role-session"))
                .stsClient(stsClient)
                .build();

        return SesClient.builder()
                .region(Region.of(awsRegion))
                .credentialsProvider(assumeRoleCredentialsProvider)
                .build();
    }

}
