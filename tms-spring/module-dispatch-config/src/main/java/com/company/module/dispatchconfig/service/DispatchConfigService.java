package com.company.module.dispatchconfig.service;

import com.company.core.common.exception.EntityNotFoundException;
import com.company.core.common.exception.ErrorCode;
import com.company.module.dispatchconfig.entity.*;
import com.company.module.dispatchconfig.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DispatchConfigService {

    private final DispatchObjectiveRepository objectiveRepository;
    private final DispatchProfileRepository   profileRepository;
    private final DispatchConstraintRepository constraintRepository;
    private final DispatchConstSetRepository  constSetRepository;

    // ── Objective ────────────────────────────────────────────────
    public List<DispatchObjective> getObjectiveList() {
        return objectiveRepository.findAllByOrderBySortSeqAscObjIdAsc();
    }

    public Optional<DispatchObjective> getActiveObjective() {
        return objectiveRepository.findByActiveYn("Y");
    }

    @Transactional
    public DispatchObjective saveObjective(Long objId, String objCode, String objNm,
                                           String objIcon, String objAlgo,
                                           String objDesc, Integer sortSeq, String activeYn) {
        if (objId != null) {
            DispatchObjective obj = objectiveRepository.findById(objId)
                    .orElseThrow(() -> new EntityNotFoundException(ErrorCode.OBJECTIVE_NOT_FOUND));
            obj.update(objCode, objNm, objIcon, objAlgo, objDesc, sortSeq, activeYn);
            return obj;
        }
        return objectiveRepository.save(DispatchObjective.builder()
                .objCode(objCode).objNm(objNm).objIcon(objIcon)
                .objAlgo(objAlgo).objDesc(objDesc).sortSeq(sortSeq)
                .activeYn(activeYn != null ? activeYn : "Y")
                .build());
    }

    @Transactional
    public void activateObjective(Long objId) {
        DispatchObjective obj = objectiveRepository.findById(objId)
                .orElseThrow(() -> new EntityNotFoundException(ErrorCode.OBJECTIVE_NOT_FOUND));
        objectiveRepository.deactivateAll();
        obj.activate();
    }

    @Transactional
    public void deleteObjective(Long objId) {
        DispatchObjective obj = objectiveRepository.findById(objId)
                .orElseThrow(() -> new EntityNotFoundException(ErrorCode.OBJECTIVE_NOT_FOUND));
        obj.deactivate();
    }

    // ── Profile ──────────────────────────────────────────────────
    public List<DispatchProfile> getProfileList() {
        return profileRepository.findAllByOrderBySortSeqAscProfileIdAsc();
    }

    public DispatchProfile getProfile(Long profileId) {
        return profileRepository.findById(profileId)
                .orElseThrow(() -> new EntityNotFoundException(ErrorCode.PROFILE_NOT_FOUND));
    }

    @Transactional
    public DispatchProfile saveProfile(Long profileId, String profileNm,
                                       String profileDesc, Integer sortSeq) {
        if (profileId != null) {
            DispatchProfile p = profileRepository.findById(profileId)
                    .orElseThrow(() -> new EntityNotFoundException(ErrorCode.PROFILE_NOT_FOUND));
            p.update(profileNm, profileDesc, sortSeq);
            return p;
        }
        int seq = sortSeq != null ? sortSeq : profileRepository.nextSortSeq();
        return profileRepository.save(DispatchProfile.builder()
                .profileNm(profileNm).profileDesc(profileDesc)
                .sortSeq(seq).activeYn("Y")
                .build());
    }

    @Transactional
    public void deleteProfile(Long profileId) {
        DispatchProfile p = profileRepository.findById(profileId)
                .orElseThrow(() -> new EntityNotFoundException(ErrorCode.PROFILE_NOT_FOUND));
        p.delete();
    }

    // ── Constraint ───────────────────────────────────────────────
    public List<DispatchConstraint> getConstraintList(Long profileId) {
        return constraintRepository.findAllByProfileIdAndIsActiveOrderBySortSeqAsc(profileId, 1);
    }

    @Transactional
    public DispatchConstraint saveConstraint(String ownrky, Long constraintId, Long profileId,
                                             String constraintType, String constraintKey,
                                             String constraintVal, Integer sortSeq) {
        if (constraintId != null) {
            DispatchConstraint c = constraintRepository.findByConstraintIdAndOwnrky(constraintId, ownrky)
                    .orElseThrow(() -> new EntityNotFoundException(ErrorCode.ENTITY_NOT_FOUND));
            c.update(constraintVal, 1);
            return c;
        }
        return constraintRepository.save(DispatchConstraint.builder()
                .profileId(profileId).ownrky(ownrky).constraintType(constraintType)
                .constraintKey(constraintKey).constraintVal(constraintVal)
                .sortSeq(sortSeq != null ? sortSeq : 0).build());
    }

    @Transactional
    public void deleteConstraint(String ownrky, Long constraintId) {
        DispatchConstraint c = constraintRepository.findByConstraintIdAndOwnrky(constraintId, ownrky)
                .orElseThrow(() -> new EntityNotFoundException(ErrorCode.ENTITY_NOT_FOUND));
        c.delete();
    }

    // ── ConstSet ─────────────────────────────────────────────────
    public List<DispatchConstSet> getConstSetList(Long profileId) {
        return constSetRepository.findAllByProfileIdAndIsActiveOrderByConstTypeAsc(profileId, 1);
    }

    public List<DispatchConstSet> getConstSetByType(Long profileId, String constType) {
        return constSetRepository.findAllByProfileIdAndConstTypeAndIsActive(profileId, constType, 1);
    }

    @Transactional
    public DispatchConstSet saveConstSet(String ownrky, Long constId, Long profileId,
                                         String constType, String cartype, String region,
                                         String constVal, Integer isDynamic,
                                         String forkliftYn, Double entryTon) {
        if (constId != null) {
            DispatchConstSet cs = constSetRepository.findByConstIdAndOwnrky(constId, ownrky)
                    .orElseThrow(() -> new EntityNotFoundException(ErrorCode.ENTITY_NOT_FOUND));
            cs.update(constVal, cartype, region, isDynamic, forkliftYn, entryTon);
            return cs;
        }
        return constSetRepository.save(DispatchConstSet.builder()
                .profileId(profileId).ownrky(ownrky).constType(constType).cartype(cartype)
                .region(region).constVal(constVal).isDynamic(isDynamic != null ? isDynamic : 0)
                .forkliftYn(forkliftYn).entryTon(entryTon).build());
    }

    @Transactional
    public void deleteConstSet(String ownrky, Long constId) {
        DispatchConstSet cs = constSetRepository.findByConstIdAndOwnrky(constId, ownrky)
                .orElseThrow(() -> new EntityNotFoundException(ErrorCode.ENTITY_NOT_FOUND));
        cs.delete();
    }
}
