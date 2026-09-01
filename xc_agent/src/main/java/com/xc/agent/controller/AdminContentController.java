package com.xc.agent.controller;

import com.xc.agent.common.api.ApiResponse;
import com.xc.agent.common.api.PageVO;
import com.xc.agent.common.exception.BusinessException;
import com.xc.agent.common.security.AdminRoleRequired;
import com.xc.agent.common.web.RequestIdFilter;
import com.xc.agent.model.dto.admin.AdminDTOs;
import com.xc.agent.model.vo.admin.AdminManagementVOs;
import com.xc.agent.service.admin.AdminManagementService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminContentController {
    private final AdminManagementService service;
    public AdminContentController(AdminManagementService service) { this.service=service; }

    @GetMapping("/faq-categories")
    public ApiResponse<List<AdminManagementVOs.CategoryVO>> faqCategories(HttpServletRequest r){return response(r,service.faqCategories());}
    @PostMapping("/faq-categories") @AdminRoleRequired
    public ApiResponse<AdminManagementVOs.CategoryVO> createFaqCategory(@Valid @RequestBody AdminDTOs.CategorySaveDTO b,HttpServletRequest r){return response(r,service.createFaqCategory(b));}
    @PatchMapping("/faq-categories/{id}") @AdminRoleRequired
    public ApiResponse<AdminManagementVOs.CategoryVO> updateFaqCategory(@PathVariable String id,@Valid @RequestBody AdminDTOs.CategorySaveDTO b,@RequestHeader("If-Match") String v,HttpServletRequest r){return response(r,service.updateFaqCategory(id,b,version(v)));}
    @DeleteMapping("/faq-categories/{id}") @AdminRoleRequired
    public ResponseEntity<Void> deleteFaqCategory(@PathVariable String id,@RequestHeader("If-Match") String v){service.deleteFaqCategory(id,version(v));return ResponseEntity.noContent().build();}
    @PutMapping("/faq-categories/order") @AdminRoleRequired
    public ApiResponse<List<AdminManagementVOs.CategoryVO>> reorderFaqCategories(@Valid @RequestBody AdminDTOs.CategoryOrderDTO b,HttpServletRequest r){return response(r,service.reorderFaqCategories(b));}

    @GetMapping("/faqs")
    public ApiResponse<PageVO<AdminManagementVOs.FaqVO>> faqs(@RequestParam(required=false) String keyword,@RequestParam(required=false) String categoryId,@RequestParam(required=false) String status,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize,HttpServletRequest r){return response(r,service.faqs(keyword,categoryId,status,page,pageSize));}
    @GetMapping("/faqs/{id}")
    public ApiResponse<AdminManagementVOs.FaqVO> faq(@PathVariable String id,HttpServletRequest r){return response(r,service.faq(id));}
    @PostMapping("/faqs") @AdminRoleRequired
    public ApiResponse<AdminManagementVOs.FaqVO> createFaq(@Valid @RequestBody AdminDTOs.FaqSaveDTO b,HttpServletRequest r){return response(r,service.createFaq(b));}
    @PatchMapping("/faqs/{id}") @AdminRoleRequired
    public ApiResponse<AdminManagementVOs.FaqVO> updateFaq(@PathVariable String id,@Valid @RequestBody AdminDTOs.FaqSaveDTO b,@RequestHeader("If-Match") String v,HttpServletRequest r){return response(r,service.updateFaq(id,b,version(v)));}
    @PostMapping("/faqs/{id}/publish") @AdminRoleRequired
    public ApiResponse<AdminManagementVOs.FaqVO> publishFaq(@PathVariable String id,@RequestHeader("If-Match") String v,HttpServletRequest r){return response(r,service.setFaqStatus(id,"PUBLISHED",version(v)));}
    @PostMapping("/faqs/{id}/archive") @AdminRoleRequired
    public ApiResponse<AdminManagementVOs.FaqVO> archiveFaq(@PathVariable String id,@RequestHeader("If-Match") String v,HttpServletRequest r){return response(r,service.setFaqStatus(id,"ARCHIVED",version(v)));}

    @GetMapping("/manual-categories")
    public ApiResponse<List<AdminManagementVOs.CategoryVO>> manualCategories(HttpServletRequest r){return response(r,service.manualCategories());}
    @PostMapping("/manual-categories") @AdminRoleRequired
    public ApiResponse<AdminManagementVOs.CategoryVO> createManualCategory(@Valid @RequestBody AdminDTOs.CategorySaveDTO b,HttpServletRequest r){return response(r,service.createManualCategory(b));}
    @PatchMapping("/manual-categories/{id}") @AdminRoleRequired
    public ApiResponse<AdminManagementVOs.CategoryVO> updateManualCategory(@PathVariable String id,@Valid @RequestBody AdminDTOs.CategorySaveDTO b,@RequestHeader("If-Match") String v,HttpServletRequest r){return response(r,service.updateManualCategory(id,b,version(v)));}
    @DeleteMapping("/manual-categories/{id}") @AdminRoleRequired
    public ResponseEntity<Void> deleteManualCategory(@PathVariable String id,@RequestHeader("If-Match") String v){service.deleteManualCategory(id,version(v));return ResponseEntity.noContent().build();}

    @GetMapping("/manuals")
    public ApiResponse<PageVO<AdminManagementVOs.ManualVO>> manuals(@RequestParam(required=false) String keyword,@RequestParam(required=false) String categoryId,@RequestParam(required=false) String status,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize,HttpServletRequest r){return response(r,service.manuals(keyword,categoryId,status,page,pageSize));}
    @GetMapping("/manuals/{id}")
    public ApiResponse<AdminManagementVOs.ManualVO> manual(@PathVariable String id,HttpServletRequest r){return response(r,service.manual(id));}
    @PostMapping(value="/manuals",consumes=MediaType.MULTIPART_FORM_DATA_VALUE) @AdminRoleRequired
    public ApiResponse<AdminManagementVOs.ManualVO> createManual(@RequestParam String categoryId,@RequestParam String title,@RequestParam(required=false) String summary,@RequestPart MultipartFile file,HttpServletRequest r){return response(r,service.createManual(categoryId,title,summary,file));}
    @PatchMapping("/manuals/{id}") @AdminRoleRequired
    public ApiResponse<AdminManagementVOs.ManualVO> updateManual(@PathVariable String id,@Valid @RequestBody AdminDTOs.ManualSaveDTO b,@RequestHeader("If-Match") String v,HttpServletRequest r){return response(r,service.updateManual(id,b,version(v)));}
    @PostMapping(value="/manuals/{id}/versions",consumes=MediaType.MULTIPART_FORM_DATA_VALUE) @AdminRoleRequired
    public ApiResponse<AdminManagementVOs.ManualVO> replaceManual(@PathVariable String id,@RequestPart MultipartFile file,@RequestHeader("If-Match") String v,HttpServletRequest r){return response(r,service.replaceManualFile(id,file,version(v)));}
    @PostMapping("/manuals/{id}/publish") @AdminRoleRequired
    public ApiResponse<AdminManagementVOs.ManualVO> publishManual(@PathVariable String id,@RequestHeader("If-Match") String v,HttpServletRequest r){return response(r,service.setManualStatus(id,"PUBLISHED",version(v)));}
    @PostMapping("/manuals/{id}/archive") @AdminRoleRequired
    public ApiResponse<AdminManagementVOs.ManualVO> archiveManual(@PathVariable String id,@RequestHeader("If-Match") String v,HttpServletRequest r){return response(r,service.setManualStatus(id,"ARCHIVED",version(v)));}
    @GetMapping("/manuals/{id}/file")
    public ResponseEntity<FileSystemResource> download(@PathVariable String id){var d=service.downloadManual(id);MediaType type;try{type=MediaType.parseMediaType(d.contentType());}catch(Exception e){type=MediaType.APPLICATION_OCTET_STREAM;}return ResponseEntity.ok().contentType(type).header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(d.fileName(), StandardCharsets.UTF_8).build().toString()).body(d.resource());}

    private int version(String value){try{return Integer.parseInt(value.replace("\"","").trim());}catch(RuntimeException e){throw new BusinessException("IF_MATCH_INVALID",400,"If-Match 必须是资源版本号");}}
    private <T> ApiResponse<T> response(HttpServletRequest r,T data){return new ApiResponse<>(RequestIdFilter.get(r),data);}
}
