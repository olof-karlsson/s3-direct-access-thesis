package com.example.server.STSutil;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.github.cdimascio.dotenv.Dotenv;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.model.AssumeRoleRequest;
import software.amazon.awssdk.services.sts.model.AssumeRoleResponse;
import software.amazon.awssdk.services.sts.model.Credentials;

public class STSTokenCreator {
  static Dotenv dotenv = Dotenv.load();

  private static final String IAMrole = dotenv.get("AWS_IAM_ROLE_ACCESSPOINT");
  private static final String userID = dotenv.get("AWS_USER_ID");
  private static final String accesspointName = dotenv.get("AWS_NAME_ACCESSPOINT");
  private static final String accessKeyId = dotenv.get("AWS_ACCESS_KEY_ID");
  private static final String secretAccessKey = dotenv.get("AWS_SECRET_ACCESS_KEY");
  private static final Region region = Region.EU_NORTH_1;
  private static final String roleSessionName = "user-session-name";
  private static final String roleArnTemplate = "arn:aws:iam::%s:role/%s";
  private static final StsClient stsClient = StsClient.builder() //
    .region(region).credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKeyId, secretAccessKey))).build();

  // Cache credentials per client
  private static final Map<String, Credentials> credentialsCache = new ConcurrentHashMap<>();

  public static Credentials generateSTSToken(String clientId) {
    // Check the cache for existing credentials
    Credentials cachedCredentials = credentialsCache.get(clientId);

    // If credentials are not cached or have expired, generate new ones
    if (cachedCredentials != null && !areCredentialsExpired(cachedCredentials)) {
      return cachedCredentials;
    }

    // Format the role ARN and the scoped resource ARN for the access point
    String formattedRoleArn = String.format(roleArnTemplate, userID, IAMrole);
    //String scopedResourceArn = String.format("arn:aws:s3:::flippingbucket/%s/*", clientId);
    String scopedResourceArn = String.format("arn:aws:s3:%s:%s:accesspoint/%s/object/%s/*", region, userID, accesspointName, clientId);

    // Define the session policy to allow s3:PutObject on the specified access point
    String sessionPolicy = String.format("""
      {
        "Version": "2012-10-17",
        "Statement": [
          {
            "Effect": "Allow",
            "Action": ["s3:GetObject", "s3:PutObject"],
            "Resource": "%s"
          }
        ]
      }
      """, scopedResourceArn);

    System.out.println("SCOPE RESROUSE: " + scopedResourceArn);
    // Build the AssumeRoleRequest with the session policy
    AssumeRoleRequest assumeRoleRequest = AssumeRoleRequest.builder() //
      .roleArn(formattedRoleArn) //
      .policy(sessionPolicy) //
      .roleSessionName(roleSessionName + "-" + clientId) //
      .durationSeconds(3600) // Temporary credentials valid for 1 hour
      .build();

    try {
      // Assume the role and get temporary credentials
      AssumeRoleResponse assumeRoleResponse = stsClient.assumeRole(assumeRoleRequest);
      Credentials newCredentials = assumeRoleResponse.credentials();

      // Cache the credentials for the client
      credentialsCache.put(clientId, newCredentials);

      System.out.println("Generated new temporary credentials for client: " + clientId);
      return newCredentials;
    } catch (Exception e) {
      System.err.println("Failed to assume role: " + e.getMessage());
      e.printStackTrace();
      return null;
    }
  }

  private static boolean areCredentialsExpired(Credentials credentials) {
    Instant expiration = credentials.expiration();
    Duration timeLeft = Duration.between(Instant.now(), expiration);
    return timeLeft.isNegative() || timeLeft.toMinutes() < 5; // Refresh if <5 minutes remaining
  }
}
