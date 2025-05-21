
package com.example.client;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;


/**
 * Class to generate files for testing. Set to 100 files with size ranging 1-500 MB, 1 GB as total size.
 */
public class RandomFileGenerator {

  public static void main(String[] args) throws IOException {
    final int fileCount = 100;
    final long totalSizeBytes = 1L * 1024 * 1024 * 1024; // 1 GB
    final long minSizeBytes = 1L * 1024 * 1024; // 1 MB
    final long maxSizeBytes = 500L * 1024 * 1024; // 500 MB
    final Path downloadDir = Paths.get(System.getProperty("user.home"), "Downloads", "TESTFILES");

    SecureRandom rnd = new SecureRandom();

    if (!Files.exists(downloadDir)) {
      Files.createDirectories(downloadDir);
    }

    // Precompute log-scale range
    double logMin = Math.log(minSizeBytes);
    double logMax = Math.log(maxSizeBytes);

    // Generate unnormalized random sizes (log-uniform)
    List<Long> rawSizes = new ArrayList<>();
    double totalRaw = 0;
    for(int i = 0; i < fileCount; i++) {
      double u = rnd.nextDouble();
      double logSize = logMin + u * (logMax - logMin);
      long size = Math.round(Math.exp(logSize));
      rawSizes.add(size);
      totalRaw += size;
    }

    // Normalize sizes to match totalSizeBytes
    List<Long> finalSizes = new ArrayList<>();
    long normalizedTotal = 0;
    for(int i = 0; i < fileCount; i++) {
      long normSize = (long)((rawSizes.get(i) / totalRaw) * totalSizeBytes);
      // Ensure no file is less than 1 MB
      normSize = Math.max(minSizeBytes, normSize);
      finalSizes.add(normSize);
      normalizedTotal += normSize;
    }

    // Adjust the last file size to make total match exactly
    long sizeCorrection = totalSizeBytes - normalizedTotal;
    finalSizes.set(fileCount - 1, finalSizes.get(fileCount - 1) + sizeCorrection);

    // Generate files
    for(int i = 0; i < fileCount; i++) {
      long size = finalSizes.get(i);
      String name = "file" + (i + 1);
      Path outPath = downloadDir.resolve(name);

      System.out.printf("Creating %-12s %,10.2f MB%n", name, size / (1024.0 * 1024.0));

      try (FileOutputStream fos = new FileOutputStream(outPath.toFile())) {
        byte[] buffer = new byte[1024 * 1024];
        long remaining = size;
        while (remaining > 0) {
          int chunk = (int)Math.min(buffer.length, remaining);
          rnd.nextBytes(buffer);
          fos.write(buffer, 0, chunk);
          remaining -= chunk;
        }
      }
    }

    System.out.println("Done. Files created in: " + downloadDir);
  }
}

// To generate only 1 file that is 100mb:
//
//package com.example.client;
//
//import java.io.FileOutputStream;
//import java.io.IOException;
//import java.nio.file.Files;
//import java.nio.file.Path;
//import java.nio.file.Paths;
//import java.security.SecureRandom;
//
//public class RandomFileGenerator {
//
//  public static void main(String[] args) throws IOException {
//    final long fileSizeBytes = 100L * 1024 * 1024; // 100 MB
//    final String fileName = "random_100MB_file";
//    final Path downloadDir = Paths.get(System.getProperty("user.home"), "Downloads", "TESTFILES");
//    final Path filePath = downloadDir.resolve(fileName);
//
//    SecureRandom rnd = new SecureRandom();
//
//    if (!Files.exists(downloadDir)) {
//      Files.createDirectories(downloadDir);
//    }
//
//    System.out.printf("Creating %s (%.2f MB)%n", fileName, fileSizeBytes / (1024.0 * 1024.0));
//
//    try (FileOutputStream fos = new FileOutputStream(filePath.toFile())) {
//      byte[] buffer = new byte[1024 * 1024]; // 1 MB buffer
//      long remaining = fileSizeBytes;
//      while (remaining > 0) {
//        int chunk = (int)Math.min(buffer.length, remaining);
//        rnd.nextBytes(buffer);
//        fos.write(buffer, 0, chunk);
//        remaining -= chunk;
//      }
//    }
//
//    System.out.println("Done. File created at: " + filePath);
//  }
//}
