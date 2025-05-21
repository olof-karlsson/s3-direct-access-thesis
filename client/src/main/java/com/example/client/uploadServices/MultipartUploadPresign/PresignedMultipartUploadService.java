package com.example.client.uploadServices.MultipartUploadPresign;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import com.example.client.utils.TimeUtils;

/**
 * Client helper that performs an S3 multipart upload via presigned URLs.
 *
 * The upload process includes:
 * 1. Initiating the upload to get an uploadId
 * 2. Splitting the file into parts and uploading each part with presigned PUT URLs
 * 3. Completing the multipart upload with a presigned POST request
 */
public class PresignedMultipartUploadService {
  private static int totalParts; // Used for debugging to display the total number of parts

  /**
   * Manage the entire multipart-upload:
   * <ul>
   *     <li>Initiate upload → get <b>uploadId</b></li>
   *     <li>Split file into parts & upload each part</li>
   *     <li>Complete upload</li>
   * </ul>
   *
   * @param client              - the HttpClient instance used for all HTTP calls
   * @param fileName            - the S3 object key to create
   * @param filePath            - path to the local file to upload
   * @param shouldSimulateAbort - Flag to simulate an error during part 2 upload
   */
  public static void uploadLargeFile(HttpClient client, String fileName, String filePath, Boolean shouldSimulateAbort) {
    String uploadId = null;

    try {
      System.out.println("[" + TimeUtils.getCurrentTimestamp() + "] [FileUpload] Initiating multipart upload with presigned URL");

      // Step 1: Initiate the multipart upload and receive a unique uploadId
      uploadId = InitiateMultipartUpload.initiateMultipartUpload(client, fileName);

      // Step 2: Prepare to split and upload file parts
      List<MultipartUploadDTO.CompletedPartDTO> parts = new ArrayList<>();
      Path path = Paths.get(filePath);
      long fileSize = Files.size(path);

      //Calculate dynamic part size to ensure no more than 10,000 parts and meet S3 requirements
//      long minPartSize = (fileSize + 9999) / 10000; // Round up to ensure <=10,000 parts
//      long partSize = Math.max(minPartSize, 5 * 1024 * 1024); // Minimum 5 MB
//      partSize = Math.min(partSize, 5L * 1024 * 1024 * 1024); // Maximum 5 GB
//
//      System.out.println("!!!!!!!! Calulated part size in mb: " + partSize / 1024.0 / 1024.0);

      long partSize = 5 * 1024 * 1024; // 5 MB per part (adjust as needed)

      totalParts = (int)Math.ceil((double)fileSize / partSize); // Total parts calculated for debugging
      // From bytes to MB
      System.out.printf("[%s] [FileUpload] FileSize=%.2f mb, PartSize=%.2f mb, TotalParts=%d%n", TimeUtils.getCurrentTimestamp(), fileSize / 1024.0 / 1024.0, partSize / 1024.0 / 1024.0, totalParts);

      int partNumber = 1;

      // Open file and sequentially read & upload parts
      try (RandomAccessFile file = new RandomAccessFile(path.toFile(), "r")) {
        long position = 0;
        while (position < fileSize) {
          // Calculate current part's size (handle last part edge case)
          long currentPartSize = Math.min(partSize, fileSize - position);

          // Request a presigned URL for the current part
          String presignedUrl = GetMultipartUrlResource.getPresignedPartUrl(client, fileName, uploadId, partNumber);

          // Upload the part using the retrieved presigned URL
          String eTag = GetMultipartUrlResource.uploadPart(file, fileName, position, currentPartSize, presignedUrl, shouldSimulateAbort, totalParts);

          // Store ETag and part number for completion request
          parts.add(new MultipartUploadDTO.CompletedPartDTO(partNumber, eTag));

          // Move file pointer to next part
          position += currentPartSize;
          partNumber++;
        }
      }

      // Step 3:Finalize the upload with collected parts
      System.out.println("[" + TimeUtils.getCurrentTimestamp() + "] [FileUpload] All parts uploaded. Completing multipart upload.");
      CompleteMultipartUploadUrl.completeMultipartUpload(client, fileName, uploadId, parts);
      System.out.println("[" + TimeUtils.getCurrentTimestamp() + "] [FileUpload] Multipart upload completed successfully ✔");

    } catch (IOException | InterruptedException e) {
      System.out.println("Upload failed due to: " + e.getMessage());

      // If upload started, try to clean up by aborting it
      if (uploadId != null) {
        try {
          System.out.println("Upload timeout or error occurred. Attempting to abort the upload...");
          AbortMultipartUpload.abortMultipartUpload(client, fileName, uploadId);
          //System.out.println("Aborted multipart upload " + uploadId);
        } catch (Exception abortEx) {
          System.err.println("Failed to abort multipart upload: " + abortEx.getMessage());
        }
      }
      Thread.currentThread().interrupt(); // Reset the interrupt flag if interrupted
    }
  }

}
