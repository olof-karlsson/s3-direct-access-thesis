package com.example.server.uploadServices;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.server.Main;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

public class PresignedUrlUploadService {
  private static final Logger logger = LoggerFactory.getLogger(PresignedUrlUploadService.class);

  /**
   * Creates a presigned S3 upload URL using AWS SDK.
   */
  public static String createPresignedUrlUpload(Region region, String bucketName, String keyName) {
    S3Presigner presigner = Main.getS3Presigner();

    try (presigner) {
      // Build request to upload to S3
      PutObjectRequest objectRequest = PutObjectRequest.builder().bucket(bucketName).key(keyName).build();

      // Build presign request (valid for 10 minutes)
      PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder().signatureDuration(Duration.ofMinutes(10)).putObjectRequest(objectRequest).build();

      // Generate the presigned URL
      PresignedPutObjectRequest presignedRequest = presigner.presignPutObject(presignRequest);
      return presignedRequest.url().toString();
    } catch (S3Exception e) {
      logger.error("Failed to generate presigned upload URL", e);
      throw e;
    }
  }

}
