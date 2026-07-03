package com.company.module.dispatchconfig.service;

import com.company.core.common.exception.BusinessException;
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
    private final DispatchProfileRepository profileRepository;
    private final DispatchConstraintRepository constraintRepository;
    private final DispatchConstSetRepository constSetRepository;

    // ── Objective ────────────────────────────────────────────────
    public List<DispatchObjective> getObjectiveList(String ownrky) {
        return objectiveRepository.findAllByOwnrkyOrderBySortSeqAsc(ownrky);
    }

    @Transactional
    public DispatchObjective saveObjective(String ownrky, Long objectiveId, String name,
                                           String description, Integer sortSeq) {
        if (objectiveId != null) {
            DispatchObjective obj = objectiveRepository.findByObjectiveIdAndOwnrky(objectiveId, ownrky)
                    .orElseThrow(() -> new EntityNotFoundException(ErrorCode.OBJECTIVE_NOT_FOUND));
            obj.update(name, description, sortSeq);
            return obj;
        }
        return objectiveRepository.save(DispatchObjective.builder()
                .ownrky(ownrky).name(name).description(description).sortSeq(sortSeq).build());
    }

    @Transactional
    public void activateObjective(String ownrky, Long objectiveId) {
        DispatchObjective obj = objectiveRepository.findByObjectiveIdAndOwnrky(objectiveId, ownrky)
                .orElseThrow(() -> new EntityNotFoundException(ErrorCode.OBJECTIVE_NOT_FOUND));
        objectiveRepository.deactivateAll(ownrky);
        obj.activate();
    }

    @Transactional
    public void deleteObjective(String ownrky, Long objectiveId) {
        DispatchObjective obj = objectiveRepository.findByObjectiveIdAndOwnrky(objectiveId, ownrky)
                .orElseThrow(() -> new EntityNotFoundException(ErrorCode.OBJECTIVE_NOT_FOUND));
        obj.deactivate();
    }

    // ── Profile ──────────────────────────────────────────────────
    public List<DispatchProfile> getProfileList(String ownrky) {
        return profileRepository.findAllByOwnrkyOrderBySortSeqAsc(ownrky);
    }

    public DispatchProfile getProfile(String ownrky, Long profileId) {
        return profileRepository.findByProfileIdAndOwnrky(profileId, ownrky)
                .orElseThrow(() -> new EntityNotFoundException(ErrorCode.PROFILE_NOT_FOUND));
    }

    @Transactional
    public DispatchProfile saveProfile(String ownrky, Long profileId, String profileName,
                                       String description, Integer sortSeq) {
        if (profileId != null) {
            DispatchProfile p = profileRepository.findByProfileIdAndOwnrky(profileId, ownrky)
                    .orElseThrow(() -> new EntityNotFoundException(ErrorCode.PROFILE_NOT_FOUND));
            p.update(profileName, description, sortSeq);
            return p;
        }
        int seq = sortSeq != null ? sortSeq : profileRepository.nextSortSeq(ownrky);
        return profileRepository.save(DispatchProfile.builder()
                .ownrky(ownrky).profileName(profileName).description(description).sortSeq(seq).build());
    }

    @Transactional
    public void deleteProfile(String ownrky, Long profileId) {
        DispatchProfile p = profileRepository.findByProfileIdAndOwnrky(profileId, ownrky)
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
                                         String constVal, Integer isDynamic, String forkliftYn, Double entryTon) {
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
