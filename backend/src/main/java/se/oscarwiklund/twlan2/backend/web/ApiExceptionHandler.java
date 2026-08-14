package se.oscarwiklund.twlan2.backend.web;

import se.oscarwiklund.twlan2.backend.service.AuthService;
import se.oscarwiklund.twlan2.backend.service.BuildService;
import se.oscarwiklund.twlan2.backend.service.MarketService;
import se.oscarwiklund.twlan2.backend.service.MovementService;
import se.oscarwiklund.twlan2.backend.service.NobleService;
import se.oscarwiklund.twlan2.backend.service.ResearchService;
import se.oscarwiklund.twlan2.backend.service.TrainService;
import se.oscarwiklund.twlan2.backend.service.TribeService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(AuthService.AuthException.class)
    public ResponseEntity<Map<String, String>> handleAuth(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(GameFacade.NotJoinedException.class)
    public ResponseEntity<Map<String, String>> handleNotJoined(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler({BuildService.BuildException.class, TrainService.TrainException.class,
            MovementService.MovementException.class, NobleService.NobleException.class, ResearchService.ResearchException.class, TribeService.TribeException.class, MarketService.MarketException.class,
            IllegalArgumentException.class})
    public ResponseEntity<Map<String, String>> handleGameException(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", ex.getMessage()));
    }
}
