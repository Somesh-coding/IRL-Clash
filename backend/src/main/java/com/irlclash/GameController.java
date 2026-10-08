package com.irlclash;
import org.springframework.http.*;import org.springframework.web.bind.annotation.*;import org.springframework.web.multipart.MultipartFile;import java.util.*;
@RestController @RequestMapping("/api") public class GameController {final GameService s;GameController(GameService s){this.s=s;}
 @GetMapping("/health") Map<String,String> health(){return Map.of("status","ok");}
 @PostMapping("/players/register") GameModels.Register register(@RequestParam String username){return s.register(username);}
 @PostMapping("/players/location") GameModels.Match location(@RequestBody GameModels.Location x){return s.locate(x);}
 @GetMapping("/battles/{id}") Map<String,Object> state(@PathVariable String id,@RequestParam String playerId){return s.state(id,playerId);}
 @PostMapping(value="/battles/{id}/photo",consumes=MediaType.MULTIPART_FORM_DATA_VALUE) Map<String,Object> photo(@PathVariable String id,@RequestParam String playerId,@RequestPart MultipartFile file)throws Exception{return s.photo(id,playerId,file);}
 @ExceptionHandler({IllegalArgumentException.class,IllegalStateException.class}) ResponseEntity<Map<String,String>> err(RuntimeException e){return ResponseEntity.badRequest().body(Map.of("error",e.getMessage()));}}
