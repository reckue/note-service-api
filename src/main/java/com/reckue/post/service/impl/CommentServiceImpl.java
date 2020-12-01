package com.reckue.post.service.impl;


import com.reckue.post.exception.ReckueAccessDeniedException;
import com.reckue.post.exception.ReckueIllegalArgumentException;
import com.reckue.post.exception.model.comment.CommentNotFoundException;
import com.reckue.post.exception.model.post.PostNotFoundException;
import com.reckue.post.model.Comment;
import com.reckue.post.model.Node;
import com.reckue.post.model.type.ParentType;
import com.reckue.post.processors.annotations.NotNullableArgs;
import com.reckue.post.repository.CommentRepository;
import com.reckue.post.repository.NodeRepository;
import com.reckue.post.repository.PostRepository;
import com.reckue.post.service.CommentService;
import com.reckue.post.service.NodeService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang.SerializationUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Class CommentServiceImpl represents realization of CommentService.
 *
 * @author Artur Magomedov
 */
@Service
@RequiredArgsConstructor
public class CommentServiceImpl implements CommentService {

    private final CommentRepository commentRepository;
    private final NodeRepository nodeRepository;
    private final PostRepository postRepository;
    private final NodeService nodeService;

    /**
     * This method is used to create an object of class Comment.
     *
     * @param comment   object of class Comment
     * @param tokenInfo user token info
     * @return comment object of class Comment
     */
    @Override
    @Transactional
    @NotNullableArgs
    public Comment create(Comment comment, Map<String, Object> tokenInfo) {
        String userId = (String) tokenInfo.get("userId");
        comment.setUserId(userId);
        // to set default value as null
        if (comment.getCommentId().length() < 7) {
            comment.setCommentId(null);
        }
        validateCreatingComment(comment);

        Comment storedComment = (Comment) SerializationUtils.clone(comment);
        List<Node> nodeList = null;

        if (comment.getNodes() != null) {
            nodeList = comment.getNodes();
            comment.setNodes(null);
            storedComment = commentRepository.save(comment);
            final String commentId = storedComment.getId();

            nodeList.forEach(node -> {
                node.setParentId(commentId);
                node.setParentType(ParentType.COMMENT);
                nodeService.create(node, tokenInfo);
            });
        }
        storedComment.setNodes(nodeList);
        return storedComment;
    }

    /**
     * This method is used to check comment validation.
     * Throws {@link PostNotFoundException} in case if such post isn't contained in database.
     * Throws {@link CommentNotFoundException} in case if such comment isn't contained in database.
     *
     * @param comment object of class Comment
     */
    public void validateCreatingComment(Comment comment) {
        if (!postRepository.existsById(comment.getPostId())) {
            throw new PostNotFoundException(comment.getPostId());
        }
        if (comment.getCommentId() != null && !commentRepository.existsById(comment.getCommentId())) {
            throw new CommentNotFoundException(comment.getCommentId());
        }
    }

    /**
     * This method is used to update data in an object of class Comment.
     * Throws {@link CommentNotFoundException} in case
     * if such object isn't contained in database.
     * Throws {@link ReckueIllegalArgumentException} in case
     * if such parameter is null.
     * Throws {@link ReckueAccessDeniedException} in case if the user isn't an comment owner or
     * hasn't admin authorities.
     *
     * @param comment   object of class Comment
     * @param tokenInfo user token info
     * @return comment object of class Comment
     */
    @Override
    public Comment update(Comment comment, Map<String, Object> tokenInfo) {
        if (comment.getId() == null) {
            throw new ReckueIllegalArgumentException("The parameter is null");
        }

        if (!comment.getNodes().isEmpty()) {
            comment.getNodes().forEach(node -> {
                node.setParentId(comment.getId());
                nodeService.create(node, tokenInfo);
            });
        }

        Comment savedComment = commentRepository
                .findById(comment.getId())
                .orElseThrow(() -> new CommentNotFoundException(comment.getId()));
        savedComment.setCommentId(comment.getCommentId().length() > 7 ? comment.getCommentId() : null);
        savedComment.setNodes(comment.getNodes());

        if (!tokenInfo.get("userId").equals(savedComment.getUserId())
                && !tokenInfo.get("authorities").equals("ROLE_ADMIN")) {
            throw new ReckueAccessDeniedException("The operation is forbidden");
        }

        return commentRepository.save(savedComment);
    }

    /**
     * This method is used to get all objects of class Comment.
     *
     * @return list of objects of class Comment
     */
    @Override
    public List<Comment> findAll() {
        List<Comment> comments = commentRepository.findAll();

        for (Comment comment : comments) {
            List<Node> nodes = nodeRepository.findAllByParentId(comment.getId());
            comment.setNodes(nodes);
        }
        return comments;
    }

    /**
     * This method is used to get all objects of class Comment by parameters.
     * Throws {@link ReckueIllegalArgumentException} in case
     * if limit or offset is incorrect.
     *
     * @param limit  quantity of objects
     * @param offset quantity to skip
     * @param sort   parameter for sorting
     * @param desc   sorting descending
     * @return list of objects of class Comment
     */
    @Override
    public List<Comment> findAll(Integer limit, Integer offset, String sort, Boolean desc) {
        if (limit == null) limit = 10;
        if (offset == null) offset = 0;
        if (StringUtils.isEmpty(sort)) sort = "id";
        if (desc == null) desc = false;

        if (limit < 0 || offset < 0) {
            throw new ReckueIllegalArgumentException("Limit or offset is incorrect");
        }
        return findAllByTypeAndDesc(sort, desc).stream()
                .limit(limit)
                .skip(offset)
                .collect(Collectors.toList());
    }

    /**
     * This method is used to sort objects in descending order by type.
     *
     * @param sort parameter for sorting
     * @param desc sorting descending
     * @return list of objects of class Comment sorted by the selected parameter for sorting
     * in descending order
     */
    public List<Comment> findAllByTypeAndDesc(String sort, boolean desc) {
        if (desc) {
            List<Comment> comments = findAllBySortType(sort);
            Collections.reverse(comments);
            return comments;
        }
        return findAllBySortType(sort);
    }

    /**
     * This method is used to sort objects by type.
     *
     * @param sort type of sorting: id, text, userId, postId, createdDate or modificationDate
     * @return list of objects of class Comment sorted by the selected parameter for sorting
     */
    public List<Comment> findAllBySortType(String sort) {
        switch (sort) {
            case "id":
                return findAllAndSortById();
            case "userId":
                return findAllAndSortByUserId();
            case "postId":
                return findAllAndSortByPostId();
            case "createdDate":
                return findAllAndSortByCreatedDate();
            case "modificationDate":
                return findAllAndSortByModificationDate();
        }
        throw new ReckueIllegalArgumentException("Such field as " + sort + " doesn't exist");
    }

    /**
     * This method is used to sort objects by modificationDate.
     *
     * @return list of objects of class Comment sorted by modificationDate
     */
    private List<Comment> findAllAndSortByModificationDate() {
        return findAll().stream()
                .sorted(Comparator.comparing(Comment::getModificationDate))
                .collect(Collectors.toList());
    }

    /**
     * This method is used to sort objects by id.
     *
     * @return list of objects of class Comment sorted by id
     */
    public List<Comment> findAllAndSortById() {
        return findAll().stream()
                .sorted(Comparator.comparing(Comment::getId))
                .collect(Collectors.toList());
    }

    /**
     * This method is used to sort objects by user id.
     *
     * @return list of objects of class Comment sorted by user id
     */
    public List<Comment> findAllAndSortByUserId() {
        return findAll().stream()
                .sorted(Comparator.comparing(Comment::getUserId))
                .collect(Collectors.toList());
    }

    /**
     * This method is used to sort objects by post id.
     *
     * @return list of objects of class Comment sorted by post id
     */
    public List<Comment> findAllAndSortByPostId() {
        return findAll().stream()
                .sorted(Comparator.comparing(Comment::getPostId))
                .collect(Collectors.toList());
    }

    /**
     * This method is used to sort objects by createdDate.
     *
     * @return list of objects of class Comment sorted by createdDate
     */
    public List<Comment> findAllAndSortByCreatedDate() {
        return findAll().stream()
                .sorted(Comparator.comparing(Comment::getCreatedDate))
                .collect(Collectors.toList());
    }

    /**
     * This method is used to get an object by id.
     * Throws {@link CommentNotFoundException} in case if such object isn't contained in database.
     *
     * @param id object
     * @return object of class Comment
     */
    @Override
    public Comment findById(String id) {
        Optional<Comment> comment = commentRepository.findById(id);
        List<Node> nodes = nodeRepository.findAllByParentId(id);
        if (comment.isEmpty())
            throw new CommentNotFoundException(id);

        comment.ifPresent(p -> p.setNodes(nodes));
        return comment.get();
    }

    /**
     * This method is used to get a list of comments by user id.
     * Throws {@link ReckueIllegalArgumentException} in case
     * if limit or offset is incorrect.
     *
     * @param userId user identificator
     * @param limit  quantity of objects
     * @param offset quantity to skip
     * @return list of objects of class Comment
     */
    @Override
    // FIXME: correct the realization of this method
    public List<Comment> findAllByUserId(String userId, Integer limit, Integer offset) {
        if (limit == null) limit = 10;
        if (offset == null) offset = 0;
        if (limit < 0 || offset < 0) {
            throw new ReckueIllegalArgumentException("Limit or offset is incorrect");
        }
        return commentRepository.findAllByUserId(userId)
                .stream()
                .limit(limit)
                .skip(offset)
                .collect(Collectors.toList());
    }

    /**
     * This method is used to delete an object by id.
     * Throws {@link CommentNotFoundException} in case
     * if such object isn't contained in database.
     * Throws {@link ReckueAccessDeniedException} in case if the user isn't an post owner or
     * hasn't admin authorities.
     *
     * @param id        object
     * @param tokenInfo user token info
     */
    @Override
    public void deleteById(String id, Map<String, Object> tokenInfo) {
        if (!commentRepository.existsById(id)) {
            throw new CommentNotFoundException(id);
        }
        Optional<Comment> comment = commentRepository.findById(id);
        if (comment.isPresent()) {
            String commentUser = comment.get().getUserId();
            if (tokenInfo.get("userId").equals(commentUser) || tokenInfo.get("authorities").equals("ROLE_ADMIN")) {
                commentRepository.deleteById(id);
            } else {
                throw new ReckueAccessDeniedException("The operation is forbidden");
            }
        }
    }

    /**
     * This method is used to delete all comments.
     */
    @Override
    public void deleteAll() {
        commentRepository.deleteAll();
    }
}
