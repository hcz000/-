package com.example.cloud.post.service;

import com.example.cloud.common.exception.BusinessException;
import com.example.cloud.post.entity.Planet;
import com.example.cloud.post.entity.PlanetMember;
import com.example.cloud.post.repository.PlanetMemberRepository;
import com.example.cloud.post.repository.PlanetRepository;
import jakarta.annotation.Resource;
import jakarta.persistence.criteria.Predicate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 星球服务（简化版）。
 * <p>
 * 与单体版的差异：
 * <ul>
 *   <li>{@code listPlanetMembers} 返回 userId 列表（{@code Page<Long>}），不再返回 {@code User} 对象。
 *       User 在 user-svc 域内，需要详情时调用方走 Feign 调 user-svc 即可。</li>
 *   <li>去掉了 cache 注解 / 通知调用。</li>
 * </ul>
 */
@Slf4j
@Service
public class PlanetService {

    @Resource
    private PlanetRepository planetRepository;

    @Resource
    private PlanetMemberRepository planetMemberRepository;

    @Transactional(rollbackFor = Exception.class)
    public Planet createPlanet(Planet planet) {
        if (planet == null || !StringUtils.hasText(planet.getName())) {
            throw new BusinessException("星球名不能为空");
        }
        LocalDateTime now = LocalDateTime.now();
        planet.setMemberCount(planet.getMemberCount() == null ? 1 : planet.getMemberCount());
        planet.setDeleted(false);
        planet.setStatus(planet.getStatus() == null ? 1 : planet.getStatus());
        planet.setCreateTime(now);
        planet.setUpdateTime(now);
        planetRepository.save(planet);
        // 创建者自动加入
        if (planet.getMaster() != null) {
            joinPlanetInternal(planet.getPlanetId(), planet.getMaster(), now);
        }
        return planet;
    }

    public Planet getPlanet(Long planetId) {
        return planetRepository.findById(planetId).orElseThrow(() -> new BusinessException("星球不存在"));
    }

    @Transactional(rollbackFor = Exception.class)
    public void removePlanet(Long planetId) {
        Planet planet = getPlanet(planetId);
        planet.setDeleted(true);
        planet.setUpdateTime(LocalDateTime.now());
        planetRepository.save(planet);
    }

    public Page<Planet> listPlanets(int pageNum, int pageSize) {
        Specification<Planet> spec = (root, query, cb) -> cb.equal(root.get("deleted"), false);
        return planetRepository.findAll(spec,
                PageRequest.of(pageNum - 1, pageSize, Sort.by(Sort.Direction.DESC, "createTime")));
    }

    /**
     * 后台管理用的多条件查询。
     */
    public Page<Planet> getPlanetList(String name, String category, Integer status, int pageNum, int pageSize) {
        Specification<Planet> spec = (root, query, cb) -> {
            Predicate p = cb.equal(root.get("deleted"), false);
            if (StringUtils.hasText(name)) {
                p = cb.and(p, cb.like(root.get("name"), "%" + name + "%"));
            }
            if (StringUtils.hasText(category)) {
                p = cb.and(p, cb.equal(root.get("category"), category));
            }
            if (status != null) {
                p = cb.and(p, cb.equal(root.get("status"), status));
            }
            return p;
        };
        return planetRepository.findAll(spec,
                PageRequest.of(pageNum - 1, pageSize, Sort.by(Sort.Direction.DESC, "createTime")));
    }

    /**
     * 列出星球的成员 userId（如需 User 详情，调用方通过 Feign 调 user-svc）。
     */
    public Page<Long> listPlanetMembers(Long planetId, int pageNum, int pageSize) {
        Pageable pageable = PageRequest.of(pageNum - 1, pageSize);
        List<Long> ids = planetMemberRepository.findUserIdsByPlanetId(planetId);
        int from = (int) pageable.getOffset();
        int to = Math.min(from + pageSize, ids.size());
        List<Long> slice = from >= ids.size() ? List.of() : ids.subList(from, to);
        return new PageImpl<>(slice, pageable, ids.size());
    }

    /**
     * 用户加入的星球列表。
     */
    public Page<Planet> listUserVisitedPlanets(Long userId, int pageNum, int pageSize) {
        Pageable pageable = PageRequest.of(pageNum - 1, pageSize);
        Page<Long> planetIds = planetMemberRepository.findPlanetIdsByUserId(userId, pageable);
        if (planetIds.isEmpty()) return Page.empty(pageable);
        List<Planet> planets = planetRepository.findAllById(planetIds.getContent());
        return new PageImpl<>(planets, pageable, planetIds.getTotalElements());
    }

    @Transactional(rollbackFor = Exception.class)
    public void joinPlanet(Long planetId, Long userId) {
        if (planetId == null || userId == null) throw new BusinessException("planetId / userId 不能为空");
        Planet planet = getPlanet(planetId);
        if (Boolean.TRUE.equals(planet.getDeleted())) throw new BusinessException("星球已删除");
        if (planetMemberRepository.existsByPlanetIdAndUserId(planetId, userId)) {
            throw new BusinessException("已加入该星球");
        }
        joinPlanetInternal(planetId, userId, LocalDateTime.now());
    }

    @Transactional(rollbackFor = Exception.class)
    public void leavePlanet(Long planetId, Long userId) {
        if (planetMemberRepository.deleteByPlanetIdAndUserId(planetId, userId) > 0) {
            Planet planet = planetRepository.findById(planetId).orElse(null);
            if (planet != null && planet.getMemberCount() != null && planet.getMemberCount() > 0) {
                planet.setMemberCount(planet.getMemberCount() - 1);
                planet.setUpdateTime(LocalDateTime.now());
                planetRepository.save(planet);
            }
        }
    }

    public boolean isMember(Long planetId, Long userId) {
        if (planetId == null || userId == null) return false;
        return planetMemberRepository.existsByPlanetIdAndUserId(planetId, userId);
    }

    private void joinPlanetInternal(Long planetId, Long userId, LocalDateTime now) {
        PlanetMember member = new PlanetMember();
        member.setPlanetId(planetId);
        member.setUserId(userId);
        member.setCreateTime(now);
        planetMemberRepository.save(member);

        Planet planet = planetRepository.findById(planetId).orElse(null);
        if (planet != null) {
            planet.setMemberCount((planet.getMemberCount() == null ? 0 : planet.getMemberCount()) + 1);
            planet.setUpdateTime(now);
            planetRepository.save(planet);
        }
    }
}
