package com.byawasaya.platform.storage;

import java.io.ByteArrayInputStream; import java.security.SecureRandom; import java.util.Base64; import javax.crypto.Cipher; import javax.crypto.spec.GCMParameterSpec; import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value; import org.springframework.stereotype.Service; import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials; import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider; import software.amazon.awssdk.core.sync.RequestBody; import software.amazon.awssdk.regions.Region; import software.amazon.awssdk.services.s3.S3Client; import software.amazon.awssdk.services.s3.S3Configuration; import software.amazon.awssdk.services.s3.model.*; import java.net.URI;

/** Private S3-compatible storage with application-side AES-256-GCM encryption before upload. */
@Service
@ConditionalOnProperty(name="app.storage.enabled",havingValue="true",matchIfMissing=true)
public class S3PrivateObjectStorage implements PrivateObjectStorage {
 private static final byte[] MAGIC=new byte[]{'B','Y','W','1'};
 private final S3Client s3; private final String bucket; private final SecretKeySpec key; private final SecureRandom random=new SecureRandom();
 public S3PrivateObjectStorage(@Value("${app.storage.endpoint}")String endpoint,@Value("${app.storage.region:ap-south-1}")String region,@Value("${app.storage.access-key}")String access,@Value("${app.storage.secret-key}")String secret,@Value("${app.storage.bucket:byawasaya-private}")String bucket,@Value("${app.storage.encryption-key-base64}")String keyB64,@Value("${app.storage.path-style:true}")boolean pathStyle){
  byte[] raw=Base64.getDecoder().decode(keyB64);if(raw.length!=32)throw new IllegalStateException("APP_STORAGE_ENCRYPTION_KEY_BASE64 must decode to exactly 32 bytes.");this.key=new SecretKeySpec(raw,"AES");this.bucket=bucket;
  this.s3=S3Client.builder().endpointOverride(URI.create(endpoint)).region(Region.of(region)).credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(access,secret))).serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(pathStyle).build()).build();
  try{s3.headBucket(HeadBucketRequest.builder().bucket(bucket).build());}catch(S3Exception missing){if(missing.statusCode()==404||missing.statusCode()==403){try{s3.createBucket(CreateBucketRequest.builder().bucket(bucket).build());}catch(S3Exception exists){if(exists.statusCode()!=409)throw exists;}}else throw missing;}
 }
 @Override public void put(String objectKey,byte[] plaintext,String contentType){byte[] encrypted=encrypt(plaintext);s3.putObject(PutObjectRequest.builder().bucket(bucket).key(objectKey).contentType("application/octet-stream").metadata(java.util.Map.of("content-type-original",safeMeta(contentType),"encryption","AES-256-GCM")).build(),RequestBody.fromBytes(encrypted));}
 @Override public byte[] get(String objectKey){return decrypt(s3.getObjectAsBytes(GetObjectRequest.builder().bucket(bucket).key(objectKey).build()).asByteArray());}
 @Override public void delete(String objectKey){s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(objectKey).build());}
 private byte[] encrypt(byte[] plaintext){try{byte[] iv=new byte[12];random.nextBytes(iv);Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,key,new GCMParameterSpec(128,iv));byte[] data=c.doFinal(plaintext);byte[] out=new byte[MAGIC.length+iv.length+data.length];System.arraycopy(MAGIC,0,out,0,MAGIC.length);System.arraycopy(iv,0,out,MAGIC.length,iv.length);System.arraycopy(data,0,out,MAGIC.length+iv.length,data.length);return out;}catch(Exception e){throw new IllegalStateException("Could not encrypt private document.",e);}}
 private byte[] decrypt(byte[] input){try{if(input.length<32||input[0]!='B'||input[1]!='Y'||input[2]!='W'||input[3]!='1')throw new IllegalStateException("Stored object has an invalid encryption envelope.");byte[] iv=java.util.Arrays.copyOfRange(input,4,16);byte[] data=java.util.Arrays.copyOfRange(input,16,input.length);Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,key,new GCMParameterSpec(128,iv));return c.doFinal(data);}catch(Exception e){throw new IllegalStateException("Could not decrypt private document.",e);}}
 private String safeMeta(String s){if(s==null)return "application/octet-stream";String value=s.replaceAll("[^a-zA-Z0-9./+-]","_");return value.substring(0,Math.min(100,value.length()));}
}
