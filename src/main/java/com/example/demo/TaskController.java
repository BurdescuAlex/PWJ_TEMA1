package com.example.demo;
import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;
import com.opencsv.exceptions.CsvException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.thoughtworks.xstream.XStream;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.hateoas.server.mvc.WebMvcLinkBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;
import java.io.FileDescriptor;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;


@RestController
@RequestMapping("api/tasks")
public class TaskController {
    @Autowired
    private TaskService service;

    @Operation(summary = "Search tasks", operationId = "getTasks")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Found tasks",
                    content = {@Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = Object[].class))}
            ),
            @ApiResponse(responseCode = "204", description = "No tasks found")
    })
    @GetMapping
    public ResponseEntity<List<Object>> getTasks (@RequestParam(required = false) String title,
                                                     @RequestParam(required = false) String description,
                                                     @RequestParam(required = false) String assignedTo,
                                                     @RequestParam(required = false) TaskModel.TaskStatus status,
                                                     @RequestParam(required = false) TaskModel.TaskSeverity severity,
                                                     @RequestHeader(required = false, name="X-Fields") String fields,
                                                     @RequestHeader(required = false, name="X-Sort") String sort) {
        List<TaskModel> tasks = service.getTasks(title, description, assignedTo, status, severity);
        if(tasks.isEmpty()) {
            return ResponseEntity.noContent().build();
        } else {
            if(sort != null && !sort.isBlank()) {
                tasks = tasks.stream().sorted((first, second) -> BaseModel.sorter(sort).compare(first, second)).collect(Collectors.toList());
            }
            List<Object> items;
            if(fields != null && !fields.isBlank()) {
                items = tasks.stream().map(task -> task.sparseFields(fields.split(","))).collect(Collectors.toList());
            } else {
                items = new ArrayList<>(tasks);
            }
            return ResponseEntity.ok(items);
        }
    }


    @Operation(summary = "Get a task", operationId = "getTask")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Found task",
                    content = {@Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = Object.class))}
            ),
            @ApiResponse(responseCode = "404", description = "No task found")
    })
    @GetMapping("/{id}")
    public ResponseEntity<Object> getTaskById(@PathVariable String id, @RequestHeader(required = false, name="X-Fields") String fields) {
        Optional<TaskModel> task = service.getTask(id);
        if(task.isEmpty()) {
            return ResponseEntity.notFound().build();
        } else {
            if(fields != null && !fields.isBlank()) {
                return ResponseEntity.ok(task.get().sparseFields(fields.split(",")));
            } else {
              return ResponseEntity.ok(task.get());
            }
        }
    }

    @Operation(summary = "Create a task", operationId = "addTask")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Task was created",
                   headers={@Header(name ="location", schema = @Schema(type = "String"))}
            ),
            @ApiResponse(responseCode = "500", description = "Something went wrong"),
            @ApiResponse(responseCode = "204", description = "Bulk tasks created")
    })
    @PostMapping
    public ResponseEntity<Void> addTask(@RequestBody String payload, @RequestHeader(required = false, name="X-Action") String action) {
        try {
            if("bulk".equals(action)) {
                for(TaskModel taskModel : new ObjectMapper().readValue(payload, TaskModel[].class)) {
                    service.addTask(taskModel);
                }
                return ResponseEntity.noContent().build();
            } else {
                TaskModel taskModel = service.addTask(new ObjectMapper().readValue(payload, TaskModel.class));
                URI uri = WebMvcLinkBuilder.linkTo(getClass()).slash(taskModel.getId()).toUri();
                return ResponseEntity.created(uri).build();
            }
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Operation(summary = "Update a task", operationId = "updateTask")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Task was updated"),
            @ApiResponse(responseCode = "500", description = "Something went wrong"),
            @ApiResponse(responseCode = "404", description = "Task not found")
    })
    @PutMapping("/{id}")
    public ResponseEntity<Void> updateTask(@PathVariable String id, @RequestBody TaskModel task) {
        try {
            if (service.updateTask(id, task)) {
                return ResponseEntity.noContent().build();
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Operation(summary = "Patch a task", operationId = "patchTask")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Task was patched"),
            @ApiResponse(responseCode = "500", description = "Something went wrong"),
            @ApiResponse(responseCode = "404", description = "Task not found")
    })
    @PatchMapping("/{id}")
    public ResponseEntity<Void> patchTask(@PathVariable String id, @RequestBody TaskModel task) {
        try {
            if (service.patchTask(id, task)) {
                return ResponseEntity.noContent().build();
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Operation(summary = "Delete a task", operationId = "deleteTask")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Task was deleted"),
            @ApiResponse(responseCode = "500", description = "Something went wrong"),
            @ApiResponse(responseCode = "404", description = "Task not found")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTask(@PathVariable String id) {
        try {
            if (service.deleteTask(id)) {
                return ResponseEntity.noContent().build();
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Operation(summary = "Check a task", operationId = "checkTask")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Task was found"),
            @ApiResponse(responseCode = "404", description = "Task was not found")
    })
    @RequestMapping(method = RequestMethod.HEAD, value ="/{id}")
    public ResponseEntity checkTask(@PathVariable String id) {
        Optional<TaskModel> taskModel = service.getTask(id);
        return taskModel.isPresent() ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }


    @Operation(summary = "Search tasks", operationId = "Tema1csv")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Found tasks",
                    content = {@Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = Object[].class))}
            ),
            @ApiResponse(responseCode = "204", description = "No tasks found")
    })
    @GetMapping("/tema1csv")
    public ResponseEntity<Void> Tema1csv (@RequestParam(required = false) String title,
                                        @RequestParam(required = false) String description,
                                        @RequestParam(required = false) String assignedTo,
                                        @RequestParam(required = false) TaskModel.TaskStatus status,
                                        @RequestParam(required = false) TaskModel.TaskSeverity severity,
                                        @RequestHeader(required = false, name="X-Fields") String fields,
                                        @RequestHeader(required = false, name="X-Sort") String sort,
                                        HttpServletResponse response) throws IOException, IllegalAccessException {
        List<TaskModel> tasks = service.getTasks(title, description, assignedTo, status, severity);
        if(tasks.isEmpty()) {
            return ResponseEntity.noContent().build();
        } else {
            if(sort != null && !sort.isBlank()) {
                tasks = tasks.stream().sorted((first, second) -> BaseModel.sorter(sort).compare(first, second)).collect(Collectors.toList());
            }
            List<Object> items;
            if(fields != null && !fields.isBlank()) {
                items = tasks.stream().map(task -> task.sparseFields(fields.split(","))).collect(Collectors.toList());
            } else {
                items = new ArrayList<>(tasks);
            }
            response.setContentType("text/csv");
            String headerKey = "Content-Disposition";
            String headerValue = String.format("attachment; filename=\"%s\"",
                    "items.csv");
            response.setHeader(headerKey, headerValue);
            CSVWriter writer = new CSVWriter(response.getWriter());
            var csvHeaders = Arrays.stream(items.get(0).getClass().getDeclaredFields()).map(field -> field.getName()).collect(Collectors.toList());
            writer.writeNext(csvHeaders.toArray(new String[0]));
            for (Object item : items) {
                Field[] itemfields = item.getClass().getDeclaredFields();
                List<String> s = new ArrayList<>();
                for (Field data : itemfields)
                {
                    data.setAccessible(true);
                    s.add(data.get(item).toString());
                }
                writer.writeNext(s.toArray(new String[0]));
            }
            writer.close();
        }
        return ResponseEntity.ok().build();
    }
    @Operation(summary = "Search tasks", operationId = "tema1XML")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Found tasks",
                    content = {@Content(mediaType = MediaType.APPLICATION_XML_VALUE)}
            ),
            @ApiResponse(responseCode = "204", description = "No tasks found")
    })
    @RequestMapping(value = "/tema1XML", method = RequestMethod.GET)
    public ResponseEntity<String> tema1XML (@RequestParam(required = false) String title,
                                          @RequestParam(required = false) String description,
                                          @RequestParam(required = false) String assignedTo,
                                          @RequestParam(required = false) TaskModel.TaskStatus status,
                                          @RequestParam(required = false) TaskModel.TaskSeverity severity,
                                          @RequestHeader(required = false, name="X-Fields") String fields,
                                          @RequestHeader(required = false, name="X-Sort") String sort) throws IOException, IllegalAccessException {
        List<TaskModel> tasks = service.getTasks(title, description, assignedTo, status, severity);
        if(tasks.isEmpty()) {
            return ResponseEntity.noContent().build();
        } else {
            if (sort != null && !sort.isBlank()) {
                tasks = tasks.stream().sorted((first, second) -> BaseModel.sorter(sort).compare(first, second)).collect(Collectors.toList());
            }
            List<Object> items;
            if (fields != null && !fields.isBlank()) {
                items = tasks.stream().map(task -> task.sparseFields(fields.split(","))).collect(Collectors.toList());
            } else {
                items = new ArrayList<>(tasks);
            }
            XStream xstream = new XStream();
            String xmldata = xstream.toXML(items);
            return ResponseEntity.ok(xmldata);
        }
    }
}
