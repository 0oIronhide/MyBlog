package com.lrj.myblogmybatis.controller.visitor;


import com.lrj.myblogmybatis.annotations.UserLoginToken;
import com.lrj.myblogmybatis.common.LikeUtils;
import com.lrj.myblogmybatis.common.UUIDUtils;
import com.lrj.myblogmybatis.dto.LikeData;
import com.lrj.myblogmybatis.dto.PubVisitor;
import com.lrj.myblogmybatis.dto.ReplyComment;
import com.lrj.myblogmybatis.enums.BType;
import com.lrj.myblogmybatis.enums.StatusCode;
import com.lrj.myblogmybatis.pojo.Ccomment;
import com.lrj.myblogmybatis.pojo.Comment;
import com.lrj.myblogmybatis.pojo.Visitor;
import com.lrj.myblogmybatis.response.BaseResponse;

import com.lrj.myblogmybatis.service.AdminBlogService;
import com.lrj.myblogmybatis.service.CommentService;
import com.lrj.myblogmybatis.service.TokenService;
import com.lrj.myblogmybatis.service.VisitorService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;


import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.util.Random;
import java.util.concurrent.TimeUnit;


/**
 * @author ：xiximai
 * @description：TODO
 * @date ：2020/3/7 18:02
 */

@Controller
public class LoginOrRegisterController {

    @Autowired
    private VisitorService visitorService;
    @Autowired
    private TokenService tokenService;
    @Autowired
    private CommentService commentService;
    private Logger logger = LoggerFactory.getLogger(this.getClass());
    @Autowired
    private RedisTemplate redisTemplate;
    String email = "";
    // 头像url前缀
    @Value("${comment.avatar}")
    private String avatar;

    @PostMapping("/Toregister")
    @ResponseBody
    public BaseResponse TogetCode(@RequestBody @Validated Visitor visitor, BindingResult result) {
        //判断该邮箱是否注册过
        Visitor exitVisitor = visitorService.getUserByUseremail(visitor.getEmail());
        if (exitVisitor != null) {
            return new BaseResponse(StatusCode.HaveExist);
        }
        if (result.hasErrors()) {
            return new BaseResponse(StatusCode.ErrorEmail);
        }
        email = visitor.getEmail();
        //生成验证码
        String realCode = UUIDUtils.generateUuid8();
        try {
            //发送验证码
            visitorService.getCode(email, realCode);
            redisTemplate.opsForValue().set(visitor.getEmail(), realCode, 60, TimeUnit.SECONDS);
            return new BaseResponse(StatusCode.Ok);
        } catch (Exception e) {
            logger.error("出现错误", e);
            return new BaseResponse(StatusCode.Fail);
        }
    }

    @ResponseBody
    @RequestMapping(value = "/register", method = RequestMethod.POST)
    public BaseResponse Register(@RequestBody Visitor visitor) {
        System.out.println(visitor);
        //判断该邮箱是否注册过
        Visitor exitVisitor = visitorService.getUserByUseremail(visitor.getEmail());
        if (exitVisitor != null) {
            return new BaseResponse(StatusCode.HaveExist);
        }
        Object code = redisTemplate.opsForValue().get(visitor.getEmail());
        if (code == null) {
            return new BaseResponse(StatusCode.timeout);
        }
        if (!visitor.getEmail().equals(email) || !visitor.getCode().equals(code)) {
            return new BaseResponse(StatusCode.UnOk);
        } else if (visitor.getNickname() == null || "".equals(visitor.getNickname())) {
            return new BaseResponse(StatusCode.UnError);
        } else if (visitor.getPassword() == null || "".equals(visitor.getPassword())) {
            return new BaseResponse(StatusCode.PwError);
        } else {
            System.out.println("注册成功");
            visitorService.saveVisitor(visitor);
            return new BaseResponse(StatusCode.Success);
        }
    }

    @ResponseBody
    @PostMapping("/VisitorLogin")
    public BaseResponse VisitorLogin(@RequestBody Visitor visitor, HttpServletResponse response, HttpServletRequest request) {
        Visitor LogVisitor = visitorService.getUserByUseremail(visitor.getEmail());
        if (LogVisitor == null) {
            return new BaseResponse(StatusCode.UserNotfound);
        } else {
            if (!LogVisitor.getPassword().equals(visitor.getPassword())) {
                return new BaseResponse(StatusCode.PwError);
            } else {
                String token = tokenService.getToken(LogVisitor);
                String username = LogVisitor.getNickname();
                Long userid = LogVisitor.getId();
                String email = LogVisitor.getEmail();

                HttpSession session = request.getSession();
                session.setAttribute("LogVisitor", LogVisitor);

                response.addHeader("token", token);
                BaseResponse baseResponse = new BaseResponse(StatusCode.Success);
                Visitor visitorInfo = new Visitor();
                visitorInfo.setNickname(username);
                visitorInfo.setEmail(email);
                visitorInfo.setId(userid);
                baseResponse.setData(visitorInfo);
                return baseResponse;
            }
        }
    }

    String setAvatar(String s) {
        Random random = new Random();
        int i = random.nextInt(42); // [0,42)
        i = i + 1;
        String suffix = i + ".jpg";
        String newavatar = s.concat(suffix);
        return newavatar;
    }

    @GetMapping("/visitorLogout")
    public BaseResponse visitorLogout(HttpServletRequest request) {
        HttpSession session = request.getSession();
        session.removeAttribute("LogVisitor");
        return new BaseResponse(StatusCode.Success);
    }

    /**
     * 用户认证
     *
     * @param pubVisitor
     * @param result
     * @return
     */
    @UserLoginToken
    @ResponseBody
    @RequestMapping(value = "/visitor/comment", method = RequestMethod.POST)
    public BaseResponse Comment(@RequestBody @Validated PubVisitor pubVisitor, BindingResult result) {
        Comment comment = new Comment();
        String navatar = visitorService.getAvatar(pubVisitor.getId());
        System.out.println(navatar);
        comment.setAvatar(navatar);
        comment.setVisitorId(pubVisitor.getId());
        comment.setBlogId(pubVisitor.getBlogId());
        comment.setEmail(pubVisitor.getUseremail());
        comment.setContent(pubVisitor.getContent());
        comment.setNickname(pubVisitor.getUsername());
        comment.setParentCommentId((long) 0);
        commentService.saveComment(comment);
        return new BaseResponse(StatusCode.Success);
    }

    @UserLoginToken
    @ResponseBody
    @RequestMapping(value = "/visitor/reply", method = RequestMethod.POST)
    public BaseResponse Reply(@RequestBody ReplyComment replyComment) {
        Ccomment ccomment = new Ccomment();
        if (replyComment.getParentid() != 0) {
            // 子评论回复
            ccomment.setParentCommentId(replyComment.getCommentid());
            Insert(ccomment, replyComment);
            if (replyComment.getCommentnickname().contains("@")) {
                String nickname = replyComment.getCommentnickname().substring(0, replyComment.getCommentnickname().indexOf(" "));
                ccomment.setNickname(replyComment.getUsername() + " @ " + nickname);
            } else {
                ccomment.setNickname(replyComment.getUsername() + " @ " + replyComment.getCommentnickname());
            }
        } else {
            //主评论回复
            ccomment.setNickname(replyComment.getUsername());
            Insert(ccomment, replyComment);
            ccomment.setParentCommentId(replyComment.getCommentid());
        }
        commentService.saveCcomment(ccomment);
        return new BaseResponse(StatusCode.Success);
    }

    void Insert(Ccomment ccomment, ReplyComment replyComment) {
        ccomment.setContent(replyComment.getContent());
        ccomment.setEmail(replyComment.getUseremail());
        ccomment.setBlogId(replyComment.getBlogId());
        ccomment.setVisitorId(replyComment.getId());
        String navatar = visitorService.getAvatar(ccomment.getVisitorId());
        System.out.println(navatar);
        ccomment.setAvatar(navatar);
    }


    @UserLoginToken
    @ResponseBody
    @RequestMapping(value = "/visitor/like", method = RequestMethod.POST)
    public BaseResponse Like(@RequestBody LikeData likeData, BindingResult result) {
        String key = LikeUtils.getLikeKey(BType.LIKED_ARTICLE, likeData.getBlogId());
        if (result.hasErrors()) {
            return new BaseResponse(StatusCode.Fail);
        }
        if (redisTemplate.opsForHash().hasKey(key, likeData.getUserId())) {
            System.out.println("已经点赞过了！");
            return new BaseResponse(StatusCode.InvalidParams);
        } else {
            try {
                redisTemplate.opsForHash().put(key, likeData.getUserId(), 0);
                System.out.println("插入成功");
            } catch (Exception e) {
                logger.error(e.getMessage());
                return new BaseResponse(StatusCode.Fail);
            }
        }
        return new BaseResponse(StatusCode.Success);
    }

    @Autowired
    private AdminBlogService adminBlogService;

    @UserLoginToken
    @ResponseBody
    @RequestMapping(value = "/visitor/dislike", method = RequestMethod.POST)
    public BaseResponse DisLike(@RequestBody LikeData likeData, BindingResult result) {
        String key = LikeUtils.getLikeKey(BType.LIKED_ARTICLE, likeData.getBlogId());
        if (result.hasErrors()) {
            return new BaseResponse(StatusCode.Fail);
        }
        try {
            redisTemplate.opsForHash().delete(key, likeData.getUserId());
            if (adminBlogService.getLikeByUserIdAndBlogId(likeData) > 0) {
                System.out.println(adminBlogService.getLikeByUserIdAndBlogId(likeData));
                adminBlogService.deleteByUserIdAndBlogId(likeData);
            }
            System.out.println("删除成功");
        } catch (Exception e) {
            logger.error(e.getMessage());
            return new BaseResponse(StatusCode.Fail);
        }
        return new BaseResponse(StatusCode.Success);

    }
}
