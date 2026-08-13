package com.company.module.document.controller;

import com.company.module.document.service.DocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * 서류관리 REST Controller
 * Flask: /api/doc/* → /api/doc/* (경로 동일 유지)
 */
@RestController
@RequestMapping("/api/doc")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

    // ── 폴더 조회 ──────────────────────────────────────────────────
    @GetMapping("/folders")
    public ResponseEntity<Map<String, Object>> getFolders(
            @RequestParam(required = false) Long parentId) {
        return ResponseEntity.ok(documentService.getFolders(parentId));
    }

    // ── 폴더 생성 ──────────────────────────────────────────────────
    @PostMapping("/folders/create")
    public ResponseEntity<Map<String, Object>> createFolder(@RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(documentService.createFolder(body));
    }

    // ── 폴더 이름 변경 ─────────────────────────────────────────────
    @PostMapping("/folders/rename")
    public ResponseEntity<Map<String, Object>> renameFolder(@RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(documentService.renameFolder(body));
    }

    // ── 폴더 삭제 ──────────────────────────────────────────────────
    @PostMapping("/folders/delete")
    public ResponseEntity<Map<String, Object>> deleteFolder(@RequestBody Map<String, Object> body) {
        Long folderId = Long.valueOf(body.get("folder_id").toString());
        return ResponseEntity.ok(documentService.deleteFolder(folderId));
    }

    // ── 파일 목록 조회 (운행일자/업로드일시/키워드 필터) ──────────
    @GetMapping("/files")
    public ResponseEntity<Map<String, Object>> getFiles(
            @RequestParam(value = "folder_id", required = false) Long folderId,
            @RequestParam(value = "op_from", required = false) String opFrom,
            @RequestParam(value = "op_to",   required = false) String opTo,
            @RequestParam(value = "up_from", required = false) String upFrom,
            @RequestParam(value = "up_to",   required = false) String upTo,
            @RequestParam(value = "kw",      required = false) String kw) {
        return ResponseEntity.ok(documentService.getFiles(folderId, opFrom, opTo, upFrom, upTo, kw));
    }

    // ── 파일 업로드 (다중 파일 + 운행일자) ────────────────────────
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> upload(
            @RequestParam("file") java.util.List<MultipartFile> files,
            @RequestParam(value = "folder_id", required = false) Long folderId,
            @RequestParam(value = "op_date", required = false) String opDate,
            @RequestParam(value = "note", required = false) String note) {
        return ResponseEntity.ok(documentService.upload(files, folderId, opDate, note));
    }

    // ── 파일 다운로드 ──────────────────────────────────────────────
    @GetMapping("/download/{fileId}")
    public ResponseEntity<Resource> download(@PathVariable Long fileId) {
        DocumentService.FileResult result = documentService.getFile(fileId, false);
        if (!result.isFound()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename=\"" + result.getEncodedFileName() + "\"")
            .contentType(MediaType.parseMediaType(result.getContentType()))
            .body(result.getResource());
    }

    // ── 파일 미리보기 ──────────────────────────────────────────────
    @GetMapping("/preview/{fileId}")
    public ResponseEntity<Resource> preview(@PathVariable Long fileId) {
        DocumentService.FileResult result = documentService.getFile(fileId, true);
        if (!result.isFound()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION,
                    "inline; filename=\"" + result.getEncodedFileName() + "\"")
            .contentType(MediaType.parseMediaType(result.getContentType()))
            .body(result.getResource());
    }

    // ── 파일 삭제 ──────────────────────────────────────────────────
    @PostMapping("/delete")
    public ResponseEntity<Map<String, Object>> deleteFile(@RequestBody Map<String, Object> body) {
        Long fileId = Long.valueOf(body.get("file_id").toString());
        return ResponseEntity.ok(documentService.deleteFile(fileId));
    }

    // ── 파일 정보 수정 ─────────────────────────────────────────────
    @PostMapping("/update")
    public ResponseEntity<Map<String, Object>> updateFile(@RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(documentService.updateFile(body));
    }
}
