package com.company.module.document.service;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 서류관리 서비스
 * Flask: /api/doc/* → DOC_FOLDER, DOC_FILE + 로컬 파일 저장
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentService {

    private final JdbcTemplate jdbc;

    @Value("${doc.upload.base-path:/home/user/webapp/uploads}")
    private String uploadBasePath;

    private static final DateTimeFormatter YMDFORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter HMSFORMAT = DateTimeFormatter.ofPattern("HHmmss");

    // ── 폴더 목록 조회 ─────────────────────────────────────────────
    public Map<String, Object> getFolders(Long parentId) {
        try {
            List<Map<String, Object>> rows;
            if (parentId != null) {
                rows = jdbc.queryForList(
                    "SELECT f.*, " +
                    "       (SELECT COUNT(*) FROM DOC_FILE df WHERE df.FOLDER_ID=f.FOLDER_ID AND df.DEL_YN='N') AS FILE_CNT " +
                    "FROM DOC_FOLDER f WHERE f.PARENT_ID=? AND f.DEL_YN='N' ORDER BY f.SORT_SEQ, f.FOLDER_NM",
                    parentId
                );
            } else {
                // 전체 트리 반환
                rows = jdbc.queryForList(
                    "SELECT f.*, " +
                    "       (SELECT COUNT(*) FROM DOC_FILE df WHERE df.FOLDER_ID=f.FOLDER_ID AND df.DEL_YN='N') AS FILE_CNT " +
                    "FROM DOC_FOLDER f WHERE f.DEL_YN='N' ORDER BY f.PARENT_ID, f.SORT_SEQ, f.FOLDER_NM"
                );
            }
            return Map.of("ok", true, "folders", rows);
        } catch (Exception e) {
            return Map.of("ok", false, "error", e.getMessage());
        }
    }

    // ── 폴더 생성 ──────────────────────────────────────────────────
    @Transactional
    public Map<String, Object> createFolder(Map<String, Object> body) {
        String folderNm = (String) body.get("folder_nm");
        if (folderNm == null || folderNm.isBlank())
            return Map.of("ok", false, "error", "폴더명 필수");

        Long parentId = body.get("parent_id") != null
            ? Long.valueOf(body.get("parent_id").toString()) : null;
        String today = LocalDate.now().format(YMDFORMAT);
        String now   = LocalDateTime.now().format(HMSFORMAT);

        try {
            jdbc.update(
                "INSERT INTO DOC_FOLDER (FOLDER_NM, PARENT_ID, SORT_SEQ, CREDAT, CRETIM, LMODAT, DEL_YN) " +
                "VALUES (?,?,?,?,?,?,?)",
                folderNm.trim(), parentId, 0, today, now, today, "N"
            );
            Long newId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
            return Map.of("ok", true, "folder_id", newId);
        } catch (Exception e) {
            return Map.of("ok", false, "error", e.getMessage());
        }
    }

    // ── 폴더 이름 변경 ─────────────────────────────────────────────
    @Transactional
    public Map<String, Object> renameFolder(Map<String, Object> body) {
        Long folderId = body.get("folder_id") != null
            ? Long.valueOf(body.get("folder_id").toString()) : null;
        String newNm = (String) body.get("folder_nm");
        if (folderId == null || newNm == null || newNm.isBlank())
            return Map.of("ok", false, "error", "folder_id, folder_nm 필수");

        String today = LocalDate.now().format(YMDFORMAT);
        try {
            jdbc.update("UPDATE DOC_FOLDER SET FOLDER_NM=?, LMODAT=? WHERE FOLDER_ID=?",
                        newNm.trim(), today, folderId);
            return Map.of("ok", true);
        } catch (Exception e) {
            return Map.of("ok", false, "error", e.getMessage());
        }
    }

    // ── 폴더 삭제 (하위 파일 포함 소프트 삭제) ──────────────────────
    @Transactional
    public Map<String, Object> deleteFolder(Long folderId) {
        String today = LocalDate.now().format(YMDFORMAT);
        try {
            // 하위 파일 소프트 삭제
            jdbc.update("UPDATE DOC_FILE SET DEL_YN='Y', LMODAT=? WHERE FOLDER_ID=?", today, folderId);
            // 폴더 소프트 삭제
            jdbc.update("UPDATE DOC_FOLDER SET DEL_YN='Y', LMODAT=? WHERE FOLDER_ID=?", today, folderId);
            return Map.of("ok", true);
        } catch (Exception e) {
            return Map.of("ok", false, "error", e.getMessage());
        }
    }

    // ── 파일 목록 조회 ─────────────────────────────────────────────
    public Map<String, Object> getFiles(Long folderId) {
        try {
            List<Map<String, Object>> rows;
            if (folderId != null) {
                rows = jdbc.queryForList(
                    "SELECT * FROM DOC_FILE WHERE FOLDER_ID=? AND DEL_YN='N' ORDER BY CREDAT DESC, FILE_ID DESC",
                    folderId
                );
            } else {
                rows = jdbc.queryForList(
                    "SELECT * FROM DOC_FILE WHERE DEL_YN='N' ORDER BY CREDAT DESC, FILE_ID DESC"
                );
            }
            return Map.of("ok", true, "files", rows);
        } catch (Exception e) {
            return Map.of("ok", false, "error", e.getMessage());
        }
    }

    // ── 파일 업로드 ────────────────────────────────────────────────
    @Transactional
    public Map<String, Object> upload(MultipartFile file, Long folderId, String note) {
        if (file == null || file.isEmpty())
            return Map.of("ok", false, "error", "파일 필수");

        String today    = LocalDate.now().format(YMDFORMAT);
        String now      = LocalDateTime.now().format(HMSFORMAT);
        String origName = file.getOriginalFilename();
        String ext      = "";
        if (origName != null && origName.contains("."))
            ext = origName.substring(origName.lastIndexOf("."));

        // 저장 경로: uploadBasePath/{today}/{UUID}{ext}
        String savedName = UUID.randomUUID().toString() + ext;
        Path dirPath  = Paths.get(uploadBasePath, today);
        Path filePath = dirPath.resolve(savedName);

        try {
            Files.createDirectories(dirPath);
            file.transferTo(filePath.toFile());

            jdbc.update(
                "INSERT INTO DOC_FILE (FOLDER_ID, FILE_NM, FILE_PATH, FILE_SIZE, FILE_TYPE, FILE_EXT, " +
                "NOTE, CREDAT, CRETIM, CREUSR, LMODAT, DEL_YN, DOWNLOAD_CNT) " +
                "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)",
                folderId, origName, filePath.toString(), file.getSize(),
                file.getContentType(), ext,
                note, today, now, "SYSTEM", today, "N", 0
            );
            Long newId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
            return Map.of("ok", true, "file_id", newId, "file_nm", origName);
        } catch (Exception e) {
            log.error("upload error: {}", e.getMessage());
            return Map.of("ok", false, "error", e.getMessage());
        }
    }

    // ── 파일 조회 (다운로드/미리보기 공통) ────────────────────────
    public FileResult getFile(Long fileId, boolean inline) {
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM DOC_FILE WHERE FILE_ID=? AND DEL_YN='N'", fileId
            );
            if (rows.isEmpty()) return FileResult.notFound();

            Map<String, Object> row = rows.get(0);
            String filePath = (String) row.get("FILE_PATH");
            String fileNm   = (String) row.get("FILE_NM");
            String fileType = (String) row.getOrDefault("FILE_TYPE", "application/octet-stream");

            File f = new File(filePath);
            if (!f.exists()) return FileResult.notFound();

            // 다운로드 카운트 증가
            if (!inline) {
                jdbc.update("UPDATE DOC_FILE SET DOWNLOAD_CNT=DOWNLOAD_CNT+1 WHERE FILE_ID=?", fileId);
            }

            String encoded = URLEncoder.encode(fileNm, StandardCharsets.UTF_8).replace("+", "%20");
            return FileResult.found(new FileSystemResource(f), fileType, encoded);
        } catch (Exception e) {
            log.error("getFile error: {}", e.getMessage());
            return FileResult.notFound();
        }
    }

    // ── 파일 삭제 (소프트 삭제 + 실제 파일 삭제) ─────────────────
    @Transactional
    public Map<String, Object> deleteFile(Long fileId) {
        String today = LocalDate.now().format(YMDFORMAT);
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT FILE_PATH FROM DOC_FILE WHERE FILE_ID=?", fileId
            );
            if (!rows.isEmpty()) {
                String filePath = (String) rows.get(0).get("FILE_PATH");
                try { Files.deleteIfExists(Paths.get(filePath)); } catch (Exception ignored) {}
            }
            jdbc.update("UPDATE DOC_FILE SET DEL_YN='Y', LMODAT=? WHERE FILE_ID=?", today, fileId);
            return Map.of("ok", true);
        } catch (Exception e) {
            return Map.of("ok", false, "error", e.getMessage());
        }
    }

    // ── 파일 정보 수정 ─────────────────────────────────────────────
    @Transactional
    public Map<String, Object> updateFile(Map<String, Object> body) {
        Long fileId = body.get("file_id") != null
            ? Long.valueOf(body.get("file_id").toString()) : null;
        if (fileId == null) return Map.of("ok", false, "error", "file_id 필수");

        String today = LocalDate.now().format(YMDFORMAT);
        try {
            String fileNm = body.containsKey("file_nm") ? (String) body.get("file_nm") : null;
            String note   = body.containsKey("note")    ? (String) body.get("note")    : null;
            Long folderId = body.get("folder_id") != null
                ? Long.valueOf(body.get("folder_id").toString()) : null;

            if (fileNm != null)
                jdbc.update("UPDATE DOC_FILE SET FILE_NM=?, LMODAT=? WHERE FILE_ID=?", fileNm.trim(), today, fileId);
            if (note != null)
                jdbc.update("UPDATE DOC_FILE SET NOTE=?, LMODAT=? WHERE FILE_ID=?", note.trim(), today, fileId);
            if (folderId != null)
                jdbc.update("UPDATE DOC_FILE SET FOLDER_ID=?, LMODAT=? WHERE FILE_ID=?", folderId, today, fileId);
            return Map.of("ok", true);
        } catch (Exception e) {
            return Map.of("ok", false, "error", e.getMessage());
        }
    }

    // ── 파일 결과 VO ──────────────────────────────────────────────
    @Getter
    public static class FileResult {
        private final boolean  found;
        private final Resource resource;
        private final String   contentType;
        private final String   encodedFileName;

        private FileResult(boolean found, Resource resource, String contentType, String encodedFileName) {
            this.found = found; this.resource = resource;
            this.contentType = contentType; this.encodedFileName = encodedFileName;
        }
        public static FileResult notFound() { return new FileResult(false, null, null, null); }
        public static FileResult found(Resource r, String ct, String fn) {
            return new FileResult(true, r, ct, fn);
        }
    }
}
