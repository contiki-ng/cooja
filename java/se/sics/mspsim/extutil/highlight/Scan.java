package se.sics.mspsim.extutil.highlight;
// Illustrate the use of the scanner by reading in a file and displaying its
// tokens. Public domain, no restrictions, Ian Holyer, University of Bristol.

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

class Scan {
  // Get the filename from the command line
  public static void main(String[] args) throws IOException {
    if (args.length != 1) {
      System.out.println("Usage: java Scan filename");
    } else {
      Scan.scan(args[0]);
    }
  }

  // Scan each line in turn
  private static void scan(String filename) throws IOException {
    File file = new File(filename);
    int len = (int) file.length();
    char[] buffer = new char[len];
    try (var in = Files.newBufferedReader(file.toPath(), UTF_8)) {
      if (in.read(buffer) < 0) {
        throw new IOException("in.read failed");
      }
    }

    Scanner scanner = new Scanner();
    scanner.change(0, 0, len);
    scanner.scan(buffer, 0, len);

    for (int i = 0; i < scanner.size(); i++) {
      Token t = scanner.getToken(i);
      System.out.print(t.position);
      System.out.print(": " + t.symbol.name);
      System.out.println(" " + TokenTypes.typeNames[t.symbol.type]);
    }
  }
}
