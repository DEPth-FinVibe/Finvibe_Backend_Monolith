package depth.finvibe.common.user.infra.error;

import depth.finvibe.common.user.error.DomainErrorCode;
import depth.finvibe.common.user.error.DomainException;
import depth.finvibe.common.user.error.GlobalErrorCode;
import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingPathVariableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

  private final List<DomainErrorHttpMapper> mappers;

  @ExceptionHandler(DomainException.class)
  public ResponseEntity<ErrorResponse> handleDomainException(DomainException ex) {
    HttpStatusCode status = resolveStatus(ex.getErrorCode());

    ErrorResponse body = ErrorResponse.of(
        status.value(),
        ex.getErrorCode().getCode(),
        ex.getErrorCode().getMessage()
    );
    return ResponseEntity.status(status).body(body);
  }

  @ExceptionHandler({
      MethodArgumentNotValidException.class,
      BindException.class,
      ConstraintViolationException.class,
      MethodArgumentTypeMismatchException.class,
      HttpMessageNotReadableException.class,
      MissingServletRequestParameterException.class,
      MissingPathVariableException.class
  })
  public ResponseEntity<ErrorResponse> handleBadRequest(Exception ex) {
    List<FieldErrorResponse> fieldErrors = null;
    if (ex instanceof MethodArgumentNotValidException manvEx) {
      fieldErrors = manvEx.getBindingResult().getFieldErrors().stream()
          .map(error -> FieldErrorResponse.of(error.getField(), error.getDefaultMessage()))
          .collect(Collectors.toList());
    } else if (ex instanceof BindException bindEx) {
      fieldErrors = bindEx.getBindingResult().getFieldErrors().stream()
          .map(error -> FieldErrorResponse.of(error.getField(), error.getDefaultMessage()))
          .collect(Collectors.toList());
    } else if (ex instanceof ConstraintViolationException cvEx) {
      fieldErrors = cvEx.getConstraintViolations().stream()
          .map(violation -> FieldErrorResponse.of(
              violation.getPropertyPath().toString(),
              violation.getMessage()))
          .collect(Collectors.toList());
    }

    ErrorResponse body = ErrorResponse.of(
        HttpStatus.BAD_REQUEST.value(),
        GlobalErrorCode.INVALID_REQUEST.getCode(),
        GlobalErrorCode.INVALID_REQUEST.getMessage(),
        fieldErrors
    );
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
  }

  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  public ResponseEntity<ErrorResponse> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex) {
    ErrorResponse body = ErrorResponse.of(
        HttpStatus.METHOD_NOT_ALLOWED.value(),
        GlobalErrorCode.METHOD_NOT_ALLOWED.getCode(),
        GlobalErrorCode.METHOD_NOT_ALLOWED.getMessage()
    );
    return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(body);
  }

  @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
  public ResponseEntity<ErrorResponse> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException ex) {
    ErrorResponse body = ErrorResponse.of(
        HttpStatus.UNSUPPORTED_MEDIA_TYPE.value(),
        GlobalErrorCode.UNSUPPORTED_MEDIA_TYPE.getCode(),
        GlobalErrorCode.UNSUPPORTED_MEDIA_TYPE.getMessage()
    );
    return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(body);
  }

  @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
  public ResponseEntity<ErrorResponse> handleNotAcceptable(HttpMediaTypeNotAcceptableException ex) {
    ErrorResponse body = ErrorResponse.of(
        HttpStatus.NOT_ACCEPTABLE.value(),
        GlobalErrorCode.NOT_ACCEPTABLE.getCode(),
        GlobalErrorCode.NOT_ACCEPTABLE.getMessage()
    );
    return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE).body(body);
  }

  @ExceptionHandler(NoHandlerFoundException.class)
  public ResponseEntity<ErrorResponse> handleNotFound(NoHandlerFoundException ex) {
    ErrorResponse body = ErrorResponse.of(
        HttpStatus.NOT_FOUND.value(),
        GlobalErrorCode.NOT_FOUND.getCode(),
        GlobalErrorCode.NOT_FOUND.getMessage()
    );
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
  }

  @ExceptionHandler(ResponseStatusException.class)
  public ResponseEntity<ErrorResponse> handleResponseStatus(ResponseStatusException ex) {
    HttpStatusCode status = ex.getStatusCode();
    GlobalErrorCode code = GlobalErrorCode.fromStatus(status);
    ErrorResponse body = ErrorResponse.of(
        status.value(),
        code.getCode(),
        code.getMessage()
    );
    return ResponseEntity.status(status).body(body);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
    ErrorResponse body = ErrorResponse.of(
        HttpStatus.INTERNAL_SERVER_ERROR.value(),
        GlobalErrorCode.INTERNAL_SERVER_ERROR.getCode(),
        GlobalErrorCode.INTERNAL_SERVER_ERROR.getMessage()
    );

    log.error("Unhandled exception occurred: ", ex);

    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
  }

  private HttpStatusCode resolveStatus(DomainErrorCode code) {
    return mappers.stream()
        .filter(mapper -> mapper.supports(code))
        .findFirst()
        .map(mapper -> mapper.toStatus(code))
        .orElse(HttpStatus.INTERNAL_SERVER_ERROR);
  }
}
