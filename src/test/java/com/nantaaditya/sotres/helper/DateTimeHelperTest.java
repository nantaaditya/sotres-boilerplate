package com.nantaaditya.sotres.helper;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("DateTimeHelper")
class DateTimeHelperTest {

  @Nested
  @DisplayName("getDateTime()")
  class GetDateTime {

    @Test
    @DisplayName("returns 10-character string in MMddHHmmss format")
    void getDateTime_returnsValidTransmissionFormat() {
      String result = DateTimeHelper.getDateTime();
      assertThat(result).isNotNull().hasSize(10).matches("[0-9]{10}");
    }
  }

  @Nested
  @DisplayName("getDateInFormat(ZonedDateTime, String)")
  class GetDateInFormatZoned {

    @Test
    @DisplayName("returns formatted string for valid input")
    void getDateInFormat_validInput_returnsFormatted() {
      ZonedDateTime input = ZonedDateTime.of(2024, 6, 15, 10, 30, 45, 0, ZoneId.of("GMT"));
      String result = DateTimeHelper.getDateInFormat(input, "yyyy-MM-dd");
      assertThat(result).isEqualTo("2024-06-15");
    }

    @Test
    @DisplayName("returns null when ZonedDateTime is null")
    void getDateInFormat_nullZonedDateTime_returnsNull() {
      assertThat(DateTimeHelper.getDateInFormat((ZonedDateTime) null, "yyyy-MM-dd")).isNull();
    }

    @Test
    @DisplayName("returns null when pattern is null")
    void getDateInFormat_nullPattern_returnsNull() {
      assertThat(DateTimeHelper.getDateInFormat(ZonedDateTime.now(), null)).isNull();
    }

    @Test
    @DisplayName("returns null when pattern is invalid")
    void getDateInFormat_invalidPattern_returnsNull() {
      assertThat(DateTimeHelper.getDateInFormat(ZonedDateTime.now(), "not-a-pattern-!!!")).isNull();
    }
  }

  @Nested
  @DisplayName("convertTransmissionDateTime(String)")
  class ConvertTransmissionDateTime {

    @Test
    @DisplayName("parses MMddHHmmss string to GMT ZonedDateTime")
    void convertTransmissionDateTime_validInput_returnsGmtDateTime() {
      String input = "0615103045"; // June 15, 10:30:45
      ZonedDateTime result = DateTimeHelper.convertTransmissionDateTime(input);

      assertThat(result).isNotNull();
      assertThat(result.getZone()).isEqualTo(DateTimeHelper.GMT_ZONE);
      assertThat(result.getMonthValue()).isEqualTo(6);
      assertThat(result.getDayOfMonth()).isEqualTo(15);
      assertThat(result.getHour()).isEqualTo(10);
      assertThat(result.getMinute()).isEqualTo(30);
      assertThat(result.getSecond()).isEqualTo(45);
    }

    @Test
    @DisplayName("uses current year when reconstructing date")
    void convertTransmissionDateTime_setsCurrentYear() {
      String input = "0101000000"; // Jan 1
      ZonedDateTime result = DateTimeHelper.convertTransmissionDateTime(input);
      assertThat(result.getYear()).isEqualTo(LocalDate.now().getYear());
    }
  }

  @Nested
  @DisplayName("convertLocalTransactionDate(String)")
  class ConvertLocalTransactionDate {

    @Test
    @DisplayName("parses MMdd to LocalDate using current year")
    void convertLocalTransactionDate_validInput_returnsLocalDate() {
      String input = "0615"; // June 15
      LocalDate result = DateTimeHelper.convertLocalTransactionDate(input);

      assertThat(result).isNotNull();
      assertThat(result.getYear()).isEqualTo(LocalDate.now().getYear());
      assertThat(result.getMonthValue()).isEqualTo(6);
      assertThat(result.getDayOfMonth()).isEqualTo(15);
    }
  }

  @Nested
  @DisplayName("convertLocalTransactionTime(String)")
  class ConvertLocalTransactionTime {

    @Test
    @DisplayName("parses HHmmss to LocalTime")
    void convertLocalTransactionTime_validInput_returnsLocalTime() {
      String input = "103045"; // 10:30:45
      LocalTime result = DateTimeHelper.convertLocalTransactionTime(input);

      assertThat(result).isNotNull();
      assertThat(result.getHour()).isEqualTo(10);
      assertThat(result.getMinute()).isEqualTo(30);
      assertThat(result.getSecond()).isEqualTo(45);
    }

    @Test
    @DisplayName("parses midnight correctly")
    void convertLocalTransactionTime_midnight_returnsZeroTime() {
      LocalTime result = DateTimeHelper.convertLocalTransactionTime("000000");
      assertThat(result).isEqualTo(LocalTime.MIDNIGHT);
    }
  }

  @Nested
  @DisplayName("convertExpiryDate(String)")
  class ConvertExpiryDate {

    @Test
    @DisplayName("returns last day of month for leap year February")
    void convertExpiryDate_leapYearFebruary_returnsDay29() {
      String input = "2402"; // February 2024 (leap year)
      assertThat(DateTimeHelper.convertExpiryDate(input).getDayOfMonth()).isEqualTo(29);
    }

    @Test
    @DisplayName("returns last day of month for non-leap year February")
    void convertExpiryDate_nonLeapYearFebruary_returnsDay28() {
      String input = "2302"; // February 2023
      assertThat(DateTimeHelper.convertExpiryDate(input).getDayOfMonth()).isEqualTo(28);
    }

    @Test
    @DisplayName("returns day 31 for December")
    void convertExpiryDate_december_returnsDay31() {
      String input = "2412"; // December 2024
      assertThat(DateTimeHelper.convertExpiryDate(input).getDayOfMonth()).isEqualTo(31);
    }

    @Test
    @DisplayName("returns day 30 for April")
    void convertExpiryDate_april_returnsDay30() {
      String input = "2404"; // April 2024
      assertThat(DateTimeHelper.convertExpiryDate(input).getDayOfMonth()).isEqualTo(30);
    }
  }

  @Nested
  @DisplayName("getDateInFormat(long, DateTimeFormatter)")
  class GetDateInFormatMillis {

    @Test
    @DisplayName("returns formatted string for valid epoch millis")
    void getDateInFormat_validMillis_returnsFormatted() {
      long millis = 1718444445000L;
      String result = DateTimeHelper.getDateInFormat(millis, DateTimeHelper.LOG_TIMESTAMP_FORMATTER);
      assertThat(result).isNotNull().isNotEmpty().matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}:\\d{3}");
    }

    @Test
    @DisplayName("returns null for zero millis")
    void getDateInFormat_zeroMillis_returnsNull() {
      assertThat(DateTimeHelper.getDateInFormat(0L, DateTimeHelper.LOG_TIMESTAMP_FORMATTER)).isNull();
    }

    @Test
    @DisplayName("returns null for negative millis")
    void getDateInFormat_negativeMillis_returnsNull() {
      assertThat(DateTimeHelper.getDateInFormat(-1L, DateTimeHelper.LOG_TIMESTAMP_FORMATTER)).isNull();
    }

    @Test
    @DisplayName("returns null when formatter is null")
    void getDateInFormat_nullFormatter_returnsNull() {
      assertThat(DateTimeHelper.getDateInFormat(1718444445000L, null)).isNull();
    }
  }
}
