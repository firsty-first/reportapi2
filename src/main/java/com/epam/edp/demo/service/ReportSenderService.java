package com.epam.edp.demo.service;

import com.epam.edp.demo.entity.Report;
import com.epam.edp.demo.entity.Waiter;
import jakarta.mail.Message;
import jakarta.mail.Multipart;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Expression;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.enhanced.dynamodb.model.ScanEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.RawMessage;
import software.amazon.awssdk.services.ses.model.SendRawEmailRequest;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;


@Service
@RequiredArgsConstructor
public class ReportSenderService {

    private final SesClient sesClient;
    private final DynamoDbTable<Report> reportDynamoDbTable;
    private final DynamoDbTable<Waiter> waiterDynamoDbTable;
    private final TemplateEngine templateEngine;

    @Value("${receiver.email}")
    private String TO_EMAIL;

    @Value("${sender.email}")
    private String FROM_EMAIL;


    /**
     * Generates a CSV report with aggregated metrics and delta calculations.
     *
     * @return CSV as a byte array.
     * @throws Exception if something goes wrong.
     */
    /**
     * Main method to be called externally to generate and send the delta report.
     */
    public void sendWeeklyDeltaReport() {
        try {
            // 1) Generate location CSV
            byte[] locationCsvBytes = generateLocationDeltaCsv();
            // 2) Generate waiter CSV
            byte[] waiterCsvBytes = generateWaiterDeltaCsv();

            // 3) Send both attachments in one email
            sendTwoCsvReportsViaSes(sesClient, locationCsvBytes, waiterCsvBytes, FROM_EMAIL, TO_EMAIL);

            System.out.println("CSV email (2 attachments) sent successfully ");
        } catch (Exception e) {
            System.out.println("Error occurred while sending weekly delta report: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private byte[] generateLocationDeltaCsv() throws Exception {

        // 1. Calculate date ranges using LocalDate.

        LocalDate today = LocalDate.now();

        // Current 7-day window: from (today - 7 days) to today.

        LocalDate currentPeriodEnd = today;

        LocalDate currentPeriodStart = currentPeriodEnd.minusDays(7);

        // Previous 7-day window: from (currentPeriodStart - 7 days) to currentPeriodStart.

        LocalDate previousPeriodEnd = currentPeriodStart;

        LocalDate previousPeriodStart = previousPeriodEnd.minusDays(7);

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        String currentStartStr = currentPeriodStart.format(fmt);

        String currentEndStr = currentPeriodEnd.format(fmt);

        String prevStartStr = previousPeriodStart.format(fmt);

        String prevEndStr = previousPeriodEnd.format(fmt);

        // 2. Fetch reports for each period from DynamoDB.

        List<Report> currentPeriodReports = getReportsInDateRange(reportDynamoDbTable, currentStartStr, currentEndStr);

        List<Report> previousPeriodReports = getReportsInDateRange(reportDynamoDbTable, prevStartStr, prevEndStr);

        // 3. Aggregate metrics per location.

        Map<String, LocationMetrics> currMetricsMap = new HashMap<>();

        for (Report r : currentPeriodReports) {

            String location = r.getLocationId();

            LocationMetrics metrics = currMetricsMap.computeIfAbsent(location, k -> new LocationMetrics());

            metrics.incrementOrders();

            if (r.getFeedbackCuisineRating() != null && !r.getFeedbackCuisineRating().isEmpty()) {

                try {

                    // skip rating with value 0

                    if (Double.parseDouble(r.getFeedbackCuisineRating()) != 0.0) {

                        metrics.addCuisineRating(Double.parseDouble(r.getFeedbackCuisineRating()));

                    }

                } catch (NumberFormatException e) {

                    System.out.println("Exception while parsing ratings");

                    // Log or ignore if feedback isn't a valid number.

                }

            }

            if (r.getRevenue() != null && !r.getRevenue().isEmpty()) {

                try {

                    metrics.addRevenue(Double.parseDouble(r.getRevenue()));

                } catch (NumberFormatException e) {

                    System.out.println("Exception while parsing revenue");

                    // Log or ignore if revenue isn't a valid number.

                }

            }

        }

        Map<String, LocationMetrics> prevMetricsMap = new HashMap<>();

        for (Report r : previousPeriodReports) {

            String location = r.getLocationId();

            LocationMetrics metrics = prevMetricsMap.computeIfAbsent(location, k -> new LocationMetrics());

            metrics.incrementOrders();

            if (r.getFeedbackCuisineRating() != null && !r.getFeedbackCuisineRating().isEmpty()) {

                try {

                    // skip rating with value 0

                    if (Double.parseDouble(r.getFeedbackCuisineRating()) != 0.0) {

                        metrics.addCuisineRating(Double.parseDouble(r.getFeedbackCuisineRating()));

                    }

                } catch (NumberFormatException e) {

                    System.out.println("Exception while parsing ratings");

                    // Log or ignore if feedback isn't a valid number.

                }

            }

            if (r.getRevenue() != null && !r.getRevenue().isEmpty()) {

                try {

                    metrics.addRevenue(Double.parseDouble(r.getRevenue()));

                } catch (NumberFormatException e) {

                    System.out.println("Exception while parsing revenue");

                    // Log or ignore if revenue isn't a valid number.

                }

            }

        }

        // 4. Build CSV String with headers.

        StringBuilder sb = new StringBuilder();

//        sb.append("Location,Report Period Start,Report Period End,Orders Processed,Delta Orders %,Avg Cuisine Feedback,Delta Cuisine Feedback,Revenue,Delta Revenue %\n");

        sb.append("Location,Report period start,Report period end,Orders processed within location,Delta of orders processed within location to previous period (in %),Avg cuisine feedback by com.restaurant location (1 to 5),Minimum cuisine feedback by com.restaurant location (1 to 5),Delta of average cuisine feedback by com.restaurant location to previous period (in %),Revenue for orders within reported period (USD),Delta of revenue for orders to previous periods (in %) \n");

        // Use a DecimalFormat for percentage and averages.

        DecimalFormat df = new DecimalFormat("#.##");

        Set<String> allLocations = new HashSet<>();

        allLocations.addAll(currMetricsMap.keySet());

        allLocations.addAll(prevMetricsMap.keySet());

        for (String location : allLocations) {

            LocationMetrics curr = currMetricsMap.getOrDefault(location, new LocationMetrics());

            LocationMetrics prev = prevMetricsMap.getOrDefault(location, new LocationMetrics());

            int currOrders = curr.getTotalOrders();

            int prevOrders = prev.getTotalOrders();

            double orderDeltaPct = (prevOrders == 0) ? 0 : ((currOrders - prevOrders) * 100.0 / prevOrders);

            double currCuisineAvg = curr.getAverageCuisineRating();

            double prevCuisineAvg = prev.getAverageCuisineRating();

            double cuisineDelta = currCuisineAvg - prevCuisineAvg;

            double currRevenue = curr.getTotalRevenue();

            double prevRevenue = prev.getTotalRevenue();

            double revenueDeltaPct = (prevRevenue == 0) ? 0 : ((currRevenue - prevRevenue) * 100.0 / prevRevenue);

            double currMinCuisine = curr.getMinCuisineRating();

            sb.append(location).append(",")

                    .append(currentStartStr).append(",")

                    .append(currentEndStr).append(",")

                    .append(currOrders).append(",")

                    .append(df.format(orderDeltaPct)).append("%,")

                    .append(df.format(currCuisineAvg)).append(",")

                    .append(df.format(currMinCuisine)).append(",")

                    .append(df.format(cuisineDelta)).append("%,")

                    .append(df.format(currRevenue)).append(",")

                    .append(df.format(revenueDeltaPct)).append("%\n");

        }

        return sb.toString().getBytes(StandardCharsets.UTF_8);

    }

    /**

     * Queries the Report DynamoDB table for entries with reservationDate between startDate and endDate.

     *

     * @param table     DynamoDbTable for Report.

     * @param startDate start date (inclusive), formatted as "yyyy-MM-dd".

     * @param endDate   end date (inclusive), formatted as "yyyy-MM-dd".

     * @return List of Report matching the date range.

     */

    private List<Report> getReportsInDateRange(DynamoDbTable<Report> table, String startDate, String endDate) {

        Expression filterExpression = Expression.builder()

                .expression("reservationDate BETWEEN :start AND :end")

                .putExpressionValue(":start", AttributeValue.builder().s(startDate).build())

                .putExpressionValue(":end", AttributeValue.builder().s(endDate).build())

                .build();

        ScanEnhancedRequest scanRequest = ScanEnhancedRequest.builder()

                .filterExpression(filterExpression)

                .build();

        List<Report> result = new ArrayList<>();

        for (Page<Report> page : table.scan(scanRequest)) {

            result.addAll(page.items());

        }

        return result;

    }

    private void sendTwoCsvReportsViaSes(SesClient sesClient, byte[] locationCsv, byte[] waiterCsv, String fromEmail, String toEmail) throws Exception {


    }


    /**

     * Inner class for aggregating location metrics.

     */

    private static class LocationMetrics {

        private int totalOrders = 0;

        private double totalCuisineRatings = 0.0;

        private int cuisineRatingsCount = 0;

        private double totalRevenue = 0.0;

        private double minCuisineRating = Double.MAX_VALUE;

        public void incrementOrders() {

            totalOrders++;

        }

        public void addCuisineRating(double rating) {

            totalCuisineRatings += rating;

            cuisineRatingsCount++;

            if (rating < minCuisineRating) {

                minCuisineRating = rating;

            }

        }

        public double getMinCuisineRating() {

            return (cuisineRatingsCount == 0) ? 0.0 : minCuisineRating;

        }

        public double getAverageCuisineRating() {

            return (cuisineRatingsCount == 0) ? 0.0 : totalCuisineRatings / cuisineRatingsCount;

        }

        public void addRevenue(double revenue) {

            totalRevenue += revenue;

        }

        public int getTotalOrders() {

            return totalOrders;

        }

        public double getTotalRevenue() {

            return totalRevenue;

        }

    }

    /**

     * Inner class for aggregating waiter metrics.

     * Similar idea to LocationMetrics, but for each waiter.

     */

    private static class WaiterMetrics {

        private int totalOrders = 0;

        private double totalServiceRatings = 0.0;

        private int serviceRatingsCount = 0;

        private double minServiceRating = Double.MAX_VALUE;

        public void incrementOrders() {

            totalOrders++;

        }

        /**

         * For each order, the waiter works 105 minutes.

         * So hours worked = (totalOrders * 105) / 60.

         */

        public double getHoursWorked() {

            // 105 min per order => convert to hours

            return (totalOrders * 105.0) / 60.0;

        }

        public void addServiceRating(double rating) {

            totalServiceRatings += rating;

            serviceRatingsCount++;

            if (rating < minServiceRating) {

                minServiceRating = rating;

            }

        }

        /**

         * Returns avg service rating (1-5 scale),

         * or 0 if no ratings yet.

         */

        public double getAverageServiceRating() {

            return (serviceRatingsCount == 0) ? 0.0 : (totalServiceRatings / serviceRatingsCount);

        }

        /**

         * Returns the min service rating encountered,

         * or 0 if no ratings at all.

         */

        public double getMinServiceRating() {

            return (serviceRatingsCount == 0) ? 0.0 : minServiceRating;

        }

        public int getTotalOrders() {

            return totalOrders;

        }

    }

    /**

     * Generates a second CSV report focused on Waiter-specific metrics:

     * - total orders by waiter

     * - hours worked

     * - average service rating

     * - min service rating

     * - deltas comparing current vs previous 7-day period

     */

    private byte[] generateWaiterDeltaCsv() throws Exception {

        // 1) Figure out date ranges (reuse the same logic as generateDeltaCsv)

        LocalDate today = LocalDate.now();

        LocalDate currentPeriodEnd = today;

        LocalDate currentPeriodStart = currentPeriodEnd.minusDays(7);

        LocalDate previousPeriodEnd = currentPeriodStart;

        LocalDate previousPeriodStart = previousPeriodEnd.minusDays(7);

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        String currentStartStr = currentPeriodStart.format(fmt);

        String currentEndStr = currentPeriodEnd.format(fmt);

        String prevStartStr = previousPeriodStart.format(fmt);

        String prevEndStr = previousPeriodEnd.format(fmt);

        // 2) Get reports for current and previous periods

        List<Report> currentPeriodReports = getReportsInDateRange(reportDynamoDbTable, currentStartStr, currentEndStr);

        List<Report> previousPeriodReports = getReportsInDateRange(reportDynamoDbTable, prevStartStr, prevEndStr);

        // 3) Build aggregator maps keyed by WaiterId

        Map<String, WaiterMetrics> currWaiterMap = new HashMap<>();

        for (Report r : currentPeriodReports) {

            String waiterId = r.getWaiterId();

            if (waiterId == null || waiterId.isEmpty()) continue; // skip if no waiter ID

            WaiterMetrics metrics = currWaiterMap.computeIfAbsent(waiterId, k -> new WaiterMetrics());

            metrics.incrementOrders();

            // parse feedbackServiceRating if you store it as string (similar to feedbackCuisineRating)

            if (r.getFeedbackServiceRating() != null && !r.getFeedbackServiceRating().isEmpty()) {

                try {

                    double val = Double.parseDouble(r.getFeedbackServiceRating());

                    if (val != 0.0) {

                        metrics.addServiceRating(val);

                    }

                } catch (NumberFormatException e) {

                    System.out.println("Error parsing service rating");

                    // handle parse error

                }

            }

        }

        Map<String, WaiterMetrics> prevWaiterMap = new HashMap<>();

        for (Report r : previousPeriodReports) {

            String waiterId = r.getWaiterId();

            if (waiterId == null || waiterId.isEmpty()) continue;

            WaiterMetrics metrics = prevWaiterMap.computeIfAbsent(waiterId, k -> new WaiterMetrics());

            metrics.incrementOrders();

            if (r.getFeedbackServiceRating() != null && !r.getFeedbackServiceRating().isEmpty()) {

                try {

                    double val = Double.parseDouble(r.getFeedbackServiceRating());

                    if (val != 0.0) {

                        metrics.addServiceRating(val);

                    }

                } catch (NumberFormatException e) {

                    System.out.println("Error parsing revenue rating ");

                    // handle parse error

                }

            }

        }

        // 4) Build CSV

        StringBuilder sb = new StringBuilder();

//        sb.append("WaiterID,Report Period Start,Report Period End,Orders Processed,Delta Orders %,Hours Worked,Delta Hours Worked %,Avg Service Rating,Delta Avg Service Rating,Min Service Rating,Delta Min Service Rating\n");

        sb.append("Location,Waiter,Waiter's e-mail,Report period start,Report period end,Waiter working hours,Waiter orders processed,Delta of waiter orders processed to previous period (in %),Average service feedback waiter (1 to 5),Minimum service feedback waiter (1 to 5),Delta of average service feedback waiter to previous period (in %)\n");

        DecimalFormat df = new DecimalFormat("#.##");

        // union of all waiters found

        Set<String> allWaiters = new HashSet<>();

        allWaiters.addAll(currWaiterMap.keySet());

        allWaiters.addAll(prevWaiterMap.keySet());

        for (String waiterId : allWaiters) {

            WaiterMetrics curr = currWaiterMap.getOrDefault(waiterId, new WaiterMetrics());

            WaiterMetrics prev = prevWaiterMap.getOrDefault(waiterId, new WaiterMetrics());

            // orders

            int currOrders = curr.getTotalOrders();

            int prevOrders = prev.getTotalOrders();

            double orderDeltaPct = (prevOrders == 0) ? 0 : ((currOrders - prevOrders) * 100.0 / prevOrders);

            // hours

            double currHours = curr.getHoursWorked();

            double prevHours = prev.getHoursWorked();

            double hoursDeltaPct = (prevHours == 0) ? 0 : ((currHours - prevHours) * 100.0 / prevHours);

            // avg rating

            double currAvgRating = curr.getAverageServiceRating();

            double prevAvgRating = prev.getAverageServiceRating();

            double avgRatingDelta = (prevAvgRating == 0) ? 0 : ((currAvgRating - prevAvgRating) * 100.0 / prevAvgRating);

            // min rating

            double currMinRating = curr.getMinServiceRating();

            double prevMinRating = prev.getMinServiceRating();

//            double minRatingDelta = currMinRating - prevMinRating;

            // Assuming the Waiter table key is the waiterId (i.e., emailId), adjust as needed.

            String location = "";

            String waiterName = "";

            try {

                // Build key and get Waiter item

                Waiter waiter = waiterDynamoDbTable.getItem(Key.builder().partitionValue(waiterId).build());

                if (waiter != null) {

                    location = waiter.getLocationId();

                    // Combine first and last name (trimmed)

                    waiterName = (waiter.getFirstName() != null ? waiter.getFirstName() : "") + " " +

                            (waiter.getLastName() != null ? waiter.getLastName() : "");

                    waiterName = waiterName.trim();

                }

            } catch (Exception e) {

                System.out.println("Error retrieving details for waiter " + waiterId + ": " + e.getMessage());

            }


            sb.append(location).append(",")

                    .append(waiterName).append(",")

                    .append(waiterId).append(",")

                    .append(currentStartStr).append(",")

                    .append(currentEndStr).append(",")

                    .append(df.format(currHours)).append(",")

                    .append(currOrders).append(",")

                    .append(df.format(orderDeltaPct)).append("%,")

                    .append(df.format(currAvgRating)).append(",")

                    .append(df.format(currMinRating)).append(",")

                    .append(df.format(avgRatingDelta)).append("%,")

                    .append("\n");

        }

        return sb.toString().getBytes(StandardCharsets.UTF_8);

    }

}
