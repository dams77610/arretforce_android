package fr.outil.nettoyage;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/** Service Shizuku exécuté comme shell ; jamais comme service Android classique. */
public final class CleanerService extends ICleanerService.Stub {
  public CleanerService() {}

  @Override public String getProcessSnapshot() {
    // Android varie selon les versions : NAME fonctionne en général sous toybox.
    // Une sortie vide ne doit jamais être présentée comme « aucune application active ».
    String[][] candidates = {
        {"/system/bin/ps", "-A", "-o", "NAME"},
        {"/system/bin/ps", "-A", "-o", "ARGS"},
        {"/system/bin/ps", "-A"}
    };
    String problem = "commande ps indisponible";
    for (String[] command : candidates) {
      Result r = execute(command, 12);
      if (r.exit == 0 && ProcessParser.runningPackages(r.output).size() > 0)
        return r.output;
      problem = "ps exit=" + r.exit;
    }
    return "#ERROR:" + problem;
  }

  @Override public String stopPackages(String[] packages) {
    if (packages == null || packages.length == 0) return "";
    StringBuilder results = new StringBuilder();
    for (int i = 0; i < Math.min(packages.length, 100); ++i) {
      String pkg = packages[i];
      if (!ProcessParser.isValidPackage(pkg)) {
        results.append("KO\t?\n");
        continue;
      }
      // Les noms de paquets sont passés en arguments, jamais via un shell interprétant une chaîne.
      Result r = execute(new String[] {"/system/bin/am", "force-stop", "--user", "current", pkg}, 8);
      results.append(r.exit == 0 ? "OK\t" : "KO\t").append(pkg).append('\n');
    }
    return results.toString();
  }

  private static Result execute(String[] args, int timeout) {
    Process process = null;
    try {
      process = new ProcessBuilder(args).redirectErrorStream(true).start();
      final Process p = process;
      StringBuilder output = new StringBuilder();
      Thread reader = new Thread(() -> {
        try (BufferedReader in = new BufferedReader(
            new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
          String line;
          while ((line = in.readLine()) != null) {
            if (output.length() < 250000) output.append(line).append('\n');
          }
        } catch (Exception ignored) {}
      });
      reader.setDaemon(true);
      reader.start();
      if (!process.waitFor(timeout, TimeUnit.SECONDS)) {
        process.destroyForcibly();
        return new Result(-1, "");
      }
      reader.join(1000);
      return new Result(process.exitValue(), output.toString());
    } catch (Exception ex) {
      if (process != null) process.destroyForcibly();
      return new Result(-1, "");
    }
  }

  private static final class Result {
    final int exit;
    final String output;
    Result(int exit, String output) { this.exit = exit; this.output = output; }
  }
}
