package com.javachina.controller;

import blade.kit.EncrypKit;
import blade.kit.PatternKit;
import blade.kit.StringKit;
import com.blade.Blade;
import com.blade.ioc.annotation.Inject;
import com.blade.jdbc.AR;
import com.blade.jdbc.Page;
import com.blade.jdbc.QueryParam;
import com.blade.patchca.PatchcaService;
import com.blade.route.annotation.Path;
import com.blade.route.annotation.PathVariable;
import com.blade.route.annotation.Route;
import com.blade.view.ModelAndView;
import com.blade.web.http.HttpMethod;
import com.blade.web.http.Request;
import com.blade.web.http.Response;
import com.javachina.Constant;
import com.javachina.Types;
import com.javachina.kit.SessionKit;
import com.javachina.kit.Utils;
import com.javachina.model.LoginUser;
import com.javachina.model.User;
import com.javachina.service.*;

import java.io.File;
import java.util.List;
import java.util.Map;

@Path("/")
public class UserController extends BaseController {

    @Inject
    private SettingsService settingsService;

    @Inject
    private UserService userService;

    @Inject
    private UserinfoService userinfoService;

    @Inject
    private CommentService commentService;

    @Inject
    private NoticeService noticeService;

    @Inject
    private FavoriteService favoriteService;

    @Inject
    private TopicService topicService;

    @Route(value = "/god", method = HttpMethod.GET)
    public ModelAndView god(Request request, Response response) {
        String str = request.param("g");
        return this.getView(str);
    }


    /**
     * 获取验证码
     */
    @Route(value = "/captcha", method = HttpMethod.GET)
    public void show_captcha(Request request, Response response) {
        PatchcaService.get().size(200, 40).render(request, response);
    }

    /**
     * 登录页面
     */
    @Route(value = "/signin", method = HttpMethod.GET)
    public ModelAndView show_signin(Request request, Response response) {
        return this.getView("signin");
    }

    /**
     * 登录操作
     */
    @Route(value = "/signin", method = HttpMethod.POST)
    public ModelAndView signin(Request request, Response response) {

        String login_name = request.query("login_name");
        String pass_word = request.query("pass_word");
        String rememberme = request.query("rememberme");

        if (StringKit.isBlank(login_name) || StringKit.isBlank(pass_word)) {
            request.attribute(this.ERROR, "用户名和密码不能为空");
            request.attribute("login_name", login_name);
            return this.getView("signin");
        }

        boolean hasUser = userService.hasUser(login_name);
        if (!hasUser) {
            request.attribute(this.ERROR, "该用户不存在");
            request.attribute("login_name", login_name);
            return this.getView("signin");
        }

        User user = userService.signin(login_name, pass_word);
        if (null == user) {
            request.attribute(this.ERROR, "用户名或密码错误");
            request.attribute("login_name", login_name);
            return this.getView("signin");
        }



        LoginUser loginUser = userService.getLoginUser(user, null);
        SessionKit.setLoginUser(request.session(), loginUser);
        if (StringKit.isNotBlank(rememberme) && rememberme.equals("on")) {
            SessionKit.setCookie(response, Constant.USER_IN_COOKIE, loginUser.getUid());
        }


        String val = SessionKit.getCookie(request, Constant.JC_REFERRER_COOKIE);
        if (StringKit.isNotBlank(val)) {
            response.redirect(val);
        } else {
            response.go("/");
        }
        return null;
    }

    /**
     * 注册页面
     */
    @Route(value = "/signup", method = HttpMethod.GET)
    public ModelAndView show_signup(Request request, Response response) {
        Object allow_signup = Constant.SYS_INFO.get(Types.allow_signup.toString());
        if (null != allow_signup && allow_signup.toString().equals("false")) {
            request.attribute(this.INFO, "暂时停止注册");
        }
        return this.getView("signup");
    }

    /**
     * 注销
     */
    @Route(value = "/logout")
    public void logout(Request request, Response response) {
        SessionKit.removeUser(request.session());
        SessionKit.removeCookie(response);
        response.go("/");
    }

    /**
     * 通知列表
     */
    @Route(value = "/notices", method = HttpMethod.GET)
    public ModelAndView notices(Request request, Response response) {
        LoginUser user = SessionKit.getLoginUser();
        if (null == user) {
            response.go("/signin");
            return null;
        }

        List<Map<String, Object>> notices = noticeService.getNoticeList(user.getUid());
        request.attribute("notices", notices);

        // 清空我的通知
        if (null != notices && notices.size() > 0) {
            noticeService.read(user.getUid());
        }

        return this.getView("notices");
    }

    /**
     * 注册操作
     */
    @Route(value = "/signup", method = HttpMethod.POST)
    public ModelAndView signup(Request request, Response response) {

        String login_name = request.query("login_name");
        String pass_word = request.query("pass_word");
        String auth_code = request.query("auth_code");

        request.attribute("login_name", login_name);

        if (StringKit.isBlank(login_name) || StringKit.isBlank(pass_word) || StringKit.isBlank(auth_code)) {
            request.attribute(this.ERROR, "参数不能为空");
            return this.getView("signup");
        }

//        if (login_name.length() > 16 || login_name.length() < 4) {
//            request.attribute(this.ERROR, "请输入4-16位用户名");
//            return this.getView("signup");
//        }

        if (!PatternKit.isStudentNum(login_name)) {
            request.attribute(this.ERROR, "请输入只包含字母／数字／下划线的用户名");
            return this.getView("signup");
        }

        if (!Utils.isSignup(login_name)) {
            request.attribute(this.ERROR, "您的用户名中包含禁用字符，请修改后注册");
            return this.getView("signup");
        }



//        if (pass_word.length() > 20 || pass_word.length() < 6) {
//            request.attribute(this.ERROR, "请输入6-20位字符的密码");
//            return this.getView("signup");
//        }

//        String patchca = request.session().attribute("patchca");
//        if (StringKit.isNotBlank(patchca) && !patchca.equalsIgnoreCase(auth_code)) {
//            request.attribute(this.ERROR, "验证码输入错误");
//            return this.getView("signup");
//        }

        QueryParam queryParam = QueryParam.me();
        queryParam.eq("login_name", login_name);
        queryParam.in("status", AR.in(0, 1));
        User user = userService.getUser(queryParam);
        if (null != user) {
            request.attribute(this.ERROR, "该用户名已经被占用，请更换用户名");
            return this.getView("signup");
        }


        User user_ = userService.signup(login_name, pass_word);
        if (null != user_) {
            request.attribute(this.INFO, "注册成功");
            return this.getView("signin");
        } else {
            request.attribute(this.ERROR, "注册发生异常");
        }
        return this.getView("signup");
    }


    /**
     * 忘记密码页面
     */
    @Route(value = "/forgot", method = HttpMethod.GET)
    public ModelAndView show_forgot(Request request, Response response) {
        return this.getView("forgot");
    }



    /**
     * 用户主页
     */
    @Route(value = "/member/:username")
    public ModelAndView member(@PathVariable("username") String username,
                               Request request, Response response) {

        QueryParam up = QueryParam.me();
        up.eq("status", 1).eq("login_name", username);

        User user = userService.getUser(up);
        if (null == user) {
            // 不存在的用户
            response.text("not found user.");
            return null;
        }

        Map<String, Object> profile = userService.getUserDetail(user.getUid());
        request.attribute("profile", profile);

        // 是否关注了该用户
        LoginUser loginUser = SessionKit.getLoginUser();
        if (null == loginUser) {
            request.attribute("is_follow", false);
            SessionKit.setCookie(response, Constant.JC_REFERRER_COOKIE, request.url());
        } else {
            boolean is_follow = favoriteService.isFavorite(Types.following.toString(), loginUser.getUid(), user.getUid());
            request.attribute("is_follow", is_follow);
        }

        // 最新创建的主题
        QueryParam tp = QueryParam.me();
        tp.eq("status", 1).eq("uid", user.getUid()).orderby("create_time desc, update_time desc").page(1, 10);
        Page<Map<String, Object>> topicPage = topicService.getPageList(tp);
        request.attribute("topicPage", topicPage);

        // 最新发布的回复
        QueryParam cp = QueryParam.me();
        cp.eq("uid", user.getUid()).orderby("create_time desc").page(1, 10);
        Page<Map<String, Object>> commentPage = commentService.getPageListMap(cp);
        request.attribute("commentPage", commentPage);

        return this.getView("member_detail");
    }

    /**
     * 我收藏的帖子
     */
    @Route(value = "/my/topics")
    public ModelAndView myTopics(Request request, Response response) {

        LoginUser user = SessionKit.getLoginUser();
        if (null == user) {
            response.go("/");
            return null;
        }

        Integer page = request.queryAsInt("p");

        Page<Map<String, Object>> favoritesPage = favoriteService.getMyTopics(user.getUid(), page, 10);
        request.attribute("favoritesPage", favoritesPage);

        return this.getView("my_topics");
    }

    /**
     * 我收藏的节点
     */
    @Route(value = "/my/nodes")
    public ModelAndView myNodes(Request request, Response response) {

        LoginUser user = SessionKit.getLoginUser();
        if (null == user) {
            response.go("/");
            return null;
        }

        List<Map<String, Object>> nodes = favoriteService.getMyNodes(user.getUid());
        request.attribute("nodes", nodes);

        return this.getView("my_nodes");
    }

    /**
     * 我的关注
     */
    @Route(value = "/my/following")
    public ModelAndView following(Request request, Response response) {

        LoginUser user = SessionKit.getLoginUser();
        if (null == user) {
            response.go("/");
            return null;
        }

        Integer page = request.queryAsInt("p");

        Page<Map<String, Object>> followingPage = favoriteService.getFollowing(user.getUid(), page, 10);
        request.attribute("followingPage", followingPage);

        return this.getView("following");
    }


    /**
     * 关注／收藏／点赞／下沉帖
     */
    @Route(value = "/favorite", method = HttpMethod.POST)
    public void favorite(Request request, Response response) {
        LoginUser user = SessionKit.getLoginUser();
        if (null == user) {
            this.nosignin(response);
            return;
        }

        // topic：帖子，node：节点，love：喜欢，following：关注
        String type = request.query("type");
        Long event_id = request.queryAsLong("event_id");

        if (StringKit.isBlank(type) || null == event_id || event_id == 0) {
            return;
        }

        Integer count = favoriteService.update(type, user.getUid(), event_id);

        LoginUser loginUser = userService.getLoginUser(null, user.getUid());
        SessionKit.setLoginUser(request.session(), loginUser);

        this.success(response, count);
    }

    /**
     * 个人设置
     */
    @Route(value = "settings", method = HttpMethod.GET)
    public ModelAndView show_settings(Request request, Response response) {

        LoginUser user = SessionKit.getLoginUser();
        if (null == user) {
            response.go("/");
            return null;
        }
        Map<String, Object> profile = userService.getUserDetail(user.getUid());
        request.attribute("profile", profile);
        return this.getView("settings");
    }

    /**
     * 个人设置
     */
    @Route(value = "settings", method = HttpMethod.POST)
    public void settings(Request request, Response response) {

        LoginUser loginUser = SessionKit.getLoginUser();
        if (null == loginUser) {
            this.nosignin(response);
            return;
        }

        String type = request.query("type");
        if (StringKit.isBlank(type)) {
            return;
        }

        String avatar = request.query("avatar");

        // 修改头像
        if (type.equals("avatar") && StringKit.isNotBlank(avatar)) {
            try {
                String avatar_path = Blade.me().webRoot() + File.separator + avatar;
                userService.updateAvatar(loginUser.getUid(), avatar_path);

                LoginUser loginUserTemp = userService.getLoginUser(null, loginUser.getUid());
                SessionKit.setLoginUser(request.session(), loginUserTemp);

                this.success(response, "");
            } catch (Exception e) {
                e.printStackTrace();
                this.error(response, "头像更换失败");
            }
            return;
        }

        // 修改基本信息
        if (type.equals("info")) {
            String nickName = request.query("nick_name");
            String jobs = request.query("jobs");
            String webSite = request.query("web_site");
            String github = request.query("github");
            String weibo = request.query("weibo");
            String location = request.query("location");
            String signature = request.query("signature");
            String instructions = request.query("instructions");

            try {
                boolean flag = userinfoService.update(loginUser.getUid(), nickName, jobs, webSite, github, weibo, location, signature, instructions);

                if (flag) {
                    LoginUser loginUserTemp = userService.getLoginUser(null, loginUser.getUid());
                    SessionKit.setLoginUser(request.session(), loginUserTemp);
                    this.success(response, "");
                } else {
                    this.error(response, "修改失败");
                }
            } catch (Exception e) {
                e.printStackTrace();
                this.error(response, "修改失败");
            }
            return;
        }

        // 修改密码
        if (type.equals("pwd")) {

            Map<String, Object> profile = userService.getUserDetail(loginUser.getUid());
            request.attribute("profile", profile);

            String curpwd = request.query("curpwd");
            String newpwd = request.query("newpwd");

            if (StringKit.isBlank(curpwd) || StringKit.isBlank(newpwd)) {
                this.error(response, "参数不能为空");
                return;
            }

            if (!EncrypKit.md5(loginUser.getUser_name() + curpwd).equals(loginUser.getPass_word())) {
                this.error(response, "旧密码输入错误");
                return;
            }

            try {
                String new_pwd = EncrypKit.md5(loginUser.getUser_name() + newpwd);
                userService.updatePwd(loginUser.getUid(), new_pwd);

                LoginUser loginUserTemp = userService.getLoginUser(null, loginUser.getUid());
                SessionKit.setLoginUser(request.session(), loginUserTemp);

                this.success(response, "");
            } catch (Exception e) {
                e.printStackTrace();
                this.error(response, "密码修改失败");
            }
            return;
        }

    }

    /**
     * 显示markdown预览
     */
    @Route(value = "markdown", method = HttpMethod.POST)
    public void getMarkdown(Request request, Response response) {
        LoginUser user = SessionKit.getLoginUser();
        if (null == user) {
            response.text("");
            return;
        }
        String content = request.query("content");
        response.text(Utils.markdown2html(content));
    }

}
