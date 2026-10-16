package com.byawasaya.platform.api;
import java.time.Instant; import java.util.LinkedHashMap; import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException; import org.springframework.http.HttpStatus; import org.springframework.http.ResponseEntity; import org.springframework.security.authentication.BadCredentialsException; import org.springframework.security.core.userdetails.UsernameNotFoundException; import org.springframework.web.bind.MethodArgumentNotValidException; import org.springframework.web.bind.annotation.ExceptionHandler; import org.springframework.web.bind.annotation.RestControllerAdvice; import org.springframework.web.server.ResponseStatusException;
@RestControllerAdvice
public class ApiExceptionHandler {
 @ExceptionHandler(MethodArgumentNotValidException.class) ResponseEntity<ApiError> validation(MethodArgumentNotValidException ex){Map<String,String> fields=new LinkedHashMap<>();ex.getBindingResult().getFieldErrors().forEach(e->fields.putIfAbsent(e.getField(),e.getDefaultMessage()));return error(HttpStatus.BAD_REQUEST,"Please check the submitted fields.",fields);}
 @ExceptionHandler({BadCredentialsException.class,UsernameNotFoundException.class}) ResponseEntity<ApiError> credentials(){return error(HttpStatus.UNAUTHORIZED,"Invalid username or password.",Map.of());}
 @ExceptionHandler(ResponseStatusException.class) ResponseEntity<ApiError> known(ResponseStatusException ex){HttpStatus status=HttpStatus.valueOf(ex.getStatusCode().value());return error(status,ex.getReason()==null?status.getReasonPhrase():ex.getReason(),Map.of());}
 @ExceptionHandler(DataIntegrityViolationException.class) ResponseEntity<ApiError> duplicate(){return error(HttpStatus.CONFLICT,"That value is already in use. Check the store URL or account email.",Map.of());}
 @ExceptionHandler(Exception.class) ResponseEntity<ApiError> unexpected(Exception ex){return error(HttpStatus.INTERNAL_SERVER_ERROR,"Something went wrong. Please try again.",Map.of());}
 private ResponseEntity<ApiError> error(HttpStatus status,String message,Map<String,String> fields){return ResponseEntity.status(status).body(new ApiError(Instant.now(),status.value(),status.getReasonPhrase(),message,fields));}
}
