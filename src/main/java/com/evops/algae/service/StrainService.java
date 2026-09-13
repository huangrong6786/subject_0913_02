package com.evops.algae.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.evops.algae.audit.AuditService;
import com.evops.algae.common.BizErrorCode;
import com.evops.algae.common.BizException;
import com.evops.algae.context.BizRequestContext;
import com.evops.algae.dto.StrainCreateRequest;
import com.evops.algae.entity.AlgaeStrain;
import com.evops.algae.mapper.AlgaeStrainMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class StrainService {

    private final AlgaeStrainMapper strainMapper;
    private final AuditService auditService;

    public StrainService(AlgaeStrainMapper strainMapper, AuditService auditService) {
        this.strainMapper = strainMapper;
        this.auditService = auditService;
    }

    @Transactional
    public AlgaeStrain create(StrainCreateRequest request) {
        boolean exists = strainMapper.exists(new LambdaQueryWrapper<AlgaeStrain>()
                .eq(AlgaeStrain::getStrainNo, request.getStrainNo()));
        if (exists) {
            throw new BizException(BizErrorCode.BIZ_KEY_DUPLICATED,
                    "藻种批次号已存在: " + request.getStrainNo());
        }
        AlgaeStrain strain = new AlgaeStrain();
        strain.setStrainNo(request.getStrainNo());
        strain.setStrainName(request.getStrainName());
        strain.setSpecies(request.getSpecies());
        strain.setSourceOrg(request.getSourceOrg());
        boolean accepted = Boolean.TRUE.equals(request.getAccepted());
        strain.setAccepted(accepted);
        strain.setAcceptedDate(accepted && request.getAcceptedDate() != null
                ? request.getAcceptedDate() : (accepted ? LocalDate.now() : null));
        strain.setAcceptRemark(request.getAcceptRemark());
        strain.setOperatorCode(BizRequestContext.get().getOperatorCode());
        strain.setVersion(0);
        strainMapper.insert(strain);
        auditService.record("STRAIN", "CREATE", "strainNo=" + strain.getStrainNo(),
                strain.getAcceptedDate(), strain);
        return strain;
    }

    @Transactional
    public AlgaeStrain accept(String strainNo, String remark) {
        AlgaeStrain strain = requireByNo(strainNo);
        if (Boolean.TRUE.equals(strain.getAccepted())) {
            throw new BizException(BizErrorCode.ILLEGAL_STATUS, "藻种批次已验收，不能重复验收");
        }
        strain.setAccepted(true);
        strain.setAcceptedDate(LocalDate.now());
        strain.setAcceptRemark(remark);
        strain.setOperatorCode(BizRequestContext.get().getOperatorCode());
        strainMapper.updateById(strain);
        auditService.record("STRAIN", "ACCEPT", "strainNo=" + strainNo,
                strain.getAcceptedDate(), strain);
        return strain;
    }

    /** 已验收的藻种批次不能删除。 */
    @Transactional
    public void delete(String strainNo) {
        AlgaeStrain strain = requireByNo(strainNo);
        if (Boolean.TRUE.equals(strain.getAccepted())) {
            throw new BizException(BizErrorCode.RECORD_LOCKED,
                    "已验收藻种批次不能删除: " + strainNo);
        }
        strainMapper.deleteById(strain.getId());
        auditService.record("STRAIN", "DELETE", "strainNo=" + strainNo, LocalDate.now(),
                strainNo);
    }

    public AlgaeStrain get(String strainNo) {
        return requireByNo(strainNo);
    }

    public List<AlgaeStrain> list() {
        return strainMapper.selectList(new LambdaQueryWrapper<AlgaeStrain>()
                .orderByAsc(AlgaeStrain::getStrainNo));
    }

    private AlgaeStrain requireByNo(String strainNo) {
        AlgaeStrain strain = strainMapper.selectOne(new LambdaQueryWrapper<AlgaeStrain>()
                .eq(AlgaeStrain::getStrainNo, strainNo));
        if (strain == null) {
            throw new BizException(BizErrorCode.NOT_FOUND, "藻种批次不存在: " + strainNo);
        }
        return strain;
    }
}
