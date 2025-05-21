package com.example.client.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class HashUtils {
  public static String calculateFileHash(String filePath, String algorithm) throws IOException, NoSuchAlgorithmException {
    MessageDigest digest = MessageDigest.getInstance(algorithm);
    File file = new File(filePath);

    try (FileInputStream fis = new FileInputStream(file)) {
      byte[] byteArray = new byte[1024];
      int bytesRead;

      while ((bytesRead = fis.read(byteArray)) != -1) {
        digest.update(byteArray, 0, bytesRead);
      }
    }

    byte[] hashBytes = digest.digest();

    // Convert hash bytes to hexadecimal format
    StringBuilder hexString = new StringBuilder();
    for(byte hashByte : hashBytes) {
      hexString.append(String.format("%02x", hashByte));
    }

    return hexString.toString();
  }
}
