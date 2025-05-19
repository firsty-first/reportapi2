package com.epam.edp.demo.scheduler;

import com.epam.edp.demo.service.ReportSenderService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class WeeklyReportSenderScheduler {

    private final ReportSenderService reportSenderService;

    @Scheduled(cron = "0 0 10 ? * SAT", zone = "Asia/Kolkata") // Every week on Sat at 10:00 AM IST
    public void scheduledTask() {
        reportSenderService.sendWeeklyDeltaReport();
    }

}
