package com.nantaaditya.sotres.helper;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAccessor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DateTimeHelper {
  public static final ZoneId ZONE_ID = ZoneId.systemDefault();
  public static final ZoneId GMT_ZONE = ZoneId.of("GMT");
  public static final String ISO_8601_GMT7_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX";
  public static final String TRANSMISSION_DATE_TIME_FORMAT = "MMddHHmmss";

  public static final DateTimeFormatter TRANSMISSION_DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern(TRANSMISSION_DATE_TIME_FORMAT);
  public static final DateTimeFormatter LOCAL_DATE_FORMATTER = DateTimeFormatter.ofPattern("MMdd");
  public static final DateTimeFormatter EXPIRY_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyMM");
  public static final DateTimeFormatter LOCAL_TIME_FORMATTER = DateTimeFormatter.ofPattern("HHmmss");

  public static String getDateTime() {
    return ZonedDateTime.now(ZoneId.of("GMT")).format(TRANSMISSION_DATE_TIME_FORMATTER);
  }

  public static String getDateInFormat(ZonedDateTime zonedDateTime, String pattern) {
    if (zonedDateTime == null || pattern == null) return null;

    try {
      return zonedDateTime.format(DateTimeFormatter.ofPattern(pattern));
    } catch (Exception e) {
      log.error("#DateTime - failed convert {}, pattern {}, error {}, {}", zonedDateTime, pattern, e.getMessage(), ErrorHelper.getRootCause(e));
      return null;
    }
  }

  public static ZonedDateTime convertTransmissionDateTime(String timeString) {
    TemporalAccessor accessor = TRANSMISSION_DATE_TIME_FORMATTER.parse(timeString);

    LocalTime localTime = LocalTime.from(accessor);
    int currentYear = LocalDate.now().getYear();
    int month = accessor.get(java.time.temporal.ChronoField.MONTH_OF_YEAR);
    int day = accessor.get(java.time.temporal.ChronoField.DAY_OF_MONTH);

    LocalDate localDate = LocalDate.of(currentYear, month, day);

    ZonedDateTime gmtZonedDateTime = ZonedDateTime.of(
        localDate,
        localTime,
        GMT_ZONE
    );

    return gmtZonedDateTime;
  }

  public static LocalDate convertLocalTransactionDate(String timeString) {
    TemporalAccessor accessor = LOCAL_DATE_FORMATTER.parse(timeString);

    int currentYear = LocalDate.now().getYear();
    int month = accessor.get(java.time.temporal.ChronoField.MONTH_OF_YEAR);
    int day = accessor.get(java.time.temporal.ChronoField.DAY_OF_MONTH);

    return LocalDate.of(currentYear, month, day);
  }

  public static LocalTime convertLocalTransactionTime(String timeString) {
    TemporalAccessor accessor = LOCAL_TIME_FORMATTER.parse(timeString);

    int hour = accessor.get(ChronoField.HOUR_OF_DAY);
    int minute = accessor.get(ChronoField.MINUTE_OF_HOUR);
    int second = accessor.get(ChronoField.SECOND_OF_MINUTE);

    return LocalTime.of(hour, minute, second);
  }

  public static LocalDate convertExpiryDate(String timeString) {
    TemporalAccessor accessor = EXPIRY_DATE_FORMATTER.parse(timeString);

    int year = accessor.get(ChronoField.YEAR);
    int month = accessor.get(ChronoField.MONTH_OF_YEAR);
    LocalDate firstDayOfMonth = LocalDate.of(year, month, 1);
    int lastDay = firstDayOfMonth.lengthOfMonth();

    return firstDayOfMonth.withDayOfMonth(lastDay);
  }
}
