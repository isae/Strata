/**
 * Copyright (C) 2015 - present by OpenGamma Inc. and the OpenGamma group of companies
 * 
 * Please see distribution for license.
 */
package com.opengamma.strata.collect.io;

import static com.opengamma.strata.collect.Guavate.toImmutableMap;
import static java.util.stream.Collectors.toCollection;

import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Locale;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.CharSource;
import com.opengamma.strata.collect.ArgChecker;
import com.opengamma.strata.collect.Messages;
import com.opengamma.strata.collect.Unchecked;

/**
 * A CSV file.
 * <p>
 * Represents a CSV file together with the ability to parse it from a {@link CharSource}.
 * <p>
 * The CSV file format is a general-purpose comma-separated value format.
 * The format is parsed line-by-line, with lines separated by CR, LF or CRLF.
 * Each line can contain one or more fields.
 * Each field is separated by a comma character ({@literal ,}).
 * Any field may be quoted using a double quote at the start and end.
 * The content of a quoted field may include commas and additional double quotes.
 * Two adjacent double quotes in a quoted field will be replaced by a single double quote.
 * Quoted fields are not trimmed. Non-quoted fields are trimmed.
 * <p>
 * The first line may be treated as a header row.
 * The header row is accessed separately from the data rows.
 * <p>
 * Blank lines are ignored.
 * Lines may be commented with has '#' or semicolon ';'.
 */
public final class CsvFile {

  /**
   * The header row, ordered as the headers appear in the file.
   */
  private final ImmutableList<String> headers;
  /**
   * The header map, transformed for case-insensitive searching.
   */
  private final ImmutableMap<String, Integer> searchHeaders;
  /**
   * The data rows in the CSV file.
   */
  private final ImmutableList<ImmutableList<String>> lines;

  //------------------------------------------------------------------------
  /**
   * Parses the specified source as a CSV file.
   * 
   * @param source  the CSV file resource
   * @param headerRow  whether the source has a header row
   * @return the CSV file
   * @throws UncheckedIOException if an IO exception occurs
   * @throws IllegalArgumentException if the file cannot be parsed
   */
  public static CsvFile of(CharSource source, boolean headerRow) {
    ArgChecker.notNull(source, "source");
    ImmutableList<String> lines = Unchecked.wrap(() -> source.readLines());
    ArrayList<ImmutableList<String>> parsedCsv = parse(lines);
    if (headerRow) {
      if (parsedCsv.isEmpty()) {
        throw new IllegalArgumentException("Could not read header row from empty CSV file");
      }
      ImmutableList<String> headers = parsedCsv.remove(0);
      ImmutableList<ImmutableList<String>> dataLines = ImmutableList.copyOf(parsedCsv);
      return new CsvFile(headers, dataLines);
    } else {
      return new CsvFile(ImmutableList.of(), ImmutableList.copyOf(parsedCsv));
    }
  }

  //------------------------------------------------------------------------
  // parses the CSV file format
  private static ArrayList<ImmutableList<String>> parse(ImmutableList<String> lines) {
    return lines.stream()
        .flatMap(line -> parseLine(line))
        .collect(toCollection(ArrayList::new));
  }

  // return Stream rather than Optional as works better with flatMap
  private static Stream<ImmutableList<String>> parseLine(String line) {
    if (line.length() == 0 || line.startsWith("#") || line.startsWith(";")) {
      return Stream.empty();
    }
    ImmutableList.Builder<String> builder = ImmutableList.builder();
    int start = 0;
    String terminated = line + ',';
    int nextComma = terminated.indexOf(',', start);
    while (nextComma >= 0) {
      String possible = terminated.substring(start, nextComma).trim();
      if (possible.startsWith("\"")) {
        while (true) {
          if (possible.substring(1).replace("\"\"", "").endsWith("\"")) {
            possible = possible.substring(1, possible.length() - 1).replace("\"\"", "\"");
            break;
          } else {
            nextComma = terminated.indexOf(',', nextComma + 1);
            if (nextComma < 0) {
              throw new IllegalArgumentException("Mismatched quotes on line: " + line);
            }
            possible = terminated.substring(start, nextComma).trim();
          }
        }
      }
      builder.add(possible);
      start = nextComma + 1;
      nextComma = terminated.indexOf(',', start);
    }
    ImmutableList<String> fields = builder.build();
    if (!hasContent(fields)) {
      return Stream.empty();
    }
    return Stream.of(fields);
  }

  // determines whether there is any content on a line
  // this handles lines that contain commas but nothing else
  private static boolean hasContent(ImmutableList<String> fields) {
    for (String field : fields) {
      if (!field.trim().isEmpty()) {
        return true;
      }
    }
    return false;
  }

  //------------------------------------------------------------------------
  /**
   * Restricted constructor.
   * 
   * @param headers  the header row
   * @param lines  the data lines
   */
  private CsvFile(ImmutableList<String> headers, ImmutableList<ImmutableList<String>> lines) {
    this.headers = headers;
    searchHeaders = IntStream.range(0, headers.size())
        .boxed()
        .collect(toImmutableMap(index -> headers.get(index).toLowerCase(Locale.ENGLISH)));
    this.lines = lines;
  }

  //------------------------------------------------------------------------
  /**
   * Gets the header row.
   * <p>
   * If there is no header row, an empty list is returned.
   * 
   * @return the header row
   */
  public ImmutableList<String> headers() {
    return headers;
  }

  /**
   * Gets all data lines in the file.
   * 
   * @return the data lines
   */
  public ImmutableList<ImmutableList<String>> lines() {
    return lines;
  }

  /**
   * Gets the number of data lines.
   * 
   * @return the number of data lines
   */
  public int lineCount() {
    return lines.size();
  }

  /**
   * Gets a single line.
   * 
   * @param index  the line index
   * @return the line
   */
  public ImmutableList<String> line(int index) {
    return lines.get(index);
  }

  /**
   * Gets a single field value from a line by column header.
   * <p>
   * This is typically used by looping around each line by index.
   * 
   * @param lineIndex  the line index
   * @param header  the column header
   * @return the field value
   * @throws IllegalArgumentException if the header is not found
   */
  public String field(int lineIndex, String header) {
    Integer headerIndex = searchHeaders.get(header.toLowerCase(Locale.ENGLISH));
    if (headerIndex == null) {
      throw new IllegalArgumentException(Messages.format("Header not found: {}", header));
    }
    return line(lineIndex).get(headerIndex);
  }

  //-------------------------------------------------------------------------
  /**
   * Checks if this CSV file equals another.
   * <p>
   * The comparison checks the content.
   * 
   * @param obj  the other file, null returns false
   * @return true if equal
   */
  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }
    if (obj instanceof CsvFile) {
      CsvFile other = (CsvFile) obj;
      return headers.equals(other.headers) && lines.equals(other.lines);
    }
    return false;
  }

  /**
   * Returns a suitable hash code for the CSV file.
   * 
   * @return the hash code
   */
  @Override
  public int hashCode() {
    return headers.hashCode() ^ lines.hashCode();
  }

  /**
   * Returns a string describing the CSV file.
   * 
   * @return the descriptive string
   */
  @Override
  public String toString() {
    return "CsvFile" + headers.toString();
  }

}
