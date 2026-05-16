package com.example.demo.service;

import com.example.demo.entity.Planet;
import com.example.demo.entity.User;
import org.springframework.data.domain.Page;

/**
 * 星球 服务类
 */
public interface IPlanetService {

    Planet createPlanet(Planet planet);

    Planet getPlanet(Long planetId);

    void removePlanet(Long planetId);

    Page<Planet> listPlanets(int pageNum, int pageSize);

    Page<Planet> getPlanetList(String name, String category, Integer status, int pageNum, int pageSize);

    Page<User> listPlanetMembers(Long planetId, int pageNum, int pageSize);

    Page<Planet> listUserVisitedPlanets(Long userId, int pageNum, int pageSize);

    void joinPlanet(Long planetId, Long userId);

    void leavePlanet(Long planetId, Long userId);

    boolean isMember(Long planetId, Long userId);

    Planet save(Planet planet);

    Planet getById(Long id);

    boolean updateById(Planet planet);
}