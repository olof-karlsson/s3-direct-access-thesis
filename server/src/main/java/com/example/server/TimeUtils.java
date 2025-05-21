package com.example.server;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class TimeUtils {
  public static String getCurrentTimestamp() {
    LocalDateTime now = LocalDateTime.now();
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss:SSS");
    return now.format(formatter);
  }
}
