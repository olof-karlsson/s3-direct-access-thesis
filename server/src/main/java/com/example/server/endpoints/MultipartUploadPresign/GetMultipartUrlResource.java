package com.example.server.endpoints.MultipartUploadPresign;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.server.Main;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.UploadPartRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedUploadPartRequest;
import software.amazon.awssdk.services.s3.presigner.model.UploadPartPresignRequest;

@Path("/files")
public class GetMultipartUrlResource {
  private static final Logger logger = LoggerFactory.getLogger(AbortMultipartUploadResource.class);

  /**
   * Endpoint to generate a presigned URL for uploading a single part of a multipart S3 upload.
   *
   * This method is invoked during a multipart upload process. Clients call it for each part of a file
   * they want to upload. It returns a time-limited, signed URL that clients can use to upload directly
   * to S3 without AWS credentials.
   *
   * Example URL: /files/multipart-presign/myfile.txt/abc123uploadid/1
   *
   * @param fileName   - The key (file name) to be stored in the S3 bucket
   * @param uploadId   - The ID of the multipart upload session
   * @param partNumber - The index of the part being uploaded (1-based)
   * @return A JSON response: { "url": "..." } with the presigned PUT URL
   */
  @GET
  @Path("/multipart-presign/{fileName}/{uploadId}/{partNumber}")
  @Produces(MediaType.APPLICATION_JSON)
  public Response getMultipartPresignedUrl( //
    @PathParam("fileName") String fileName, //
    @PathParam("uploadId") String uploadId, //
    @PathParam("partNumber") int partNumber) {

    // Generate a presigned URL for this specific part of the upload
    String presignedUrl = generatePresignedPartUrl(Main.getRegion(), Main.getBucketName(), fileName, uploadId, partNumber);

    // Truncated (shorten) upload id for nicer debugging
    String truncatedUploadId = uploadId.length() > 20 ? uploadId.substring(0, 20) + "..." : uploadId;
    System.out.println(String.format("   Generating presigned URL for part %d, of file %s\n" + "   Upload ID: %s", partNumber, fileName, truncatedUploadId));

    // Return the presigned URL as a JSON response
    return Response.ok("{\"url\": \"" + presignedUrl + "\"}").build();
  }

  /**
   * Helper method to generate a presigned PUT URL for uploading a specific part to S3.
   *
   * This method creates an UploadPartRequest, then uses an S3Presigner to sign it,
   * returning a URL that the client can use to upload the part directly to S3.
   *
   * @param region     - The AWS region where the S3 bucket is hosted
   * @param bucketName - The S3 bucket name
   * @param keyName    - The key (file name) being uploaded
   * @param uploadId   - The multipart upload ID
   * @param partNumber - The specific part index (1-based)
   * @return A presigned PUT URL as a string
   */
  public static String generatePresignedPartUrl( //
    Region region, //
    String bucketName, //
    String keyName, //
    String uploadId, //
    int partNumber) {

    // Create the presigner with default AWS credentials and target region
    try (S3Presigner s3presigner = S3Presigner.builder().credentialsProvider(DefaultCredentialsProvider.create()).region(region).build()) {

      // Build the actual UploadPartRequest that will be signed
      UploadPartRequest uploadPartRequest = UploadPartRequest.builder() //
        .bucket(bucketName) //
        .key(keyName) //
        .uploadId(uploadId) //
        .partNumber(partNumber).build();

      // Create the presign request with a 10 min expiration
      PresignedUploadPartRequest presignedRequest = s3presigner.presignUploadPart( //
        UploadPartPresignRequest.builder() //
          .signatureDuration(Duration.ofMinutes(10)) //
          .uploadPartRequest(uploadPartRequest) //
          .build());

      // Return the presigned URL as a string
      return presignedRequest.url().toString();
    } catch (S3Exception e) {
      logger.error("Failed to generate presigned URL for part {}: {}", partNumber, e.getMessage(), e);
      throw e;
    }
  }
}
