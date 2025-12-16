package com.nantaaditya.sotres.helper;

import java.lang.reflect.InvocationTargetException;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class StringHelper {

  public static final int SECOND_TO_MILLIS = 1000;

  private StringHelper() {}

  public static Collection<String> toCollection(String fields, String delimiter,
      Class<? extends Collection> collectionClass) {
    try {
      Collection<String> collections = (Collection<String>) collectionClass.getDeclaredConstructor().newInstance(); // NOSONAR

      if (fields == null || fields.isEmpty()) {
        return collections;
      }

      StringTokenizer tokenizer = delimiter == null || delimiter.isEmpty() ?
          new StringTokenizer(fields) : new StringTokenizer(fields, delimiter);

      while (tokenizer.hasMoreTokens()) {
        collections.add(tokenizer.nextToken().trim());
      }
      return collections;
    } catch (InstantiationException | IllegalAccessException
             | NoSuchMethodException | InvocationTargetException e) {
      log.error("#Converter - error creating collection instance {}, cause {}", e.getMessage(), ErrorHelper.getRootCause(e));
      return null;
    }
  }

  public static Map<String, String> toCollection(String fields, String rowDelimiter, String columnDelimiter,
      Class<? extends Map> mapClass) {
    try {
      Map<String, String> maps = (Map<String, String>) mapClass.getDeclaredConstructor().newInstance(); // NOSONAR

      if (fields == null || fields.isEmpty()) {
        return maps;
      }

      StringTokenizer tokenizer = rowDelimiter == null || rowDelimiter.isEmpty() ?
          new StringTokenizer(fields) : new StringTokenizer(fields, rowDelimiter);

      while (tokenizer.hasMoreTokens()) {
        String [] pair = tokenizer.nextToken().trim().split(columnDelimiter);
        maps.put(pair[0], pair[1]);
      }
      return maps;
    } catch (InstantiationException | IllegalAccessException
             | NoSuchMethodException | InvocationTargetException e) {
      log.error("#Converter - error creating map instance {}, cause {}", e.getMessage(), ErrorHelper.getRootCause(e));
      return null;
    }
  }

  public static String append(String data, char appendChar, int count) {
    if (data.length() > count) {
      return data;
    }
    return data + String.valueOf(appendChar).repeat(count - data.length());
  }

  public static String prepend(String data, char prependChar, int count) {
    if (data.length() > count) {
      return data;
    }
    return String.valueOf(prependChar).repeat(count - data.length()) + data;
  }

  public static String unPrepend(String string, char padCharacter) {
    if (string == null || string.isEmpty()) {
      return string;
    }

    int firstNonPadIndex = 0;
    while (firstNonPadIndex < string.length() && string.charAt(firstNonPadIndex) == padCharacter) {
      firstNonPadIndex++;
    }

    return string.substring(firstNonPadIndex);
  }

  public static String unAppend(String string, char padCharacter) {
    if (string == null || string.isEmpty()) {
      return string;
    }

    int lastNonPadIndex = string.length() - 1;
    while (lastNonPadIndex >= 0 && string.charAt(lastNonPadIndex) == padCharacter) {
      lastNonPadIndex--;
    }

    return string.substring(0, lastNonPadIndex + 1);
  }

  public static Map<String, String> toMaps(String string) {
    StringTokenizer tokenizer = new StringTokenizer(string, ",");
    Map<String, String> result = new HashMap<>();
    while (tokenizer.hasMoreTokens()) {
      String[] token = tokenizer.nextToken().split(":");
      result.put(token[0], token[1]);
    }
    return result;
  }

  public static Set<String> toSet(String string) {
    StringTokenizer tokenizer = new StringTokenizer(string, ",");
    Set<String> result = new LinkedHashSet<>();
    while (tokenizer.hasMoreTokens()) {
      result.add(tokenizer.nextToken());
    }
    return result;
  }
}