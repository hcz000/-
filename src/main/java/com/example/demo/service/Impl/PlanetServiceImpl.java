package com.example.demo.service.Impl;

import cn.dev33.satoken.stp.StpUtil;
import com.example.demo.entity.Planet;
import com.example.demo.entity.PlanetMember;
import com.example.demo.entity.User;
import com.example.demo.exception.BusinessException;
import com.example.demo.repository.PlanetMemberRepository;
import com.example.demo.repository.PlanetRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.IPlanetService;
import jakarta.annotation.Resource;
import jakarta.persistence.criteria.Predicate;
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
import java.util.Collections;
import java.util.List;


@Service
public class PlanetServiceImpl implements IPlanetService {

    @Resource
    private UserRepository userRepository;
    @Resource
    private PlanetRepository planetRepository;
    @Resource
    private PlanetMemberRepository planetMemberRepository;

    @Override
    @Transactional
    public Planet createPlanet(Planet planet) {
        if (planet == null || !StringUtils.hasText(planet.getName())) {
            throw new BusinessException("星球名称不能为空");
        }
        if (!StpUtil.isLogin()) {
            throw new BusinessException("用户未登录");
        }
        Long ownerId = StpUtil.getLoginIdAsLong();
        User owner = loadUser(ownerId);
        planet.setMaster(ownerId);
        planet.setDeleted(false);
        planet.setMemberCount(1);
        planet.setCreateTime(LocalDateTime.now());
        planet.setUpdateTime(LocalDateTime.now());
        planetRepository.save(planet);

        PlanetMember pm = new PlanetMember();
        pm.setPlanetId(planet.getPlanetId());
        pm.setUserId(ownerId);
        planetMemberRepository.save(pm);

        return planet;
    }

    @Override
    public Planet getPlanet(Long planetId) {
        Planet planet = planetRepository.findById(planetId).orElse(null);
        if (planet == null || Boolean.TRUE.equals(planet.getDeleted())) {
            throw new BusinessException("星球不存在");
        }
        return planet;
    }

    @Override
    @Transactional
    public void removePlanet(Long planetId) {
        Planet planet = getPlanet(planetId);
        planet.setDeleted(true);
        planet.setUpdateTime(LocalDateTime.now());
        planetRepository.save(planet);
    }

    @Override
    public Page<Planet> listPlanets(int pageNum, int pageSize) {
        Specification<Planet> spec = (root, query, cb) -> {
            Predicate predicate = cb.equal(root.get("deleted"), false);
            return predicate;
        };
        Pageable pageable = PageRequest.of(pageNum - 1, pageSize, Sort.by(Sort.Direction.DESC, "createTime"));
        return planetRepository.findAll(spec, pageable);
    }

    @Override
    public Page<Planet> getPlanetList(String name, String category, Integer status, int pageNum, int pageSize) {
        Specification<Planet> spec = (root, query, cb) -> {
            Predicate predicate = cb.conjunction();
            predicate = cb.and(predicate, cb.equal(root.get("deleted"), false));
            if (StringUtils.hasText(name)) {
                predicate = cb.and(predicate, cb.like(root.get("name"), "%" + name + "%"));
            }
            if (StringUtils.hasText(category)) {
                predicate = cb.and(predicate, cb.equal(root.get("category"), category));
            }
            if (status != null) {
                predicate = cb.and(predicate, cb.equal(root.get("status"), status));
            }
            return predicate;
        };
        Pageable pageable = PageRequest.of(pageNum - 1, pageSize, Sort.by(Sort.Direction.DESC, "createTime"));
        return planetRepository.findAll(spec, pageable);
    }

    @Override
    public Page<User> listPlanetMembers(Long planetId, int pageNum, int pageSize) {
        if (planetId == null) {
            throw new BusinessException("星球ID不能为空");
        }
        Planet planet = getPlanet(planetId);

        List<Long> memberIds = planetMemberRepository.findUserIdsByPlanetId(planetId);
        if (memberIds.isEmpty()) {
            return new PageImpl<>(Collections.emptyList(), PageRequest.of(pageNum - 1, pageSize), 0);
        }

        Specification<User> spec = (root, query, cb) -> {
            Predicate predicate = root.get("id").in(memberIds);
            return predicate;
        };
        Pageable pageable = PageRequest.of(pageNum - 1, pageSize);
        return userRepository.findAll(spec, pageable);
    }

    @Override
    public Page<Planet> listUserVisitedPlanets(Long userId, int pageNum, int pageSize) {
        if (userId == null) {
            throw new BusinessException("用户ID不能为空");
        }
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        List<Long> planetIds = planetMemberRepository.findPlanetIdsByUserId(userId);
        if (planetIds.isEmpty()) {
            return new PageImpl<>(Collections.emptyList(), PageRequest.of(pageNum - 1, pageSize), 0);
        }
        Specification<Planet> spec = (root, query, cb) -> {
            Predicate predicate = cb.conjunction();
            predicate = cb.and(predicate, cb.equal(root.get("deleted"), false));
            predicate = cb.and(predicate, root.get("planetId").in(planetIds));
            return predicate;
        };
        Pageable pageable = PageRequest.of(pageNum - 1, pageSize, Sort.by(Sort.Direction.DESC, "createTime"));
        return planetRepository.findAll(spec, pageable);
    }

    @Override
    @Transactional
    public void joinPlanet(Long planetId, Long userId) {
        Planet planet = getPlanet(planetId);
        User user = loadUser(userId);

        if (planetMemberRepository.existsByPlanetIdAndUserId(planetId, userId)) {
            throw new BusinessException("已加入该星球");
        }

        PlanetMember pm = new PlanetMember();
        pm.setPlanetId(planetId);
        pm.setUserId(userId);
        planetMemberRepository.save(pm);

        planet.setMemberCount(planet.getMemberCount() == null ? 1 : planet.getMemberCount() + 1);
        planet.setUpdateTime(LocalDateTime.now());
        planetRepository.save(planet);
    }

    @Override
    @Transactional
    public void leavePlanet(Long planetId, Long userId) {
        Planet planet = getPlanet(planetId);
        User user = loadUser(userId);

        int deleted = planetMemberRepository.deleteByPlanetIdAndUserId(planetId, userId);
        if (deleted == 0) {
            throw new BusinessException("尚未加入该星球");
        }

        planet.setMemberCount(Math.max(0, (planet.getMemberCount() == null ? 0 : planet.getMemberCount()) - 1));
        planet.setUpdateTime(LocalDateTime.now());
        planetRepository.save(planet);
    }

    @Override
    public boolean isMember(Long planetId, Long userId) {
        if (planetId == null || userId == null) {
            return false;
        }
        Planet planet = planetRepository.findById(planetId).orElse(null);
        if (planet == null || Boolean.TRUE.equals(planet.getDeleted())) {
            return false;
        }
        return planetMemberRepository.existsByPlanetIdAndUserId(planetId, userId);
    }

    private User loadUser(Long userId) {
        if (userId == null) {
            throw new BusinessException("用户ID不能为空");
        }
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        return user;
    }

    @Override
    public Planet save(Planet planet) {
        return planetRepository.save(planet);
    }

    @Override
    public Planet getById(Long id) {
        return planetRepository.findById(id).orElse(null);
    }

    @Override
    public boolean updateById(Planet planet) {
        if (planet == null || planet.getPlanetId() == null) {
            return false;
        }
        planetRepository.save(planet);
        return true;
    }
}
