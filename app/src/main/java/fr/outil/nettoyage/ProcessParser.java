package fr.outil.nettoyage;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

public final class ProcessParser {
  private static final Pattern PACKAGE = Pattern.compile("[A-Za-z_][A-Za-z_0-9]*(\\.[A-Za-z_][A-Za-z_0-9]*)+");
  private ProcessParser() {}

  public static boolean isValidPackage(String value) {
    return value != null && PACKAGE.matcher(value).matches();
  }

  public static Set<String> runningPackages(String output) {
    if (output == null || output.isEmpty()) return Collections.emptySet();
    Set<String> found = new HashSet<>();
    for (String line : output.split("\\R")) {
      String[] tokens = line.trim().split("\\s+");
      if (tokens.length == 0) continue;
      String base = tokens[0].split(":", 2)[0];
      if (isValidPackage(base)) found.add(base);
    }
    return found;
  }
}
