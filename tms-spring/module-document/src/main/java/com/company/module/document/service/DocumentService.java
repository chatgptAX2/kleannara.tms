package com.company.module.document.service;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
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
import java.sql.Types;
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
public class DocumentService {

    private final JdbcTemplate jdbc;

    @Value("${doc.upload.base-path:/home/user/webapp/uploads}")
    private String uploadBasePath;

    public DocumentService(@Qualifier("tmsJdbcTemplate") JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final DateTimeFormatter YMDFORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter HMSFORMAT = DateTimeFormatter.ofPattern("HHmmss");

    /* DOC_FILE.OP_DATE(운행일자) 컬럼 존재 여부 캐시.
       운영 DB에 아직 컬럼 추가 SQL(FIX_DOC_FILE_ADD_OP_DATE.sql)이 적용되지 않았을 수 있어,
       런타임에 컬럼 존재를 감지하여 SQL 을 자동 적응시킨다(ORA-00904 방지). */
    private volatile Boolean opDateColExists = null;

    private boolean hasOpDateColumn() {
        Boolean cached = opDateColExists;
        if (cached != null) return cached;
        boolean exists = false;
        try {
            Integer cnt = jdbc.queryForObject(
                "SELECT COUNT(*) FROM ALL_TAB_COLUMNS " +
                "WHERE OWNER='KNRAWMS' AND TABLE_NAME='DOC_FILE' AND COLUMN_NAME='OP_DATE'",
                Integer.class);
            exists = cnt != null && cnt > 0;
        } catch (Exception e) {
            /* ALL_TAB_COLUMNS 조회 실패(권한/비Oracle 등) 시 안전하게 미존재로 간주 */
            log.warn("hasOpDateColumn detect failed, assume absent: {}", e.getMessage());
            exists = false;
        }
        opDateColExists = exists;
        return exists;
    }

    // ── 폴더 목록 조회 ─────────────────────────────────────────────
    public Map<String, Object> getFolders(Long parentId) {
        try {
            List<Map<String, Object>> rows;
            if (parentId != null) {
                rows = jdbc.queryForList(
                    "SELECT f.*, " +
                    "       (SELECT COUNT(*) FROM KNRAWMS.DOC_FILE df WHERE df.FOLDER_ID=f.FOLDER_ID AND df.DEL_YN='N') AS FILE_CNT " +
                    "FROM KNRAWMS.DOC_FOLDER f WHERE f.PARENT_ID=? AND f.DEL_YN='N' ORDER BY f.SORT_SEQ, f.FOLDER_NM",
                    parentId
                );
            } else {
                // 전체 트리 반환
                rows = jdbc.queryForList(
                    "SELECT f.*, " +
                    "       (SELECT COUNT(*) FROM KNRAWMS.DOC_FILE df WHERE df.FOLDER_ID=f.FOLDER_ID AND df.DEL_YN='N') AS FILE_CNT " +
                    "FROM KNRAWMS.DOC_FOLDER f WHERE f.DEL_YN='N' ORDER BY f.PARENT_ID, f.SORT_SEQ, f.FOLDER_NM"
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
            /* [ORA-02289 FIX] SEQ_DOC_FOLDER 시퀀스가 운영 Oracle 에 존재하지 않아
               NEXTVAL 호출 시 INSERT 실행 실패(ORA-02289: sequence does not exist).
               → 타 모듈 표준과 동일하게 MAX(FOLDER_ID)+1 로 채번한다.
               [ORA-18734 FIX] parent_id=null(루트 폴더) 등은 argTypes(java.sql.Types) 명시. */
            Long newId = jdbc.queryForObject(
                "SELECT NVL(MAX(FOLDER_ID),0)+1 FROM KNRAWMS.DOC_FOLDER", Long.class);
            jdbc.update(
                "INSERT INTO KNRAWMS.DOC_FOLDER (FOLDER_ID, FOLDER_NM, PARENT_ID, SORT_SEQ, CREDAT, CRETIM, LMODAT, DEL_YN) " +
                "VALUES (?,?,?,?,?,?,?,?)",
                new Object[]{ newId, folderNm.trim(), parentId, 0, today, now, today, "N" },
                new int[]{ Types.NUMERIC, Types.VARCHAR, Types.NUMERIC, Types.NUMERIC,
                           Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR }
            );
            return Map.of("ok", true, "folder_id", newId);
        } catch (Exception e) {
            log.error("createFolder error: {}", e.getMessage());
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
            jdbc.update("UPDATE KNRAWMS.DOC_FOLDER SET FOLDER_NM=?, LMODAT=? WHERE FOLDER_ID=?",
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
            jdbc.update("UPDATE KNRAWMS.DOC_FILE SET DEL_YN='Y', LMODAT=? WHERE FOLDER_ID=?", today, folderId);
            // 폴더 소프트 삭제
            jdbc.update("UPDATE KNRAWMS.DOC_FOLDER SET DEL_YN='Y', LMODAT=? WHERE FOLDER_ID=?", today, folderId);
            return Map.of("ok", true);
        } catch (Exception e) {
            return Map.of("ok", false, "error", e.getMessage());
        }
    }

    // ── 파일 목록 조회 (프론트 필드명 alias + 운행일자/업로드일시/키워드 필터) ──
    public Map<String, Object> getFiles(Long folderId, String opFrom, String opTo,
                                        String upFrom, String upTo, String kw) {
        try {
            boolean hasOpDate = hasOpDateColumn();
            /* 프론트(_dmRenderFileTable)가 기대하는 필드명으로 alias:
               ORIG_NM(파일명)=FILE_NM, UPLOAD_DAT=CREDAT, UPLOAD_TIM=CRETIM, OP_DATE=운행일자.
               OP_DATE 컬럼 미존재 DB 대비: 있으면 실제 컬럼, 없으면 NULL AS OP_DATE 로 대체. */
            StringBuilder sql = new StringBuilder(
                "SELECT FILE_ID, FOLDER_ID, FILE_NM AS ORIG_NM, FILE_NM, FILE_PATH, FILE_SIZE, " +
                "FILE_TYPE, FILE_EXT, NOTE, " +
                (hasOpDate ? "OP_DATE" : "NULL AS OP_DATE") + ", " +
                "CREDAT AS UPLOAD_DAT, CRETIM AS UPLOAD_TIM, " +
                "CREDAT, CRETIM, DOWNLOAD_CNT " +
                "FROM KNRAWMS.DOC_FILE WHERE DEL_YN='N' ");
            List<Object> args = new ArrayList<>();
            List<Integer> types = new ArrayList<>();
            if (folderId != null) { sql.append("AND FOLDER_ID=? ");            args.add(folderId); types.add(Types.NUMERIC); }
            /* OP_DATE 필터는 컬럼이 있을 때만 적용(없으면 무시) */
            if (hasOpDate && opFrom != null && !opFrom.isBlank()) { sql.append("AND OP_DATE >= ? "); args.add(opFrom); types.add(Types.VARCHAR); }
            if (hasOpDate && opTo   != null && !opTo.isBlank())   { sql.append("AND OP_DATE <= ? "); args.add(opTo);   types.add(Types.VARCHAR); }
            if (upFrom != null && !upFrom.isBlank()) { sql.append("AND CREDAT  >= ? "); args.add(upFrom); types.add(Types.VARCHAR); }
            if (upTo   != null && !upTo.isBlank())   { sql.append("AND CREDAT  <= ? "); args.add(upTo);   types.add(Types.VARCHAR); }
            if (kw     != null && !kw.isBlank())     { sql.append("AND (FILE_NM LIKE ? OR NOTE LIKE ?) "); args.add("%"+kw+"%"); types.add(Types.VARCHAR); args.add("%"+kw+"%"); types.add(Types.VARCHAR); }
            sql.append("ORDER BY CREDAT DESC, FILE_ID DESC");

            List<Map<String, Object>> rows = jdbc.queryForList(
                sql.toString(), args.toArray(),
                types.stream().mapToInt(Integer::intValue).toArray());
            return Map.of("ok", true, "files", rows);
        } catch (Exception e) {
            log.error("getFiles error: {}", e.getMessage());
            return Map.of("ok", false, "error", e.getMessage());
        }
    }

    // ── 파일 업로드 (다중 파일 지원 + 운행일자) ───────────────────
    @Transactional
    public Map<String, Object> upload(List<MultipartFile> files, Long folderId,
                                      String opDate, String note) {
        if (files == null || files.isEmpty())
            return Map.of("ok", false, "error", "파일 필수");

        String today = LocalDate.now().format(YMDFORMAT);
        String now   = LocalDateTime.now().format(HMSFORMAT);
        String opd   = (opDate != null && !opDate.isBlank()) ? opDate.trim() : null;

        boolean hasOpDate = hasOpDateColumn();
        List<Map<String, Object>> saved = new ArrayList<>();
        try {
            for (MultipartFile file : files) {
                if (file == null || file.isEmpty()) continue;

                String origName = file.getOriginalFilename();
                String ext = "";
                if (origName != null && origName.contains("."))
                    ext = origName.substring(origName.lastIndexOf(".") + 1); // 점(.) 제외 → 프론트 FILE_EXT 매칭('pdf')

                // 저장 경로: uploadBasePath/{today}/{UUID}.{ext}
                String savedName = UUID.randomUUID().toString() + (ext.isBlank() ? "" : "." + ext);
                Path dirPath  = Paths.get(uploadBasePath, today);
                Path filePath = dirPath.resolve(savedName);
                Files.createDirectories(dirPath);
                file.transferTo(filePath.toFile());

                /* [ORA-02289] SEQ 미존재 대비 MAX+1 채번. [ORA-18734] argTypes 명시.
                   OP_DATE 컬럼 미존재 DB 대비: 있으면 포함, 없으면 컬럼 제외 INSERT. */
                Long newId = jdbc.queryForObject(
                    "SELECT NVL(MAX(FILE_ID),0)+1 FROM KNRAWMS.DOC_FILE", Long.class);
                if (hasOpDate) {
                    jdbc.update(
                        "INSERT INTO KNRAWMS.DOC_FILE (FILE_ID, FOLDER_ID, FILE_NM, FILE_PATH, FILE_SIZE, FILE_TYPE, FILE_EXT, " +
                        "OP_DATE, NOTE, CREDAT, CRETIM, CREUSR, LMODAT, DEL_YN, DOWNLOAD_CNT) " +
                        "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                        new Object[]{
                            newId, folderId, origName, filePath.toString(), file.getSize(),
                            file.getContentType(), ext,
                            opd, note, today, now, "SYSTEM", today, "N", 0
                        },
                        new int[]{
                            Types.NUMERIC, Types.NUMERIC, Types.VARCHAR, Types.VARCHAR, Types.NUMERIC,
                            Types.VARCHAR, Types.VARCHAR,
                            Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR,
                            Types.VARCHAR, Types.VARCHAR, Types.NUMERIC
                        }
                    );
                } else {
                    jdbc.update(
                        "INSERT INTO KNRAWMS.DOC_FILE (FILE_ID, FOLDER_ID, FILE_NM, FILE_PATH, FILE_SIZE, FILE_TYPE, FILE_EXT, " +
                        "NOTE, CREDAT, CRETIM, CREUSR, LMODAT, DEL_YN, DOWNLOAD_CNT) " +
                        "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                        new Object[]{
                            newId, folderId, origName, filePath.toString(), file.getSize(),
                            file.getContentType(), ext,
                            note, today, now, "SYSTEM", today, "N", 0
                        },
                        new int[]{
                            Types.NUMERIC, Types.NUMERIC, Types.VARCHAR, Types.VARCHAR, Types.NUMERIC,
                            Types.VARCHAR, Types.VARCHAR,
                            Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR,
                            Types.VARCHAR, Types.NUMERIC
                        }
                    );
                }
                Map<String, Object> one = new LinkedHashMap<>();
                one.put("file_id", newId);
                one.put("file_nm", origName);
                saved.add(one);
            }
            if (saved.isEmpty()) return Map.of("ok", false, "error", "업로드할 유효한 파일이 없습니다");
            return Map.of("ok", true, "saved", saved);
        } catch (Exception e) {
            log.error("upload error: {}", e.getMessage());
            return Map.of("ok", false, "error", e.getMessage());
        }
    }

    // ── 파일 조회 (다운로드/미리보기 공통) ────────────────────────
    public FileResult getFile(Long fileId, boolean inline) {
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM KNRAWMS.DOC_FILE WHERE FILE_ID=? AND DEL_YN='N'", fileId
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
                jdbc.update("UPDATE KNRAWMS.DOC_FILE SET DOWNLOAD_CNT=DOWNLOAD_CNT+1 WHERE FILE_ID=?", fileId);
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
                "SELECT FILE_PATH FROM KNRAWMS.DOC_FILE WHERE FILE_ID=?", fileId
            );
            if (!rows.isEmpty()) {
                String filePath = (String) rows.get(0).get("FILE_PATH");
                try { Files.deleteIfExists(Paths.get(filePath)); } catch (Exception ignored) {}
            }
            jdbc.update("UPDATE KNRAWMS.DOC_FILE SET DEL_YN='Y', LMODAT=? WHERE FILE_ID=?", today, fileId);
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
            String opDate = body.containsKey("op_date") ? Objects.toString(body.get("op_date"), null) : null;
            Long folderId = body.get("folder_id") != null
                ? Long.valueOf(body.get("folder_id").toString()) : null;

            if (fileNm != null)
                jdbc.update("UPDATE KNRAWMS.DOC_FILE SET FILE_NM=?, LMODAT=? WHERE FILE_ID=?", fileNm.trim(), today, fileId);
            if (note != null)
                jdbc.update("UPDATE KNRAWMS.DOC_FILE SET NOTE=?, LMODAT=? WHERE FILE_ID=?", note.trim(), today, fileId);
            if (opDate != null && hasOpDateColumn())
                jdbc.update(
                    "UPDATE KNRAWMS.DOC_FILE SET OP_DATE=?, LMODAT=? WHERE FILE_ID=?",
                    new Object[]{ opDate.isBlank() ? null : opDate.trim(), today, fileId },
                    new int[]{ Types.VARCHAR, Types.VARCHAR, Types.NUMERIC });
            if (folderId != null)
                jdbc.update("UPDATE KNRAWMS.DOC_FILE SET FOLDER_ID=?, LMODAT=? WHERE FILE_ID=?", folderId, today, fileId);
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
