package com.byawasaya.platform.storage;
public interface PrivateObjectStorage { void put(String key, byte[] plaintext, String contentType); byte[] get(String key); void delete(String key); }
