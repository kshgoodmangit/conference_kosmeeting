package com.bjworld21.congress.publicsite;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

@RestControllerAdvice
public class PublicApiAdvice implements ResponseBodyAdvice<Object> {
    private final ObjectMapper mapper;
    public PublicApiAdvice(ObjectMapper mapper) { this.mapper = mapper; }

    @Override
    public boolean supports(MethodParameter method, Class<? extends HttpMessageConverter<?>> converter) { return true; }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter method, MediaType contentType,
                                  Class<? extends HttpMessageConverter<?>> converter,
                                  ServerHttpRequest request, ServerHttpResponse response) {
        if (!(request instanceof ServletServerHttpRequest servlet)
                || !servlet.getServletRequest().getRequestURI().startsWith("/api/public/")) return body;
        int status = response instanceof ServletServerHttpResponse actual ? actual.getServletResponse().getStatus() : 200;
        if (status == 204 || status >= 300 && status < 400) return body;
        String language = language(servlet.getServletRequest());
        ObjectNode result;
        if (body instanceof String text) {
            result = mapper.createObjectNode();
            result.put("code", PublicApiMessages.code(text, status));
            result.put("message", PublicApiMessages.translate(text, language, status));
        } else if (body == null && status >= 400) {
            result = mapper.createObjectNode();
            String code = PublicApiMessages.code(null, status);
            result.put("code", code).put("message", PublicApiMessages.message(code, language));
        } else if (body != null && !(body instanceof org.springframework.core.io.Resource)
                && !(body instanceof byte[])) {
            var tree = mapper.valueToTree(body);
            if (!(tree instanceof ObjectNode object) || !object.hasNonNull("message")) return body;
            result = object;
            if (result.hasNonNull("code") && PublicApiMessages.hasCode(result.path("code").asText())) {
                result.put("message", PublicApiMessages.message(result.path("code").asText(), language));
                return result;
            }
            String text = result.path("message").asText();
            // Read responses may intentionally have no status message to display.
            if (status >= 200 && status < 300 && text.isBlank()) return body;
            result.put("code", PublicApiMessages.code(text, status));
            result.put("message", PublicApiMessages.translate(text, language, status));
        } else return body;
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        if (StringHttpMessageConverter.class.isAssignableFrom(converter)) {
            try { return mapper.writeValueAsString(result); }
            catch (com.fasterxml.jackson.core.JsonProcessingException exception) { throw new IllegalStateException(exception); }
        }
        return result;
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> failure(Exception exception, HttpServletRequest request) throws Exception {
        if (!request.getRequestURI().startsWith("/api/public/")) throw exception;
        int status = exception instanceof ResponseStatusException response ? response.getStatusCode().value()
                : exception instanceof org.springframework.web.bind.ServletRequestBindingException
                  || exception instanceof org.springframework.web.bind.MethodArgumentNotValidException
                  || exception instanceof org.springframework.http.converter.HttpMessageNotReadableException
                  || exception instanceof IllegalArgumentException ? 400 : HttpStatus.INTERNAL_SERVER_ERROR.value();
        String code = PublicApiMessages.code(null, status);
        return ResponseEntity.status(status).body(java.util.Map.of("code", code,
                "message", PublicApiMessages.message(code, language(request))));
    }

    private String language(HttpServletRequest request) {
        Object value = request.getAttribute(PublicSiteContext.ATTRIBUTE);
        return value instanceof PublicSiteContext context ? context.language()
                : "ko".equals(request.getParameter("lang")) ? "ko" : "en";
    }
}
