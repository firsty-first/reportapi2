package com.epam.edp.demo.entity;

import lombok.*;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@DynamoDbBean
@ToString
public class Waiter {

    private String emailId;
    private String firstName;
    private String lastName;
    private List<String> reservationIds;
    private String locationId;
    private String imageUrl;

    @DynamoDbPartitionKey
    public String getEmailId() {
        return emailId;
    }
}
