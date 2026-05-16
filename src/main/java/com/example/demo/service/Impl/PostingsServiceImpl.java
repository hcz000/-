package com.example.demo.service.Impl;

import cn.dev33.satoken.stp.StpUtil;
import com.example.demo.config.CacheConfig;
import com.example.demo.entity.Planet;
import com.example.demo.entity.Postings;
import com.example.demo.enums.LikeBizType;
import com.example.demo.enums.PostingType;
import com.example.demo.exception.BusinessException;
import com.example.demo.repository.PlanetMemberRepository;
import com.example.demo.repository.PostingsRepository;
import com.example.demo.service.IPlanetService;
import com.example.demo.service.IPostingsService;
import com.example.demo.service.ISensitiveWordService;
import com.example.demo.service.LikeService;
import com.example.demo.task.NotificationSender;
import com.example.demo.task.ReplyCountBufferTrigger;
import jakarta.annotation.Resource;
import jakarta.persistence.criteria.Predicate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;


@Slf4j
@Service
public class PostingsServiceImpl implements IPostingsService {

    private static final String REPLY_COUNT_KEY_PREFIX = "postings:reply_count:";

    @Resource
    private IPlanetService planetService;
    @Resource
    private LikeService likeService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private ISensitiveWordService sensitiveWordService;
    @Resource
    private NotificationSender notificationSender;
    @Resource
    private ReplyCountBufferTrigger replyCountBufferTrigger;
    @Resource
    private PostingsRepository postingsRepository;
    @Resource
    private PlanetMemberRepository planetMemberRepository;

    @Override
    @Transactional
    public Postings createPost(Postings postings) {
        log.debug("createPost 开始验证帖子参数 planetId: {}", postings.getPlanetId());
        validatePostRequest(postings);

        checkSensitiveWords(postings.getTitle(), "title");
        checkSensitiveWords(postings.getContent(), "content");

        Planet planet = planetService.getPlanet(postings.getPlanetId());
        if (Boolean.TRUE.equals(planet.getDeleted())) {
            throw new BusinessException("planet is deleted");
        }
        Long userId = currentUserId();
        if (!planetService.isMember(postings.getPlanetId(), userId)) {
            throw new BusinessException("not joined planet");
        }
        LocalDateTime now = LocalDateTime.now();
        postings.setStatus(postings.getStatus() == null ? 0 : postings.getStatus());
        postings.setAuditStatus(postings.getAuditStatus() == null ? 0 : postings.getAuditStatus()); // 默认待审核
        postings.setReplyCount(postings.getReplyCount() == null ? 0 : postings.getReplyCount());
        postings.setLikeCount(postings.getLikeCount() == null ? 0 : postings.getLikeCount());
        postings.setDeleted(false);
        postings.setUserId(currentUserId());
        postings.setCreateTime(now);
        postings.setUpdateTime(now);
        postingsRepository.save(postings);

        notifyPlanetMembers(postings, planet, userId);

        return postings;
    }

    @Override
    @Cacheable(value = CacheConfig.CACHE_POSTINGS, key = "#postingsId", unless = "#result == null")
    public Postings getPost(Long postingsId) {
        Postings postings = postingsRepository.findById(postingsId).orElse(null);
        if (postings == null || Boolean.TRUE.equals(postings.getDeleted())) {
            throw new BusinessException("post not found");
        }
        postings.setReplyCount(loadReplyCount(postingsId, postings.getReplyCount()));
        return postings;
    }

    @Override
    @CacheEvict(value = CacheConfig.CACHE_POSTINGS, key = "#postings.postingsId")
    @Transactional
    public Postings updatePost(Postings postings) {
        if (postings == null || postings.getPostingsId() == null) {
            throw new BusinessException("post id must not be null");
        }
        Postings existing = getPost(postings.getPostingsId());
        if (StringUtils.hasText(postings.getTitle())) {
            checkSensitiveWords(postings.getTitle(), "title");
            existing.setTitle(postings.getTitle());
        }
        if (StringUtils.hasText(postings.getContent())) {
            checkSensitiveWords(postings.getContent(), "content");
            existing.setContent(postings.getContent());
        }
        existing.setUpdateTime(LocalDateTime.now());
        postingsRepository.save(existing);

        return existing;
    }

    @Override
    @CacheEvict(value = CacheConfig.CACHE_POSTINGS, key = "#postingsId")
    @Transactional
    public void removePost(Long postingsId) {
        Postings post = getPost(postingsId);
        post.setDeleted(true);
        post.setUpdateTime(LocalDateTime.now());
        postingsRepository.save(post);
    }

    @Override
    public Page<Postings> listByPlanetId(Long planetId, int pageNum, int pageSize) {
        if (planetId == null) {
            throw new BusinessException("planetId must not be null");
        }
        planetService.getPlanet(planetId);
        Specification<Postings> spec = (root, query, cb) -> {
            Predicate predicate = cb.conjunction();
            predicate = cb.and(predicate, cb.equal(root.get("planetId"), planetId));
            predicate = cb.and(predicate, cb.equal(root.get("deleted"), false));
            predicate = cb.and(predicate, cb.equal(root.get("status"), 1));
            predicate = cb.and(predicate, cb.equal(root.get("auditStatus"), 1)); // 只显示已审核通过
            return predicate;
        };
        Pageable pageable = PageRequest.of(pageNum - 1, pageSize, Sort.by(Sort.Direction.DESC, "createTime"));
        Page<Postings> result = postingsRepository.findAll(spec, pageable);
        if (result != null && result.getContent() != null) {
            result.getContent().forEach(record -> {
                if (record == null) {
                    return;
                }
                record.setLikeCount(likeService.getLikeCount(LikeBizType.POST, record.getPostingsId()));
                record.setReplyCount(loadReplyCount(record.getPostingsId(), record.getReplyCount()));
            });
        }
        return result;
    }

    @Override
    public int togglePostLike(Long postingsId, Long userId, boolean like) {
        Postings post = getPost(postingsId);
        LikeService.LikeToggleResult result = likeService.toggleLikeWithChange(LikeBizType.POST, postingsId, userId, like);
        // 点赞时发送通知给帖子作者（取消点赞不发送）
        if (like && result.changed() && !userId.equals(post.getUserId())) {
            notificationSender.notifyPostLike(userId, post.getUserId(), postingsId, post.getTitle());
        }
        return result.count();
    }

    @Override
    public Page<Postings> searchPosts(String keyword, Long planetId, int pageNum, int pageSize) {
        if (!StringUtils.hasText(keyword)) {
            throw new BusinessException("keyword must not be blank");
        }
        long offset = (pageNum - 1) * pageSize;

        List<Postings> records;
        long total;
        if (planetId != null) {
            records = postingsRepository.searchByFullTextByPlanet(keyword, planetId, pageSize, offset);
            total = postingsRepository.countSearchResultsByPlanet(keyword, planetId);
        } else {
            records = postingsRepository.searchByFullText(keyword, pageSize, offset);
            total = postingsRepository.countSearchResults(keyword);
        }

        Pageable pageable = PageRequest.of(pageNum - 1, pageSize);
        Page<Postings> page = new PageImpl<>(records, pageable, total);

        if (records != null) {
            records.forEach(record -> {
                if (record == null) {
                    return;
                }
                record.setLikeCount(likeService.getLikeCount(LikeBizType.POST, record.getPostingsId()));
                record.setReplyCount(loadReplyCount(record.getPostingsId(), record.getReplyCount()));
            });
        }
        return page;
    }

    @Override
    public Page<Postings> listLikedPosts(Long userId, int pageNum, int pageSize) {
        if (userId == null) {
            throw new BusinessException("userId must not be null");
        }
        List<Long> likedPostIds = likeService.getUserLikedBizIds(LikeBizType.POST, userId);
        if (likedPostIds.isEmpty()) {
            return new PageImpl<>(List.of(), PageRequest.of(pageNum - 1, pageSize), 0);
        }

        int total = likedPostIds.size();
        int fromIndex = (pageNum - 1) * pageSize;
        int toIndex = Math.min(fromIndex + pageSize, total);

        Pageable pageable = PageRequest.of(pageNum - 1, pageSize);

        if (fromIndex >= total) {
            return new PageImpl<>(List.of(), pageable, total);
        }

        List<Long> pageIds = likedPostIds.subList(fromIndex, toIndex);
        if (pageIds.isEmpty()) {
            return new PageImpl<>(List.of(), pageable, total);
        }

        List<Postings> posts = postingsRepository.findAllById(pageIds);
        Map<Long, Postings> postById = new HashMap<>();
        for (Postings post : posts) {
            if (post != null && !Boolean.TRUE.equals(post.getDeleted())) {
                postById.put(post.getPostingsId(), post);
            }
        }

        List<Postings> ordered = new ArrayList<>();
        for (Long id : pageIds) {
            Postings post = postById.get(id);
            if (post != null) {
                post.setLikeCount(likeService.getLikeCount(LikeBizType.POST, post.getPostingsId()));
                post.setReplyCount(loadReplyCount(post.getPostingsId(), post.getReplyCount()));
                ordered.add(post);
            }
        }
        return new PageImpl<>(ordered, pageable, total);
    }

    @Override
    public void adjustReplyCount(Long postingsId, int delta) {
        if (postingsId == null || delta == 0) {
            return;
        }
        if (delta < 0) {
            Postings existing = postingsRepository.findById(postingsId).orElse(null);
            if (existing == null || Boolean.TRUE.equals(existing.getDeleted())) {
                return;
            }
        }
        replyCountBufferTrigger.enqueue(postingsId, delta);
    }

    private Integer loadReplyCount(Long postingsId, Integer fallback) {
        if (postingsId == null) {
            return fallback == null ? 0 : fallback;
        }
        String value = stringRedisTemplate.opsForValue().get(REPLY_COUNT_KEY_PREFIX + postingsId);
        if (StringUtils.hasText(value)) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException ignored) {
            }
        }
        return fallback == null ? 0 : fallback;
    }

    private void validatePostRequest(Postings postings) {
        if (postings == null || postings.getPlanetId() == null) {
            throw new BusinessException("planet id must not be null");
        }
        if (!StringUtils.hasText(postings.getTitle())) {
            throw new BusinessException("post title must not be blank");
        }
        if (!StringUtils.hasText(postings.getType())) {
            throw new BusinessException("post type is required");
        }
        PostingType postingType = PostingType.fromString(postings.getType());
        if (postingType == null) {
            throw new BusinessException("invalid post type");
        }
        postings.setType(postingType.getTypeName());
    }

    private void checkSensitiveWords(String text, String field) {
        if (!StringUtils.hasText(text)) {
            return;
        }
        Set<String> words = sensitiveWordService.findSensitiveWords(text);
        if (!words.isEmpty()) {
            String hitWord = pickMatchedSensitiveWord(text, words);
            throw new BusinessException(field + " contains sensitive word: " + hitWord);
        }
    }

    private String pickMatchedSensitiveWord(String text, Set<String> words) {
        if (words == null || words.isEmpty()) {
            return "";
        }
        if (!StringUtils.hasText(text)) {
            return words.iterator().next();
        }
        String lowerText = text.toLowerCase(Locale.ROOT);
        return words.stream()
                .filter(StringUtils::hasText)
                .sorted(Comparator
                        .comparingInt((String word) -> {
                            int index = lowerText.indexOf(word.toLowerCase(Locale.ROOT));
                            return index >= 0 ? index : Integer.MAX_VALUE;
                        })
                        .thenComparing(Comparator.comparingInt(String::length).reversed())
                        .thenComparing(Comparator.naturalOrder()))
                .findFirst()
                .orElseGet(() -> words.iterator().next());
    }

    private Long currentUserId() {
        if (!StpUtil.isLogin()) {
            throw new BusinessException("user not logged in");
        }
        return StpUtil.getLoginIdAsLong();
    }

    private void notifyPlanetMembers(Postings postings, Planet planet, Long senderId) {
        List<Long> memberIds = planetMemberRepository.findUserIdsByPlanetId(planet.getPlanetId());
        if (memberIds.isEmpty()) {
            return;
        }
        String planetName = planet.getName();
        String postTitle = postings.getTitle();
        Long planetId = postings.getPlanetId();
        Long postId = postings.getPostingsId();

        for (Long memberId : memberIds) {
            if (memberId.equals(senderId)) {
                continue;
            }
            try {
                notificationSender.notifyPlanetNewPost(senderId, memberId, planetId, postId, planetName, postTitle);
            } catch (Exception e) {
                log.warn("notify planet member {} new post failed", memberId, e);
            }
        }
    }

    @Override
    public Page<Postings> getPostList(Long planetId, Long userId, String keyword, int pageNum, int pageSize) {
        Specification<Postings> spec = (root, query, cb) -> {
            Predicate predicate = cb.conjunction();
            if (planetId != null) {
                predicate = cb.and(predicate, cb.equal(root.get("planetId"), planetId));
            }
            if (userId != null) {
                predicate = cb.and(predicate, cb.equal(root.get("userId"), userId));
            }
            if (keyword != null && !keyword.isEmpty()) {
                Predicate titleLike = cb.like(root.get("title"), "%" + keyword + "%");
                Predicate contentLike = cb.like(root.get("content"), "%" + keyword + "%");
                predicate = cb.and(predicate, cb.or(titleLike, contentLike));
            }
            return predicate;
        };
        Pageable pageable = PageRequest.of(pageNum - 1, pageSize, Sort.by(Sort.Direction.DESC, "createTime"));
        return postingsRepository.findAll(spec, pageable);
    }

    @Override
    public Postings save(Postings postings) {
        return postingsRepository.save(postings);
    }

    @Override
    public Postings getById(Long id) {
        return postingsRepository.findById(id).orElse(null);
    }

    @Override
    public boolean updateById(Postings postings) {
        if (postings == null || postings.getPostingsId() == null) {
            return false;
        }
        postingsRepository.save(postings);
        return true;
    }
}