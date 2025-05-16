package com.epam.edp.demo.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@DynamoDbBean
public class Report {

    private String id;
    private String reservationId;
    private String waiterId;
    private String feedbackServiceRating;
    private String feedbackCuisineRating;
    private String reservationDate;
    private String revenue;
    private String locationId;

    @DynamoDbPartitionKey
    public String getId() {
        return id;
    }
}
