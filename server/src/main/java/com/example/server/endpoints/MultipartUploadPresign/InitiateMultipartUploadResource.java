package com.example.server.endpoints.MultipartUploadPresign;

import java.time.Duration;

import com.example.server.Main;
import com.example.server.TimeUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.CreateMultipartUploadPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedCreateMultipartUploadRequest;

//Uploads are now aborted if the client encounters an error.
//Future improvements to consider:
//• Tag multipart uploads with a timestamp at creation
//• Schedule a background job (e.g., Lambda, cron) to call AbortMultipartUpload
//for any upload older than a defined threshold (e.g., 1 hour)

/**
* REST endpoint in the folder 'InitiateMultipartUploadResource' for handling S3 multipart upload presigning.
*
* Exposes the following endpoints:
* <ul>
*     <li><b>GET</b>  /files/multipart-initiate/{fileName} - Starts a multipart upload</li>
*     <li><b>GET</b>  /files/multipart-presign/{fileName}/{uploadId}/{partNumber} - Returns a presigned PUT URL for each part of the upload</li>
*     <li><b>POST</b> /files/multipart-complete-presign/{fileName}/{uploadId} - Returns a presigned complete-upload request</li>
*     <li><b>GET</b>  /files/multipart-abort-presign/{fileName}/{uploadId} - Abort multipart upload")
</li>
* </ul>
*/
@Path("/files")
public class InitiateMultipartUploadResource {

  /**
   * Initiates a multipart upload in S3 and returns a presigned POST URL + payload.
   *
   * This endpoint creates a presigned URL that allows a client to start a multipart upload
   * directly to S3 without needing AWS credentials. The presigned URL is valid for 10 minutes.
    * Response:
   *  - "url": The presigned URL to initiate the upload.
   *  - "payload": Any signed payload needed by the client.
   *
   * @param fileName the object key to create in the S3 bucket
   * @return HTTP 200 with JSON { "url": "...", "payload": "..." } or 500 on error
   */
  @GET
  @Path("/multipart-initiate/{fileName}")
  @Produces(MediaType.APPLICATION_JSON)
  public Response initiateMultipartUpload(@PathParam("fileName") String fileName) {
    try {
      System.out.println(String.format("[%s] [PresignedURL]  Initiating multipart upload with presigned URL | Bucket: %s | Key: %s", TimeUtils.getCurrentTimestamp(), Main.getBucketName(), fileName));

      // Generate a presigned request to initiate a multipart upload
      PresignedCreateMultipartUploadRequest presignedRequest = //
        generateCreateMultipartUploadPresignedUrl(Main.getRegion(), Main.getBucketName(), fileName);

      // Extract the presigned URL
      String url = presignedRequest.url().toString();

      // Prepare JSON response
      ObjectMapper mapper = new ObjectMapper();
      ObjectNode responseJson = mapper.createObjectNode();
      responseJson.put("url", url);

      return Response.ok(mapper.writeValueAsString(responseJson)).build();
    } catch (S3Exception | JsonProcessingException e) {
      // Return error response if AWS SDK or JSON serialization fails
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("{\"error\": \"Failed to generate presigned URL: " + e.getMessage() + "\"}").build();
    }
  }

  /**
   * Generates a presigned CreateMultipartUploadRequest using the AWS SDK.
   *
   * This helper method builds the S3 request to start a multipart upload and signs it
   * with a 10-minute expiration time. It uses the AWS SDK's S3Presigner with default credentials..
   *
   * @param region     the AWS region (e.g., us-east-1)
   * @param bucketName the name of the S3 bucket
   * @param keyName    the key (object name) for the upload
   * @return a presigned request that can be used by a client to initiate a multipart upload
   */
  public static PresignedCreateMultipartUploadRequest generateCreateMultipartUploadPresignedUrl(Region region, String bucketName, String keyName) {

    try (S3Presigner s3presigner = S3Presigner.builder() //
      .credentialsProvider(DefaultCredentialsProvider //
        .create()) //
      .region(region) //
      .build()) {

      // Create the upload initiation request
      CreateMultipartUploadRequest createRequest = CreateMultipartUploadRequest.builder() //
        .bucket(bucketName) //
        .key(keyName) //
        .build();

      // Build a presign request that includes the upload initiation and expiration
      // CreateMultipartUploadPresignRequest requires a POST method. The client will send a POST request with no body.
      CreateMultipartUploadPresignRequest presignRequest = CreateMultipartUploadPresignRequest.builder() //
        .signatureDuration(Duration.ofMinutes(10)) //
        .createMultipartUploadRequest(createRequest) //
        .build();

      // Return the fully signed request
      return s3presigner.presignCreateMultipartUpload(presignRequest);
    }
  }
}

//List aborted parts in powershell:
//aws s3api list-multipart-uploads
//
//These uploads should be manually aborted, as they continue to consume storage.

//To clean up incomplete multipart uploads (i.e., uploads that haven't been completed or aborted),
//run the following two PowerShell commands:

//# Command 1 - set your bucket name
//$bucket = 'm3tech-thesis-2025-storage'
//
//# Command 2 - list uploads, extract Key+UploadId, then abort each
//aws s3api list-multipart-uploads --bucket $bucket ` --query 'Uploads[].[Key,UploadId]' --output text |
//ForEach-Object {
//$key, $uploadId = $_ -split "`t"
//Write-Host "Aborting upload for Key=$key UploadId=$uploadId"
//aws s3api abort-multipart-upload --bucket $bucket --key $key --upload-id $uploadId
//}
