package com.epam.edp.demo.controller;

import com.epam.edp.demo.service.ReportSenderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@CrossOrigin(
        origins = "*",
        allowedHeaders = {
                "Content-Type",
                "X-Amz-Date",
                "Authorization",
                "X-Api-Key",
                "X-Amz-Security-Token"
        },
        methods = {
                RequestMethod.GET,
                RequestMethod.PUT,
                RequestMethod.POST,
                RequestMethod.DELETE,
                RequestMethod.OPTIONS
        }
)
@RequiredArgsConstructor
@RestController
public class ReportController {

    private final ReportSenderService reportSenderService;

    @PostMapping("/send-reports")
    public ResponseEntity<Map<String, String>> sendWeeklyDeltaReport() {
        reportSenderService.sendWeeklyDeltaReport();
        return ResponseEntity.ok(Map.of("message", "Report sent successfully"));
    }
}
