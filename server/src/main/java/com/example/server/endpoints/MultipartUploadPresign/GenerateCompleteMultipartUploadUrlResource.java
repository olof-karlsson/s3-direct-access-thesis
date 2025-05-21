package com.example.server.endpoints.MultipartUploadPresign;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.server.Main;
import com.example.server.TimeUtils;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompletedMultipartUpload;
import software.amazon.awssdk.services.s3.model.CompletedPart;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.CompleteMultipartUploadPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedCompleteMultipartUploadRequest;

@Path("/files")
public class GenerateCompleteMultipartUploadUrlResource {
  private static final Logger logger = LoggerFactory.getLogger(AbortMultipartUploadResource.class);

  /**
   * Endpoint to generate a presigned URL and its signed payload for completing a multipart upload to S3.
   *
   * This is typically used at the end of a multipart upload process. After all parts have been uploaded,
   * the client submits a list of uploaded parts (with part numbers and ETags), and this method will:
   *
   * 1. Convert the list of part DTOs (CompletedPartDTO) to AWS SDK CompletedPart objects.
   * 2. Build an S3 CompleteMultipartUploadRequest with the given parts and uploadId.
   * 3. Use AWS S3Presigner to generate a presigned URL and its signed payload to complete the multipart upload.
   * 4. Return a JSON response containing both the presigned URL and the signed payload.
   *
   * Example usage:
   * POST /multipart-complete-presign/myFile.txt/UPLOAD_ID
   * Body: [ { "partNumber": 1, "eTag": "etag1" }, { "partNumber": 2, "eTag": "etag2" } ]
   *
   * @param fileName - The name of the file (object key) in the S3 bucket.
   * @param uploadId -  The unique identifier for the multipart upload session.
   * @param partsDto -  List of DTOs representing each uploaded part (part number + ETag).
   * @return HTTP 200 with JSON: { "url": "https://s3...", "payload": "..." } or HTTP 500 on error.
   *
   * */
  @POST
  @Path("/multipart-complete-presign/{fileName}/{uploadId}")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public Response generateCompleteMultipartUploadPresignedUrl( //
    @PathParam("fileName") String fileName, // Extract fileName from path
    @PathParam("uploadId") String uploadId, // Extract uploadId from path
    List<CompletedPartDTO> partsDto) { // Receive part metadata from request body

    try {
      // 1) Convert DTO list to AWS SDK CompletedPart list
      List<CompletedPart> parts = partsDto.stream() //
        .map(dto -> CompletedPart.builder() // Build CompletedPart for each DTO
          .partNumber(dto.getPartNumber()) // Set part number
          .eTag(dto.getETag()) // Set ETag
          .build()) //
        .collect(Collectors.toList()); // Collect to list

      String truncatedUploadId = uploadId.length() > 20 ? uploadId.substring(0, 20) + "..." : uploadId;
      System.out.println(String.format("[%s] [PresignedURL]  Completing multipart upload for file '%s', uploadId '%s', total parts: %d", TimeUtils.getCurrentTimestamp(), fileName, truncatedUploadId, parts.size()));
      System.out.println(String.format("Received parts details:  %s", new ObjectMapper().writeValueAsString(partsDto)));

      // 2) Generate the presigned request
      PresignedCompleteMultipartUploadRequest presignedRequest = //
        generateCompleteMultipartUploadPresignedUrl(Main.getRegion(), Main.getBucketName(), fileName, uploadId, parts);

      //Extract the signed payload if present, which will be the parts that was added
      String signedPayload = presignedRequest.signedPayload() //
        .map(payload -> payload.asUtf8String()) // Convert payload to UTF-8 string
        .orElseThrow(() -> new RuntimeException("No signed payload")); // Fail if missing

      // Build the JSON response
      ObjectMapper mapper = new ObjectMapper();
      ObjectNode responseJson = mapper.createObjectNode();
      responseJson.put("url", presignedRequest.url().toString()); // Add presigned URL
      responseJson.put("payload", signedPayload); // Add signed payload

      System.out.println(String.format("[%s] [PresignedURL]  Completing upload with parts:", TimeUtils.getCurrentTimestamp()));
      parts.forEach(part -> //
      System.out.println(String.format("Part %d: ETag %s", part.partNumber(), part.eTag())));

      // Return successful response with JSON
      return Response.ok(mapper.writeValueAsString(responseJson)).build();
    } catch (S3Exception | JsonProcessingException e) {
      // Return error response if AWS or JSON processing fails
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("{\"error\": \"Failed to generate presigned URL: " + e.getMessage() + "\"}").build();
    }
  }

  // ---------------------------------------------------------

  /**
  * Builds and presigns a CompleteMultipartUploadRequest for AWS S3.
  *
  * This utility method:
  * - Constructs a CompletedMultipartUpload object with the given parts.
  * - Creates a CompleteMultipartUploadRequest with bucket, key, uploadId, and the parts payload.
  * - Signs the request using the AWS S3Presigner with a 10-minute expiration.
  *
  * This presigned request can be used by clients to finalize the multipart upload to S3
  * without needing AWS credentials directly.
  *
  * @param region      AWS region where the bucket is hosted.
  * @param bucketName  The name of the S3 bucket.
  * @param keyName     The key (filename) of the object being uploaded.
  * @param uploadId    The ID of the multipart upload session.
  * @param parts       List of CompletedPart objects representing uploaded parts.
  * @return A PresignedCompleteMultipartUploadRequest containing URL and optional payload.
  */
  public static PresignedCompleteMultipartUploadRequest generateCompleteMultipartUploadPresignedUrl( //
    Region region, String bucketName, String keyName, String uploadId, List<CompletedPart> parts) {

    try (S3Presigner s3presigner = S3Presigner.builder().credentialsProvider(DefaultCredentialsProvider.create()).region(region).build()) {

//      System.out.println("Part that is used for CompleteMultipartUpload");
//      System.out.println(parts);
//
//      Collection<CompletedPart> cleanedParts = parts.stream() //
//        .map(p -> CompletedPart.builder() //
//          .partNumber(p.partNumber()) //
//          .eTag(p.eTag().replace("\"", "")) //
//          .build()) //
//        .collect(Collectors.toList());
//
//      cleanedParts.forEach(System.out::println);
//      System.out.println(cleanedParts);

      // Build the CompletedMultipartUpload object with provided parts
      CompletedMultipartUpload completedUpload = CompletedMultipartUpload.builder() //
        .parts(parts) //
        .build();

      // Build the CompleteMultipartUploadRequest with all necessary fields
      CompleteMultipartUploadRequest completeRequest = CompleteMultipartUploadRequest.builder() //
        .bucket(bucketName) //
        .key(keyName) //
        .uploadId(uploadId) //
        .multipartUpload(completedUpload) //
        .build();

      // Build the request to presign, setting a signature validity of 10 minutes
      CompleteMultipartUploadPresignRequest presignRequest = //
        CompleteMultipartUploadPresignRequest.builder() //
          .signatureDuration(Duration.ofMinutes(10)) //
          .completeMultipartUploadRequest(completeRequest) //
          .build();

      // Return the presigned request object
      return s3presigner.presignCompleteMultipartUpload(presignRequest);
    }
  }

  // ---------------------------------------------------------

  /**
   * Data Transfer Object (DTO) used by Jackson to bind multipart upload part data from JSON.
   *
   * This DTO is designed to receive JSON input for individual parts of a multipart upload,
   * where each part is identified by a `partNumber` and its corresponding `eTag` (returned by S3).
   *
   * Example incoming JSON:
   * {
   *   "partNumber": 1,
   *   "eTag": "etag-from-s3"
   * }
   *
   * Jackson is configured to use direct field access (ignoring getters), except where explicitly annotated.
   */
  @JsonAutoDetect( //
    fieldVisibility = Visibility.ANY, // Make all fields visible to Jackson without needing getters
    getterVisibility = Visibility.NONE, // Prevent Jackson from using getters automatically
    isGetterVisibility = Visibility.NONE) // Prevent Jackson from using 'is' getters (e.g. isEnabled)
  public static class CompletedPartDTO {
    private int partNumber; // The part number (1-based index as required by S3)

    @JsonProperty("eTag") // Explicitly map to lowercase field
    private String eTag;

    // Getter for partNumber, required for serialization
    // and for mapping to AWS SDK's CompletedPart in generateCompleteMultipartUploadPresignedUrl.
    public int getPartNumber() {
      return partNumber;
    }

    // Getter for the ETag field, required for serialization
    // and for mapping to AWS SDK's CompletedPart in generateCompleteMultipartUploadPresignedUrl.
    public String getETag() {
      return eTag;
    }

    // Setter for partNumber, used by Jackson when deserializing incoming JSON into this DTO
    // Consumed in generateCompleteMultipartUploadPresignedUrl
    public void setPartNumber(int partNumber) {
      this.partNumber = partNumber;
    }

    // Setter for the ETag field, used by Jackson when deserializing incoming JSON into this DTO
    // Consumed in generateCompleteMultipartUploadPresignedUrl
    public void setETag(String eTag) {
      this.eTag = eTag;
    }
  }

}
