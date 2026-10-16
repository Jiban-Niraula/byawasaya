package com.byawasaya.platform.storage;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Profile;
/** Test-only substitute; production must use encrypted S3-compatible storage. */
@Service
@Profile("test")
@ConditionalOnProperty(name="app.storage.enabled",havingValue="false")
public class InMemoryPrivateObjectStorage implements PrivateObjectStorage {
 private final ConcurrentHashMap<String,byte[]> objects=new ConcurrentHashMap<>();
 @Override public void put(String key,byte[] plaintext,String contentType){objects.put(key,plaintext.clone());}
 @Override public byte[] get(String key){byte[] value=objects.get(key);if(value==null)throw new IllegalArgumentException("Object not found.");return value.clone();}
 @Override public void delete(String key){objects.remove(key);}
}
